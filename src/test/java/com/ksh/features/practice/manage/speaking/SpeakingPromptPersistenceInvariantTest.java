package com.ksh.features.practice.manage.speaking;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpeakingPromptPersistenceInvariantTest {

    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Test
    void ttsCurrentnessBindsExactArtifactAtSameSourceRevision() {
        SpeakingPromptSource source = SpeakingPromptSource.manualText(
                10L, "question-a", 20L, HASH_A, true, 20L);
        set(source, "id", 30L);
        SpeakingPromptAiArtifact first = ttsArtifact(40L, 20L, HASH_A);
        SpeakingPromptAiArtifact second = ttsArtifact(41L, 20L, HASH_B);

        source.markTtsQueued(first.getId(), 20L);
        SpeakingPromptAiTask firstTask = task(
                50L, first, source, SpeakingPromptAiContract.Operation.TTS);
        assertThat(source.currentFor(firstTask, first)).isTrue();
        assertThat(source.currentForArtifact(second)).isFalse();

        source.markTtsQueued(second.getId(), 20L);
        SpeakingPromptAiTask secondTask = task(
                51L, second, source, SpeakingPromptAiContract.Operation.TTS);
        assertThat(source.getSourceRevision()).isEqualTo(1L);
        assertThat(source.currentFor(firstTask, first)).isFalse();
        assertThat(source.currentFor(secondTask, second)).isTrue();
    }

    @Test
    void queuedTtsClearsStaleGeneratedIdentityBeforeBindingNewArtifact() {
        SpeakingPromptSource source = SpeakingPromptSource.manualText(
                10L, "question-a", 20L, HASH_A, true, 20L);
        source.markTtsQueued(40L, 20L);
        source.attachTtsArtifact(40L, 88L);

        source.markManualConfigurationChanged(HASH_B, true, 20L);

        assertThat(source.getGeneratedAudioAssetId()).isEqualTo(88L);
        assertThat(source.getCurrentTtsArtifactId()).isNull();
        assertThat(source.getAudioSyncStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_STALE);

        source.markTtsQueued(41L, 20L);

        assertThat(source.getGeneratedAudioAssetId()).isNull();
        assertThat(source.getActiveAudioAssetId()).isNull();
        assertThat(source.getCurrentTtsArtifactId()).isEqualTo(41L);
        assertThat(source.getAudioSyncStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_QUEUED);
    }

    @Test
    void sourceCancellationCannotCorruptSharedReusableArtifactOutcome() {
        SpeakingPromptAiArtifact shared = ttsArtifact(40L, 20L, HASH_A);
        set(shared, "artifactStatus", SpeakingPromptSource.STATUS_READY);
        set(shared, "generatedAudioAssetId", 88L);
        SpeakingPromptSource first = SpeakingPromptSource.manualText(
                10L, "question-a", 20L, HASH_A, true, 20L);
        SpeakingPromptSource second = SpeakingPromptSource.manualText(
                11L, "question-b", 20L, HASH_A, true, 20L);
        set(first, "id", 30L);
        set(second, "id", 31L);
        first.markTtsQueued(shared.getId(), 20L);
        second.markTtsQueued(shared.getId(), 20L);

        first.markOperationCancelled(
                SpeakingPromptAiContract.Operation.TTS, 20L);

        assertThat(first.getCurrentTtsArtifactId()).isNull();
        assertThat(first.getSourceRevision()).isEqualTo(2L);
        assertThat(first.getAudioSyncStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_CANCELLED);
        assertThat(shared.getArtifactStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_READY);
        assertThat(shared.getGeneratedAudioAssetId()).isEqualTo(88L);
        assertThatThrownBy(() -> shared.markTtsReady(
                null, 99L, LocalDateTime.of(2026, 7, 26, 10, 0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outcome is immutable");
        assertThat(second.currentForArtifact(shared)).isTrue();
        second.attachTtsArtifact(shared.getId(), shared.getGeneratedAudioAssetId());
        assertThat(second.getActiveAudioAssetId()).isEqualTo(88L);
    }

    @Test
    void lecturerTranscriptEditSelectsSourceRevisionWithoutMutatingSharedArtifact() {
        SpeakingPromptAiArtifact shared = sttArtifact(40L, 20L, 77L);
        set(shared, "artifactStatus", SpeakingPromptSource.STATUS_READY);
        set(shared, "providerTranscriptText", "공유 제공자 전사");
        SpeakingPromptSource first = SpeakingPromptSource.audioUpload(
                10L, "question-a", 20L, 77L, 20L);
        SpeakingPromptSource second = SpeakingPromptSource.audioUpload(
                11L, "question-b", 20L, 77L, 20L);
        first.markSttQueued(shared.getId(), 20L);
        second.markSttQueued(shared.getId(), 20L);
        first.attachSttArtifact(shared.getId(), 90L, false);
        second.attachSttArtifact(shared.getId(), 90L, false);

        first.recordTranscriptEdit(
                91L, 20L, true, LocalDateTime.of(2026, 7, 26, 10, 0));

        assertThat(first.getCurrentTranscriptRevisionId()).isEqualTo(91L);
        assertThat(second.getCurrentTranscriptRevisionId()).isEqualTo(90L);
        assertThat(ReflectionTestUtils.getField(
                shared, "providerTranscriptText"))
                .isEqualTo("공유 제공자 전사");
        assertThat(shared.getArtifactStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_READY);
    }

    @Test
    void switchingToManualTextRetainsOriginalUploadWithoutMakingItActive() {
        SpeakingPromptSource source = SpeakingPromptSource.audioUpload(
                10L, "question-a", 20L, 77L, 20L);

        source.switchToManualText(HASH_A, true, 20L);

        assertThat(source.getInputType())
                .isEqualTo(SpeakingPromptSource.INPUT_MANUAL_TEXT);
        assertThat(source.getOriginalAudioAssetId()).isEqualTo(77L);
        assertThat(source.getActiveAudioAssetId()).isNull();
        assertThat(source.getCurrentSttArtifactId()).isNull();
        assertThat(source.getAudioSyncStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_STALE);
    }

    @Test
    void attemptedRowCannotBeReclaimedEvenWhenExpiredLeaseNeedsRecoveryRouting() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 26, 10, 0);
        SpeakingPromptAiTask task = new SpeakingPromptAiTask();
        set(task, "taskStatus", SpeakingPromptAiTask.STATUS_QUEUED);
        set(task, "attemptCount", 0);
        set(task, "maxAttempts", 2);
        set(task, "nextAttemptAt", now);

        assertThat(task.canClaim(now)).isTrue();
        task.claim("node-a:claim-1", now, now.plusMinutes(3));

        assertThat(task.getAttemptCount()).isEqualTo(1);
        assertThat(task.ownsLiveLease("node-a:claim-1", now.plusMinutes(1)))
                .isTrue();
        assertThat(task.ownsLiveLease("node-b:stale", now.plusMinutes(1)))
                .isFalse();
        assertThat(task.canClaim(now.plusMinutes(1))).isFalse();
        assertThat(task.canClaim(now.plusMinutes(3))).isTrue();

        assertThatThrownBy(() -> task.claim(
                "node-b:claim-2",
                now.plusMinutes(3),
                now.plusMinutes(6)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be claimed again");

        assertThat(task.getAttemptCount()).isEqualTo(1);
        assertThat(task.attemptsExhausted()).isFalse();
        assertThat(task.ownsLiveLease("node-a:claim-1", now.plusMinutes(4)))
                .isFalse();
        assertThat(task.ownsLiveLease("node-b:claim-2", now.plusMinutes(4)))
                .isFalse();
    }

    private static SpeakingPromptAiArtifact ttsArtifact(
            Long id,
            Long ownerId,
            String fingerprint) {
        SpeakingPromptAiArtifact artifact = artifact(
                id, ownerId, SpeakingPromptAiContract.Operation.TTS, fingerprint);
        set(artifact, "voiceCode", "alloy");
        set(artifact, "speed", java.math.BigDecimal.ONE);
        set(artifact, "outputFormat", "mp3");
        return artifact;
    }

    private static SpeakingPromptAiArtifact sttArtifact(
            Long id,
            Long ownerId,
            Long inputAssetId) {
        SpeakingPromptAiArtifact artifact = artifact(
                id, ownerId, SpeakingPromptAiContract.Operation.STT, HASH_A);
        set(artifact, "inputAudioAssetId", inputAssetId);
        return artifact;
    }

    private static SpeakingPromptAiArtifact artifact(
            Long id,
            Long ownerId,
            SpeakingPromptAiContract.Operation operation,
            String fingerprint) {
        SpeakingPromptAiArtifact artifact = new SpeakingPromptAiArtifact();
        set(artifact, "id", id);
        set(artifact, "ownerLecturerId", ownerId);
        set(artifact, "operation", operation.code());
        set(artifact, "operationFingerprint", fingerprint);
        set(artifact, "inputSourceRevision", 1L);
        set(artifact, "inputSha256", HASH_A);
        set(artifact, "providerCode", "openai");
        set(artifact, "modelCode", "model");
        set(artifact, "languageTag", "ko");
        set(artifact, "contractVersion", SpeakingPromptAiContract.CONTRACT_VERSION);
        set(artifact, "purposeCode", "speaking_prompt_" + operation.code());
        set(artifact, "retentionCode", "provider_default");
        set(artifact, "artifactStatus", SpeakingPromptSource.STATUS_QUEUED);
        return artifact;
    }

    private static SpeakingPromptAiTask task(
            Long id,
            SpeakingPromptAiArtifact artifact,
            SpeakingPromptSource source,
            SpeakingPromptAiContract.Operation operation) {
        SpeakingPromptAiTask task = new SpeakingPromptAiTask();
        set(task, "id", id);
        set(task, "artifactId", artifact.getId());
        set(task, "sourceId", source.getId());
        set(task, "ownerLecturerId", source.getOwnerLecturerId());
        set(task, "operation", operation.code());
        set(task, "sourceInputType", source.getInputType());
        set(task, "operationFingerprint", artifact.getOperationFingerprint());
        set(task, "expectedSourceRevision", source.getSourceRevision());
        set(task, "taskStatus", SpeakingPromptAiTask.STATUS_QUEUED);
        set(task, "attemptCount", 0);
        set(task, "maxAttempts", 4);
        return task;
    }

    private static void set(Object target, String field, Object value) {
        ReflectionTestUtils.setField(target, field, value);
    }
}
