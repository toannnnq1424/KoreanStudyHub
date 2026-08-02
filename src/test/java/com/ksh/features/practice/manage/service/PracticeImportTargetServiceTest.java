package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeImportTargetServiceTest {

    private final PracticeDraftRepository drafts = mock(PracticeDraftRepository.class);
    private final PracticeAuthorizationService authorization =
            mock(PracticeAuthorizationService.class);
    private final AssessmentAuthoringCatalogService catalog =
            mock(AssessmentAuthoringCatalogService.class, RETURNS_DEEP_STUBS);
    private final PracticeImportTargetService service = new PracticeImportTargetService(
            drafts, authorization, catalog, new ObjectMapper());

    @Test
    void exactOwnedSectionBecomesImmutableCandidateTarget() {
        when(drafts.findById(91L)).thenReturn(Optional.of(draft()));

        TargetRoute target = service.requireExactTarget(
                91L, 2, "WRITING", "W2", 7L);

        assertThat(target).isEqualTo(new TargetRoute(
                91L, 2, "WRITING", "W2"));
        verify(authorization).requireDraft(91L, 7L, PracticeAction.EDIT);
    }

    @Test
    void mismatchedSkillFailsAfterExactDraftAuthorization() {
        when(drafts.findById(91L)).thenReturn(Optional.of(draft()));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.requireExactTarget(
                        91L, 2, "READING", "W2", 7L));

        assertThat(failure.getMessage()).contains("không khớp");
        verify(authorization).requireDraft(91L, 7L, PracticeAction.EDIT);
    }

    @Test
    void staleRequestedSectionNeverFallsBackToTheFirstDraftSection() {
        when(drafts.findById(91L)).thenReturn(Optional.of(draft()));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.resolveStartContext(
                        91L, 99, "READING", "R99", 7L));

        assertThat(failure.getMessage()).contains("không khớp");
        verify(authorization).requireDraft(91L, 7L, PracticeAction.EDIT);
    }

    private static PracticeDraft draft() {
        return new PracticeDraft(
                "Draft", "", "GLOBAL", null, "DRAFT", 7L,
                """
                {"sections":[
                  {"testNo":1,"lessonCode":"R1","skill":"READING","title":"Đọc"},
                  {"testNo":2,"lessonCode":"W2","skill":"WRITING","title":"Viết"}
                ]}
                """);
    }
}
