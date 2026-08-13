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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LecturerAssignmentLifecycleConcurrencyContractTest {

    private static final Long CLASS_ID = 10L;
    private static final Long ASSIGNMENT_ID = 20L;
    private static final Long LECTURER_ID = 30L;

    @Mock private AssignmentRepository assignmentRepository;
    @Mock private AssignmentSubmissionRepository submissionRepository;
    @Mock private AssignmentFeedbackRepository feedbackRepository;
    @Mock private AssignmentAccessSupport access;
    @Mock private NotificationService notificationService;

    @Test
    void staleDraftEditRechecksLockedPublishedStateBeforeWriting() {
        Assignment staleDraft = assignment(AssignmentStatus.DRAFT);
        Assignment lockedCurrent = assignment(AssignmentStatus.PUBLISHED);
        stubUnlockedStateUsedByTheVulnerableImplementation(staleDraft);
        when(assignmentRepository.findByIdAndClassIdNotDeletedForUpdate(
                ASSIGNMENT_ID, CLASS_ID)).thenReturn(Optional.of(lockedCurrent));
        AssignmentForm form = new AssignmentForm(
                ASSIGNMENT_ID, "Stale edit", "Stale description",
                BigDecimal.TEN, null, false);

        assertThatThrownBy(() -> service().update(
                CLASS_ID, ASSIGNMENT_ID, form, LECTURER_ID, Role.LECTURER))
                .isInstanceOf(IllegalStateException.class);

        verify(access, never()).applyForm(lockedCurrent, form);
        verify(assignmentRepository, never()).save(lockedCurrent);
    }

    @Test
    void duplicatePublishRechecksLockedPublishedStateBeforeFanOut() {
        Assignment staleDraft = assignment(AssignmentStatus.DRAFT);
        Assignment lockedCurrent = assignment(AssignmentStatus.PUBLISHED);
        stubUnlockedStateUsedByTheVulnerableImplementation(staleDraft);
        when(assignmentRepository.findByIdAndClassIdNotDeletedForUpdate(
                ASSIGNMENT_ID, CLASS_ID)).thenReturn(Optional.of(lockedCurrent));

        assertThatThrownBy(() -> service().publish(
                CLASS_ID, ASSIGNMENT_ID, LECTURER_ID, Role.LECTURER))
                .isInstanceOf(IllegalStateException.class);

        verify(assignmentRepository, never()).save(lockedCurrent);
        verify(notificationService, never()).create(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void duplicateCloseRechecksLockedClosedStateBeforeWriting() {
        Assignment stalePublished = assignment(AssignmentStatus.PUBLISHED);
        Assignment lockedCurrent = assignment(AssignmentStatus.CLOSED);
        stubUnlockedStateUsedByTheVulnerableImplementation(stalePublished);
        when(assignmentRepository.findByIdAndClassIdNotDeletedForUpdate(
                ASSIGNMENT_ID, CLASS_ID)).thenReturn(Optional.of(lockedCurrent));

        assertThatThrownBy(() -> service().close(
                CLASS_ID, ASSIGNMENT_ID, LECTURER_ID, Role.LECTURER))
                .isInstanceOf(IllegalStateException.class);

        verify(assignmentRepository, never()).save(lockedCurrent);
    }

    private void stubUnlockedStateUsedByTheVulnerableImplementation(Assignment staleState) {
        // Lenient by design: the fixed implementation must not use this path.
        lenient().when(access.requireAssignment(CLASS_ID, ASSIGNMENT_ID))
                .thenReturn(staleState);
    }

    private LecturerAssignmentService service() {
        return new LecturerAssignmentService(
                assignmentRepository,
                submissionRepository,
                feedbackRepository,
                access,
                notificationService);
    }

    private static Assignment assignment(String status) {
        Assignment assignment = new Assignment();
        assignment.setId(ASSIGNMENT_ID);
        assignment.setClassId(CLASS_ID);
        assignment.setStatus(status);
        assignment.setTitle("Assignment");
        return assignment;
    }
}
