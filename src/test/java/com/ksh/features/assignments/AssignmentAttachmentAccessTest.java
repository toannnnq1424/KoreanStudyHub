package com.ksh.features.assignments;

import com.ksh.features.assignments.controller.AssignmentAttachmentController;
import com.ksh.features.assignments.entity.AssignmentSubmission;
import com.ksh.features.assignments.repository.AssignmentSubmissionRepository;
import com.ksh.features.assignments.service.AssignmentAccessSupport;
import com.ksh.features.assignments.service.AssignmentAttachmentStorage;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AssignmentAttachmentAccessTest {
    AssignmentSubmissionRepository repository = mock(AssignmentSubmissionRepository.class);
    AssignmentAccessSupport access = mock(AssignmentAccessSupport.class);
    AssignmentAttachmentStorage storage = mock(AssignmentAttachmentStorage.class);
    KshUserDetails user = mock(KshUserDetails.class);
    AssignmentAttachmentController controller = new AssignmentAttachmentController(repository, access, storage);
    AssignmentSubmission submission() {
        var sub = new AssignmentSubmission(); sub.setId(4L); sub.setAssignmentId(2L); sub.setUserId(9L);
        sub.setAttachmentUrl("assignment-submissions/2/9/test.pdf");
        when(repository.findById(4L)).thenReturn(Optional.of(sub)); return sub;
    }
    @Test void studentCannotDownloadAnotherStudentsFile() {
        submission(); when(user.getRole()).thenReturn(Role.STUDENT); when(user.getId()).thenReturn(8L);
        assertThat(controller.download(1L,2L,4L,user).getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(storage);
        assertThat(controller.preview(1L,2L,4L,user).getStatusCode().value()).isEqualTo(404);
    }
    @Test void lecturerMustHaveClassAccess() {
        when(user.getRole()).thenReturn(Role.LECTURER); when(user.getId()).thenReturn(8L);
        doThrow(new EntityNotFoundException()).when(access).requireEditableClass(1L,8L,Role.LECTURER);
        assertThat(controller.download(1L,2L,4L,user).getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(repository, storage);
    }
    @Test void assignmentScopeCannotBeSubstituted() {
        submission(); when(user.getRole()).thenReturn(Role.LEADER); when(user.getId()).thenReturn(8L);
        assertThat(controller.download(1L,3L,4L,user).getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(storage);
    }
    @Test void ownerDownloadIsPrivateAndForcedAttachment() throws Exception {
        var sub = submission(); when(user.getRole()).thenReturn(Role.STUDENT); when(user.getId()).thenReturn(9L);
        when(storage.read(sub.getAttachmentUrl(),2L,9L)).thenReturn(new byte[]{1});
        var response = controller.download(1L,2L,4L,user);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
        assertThat(controller.preview(1L,2L,4L,user).getHeaders().getContentDisposition().getType()).isEqualTo("inline");
    }
}
