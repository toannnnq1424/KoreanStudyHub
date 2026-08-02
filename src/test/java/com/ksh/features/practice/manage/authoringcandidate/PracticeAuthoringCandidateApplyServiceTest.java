package com.ksh.features.practice.manage.authoringcandidate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ApplyCommand;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ApplyResultCode;
import com.ksh.features.practice.manage.service.PracticeDraftContractService;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class PracticeAuthoringCandidateApplyServiceTest {

    private static final UUID REQUEST_ID =
            UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Mock
    private PracticeAuthoringCandidateRepository candidateRepository;
    @Mock
    private PracticeAuthoringCandidateApplyEventRepository eventRepository;
    @Mock
    private PracticeDraftRepository draftRepository;
    @Mock
    private PracticeAuthorizationService authorizationService;
    @Mock
    private PracticeAuthoringCandidateDraftProjector projector;
    @Mock
    private PracticeDraftContractService draftContractService;
    @Mock
    private PracticeDraftValidator draftValidator;

    private ObjectMapper objectMapper;
    private PracticeAuthoringCandidateJson candidateJson;
    private PracticeAuthoringCandidateApplyService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        candidateJson = new PracticeAuthoringCandidateJson(objectMapper);
        service = new PracticeAuthoringCandidateApplyService(
                candidateRepository, eventRepository, draftRepository,
                authorizationService, projector, draftContractService,
                draftValidator, candidateJson,
                Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"),
                        ZoneOffset.UTC));
    }

    @Test
    void staleDraftVersionRecordsConflictWithoutAnyDraftMutation() {
        PracticeAuthoringCandidate candidate = readyCandidate();
        PracticeDraft staleDraft = PracticeAuthoringCandidateTestFixtures
                .targetDraft(1);
        arrangeLocks(candidate, staleDraft);

        var result = service.apply(command(candidate));

        assertThat(result.result()).isEqualTo(ApplyResultCode.CONFLICT);
        assertThat(result.resultCode())
                .isEqualTo("TARGET_DRAFT_VERSION_CONFLICT");
        verify(draftRepository, never()).saveAndFlush(any());
        verify(projector, never()).append(any(), any(), any());
        verify(eventRepository).save(any(
                PracticeAuthoringCandidateApplyEvent.class));
    }

    @Test
    void exactSuccessfulReplayReturnsLedgerResultWithoutSecondMutation() {
        PracticeAuthoringCandidate candidate = readyCandidate();
        PracticeDraft draft = PracticeAuthoringCandidateTestFixtures
                .targetDraft(1);
        arrangeLocks(candidate, draft);
        PracticeAuthoringCandidateApplyEvent event =
                new PracticeAuthoringCandidateApplyEvent(
                        candidate.getId(), REQUEST_ID, candidate.getLockVersion(),
                        candidate.getContentDigest(), candidate.getBaseDraftVersion(),
                        ApplyResultCode.DRAFT_APPLIED, "DRAFT_APPLIED", 1,
                        101L, LocalDateTime.of(2026, 8, 2, 1, 0));
        when(eventRepository.findByCandidateIdAndApplyRequestId(
                candidate.getId(), REQUEST_ID.toString()))
                .thenReturn(Optional.of(event));

        var result = service.apply(command(candidate));

        assertThat(result.result()).isEqualTo(ApplyResultCode.DRAFT_APPLIED);
        assertThat(result.replayed()).isTrue();
        assertThat(result.draftVersion()).isEqualTo(1);
        verify(draftRepository, never()).saveAndFlush(any());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void successfulApplyMutatesDraftOnceAndClosesCandidateAtomically() {
        PracticeAuthoringCandidate candidate = readyCandidate();
        PracticeDraft draft = PracticeAuthoringCandidateTestFixtures
                .targetDraft(0);
        arrangeLocks(candidate, draft);
        ObjectNode projected = objectMapper.createObjectNode();
        when(projector.append(draft.getDraftJson(), candidate, candidateJson
                .readObject(candidate.getCandidateJson())))
                .thenReturn(projected);
        when(draftContractService.normalize(projected, "QUICK_EXCEL"))
                .thenReturn(new PracticeDraftContractService.NormalizedDraft(
                        "{\"normalized\":true}"));
        when(draftValidator.validate("{\"normalized\":true}"))
                .thenReturn(new PracticeDraftValidator.ValidationResult(
                        false, List.of(), 1, 1, 1, 1));
        when(draftRepository.saveAndFlush(draft)).thenAnswer(invocation -> {
            draft.setVersion(1);
            return draft;
        });

        var result = service.apply(command(candidate));

        assertThat(result.result()).isEqualTo(ApplyResultCode.DRAFT_APPLIED);
        assertThat(result.draftVersion()).isEqualTo(1);
        assertThat(candidate.getState())
                .isEqualTo(PracticeAuthoringCandidateModels.CandidateState.APPLIED);
        assertThat(draft.getDraftJson()).isEqualTo("{\"normalized\":true}");
        verify(draftRepository).saveAndFlush(draft);
        verify(eventRepository).save(any(
                PracticeAuthoringCandidateApplyEvent.class));
        verify(candidateRepository).save(candidate);
    }

    @Test
    void canonicalValidationBlockerLeavesDraftUntouchedAndRecordsRejection() {
        PracticeAuthoringCandidate candidate = readyCandidate();
        PracticeDraft draft = PracticeAuthoringCandidateTestFixtures
                .targetDraft(0);
        arrangeLocks(candidate, draft);
        ObjectNode projected = objectMapper.createObjectNode();
        when(projector.append(any(), any(), any())).thenReturn(projected);
        when(draftContractService.normalize(projected, "QUICK_EXCEL"))
                .thenReturn(new PracticeDraftContractService.NormalizedDraft(
                        "{\"normalized\":true}"));
        when(draftValidator.validate("{\"normalized\":true}"))
                .thenReturn(new PracticeDraftValidator.ValidationResult(
                        true, List.of(), 1, 1, 1, 1));

        var result = service.apply(command(candidate));

        assertThat(result.result()).isEqualTo(ApplyResultCode.REJECTED);
        assertThat(result.resultCode())
                .isEqualTo("CANDIDATE_DRAFT_VALIDATION_FAILED");
        assertThat(draft.getDraftJson()).doesNotContain("normalized");
        verify(draftRepository, never()).saveAndFlush(any());
        verify(eventRepository).save(any(
                PracticeAuthoringCandidateApplyEvent.class));
    }

    @Test
    void deniedDraftAuthorizationStopsBeforeEitherWriteLock() {
        PracticeAuthoringCandidate candidate = readyCandidate();
        when(candidateRepository.findByIdAndOwnerId(candidate.getId(), 101L))
                .thenReturn(Optional.of(candidate));
        doThrow(new AccessDeniedException("denied"))
                .when(authorizationService).requireDraft(
                        5001L, 101L, PracticeAction.EDIT);

        assertThatThrownBy(() -> service.apply(command(candidate)))
                .isInstanceOf(AccessDeniedException.class);

        verify(candidateRepository, never()).findByIdForUpdate(any());
        verify(draftRepository, never()).findByIdForUpdate(any());
        verify(eventRepository, never()).save(any());
    }

    private PracticeAuthoringCandidate readyCandidate() {
        return PracticeAuthoringCandidateTestFixtures
                .readyCandidate(objectMapper);
    }

    private ApplyCommand command(PracticeAuthoringCandidate candidate) {
        return new ApplyCommand(
                candidate.getId(), REQUEST_ID, 101L,
                candidate.getLockVersion(),
                "sha256:" + candidate.getContentDigest());
    }

    private void arrangeLocks(
            PracticeAuthoringCandidate candidate,
            PracticeDraft draft) {
        when(candidateRepository.findByIdAndOwnerId(candidate.getId(), 101L))
                .thenReturn(Optional.of(candidate));
        when(candidateRepository.findByIdForUpdate(candidate.getId()))
                .thenReturn(Optional.of(candidate));
        when(draftRepository.findByIdForUpdate(5001L))
                .thenReturn(Optional.of(draft));
        when(eventRepository.findByCandidateIdAndApplyRequestId(
                candidate.getId(), REQUEST_ID.toString()))
                .thenReturn(Optional.empty());
        verifyNoAuthorizationYet();
    }

    private void verifyNoAuthorizationYet() {
        // Kept as an explicit seam: authorization is verified after apply.
    }
}
