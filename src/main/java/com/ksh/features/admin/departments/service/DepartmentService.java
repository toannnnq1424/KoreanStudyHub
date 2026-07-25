package com.ksh.features.admin.departments.service;

import com.ksh.entities.Department;
import com.ksh.entities.DepartmentActivity;
import com.ksh.entities.User;
import com.ksh.features.admin.departments.dto.DepartmentDtos.DepartmentForm;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.security.Role;
import com.ksh.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Write-side department mutations: create/update/toggle and leader assignment
 * with promote/demote rules. Audit rows go through {@link DepartmentAuditWriter}.
 * Read queries live on {@link DepartmentQueryService}.
 */
@Service
public class DepartmentService {

    static final String MSG_NOT_FOUND = "Không tìm thấy bộ môn";
    static final String MSG_CODE_EXISTS = "Mã bộ môn đã tồn tại";
    static final String MSG_LEADER_NOT_FOUND = "Không tìm thấy người dùng để gán trưởng bộ môn";
    static final String MSG_LEADER_INELIGIBLE =
            "Trưởng bộ môn phải là giảng viên hoặc trưởng bộ môn đang hoạt động";

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final DepartmentAuditWriter auditWriter;

    public DepartmentService(DepartmentRepository departmentRepository,
                             UserRepository userRepository,
                             DepartmentAuditWriter auditWriter) {
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.auditWriter = auditWriter;
    }

    @Transactional
    public String create(DepartmentForm form, Long actorId) {
        String code = normalizeCode(form.code());
        if (departmentRepository.existsByCode(code)) {
            throw new DepartmentValidationException(MSG_CODE_EXISTS);
        }
        Department entity = new Department(
                form.name().trim(),
                code,
                StringUtils.blankToNull(form.description()),
                form.active());
        Department saved = departmentRepository.save(entity);
        auditWriter.write(saved.getId(), DepartmentActivity.TYPE_CREATED,
                "Tạo bộ môn " + saved.getName() + " (" + saved.getCode() + ")",
                null, actorId);

        if (form.leaderUserId() != null) {
            applyLeaderAssignment(saved, form.leaderUserId(), actorId);
            departmentRepository.save(saved);
        }
        return saved.getName();
    }

    @Transactional
    public void update(Long id, DepartmentForm form, Long actorId) {
        Department entity = departmentRepository.findById(id)
                .orElseThrow(() -> new DepartmentValidationException(MSG_NOT_FOUND));
        String code = normalizeCode(form.code());
        if (departmentRepository.existsByCodeAndIdNot(code, id)) {
            throw new DepartmentValidationException(MSG_CODE_EXISTS);
        }

        Map<String, Object> changes = diffFields(entity, form, code);
        boolean activeChanged = entity.isActive() != form.active();

        entity.applyEdit(
                form.name().trim(),
                code,
                StringUtils.blankToNull(form.description()),
                form.active());
        applyLeaderAssignment(entity, form.leaderUserId(), actorId);
        departmentRepository.save(entity);

        // Identity/description changes → UPDATED (active handled as dedicated type).
        Map<String, Object> fieldChanges = new LinkedHashMap<>(changes);
        fieldChanges.remove("active");
        if (!fieldChanges.isEmpty()) {
            auditWriter.write(entity.getId(), DepartmentActivity.TYPE_UPDATED,
                    "Cập nhật thông tin bộ môn",
                    auditWriter.serialize(fieldChanges), actorId);
        }
        // Visibility flip on the edit form (list toggle endpoint also writes this type).
        if (activeChanged) {
            boolean nowActive = form.active();
            auditWriter.write(entity.getId(),
                    nowActive ? DepartmentActivity.TYPE_ACTIVATED : DepartmentActivity.TYPE_DEACTIVATED,
                    nowActive ? "Hiện bộ môn" : "Ẩn bộ môn",
                    null, actorId);
        }
    }

    @Transactional
    public boolean toggleActive(Long id, Long actorId) {
        Department entity = departmentRepository.findById(id)
                .orElseThrow(() -> new DepartmentValidationException(MSG_NOT_FOUND));
        boolean now = entity.toggleActive();
        departmentRepository.save(entity);
        auditWriter.write(entity.getId(),
                now ? DepartmentActivity.TYPE_ACTIVATED : DepartmentActivity.TYPE_DEACTIVATED,
                now ? "Hiện bộ môn" : "Ẩn bộ môn",
                null, actorId);
        return now;
    }

    /**
     * Assigns or clears the department leader with promote/demote side effects.
     *
     * @param departmentId target department
     * @param leaderUserId   new leader, or null to unassign
     * @param actorId      admin performing the action (audit)
     */
    @Transactional
    public void assignLeader(Long departmentId, Long leaderUserId, Long actorId) {
        Department entity = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new DepartmentValidationException(MSG_NOT_FOUND));
        applyLeaderAssignment(entity, leaderUserId, actorId);
        departmentRepository.save(entity);
    }

    private void applyLeaderAssignment(Department entity, Long newLeaderUserId, Long actorId) {
        Long oldLeaderId = entity.getLeaderUserId();
        if (oldLeaderId != null && oldLeaderId.equals(newLeaderUserId)) {
            return;
        }
        if (oldLeaderId == null && newLeaderUserId == null) {
            return;
        }

        String newLeaderEmail = null;
        if (newLeaderUserId != null) {
            User candidate = userRepository.findById(newLeaderUserId)
                    .orElseThrow(() -> new DepartmentValidationException(MSG_LEADER_NOT_FOUND));
            if (!candidate.isActive() || candidate.isDeleted()
                    || !DepartmentQueryService.LEADER_ELIGIBLE.contains(candidate.getRole())) {
                throw new DepartmentValidationException(MSG_LEADER_INELIGIBLE);
            }
            candidate.promoteToLeader(entity.getId());
            userRepository.save(candidate);
            newLeaderEmail = candidate.getEmail();
        }

        entity.assignLeader(newLeaderUserId);
        // Flush leader_user_id before demote check so DB no longer lists the old leader.
        departmentRepository.saveAndFlush(entity);

        // Demote previous leader only when they leader no other department.
        if (oldLeaderId != null && !oldLeaderId.equals(newLeaderUserId)) {
            demoteIfNoLongerLeader(oldLeaderId);
        }

        if (entity.getId() != null) {
            if (newLeaderUserId == null) {
                auditWriter.write(entity.getId(), DepartmentActivity.TYPE_LEADER_CLEARED,
                        "Bỏ gán trưởng bộ môn", null, actorId);
            } else {
                auditWriter.write(entity.getId(), DepartmentActivity.TYPE_LEADER_ASSIGNED,
                        "Gán trưởng bộ môn: " + (newLeaderEmail != null ? newLeaderEmail : newLeaderUserId),
                        null, actorId);
            }
        }
    }

    private void demoteIfNoLongerLeader(Long userId) {
        if (departmentRepository.existsByLeaderUserId(userId)) {
            return;
        }
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getRole() == Role.LEADER) {
                user.demoteFromLeaderToLecturer();
                userRepository.save(user);
            }
        });
    }

    /** Builds a map of changed identity fields for UPDATED audit metadata. */
    private static Map<String, Object> diffFields(Department entity, DepartmentForm form, String code) {
        Map<String, Object> changes = new LinkedHashMap<>();
        String newName = form.name().trim();
        String newDesc = StringUtils.blankToNull(form.description());
        if (!Objects.equals(entity.getName(), newName)) {
            changes.put("name", Map.of("from", nullToEmpty(entity.getName()), "to", newName));
        }
        if (!Objects.equals(entity.getCode(), code)) {
            changes.put("code", Map.of("from", nullToEmpty(entity.getCode()), "to", code));
        }
        if (!Objects.equals(entity.getDescription(), newDesc)) {
            changes.put("description", true);
        }
        if (entity.isActive() != form.active()) {
            changes.put("active", Map.of("from", entity.isActive(), "to", form.active()));
        }
        return changes;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeCode(String raw) {
        return raw == null ? null : raw.trim().toUpperCase(Locale.ROOT);
    }
}
