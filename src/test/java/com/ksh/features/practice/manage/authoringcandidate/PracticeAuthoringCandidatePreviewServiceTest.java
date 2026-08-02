package com.ksh.features.practice.manage.authoringcandidate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.manage.service.PracticeDraftContractService;
import com.ksh.features.practice.manage.service.PracticeDraftPreviewService;
import com.ksh.features.practice.manage.validator.PracticeDraftValidator;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeAuthoringCandidatePreviewServiceTest {

    @Mock PracticeAuthoringCandidateRepository candidateRepository;
    @Mock PracticeDraftRepository draftRepository;
    @Mock PracticeAuthorizationService authorizationService;
    @Mock PracticeAuthoringCandidateDraftProjector projector;
    @Mock PracticeDraftContractService draftContractService;
    @Mock PracticeDraftValidator draftValidator;
    @Mock PracticeDraftPreviewService draftPreviewService;
    @Mock PracticeAuthoringCandidateMaterialAuthority materialAuthority;

    private ObjectMapper objectMapper;
    private PracticeAuthoringCandidateJson candidateJson;
    private PracticeAuthoringCandidatePreviewService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        candidateJson = new PracticeAuthoringCandidateJson(objectMapper);
        service = new PracticeAuthoringCandidatePreviewService(
                candidateRepository, draftRepository, authorizationService,
                projector, draftContractService, draftValidator,
                draftPreviewService, materialAuthority, candidateJson,
                Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"),
                        ZoneOffset.UTC));
    }

    @Test
    void projectsNormalizesValidatesAndPresentsWithoutPersistence() {
        PracticeAuthoringCandidate candidate = readyCandidate();
        PracticeDraft draft = PracticeAuthoringCandidateTestFixtures.targetDraft(0);
        ObjectNode projected = objectMapper.createObjectNode();
        arrange(candidate, draft);
        when(projector.append(
                draft.getDraftJson(), candidate,
                candidateJson.readObject(candidate.getCandidateJson())))
                .thenReturn(projected);
        when(draftContractService.normalize(projected, "QUICK_EXCEL"))
                .thenReturn(new PracticeDraftContractService.NormalizedDraft(
                        "{\"normalized\":true}"));
        when(draftValidator.validate("{\"normalized\":true}"))
                .thenReturn(new PracticeDraftValidator.ValidationResult(
                        false, List.of(), 1, 1, 1, 1));
        var delivery = new PracticeDraftPreviewService.DraftDeliveryPreview(
                PracticeDraftPreviewService.SCHEMA_VERSION,
                "Reading", List.of());
        when(draftPreviewService.preview("{\"normalized\":true}"))
                .thenReturn(delivery);

        var result = service.preview(
                candidate.getId(), 101L, candidate.getLockVersion(),
                "sha256:" + candidate.getContentDigest());

        assertThat(result.baseDraftVersion()).isZero();
        assertThat(result.delivery()).isSameAs(delivery);
        verify(authorizationService).requireDraft(
                5001L, 101L, PracticeAction.READ);
        verify(materialAuthority).requireAuthorized(
                5001L, "{\"normalized\":true}");
        verify(candidateRepository, never()).save(any());
        verify(draftRepository, never()).save(any());
    }

    @Test
    void changedDraftVersionReturnsConflictBeforeProjection() {
        PracticeAuthoringCandidate candidate = readyCandidate();
        arrange(candidate, PracticeAuthoringCandidateTestFixtures.targetDraft(1));

        assertThatThrownBy(() -> service.preview(
                candidate.getId(), 101L, candidate.getLockVersion(),
                "sha256:" + candidate.getContentDigest()))
                .isInstanceOf(PracticeAuthoringCandidateException.class)
                .extracting("code")
                .isEqualTo("TARGET_DRAFT_VERSION_CONFLICT");
        verify(projector, never()).append(any(), any(), any());
        verify(draftPreviewService, never()).preview(any());
    }

    @Test
    void staleCandidateDigestReturnsConflictBeforeReadingDraft() {
        PracticeAuthoringCandidate candidate = readyCandidate();
        when(candidateRepository.findByIdAndOwnerIdForRead(
                candidate.getId(), 101L)).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> service.preview(
                candidate.getId(), 101L, candidate.getLockVersion(),
                "sha256:" + "b".repeat(64)))
                .isInstanceOf(PracticeAuthoringCandidateException.class)
                .extracting("code")
                .isEqualTo("CANDIDATE_VERSION_CONFLICT");
        verify(draftRepository, never()).findByIdForRead(any());
        verify(projector, never()).append(any(), any(), any());
    }

    @Test
    void anotherLecturerCannotDiscoverCandidateOrTarget() {
        when(candidateRepository.findByIdAndOwnerIdForRead(
                PracticeAuthoringCandidateTestFixtures.CANDIDATE_ID, 202L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.preview(
                PracticeAuthoringCandidateTestFixtures.CANDIDATE_ID,
                202L, 0L, "sha256:" + "a".repeat(64)))
                .isInstanceOf(AccessDeniedException.class);
        verify(authorizationService, never())
                .requireDraft(any(), any(), any());
        verify(draftRepository, never()).findByIdForRead(any());
    }

    private void arrange(
            PracticeAuthoringCandidate candidate,
            PracticeDraft draft) {
        when(candidateRepository.findByIdAndOwnerIdForRead(candidate.getId(), 101L))
                .thenReturn(Optional.of(candidate));
        when(draftRepository.findByIdForRead(5001L)).thenReturn(Optional.of(draft));
    }

    private PracticeAuthoringCandidate readyCandidate() {
        return PracticeAuthoringCandidateTestFixtures.readyCandidate(objectMapper);
    }
}
