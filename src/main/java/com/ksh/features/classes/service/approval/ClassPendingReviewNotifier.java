package com.ksh.features.classes.service.approval;

import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.notifications.entity.NotificationType;
import com.ksh.features.notifications.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends the subject Leader an in-app notification after class creation commits. */
@Component
public class ClassPendingReviewNotifier {

    private static final Logger log = LoggerFactory.getLogger(ClassPendingReviewNotifier.class);

    private final DepartmentRepository departmentRepository;
    private final NotificationService notificationService;

    public ClassPendingReviewNotifier(DepartmentRepository departmentRepository,
                                      NotificationService notificationService) {
        this.departmentRepository = departmentRepository;
        this.notificationService = notificationService;
    }

    /**
     * Runs after commit so a notification failure can never roll back the new
     * class or its activity record.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyLeader(ClassPendingReviewEvent event) {
        try {
            if (event.departmentId() == null) {
                return;
            }
            departmentRepository.findById(event.departmentId())
                    .map(department -> department.getLeaderUserId())
                    .filter(leaderId -> !leaderId.equals(event.lecturerId()))
                    .ifPresent(leaderId -> notificationService.create(
                            leaderId,
                            "Lớp mới chờ duyệt",
                            "Lớp \"" + event.className() + "\" thuộc mã môn "
                                    + event.subjectCode() + " đang chờ bạn duyệt.",
                            NotificationType.CLASS_PENDING_APPROVAL,
                            NotificationType.REF_CLASS,
                            event.classId()));
        } catch (RuntimeException exception) {
            log.warn("Không gửi được thông báo chờ duyệt cho lớp {}",
                    event.classId(), exception);
        }
    }
}
