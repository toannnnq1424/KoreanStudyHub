package com.ksh.features.practice.manage.speaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.governance.PracticeAction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

class SpeakingPromptAuthoringStateServiceTest {

    @Test
    void emptyProjectionIsAuthorizedProviderFreeAndSecretSafe() throws Exception {
        SpeakingPromptDraftAuthority authority =
                mock(SpeakingPromptDraftAuthority.class);
        SpeakingPromptSourceRepository sources =
                mock(SpeakingPromptSourceRepository.class);
        ObjectNode root = new ObjectMapper().createObjectNode();
        ObjectNode question = root.putObject("question");
        question.put("prompt", "다음 질문에 답하세요.");
        PracticeDraft draft = new PracticeDraft(
                "Draft", "", "PRIVATE", null, "DRAFT", 81L, "{}");
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                new SpeakingPromptDraftAuthority.AuthorizedDraft(
                        draft, 81L, 81L, root, question);
        when(authority.authorize(
                91L, "speaking-a", 81L, PracticeAction.EDIT))
                .thenReturn(authorized);
        when(authority.authoringOptions(authorized))
                .thenReturn(new SpeakingPromptDraftAuthority.DraftAuthoringOptions(
                        SpeakingPromptSource.INPUT_MANUAL_TEXT,
                        true,
                        "default",
                        BigDecimal.ONE,
                        "mp3"));
        when(sources.findByDraftIdAndQuestionClientId(
                91L, "speaking-a")).thenReturn(Optional.empty());
        SpeakingPromptAssetService assets =
                mock(SpeakingPromptAssetService.class);
        when(assets.hasExcelStaging(91L, 81L, "speaking-a"))
                .thenReturn(true);
        SpeakingPromptAuthoringStateService service =
                new SpeakingPromptAuthoringStateService(
                        authority,
                        sources,
                        mock(SpeakingPromptAiArtifactRepository.class),
                        mock(SpeakingPromptAiTaskRepository.class),
                        mock(SpeakingPromptTranscriptRevisionRepository.class),
                        assets,
                        mock(SpeakingPromptFingerprintService.class),
                        new SpeakingPromptAuthoringAiProperties());

        SpeakingPromptAuthoringStateService.EditorState state =
                service.load(91L, "speaking-a", 81L);
        String json = new ObjectMapper().writeValueAsString(state);

        assertThat(state.sourceRevision()).isZero();
        assertThat(state.draftVersion()).isZero();
        assertThat(state.inputType())
                .isEqualTo(SpeakingPromptSource.INPUT_MANUAL_TEXT);
        assertThat(state.manualText()).isEqualTo("다음 질문에 답하세요.");
        assertThat(state.excelStagingAudioAvailable()).isTrue();
        assertThat(state.toString()).doesNotContain("다음 질문에 답하세요.");
        assertThat(json)
                .contains("\"excelStagingAudioAvailable\":true")
                .doesNotContain(
                        "sourceId",
                        "artifactId",
                        "assetId",
                        "storageKey",
                        "fingerprint",
                        "apiKey",
                        "providerRequestReference");
        verify(authority).authorize(
                91L, "speaking-a", 81L, PracticeAction.EDIT);
    }

    @Test
    void getProjectsRetainedGeneratedAudioAsStaleWhenConfigIdentityChanged()
            throws Exception {
        SpeakingPromptFingerprintService fingerprints =
                new SpeakingPromptFingerprintService();
        SpeakingPromptAuthoringAiProperties properties =
                new SpeakingPromptAuthoringAiProperties();
        SpeakingPromptAuthoringAiProperties.TtsConfig approved =
                properties.ttsConfig();
        SpeakingPromptDraftAuthority authority =
                mock(SpeakingPromptDraftAuthority.class);
        SpeakingPromptSourceRepository sources =
                mock(SpeakingPromptSourceRepository.class);
        SpeakingPromptAiArtifactRepository artifacts =
                mock(SpeakingPromptAiArtifactRepository.class);
        SpeakingPromptAiTaskRepository tasks =
                mock(SpeakingPromptAiTaskRepository.class);
        SpeakingPromptAssetService assets =
                mock(SpeakingPromptAssetService.class);
        String prompt = "다음 주제에 대해 말하세요.";
        PracticeDraft draft = new PracticeDraft(
                "Draft", "", "PRIVATE", null, "DRAFT", 81L, "{}");
        ObjectNode root = new ObjectMapper().createObjectNode();
        ObjectNode question = root.putObject("question");
        question.put("prompt", prompt);
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                new SpeakingPromptDraftAuthority.AuthorizedDraft(
                        draft, 81L, 81L, root, question);
        SpeakingPromptSource source = SpeakingPromptSource.manualText(
                91L,
                "speaking-a",
                81L,
                fingerprints.exactTextSha256(prompt),
                true,
                81L);
        source.markTtsQueued(201L, 81L);
        source.attachTtsArtifact(201L, 301L);
        SpeakingPromptAiArtifact artifact = new SpeakingPromptAiArtifact();
        org.springframework.test.util.ReflectionTestUtils.setField(
                artifact, "id", 201L);
        org.springframework.test.util.ReflectionTestUtils.setField(
                artifact, "ownerLecturerId", 81L);
        org.springframework.test.util.ReflectionTestUtils.setField(
                artifact, "operation", SpeakingPromptAiContract.Operation.TTS.code());
        org.springframework.test.util.ReflectionTestUtils.setField(
                artifact, "operationFingerprint", "a".repeat(64));
        org.springframework.test.util.ReflectionTestUtils.setField(
                artifact, "modelCode", "older-model");
        org.springframework.test.util.ReflectionTestUtils.setField(
                artifact, "artifactStatus", SpeakingPromptSource.STATUS_READY);
        when(authority.authorize(
                91L, "speaking-a", 81L, PracticeAction.EDIT))
                .thenReturn(authorized);
        when(authority.authoringOptions(authorized))
                .thenReturn(new SpeakingPromptDraftAuthority.DraftAuthoringOptions(
                        SpeakingPromptSource.INPUT_MANUAL_TEXT,
                        true,
                        approved.voice(),
                        approved.speed(),
                        approved.outputFormat()));
        when(sources.findByDraftIdAndQuestionClientId(
                91L, "speaking-a")).thenReturn(Optional.of(source));
        when(artifacts.findById(201L)).thenReturn(Optional.of(artifact));
        when(tasks.findLatestByFingerprint(anyLong(), any(), any(), any()))
                .thenReturn(List.of());
        when(assets.generatedPresentation(
                anyLong(), anyLong(), anyLong(), any(), any()))
                .thenReturn(new SpeakingPromptAssetService.AssetPresentation(
                        "/opaque/generated",
                        "audio.mp3",
                        "audio/mpeg",
                        123L,
                        "AI_GENERATED"));
        SpeakingPromptAuthoringStateService service =
                new SpeakingPromptAuthoringStateService(
                        authority,
                        sources,
                        artifacts,
                        tasks,
                        mock(SpeakingPromptTranscriptRevisionRepository.class),
                        assets,
                        fingerprints,
                        properties);

        SpeakingPromptAuthoringStateService.EditorState state =
                service.load(91L, "speaking-a", 81L);

        assertThat(state.audioStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_STALE);
        assertThat(state.generatedAudioCurrent()).isFalse();
        assertThat(state.generatedAudio().contentUrl())
                .isEqualTo("/opaque/generated");
    }

    @Test
    void lowConfidenceTranscriptProjectsNeedsReviewWithoutPrivateIdentity()
            throws Exception {
        SpeakingPromptFingerprintService fingerprints =
                new SpeakingPromptFingerprintService();
        SpeakingPromptDraftAuthority authority =
                mock(SpeakingPromptDraftAuthority.class);
        SpeakingPromptSourceRepository sources =
                mock(SpeakingPromptSourceRepository.class);
        SpeakingPromptAiArtifactRepository artifacts =
                mock(SpeakingPromptAiArtifactRepository.class);
        SpeakingPromptAiTaskRepository tasks =
                mock(SpeakingPromptAiTaskRepository.class);
        SpeakingPromptTranscriptRevisionRepository revisions =
                mock(SpeakingPromptTranscriptRevisionRepository.class);
        SpeakingPromptAssetService assets =
                mock(SpeakingPromptAssetService.class);
        PracticeDraft draft = new PracticeDraft(
                "Draft", "", "PRIVATE", null, "DRAFT", 81L, "{}");
        ObjectNode root = new ObjectMapper().createObjectNode();
        ObjectNode question = root.putObject("question");
        question.put("prompt", "");
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                new SpeakingPromptDraftAuthority.AuthorizedDraft(
                        draft, 81L, 81L, root, question);
        SpeakingPromptSource source = SpeakingPromptSource.audioUpload(
                91L, "speaking-a", 81L, 71L, 81L);
        SpeakingPromptAiArtifact artifact = new SpeakingPromptAiArtifact();
        org.springframework.test.util.ReflectionTestUtils.setField(
                artifact, "id", 201L);
        org.springframework.test.util.ReflectionTestUtils.setField(
                artifact, "ownerLecturerId", 81L);
        org.springframework.test.util.ReflectionTestUtils.setField(
                artifact, "operation",
                SpeakingPromptAiContract.Operation.STT.code());
        org.springframework.test.util.ReflectionTestUtils.setField(
                artifact, "operationFingerprint", "a".repeat(64));
        org.springframework.test.util.ReflectionTestUtils.setField(
                artifact, "inputAudioAssetId", 71L);
        org.springframework.test.util.ReflectionTestUtils.setField(
                artifact, "artifactStatus",
                SpeakingPromptSource.STATUS_READY);
        org.springframework.test.util.ReflectionTestUtils.setField(
                artifact, "confidence", new BigDecimal("0.30"));
        source.markSttQueued(201L, 81L);
        source.attachSttArtifact(201L, 301L, true);
        String transcript = "질문을 듣고 대답하세요.";
        SpeakingPromptTranscriptRevision revision =
                SpeakingPromptTranscriptRevision.provider(
                        artifact,
                        1,
                        transcript,
                        fingerprints.exactTextSha256(transcript),
                        null);
        org.springframework.test.util.ReflectionTestUtils.setField(
                revision, "id", 301L);
        when(authority.authorize(
                91L, "speaking-a", 81L, PracticeAction.EDIT))
                .thenReturn(authorized);
        when(authority.authoringOptions(authorized))
                .thenReturn(new SpeakingPromptDraftAuthority.DraftAuthoringOptions(
                        SpeakingPromptSource.INPUT_AUDIO_UPLOAD,
                        false,
                        "default",
                        BigDecimal.ONE,
                        "mp3"));
        when(sources.findByDraftIdAndQuestionClientId(
                91L, "speaking-a")).thenReturn(Optional.of(source));
        when(artifacts.findById(201L)).thenReturn(Optional.of(artifact));
        when(revisions.findById(301L)).thenReturn(Optional.of(revision));
        when(tasks.findLatestByFingerprint(anyLong(), any(), any(), any()))
                .thenReturn(List.of());
        when(assets.originalPresentation(
                anyLong(), anyLong(), anyLong(), any(), any()))
                .thenReturn(new SpeakingPromptAssetService.AssetPresentation(
                        "/opaque/original",
                        "prompt.mp3",
                        "audio/mpeg",
                        2_000L,
                        "LECTURER_UPLOAD"));
        SpeakingPromptAuthoringStateService service =
                new SpeakingPromptAuthoringStateService(
                        authority,
                        sources,
                        artifacts,
                        tasks,
                        revisions,
                        assets,
                        fingerprints,
                        new SpeakingPromptAuthoringAiProperties());

        SpeakingPromptAuthoringStateService.EditorState state =
                service.load(91L, "speaking-a", 81L);
        String json = new ObjectMapper().writeValueAsString(state);

        assertThat(state.transcriptStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_NEEDS_REVIEW);
        assertThat(state.lecturerContext()).isEqualTo(transcript);
        assertThat(state.transcriptConfirmed()).isFalse();
        assertThat(state.transcriptConfidence())
                .isEqualByComparingTo("0.30");
        assertThat(json)
                .contains(
                        "\"transcriptStatus\":\"needs_review\"",
                        "\"transcriptConfirmed\":false")
                .doesNotContain(
                        "assetId",
                        "artifactId",
                        "storageKey",
                        "providerRequestReference");
    }
}
