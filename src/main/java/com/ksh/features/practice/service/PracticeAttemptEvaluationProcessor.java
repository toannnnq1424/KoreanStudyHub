package com.ksh.features.practice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.repository.PracticeAttemptEvaluationJobRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@ConditionalOnProperty(
        name = "app.practice.attempt-evaluation.worker-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PracticeAttemptEvaluationProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(
                    PracticeAttemptEvaluationProcessor.class);
    private static final int WORKER_CONCURRENCY = 2;
    private static final Duration HEARTBEAT_INTERVAL =
            Duration.ofSeconds(30);
    private static final Duration MAX_EXECUTION =
            Duration.ofMinutes(20);
    private static final Duration EXPIRY_SAFETY_MARGIN =
            Duration.ofSeconds(1);

    private final PracticeAttemptEvaluationJobRepository jobRepository;
    private final PracticeAttemptEvaluationJobTransactions transactions;
    private final PracticeService practiceService;
    private final ObjectMapper objectMapper;
    private final ExecutorService evaluationExecutor;
    private final ScheduledExecutorService leaseScheduler;
    private final ScheduledExecutorService timeoutScheduler;
    private final Semaphore availableWorkers;
    private final Duration heartbeatInterval;
    private final Duration maxExecution;
    private final boolean ownsExecutors;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final String workerId = "pae-" + UUID.randomUUID();

    @Autowired
    public PracticeAttemptEvaluationProcessor(
            PracticeAttemptEvaluationJobRepository jobRepository,
            PracticeAttemptEvaluationJobTransactions transactions,
            PracticeService practiceService,
            ObjectMapper objectMapper) {
        this(
                jobRepository,
                transactions,
                practiceService,
                objectMapper,
                Executors.newFixedThreadPool(
                        WORKER_CONCURRENCY,
                        daemonThreadFactory(
                                "practice-attempt-evaluation")),
                Executors.newScheduledThreadPool(
                        WORKER_CONCURRENCY,
                        daemonThreadFactory(
                                "practice-attempt-evaluation-lease")),
                Executors.newScheduledThreadPool(
                        WORKER_CONCURRENCY,
                        daemonThreadFactory(
                                "practice-attempt-evaluation-timeout")),
                WORKER_CONCURRENCY,
                HEARTBEAT_INTERVAL,
                MAX_EXECUTION,
                true);
    }

    PracticeAttemptEvaluationProcessor(
            PracticeAttemptEvaluationJobRepository jobRepository,
            PracticeAttemptEvaluationJobTransactions transactions,
            PracticeService practiceService,
            ObjectMapper objectMapper,
            ExecutorService evaluationExecutor,
            ScheduledExecutorService leaseScheduler,
            ScheduledExecutorService timeoutScheduler,
            int concurrency) {
        this(
                jobRepository,
                transactions,
                practiceService,
                objectMapper,
                evaluationExecutor,
                leaseScheduler,
                timeoutScheduler,
                concurrency,
                HEARTBEAT_INTERVAL,
                MAX_EXECUTION,
                false);
    }

    PracticeAttemptEvaluationProcessor(
            PracticeAttemptEvaluationJobRepository jobRepository,
            PracticeAttemptEvaluationJobTransactions transactions,
            PracticeService practiceService,
            ObjectMapper objectMapper,
            ExecutorService evaluationExecutor,
            ScheduledExecutorService leaseScheduler,
            ScheduledExecutorService timeoutScheduler,
            int concurrency,
            Duration heartbeatInterval,
            Duration maxExecution) {
        this(
                jobRepository,
                transactions,
                practiceService,
                objectMapper,
                evaluationExecutor,
                leaseScheduler,
                timeoutScheduler,
                concurrency,
                heartbeatInterval,
                maxExecution,
                false);
    }

    private PracticeAttemptEvaluationProcessor(
            PracticeAttemptEvaluationJobRepository jobRepository,
            PracticeAttemptEvaluationJobTransactions transactions,
            PracticeService practiceService,
            ObjectMapper objectMapper,
            ExecutorService evaluationExecutor,
            ScheduledExecutorService leaseScheduler,
            ScheduledExecutorService timeoutScheduler,
            int concurrency,
            Duration heartbeatInterval,
            Duration maxExecution,
            boolean ownsExecutors) {
        this.jobRepository = jobRepository;
        this.transactions = transactions;
        this.practiceService = practiceService;
        this.objectMapper = objectMapper;
        this.evaluationExecutor = evaluationExecutor;
        this.leaseScheduler = leaseScheduler;
        this.timeoutScheduler = timeoutScheduler;
        this.availableWorkers = new Semaphore(
                Math.max(1, concurrency));
        this.heartbeatInterval = requirePositive(
                heartbeatInterval, "heartbeatInterval");
        this.maxExecution = requirePositive(
                maxExecution, "maxExecution");
        this.ownsExecutors = ownsExecutors;
    }

    @Scheduled(
            initialDelayString =
                    "${app.practice.attempt-evaluation.initial-delay:PT2S}",
            fixedDelayString =
                    "${app.practice.attempt-evaluation.fixed-delay:PT2S}")
    public void runScheduledBatch() {
        processDue(10);
    }

    public int processDue(int limit) {
        if (shuttingDown.get()
                || limit <= 0
                || availableWorkers.availablePermits() <= 0) {
            return 0;
        }
        int candidateLimit = Math.min(
                Math.min(limit, 50),
                availableWorkers.availablePermits());
        List<Long> ids = jobRepository.findClaimableIds(
                LocalDateTime.now(),
                PageRequest.of(0, candidateLimit));
        int claimed = 0;
        for (Long id : ids) {
            if (!availableWorkers.tryAcquire()) {
                break;
            }
            String owner = leaseOwner();
            PracticeAttemptEvaluationJobTransactions.ClaimedEvaluationJob
                    claim;
            try {
                claim = transactions.claim(
                                id, owner, LocalDateTime.now())
                        .orElse(null);
            } catch (RuntimeException exception) {
                availableWorkers.release();
                log.warn(
                        "[PracticeEvaluation] Claim failed jobId={} category={}",
                        id,
                        exception.getClass().getSimpleName());
                continue;
            }
            if (claim == null) {
                availableWorkers.release();
                continue;
            }
            try {
                evaluationExecutor.execute(
                        () -> processAndRelease(claim));
                claimed++;
            } catch (RejectedExecutionException exception) {
                availableWorkers.release();
                transactions.fail(
                        claim,
                        "EVALUATION_WORKER_SATURATED",
                        "Evaluation worker could not accept the claimed job.",
                        true,
                        LocalDateTime.now());
            }
        }
        return claimed;
    }

    private String leaseOwner() {
        String owner = workerId + ":" + UUID.randomUUID();
        if (owner.length() > 100) {
            throw new IllegalStateException(
                    "Generated evaluation lease owner exceeds storage bounds.");
        }
        return owner;
    }

    private void processAndRelease(
            PracticeAttemptEvaluationJobTransactions.ClaimedEvaluationJob
                    claim) {
        try {
            process(claim);
        } finally {
            Thread.interrupted();
            availableWorkers.release();
        }
    }

    private void process(
            PracticeAttemptEvaluationJobTransactions.ClaimedEvaluationJob
                    claim) {
        LocalDateTime startedAt = LocalDateTime.now();
        Duration remaining = Duration.between(
                startedAt,
                claim.expiresAt()).minus(EXPIRY_SAFETY_MARGIN);
        Duration executionWindow = remaining.compareTo(maxExecution) < 0
                ? remaining
                : maxExecution;
        if (executionWindow.isZero() || executionWindow.isNegative()) {
            transactions.fail(
                    claim,
                    "EVALUATION_JOB_DEADLINE_EXPIRED",
                    "Evaluation job has no remaining execution window.",
                    false,
                    startedAt);
            return;
        }

        AtomicBoolean timedOut = new AtomicBoolean(false);
        AtomicBoolean leaseLost = new AtomicBoolean(false);
        Thread evaluationThread = Thread.currentThread();
        ScheduledFuture<?> heartbeat = leaseScheduler
                .scheduleAtFixedRate(
                        () -> renewLease(
                                claim,
                                timedOut,
                                leaseLost,
                                evaluationThread),
                        heartbeatInterval.toMillis(),
                        heartbeatInterval.toMillis(),
                        TimeUnit.MILLISECONDS);
        ScheduledFuture<?> timeout = timeoutScheduler.schedule(
                () -> enforceTimeout(
                        claim,
                        timedOut,
                        leaseLost,
                        heartbeat,
                        evaluationThread),
                Math.max(1L, executionWindow.toMillis()),
                TimeUnit.MILLISECONDS);
        try {
            PracticeAttemptEvaluationOutcome outcome =
                    practiceService.evaluateClaimedAttempt(claim);
            if (leaseLost.get()) {
                log.info(
                        "[PracticeEvaluation] Discarded lease-lost result jobId={}",
                        claim.jobId());
                return;
            }
            if (timedOut.get()) {
                log.info(
                        "[PracticeEvaluation] Discarded timed-out result jobId={}",
                        claim.jobId());
                return;
            }
            if (shuttingDown.get()
                    || Thread.currentThread().isInterrupted()) {
                log.info(
                        "[PracticeEvaluation] Discarded shutdown-interrupted result for lease reclaim jobId={}",
                        claim.jobId());
                return;
            }
            String resultJson = objectMapper.writeValueAsString(outcome);
            if (!transactions.complete(
                    claim, outcome, resultJson, LocalDateTime.now())) {
                log.info(
                        "[PracticeEvaluation] Discarded stale completion jobId={}",
                        claim.jobId());
            }
        } catch (PracticeEvaluationContractChangedException exception) {
            transactions.fail(
                    claim,
                    "EVALUATION_CONTRACT_CHANGED",
                    "Evaluation contract changed after the job was queued.",
                    false,
                    LocalDateTime.now());
        } catch (Exception exception) {
            if (leaseLost.get()) {
                log.info(
                        "[PracticeEvaluation] Lease lost while evaluating jobId={}",
                        claim.jobId());
                return;
            }
            if (timedOut.get()) {
                log.info(
                        "[PracticeEvaluation] Timed-out evaluation exited jobId={}",
                        claim.jobId());
                return;
            }
            if (shuttingDown.get()
                    || Thread.currentThread().isInterrupted()) {
                // Graceful application shutdown interrupts the owned executor
                // without first fencing the durable lease. Leave PROCESSING
                // untouched so the expired lease is reclaimed after restart.
                log.info(
                        "[PracticeEvaluation] Interrupted evaluation left for lease reclaim jobId={}",
                        claim.jobId());
                return;
            }
            transactions.fail(
                    claim,
                    "EVALUATION_INTERNAL_ERROR",
                    "Subjective evaluation failed before a terminal result.",
                    true,
                    LocalDateTime.now());
            log.warn(
                    "[PracticeEvaluation] Evaluation failed jobId={} category={}",
                    claim.jobId(),
                    exception.getClass().getSimpleName());
        } finally {
            heartbeat.cancel(false);
            timeout.cancel(false);
        }
    }

    private void renewLease(
            PracticeAttemptEvaluationJobTransactions.ClaimedEvaluationJob
                    claim,
            AtomicBoolean timedOut,
            AtomicBoolean leaseLost,
            Thread evaluationThread) {
        if (timedOut.get()) {
            return;
        }
        try {
            if (!transactions.renewLease(
                    claim, LocalDateTime.now())) {
                leaseLost.set(true);
                evaluationThread.interrupt();
            }
        } catch (RuntimeException exception) {
            leaseLost.set(true);
            evaluationThread.interrupt();
            log.warn(
                    "[PracticeEvaluation] Lease heartbeat failed jobId={} category={}",
                    claim.jobId(),
                    exception.getClass().getSimpleName());
        }
    }

    private void enforceTimeout(
            PracticeAttemptEvaluationJobTransactions.ClaimedEvaluationJob
                    claim,
            AtomicBoolean timedOut,
            AtomicBoolean leaseLost,
            ScheduledFuture<?> heartbeat,
            Thread evaluationThread) {
        if (!timedOut.compareAndSet(false, true)) {
            return;
        }
        heartbeat.cancel(false);
        evaluationThread.interrupt();
        if (leaseLost.get()) {
            return;
        }
        try {
            if (!transactions.fail(
                    claim,
                    "EVALUATION_EXECUTION_TIMEOUT",
                    "Subjective evaluation exceeded its bounded execution window.",
                    false,
                    LocalDateTime.now())) {
                log.info(
                        "[PracticeEvaluation] Timeout lost claim race jobId={}",
                        claim.jobId());
            }
        } catch (RuntimeException exception) {
            log.error(
                    "[PracticeEvaluation] Could not persist timeout jobId={} category={}",
                    claim.jobId(),
                    exception.getClass().getSimpleName());
        }
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown.set(true);
        if (!ownsExecutors) {
            return;
        }
        evaluationExecutor.shutdownNow();
        leaseScheduler.shutdownNow();
        timeoutScheduler.shutdownNow();
    }

    private static Duration requirePositive(
            Duration value,
            String label) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(
                    label + " must be positive.");
        }
        return value;
    }

    private static ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(
                    task,
                    prefix + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
