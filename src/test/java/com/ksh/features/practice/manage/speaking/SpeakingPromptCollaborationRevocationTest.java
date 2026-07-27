package com.ksh.features.practice.manage.speaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SpeakingPromptCollaborationRevocationTest {

    @Test
    void revokedCollaboratorIsDeniedBeforeDraftSourceTaskOrMediaLookup() {
        PracticeAuthorizationService authorization =
                mock(PracticeAuthorizationService.class);
        PracticeDraftRepository drafts = mock(PracticeDraftRepository.class);
        SpeakingPromptDraftAuthority authority = new SpeakingPromptDraftAuthority(
                authorization, drafts, new ObjectMapper());
        when(authorization.requireDraft(
                10L, 99L, PracticeAction.EDIT))
                .thenThrow(new AccessDeniedException(
                        "Quyền cộng tác đã bị thu hồi."));

        assertThrows(
                AccessDeniedException.class,
                () -> authority.authorizeAndLock(
                        10L, "q-speaking", 99L, PracticeAction.EDIT));

        verifyNoInteractions(drafts);
    }
}
