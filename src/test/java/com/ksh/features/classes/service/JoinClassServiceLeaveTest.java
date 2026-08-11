package com.ksh.features.classes.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Enrollment;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.notifications.service.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JoinClassServiceLeaveTest {

    private final EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
    private final ClassRepository classRepository = mock(ClassRepository.class);
    private final ClassActivityWriter activityWriter = mock(ClassActivityWriter.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final ClassesService classesService = mock(ClassesService.class);

    private final JoinClassService service = new JoinClassService(
            enrollmentRepository,
            classRepository,
            activityWriter,
            userRepository,
            notificationService,
            classesService
    );

    @Test
    void leave_withActiveEnrollment_marksRemovedAndReturnsClass() {
        Enrollment enrollment = activeEnrollment(10L, 3L);
        ClassEntity clazz = mock(ClassEntity.class);

        when(enrollmentRepository.findByUserIdAndClassId(10L, 3L))
                .thenReturn(Optional.of(enrollment));
        when(classRepository.findById(3L)).thenReturn(Optional.of(clazz));

        ClassEntity result = service.leave(3L, 10L);

        assertThat(result).isSameAs(clazz);
        assertThat(enrollment.getStatus()).isEqualTo(Enrollment.STATUS_REMOVED);
        verify(enrollmentRepository).save(enrollment);
    }

    @Test
    void leave_withMissingEnrollment_throwsEntityNotFoundException() {
        when(enrollmentRepository.findByUserIdAndClassId(10L, 3L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.leave(3L, 10L))
                .isInstanceOf(EntityNotFoundException.class);

        verify(enrollmentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void leave_withCompletedEnrollment_throwsIllegalStateException() {
        Enrollment enrollment = activeEnrollment(10L, 3L);
        ReflectionTestUtils.setField(enrollment, "status", Enrollment.STATUS_COMPLETED);

        when(enrollmentRepository.findByUserIdAndClassId(10L, 3L))
                .thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.leave(3L, 10L))
                .isInstanceOf(IllegalStateException.class);

        verify(enrollmentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static Enrollment activeEnrollment(Long userId, Long classId) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        return new Enrollment(user, classId, Enrollment.JoinedVia.MANUAL.name(), null);
    }
}
