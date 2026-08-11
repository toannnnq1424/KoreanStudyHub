package com.ksh.features.assignments.service;

import com.ksh.features.assignments.dto.AssignmentDtos.SubmitForm;
import com.ksh.features.assignments.entity.Assignment;
import com.ksh.features.assignments.entity.AssignmentStatus;
import com.ksh.features.assignments.entity.AssignmentSubmission;
import com.ksh.features.assignments.repository.AssignmentFeedbackRepository;
import com.ksh.features.assignments.repository.AssignmentRepository;
import com.ksh.features.assignments.repository.AssignmentSubmissionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentAssignmentServiceSubmitTest {

    private final AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    private final AssignmentSubmissionRepository submissionRepository =
            mock(AssignmentSubmissionRepository.class);
    private final AssignmentFeedbackRepository feedbackRepository =
            mock(AssignmentFeedbackRepository.class);
    private final AssignmentAccessSupport access = mock(AssignmentAccessSupport.class);

    private final StudentAssignmentService service =
            new StudentAssignmentService(
                    assignmentRepository,
                    submissionRepository,
                    feedbackRepository,
                    access
            );

    @Test
    void submit_withPublishedAssignment_savesSubmittedNotLateSubmission() {
        Assignment assignment = assignment(10L, AssignmentStatus.PUBLISHED,
                LocalDateTime.now().plusDays(1), false);

        when(assignmentRepository.findByIdAndClassIdNotDeletedForUpdate(10L, 100L))
                .thenReturn(Optional.of(assignment));
        when(submissionRepository.findByAssignmentIdAndUserIdForUpdate(10L, 200L))
                .thenReturn(Optional.empty());

        service.submit(100L, 10L, new SubmitForm("My answer"), 200L);

        verify(access).requireActiveEnrollment(100L, 200L);
        verify(submissionRepository).save(any(AssignmentSubmission.class));
    }

    @Test
    void submit_withExistingSubmittedSubmission_updatesExistingSubmission() {
        Assignment assignment = assignment(10L, AssignmentStatus.PUBLISHED,
                LocalDateTime.now().plusDays(1), false);
        AssignmentSubmission existing = submission(
                10L,
                200L,
                "Old answer",
                AssignmentStatus.SUB_SUBMITTED,
                false
        );

        when(assignmentRepository.findByIdAndClassIdNotDeletedForUpdate(10L, 100L))
                .thenReturn(Optional.of(assignment));
        when(submissionRepository.findByAssignmentIdAndUserIdForUpdate(10L, 200L))
                .thenReturn(Optional.of(existing));

        service.submit(100L, 10L, new SubmitForm("Updated answer"), 200L);

        assertThat(existing.getContent()).isEqualTo("Updated answer");
        assertThat(existing.getStatus()).isEqualTo(AssignmentStatus.SUB_SUBMITTED);
        assertThat(existing.isLate()).isFalse();
        verify(submissionRepository).save(existing);
    }

    @Test
    void submit_lateAllowed_savesLateSubmission() {
        Assignment assignment = assignment(10L, AssignmentStatus.PUBLISHED,
                LocalDateTime.now().minusDays(1), true);

        when(assignmentRepository.findByIdAndClassIdNotDeletedForUpdate(10L, 100L))
                .thenReturn(Optional.of(assignment));
        when(submissionRepository.findByAssignmentIdAndUserIdForUpdate(10L, 200L))
                .thenReturn(Optional.empty());

        service.submit(100L, 10L, new SubmitForm("Late answer"), 200L);

        verify(submissionRepository).save(org.mockito.ArgumentMatchers.argThat(sub ->
                sub.isLate()
                        && sub.getAssignmentId().equals(10L)
                        && sub.getUserId().equals(200L)
                        && "Late answer".equals(sub.getContent())
        ));
    }

    @Test
    void submit_lateNotAllowed_throwsIllegalArgumentException() {
        Assignment assignment = assignment(10L, AssignmentStatus.PUBLISHED,
                LocalDateTime.now().minusDays(1), false);

        when(assignmentRepository.findByIdAndClassIdNotDeletedForUpdate(10L, 100L))
                .thenReturn(Optional.of(assignment));
        when(submissionRepository.findByAssignmentIdAndUserIdForUpdate(10L, 200L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(
                100L,
                10L,
                new SubmitForm("Late answer"),
                200L
        ))
                .isInstanceOf(IllegalArgumentException.class);

        verify(submissionRepository, never()).save(any());
    }

    @Test
    void submit_afterGraded_throwsIllegalArgumentException() {
        Assignment assignment = assignment(10L, AssignmentStatus.PUBLISHED,
                LocalDateTime.now().plusDays(1), false);
        AssignmentSubmission graded = submission(
                10L,
                200L,
                "Graded answer",
                AssignmentStatus.SUB_GRADED,
                false
        );

        when(assignmentRepository.findByIdAndClassIdNotDeletedForUpdate(10L, 100L))
                .thenReturn(Optional.of(assignment));
        when(submissionRepository.findByAssignmentIdAndUserIdForUpdate(10L, 200L))
                .thenReturn(Optional.of(graded));

        assertThatThrownBy(() -> service.submit(
                100L,
                10L,
                new SubmitForm("Resubmit after graded"),
                200L
        ))
                .isInstanceOf(IllegalArgumentException.class);

        verify(submissionRepository, never()).save(any());
    }

    @Test
    void submit_withMissingAssignment_throwsEntityNotFoundException() {
        when(assignmentRepository.findByIdAndClassIdNotDeletedForUpdate(99L, 100L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(
                100L,
                99L,
                new SubmitForm("My answer"),
                200L
        ))
                .isInstanceOf(EntityNotFoundException.class);

        verify(submissionRepository, never()).save(any());
    }

    @Test
    void submit_withDraftAssignment_throwsIllegalStateException() {
        Assignment assignment = assignment(10L, AssignmentStatus.DRAFT,
                LocalDateTime.now().plusDays(1), false);

        when(assignmentRepository.findByIdAndClassIdNotDeletedForUpdate(10L, 100L))
                .thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.submit(
                100L,
                10L,
                new SubmitForm("My answer"),
                200L
        ))
                .isInstanceOf(IllegalStateException.class);

        verify(submissionRepository, never()).save(any());
    }

    private static Assignment assignment(
            Long id,
            String status,
            LocalDateTime dueDate,
            boolean allowLate
    ) {
        Assignment assignment = new Assignment();
        assignment.setId(id);
        assignment.setClassId(100L);
        assignment.setTitle("Assignment 1");
        assignment.setDescription("Write a short essay");
        assignment.setStatus(status);
        assignment.setDueDate(dueDate);
        assignment.setAllowLateSubmission(allowLate);
        return assignment;
    }

    private static AssignmentSubmission submission(
            Long assignmentId,
            Long userId,
            String content,
            String status,
            boolean late
    ) {
        AssignmentSubmission submission = new AssignmentSubmission();
        submission.setAssignmentId(assignmentId);
        submission.setUserId(userId);
        submission.setContent(content);
        submission.setStatus(status);
        submission.setLate(late);
        submission.setSubmittedAt(LocalDateTime.now());
        return submission;
    }
}