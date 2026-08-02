package com.ksh.features.practice.manage.authoringcandidate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CreateCommand;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ReviewUpdateCommand;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceKind;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceSnapshot;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ValidationIssue;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeAuthoringCandidateServiceTest {

    @Mock
    private PracticeAuthoringCandidateRepository candidateRepository;
    @Mock
    private PracticeDraftRepository draftRepository;
    @Mock
    private PracticeAuthorizationService authorizationService;

    private ObjectMapper objectMapper;
    private PracticeAuthoringCandidateService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        QuestionTypeResolver typeResolver = new QuestionTypeResolver();
        AssessmentContractCodec codec = new AssessmentContractCodec(
                objectMapper, typeResolver);
        PracticeAuthoringCandidateJson json =
                new PracticeAuthoringCandidateJson(objectMapper);
        PracticeAuthoringCandidateNormalizer normalizer =
                new PracticeAuthoringCandidateNormalizer(
                        objectMapper, codec, typeResolver, json);
        PracticeAuthoringCandidateValidator validator =
                new PracticeAuthoringCandidateValidator(
                        codec, typeResolver, json);
        service = new PracticeAuthoringCandidateService(
                candidateRepository, draftRepository, authorizationService,
                normalizer, validator, json, objectMapper,
                Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"),
                        ZoneOffset.UTC),
                Duration.ofDays(7));
    }

    @Test
    void identicalSourceAndUnchangedTargetReusesPersistentCandidate() {
        PracticeDraft draft = PracticeAuthoringCandidateTestFixtures
                .targetDraft(0);
        PracticeAuthoringCandidate existing =
                PracticeAuthoringCandidateTestFixtures.readyCandidate(objectMapper);
        when(draftRepository.findByIdForUpdate(5001L))
                .thenReturn(Optional.of(draft));
        when(candidateRepository.findIdempotent(
                101L, SourceKind.QUICK_EXCEL,
                "practice-quick-excel-v1",
                PracticeAuthoringCandidateTestFixtures.SOURCE_DIGEST,
                "upload-1", SourceOperation.NONE,
                5001L, 1, "READING", "R1", 0,
                PracticeAuthoringCandidate.NORMALIZER_VERSION))
                .thenReturn(Optional.of(existing));

        var result = service.createOrReuse(command());

        assertThat(result.candidateId())
                .isEqualTo(PracticeAuthoringCandidateTestFixtures.CANDIDATE_ID);
        verify(authorizationService).requireDraft(
                5001L, 101L, PracticeAction.EDIT);
        verify(candidateRepository, never()).saveAndFlush(any());
    }

    @Test
    void zeroBasedPdfAiSnapshotPersistsWhileNegativeRevisionFailsClosed() {
        PracticeDraft draft = PracticeAuthoringCandidateTestFixtures.targetDraft(0);
        when(draftRepository.findByIdForUpdate(5001L))
                .thenReturn(Optional.of(draft));
        when(candidateRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ArrayNode groups = PracticeAuthoringCandidateTestFixtures
                .readingGroups(objectMapper, false);
        ObjectNode group = (ObjectNode) groups.get(0);
        ObjectNode sourceRef = objectMapper.createObjectNode();
        sourceRef.put("kind", "TEXT_SPAN");
        sourceRef.put("sourceId", "text-1");
        sourceRef.put("start", 0);
        sourceRef.put("end", 20);
        group.set("sourceRefs", objectMapper.createArrayNode().add(sourceRef.deepCopy()));
        group.withObject("/stimulus/provenance").put("source", "PDF_AI")
                .set("sourceRefs", objectMapper.createArrayNode().add(sourceRef.deepCopy()));
        group.withObject("/questions/0").put("reviewState", "REVIEW_REQUIRED")
                .set("sourceRefs", objectMapper.createArrayNode().add(sourceRef.deepCopy()));
        ObjectNode execution = objectMapper.createObjectNode();
        execution.put("purpose", "PRACTICE_PDF_AUTHORING");
        execution.put("bindingRevision", 0L);
        execution.put("providerProfileCode", "PRACTICE_AUTHORING_V1");
        execution.put("providerFamily", "OPENAI_COMPATIBLE");
        execution.put("model", "fake-model");
        execution.put("transportDialect", "OPENAI_COMPATIBLE_V1");
        execution.put("requestId", "11111111-1111-4111-8111-111111111111");
        CreateCommand pdf = new CreateCommand(
                101L,
                new SourceSnapshot(
                        SourceKind.PDF_AI,
                        "practice-pdf-authoring-output-v1",
                        "sha256:" + PracticeAuthoringCandidateTestFixtures.SOURCE_DIGEST,
                        "authoring-v1-b0", "source.pdf",
                        SourceOperation.EXTRACT, execution),
                new TargetRoute(5001L, 1, "READING", "R1"),
                groups);

        var result = service.createOrReuse(pdf, List.of(
                ValidationIssue.warning(
                        "PDF_LOW_CONFIDENCE", "SOURCE", "/groups/0/questions/0",
                        "Cần rà soát.", "EDIT_IN_REVIEW")));

        assertThat(result.state()).isEqualTo(
                PracticeAuthoringCandidateModels.CandidateState.REVIEWING);
        assertThat(result.candidate().at("/source/kind").asText())
                .isEqualTo("PDF_AI");
        assertThat(result.candidate().at("/source/operation").asText())
                .isEqualTo("EXTRACT");
        assertThat(result.candidate().at("/source/aiExecution/purpose").asText())
                .isEqualTo("PRACTICE_PDF_AUTHORING");
        assertThat(result.candidate().at(
                "/source/aiExecution/bindingRevision").asLong()).isZero();
        assertThat(result.issues()).extracting(ValidationIssue::code)
                .contains("PDF_LOW_CONFIDENCE");
        ObjectNode negativeExecution = execution.deepCopy();
        negativeExecution.put("bindingRevision", -1L);
        CreateCommand negativeRevision = new CreateCommand(
                101L,
                new SourceSnapshot(
                        SourceKind.PDF_AI,
                        "practice-pdf-authoring-output-v1",
                        "sha256:" + PracticeAuthoringCandidateTestFixtures.SOURCE_DIGEST,
                        "authoring-v1-b-1", "source.pdf",
                        SourceOperation.EXTRACT, negativeExecution),
                new TargetRoute(5001L, 1, "READING", "R1"),
                groups);

        assertThatThrownBy(() -> service.createOrReuse(negativeRevision))
                .isInstanceOf(PracticeAuthoringCandidateException.class)
                .extracting("code")
                .isEqualTo("CANDIDATE_SOURCE_INVALID");
        verify(draftRepository, never()).save(any());
        verify(candidateRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void candidateLookupDoesNotLeakAnotherOwnersIdentity() {
        when(candidateRepository.findByIdAndOwnerId(
                PracticeAuthoringCandidateTestFixtures.CANDIDATE_ID, 202L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(
                PracticeAuthoringCandidateTestFixtures.CANDIDATE_ID, 202L))
                .isInstanceOf(AccessDeniedException.class);
        verify(authorizationService, never())
                .requireDraft(any(), any(), any());
    }

    @Test
    void staleReviewVersionFailsBeforeNormalizationOrPersistence() {
        PracticeAuthoringCandidate candidate =
                PracticeAuthoringCandidateTestFixtures.readyCandidate(objectMapper);
        when(candidateRepository.findByIdAndOwnerId(
                candidate.getId(), 101L)).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> service.updateReview(
                new ReviewUpdateCommand(
                        candidate.getId(), 101L, 9L,
                        "sha256:" + candidate.getContentDigest(),
                        PracticeAuthoringCandidateTestFixtures
                                .readingGroups(objectMapper, true), true)))
                .isInstanceOf(PracticeAuthoringCandidateException.class)
                .extracting("code")
                .isEqualTo("CANDIDATE_VERSION_CONFLICT");
        verify(candidateRepository, never()).saveAndFlush(any());
    }

    @Test
    void staleReviewDigestFailsEvenWhenVersionStillMatches() {
        PracticeAuthoringCandidate candidate =
                PracticeAuthoringCandidateTestFixtures.readyCandidate(objectMapper);
        when(candidateRepository.findByIdAndOwnerId(
                candidate.getId(), 101L)).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> service.updateReview(
                new ReviewUpdateCommand(
                        candidate.getId(), 101L, candidate.getLockVersion(),
                        "sha256:" + "b".repeat(64),
                        PracticeAuthoringCandidateTestFixtures
                                .readingGroups(objectMapper, true), true)))
                .isInstanceOf(PracticeAuthoringCandidateException.class)
                .extracting("code")
                .isEqualTo("CANDIDATE_VERSION_CONFLICT");
        verify(candidateRepository, never()).saveAndFlush(any());
    }

    @Test
    void mismatchedSourceContractFailsBeforeDraftAuthorization() {
        CreateCommand invalid = new CreateCommand(
                101L,
                new SourceSnapshot(
                        SourceKind.QUICK_EXCEL,
                        "practice-excel-v2",
                        "sha256:" + PracticeAuthoringCandidateTestFixtures.SOURCE_DIGEST,
                        "upload-1", "reading.xlsx",
                        SourceOperation.NONE, null),
                new TargetRoute(5001L, 1, "READING", "R1"),
                PracticeAuthoringCandidateTestFixtures
                        .readingGroups(objectMapper, true));

        assertThatThrownBy(() -> service.createOrReuse(invalid))
                .isInstanceOf(PracticeAuthoringCandidateException.class)
                .extracting("code")
                .isEqualTo("CANDIDATE_SOURCE_INVALID");
        verify(authorizationService, never())
                .requireDraft(any(), any(), any());
    }

    @Test
    void reviewCannotRewriteSourceProvenanceForRetainedQuestion() {
        PracticeAuthoringCandidate candidate =
                PracticeAuthoringCandidateTestFixtures.readyCandidate(objectMapper);
        when(candidateRepository.findByIdAndOwnerId(
                candidate.getId(), 101L)).thenReturn(Optional.of(candidate));
        var edited = PracticeAuthoringCandidateTestFixtures
                .readingGroups(objectMapper, true);
        edited.get(0).withObject("/questions/0/sourceRefs/0")
                .put("sourceId", "forged-row");

        assertThatThrownBy(() -> service.updateReview(
                new ReviewUpdateCommand(
                        candidate.getId(), 101L, candidate.getLockVersion(),
                        "sha256:" + candidate.getContentDigest(),
                        edited, false)))
                .isInstanceOf(PracticeAuthoringCandidateException.class)
                .extracting("code")
                .isEqualTo("CANDIDATE_SOURCE_REFERENCE_CHANGED");
        verify(candidateRepository, never()).saveAndFlush(any());
    }

    @Test
    void readyCandidateCannotBeMarkedReadyAgain() {
        PracticeAuthoringCandidate candidate =
                PracticeAuthoringCandidateTestFixtures.readyCandidate(objectMapper);
        when(candidateRepository.findByIdAndOwnerId(
                candidate.getId(), 101L)).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> service.markReady(
                candidate.getId(), 101L, candidate.getLockVersion(),
                "sha256:" + candidate.getContentDigest()))
                .isInstanceOf(PracticeAuthoringCandidateException.class)
                .extracting("code")
                .isEqualTo("CANDIDATE_NOT_READY");
        verify(candidateRepository, never()).saveAndFlush(any());
    }

    @Test
    void appliedCandidateCannotBeRejectedAgain() {
        PracticeAuthoringCandidate candidate =
                PracticeAuthoringCandidateTestFixtures.readyCandidate(objectMapper);
        candidate.markApplied(
                candidate.getCandidateJson(), 1,
                java.time.LocalDateTime.of(2026, 8, 2, 0, 1));
        when(candidateRepository.findByIdAndOwnerId(
                candidate.getId(), 101L)).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> service.reject(
                candidate.getId(), 101L, candidate.getLockVersion(),
                "sha256:" + candidate.getContentDigest()))
                .isInstanceOf(PracticeAuthoringCandidateException.class)
                .extracting("code")
                .isEqualTo("CANDIDATE_NOT_REVIEWABLE");
        verify(candidateRepository, never()).saveAndFlush(any());
    }

    private CreateCommand command() {
        return new CreateCommand(
                101L,
                new SourceSnapshot(
                        SourceKind.QUICK_EXCEL,
                        "practice-quick-excel-v1",
                        "sha256:" + PracticeAuthoringCandidateTestFixtures.SOURCE_DIGEST,
                        "upload-1", "reading.xlsx",
                        SourceOperation.NONE, null),
                new TargetRoute(5001L, 1, "READING", "R1"),
                PracticeAuthoringCandidateTestFixtures
                        .readingGroups(objectMapper, true));
    }
}
