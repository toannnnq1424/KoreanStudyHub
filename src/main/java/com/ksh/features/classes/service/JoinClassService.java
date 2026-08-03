package com.ksh.features.classes.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Enrollment;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.notifications.entity.NotificationType;
import com.ksh.features.notifications.service.NotificationService;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Catalog request, leave, and owner approval flow without invite tokens. */
@Service
public class JoinClassService {

    private final EnrollmentRepository enrollmentRepository;
    private final ClassRepository classRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ClassesService classesService;
    private final JoinAuditWriter auditWriter;

    public JoinClassService(EnrollmentRepository enrollmentRepository,
                            ClassRepository classRepository,
                            ClassActivityWriter activityWriter,
                            UserRepository userRepository,
                            NotificationService notificationService,
                            ClassesService classesService) {
        this.enrollmentRepository = enrollmentRepository;
        this.classRepository = classRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.classesService = classesService;
        this.auditWriter = new JoinAuditWriter(activityWriter);
    }

    public sealed interface JoinResult permits Success, AlreadyJoined, PendingRequested {}
    public record Success(ClassEntity clazz) implements JoinResult {}
    public record AlreadyJoined(ClassEntity clazz) implements JoinResult {}
    public record PendingRequested(ClassEntity clazz, boolean alreadyPending)
            implements JoinResult {}

    /** Creates or re-opens a PENDING request for a leader-approved ACTIVE class. */
    @Transactional
    public JoinResult requestJoin(Long classId, Long userId) {
        ClassEntity clazz = classRepository.findById(classId)
                .orElseThrow(() -> new EntityNotFoundException("Lớp không tồn tại"));
        if (!ClassEntity.STATUS_ACTIVE.equals(clazz.getStatus())) {
            throw new IllegalStateException("Lớp không mở yêu cầu tham gia");
        }
        if (clazz.getLecturerId().equals(userId)) {
            throw new AccessDeniedException("Giảng viên chủ lớp không thể gửi yêu cầu tham gia");
        }

        Optional<Enrollment> existing =
                enrollmentRepository.findByUserIdAndClassId(userId, classId);
        if (existing.isPresent()) {
            Enrollment row = existing.get();
            if (Enrollment.STATUS_ACTIVE.equals(row.getStatus())) {
                return new AlreadyJoined(clazz);
            }
            if (Enrollment.STATUS_COMPLETED.equals(row.getStatus())) {
                throw new IllegalStateException("Bạn đã hoàn thành lớp này");
            }
            if (Enrollment.STATUS_PENDING.equals(row.getStatus())) {
                return new PendingRequested(clazz, true);
            }
            enforceCapacity(clazz);
            row.markPending(Enrollment.JoinedVia.REQUEST, null);
            enrollmentRepository.save(row);
            auditWriter.writeJoin(clazz, userId, Enrollment.JoinedVia.REQUEST);
            User student = row.getUser() != null ? row.getUser()
                    : userRepository.findById(userId).orElse(null);
            if (student != null) emitJoinRequestToOwner(clazz, student);
            return new PendingRequested(clazz, false);
        }

        enforceCapacity(clazz);
        User student = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Người dùng không tồn tại"));
        Enrollment fresh = Enrollment.createPending(
                student, classId, Enrollment.JoinedVia.REQUEST, null);
        enrollmentRepository.save(fresh);
        auditWriter.writeJoin(clazz, userId, Enrollment.JoinedVia.REQUEST);
        emitJoinRequestToOwner(clazz, student);
        return new PendingRequested(clazz, false);
    }

    @Transactional
    public ClassEntity leave(Long classId, Long userId) {
        Enrollment row = enrollmentRepository.findByUserIdAndClassId(userId, classId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy enrollment"));
        if (Enrollment.STATUS_REMOVED.equals(row.getStatus())) {
            throw new EntityNotFoundException("Không tìm thấy enrollment");
        }
        if (Enrollment.STATUS_COMPLETED.equals(row.getStatus())) {
            throw new IllegalStateException("Không thể rời lớp đã hoàn thành");
        }
        ClassEntity clazz = classRepository.findById(classId)
                .orElseThrow(() -> new EntityNotFoundException("Lớp không tồn tại"));
        row.markRemoved();
        enrollmentRepository.save(row);
        auditWriter.writeLeave(clazz, userId);
        return clazz;
    }

    @Transactional
    public ClassEntity approve(Long classId, Long studentUserId, Long actorId, Role actorRole) {
        classRepository.findByIdForUpdate(classId)
                .orElseThrow(() -> new EntityNotFoundException("Lớp không tồn tại"));
        ClassEntity clazz = requireOwner(classId, actorId, actorRole);
        Enrollment row = enrollmentRepository.findByUserIdAndClassId(studentUserId, classId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy yêu cầu tham gia"));
        if (!Enrollment.STATUS_PENDING.equals(row.getStatus())) {
            throw new IllegalStateException("Yêu cầu không ở trạng thái chờ duyệt");
        }
        enforceCapacity(clazz);
        row.activateFromPending();
        enrollmentRepository.save(row);
        emitApprovedNotifications(clazz, studentUserId);
        return clazz;
    }

    @Transactional
    public ClassEntity reject(Long classId, Long studentUserId, Long actorId, Role actorRole) {
        ClassEntity clazz = requireOwner(classId, actorId, actorRole);
        Enrollment row = enrollmentRepository.findByUserIdAndClassId(studentUserId, classId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy yêu cầu tham gia"));
        if (!Enrollment.STATUS_PENDING.equals(row.getStatus())) {
            throw new IllegalStateException("Yêu cầu không ở trạng thái chờ duyệt");
        }
        row.markRejected();
        enrollmentRepository.save(row);
        emitRejectedNotification(clazz, studentUserId);
        return clazz;
    }

    private void enforceCapacity(ClassEntity clazz) {
        Integer cap = clazz.getMaxStudents();
        if (cap == null) return;
        classRepository.findByIdForUpdate(clazz.getId());
        if (enrollmentRepository.countActiveByClassIdForUpdate(clazz.getId()) >= cap) {
            throw new IllegalStateException("Lớp đã đủ sĩ số");
        }
    }

    private ClassEntity requireOwner(Long classId, Long actorId, Role actorRole) {
        ClassEntity clazz = classesService.getEditable(classId, actorId, actorRole);
        if (!clazz.getLecturerId().equals(actorId)) {
            throw new AccessDeniedException("Chỉ giảng viên chủ lớp mới được duyệt yêu cầu");
        }
        return clazz;
    }

    private void emitJoinRequestToOwner(ClassEntity clazz, User student) {
        try {
            String name = student.getFullName() != null ? student.getFullName() : student.getEmail();
            notificationService.create(clazz.getLecturerId(), "Yêu cầu tham gia lớp",
                    name + " đã gửi yêu cầu tham gia lớp \"" + clazz.getName() + "\".",
                    NotificationType.JOIN_REQUEST, NotificationType.REF_CLASS, clazz.getId());
        } catch (Exception ignored) {
            // Notification failure must not roll back enrollment state.
        }
    }

    private void emitApprovedNotifications(ClassEntity clazz, Long studentUserId) {
        try {
            notificationService.create(studentUserId, "Yêu cầu tham gia được duyệt",
                    "Bạn đã được duyệt vào lớp \"" + clazz.getName() + "\".",
                    NotificationType.JOIN_APPROVED, NotificationType.REF_CLASS, clazz.getId());
            notificationService.create(studentUserId, "Đã tham gia lớp",
                    "Bạn đã được thêm vào lớp \"" + clazz.getName() + "\".",
                    NotificationType.CLASS_ENROLLED, NotificationType.REF_CLASS, clazz.getId());
        } catch (Exception ignored) {
            // Notification failure must not roll back enrollment state.
        }
    }

    private void emitRejectedNotification(ClassEntity clazz, Long studentUserId) {
        try {
            notificationService.create(studentUserId, "Yêu cầu tham gia bị từ chối",
                    "Yêu cầu tham gia lớp \"" + clazz.getName() + "\" chưa được chấp nhận.",
                    NotificationType.JOIN_REJECTED, NotificationType.REF_CLASS, clazz.getId());
        } catch (Exception ignored) {
            // Notification failure must not roll back enrollment state.
        }
    }
}
