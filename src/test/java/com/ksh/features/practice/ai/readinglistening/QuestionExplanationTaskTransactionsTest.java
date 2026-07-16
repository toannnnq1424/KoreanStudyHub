package com.ksh.features.practice.ai.readinglistening;

import com.ksh.entities.QuestionExplanationArtifact;
import com.ksh.entities.QuestionExplanationGenerationTask;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationTaskTransactions.ClaimedTask;
import com.ksh.features.practice.repository.QuestionExplanationArtifactRepository;
import com.ksh.features.practice.repository.QuestionExplanationGenerationTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionExplanationTaskTransactionsTest {

    private QuestionExplanationGenerationTaskRepository taskRepository;
    private QuestionExplanationArtifactRepository artifactRepository;
    private QuestionExplanationTaskTransactions transactions;

    @BeforeEach
    void setUp() {
        taskRepository = mock(QuestionExplanationGenerationTaskRepository.class);
        artifactRepository = mock(QuestionExplanationArtifactRepository.class);
        transactions = new QuestionExplanationTaskTransactions(taskRepository, artifactRepository);
    }

    @Test
    void claimUsesABoundedLeaseAndIncrementsAttemptCount() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 12, 0);
        QuestionExplanationGenerationTask task = task(
                QuestionExplanationGenerationTask.STATUS_PENDING, 0, 4, null, null);
        QuestionExplanationArtifact artifact = artifact(QuestionExplanationArtifact.STATUS_PENDING);
        repositories(task, artifact);

        Optional<ClaimedTask> claimed = transactions.claim(10L, "worker:claim-1", now);

        assertThat(claimed).isPresent();
        assertThat(claimed.orElseThrow().owner()).isEqualTo("worker:claim-1");
        assertThat(task.getStatus()).isEqualTo(QuestionExplanationGenerationTask.STATUS_PROCESSING);
        assertThat(task.getAttemptCount()).isEqualTo(1);
        assertThat(task.getLeaseOwner()).isEqualTo("worker:claim-1");
        assertThat(task.getLeaseExpiresAt()).isEqualTo(now.plusMinutes(5));
    }

    @Test
    void staleCompletionCannotOverwriteWorkAfterLeaseWasReclaimed() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 12, 0);
        QuestionExplanationGenerationTask task = task(
                QuestionExplanationGenerationTask.STATUS_PROCESSING,
                1,
                4,
                "worker:claim-2",
                now.plusMinutes(5));
        QuestionExplanationArtifact artifact = artifact(QuestionExplanationArtifact.STATUS_PENDING);
        repositories(task, artifact);
        ClaimedTask stale = new ClaimedTask(
                10L, 20L, 30L, "f".repeat(64), "worker:claim-1");

        boolean completed = transactions.complete(stale, "{\"meaningVi\":\"old\"}", now);

        assertThat(completed).isFalse();
        assertThat(artifact.getStatus()).isEqualTo(QuestionExplanationArtifact.STATUS_PENDING);
        assertThat(artifact.getExplanationJson()).isNull();
        assertThat(task.getLeaseOwner()).isEqualTo("worker:claim-2");
    }

    @Test
    void retryableFailureMovesTaskToRetryWaitWithoutFailingArtifact() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 12, 0);
        QuestionExplanationGenerationTask task = task(
                QuestionExplanationGenerationTask.STATUS_PROCESSING,
                1,
                4,
                "worker:claim-1",
                now.plusMinutes(5));
        QuestionExplanationArtifact artifact = artifact(QuestionExplanationArtifact.STATUS_PENDING);
        repositories(task, artifact);
        ClaimedTask claim = new ClaimedTask(
                10L, 20L, 30L, "f".repeat(64), "worker:claim-1");

        boolean failed = transactions.fail(
                claim, "RATE_LIMIT", "Provider asked us to retry", true, now);

        assertThat(failed).isTrue();
        assertThat(task.getStatus()).isEqualTo(QuestionExplanationGenerationTask.STATUS_RETRY_WAIT);
        assertThat(task.getNextAttemptAt()).isEqualTo(now.plusSeconds(30));
        assertThat(task.getLeaseOwner()).isNull();
        assertThat(artifact.getStatus()).isEqualTo(QuestionExplanationArtifact.STATUS_PENDING);
    }

    @Test
    void exhaustedFailureTransitionsTaskAndArtifactToTerminalFailed() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 12, 0);
        QuestionExplanationGenerationTask task = task(
                QuestionExplanationGenerationTask.STATUS_PROCESSING,
                4,
                4,
                "worker:claim-1",
                now.plusMinutes(5));
        QuestionExplanationArtifact artifact = artifact(QuestionExplanationArtifact.STATUS_PENDING);
        repositories(task, artifact);
        ClaimedTask claim = new ClaimedTask(
                10L, 20L, 30L, "f".repeat(64), "worker:claim-1");

        boolean failed = transactions.fail(
                claim, "INVALID_PROVIDER_RESPONSE", "Terminal failure", true, now);

        assertThat(failed).isTrue();
        assertThat(task.getStatus()).isEqualTo(QuestionExplanationGenerationTask.STATUS_FAILED);
        assertThat(task.getCompletedAt()).isEqualTo(now);
        assertThat(artifact.getStatus()).isEqualTo(QuestionExplanationArtifact.STATUS_FAILED);
        assertThat(artifact.getErrorCategory()).isEqualTo("INVALID_PROVIDER_RESPONSE");
        assertThat(artifact.getFailedAt()).isEqualTo(now);
    }

    @Test
    void expiredLeaseAtAttemptLimitBecomesTerminalWithoutAnotherProviderClaim() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 12, 0);
        QuestionExplanationGenerationTask task = task(
                QuestionExplanationGenerationTask.STATUS_PROCESSING,
                4,
                4,
                "worker:expired",
                now.minusSeconds(1));
        QuestionExplanationArtifact artifact = artifact(QuestionExplanationArtifact.STATUS_PENDING);
        repositories(task, artifact);

        Optional<ClaimedTask> claimed = transactions.claim(10L, "worker:new", now);

        assertThat(claimed).isEmpty();
        assertThat(task.getAttemptCount()).isEqualTo(4);
        assertThat(task.getStatus()).isEqualTo(QuestionExplanationGenerationTask.STATUS_FAILED);
        assertThat(task.getLeaseOwner()).isNull();
        assertThat(task.getErrorCategory()).isEqualTo("LEASE_EXPIRED_AFTER_MAX_ATTEMPTS");
        assertThat(artifact.getStatus()).isEqualTo(QuestionExplanationArtifact.STATUS_FAILED);
        assertThat(artifact.getErrorCategory()).isEqualTo("LEASE_EXPIRED_AFTER_MAX_ATTEMPTS");
    }

    @Test
    void ownedCompletionMarksArtifactReadyAndTaskSucceeded() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 12, 0);
        QuestionExplanationGenerationTask task = task(
                QuestionExplanationGenerationTask.STATUS_PROCESSING,
                1,
                4,
                "worker:claim-1",
                now.plusMinutes(5));
        QuestionExplanationArtifact artifact = artifact(QuestionExplanationArtifact.STATUS_PENDING);
        repositories(task, artifact);
        ClaimedTask claim = new ClaimedTask(
                10L, 20L, 30L, "f".repeat(64), "worker:claim-1");

        boolean completed = transactions.complete(
                claim, "{\"meaningVi\":\"ready\"}", now);

        assertThat(completed).isTrue();
        assertThat(artifact.getStatus()).isEqualTo(QuestionExplanationArtifact.STATUS_READY);
        assertThat(artifact.getExplanationJson()).contains("ready");
        assertThat(task.getStatus()).isEqualTo(QuestionExplanationGenerationTask.STATUS_SUCCEEDED);
        assertThat(task.getCompletedAt()).isEqualTo(now);
    }

    private void repositories(
            QuestionExplanationGenerationTask task,
            QuestionExplanationArtifact artifact) {
        when(taskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(task));
        when(artifactRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(artifact));
    }

    private static QuestionExplanationGenerationTask task(
            String status,
            int attemptCount,
            int maxAttempts,
            String leaseOwner,
            LocalDateTime leaseExpiresAt) {
        QuestionExplanationGenerationTask task = instantiate(QuestionExplanationGenerationTask.class);
        ReflectionTestUtils.setField(task, "id", 10L);
        ReflectionTestUtils.setField(task, "artifactId", 20L);
        ReflectionTestUtils.setField(task, "sourceQuestionVersionId", 30L);
        ReflectionTestUtils.setField(task, "status", status);
        ReflectionTestUtils.setField(task, "attemptCount", attemptCount);
        ReflectionTestUtils.setField(task, "maxAttempts", maxAttempts);
        ReflectionTestUtils.setField(task, "manualRetryCount", 0);
        ReflectionTestUtils.setField(task, "leaseOwner", leaseOwner);
        ReflectionTestUtils.setField(task, "leaseExpiresAt", leaseExpiresAt);
        return task;
    }

    private static QuestionExplanationArtifact artifact(String status) {
        QuestionExplanationArtifact artifact = instantiate(QuestionExplanationArtifact.class);
        ReflectionTestUtils.setField(artifact, "id", 20L);
        ReflectionTestUtils.setField(artifact, "fingerprint", "f".repeat(64));
        ReflectionTestUtils.setField(artifact, "inputContractJson", "{}");
        ReflectionTestUtils.setField(artifact, "status", status);
        return artifact;
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not create test fixture " + type.getSimpleName(), exception);
        }
    }
}
