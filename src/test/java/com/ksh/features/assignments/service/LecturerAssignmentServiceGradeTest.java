package com.ksh.features.assignments.service;

import com.ksh.features.assignments.dto.AssignmentDtos.GradeForm;
import com.ksh.features.assignments.entity.Assignment;
import com.ksh.features.assignments.entity.AssignmentFeedback;
import com.ksh.features.assignments.entity.AssignmentStatus;
import com.ksh.features.assignments.entity.AssignmentSubmission;
import com.ksh.features.assignments.repository.AssignmentFeedbackRepository;
import com.ksh.features.assignments.repository.AssignmentRepository;
import com.ksh.features.assignments.repository.AssignmentSubmissionRepository;
import com.ksh.features.notifications.service.NotificationService;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LecturerAssignmentServiceGradeTest {

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
    void grade_withValidScore_savesFeedbackAndMarksSubmissionGraded() {
        Assignment assignment = assignment(10L, BigDecimal.valueOf(100));
        AssignmentSubmission submission = submission(50L, 10L, 200L);

        when(assignmentRepository.findByIdAndClassIdNotDeletedForUpdate(10L, 100L))
                .thenReturn(Optional.of(assignment));
        when(submissionRepository.findByIdForUpdate(50L))
                .thenReturn(Optional.of(submission));
        when(feedbackRepository.findBySubmissionId(50L))
                .thenReturn(Optional.empty());

        service.grade(
                100L,
                10L,
                50L,
                new GradeForm(BigDecimal.valueOf(85), "Good job"),
                5L,
                Role.LECTURER
        );

        verify(access).requireEditableClass(100L, 5L, Role.LECTURER);
        verify(feedbackRepository).save(any(AssignmentFeedback.class));
        assertThat(submission.getStatus()).isEqualTo(AssignmentStatus.SUB_GRADED);
        verify(submissionRepository).save(submission);
        verify(notificationService).create(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void grade_withZeroScore_savesFeedback() {
        Assignment assignment = assignment(10L, BigDecimal.valueOf(100));
        AssignmentSubmission submission = submission(50L, 10L, 200L);

        when(assignmentRepository.findByIdAndClassIdNotDeletedForUpdate(10L, 100L))
                .thenReturn(Optional.of(assignment));
        when(submissionRepository.findByIdForUpdate(50L))
                .thenReturn(Optional.of(submission));
        when(feedbackRepository.findBySubmissionId(50L))
                .thenReturn(Optional.empty());

        service.grade(
                100L,
                10L,
                50L,
                new GradeForm(BigDecimal.ZERO, "Need improvement"),
                5L,
                Role.LECTURER
        );

        assertThat(submission.getStatus()).isEqualTo(AssignmentStatus.SUB_GRADED);
        verify(feedbackRepository).save(any(AssignmentFeedback.class));
    }

    @Test
    void grade_withMaxScore_savesFeedback() {
        Assignment assignment = assignment(10L, BigDecimal.valueOf(100));
        AssignmentSubmission submission = submission(50L, 10L, 200L);

        when(assignmentRepository.findByIdAndClassIdNotDeletedForUpdate(10L, 100L))
                .thenReturn(Optional.of(assignment));
        when(submissionRepository.findByIdForUpdate(50L))
                .thenReturn(Optional.of(submission));
        when(feedbackRepository.findBySubmissionId(50L))
                .thenReturn(Optional.empty());

        service.grade(
                100L,
                10L,
                50L,
                new GradeForm(BigDecimal.valueOf(100), "Excellent"),
                5L,
                Role.LECTURER
        );

        assertThat(submission.getStatus()).isEqualTo(AssignmentStatus.SUB_GRADED);
        verify(feedbackRepository).save(any(AssignmentFeedback.class));
    }

    @Test
    void grade_withScoreGreaterThanMax_throwsIllegalArgumentException() {
        Assignment assignment = assignment(10L, BigDecimal.valueOf(100));
        AssignmentSubmission submission = submission(50L, 10L, 200L);

        when(assignmentRepository.findByIdAndClassIdNotDeletedForUpdate(10L, 100L))
                .thenReturn(Optional.of(assignment));
        when(submissionRepository.findByIdForUpdate(50L))
                .thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> service.grade(
                100L,
                10L,
                50L,
                new GradeForm(BigDecimal.valueOf(101), "Invalid"),
                5L,
                Role.LECTURER
        ))
                .isInstanceOf(IllegalArgumentException.class);

        verify(feedbackRepository, never()).save(any());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void grade_withNegativeScore_throwsIllegalArgumentException() {
        Assignment assignment = assignment(10L, BigDecimal.valueOf(100));
        AssignmentSubmission submission = submission(50L, 10L, 200L);

        when(assignmentRepository.findByIdAndClassIdNotDeletedForUpdate(10L, 100L))
                .thenReturn(Optional.of(assignment));
        when(submissionRepository.findByIdForUpdate(50L))
                .thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> service.grade(
                100L,
                10L,
                50L,
                new GradeForm(BigDecimal.valueOf(-1), "Invalid"),
                5L,
                Role.LECTURER
        ))
                .isInstanceOf(IllegalArgumentException.class);

        verify(feedbackRepository, never()).save(any());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void grade_withMissingAssignment_throwsEntityNotFoundException() {
        when(assignmentRepository.findByIdAndClassIdNotDeletedForUpdate(999L, 100L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grade(
                100L,
                999L,
                50L,
                new GradeForm(BigDecimal.valueOf(85), "Good job"),
                5L,
                Role.LECTURER
        ))
                .isInstanceOf(EntityNotFoundException.class);

        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void grade_withMissingSubmission_throwsEntityNotFoundException() {
        Assignment assignment = assignment(10L, BigDecimal.valueOf(100));

        when(assignmentRepository.findByIdAndClassIdNotDeletedForUpdate(10L, 100L))
                .thenReturn(Optional.of(assignment));
        when(submissionRepository.findByIdForUpdate(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grade(
                100L,
                10L,
                999L,
                new GradeForm(BigDecimal.valueOf(85), "Good job"),
                5L,
                Role.LECTURER
        ))
                .isInstanceOf(EntityNotFoundException.class);

        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void grade_withoutEditPermission_throwsAccessDeniedException() {
        doThrow(new AccessDeniedException("Access denied"))
                .when(access).requireEditableClass(100L, 6L, Role.LECTURER);

        assertThatThrownBy(() -> service.grade(
                100L,
                10L,
                50L,
                new GradeForm(BigDecimal.valueOf(85), "Good job"),
                6L,
                Role.LECTURER
        ))
                .isInstanceOf(AccessDeniedException.class);

        verify(assignmentRepository, never())
                .findByIdAndClassIdNotDeletedForUpdate(any(), any());
        verify(feedbackRepository, never()).save(any());
    }

    private static Assignment assignment(Long id, BigDecimal maxScore) {
        Assignment assignment = new Assignment();
        assignment.setId(id);
        assignment.setClassId(100L);
        assignment.setTitle("Assignment 1");
        assignment.setMaxScore(maxScore);
        return assignment;
    }

    private static AssignmentSubmission submission(
            Long id,
            Long assignmentId,
            Long userId
    ) {
        AssignmentSubmission submission = new AssignmentSubmission();
        submission.setId(id);
        submission.setAssignmentId(assignmentId);
        submission.setUserId(userId);
        submission.setStatus(AssignmentStatus.SUB_SUBMITTED);
        return submission;
    }
}