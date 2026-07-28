package com.ksh.features.practice.manage.speaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeakingPromptRetryPolicyTest {

    @Test
    void failedExactRequestCannotRetryBeforeCooldown() {
        Fixture fixture = fixture();
        set(fixture.failedTask, "updatedAt", LocalDateTime.now());

        SpeakingPromptAuthoringService.RetryResult result =
                fixture.service.retryCurrentOperation(fixture.command);

        assertThat(result.queued()).isFalse();
        assertThat(result.status()).isEqualTo("cooldown");
        assertThat(result.retryAfterSeconds()).isPositive();
        verify(fixture.tasks, never()).insertRetrySuccessor(
                anyLong(),
                anyLong(),
                anyLong(),
                any(),
                any(),
                any(),
                anyLong(),
                anyInt(),
                anyInt(),
                any(),
                anyLong());
    }

    @Test
    void hourlyAttemptQuotaBlocksManualRetryWithoutHidingOldTask() {
        Fixture fixture = fixture();
        set(
                fixture.failedTask,
                "updatedAt",
                LocalDateTime.now().minusMinutes(2));
        when(fixture.tasks.countProviderAttemptsSince(anyLong(), any()))
                .thenReturn(20L);

        SpeakingPromptAuthoringService.RetryResult result =
                fixture.service.retryCurrentOperation(fixture.command);

        assertThat(result)
                .isEqualTo(new SpeakingPromptAuthoringService.RetryResult(
                        false, 0L, "quota"));
        verify(fixture.tasks, never()).insertRetrySuccessor(
                anyLong(),
                anyLong(),
                anyLong(),
                any(),
                any(),
                any(),
                anyLong(),
                anyInt(),
                anyInt(),
                any(),
                anyLong());
        assertThat(fixture.failedTask.getTaskStatus())
                .isEqualTo(SpeakingPromptAiTask.STATUS_FAILED);
    }

    @Test
    void exhaustedTaskCannotBeResetOrRequeuedInPlace() {
        Fixture fixture = fixture();
        set(
                fixture.failedTask,
                "updatedAt",
                LocalDateTime.now().minusMinutes(2));
        set(fixture.failedTask, "attemptCount", 4);
        set(fixture.failedTask, "maxAttempts", 4);

        SpeakingPromptAuthoringService.RetryResult result =
                fixture.service.retryCurrentOperation(fixture.command);

        assertThat(result.status()).isEqualTo("not_retryable");
        assertThat(result.queued()).isFalse();
        assertThat(fixture.failedTask.getTaskStatus())
                .isEqualTo(SpeakingPromptAiTask.STATUS_FAILED);
        verify(fixture.tasks, never()).save(fixture.failedTask);
    }

    private static Fixture fixture() {
        String prompt = "질문을 듣고 답하세요.";
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
        SpeakingPromptSource source = SpeakingPromptSource.manualText(
                10L,
                "question-a",
                20L,
                fingerprints.exactTextSha256(prompt),
                true,
                20L);
        set(source, "id", 30L);
        source.markTtsQueued(40L, 20L);
        set(source, "audioSyncStatus", SpeakingPromptSource.STATUS_FAILED_RETRYABLE);
        SpeakingPromptAiArtifact artifact = new SpeakingPromptAiArtifact();
        set(artifact, "id", 40L);
        set(artifact, "ownerLecturerId", 20L);
        set(artifact, "operation", SpeakingPromptAiContract.Operation.TTS.code());
        set(artifact, "operationFingerprint", "b".repeat(64));
        set(
                artifact,
                "inputSha256",
                SpeakingPromptAiContract.unicodeNfcUtf8Sha256(prompt));
        set(artifact, "providerCode", "openai");
        set(artifact, "modelCode", "speech-model");
        set(artifact, "languageTag", "ko");
        set(artifact, "outputFormat", "mp3");
        set(artifact, "contractVersion", SpeakingPromptAiContract.CONTRACT_VERSION);
        set(artifact, "purposeCode", "speaking_prompt_tts");
        set(artifact, "retentionCode", "provider_default");
        set(artifact, "artifactStatus", SpeakingPromptSource.STATUS_QUEUED);
        SpeakingPromptAiTask failedTask = new SpeakingPromptAiTask();
        set(failedTask, "id", 50L);
        set(failedTask, "artifactId", 40L);
        set(failedTask, "sourceId", 30L);
        set(failedTask, "ownerLecturerId", 20L);
        set(failedTask, "operation", SpeakingPromptAiContract.Operation.TTS.code());
        set(failedTask, "operationFingerprint", "b".repeat(64));
        set(failedTask, "taskStatus", SpeakingPromptAiTask.STATUS_FAILED);
        set(failedTask, "attemptCount", 1);
        set(failedTask, "maxAttempts", 4);
        set(
                failedTask,
                "publicErrorCategory",
                SpeakingPromptAiContract.PublicErrorCategory.TIMEOUT.name());
        PracticeDraft draft = mock(PracticeDraft.class);
        when(draft.getId()).thenReturn(10L);
        ObjectNode root = new ObjectMapper().createObjectNode();
        ObjectNode question = root.putObject("question");
        question.put("prompt", prompt);
        when(authority.authorizeAndLock(
                10L,
                "question-a",
                20L,
                com.ksh.features.practice.governance.PracticeAction.EDIT))
                .thenReturn(new SpeakingPromptDraftAuthority.AuthorizedDraft(
                        draft, 20L, 20L, root, question));
        when(sources.findByDraftAndClientForUpdate(10L, "question-a"))
                .thenReturn(Optional.of(source));
        when(artifacts.findByIdForUpdate(40L))
                .thenReturn(Optional.of(artifact));
        when(tasks.findActiveByFingerprint(
                20L,
                SpeakingPromptAiContract.Operation.TTS.code(),
                "b".repeat(64)))
                .thenReturn(Optional.empty());
        when(tasks.findLatestByFingerprint(anyLong(), any(), any(), any()))
                .thenReturn(List.of(failedTask));
        when(tasks.findByIdForUpdate(50L)).thenReturn(Optional.of(failedTask));
        SpeakingPromptAuthoringAiProperties properties =
                operationalProperties();
        SpeakingPromptAuthoringService service =
                new SpeakingPromptAuthoringService(
                        authority,
                        sources,
                        artifacts,
                        tasks,
                        revisions,
                        fingerprints,
                        mock(SpeakingPromptAssetService.class),
                        properties);
        return new Fixture(
                service,
                tasks,
                failedTask,
                new SpeakingPromptAuthoringService.RetryCommand(
                        10L,
                        "question-a",
                        20L,
                        1L,
                        0L,
                        SpeakingPromptAiContract.Operation.TTS));
    }

    private static SpeakingPromptAuthoringAiProperties operationalProperties() {
        SpeakingPromptAuthoringAiProperties properties =
                new SpeakingPromptAuthoringAiProperties();
        properties.setWorkerEnabled(true);
        properties.getTts().setEnabled(true);
        properties.getTts().setProvider("openai");
        properties.getTts().setBaseUrl("https://provider.invalid");
        properties.getTts().setApiKey("private-key");
        properties.getTts().setModel("speech-model");
        return properties;
    }

    private static void set(Object target, String field, Object value) {
        ReflectionTestUtils.setField(target, field, value);
    }

    private record Fixture(
            SpeakingPromptAuthoringService service,
            SpeakingPromptAiTaskRepository tasks,
            SpeakingPromptAiTask failedTask,
            SpeakingPromptAuthoringService.RetryCommand command) {
    }
}
