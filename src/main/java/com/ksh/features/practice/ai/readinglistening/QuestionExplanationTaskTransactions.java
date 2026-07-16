package com.ksh.features.practice.ai.readinglistening;

import com.ksh.entities.QuestionExplanationArtifact;
import com.ksh.entities.QuestionExplanationGenerationTask;
import com.ksh.features.practice.repository.QuestionExplanationArtifactRepository;
import com.ksh.features.practice.repository.QuestionExplanationGenerationTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class QuestionExplanationTaskTransactions {

    static final Duration LEASE_DURATION = Duration.ofMinutes(5);

    private final QuestionExplanationGenerationTaskRepository taskRepository;
    private final QuestionExplanationArtifactRepository artifactRepository;

    public QuestionExplanationTaskTransactions(
            QuestionExplanationGenerationTaskRepository taskRepository,
            QuestionExplanationArtifactRepository artifactRepository) {
        this.taskRepository = taskRepository;
        this.artifactRepository = artifactRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedTask> claim(Long taskId, String owner, LocalDateTime now) {
        QuestionExplanationGenerationTask task = taskRepository.findByIdForUpdate(taskId)
                .orElse(null);
        if (task == null || !task.canClaim(now)) {
            return Optional.empty();
        }
        QuestionExplanationArtifact artifact = artifactRepository.findByIdForUpdate(task.getArtifactId())
                .orElseThrow(() -> new IllegalStateException("Explanation artifact not found"));
        if (QuestionExplanationArtifact.STATUS_READY.equals(artifact.getStatus())) {
            task.markSucceeded(now);
            taskRepository.save(task);
            return Optional.empty();
        }
        if (!QuestionExplanationArtifact.STATUS_PENDING.equals(artifact.getStatus())) {
            task.markFailure(
                    "ARTIFACT_NOT_PENDING",
                    "Explanation artifact is not claimable.",
                    false,
                    null,
                    now);
            taskRepository.save(task);
            return Optional.empty();
        }
        if (attemptsExhausted(task)) {
            String category = "LEASE_EXPIRED_AFTER_MAX_ATTEMPTS";
            String message = "Explanation generation lease expired after the maximum attempts.";
            task.markFailure(category, message, false, null, now);
            artifact.markFailed(category, message, now);
            taskRepository.save(task);
            artifactRepository.save(artifact);
            return Optional.empty();
        }
        task.claim(owner, now, now.plus(LEASE_DURATION));
        taskRepository.save(task);
        return Optional.of(new ClaimedTask(
                task.getId(),
                artifact.getId(),
                task.getSourceQuestionVersionId(),
                artifact.getFingerprint(),
                owner));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(ClaimedTask claim, String explanationJson, LocalDateTime now) {
        QuestionExplanationGenerationTask task = taskRepository.findByIdForUpdate(claim.taskId())
                .orElse(null);
        if (task == null || !task.isOwnedBy(claim.owner())) {
            return false;
        }
        QuestionExplanationArtifact artifact = artifactRepository.findByIdForUpdate(claim.artifactId())
                .orElseThrow(() -> new IllegalStateException("Explanation artifact not found"));
        if (QuestionExplanationArtifact.STATUS_PENDING.equals(artifact.getStatus())) {
            artifact.markReady(explanationJson, now);
            artifactRepository.save(artifact);
        }
        task.markSucceeded(now);
        taskRepository.save(task);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fail(
            ClaimedTask claim,
            String category,
            String message,
            boolean retryable,
            LocalDateTime now) {
        QuestionExplanationGenerationTask task = taskRepository.findByIdForUpdate(claim.taskId())
                .orElse(null);
        if (task == null || !task.isOwnedBy(claim.owner())) {
            return false;
        }
        long delaySeconds = retryDelaySeconds(task.getAttemptCount());
        task.markFailure(category, message, retryable, now.plusSeconds(delaySeconds), now);
        taskRepository.save(task);
        if (QuestionExplanationGenerationTask.STATUS_FAILED.equals(task.getStatus())) {
            QuestionExplanationArtifact artifact = artifactRepository.findByIdForUpdate(claim.artifactId())
                    .orElseThrow(() -> new IllegalStateException("Explanation artifact not found"));
            if (QuestionExplanationArtifact.STATUS_PENDING.equals(artifact.getStatus())) {
                artifact.markFailed(category, message, now);
                artifactRepository.save(artifact);
            }
        }
        return true;
    }

    private static long retryDelaySeconds(Integer attemptCount) {
        int attempt = attemptCount == null ? 1 : Math.max(1, attemptCount);
        return Math.min(900L, 30L * (1L << Math.min(attempt - 1, 5)));
    }

    private static boolean attemptsExhausted(QuestionExplanationGenerationTask task) {
        return task.getAttemptCount() != null
                && task.getMaxAttempts() != null
                && task.getAttemptCount() >= task.getMaxAttempts();
    }

    public record ClaimedTask(
            Long taskId,
            Long artifactId,
            Long sourceQuestionVersionId,
            String fingerprint,
            String owner
    ) {
    }
}
