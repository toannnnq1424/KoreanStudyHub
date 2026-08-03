package com.ksh.features.classes.service.approval;

import com.ksh.entities.Department;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.notifications.entity.NotificationType;
import com.ksh.features.notifications.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ClassPendingReviewNotifierTest {

    @Test
    void notifiesAssignedLeaderWithKshLifecycleType() {
        DepartmentRepository departments = mock(DepartmentRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        Department department = new Department("Korean", "KOR", null, true);
        department.assignLeader(9L);
        when(departments.findById(3L)).thenReturn(Optional.of(department));
        ClassPendingReviewNotifier notifier =
                new ClassPendingReviewNotifier(departments, notifications);

        notifier.notifyLeader(new ClassPendingReviewEvent(11L, 3L, 7L, "TOPIK 3", "K3ABC"));

        verify(notifications).create(
                9L,
                "Lớp mới chờ duyệt",
                "Lớp \"TOPIK 3\" thuộc mã môn K3ABC đang chờ bạn duyệt.",
                NotificationType.CLASS_PENDING_APPROVAL,
                NotificationType.REF_CLASS,
                11L);
    }

    @Test
    void skipsNotificationWhenCreatorIsTheLeader() {
        DepartmentRepository departments = mock(DepartmentRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        Department department = new Department("Korean", "KOR", null, true);
        department.assignLeader(7L);
        when(departments.findById(3L)).thenReturn(Optional.of(department));
        ClassPendingReviewNotifier notifier =
                new ClassPendingReviewNotifier(departments, notifications);

        notifier.notifyLeader(new ClassPendingReviewEvent(11L, 3L, 7L, "TOPIK 3", "K3ABC"));

        verifyNoInteractions(notifications);
    }
}
