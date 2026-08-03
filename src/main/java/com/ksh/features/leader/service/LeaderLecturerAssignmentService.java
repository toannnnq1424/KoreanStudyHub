package com.ksh.features.leader.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.ClassCoLecturer;
import com.ksh.entities.Department;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.ClassCoLecturerRepository;
import com.ksh.features.leader.dto.LeaderDtos.AssignClassRow;
import com.ksh.features.leader.dto.LeaderDtos.AssignView;
import com.ksh.features.leader.dto.LeaderDtos.DepartmentSummary;
import com.ksh.features.leader.dto.LeaderDtos.LecturerOption;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lists subject classes and adds co-lecturers without transferring ownership.
 */
@Service
public class LeaderLecturerAssignmentService {

    private static final Set<Role> ELIGIBLE = Set.of(Role.LECTURER, Role.LEADER);

    private final LeaderDepartmentResolver resolver;
    private final ClassRepository classRepository;
    private final UserRepository userRepository;
    private final ClassCoLecturerRepository coLecturerRepository;

    public LeaderLecturerAssignmentService(LeaderDepartmentResolver resolver,
                                         ClassRepository classRepository,
                                         UserRepository userRepository,
                                         ClassCoLecturerRepository coLecturerRepository) {
        this.resolver = resolver;
        this.classRepository = classRepository;
        this.userRepository = userRepository;
        this.coLecturerRepository = coLecturerRepository;
    }

    @Transactional(readOnly = true)
    public AssignView load(Long leaderUserId) {
        Optional<Department> deptOpt = resolver.resolve(leaderUserId);
        if (deptOpt.isEmpty()) {
            return new AssignView(null, List.of(), List.of(), true);
        }
        Department dept = deptOpt.get();
        List<ClassEntity> classes =
                classRepository.findAllByDepartmentIdOrderByCreatedAtDesc(dept.getId());
        Map<Long, String> names = loadNames(classes);
        Map<Long, List<String>> coLecturerNames = loadCoLecturerNames(classes);
        List<AssignClassRow> rows = new ArrayList<>(classes.size());
        for (ClassEntity c : classes) {
            rows.add(new AssignClassRow(
                    c.getId(), c.getName(), dept.getCode(),
                    c.getLecturerId(),
                    names.getOrDefault(c.getLecturerId(), "—"),
                    coLecturerNames.getOrDefault(c.getId(), List.of())));
        }
        List<LecturerOption> lecturers = departmentLecturers(dept.getId());
        return new AssignView(
                new DepartmentSummary(dept.getId(), dept.getCode(), dept.getName()),
                rows, lecturers, false);
    }

    /**
     * Adds a co-lecturer without changing the owning lecturer or creator.
     *
     * @return class display name for success toast
     */
    @Transactional
    public String assignCoLecturer(Long leaderUserId, Long classId, Long newLecturerId) {
        Department dept = resolver.resolve(leaderUserId)
                .orElseThrow(() -> new AccessDeniedException("Không có bộ môn"));
        ClassEntity clazz = classRepository.findById(classId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy lớp"));
        if (clazz.getDepartmentId() == null
                || !clazz.getDepartmentId().equals(dept.getId())) {
            throw new AccessDeniedException("Lớp không thuộc bộ môn của bạn");
        }
        User lecturer = userRepository.findById(newLecturerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy giảng viên"));
        if (!lecturer.isActive() || lecturer.isDeleted()
                || !ELIGIBLE.contains(lecturer.getRole())
                || lecturer.getDepartmentId() == null
                || !lecturer.getDepartmentId().equals(dept.getId())) {
            throw new IllegalArgumentException(
                    "Giảng viên phải thuộc cùng bộ môn và đang hoạt động");
        }
        if (newLecturerId.equals(clazz.getLecturerId())) {
            throw new IllegalArgumentException("Giảng viên này đang là chủ lớp");
        }
        if (!coLecturerRepository.existsByClassIdAndLecturerId(classId, newLecturerId)) {
            coLecturerRepository.save(new ClassCoLecturer(classId, newLecturerId, leaderUserId));
        }
        return clazz.getName();
    }

    private Map<Long, List<String>> loadCoLecturerNames(List<ClassEntity> classes) {
        if (classes.isEmpty()) {
            return Map.of();
        }
        List<ClassCoLecturer> assignments = coLecturerRepository.findAllByClassIdIn(
                classes.stream().map(ClassEntity::getId).toList());
        Map<Long, String> userNames = new HashMap<>();
        for (User user : userRepository.findAllById(
                assignments.stream().map(ClassCoLecturer::getLecturerId).distinct().toList())) {
            userNames.put(user.getId(), user.getFullName());
        }
        Map<Long, List<String>> result = new HashMap<>();
        for (ClassCoLecturer assignment : assignments) {
            result.computeIfAbsent(assignment.getClassId(), ignored -> new ArrayList<>())
                    .add(userNames.getOrDefault(assignment.getLecturerId(), "—"));
        }
        return result;
    }

    private List<LecturerOption> departmentLecturers(Long departmentId) {
        return userRepository.findAll().stream()
                .filter(u -> u.isActive() && !u.isDeleted())
                .filter(u -> ELIGIBLE.contains(u.getRole()))
                .filter(u -> departmentId.equals(u.getDepartmentId()))
                .sorted((a, b) -> a.getFullName().compareToIgnoreCase(b.getFullName()))
                .map(u -> new LecturerOption(u.getId(), u.getFullName(), u.getEmail()))
                .toList();
    }

    private Map<Long, String> loadNames(List<ClassEntity> classes) {
        Map<Long, String> names = new HashMap<>();
        for (ClassEntity c : classes) {
            if (c.getLecturerId() != null && !names.containsKey(c.getLecturerId())) {
                userRepository.findById(c.getLecturerId())
                        .ifPresent(u -> names.put(u.getId(), u.getFullName()));
            }
        }
        return names;
    }
}
