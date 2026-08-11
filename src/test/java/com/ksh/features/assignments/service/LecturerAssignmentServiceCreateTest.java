package com.ksh.features.assignments.service;

import com.ksh.features.assignments.dto.AssignmentDtos.AssignmentForm;
import com.ksh.features.assignments.entity.Assignment;
import com.ksh.features.assignments.entity.AssignmentStatus;
import com.ksh.features.assignments.repository.AssignmentFeedbackRepository;
import com.ksh.features.assignments.repository.AssignmentRepository;
import com.ksh.features.assignments.repository.AssignmentSubmissionRepository;
import com.ksh.features.notifications.service.NotificationService;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LecturerAssignmentServiceCreateTest {

    private final AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    private final AssignmentSubmissionRepository submissionRepository =
            mock(AssignmentSubmissionRepository.class);
    private final AssignmentFeedbackRepository feedbackRepository =
            mock(AssignmentFeedbackRepository.class);
    private final AssignmentAccessSupport access = mock(AssignmentAccessSupport.class);
    private final NotificationService notificationService = mock(NotificationService.class);

    private final LecturerAssignmentService service =
            new LecturerAssignmentService(
                    assignmentRepository,
                    submissionRepository,
                    feedbackRepository,
                    access,
                    notificationService
            );

    @Test
    void create_withValidInput_savesDraftAssignmentAndReturnsId() {
        AssignmentForm form = new AssignmentForm(
                null,
                "Assignment 1",
                "Write a short essay",
                BigDecimal.valueOf(100),
                LocalDateTime.of(2026, 12, 31, 23, 59),
                false
        );

        when(assignmentRepository.save(any(Assignment.class)))
                .thenAnswer(invocation -> {
                    Assignment assignment = invocation.getArgument(0);
                    ReflectionTestUtils.setField(assignment, "id", 10L);
                    return assignment;
                });

        Long result = service.create(100L, form, 5L, Role.LECTURER);

        assertThat(result).isEqualTo(10L);
        verify(access).requireEditableClass(100L, 5L, Role.LECTURER);
        verify(access).validateForm(form);
        verify(access).applyForm(any(Assignment.class), org.mockito.Mockito.eq(form));
    }

    @Test
    void create_withZeroMaxScoreAndNoDueDate_savesAssignment() {
        AssignmentForm form = new AssignmentForm(
                null,
                "Assignment 1",
                "Write a short essay",
                BigDecimal.ZERO,
                null,
                true
        );

        when(assignmentRepository.save(any(Assignment.class)))
                .thenAnswer(invocation -> {
                    Assignment assignment = invocation.getArgument(0);
                    ReflectionTestUtils.setField(assignment, "id", 11L);
                    return assignment;
                });

        Long result = service.create(100L, form, 5L, Role.LECTURER);

        assertThat(result).isEqualTo(11L);
        verify(access).validateForm(form);
        verify(access).applyForm(any(Assignment.class), org.mockito.Mockito.eq(form));
    }

    @Test
    void create_withBlankTitle_throwsIllegalArgumentException() {
        AssignmentForm form = new AssignmentForm(
                null,
                "",
                "Write a short essay",
                BigDecimal.valueOf(100),
                null,
                false
        );

        doThrow(new IllegalArgumentException("Invalid assignment title"))
                .when(access).validateForm(form);

        assertThatThrownBy(() -> service.create(100L, form, 5L, Role.LECTURER))
                .isInstanceOf(IllegalArgumentException.class);

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void create_withNegativeMaxScore_throwsIllegalArgumentException() {
        AssignmentForm form = new AssignmentForm(
                null,
                "Assignment 1",
                "Write a short essay",
                BigDecimal.valueOf(-1),
                null,
                false
        );

        doThrow(new IllegalArgumentException("Invalid max score"))
                .when(access).validateForm(form);

        assertThatThrownBy(() -> service.create(100L, form, 5L, Role.LECTURER))
                .isInstanceOf(IllegalArgumentException.class);

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void create_withoutEditPermission_throwsAccessDeniedException() {
        AssignmentForm form = new AssignmentForm(
                null,
                "Assignment 1",
                "Write a short essay",
                BigDecimal.valueOf(100),
                null,
                false
        );

        doThrow(new AccessDeniedException("Access denied"))
                .when(access).requireEditableClass(100L, 6L, Role.LECTURER);

        assertThatThrownBy(() -> service.create(100L, form, 6L, Role.LECTURER))
                .isInstanceOf(AccessDeniedException.class);

        verify(assignmentRepository, never()).save(any());
    }
}