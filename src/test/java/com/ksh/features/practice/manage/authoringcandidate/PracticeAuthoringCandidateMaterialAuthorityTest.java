package com.ksh.features.practice.manage.authoringcandidate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.features.practice.manage.service.PracticeMaterialReferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PracticeAuthoringCandidateMaterialAuthorityTest {

    private PracticeMaterialReferenceService referenceService;
    private PracticeAuthoringCandidateMaterialAuthority authority;

    @BeforeEach
    void setUp() {
        referenceService = mock(PracticeMaterialReferenceService.class);
        authority = new PracticeAuthoringCandidateMaterialAuthority(
                referenceService, new ObjectMapper());
    }

    @Test
    void acceptsOnlyMaterialAlreadyLinkedToExactDraft() {
        PracticeMaterialReference reference =
                PracticeMaterialReference.draft(7L, 5001L, "EXCEL_MEDIA");
        when(referenceService.referencesForDraft(5001L))
                .thenReturn(List.of(reference));

        authority.requireAuthorized(5001L, """
                {"imageReference":"/practice/materials/7/content"}
                """);
    }

    @Test
    void rejectsManagedMaterialFromAnotherAuthority() {
        when(referenceService.referencesForDraft(5001L)).thenReturn(List.of());

        assertThatThrownBy(() -> authority.requireAuthorized(5001L, """
                {"imageReference":"/practice/materials/8/content"}
                """))
                .isInstanceOf(PracticeAuthoringCandidateException.class)
                .extracting("code")
                .isEqualTo("CANDIDATE_MATERIAL_AUTHORITY_INVALID");
    }

    @Test
    void rejectsPrivateSpeakingRouteForAnotherDraft() {
        when(referenceService.referencesForDraft(5001L)).thenReturn(List.of());

        assertThatThrownBy(() -> authority.requireAuthorized(5001L, """
                {"audioReference":"/practice/manage/drafts/999/questions/q/media"}
                """))
                .isInstanceOf(PracticeAuthoringCandidateException.class)
                .extracting("code")
                .isEqualTo("CANDIDATE_MATERIAL_AUTHORITY_INVALID");
    }

    @Test
    void oversizedMaterialIdentityFailsClosedAsCandidateError() {
        when(referenceService.referencesForDraft(5001L)).thenReturn(List.of());

        assertThatThrownBy(() -> authority.requireAuthorized(5001L, """
                {"imageReference":"/practice/materials/999999999999999999999/content"}
                """))
                .isInstanceOf(PracticeAuthoringCandidateException.class)
                .extracting("code")
                .isEqualTo("CANDIDATE_MATERIAL_AUTHORITY_INVALID");
    }
}
