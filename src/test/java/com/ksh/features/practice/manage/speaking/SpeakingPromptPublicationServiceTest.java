package com.ksh.features.practice.manage.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeMaterialReferenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeakingPromptPublicationServiceTest {
    private static final Long DRAFT_ID = 10L;
    private static final Long OWNER_ID = 20L;
    private static final String CLIENT_ID = "speaking-a";
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 26, 10, 0);

    private final ObjectMapper mapper = new ObjectMapper();
    private final SpeakingPromptSourceRepository sources =
            mock(SpeakingPromptSourceRepository.class);
    private final SpeakingPromptAiArtifactRepository artifacts =
            mock(SpeakingPromptAiArtifactRepository.class);
    private final SpeakingPromptTranscriptRevisionRepository revisions =
            mock(SpeakingPromptTranscriptRevisionRepository.class);
    private final LecturerAssetRepository assets =
            mock(LecturerAssetRepository.class);
    private final PracticeMaterialReferenceRepository references =
            mock(PracticeMaterialReferenceRepository.class);
    private final SpeakingPromptVersionContextRepository contexts =
            mock(SpeakingPromptVersionContextRepository.class);
    private final SpeakingPromptFingerprintService fingerprints =
            new SpeakingPromptFingerprintService();
    private final SpeakingPromptAuthoringAiProperties properties =
            new SpeakingPromptAuthoringAiProperties();
    private final SpeakingPromptPublicationService service =
            new SpeakingPromptPublicationService(
                    sources,
                    artifacts,
                    revisions,
                    assets,
                    references,
                    fingerprints,
                    properties,
                    new AssessmentContractCodec(
                            mapper, new QuestionTypeResolver()),
                    contexts);

    @Test
    void manualTextOnlyPublishesV2WithoutTtsArtifactOrPlayStep()
            throws Exception {
        String prompt = "주말에 무엇을 합니까?";
        SpeakingPromptSource source = SpeakingPromptSource.audioUpload(
                DRAFT_ID, CLIENT_ID, OWNER_ID, 70L, OWNER_ID);
        source.switchToManualText(
                fingerprints.exactTextSha256(prompt), false, OWNER_ID);
        when(sources.findByDraftIdForUpdate(DRAFT_ID))
                .thenReturn(List.of(source));

        SpeakingPromptPublicationService.PublicationPlan plan =
                service.prepare(
                        DRAFT_ID,
                        OWNER_ID,
                        root(prompt, delivery(
                                QuestionContent.SpeakingPromptInputType
                                        .MANUAL_TEXT,
                                QuestionContent.SpeakingDeliveryMode.TEXT_ONLY,
                                QuestionContent.SpeakingAudioOrigin.NONE,
                                null,
                                null)));

        SpeakingPromptPublicationService.Candidate candidate =
                plan.require(CLIENT_ID);
        assertThat(candidate.learnerContent().schemaVersion())
                .isEqualTo(QuestionContent.SCHEMA_VERSION_V2);
        assertThat(candidate.learnerContent().speakingDelivery()
                .promptPlayLimit()).isNull();
        assertThat(candidate.activeAssetId()).isNull();
        assertThat(candidate.context().promptContextText())
                .isEqualTo(prompt);
        assertThat(candidate.context().originalAudioAssetId()).isNull();
        verify(artifacts, never()).findByIdForUpdate(
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void exactReadySyncedTtsPublishesOnlyGeneratedActiveAsset()
            throws Exception {
        String prompt = "자기소개를 하세요.";
        SpeakingPromptSource source = SpeakingPromptSource.audioUpload(
                DRAFT_ID, CLIENT_ID, OWNER_ID, 70L, OWNER_ID);
        source.switchToManualText(
                fingerprints.exactTextSha256(prompt), true, OWNER_ID);
        SpeakingPromptAiArtifact artifact = ttsArtifact(
                41L, source, prompt, 81L);
        source.switchToManualText(
                fingerprints.exactTextSha256(prompt), true, OWNER_ID);
        source.markTtsQueued(artifact.getId(), OWNER_ID);
        source.attachTtsArtifact(artifact.getId(), 81L);
        LecturerAsset asset = audioAsset(81L, "AI_TTS");
        when(sources.findByDraftIdForUpdate(DRAFT_ID))
                .thenReturn(List.of(source));
        when(artifacts.findByIdForUpdate(41L))
                .thenReturn(Optional.of(artifact));
        when(assets.findByIdAndOwnerLecturerId(81L, OWNER_ID))
                .thenReturn(Optional.of(asset));
        when(references
                .existsByAssetIdAndDraftIdAndPlacementAndReferenceKey(
                        81L,
                        DRAFT_ID,
                        SpeakingPromptAssetService.GENERATED_PLACEMENT,
                        CLIENT_ID))
                .thenReturn(true);

        SpeakingPromptPublicationService.PublicationPlan plan =
                service.prepare(
                        DRAFT_ID,
                        OWNER_ID,
                        root(prompt, delivery(
                                QuestionContent.SpeakingPromptInputType
                                        .MANUAL_TEXT,
                                QuestionContent.SpeakingDeliveryMode
                                        .TEXT_AND_AUDIO,
                                QuestionContent.SpeakingAudioOrigin.AI_TTS,
                                authoringReference("generated"),
                                1)));

        assertThat(plan.require(CLIENT_ID).learnerContent()
                .speakingDelivery().promptAudioReference())
                .isEqualTo("/practice/materials/81/content");
        assertThat(plan.activeAssetBindings())
                .containsExactly(new SpeakingPromptPublicationService
                        .ActiveAssetBinding(
                                81L,
                                SpeakingPromptAssetService
                                        .GENERATED_PLACEMENT,
                                CLIENT_ID));
        assertThat(plan.require(CLIENT_ID).context()
                .originalAudioAssetId()).isNull();
    }

    @Test
    void uploadedAudioRequiresCurrentReadyTranscriptAndLecturerConfirmation()
            throws Exception {
        UploadedFixture ready = uploadedFixture(
                SpeakingPromptSource.STATUS_READY,
                new BigDecimal("0.90"),
                true);

        SpeakingPromptPublicationService.PublicationPlan plan =
                ready.service().prepare(
                        DRAFT_ID,
                        OWNER_ID,
                        root("", delivery(
                                QuestionContent.SpeakingPromptInputType
                                        .AUDIO_UPLOAD,
                                QuestionContent.SpeakingDeliveryMode.AUDIO_ONLY,
                                QuestionContent.SpeakingAudioOrigin
                                        .TEACHER_UPLOAD,
                                authoringReference("original"),
                                2)));

        assertThat(plan.require(CLIENT_ID).context()
                .promptContextText()).isEqualTo("질문을 듣고 대답하세요.");
        assertThat(plan.require(CLIENT_ID).learnerContent()
                .speakingDelivery().promptAudioReference())
                .isEqualTo("/practice/materials/71/content");
    }

    @Test
    void lecturerRevisionConfirmsLowConfidenceTranscriptAndThenPublishes()
            throws Exception {
        UploadedFixture fixture = uploadedFixture(
                SpeakingPromptSource.STATUS_NEEDS_REVIEW,
                new BigDecimal("0.30"),
                false);
        SpeakingPromptDraftAuthority authority =
                mock(SpeakingPromptDraftAuthority.class);
        PracticeDraft draft = new PracticeDraft(
                "Draft", "", "PRIVATE", null, "DRAFT", OWNER_ID, "{}");
        ObjectNode authorityRoot = mapper.createObjectNode();
        ObjectNode authorityQuestion = authorityRoot.putObject("question");
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                new SpeakingPromptDraftAuthority.AuthorizedDraft(
                        draft,
                        OWNER_ID,
                        OWNER_ID,
                        authorityRoot,
                        authorityQuestion);
        when(authority.authorizeAndLock(
                DRAFT_ID, CLIENT_ID, OWNER_ID, PracticeAction.EDIT))
                .thenReturn(authorized);
        when(fixture.sourceRepository().findByDraftAndClientForUpdate(
                DRAFT_ID, CLIENT_ID))
                .thenReturn(Optional.of(fixture.source()));
        when(fixture.artifactRepository().findByIdForUpdate(31L))
                .thenReturn(Optional.of(fixture.artifact()));
        when(fixture.revisionRepository()
                .findMaximumRevisionNumber(31L))
                .thenReturn(1);
        AtomicReference<SpeakingPromptTranscriptRevision> savedRevision =
                new AtomicReference<>();
        when(fixture.revisionRepository().saveAndFlush(
                any(SpeakingPromptTranscriptRevision.class)))
                .thenAnswer(invocation -> {
                    SpeakingPromptTranscriptRevision revision =
                            invocation.getArgument(0);
                    set(revision, "id", 62L);
                    savedRevision.set(revision);
                    return revision;
                });
        when(fixture.revisionRepository().findById(62L))
                .thenAnswer(ignored -> Optional.ofNullable(
                        savedRevision.get()));
        SpeakingPromptTranscriptService transcriptService =
                new SpeakingPromptTranscriptService(
                        authority,
                        fixture.sourceRepository(),
                        fixture.artifactRepository(),
                        fixture.revisionRepository(),
                        fingerprints);
        String corrected = "질문을 듣고 구체적으로 대답하세요.";

        SpeakingPromptTranscriptService.RevisionResult revision =
                transcriptService.revise(
                        new SpeakingPromptTranscriptService.ReviseTranscript(
                                DRAFT_ID,
                                CLIENT_ID,
                                OWNER_ID,
                                fixture.source().getSourceRevision(),
                                0L,
                                fixture.artifact().getId(),
                                corrected,
                                true));

        assertThat(revision.confirmed()).isTrue();
        assertThat(revision.sourceRevision()).isEqualTo(2L);
        assertThat(fixture.source().getTranscriptStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_READY);
        assertThat(fixture.source().getLecturerTranscriptConfirmedAt())
                .isNotNull();

        SpeakingPromptPublicationService.PublicationPlan plan =
                fixture.service().prepare(
                        DRAFT_ID,
                        OWNER_ID,
                        root("", delivery(
                                QuestionContent.SpeakingPromptInputType
                                        .AUDIO_UPLOAD,
                                QuestionContent.SpeakingDeliveryMode.AUDIO_ONLY,
                                QuestionContent.SpeakingAudioOrigin
                                        .TEACHER_UPLOAD,
                                authoringReference("original"),
                                1)));

        assertThat(plan.require(CLIENT_ID).context()
                .promptContextText()).isEqualTo(corrected);
        assertThat(plan.require(CLIENT_ID).learnerContent()
                .speakingDelivery().promptAudioReference())
                .isEqualTo("/practice/materials/71/content");
        verify(fixture.revisionRepository()).saveAndFlush(
                any(SpeakingPromptTranscriptRevision.class));
        verify(fixture.sourceRepository()).save(fixture.source());
    }

    @Test
    void queuedStaleFailedAndUnconfirmedLowConfidenceOutcomesBlockPublish()
            throws Exception {
        for (String status : List.of(
                SpeakingPromptSource.STATUS_QUEUED,
                SpeakingPromptSource.STATUS_STALE,
                SpeakingPromptSource.STATUS_FAILED_RETRYABLE,
                SpeakingPromptSource.STATUS_FAILED_FINAL)) {
            UploadedFixture fixture =
                    uploadedFixture(status, new BigDecimal("0.90"), true);
            assertThatThrownBy(() -> fixture.service().prepare(
                    DRAFT_ID,
                    OWNER_ID,
                    root("", delivery(
                            QuestionContent.SpeakingPromptInputType.AUDIO_UPLOAD,
                            QuestionContent.SpeakingDeliveryMode.AUDIO_ONLY,
                            QuestionContent.SpeakingAudioOrigin.TEACHER_UPLOAD,
                            authoringReference("original"),
                            1))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("STT chưa Sẵn sàng");
        }

        UploadedFixture lowConfidence =
                uploadedFixture(
                        SpeakingPromptSource.STATUS_READY,
                        new BigDecimal("0.30"),
                        false);
        assertThatThrownBy(() -> lowConfidence.service().prepare(
                DRAFT_ID,
                OWNER_ID,
                root("", delivery(
                        QuestionContent.SpeakingPromptInputType.AUDIO_UPLOAD,
                        QuestionContent.SpeakingDeliveryMode.AUDIO_ONLY,
                        QuestionContent.SpeakingAudioOrigin.TEACHER_UPLOAD,
                        authoringReference("original"),
                        1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("độ tin cậy thấp");
    }

    @Test
    void crossCombinationAndChangedCurrentAttachmentFailClosed()
            throws Exception {
        String prompt = "한국어로 말하세요.";
        SpeakingPromptSource source = SpeakingPromptSource.manualText(
                DRAFT_ID,
                CLIENT_ID,
                OWNER_ID,
                fingerprints.exactTextSha256(prompt),
                false,
                OWNER_ID);
        when(sources.findByDraftIdForUpdate(DRAFT_ID))
                .thenReturn(List.of(source));

        assertThatThrownBy(() -> service.prepare(
                DRAFT_ID,
                OWNER_ID,
                root(prompt, delivery(
                        QuestionContent.SpeakingPromptInputType.MANUAL_TEXT,
                        QuestionContent.SpeakingDeliveryMode.TEXT_AND_AUDIO,
                        QuestionContent.SpeakingAudioOrigin.AI_TTS,
                        authoringReference("generated"),
                        1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("không đồng nhất");

        SpeakingPromptSource enabledSource =
                SpeakingPromptSource.manualText(
                DRAFT_ID,
                CLIENT_ID,
                OWNER_ID,
                fingerprints.exactTextSha256(prompt),
                true,
                OWNER_ID);
        when(sources.findByDraftIdForUpdate(DRAFT_ID))
                .thenReturn(List.of(enabledSource));
        SpeakingPromptAiArtifact reusable =
                ttsArtifact(42L, enabledSource, prompt, 82L);
        enabledSource.markTtsQueued(42L, OWNER_ID);
        enabledSource.attachTtsArtifact(42L, 82L);
        when(artifacts.findByIdForUpdate(42L))
                .thenReturn(Optional.of(reusable));
        when(assets.findByIdAndOwnerLecturerId(82L, OWNER_ID))
                .thenReturn(Optional.of(audioAsset(82L, "AI_TTS")));
        when(references
                .existsByAssetIdAndDraftIdAndPlacementAndReferenceKey(
                        82L,
                        DRAFT_ID,
                        SpeakingPromptAssetService.GENERATED_PLACEMENT,
                        CLIENT_ID))
                .thenReturn(true);

        SpeakingPromptPublicationService.PublicationPlan plan =
                service.prepare(
                DRAFT_ID,
                OWNER_ID,
                root(prompt, delivery(
                        QuestionContent.SpeakingPromptInputType.MANUAL_TEXT,
                        QuestionContent.SpeakingDeliveryMode.TEXT_AND_AUDIO,
                        QuestionContent.SpeakingAudioOrigin.AI_TTS,
                        authoringReference("generated"),
                        1)));
        enabledSource.switchToManualText(
                fingerprints.exactTextSha256(prompt), true, OWNER_ID);

        assertThatThrownBy(() -> service.persistContexts(
                plan,
                java.util.Map.of(CLIENT_ID, 902L),
                OWNER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nguồn đề đã thay đổi");
    }

    @Test
    void contextRowsAreOnePerExactQuestionVersionAndSavedInOneBatch() {
        SpeakingPromptSource source = SpeakingPromptSource.manualText(
                DRAFT_ID,
                CLIENT_ID,
                OWNER_ID,
                fingerprints.exactTextSha256("질문입니다."),
                false,
                OWNER_ID);
        SpeakingPromptPublicationService.Candidate candidate =
                new SpeakingPromptPublicationService.Candidate(
                        DRAFT_ID,
                        CLIENT_ID,
                        source,
                        source.getSourceRevision(),
                        null,
                        null,
                        null,
                        null,
                        QuestionContent.speakingV2(
                                delivery(
                                        QuestionContent
                                                .SpeakingPromptInputType
                                                .MANUAL_TEXT,
                                        QuestionContent.SpeakingDeliveryMode
                                                .TEXT_ONLY,
                                        QuestionContent.SpeakingAudioOrigin
                                                .NONE,
                                        null,
                                        null)),
                        manualContext("질문입니다."));
        SpeakingPromptPublicationService.PublicationPlan plan =
                new SpeakingPromptPublicationService.PublicationPlan(
                        java.util.Map.of(CLIENT_ID, candidate));
        when(contexts.existsById(901L)).thenReturn(false);

        service.persistContexts(
                plan, java.util.Map.of(CLIENT_ID, 901L), OWNER_ID);

        verify(contexts).saveAll(
                org.mockito.ArgumentMatchers.argThat(rows ->
                        java.util.stream.StreamSupport.stream(
                                rows.spliterator(), false).count() == 1
                                && rows.iterator().next()
                                .getQuestionVersionId().equals(901L)));
        verify(contexts).flush();
    }

    private UploadedFixture uploadedFixture(
            String sourceStatus,
            BigDecimal confidence,
            boolean sourceConfirmed) {
        SpeakingPromptSourceRepository fixtureSources =
                mock(SpeakingPromptSourceRepository.class);
        SpeakingPromptAiArtifactRepository fixtureArtifacts =
                mock(SpeakingPromptAiArtifactRepository.class);
        SpeakingPromptTranscriptRevisionRepository fixtureRevisions =
                mock(SpeakingPromptTranscriptRevisionRepository.class);
        LecturerAssetRepository fixtureAssets =
                mock(LecturerAssetRepository.class);
        PracticeMaterialReferenceRepository fixtureReferences =
                mock(PracticeMaterialReferenceRepository.class);
        SpeakingPromptSource source = SpeakingPromptSource.audioUpload(
                DRAFT_ID, CLIENT_ID, OWNER_ID, 71L, OWNER_ID);
        SpeakingPromptAiArtifact artifact = sttArtifact(
                31L, source, 71L, confidence);
        source.markSttQueued(31L, OWNER_ID);
        source.attachSttArtifact(31L, 61L, false);
        String transcript = "질문을 듣고 대답하세요.";
        SpeakingPromptTranscriptRevision revision = sourceConfirmed
                ? SpeakingPromptTranscriptRevision.lecturerEdit(
                artifact,
                2,
                transcript,
                fingerprints.exactTextSha256(transcript),
                OWNER_ID,
                NOW)
                : SpeakingPromptTranscriptRevision.provider(
                artifact,
                1,
                transcript,
                fingerprints.exactTextSha256(transcript),
                null);
        set(revision, "id", 61L);
        if (sourceConfirmed) {
            source.recordTranscriptEdit(61L, OWNER_ID, true, NOW);
        }
        set(source, "transcriptStatus", sourceStatus);
        set(source, "lecturerTranscriptConfirmedAt",
                sourceConfirmed ? NOW : null);
        when(fixtureSources.findByDraftIdForUpdate(DRAFT_ID))
                .thenReturn(List.of(source));
        when(fixtureArtifacts.findByIdForUpdate(31L))
                .thenReturn(Optional.of(artifact));
        when(fixtureRevisions.findById(61L))
                .thenReturn(Optional.of(revision));
        when(fixtureAssets.findByIdAndOwnerLecturerId(71L, OWNER_ID))
                .thenReturn(Optional.of(audioAsset(71L, "MANUAL_UPLOAD")));
        when(fixtureReferences
                .existsByAssetIdAndDraftIdAndPlacementAndReferenceKey(
                        71L,
                        DRAFT_ID,
                        SpeakingPromptAssetService.ORIGINAL_PLACEMENT,
                        CLIENT_ID))
                .thenReturn(true);
        return new UploadedFixture(
                new SpeakingPromptPublicationService(
                        fixtureSources,
                        fixtureArtifacts,
                        fixtureRevisions,
                        fixtureAssets,
                        fixtureReferences,
                        fingerprints,
                        properties,
                        new AssessmentContractCodec(
                                mapper, new QuestionTypeResolver()),
                        contexts),
                source,
                artifact,
                fixtureSources,
                fixtureArtifacts,
                fixtureRevisions);
    }

    private SpeakingPromptAiArtifact sttArtifact(
            Long id,
            SpeakingPromptSource source,
            Long assetId,
            BigDecimal confidence) {
        SpeakingPromptAuthoringAiProperties.SttConfig config =
                properties.sttConfig();
        LecturerAsset asset = audioAsset(assetId, "MANUAL_UPLOAD");
        SpeakingPromptAiArtifact artifact = baseArtifact(
                id,
                source,
                SpeakingPromptAiContract.Operation.STT,
                fingerprints.sttFingerprint(
                        OWNER_ID, assetId, asset.getSha256(), config),
                asset.getSha256(),
                config.provider(),
                config.model(),
                config.language(),
                config.purposeCode(),
                config.retentionCode());
        set(artifact, "inputAudioAssetId", assetId);
        set(artifact, "artifactStatus", SpeakingPromptSource.STATUS_READY);
        set(artifact, "readyAt", NOW);
        set(artifact, "confidence", confidence);
        return artifact;
    }

    private SpeakingPromptAiArtifact ttsArtifact(
            Long id,
            SpeakingPromptSource source,
            String prompt,
            Long generatedAssetId) {
        SpeakingPromptAuthoringAiProperties.TtsConfig config =
                properties.ttsConfig();
        SpeakingPromptAiArtifact artifact = baseArtifact(
                id,
                source,
                SpeakingPromptAiContract.Operation.TTS,
                fingerprints.ttsFingerprint(OWNER_ID, prompt, config),
                SpeakingPromptAiContract.unicodeNfcUtf8Sha256(prompt),
                config.provider(),
                config.model(),
                config.language(),
                config.purposeCode(),
                config.retentionCode());
        set(artifact, "voiceCode", config.voice());
        set(artifact, "speed", config.speed());
        set(artifact, "outputFormat", config.outputFormat());
        set(artifact, "generatedAudioAssetId", generatedAssetId);
        set(artifact, "artifactStatus", SpeakingPromptSource.STATUS_READY);
        set(artifact, "readyAt", NOW);
        return artifact;
    }

    private static SpeakingPromptAiArtifact baseArtifact(
            Long id,
            SpeakingPromptSource source,
            SpeakingPromptAiContract.Operation operation,
            String fingerprint,
            String inputSha,
            String provider,
            String model,
            String language,
            String purpose,
            String retention) {
        SpeakingPromptAiArtifact artifact =
                new SpeakingPromptAiArtifact();
        set(artifact, "id", id);
        set(artifact, "ownerLecturerId", OWNER_ID);
        set(artifact, "operation", operation.code());
        set(artifact, "operationFingerprint", fingerprint);
        set(artifact, "inputSourceRevision",
                source.getSourceRevision());
        set(artifact, "inputSha256", inputSha);
        set(artifact, "providerCode", provider);
        set(artifact, "modelCode", model);
        set(artifact, "languageTag", language);
        set(artifact, "contractVersion",
                SpeakingPromptAiContract.CONTRACT_VERSION);
        set(artifact, "purposeCode", purpose);
        set(artifact, "retentionCode", retention);
        return artifact;
    }

    private static LecturerAsset audioAsset(Long id, String sourceType) {
        LecturerAsset asset = new LecturerAsset();
        asset.setId(id);
        asset.setOwnerLecturerId(OWNER_ID);
        asset.setContentVerified(true);
        asset.setStatus("ACTIVE");
        asset.setAssetType("AUDIO");
        asset.setSourceType(sourceType);
        asset.setSha256("a".repeat(64));
        return asset;
    }

    private JsonNode root(
            String prompt,
            QuestionContent.SpeakingDelivery delivery)
            throws Exception {
        String contentJson = new AssessmentContractCodec(
                mapper, new QuestionTypeResolver())
                .writeQuestionContent(
                        QuestionContent.speakingV2(delivery),
                        CanonicalQuestionType.SPEAKING);
        return mapper.readTree("""
                {"sections":[{"skill":"SPEAKING","groups":[{"questions":[{
                  "clientId":"%s",
                  "questionType":"SPEAKING",
                  "prompt":%s,
                  "speakingPromptAuthoring":{
                    "voiceCode":"default",
                    "speed":1,
                    "outputFormat":"mp3"
                  },
                  "questionContent":%s
                }]}]}]}
                """.formatted(
                CLIENT_ID,
                mapper.writeValueAsString(prompt),
                contentJson));
    }

    private static QuestionContent.SpeakingDelivery delivery(
            QuestionContent.SpeakingPromptInputType input,
            QuestionContent.SpeakingDeliveryMode mode,
            QuestionContent.SpeakingAudioOrigin origin,
            String audio,
            Integer playLimit) {
        return new QuestionContent.SpeakingDelivery(
                input, mode, audio, origin, playLimit, 30, 60);
    }

    private static String authoringReference(String origin) {
        return "/practice/manage/drafts/" + DRAFT_ID
                + "/questions/" + CLIENT_ID
                + "/speaking-prompt/media/" + origin;
    }

    private static SpeakingPromptVersionContext.ImmutableData
    manualContext(String text) {
        return new SpeakingPromptVersionContext.ImmutableData(
                OWNER_ID,
                "manual_text",
                "text_only",
                "none",
                "manual_text",
                text,
                "",
                "",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null).withFingerprint();
    }

    private static void set(Object target, String field, Object value) {
        ReflectionTestUtils.setField(target, field, value);
    }

    private record UploadedFixture(
            SpeakingPromptPublicationService service,
            SpeakingPromptSource source,
            SpeakingPromptAiArtifact artifact,
            SpeakingPromptSourceRepository sourceRepository,
            SpeakingPromptAiArtifactRepository artifactRepository,
            SpeakingPromptTranscriptRevisionRepository revisionRepository) {
    }
}
