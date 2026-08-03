package com.ksh.features.admin.departments.service;

import com.ksh.entities.Department;
import com.ksh.entities.User;
import com.ksh.features.admin.departments.dto.SubjectActivityRow;
import com.ksh.features.admin.departments.dto.DepartmentDtos.DepartmentFilter;
import com.ksh.features.admin.departments.dto.DepartmentDtos.DepartmentForm;
import com.ksh.features.admin.departments.dto.DepartmentDtos.DepartmentOption;
import com.ksh.features.admin.departments.dto.DepartmentDtos.DepartmentRow;
import com.ksh.features.admin.departments.dto.DepartmentDtos.LeaderCandidate;
import com.ksh.features.admin.departments.repository.SubjectActivityRepository;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.security.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Read-only department queries for admin list/form screens.
 * Mutations live on {@link DepartmentService}.
 */
@Service
public class DepartmentQueryService {

    static final Set<Role> LEADER_ELIGIBLE = Set.of(Role.LECTURER, Role.LEADER);

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final SubjectActivityRepository activityRepository;

    public DepartmentQueryService(DepartmentRepository departmentRepository,
                                  UserRepository userRepository,
                                  SubjectActivityRepository activityRepository) {
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
    }

    @Transactional(readOnly = true)
    public List<DepartmentRow> list() {
        return list(DepartmentFilter.empty());
    }

    /**
     * Lists departments with optional search / status / sort applied in-memory.
     * Department volume is small, so a full load keeps the path simple.
     */
    @Transactional(readOnly = true)
    public List<DepartmentRow> list(DepartmentFilter filter) {
        DepartmentFilter f = filter == null ? DepartmentFilter.empty() : filter;
        List<Department> departments = departmentRepository.findAllByOrderByNameAsc();
        Map<Long, String> leaderNames = loadLeaderNames(departments);
        List<DepartmentRow> rows = new ArrayList<>(departments.size());
        for (Department d : departments) {
            if (!matchesQuery(d, f.q()) || !matchesStatus(d, f.status())) {
                continue;
            }
            String leaderLabel = d.getLeaderUserId() == null
                    ? null
                    : leaderNames.getOrDefault(d.getLeaderUserId(), "—");
            rows.add(new DepartmentRow(
                    d.getId(), d.getCode(), d.getName(), d.getDescription(),
                    d.isActive(), d.getLeaderUserId(), leaderLabel, d.getCreatedAt()));
        }
        rows.sort(comparatorFor(f.sort()));
        return rows;
    }

    @Transactional(readOnly = true)
    public List<DepartmentOption> options() {
        return departmentRepository.findAllByOrderByNameAsc().stream()
                .map(d -> new DepartmentOption(d.getId(), d.getCode(), d.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DepartmentOption> activeOptions() {
        return departmentRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(d -> new DepartmentOption(d.getId(), d.getCode(), d.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DepartmentForm loadForm(Long id) {
        Department d = departmentRepository.findById(id).orElse(null);
        if (d == null) {
            return null;
        }
        return new DepartmentForm(
                d.getName(), d.getCode(), d.getDescription(),
                d.isActive(), d.getLeaderUserId());
    }

    /** Active LECTURER/LEADER users eligible to become department leader. */
    @Transactional(readOnly = true)
    public List<LeaderCandidate> leaderCandidates() {
        return userRepository.findByRoleInAndActiveTrueOrderByFullNameAsc(LEADER_ELIGIBLE).stream()
                .map(u -> new LeaderCandidate(
                        u.getId(), u.getFullName(), u.getEmail(), u.getRole().name()))
                .toList();
    }

    /** Paged audit history for the subject detail history tab. */
    @Transactional(readOnly = true)
    public Page<SubjectActivityRow> listActivities(Long subjectId, Pageable pageable) {
        return activityRepository.findActivitiesForSubject(subjectId, pageable);
    }

    /** Batch-loads full names for distinct non-null leader user ids. */
    private Map<Long, String> loadLeaderNames(List<Department> departments) {
        List<Long> ids = departments.stream()
                .map(Department::getLeaderUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (User u : userRepository.findAllById(ids)) {
            names.put(u.getId(), u.getFullName());
        }
        return names;
    }

    private static boolean matchesQuery(Department d, String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String needle = q.trim().toLowerCase(Locale.ROOT);
        String name = d.getName() == null ? "" : d.getName().toLowerCase(Locale.ROOT);
        String code = d.getCode() == null ? "" : d.getCode().toLowerCase(Locale.ROOT);
        return name.contains(needle) || code.contains(needle);
    }

    private static boolean matchesStatus(Department d, String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        // Whitelist: only active / inactive filter values are meaningful.
        if ("active".equalsIgnoreCase(status)) {
            return d.isActive();
        }
        if ("inactive".equalsIgnoreCase(status)) {
            return !d.isActive();
        }
        return true;
    }

    private static Comparator<DepartmentRow> comparatorFor(String sort) {
        String key = (sort == null || sort.isBlank()) ? "name,asc" : sort;
        return switch (key) {
            case "name,desc" -> Comparator.comparing(
                    DepartmentRow::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)).reversed();
            case "code,asc" -> Comparator.comparing(
                    DepartmentRow::code, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "code,desc" -> Comparator.comparing(
                    DepartmentRow::code, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)).reversed();
            case "createdAt,asc" -> Comparator.comparing(
                    DepartmentRow::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "createdAt,desc" -> Comparator.comparing(
                    DepartmentRow::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
            default -> Comparator.comparing(
                    DepartmentRow::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        };
    }
}
