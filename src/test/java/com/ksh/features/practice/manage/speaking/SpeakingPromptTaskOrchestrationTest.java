package com.ksh.features.practice.manage.speaking;

import com.ksh.entities.PracticeDraft;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SpeakingPromptTaskOrchestrationTest {

    @Test
    void readySharedArtifactFansOutToEveryStillCurrentSourceWithoutProviderClaim() {
        Fixture fixture = fixture(true);

        Optional<SpeakingPromptTaskTransactions.ClaimedTask> claim =
                fixture.transactions.claim(
                        fixture.task.getId(),
                        "node-a:claim",
                        fixture.now);

        assertThat(claim).isEmpty();
        assertThat(fixture.task.getTaskStatus())
                .isEqualTo(SpeakingPromptAiTask.STATUS_SUCCEEDED);
        assertThat(fixture.first.getAudioSyncStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_READY);
        assertThat(fixture.second.getAudioSyncStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_READY);
        assertThat(fixture.first.getActiveAudioAssetId()).isEqualTo(88L);
        assertThat(fixture.second.getActiveAudioAssetId()).isEqualTo(88L);
        verify(fixture.assets).linkExistingGeneratedAsset(
                10L, 20L, "question-a", 88L);
        verify(fixture.assets).linkExistingGeneratedAsset(
                11L, 20L, "question-b", 88L);

        InOrder lockOrder = inOrder(
                fixture.users,
                fixture.authority,
                fixture.sources,
                fixture.artifacts,
                fixture.tasks);
        lockOrder.verify(fixture.tasks).findById(50L);
        lockOrder.verify(fixture.users).findByIdForUpdate(20L);
        lockOrder.verify(fixture.authority).lockDraft(10L, 20L);
        lockOrder.verify(fixture.authority).lockDraft(11L, 20L);
        lockOrder.verify(fixture.sources).findByIdsForUpdate(List.of(30L, 31L));
        lockOrder.verify(fixture.artifacts).findByIdForUpdate(40L);
        lockOrder.verify(fixture.tasks).findByIdForUpdate(50L);
    }

    @Test
    void staleLateCompletionClosesTaskWithoutChangingReusableArtifact() {
        Fixture fixture = fixture(false);
        when(fixture.sources
                .findByCurrentTtsArtifactIdOrderByDraftIdAscIdAsc(40L))
                .thenAnswer(invocation ->
                        fixture.first.getCurrentTtsArtifactId() == null
                                ? List.of()
                                : List.of(fixture.first));
        when(fixture.sources.findByIdsForUpdate(any()))
                .thenReturn(List.of(fixture.first));

        SpeakingPromptTaskTransactions.ClaimedTask claim =
                fixture.transactions.claim(
                        50L, "node-a:claim", fixture.now).orElseThrow();
        fixture.first.markOperationCancelled(
                SpeakingPromptAiContract.Operation.TTS, 20L);

        boolean accepted = fixture.transactions.completeTts(
                claim,
                null,
                null,
                "irrelevant-stale-input",
                fixture.now.plusSeconds(1));

        assertThat(accepted).isFalse();
        assertThat(fixture.task.getTaskStatus())
                .isEqualTo(SpeakingPromptAiTask.STATUS_SUPERSEDED);
        assertThat(fixture.artifact.getArtifactStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_QUEUED);
        assertThat(fixture.artifact.getGeneratedAudioAssetId()).isNull();
        verifyNoInteractions(fixture.assets);
    }

    @Test
    void concurrencyLimitDefersWithoutConsumingProviderAttemptOrMutatingArtifact() {
        Fixture fixture = fixture(false);
        when(fixture.tasks.countProcessingByOwnerExcluding(20L, 50L))
                .thenReturn(4L);

        Optional<SpeakingPromptTaskTransactions.ClaimedTask> claim =
                fixture.transactions.claim(
                        50L, "node-a:claim", fixture.now);

        assertThat(claim).isEmpty();
        assertThat(fixture.task.getTaskStatus())
                .isEqualTo(SpeakingPromptAiTask.STATUS_RETRY_WAIT);
        assertThat(fixture.task.getAttemptCount()).isZero();
        assertThat(fixture.artifact.getArtifactStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_QUEUED);
        assertThat(fixture.first.getAudioSyncStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_QUEUED);
        verify(fixture.tasks, never()).countProviderAttemptsSince(
                anyLong(), any());
    }

    @Test
    void reroutedSharedTaskCountsConcurrencyAgainstExecutionDraft() {
        Fixture fixture = fixture(false);
        fixture.first.markOperationCancelled(
                SpeakingPromptAiContract.Operation.TTS, 20L);
        when(fixture.sources
                .findByCurrentTtsArtifactIdOrderByDraftIdAscIdAsc(40L))
                .thenReturn(List.of(fixture.second));

        SpeakingPromptTaskTransactions.ClaimedTask claim =
                fixture.transactions.claim(
                        50L, "node-b:claim", fixture.now).orElseThrow();

        assertThat(claim.executionSourceId()).isEqualTo(31L);
        assertThat(claim.draftId()).isEqualTo(11L);
        verify(fixture.tasks).countProcessingByDraftExcluding(11L, 50L);
        verify(fixture.tasks, never()).countProcessingByDraftExcluding(10L, 50L);
    }

    @Test
    void sourceSpecificFailureFansOutWithoutTurningSharedArtifactIntoFailureState() {
        Fixture fixture = fixture(false);
        SpeakingPromptTaskTransactions.ClaimedTask claim =
                fixture.transactions.claim(
                        50L, "node-a:claim", fixture.now).orElseThrow();

        boolean recorded = fixture.transactions.fail(
                claim,
                SpeakingPromptAiContract.PublicErrorCategory.TIMEOUT,
                false,
                fixture.now.plusSeconds(1));

        assertThat(recorded).isTrue();
        assertThat(fixture.task.getTaskStatus())
                .isEqualTo(SpeakingPromptAiTask.STATUS_FAILED);
        assertThat(fixture.first.getAudioSyncStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_FAILED_FINAL);
        assertThat(fixture.second.getAudioSyncStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_FAILED_FINAL);
        assertThat(fixture.artifact.getArtifactStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_QUEUED);
        assertThat(fixture.artifact.getPublicErrorCategory()).isNull();
    }

    @Test
    void retryableProviderAttemptClosesVisibleRowAndSchedulesBoundedSuccessor() {
        Fixture fixture = fixture(false);
        LocalDateTime nextAttempt = fixture.now.plusSeconds(11);
        when(fixture.tasks.insertRetrySuccessor(
                40L,
                30L,
                20L,
                SpeakingPromptAiContract.Operation.TTS.code(),
                SpeakingPromptSource.INPUT_MANUAL_TEXT,
                "f".repeat(64),
                1L,
                1,
                4,
                nextAttempt,
                20L))
                .thenReturn(1);
        SpeakingPromptTaskTransactions.ClaimedTask claim =
                fixture.transactions.claim(
                        50L, "node-a:claim", fixture.now).orElseThrow();

        boolean recorded = fixture.transactions.fail(
                claim,
                SpeakingPromptAiContract.PublicErrorCategory.TIMEOUT,
                true,
                fixture.now.plusSeconds(1));

        assertThat(recorded).isTrue();
        assertThat(fixture.task.getTaskStatus())
                .isEqualTo(SpeakingPromptAiTask.STATUS_FAILED);
        assertThat(fixture.task.getAttemptCount()).isEqualTo(1);
        assertThat(fixture.first.getAudioSyncStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_FAILED_RETRYABLE);
        assertThat(fixture.second.getAudioSyncStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_FAILED_RETRYABLE);
        assertThat(fixture.artifact.getArtifactStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_QUEUED);
        verify(fixture.tasks).insertRetrySuccessor(
                40L,
                30L,
                20L,
                SpeakingPromptAiContract.Operation.TTS.code(),
                SpeakingPromptSource.INPUT_MANUAL_TEXT,
                "f".repeat(64),
                1L,
                1,
                4,
                nextAttempt,
                20L);
    }

    @Test
    void expiredLeaseIsClosedAndRecoveredThroughNewDurableSuccessor() {
        Fixture fixture = fixture(false);
        LocalDateTime expiredAt = fixture.now.plusMinutes(3);
        LocalDateTime successorAt = expiredAt.plusSeconds(10);
        when(fixture.tasks.insertRetrySuccessor(
                40L,
                30L,
                20L,
                SpeakingPromptAiContract.Operation.TTS.code(),
                SpeakingPromptSource.INPUT_MANUAL_TEXT,
                "f".repeat(64),
                1L,
                1,
                4,
                successorAt,
                20L))
                .thenReturn(1);
        assertThat(fixture.transactions.claim(
                50L, "node-a:claim", fixture.now)).isPresent();

        Optional<SpeakingPromptTaskTransactions.ClaimedTask> recovered =
                fixture.transactions.claim(
                        50L, "node-b:claim", expiredAt);

        assertThat(recovered).isEmpty();
        assertThat(fixture.task.getTaskStatus())
                .isEqualTo(SpeakingPromptAiTask.STATUS_FAILED);
        assertThat(fixture.task.getAttemptCount()).isEqualTo(1);
        assertThat(fixture.task.ownsLiveLease(
                "node-a:claim", expiredAt.plusSeconds(1))).isFalse();
        verify(fixture.tasks).insertRetrySuccessor(
                40L,
                30L,
                20L,
                SpeakingPromptAiContract.Operation.TTS.code(),
                SpeakingPromptSource.INPUT_MANUAL_TEXT,
                "f".repeat(64),
                1L,
                1,
                4,
                successorAt,
                20L);
    }

    @Test
    void outcomeReconcilerTerminalizesFailedWithPreservedCategoryAndNoCharge() {
        Fixture fixture = fixture(false);
        set(fixture.task, "attemptCount", 4);
        set(fixture.task, "maxAttempts", 4);
        set(
                fixture.task,
                "publicErrorCategory",
                SpeakingPromptAiContract.PublicErrorCategory
                        .PROVIDER_REJECTED.name());

        Optional<SpeakingPromptTaskTransactions.ClaimedTask> claim =
                fixture.transactions.claim(
                        50L, "node-a:must-not-claim", fixture.now);

        assertThat(claim).isEmpty();
        assertThat(fixture.task.getTaskStatus())
                .isEqualTo(SpeakingPromptAiTask.STATUS_FAILED);
        assertThat(fixture.task.getPublicErrorCategory())
                .isEqualTo(
                        SpeakingPromptAiContract.PublicErrorCategory
                                .PROVIDER_REJECTED.name());
        assertThat(fixture.task.getAttemptCount()).isEqualTo(4);
        assertThat(fixture.first.getAudioSyncStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_FAILED_FINAL);
        assertThat(fixture.second.getAudioSyncStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_FAILED_FINAL);
        assertThat(fixture.artifact.getArtifactStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_QUEUED);
        verify(fixture.tasks, never()).insertRetrySuccessor(
                anyLong(),
                anyLong(),
                anyLong(),
                any(),
                any(),
                any(),
                anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                any(),
                anyLong());
        verifyNoInteractions(fixture.assets);
    }

    private static Fixture fixture(boolean readyArtifact) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 26, 10, 0);
        String prompt = "질문을 듣고 답하세요.";
        String exactHash = SpeakingPromptAiContract.exactBytesSha256(
                prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String nfcHash =
                SpeakingPromptAiContract.unicodeNfcUtf8Sha256(prompt);
        SpeakingPromptSource first = SpeakingPromptSource.manualText(
                10L, "question-a", 20L, exactHash, true, 20L);
        SpeakingPromptSource second = SpeakingPromptSource.manualText(
                11L, "question-b", 20L, exactHash, true, 20L);
        set(first, "id", 30L);
        set(second, "id", 31L);
        first.markTtsQueued(40L, 20L);
        second.markTtsQueued(40L, 20L);
        SpeakingPromptAiArtifact artifact = new SpeakingPromptAiArtifact();
        set(artifact, "id", 40L);
        set(artifact, "ownerLecturerId", 20L);
        set(artifact, "operation", SpeakingPromptAiContract.Operation.TTS.code());
        set(artifact, "operationFingerprint", "f".repeat(64));
        set(artifact, "inputSha256", nfcHash);
        set(
                artifact,
                "artifactStatus",
                readyArtifact
                        ? SpeakingPromptSource.STATUS_READY
                        : SpeakingPromptSource.STATUS_QUEUED);
        if (readyArtifact) {
            set(artifact, "generatedAudioAssetId", 88L);
        }
        SpeakingPromptAiTask task = new SpeakingPromptAiTask();
        set(task, "id", 50L);
        set(task, "artifactId", 40L);
        set(task, "sourceId", 30L);
        set(task, "ownerLecturerId", 20L);
        set(task, "operation", SpeakingPromptAiContract.Operation.TTS.code());
        set(task, "sourceInputType", SpeakingPromptSource.INPUT_MANUAL_TEXT);
        set(task, "operationFingerprint", "f".repeat(64));
        set(task, "expectedSourceRevision", 1L);
        set(task, "taskStatus", SpeakingPromptAiTask.STATUS_QUEUED);
        set(task, "attemptCount", 0);
        set(task, "maxAttempts", 4);
        set(task, "requestedBy", 20L);
        SpeakingPromptAiTaskRepository tasks =
                mock(SpeakingPromptAiTaskRepository.class);
        UserRepository users = mock(UserRepository.class);
        SpeakingPromptSourceRepository sources =
                mock(SpeakingPromptSourceRepository.class);
        SpeakingPromptAiArtifactRepository artifacts =
                mock(SpeakingPromptAiArtifactRepository.class);
        SpeakingPromptTranscriptRevisionRepository revisions =
                mock(SpeakingPromptTranscriptRevisionRepository.class);
        SpeakingPromptAssetService assets =
                mock(SpeakingPromptAssetService.class);
        SpeakingPromptDraftAuthority authority =
                mock(SpeakingPromptDraftAuthority.class);
        when(tasks.findById(50L)).thenReturn(Optional.of(task));
        when(tasks.findByIdForUpdate(50L)).thenReturn(Optional.of(task));
        when(users.findByIdForUpdate(20L)).thenReturn(Optional.of(mock(User.class)));
        when(sources.findById(30L)).thenReturn(Optional.of(first));
        when(sources
                .findByCurrentTtsArtifactIdOrderByDraftIdAscIdAsc(40L))
                .thenReturn(List.of(first, second));
        when(sources.findByIdsForUpdate(List.of(30L, 31L)))
                .thenReturn(List.of(first, second));
        when(artifacts.findByIdForUpdate(40L))
                .thenReturn(Optional.of(artifact));
        PracticeDraft draftA = mock(PracticeDraft.class);
        PracticeDraft draftB = mock(PracticeDraft.class);
        SpeakingPromptDraftAuthority.LockedDraft lockA =
                new SpeakingPromptDraftAuthority.LockedDraft(draftA);
        SpeakingPromptDraftAuthority.LockedDraft lockB =
                new SpeakingPromptDraftAuthority.LockedDraft(draftB);
        when(authority.lockDraft(10L, 20L)).thenReturn(lockA);
        when(authority.lockDraft(11L, 20L)).thenReturn(lockB);
        when(authority.locateInLockedDraft(lockA, "question-a"))
                .thenReturn(new SpeakingPromptDraftAuthority.DraftPrompt(
                        10L, 20L, "question-a", prompt));
        when(authority.locateInLockedDraft(lockB, "question-b"))
                .thenReturn(new SpeakingPromptDraftAuthority.DraftPrompt(
                        11L, 20L, "question-b", prompt));
        SpeakingPromptAuthoringAiProperties properties =
                new SpeakingPromptAuthoringAiProperties();
        SpeakingPromptTaskTransactions transactions =
                new SpeakingPromptTaskTransactions(
                        tasks,
                        users,
                        sources,
                        artifacts,
                        revisions,
                        assets,
                        properties,
                        new SpeakingPromptFingerprintService(),
                        authority);
        return new Fixture(
                transactions,
                tasks,
                users,
                sources,
                artifacts,
                assets,
                authority,
                task,
                artifact,
                first,
                second,
                now);
    }

    private static void set(Object target, String field, Object value) {
        ReflectionTestUtils.setField(target, field, value);
    }

    private record Fixture(
            SpeakingPromptTaskTransactions transactions,
            SpeakingPromptAiTaskRepository tasks,
            UserRepository users,
            SpeakingPromptSourceRepository sources,
            SpeakingPromptAiArtifactRepository artifacts,
            SpeakingPromptAssetService assets,
            SpeakingPromptDraftAuthority authority,
            SpeakingPromptAiTask task,
            SpeakingPromptAiArtifact artifact,
            SpeakingPromptSource first,
            SpeakingPromptSource second,
            LocalDateTime now) {
    }
}
