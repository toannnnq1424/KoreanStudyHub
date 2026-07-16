package com.ksh.features.practice.ai.writing;

import com.ksh.entities.WritingEvaluationCacheEntry;
import com.ksh.features.practice.ai.metrics.PracticeAiMetrics;
import com.ksh.features.practice.repository.WritingEvaluationCacheRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

@Component
public class WritingEvaluationCacheService {

    private static final Logger log = LoggerFactory.getLogger(WritingEvaluationCacheService.class);
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String DELIMITER = "|";

    private final WritingEvaluationCacheRepository repository;
    private final Clock clock;
    private final PracticeAiMetrics metrics;

    @Autowired
    public WritingEvaluationCacheService(WritingEvaluationCacheRepository repository,
                                         PracticeAiMetrics metrics) {
        this(repository, Clock.systemUTC(), metrics);
    }

    public WritingEvaluationCacheService(WritingEvaluationCacheRepository repository) {
        this(repository, Clock.systemUTC(), PracticeAiMetrics.noop());
    }

    /** Test-only constructor for injectable clock (avoids Thread.sleep in TTL tests). */
    WritingEvaluationCacheService(WritingEvaluationCacheRepository repository, Clock clock) {
        this(repository, clock, PracticeAiMetrics.noop());
    }

    WritingEvaluationCacheService(WritingEvaluationCacheRepository repository, Clock clock,
                                  PracticeAiMetrics metrics) {
        this.repository = repository;
        this.clock = clock;
        this.metrics = metrics == null ? PracticeAiMetrics.noop() : metrics;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<String> get(Long userId, String prompt, String learnerAnswer,
                                String taskType, String model,
                                String promptVersion, String rubricVersion,
                                String schemaVersion) {
        if (userId == null) {
            log.debug("Writing evaluation cache bypassed because user scope is missing.");
            return Optional.empty();
        }

        String cacheKey = scopedKey(userId, prompt, learnerAnswer, taskType, model,
                promptVersion, rubricVersion, schemaVersion);
        long lookupStart = PracticeAiMetrics.startNanos();
        boolean lookupRecorded = false;
        try {
            Optional<WritingEvaluationCacheEntry> entry = repository.findById(cacheKey);
            if (entry.isEmpty()) {
                recordLookup(PracticeAiMetrics.CacheOutcome.MISS, lookupStart);
                return Optional.empty();
            }

            LocalDateTime now = now();
            if (!entry.get().getExpiresAt().isAfter(now)) {
                recordLookup(PracticeAiMetrics.CacheOutcome.EXPIRED, lookupStart);
                lookupRecorded = true;
                deleteByCacheKeyWithMetrics(cacheKey);
                return Optional.empty();
            }
            recordLookup(PracticeAiMetrics.CacheOutcome.HIT, lookupStart);
            return Optional.of(entry.get().getResultJson());
        } catch (RuntimeException ex) {
            if (!lookupRecorded) {
                recordLookup(PracticeAiMetrics.CacheOutcome.FAILURE, lookupStart);
            }
            throw ex;
        }
    }

    private void recordLookup(PracticeAiMetrics.CacheOutcome outcome, long startNanos) {
        metrics.recordCacheOperation(
                PracticeAiMetrics.CacheType.WRITING,
                PracticeAiMetrics.CacheOperation.LOOKUP,
                outcome,
                PracticeAiMetrics.elapsedSince(startNanos));
    }

    private void deleteByCacheKeyWithMetrics(String cacheKey) {
        long deleteStart = PracticeAiMetrics.startNanos();
        try {
            repository.deleteByCacheKey(cacheKey);
            metrics.recordCacheOperation(
                    PracticeAiMetrics.CacheType.WRITING,
                    PracticeAiMetrics.CacheOperation.DELETE,
                    PracticeAiMetrics.CacheOutcome.SUCCESS,
                    PracticeAiMetrics.elapsedSince(deleteStart));
        } catch (RuntimeException ex) {
            metrics.recordCacheOperation(
                    PracticeAiMetrics.CacheType.WRITING,
                    PracticeAiMetrics.CacheOperation.DELETE,
                    PracticeAiMetrics.CacheOutcome.FAILURE,
                    PracticeAiMetrics.elapsedSince(deleteStart));
            throw ex;
        }
    }

    private void deleteExpiredWithMetrics(LocalDateTime now) {
        long deleteStart = PracticeAiMetrics.startNanos();
        try {
            repository.deleteExpired(now);
            metrics.recordCacheOperation(
                    PracticeAiMetrics.CacheType.WRITING,
                    PracticeAiMetrics.CacheOperation.DELETE,
                    PracticeAiMetrics.CacheOutcome.SUCCESS,
                    PracticeAiMetrics.elapsedSince(deleteStart));
        } catch (RuntimeException ex) {
            metrics.recordCacheOperation(
                    PracticeAiMetrics.CacheType.WRITING,
                    PracticeAiMetrics.CacheOperation.DELETE,
                    PracticeAiMetrics.CacheOutcome.FAILURE,
                    PracticeAiMetrics.elapsedSince(deleteStart));
            throw ex;
        }
    }

    private void upsertWithMetrics(String cacheKey, String userScopeHash,
                                   String taskType, String model, String promptVersion,
                                   String rubricVersion, String schemaVersion,
                                   String value, LocalDateTime expiresAt) {
        long writeStart = PracticeAiMetrics.startNanos();
        try {
            repository.upsert(cacheKey, userScopeHash,
                    nullToEmpty(taskType), nullToEmpty(model), nullToEmpty(promptVersion),
                    nullToEmpty(rubricVersion), nullToEmpty(schemaVersion),
                    value, expiresAt);
            metrics.recordCacheOperation(
                    PracticeAiMetrics.CacheType.WRITING,
                    PracticeAiMetrics.CacheOperation.WRITE,
                    PracticeAiMetrics.CacheOutcome.SUCCESS,
                    PracticeAiMetrics.elapsedSince(writeStart));
        } catch (RuntimeException ex) {
            metrics.recordCacheOperation(
                    PracticeAiMetrics.CacheType.WRITING,
                    PracticeAiMetrics.CacheOperation.WRITE,
                    PracticeAiMetrics.CacheOutcome.FAILURE,
                    PracticeAiMetrics.elapsedSince(writeStart));
            throw ex;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void put(Long userId, String prompt, String learnerAnswer,
                    String taskType, String model,
                    String promptVersion, String rubricVersion,
                    String schemaVersion,
                    String value) {
        if (userId == null) {
            log.debug("Writing evaluation cache write bypassed because user scope is missing.");
            return;
        }

        LocalDateTime now = now();
        deleteExpiredWithMetrics(now);
        String userScopeHash = userScopeHash(userId);
        String cacheKey = scopedKey(userScopeHash,
                key(prompt, learnerAnswer, taskType, model, promptVersion, rubricVersion, schemaVersion));
        upsertWithMetrics(cacheKey, userScopeHash,
                taskType, model, promptVersion, rubricVersion, schemaVersion,
                value, now.plus(TTL));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(Long userId, String prompt, String learnerAnswer,
                       String taskType, String model,
                       String promptVersion, String rubricVersion,
                       String schemaVersion) {
        if (userId == null) {
            return;
        }
        deleteByCacheKeyWithMetrics(scopedKey(userId, prompt, learnerAnswer, taskType, model,
                promptVersion, rubricVersion, schemaVersion));
    }

    static String key(String prompt, String learnerAnswer,
                      String taskType, String model,
                      String promptVersion, String rubricVersion,
                      String schemaVersion) {
        String raw = frame(normalizeForKey(prompt))
                + frame(normalizeForKey(learnerAnswer))
                + frame(nullToEmpty(taskType))
                + frame(nullToEmpty(model))
                + frame(nullToEmpty(promptVersion))
                + frame(nullToEmpty(rubricVersion))
                + frame(nullToEmpty(schemaVersion));
        return sha256(raw);
    }

    static String scopedKey(Long userId, String prompt, String learnerAnswer,
                            String taskType, String model,
                            String promptVersion, String rubricVersion,
                            String schemaVersion) {
        return scopedKey(userScopeHash(userId),
                key(prompt, learnerAnswer, taskType, model, promptVersion, rubricVersion, schemaVersion));
    }

    static String userScopeHash(Long userId) {
        if (userId == null) {
            return "";
        }
        return sha256("USER:" + userId);
    }

    private static String scopedKey(String userScopeHash, String contentKey) {
        return sha256(userScopeHash + DELIMITER + contentKey);
    }

    private static String normalizeForKey(String s) {
        if (s == null) return "";
        return s.trim().replace("\r\n", "\n").replace("\r", "\n");
    }

    private static String frame(String value) {
        int byteLength = value.getBytes(StandardCharsets.UTF_8).length;
        return byteLength + ":" + value;
    }

    private LocalDateTime now() {
        Instant instant = Instant.now(clock);
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(raw.hashCode());
        }
    }
}
