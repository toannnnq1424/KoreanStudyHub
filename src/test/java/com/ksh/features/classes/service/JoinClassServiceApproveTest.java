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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JoinClassServiceApproveTest {

    private static final Long CLASS_ID = 9L;
    private static final Long STUDENT_ID = 200L;
    private static final Long OWNER_ID = 42L;

    private EnrollmentRepository enrollmentRepository;
    private ClassRepository classRepository;
    private ClassActivityWriter activityWriter;
    private UserRepository userRepository;
    private NotificationService notificationService;
    private ClassesService classesService;
    private JoinClassService service;

    @BeforeEach
    void setUp() {
        enrollmentRepository = mock(EnrollmentRepository.class);
        classRepository = mock(ClassRepository.class);
        activityWriter = mock(ClassActivityWriter.class);
        userRepository = mock(UserRepository.class);
        notificationService = mock(NotificationService.class);
        classesService = mock(ClassesService.class);

        service = new JoinClassService(
                enrollmentRepository,
                classRepository,
                activityWriter,
                userRepository,
                notificationService,
                classesService
        );

        when(classRepository.findByIdForUpdate(CLASS_ID))
                .thenReturn(Optional.of(activeClass()));
    }

    @Test
    void approve_withPendingRequest_activatesEnrollmentAndReturnsClass() {
        ClassEntity clazz = activeClass();
        Enrollment pending = pendingEnrollment();

        when(classesService.getEditable(CLASS_ID, OWNER_ID, Role.LECTURER))
                .thenReturn(clazz);
        when(enrollmentRepository.findByUserIdAndClassId(STUDENT_ID, CLASS_ID))
                .thenReturn(Optional.of(pending));
        when(enrollmentRepository.countActiveByClassIdForUpdate(CLASS_ID))
                .thenReturn(0L);

        ClassEntity result = service.approve(
                CLASS_ID,
                STUDENT_ID,
                OWNER_ID,
                Role.LECTURER
        );

        assertThat(result).isSameAs(clazz);
        assertThat(pending.getStatus()).isEqualTo(Enrollment.STATUS_ACTIVE);
        verify(enrollmentRepository).save(pending);
        verify(notificationService).create(
                eq(STUDENT_ID),
                eq("Yêu cầu tham gia được duyệt"),
                any(),
                eq(NotificationType.JOIN_APPROVED),
                eq(NotificationType.REF_CLASS),
                eq(CLASS_ID)
        );
        verify(notificationService).create(
                eq(STUDENT_ID),
                eq("Đã tham gia lớp"),
                any(),
                eq(NotificationType.CLASS_ENROLLED),
                eq(NotificationType.REF_CLASS),
                eq(CLASS_ID)
        );
    }

    @Test
    void approve_whenActorIsNotClassOwner_throwsAccessDeniedException() {
        ClassEntity clazz = activeClass();

        when(classesService.getEditable(CLASS_ID, 99L, Role.LECTURER))
                .thenReturn(clazz);

        assertThatThrownBy(() -> service.approve(
                CLASS_ID,
                STUDENT_ID,
                99L,
                Role.LECTURER
        ))
                .isInstanceOf(AccessDeniedException.class);

        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void approve_withActiveEnrollment_throwsIllegalStateException() {
        ClassEntity clazz = activeClass();
        Enrollment active = pendingEnrollment();
        active.activateFromPending();

        when(classesService.getEditable(CLASS_ID, OWNER_ID, Role.LECTURER))
                .thenReturn(clazz);
        when(enrollmentRepository.findByUserIdAndClassId(STUDENT_ID, CLASS_ID))
                .thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.approve(
                CLASS_ID,
                STUDENT_ID,
                OWNER_ID,
                Role.LECTURER
        ))
                .isInstanceOf(IllegalStateException.class);

        verify(enrollmentRepository, never()).save(active);
    }

    @Test
    void approve_withMissingJoinRequest_throwsEntityNotFoundException() {
        ClassEntity clazz = activeClass();

        when(classesService.getEditable(CLASS_ID, OWNER_ID, Role.LECTURER))
                .thenReturn(clazz);
        when(enrollmentRepository.findByUserIdAndClassId(201L, CLASS_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(
                CLASS_ID,
                201L,
                OWNER_ID,
                Role.LECTURER
        ))
                .isInstanceOf(EntityNotFoundException.class);

        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void approve_whenClassIsFull_throwsIllegalStateExceptionAndKeepsPendingStatus() {
        ClassEntity clazz = activeClass();
        Enrollment pending = pendingEnrollment();

        when(classesService.getEditable(CLASS_ID, OWNER_ID, Role.LECTURER))
                .thenReturn(clazz);
        when(enrollmentRepository.findByUserIdAndClassId(STUDENT_ID, CLASS_ID))
                .thenReturn(Optional.of(pending));
        when(enrollmentRepository.countActiveByClassIdForUpdate(CLASS_ID))
                .thenReturn(100L);

        assertThatThrownBy(() -> service.approve(
                CLASS_ID,
                STUDENT_ID,
                OWNER_ID,
                Role.LECTURER
        ))
                .isInstanceOf(IllegalStateException.class);

        assertThat(pending.getStatus()).isEqualTo(Enrollment.STATUS_PENDING);
        verify(enrollmentRepository, never()).save(pending);
    }

    private static ClassEntity activeClass() {
        ClassEntity clazz = new ClassEntity(
                "Korean Basic 1",
                OWNER_ID,
                OWNER_ID,
                null,
                null,
                null,
                100
        );
        ReflectionTestUtils.setField(clazz, "id", CLASS_ID);
        clazz.approve(OWNER_ID, LocalDateTime.now());
        return clazz;
    }

    private static Enrollment pendingEnrollment() {
        return Enrollment.createPending(
                student(),
                CLASS_ID,
                Enrollment.JoinedVia.REQUEST,
                null
        );
    }

    private static User student() {
        User user = new User() {};
        ReflectionTestUtils.setField(user, "id", STUDENT_ID);
        ReflectionTestUtils.setField(user, "email", "student@example.com");
        ReflectionTestUtils.setField(user, "fullName", "Student User");
        ReflectionTestUtils.setField(user, "passwordHash", "encoded-password");
        return user;
    }
}