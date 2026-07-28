package com.ksh.features.practice.service;

import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeAttemptEvaluationJob;
import com.ksh.features.practice.repository.PracticeAttemptEvaluationJobRepository;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Service
public class PracticeAttemptEvaluationJobTransactions {

    static final Duration LEASE_DURATION = Duration.ofMinutes(2);

    private final PracticeAttemptEvaluationJobRepository jobRepository;
    private final PracticeAttemptRepository attemptRepository;

    public PracticeAttemptEvaluationJobTransactions(
            PracticeAttemptEvaluationJobRepository jobRepository,
            PracticeAttemptRepository attemptRepository) {
        this.jobRepository = jobRepository;
        this.attemptRepository = attemptRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedEvaluationJob> claim(
            Long jobId, String owner, LocalDateTime now) {
        PracticeAttemptEvaluationJob job =
                jobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null) {
            return Optional.empty();
        }
        PracticeAttempt attempt = attemptRepository
                .findByIdAndUserIdForUpdate(
                        job.getAttemptId(), job.getRequestedBy())
                .orElse(null);
        if (attempt == null
                || (!PracticeAttempt.STATUS_SUBMITTED.equals(attempt.getStatus())
                && !PracticeAttempt.STATUS_GRADED.equals(attempt.getStatus()))) {
            job.markFailure(
                    "ATTEMPT_NOT_TERMINAL",
                    "Evaluation target is no longer a terminal learner submission.",
                    false, null, now);
            jobRepository.save(job);
            return Optional.empty();
        }
        if (job.expired(now) || job.attemptsExhausted()) {
            String code = job.expired(now)
                    ? "EVALUATION_JOB_DEADLINE_EXPIRED"
                    : "EVALUATION_JOB_ATTEMPTS_EXHAUSTED";
            job.markFailure(
                    code,
                    "Evaluation job exhausted its bounded execution window.",
                    false, null, now);
            attempt.markAnalysisUnavailable(
                    attempt.getTotalPoints(),
                    attempt.getAnswersJson(),
                    attempt.getAiFeedbackJson(),
                    code,
                    PracticeAttempt.STATUS_GRADED.equals(attempt.getStatus()),
                    now);
            jobRepository.save(job);
            attemptRepository.save(attempt);
            return Optional.empty();
        }
        if (!job.canClaim(now)) {
            return Optional.empty();
        }
        job.claim(owner, now, now.plus(LEASE_DURATION));
        attempt.markAnalysisProcessing();
        jobRepository.save(job);
        attemptRepository.save(attempt);
        return Optional.of(new ClaimedEvaluationJob(
                job.getId(),
                job.getAttemptId(),
                job.getRequestedBy(),
                job.getOperation(),
                job.getTargetQuestionId(),
                job.getInputFingerprint(),
                job.getEvaluationContractIdentity(),
                job.getExpiresAt(),
                owner));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean renewLease(
            ClaimedEvaluationJob claim,
            LocalDateTime now) {
        PracticeAttemptEvaluationJob job =
                jobRepository.findByIdForUpdate(claim.jobId()).orElse(null);
        if (job == null
                || !claimMatches(job, claim)
                || !job.ownsActiveLease(claim.owner(), now)
                || job.expired(now)) {
            return false;
        }
        job.renewLease(
                claim.owner(), now, now.plus(LEASE_DURATION));
        jobRepository.save(job);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(
            ClaimedEvaluationJob claim,
            PracticeAttemptEvaluationOutcome outcome,
            String resultJson,
            LocalDateTime now) {
        PracticeAttemptEvaluationJob job =
                jobRepository.findByIdForUpdate(claim.jobId()).orElse(null);
        if (job == null || !claimMatches(job, claim)
                || !job.ownsActiveLease(claim.owner(), now)
                || job.expired(now)
                || !job.getInputFingerprint().equals(
                        outcome.inputFingerprint())) {
            return false;
        }
        PracticeAttempt attempt = attemptRepository
                .findByIdAndUserIdForUpdate(
                        claim.attemptId(), claim.userId())
                .orElse(null);
        if (attempt == null) {
            return false;
        }

        boolean preserveExisting =
                !PracticeAttemptEvaluationJob.OPERATION_SUBMIT.equals(
                        job.getOperation())
                && PracticeAttempt.STATUS_GRADED.equals(attempt.getStatus());
        if (PracticeAttemptEvaluationOutcome.SUCCEEDED.equals(
                outcome.terminalStatus())) {
            attempt.markAnalysisSucceeded(
                    outcome.score(),
                    outcome.totalPoints(),
                    outcome.answersJson(),
                    outcome.feedbackJson(),
                    outcome.engine(),
                    now);
            job.markTerminal(
                    PracticeAttemptEvaluationJob.STATUS_SUCCEEDED,
                    resultJson, null, false, now);
        } else if (outcome.retryable()
                && !job.attemptsExhausted()
                && !job.expired(now)) {
            // Retry evidence belongs to the durable job. Keep submit feedback
            // empty and preserve any previously graded re-evaluation result so
            // Result/Detail remain PENDING while the job is retryable.
            attempt.markAnalysisQueued(now);
            job.markFailure(
                    outcome.errorCode(),
                    "Subjective evaluation is temporarily unavailable.",
                    true,
                    now.plusSeconds(
                            retryDelaySeconds(job.getAttemptCount())),
                    now);
        } else {
            if (PracticeAttemptEvaluationOutcome.UNAVAILABLE.equals(
                    outcome.terminalStatus())) {
                attempt.markAnalysisUnavailable(
                        outcome.totalPoints(),
                        outcome.answersJson(),
                        outcome.feedbackJson(),
                        outcome.errorCode(),
                        preserveExisting,
                        now);
            } else {
                attempt.markAnalysisFailed(
                        outcome.errorCode(), now);
            }
            job.markTerminal(
                    PracticeAttemptEvaluationOutcome.UNAVAILABLE.equals(
                            outcome.terminalStatus())
                            ? PracticeAttemptEvaluationJob.STATUS_UNAVAILABLE
                            : PracticeAttemptEvaluationJob.STATUS_FAILED,
                    resultJson,
                    outcome.errorCode(),
                    outcome.retryable(),
                    now);
        }
        attemptRepository.save(attempt);
        jobRepository.save(job);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fail(
            ClaimedEvaluationJob claim,
            String code,
            String message,
            boolean retryable,
            LocalDateTime now) {
        PracticeAttemptEvaluationJob job =
                jobRepository.findByIdForUpdate(claim.jobId()).orElse(null);
        if (job == null || !claimMatches(job, claim)
                || !job.ownsActiveLease(claim.owner(), now)) {
            return false;
        }
        long delaySeconds = retryDelaySeconds(job.getAttemptCount());
        job.markFailure(
                code, message, retryable,
                now.plusSeconds(delaySeconds), now);
        PracticeAttempt attempt = attemptRepository
                .findByIdAndUserIdForUpdate(
                        claim.attemptId(), claim.userId())
                .orElse(null);
        if (attempt != null) {
            if (PracticeAttemptEvaluationJob.STATUS_RETRY_WAIT.equals(
                    job.getJobStatus())) {
                attempt.markAnalysisQueued(now);
            } else {
                attempt.markAnalysisFailed(code, now);
            }
            attemptRepository.save(attempt);
        }
        jobRepository.save(job);
        return true;
    }

    private static long retryDelaySeconds(Integer attemptCount) {
        int attempt = attemptCount == null ? 1 : Math.max(1, attemptCount);
        return Math.min(300L, 15L * (1L << Math.min(attempt - 1, 4)));
    }

    private static boolean claimMatches(
            PracticeAttemptEvaluationJob job,
            ClaimedEvaluationJob claim) {
        return Objects.equals(job.getAttemptId(), claim.attemptId())
                && Objects.equals(job.getRequestedBy(), claim.userId())
                && Objects.equals(job.getOperation(), claim.operation())
                && Objects.equals(
                        job.getTargetQuestionId(),
                        claim.targetQuestionId())
                && Objects.equals(
                        job.getInputFingerprint(),
                        claim.inputFingerprint())
                && Objects.equals(
                        job.getEvaluationContractIdentity(),
                        claim.evaluationContractIdentity());
    }

    public record ClaimedEvaluationJob(
            Long jobId,
            Long attemptId,
            Long userId,
            String operation,
            Long targetQuestionId,
            String inputFingerprint,
            String evaluationContractIdentity,
            LocalDateTime expiresAt,
            String owner
    ) {
    }
}
