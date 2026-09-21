package com.ksh.features.assignments;

import com.ksh.features.assignments.dto.AssignmentDtos.SubmitForm;
import com.ksh.features.assignments.entity.Assignment;
import com.ksh.features.assignments.entity.AssignmentSubmission;
import com.ksh.features.assignments.repository.*;
import com.ksh.features.assignments.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AssignmentFileSubmissionTest {
    AssignmentRepository assignments = mock(AssignmentRepository.class);
    AssignmentSubmissionRepository submissions = mock(AssignmentSubmissionRepository.class);
    AssignmentFeedbackRepository feedback = mock(AssignmentFeedbackRepository.class);
    AssignmentAccessSupport access = mock(AssignmentAccessSupport.class);
    AssignmentAttachmentStorage files = mock(AssignmentAttachmentStorage.class);
    StudentAssignmentService service = new StudentAssignmentService(assignments, submissions, feedback, access, files);
    MockMultipartFile file = new MockMultipartFile("attachment","answer.pdf","application/pdf","%PDF-1.7".getBytes());
    @Test void permitsAttachmentWithoutText() {
        var assignment = new Assignment(); assignment.setStatus("PUBLISHED");
        when(assignments.findByIdAndClassIdNotDeletedForUpdate(2L,1L)).thenReturn(Optional.of(assignment));
        when(files.store(file,2L,9L)).thenReturn("private-key");
        service.submit(1L,2L,new SubmitForm(""),9L,file);
        verify(submissions).save(argThat(s -> "private-key".equals(s.getAttachmentUrl()) && "SUBMITTED".equals(s.getStatus())));
    }
    @Test void refusesEmptySubmission() {
        assertThatThrownBy(() -> service.submit(1L,2L,new SubmitForm(" "),9L,null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(files, submissions);
    }
    @Test void rejectsSecondSubmissionBeforeStoringFile() {
        var assignment = new Assignment(); assignment.setStatus("PUBLISHED");
        when(assignments.findByIdAndClassIdNotDeletedForUpdate(2L,1L)).thenReturn(Optional.of(assignment));
        when(submissions.findByAssignmentIdAndUserIdForUpdate(2L,9L)).thenReturn(Optional.of(new AssignmentSubmission()));
        assertThatThrownBy(() -> service.submit(1L,2L,new SubmitForm(""),9L,file)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(files);
    }
}
