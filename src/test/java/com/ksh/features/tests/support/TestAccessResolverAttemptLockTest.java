package com.ksh.features.tests.support;

import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.classes.service.ClassRoleAccessPolicy;
import com.ksh.features.tests.entity.TestAttempt;
import com.ksh.features.tests.repository.TestAttemptRepository;
import com.ksh.features.tests.repository.TestRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestAccessResolverAttemptLockTest {

    @Test
    void ownedAttemptLockRefreshesPreloadedStateBeforeLifecycleMutation() {
        TestRepository tests = mock(TestRepository.class);
        TestAttemptRepository attempts = mock(TestAttemptRepository.class);
        EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
        ClassRepository classes = mock(ClassRepository.class);
        UserRepository users = mock(UserRepository.class);
        ClassRoleAccessPolicy classAccess = mock(ClassRoleAccessPolicy.class);
        EntityManager entityManager = mock(EntityManager.class);
        TestAccessResolver resolver = new TestAccessResolver(
                tests, attempts, enrollments, classes, users, classAccess, entityManager);

        TestAttempt staleManagedAttempt = new TestAttempt(21L, 42L);
        ReflectionTestUtils.setField(staleManagedAttempt, "id", 101L);
        when(attempts.findByIdAndUserIdForUpdate(101L, 42L))
                .thenReturn(Optional.of(staleManagedAttempt));
        doAnswer(invocation -> {
            staleManagedAttempt.finalizeGrade(BigDecimal.valueOf(8), BigDecimal.TEN,
                    8, 10, 120, TestAttempt.STATUS_SUBMITTED);
            return null;
        }).when(entityManager).refresh(staleManagedAttempt, LockModeType.PESSIMISTIC_WRITE);

        TestAttempt locked = resolver.requireOwnAttemptForUpdate(101L, 42L);

        assertThat(locked.getStatus()).isEqualTo(TestAttempt.STATUS_SUBMITTED);
        assertThat(locked.getScore()).isEqualByComparingTo("8");
        InOrder lockOrder = inOrder(attempts, entityManager);
        lockOrder.verify(attempts).findByIdAndUserIdForUpdate(101L, 42L);
        lockOrder.verify(entityManager)
                .refresh(staleManagedAttempt, LockModeType.PESSIMISTIC_WRITE);
    }
}
