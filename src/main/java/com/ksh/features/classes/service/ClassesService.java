package com.ksh.features.classes.service;

import com.ksh.entities.ClassActivity;
import com.ksh.entities.ClassEntity;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.classes.dto.ClassesDtos.ClassForm;
import com.ksh.features.classes.dto.ClassesDtos.ClassRow;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Business service for class CRUD operations on the lecturer-facing screens.
 *
 * <p>Authorization rules (enforced here, NOT in the controller):
 * <ul>
 *   <li>LECTURER can only view/edit/delete their own classes ({@code lecturer_id == user.id}).</li>
 *   <li>LEADER can view/edit/delete classes in their resolved department; ADMIN is global.</li>
 *   <li>Authorization violations throw {@link AccessDeniedException} → HTTP 403.</li>
 *   <li>Non-existent or soft-deleted classes throw {@link EntityNotFoundException} → HTTP 404.</li>
 * </ul>
 *
 * <p>Caller identity is supplied directly by controllers from
 * {@code @AuthenticationPrincipal KshUserDetails} as {@code (Long userId, Role role)}.
 * The service does not look up the caller by email — Spring Security has already
 * loaded the user during authentication, so a second SELECT per request would be
 * wasted work.
 *
 * <p>Every mutation (create/update/softDelete) writes one row to
 * {@link ClassActivity} via {@link ClassActivityWriter}. Because service methods
 * are {@code @Transactional}, a failure when inserting the activity record will
 * also roll back the class mutation. The create flow is delegated to a
 * package-private {@link ClassCreator} helper that owns subject validation and
 * the collision-retry loop.
 */
@Service
public class ClassesService {

    private final ClassRepository classRepository;
    private final ClassActivityWriter activityWriter;
    private final DepartmentRepository subjectRepository;
    private final ClassCreator creator;
    private final ClassRoleAccessPolicy accessPolicy;

    public ClassesService(ClassRepository classRepository,
                          ClassActivityWriter activityWriter,
                          DepartmentRepository subjectRepository,
                          ClassRoleAccessPolicy accessPolicy,
                          ApplicationEventPublisher eventPublisher) {
        this.classRepository = classRepository;
        this.activityWriter = activityWriter;
        this.subjectRepository = subjectRepository;
        this.accessPolicy = accessPolicy;
        this.creator = new ClassCreator(classRepository, activityWriter,
                subjectRepository, eventPublisher);
    }

    // ───────────────────── Public CRUD API ──────────────────────────

    /**
     * Returns the page of classes visible to the current user.
     * LECTURER → only their own classes.
     * LEADER/ADMIN → all classes that have not been soft-deleted.
     *
     * <p>The gradient assigned to each {@link ClassRow} is derived from the row's
     * position within the CURRENT page (0-based), not the global ranking. Different
     * pages can therefore repeat gradient colours — this is intentional and matches
     * the audit's "good enough" tolerance for the cosmetic ordering of class
     * thumbnails. Pages are otherwise sorted strictly per the supplied {@link Pageable}
     * (typically {@code createdAt DESC}).
     */
    @Transactional(readOnly = true)
    public Page<ClassRow> listForUser(Long userId, Role role, Pageable pageable) {
        Page<ClassEntity> page;
        if (role == Role.LECTURER) {
            page = classRepository.findAllAccessibleToLecturer(userId, pageable);
        } else if (role == Role.LEADER) {
            page = accessPolicy.leaderDepartmentId(userId)
                    .map(id -> classRepository.findAllByDepartmentId(id, pageable))
                    .orElseGet(() -> Page.empty(pageable));
        } else if (role == Role.ADMIN) {
            page = classRepository.findAllBy(pageable);
        } else {
            page = Page.empty(pageable);
        }

        List<ClassEntity> content = page.getContent();
        Map<Long, String> subjectCodes = new HashMap<>();
        subjectRepository.findAllById(content.stream().map(ClassEntity::getDepartmentId)
                        .filter(java.util.Objects::nonNull).distinct().toList())
                .forEach(subject -> subjectCodes.put(subject.getId(), subject.getCode()));
        List<ClassRow> rows = new ArrayList<>(content.size());
        for (int i = 0; i < content.size(); i++) {
            ClassEntity entity = content.get(i);
            rows.add(ClassRowMapper.toRow(entity, i,
                    subjectCodes.getOrDefault(entity.getDepartmentId(), "—")));
        }
        return new PageImpl<>(rows, pageable, page.getTotalElements());
    }

    /** Loads a class for editing after enforcing authorization. */
    @Transactional(readOnly = true)
    public ClassEntity getEditable(Long id, Long userId, Role role) {
        return loadEditable(id, userId, role);
    }

    /** Locks an editable class so sibling append operations share one mutex row. */
    @Transactional(propagation = Propagation.MANDATORY)
    public ClassEntity getEditableForUpdate(Long id, Long userId, Role role) {
        ClassEntity entity = classRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Lớp không tồn tại"));
        if (!isEditableBy(entity, userId, role)) {
            throw new AccessDeniedException("Bạn không có quyền chỉnh sửa lớp này");
        }
        return entity;
    }

    /**
     * Loads a class for the detail view (members, board, ...). Applies the
     * same authorization as {@link #getEditable}: LECTURER may only access
     * their own classes; LEADER may access classes in their resolved department,
     * while ADMIN may access any class. The viewable
     * and editable code paths are kept separate so a future sprint can
     * relax the read-side rule (for example, allowing students enrolled in
     * a class to read the board) without touching the edit-side rule.
     */
    @Transactional(readOnly = true)
    public ClassEntity getViewable(Long id, Long userId, Role role) {
        return loadEditable(id, userId, role);
    }

    /**
     * Creates a new class. Delegates code collision retry, required subject
     * binding and the review notification to {@link ClassCreator}.
     */
    @Transactional
    public ClassEntity create(ClassForm form, Long userId) {
        return creator.create(form, userId);
    }

    /** Updates an existing class. Authorization is enforced; writes an UPDATED activity row with a before/after diff. */
    @Transactional
    public ClassEntity update(Long id, ClassForm form, Long userId, Role role) {
        ClassEntity entity = loadEditable(id, userId, role);

        Map<String, Object> oldState = ClassRowMapper.snapshot(entity);
        entity.updateDetails(form.name(), form.description(),
                form.startDate(), form.endDate(), form.maxStudents());
        ClassEntity saved = classRepository.save(entity);

        Map<String, Object> newState = ClassRowMapper.snapshot(saved);
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("old", oldState);
        diff.put("new", newState);

        activityWriter.write(
                saved.getId(),
                ClassActivity.TYPE_UPDATED,
                "Cập nhật lớp " + saved.getName(),
                diff,
                userId
        );
        return saved;
    }

    /** Soft-deletes a class. Authorization is enforced; writes a DELETED activity row. */
    @Transactional
    public void softDelete(Long id, Long userId, Role role) {
        ClassEntity entity = loadEditable(id, userId, role);

        entity.softDelete();
        classRepository.save(entity);
        activityWriter.write(
                entity.getId(),
                ClassActivity.TYPE_DELETED,
                "Xoá lớp " + entity.getName(),
                userId
        );
    }

    // ──────────────────────── Internal ──────────────────────────────

    /**
     * Returns whether the caller is authorised to edit the given class.
     * LEADER may edit classes in their resolved department; ADMIN may edit any
     * class; LECTURER may only edit classes they own.
     */
    public boolean isEditableBy(ClassEntity clazz, Long userId, Role role) {
        if (role == null) return false;
        return accessPolicy.canAccess(clazz, userId, role);
    }

    /** Loads the class and enforces the editable-by authorisation rule. */
    private ClassEntity loadEditable(Long id, Long userId, Role role) {
        ClassEntity entity = classRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Lớp không tồn tại"));
        if (!isEditableBy(entity, userId, role)) {
            throw new AccessDeniedException("Bạn không có quyền chỉnh sửa lớp này");
        }
        return entity;
    }
}
