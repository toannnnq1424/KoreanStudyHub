package com.ksh.features.leader.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Department;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.leader.dto.LeaderDtos.ApprovalQueueView;
import com.ksh.features.leader.dto.LeaderDtos.DepartmentSummary;
import com.ksh.features.leader.dto.LeaderDtos.PendingClassRow;
import com.ksh.features.notifications.entity.NotificationType;
import com.ksh.features.notifications.service.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LeaderClassApprovalService {
    private final LeaderDepartmentResolver resolver;
    private final ClassRepository classRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public LeaderClassApprovalService(LeaderDepartmentResolver resolver,
                                      ClassRepository classRepository,
                                      UserRepository userRepository,
                                      NotificationService notificationService) {
        this.resolver = resolver;
        this.classRepository = classRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public ApprovalQueueView load(Long leaderUserId) {
        Department department = resolver.resolve(leaderUserId).orElse(null);
        if (department == null) return new ApprovalQueueView(null, List.of(), true);
        List<ClassEntity> pending = classRepository
                .findAllByDepartmentIdAndStatusOrderByCreatedAtDesc(
                        department.getId(), ClassEntity.STATUS_DRAFT);
        Map<Long, String> names = new HashMap<>();
        pending.forEach(clazz -> userRepository.findById(clazz.getLecturerId())
                .ifPresent(user -> names.put(user.getId(), user.getFullName())));
        List<PendingClassRow> rows = pending.stream().map(clazz -> new PendingClassRow(
                clazz.getId(), clazz.getName(), department.getCode(),
                names.getOrDefault(clazz.getLecturerId(), "—"), clazz.getCreatedAt())).toList();
        return new ApprovalQueueView(new DepartmentSummary(department.getId(),
                department.getCode(), department.getName()), rows, false);
    }

    @Transactional
    public String approve(Long leaderUserId, Long classId) {
        ClassEntity clazz = loadLockedInLeaderDepartment(leaderUserId, classId);
        clazz.approve(leaderUserId, LocalDateTime.now());
        classRepository.save(clazz);
        notifyOutcome(clazz, NotificationType.CLASS_APPROVED, "Lớp đã được duyệt",
                "Lớp \"" + clazz.getName() + "\" đã được duyệt. Sinh viên có thể tham gia.");
        return clazz.getName();
    }

    @Transactional
    public String reject(Long leaderUserId, Long classId, String note) {
        ClassEntity clazz = loadLockedInLeaderDepartment(leaderUserId, classId);
        clazz.reject(leaderUserId, note, LocalDateTime.now());
        classRepository.save(clazz);
        String body = "Lớp \"" + clazz.getName() + "\" đã bị từ chối."
                + (clazz.getRejectionNote() == null ? "" : " Lý do: " + clazz.getRejectionNote());
        notifyOutcome(clazz, NotificationType.CLASS_REJECTED, "Lớp bị từ chối", body);
        return clazz.getName();
    }

    private ClassEntity loadLockedInLeaderDepartment(Long leaderUserId, Long classId) {
        Department department = resolver.resolve(leaderUserId)
                .orElseThrow(() -> new AccessDeniedException("Không có bộ môn"));
        ClassEntity clazz = classRepository.findByIdForUpdate(classId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy lớp"));
        if (!department.getId().equals(clazz.getDepartmentId())) {
            throw new AccessDeniedException("Lớp không thuộc bộ môn của bạn");
        }
        return clazz;
    }

    private void notifyOutcome(ClassEntity clazz, String type, String title, String body) {
        try {
            notificationService.create(clazz.getLecturerId(), title, body,
                    type, NotificationType.REF_CLASS, clazz.getId());
        } catch (RuntimeException ignored) {
            // Notification must never roll back a completed review transition.
        }
    }
}
