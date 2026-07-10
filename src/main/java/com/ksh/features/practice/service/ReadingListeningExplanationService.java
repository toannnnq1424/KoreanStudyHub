package com.ksh.features.practice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.QuestionExplanationCache;
import com.ksh.features.practice.ai.OpenAiProperties;
import com.ksh.features.practice.ai.readinglistening.ReadingListeningExplanationClient;
import com.ksh.features.practice.ai.readinglistening.ReadingListeningMockExplanationService;
import com.ksh.features.practice.ai.metrics.PracticeAiMetrics;
import com.ksh.features.practice.repository.QuestionExplanationCacheRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages shared per-question explanation lookup and caching for Reading/Listening skills.
 */
@Service
public class ReadingListeningExplanationService {

    private static final Logger log = LoggerFactory.getLogger(ReadingListeningExplanationService.class);

    private final QuestionExplanationCacheRepository cacheRepository;
    private final ReadingListeningExplanationClient explanationClient;
    private final ReadingListeningMockExplanationService mockExplanationService;
    private final OpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;
    private final PracticeAiMetrics metrics;

    /** Per-cache-key lock objects. Prevents duplicate provider calls in one JVM only. */
    private final ConcurrentHashMap<String, Object> cacheLocks = new ConcurrentHashMap<>();

    public ReadingListeningExplanationService(QuestionExplanationCacheRepository cacheRepository,
                                              ReadingListeningExplanationClient explanationClient,
                                              ReadingListeningMockExplanationService mockExplanationService,
                                              OpenAiProperties openAiProperties,
                                              ObjectMapper objectMapper) {
        this(cacheRepository, explanationClient, mockExplanationService, openAiProperties, objectMapper,
                PracticeAiMetrics.noop());
    }

    @Autowired
    public ReadingListeningExplanationService(QuestionExplanationCacheRepository cacheRepository,
                                              ReadingListeningExplanationClient explanationClient,
                                              ReadingListeningMockExplanationService mockExplanationService,
                                              OpenAiProperties openAiProperties,
                                              ObjectMapper objectMapper,
                                              PracticeAiMetrics metrics) {
        this.cacheRepository = cacheRepository;
        this.explanationClient = explanationClient;
        this.mockExplanationService = mockExplanationService;
        this.openAiProperties = openAiProperties;
        this.objectMapper = objectMapper;
        this.metrics = metrics == null ? PracticeAiMetrics.noop() : metrics;
    }

    /**
     * Returns an explanation JSON for the given question. Cache DB operations use repository-level
     * REQUIRES_NEW transactions; the provider call and fallback generation do not rely on this method
     * participating in the caller's result transaction.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String getOrCreateExplanation(PracticeQuestion question, String passageText,
                                         String skillType, Long testId, String optionLabelMode) {
        CacheKeyParts keyParts = buildCacheKeyParts(question, passageText, skillType, optionLabelMode);

        Optional<String> cached = readValidCache(keyParts, question, skillType);
        if (cached.isPresent()) {
            log.info("[ReadingListeningCache] Hit questionId={}", question.getId());
            return cached.get();
        }

        Object lock = cacheLocks.computeIfAbsent(keyParts.cacheKey(), k -> new Object());
        synchronized (lock) {
            try {
                cached = readValidCache(keyParts, question, skillType);
                if (cached.isPresent()) {
                    log.info("[ReadingListeningCache] Hit (double-check) questionId={}", question.getId());
                    return cached.get();
                }

                log.info("[ReadingListeningCache] Miss questionId={}, calling AI", question.getId());
                long providerStart = PracticeAiMetrics.startNanos();
                try {
                    String aiJson = explanationClient.explain(question, passageText, skillType, optionLabelMode);
                    if (isValidExplanationJson(aiJson)) {
                        recordRlProvider(PracticeAiMetrics.ProviderOutcome.SUCCESS, providerStart);
                        writeCache(keyParts, question, testId, skillType, aiJson);
                        return aiJson;
                    }

                    String mockReason = (openAiProperties.apiKey() == null || openAiProperties.apiKey().isBlank())
                            ? "chua cau hinh API key"
                            : "han ngach API tam thoi da het - thu lai sau";
                    log.info("[ReadingListeningCache] AI unavailable for questionId={}, using mock", question.getId());
                    String fallback = mockExplanationService.explain(question, passageText, skillType, optionLabelMode, mockReason);
                    recordRlProvider(PracticeAiMetrics.ProviderOutcome.FALLBACK, providerStart);
                    return fallback;
                } catch (RuntimeException ex) {
                    recordRlProvider(PracticeAiMetrics.ProviderOutcome.FAILURE, providerStart);
                    throw ex;
                }
            } finally {
                cacheLocks.remove(keyParts.cacheKey(), lock);
            }
        }
    }

    Optional<String> readValidCache(CacheKeyParts keyParts, PracticeQuestion question, String skillType) {
        long lookupStart = PracticeAiMetrics.startNanos();
        try {
            Optional<QuestionExplanationCache> row = cacheRepository.findByCacheKey(keyParts.cacheKey());
            if (row.isEmpty()) {
                recordRlCache(PracticeAiMetrics.CacheOperation.LOOKUP,
                        PracticeAiMetrics.CacheOutcome.MISS, lookupStart);
                return Optional.empty();
            }
            QuestionExplanationCache cache = row.get();
            if (!metadataMatches(cache, keyParts)) {
                recordRlCache(PracticeAiMetrics.CacheOperation.LOOKUP,
                        PracticeAiMetrics.CacheOutcome.MISS, lookupStart);
                log.warn("[ReadingListeningCache] Metadata mismatch for cached explanation; ignoring questionId={} skill={}",
                        question.getId(), normalize(skillType));
                return Optional.empty();
            }
            String explanationJson = cache.getExplanationJson();
            if (isValidExplanationJson(explanationJson)) {
                recordRlCache(PracticeAiMetrics.CacheOperation.LOOKUP,
                        PracticeAiMetrics.CacheOutcome.HIT, lookupStart);
                return Optional.of(explanationJson);
            }
            recordRlCache(PracticeAiMetrics.CacheOperation.LOOKUP,
                    PracticeAiMetrics.CacheOutcome.HIT, lookupStart);
            metrics.recordCacheOperation(
                    PracticeAiMetrics.CacheType.RL_EXPLANATION,
                    PracticeAiMetrics.CacheOperation.PARSE,
                    PracticeAiMetrics.CacheOutcome.MALFORMED,
                    PracticeAiMetrics.elapsedSince(lookupStart));
            log.warn("[ReadingListeningCache] Malformed cached explanation ignored questionId={} skill={} category=malformed-cache",
                    question.getId(), normalize(skillType));
            deleteCache(keyParts.cacheKey(), question.getId(), skillType, "malformed-cache");
            return Optional.empty();
        } catch (Exception ex) {
            recordRlCache(PracticeAiMetrics.CacheOperation.LOOKUP,
                    PracticeAiMetrics.CacheOutcome.FAILURE, lookupStart);
            log.warn("[ReadingListeningCache] Read failed; treating as miss operation=cache-read questionId={} skill={} exception={}",
                    question.getId(), normalize(skillType), exceptionCategory(ex));
            return Optional.empty();
        }
    }

    private void writeCache(CacheKeyParts keyParts, PracticeQuestion question, Long testId,
                            String skillType, String explanationJson) {
        long writeStart = PracticeAiMetrics.startNanos();
        try {
            cacheRepository.upsert(
                    keyParts.cacheKey(),
                    question.getId(),
                    testId,
                    normalize(skillType),
                    normalize(question.getQuestionType()),
                    keyParts.questionHash(),
                    normalize(question.getAnswerKey()),
                    explanationJson,
                    keyParts.model(),
                    keyParts.promptVersion(),
                    keyParts.schemaVersion(),
                    keyParts.language()
            );
            recordRlCache(PracticeAiMetrics.CacheOperation.WRITE,
                    PracticeAiMetrics.CacheOutcome.SUCCESS, writeStart);
            log.info("[ReadingListeningCache] Cached questionId={}", question.getId());
        } catch (Exception ex) {
            recordRlCache(PracticeAiMetrics.CacheOperation.WRITE,
                    PracticeAiMetrics.CacheOutcome.FAILURE, writeStart);
            log.warn("[ReadingListeningCache] Write failed; returning provider explanation operation=cache-write questionId={} skill={} exception={}",
                    question.getId(), normalize(skillType), exceptionCategory(ex));
        }
    }

    private void deleteCache(String cacheKey, Long questionId, String skillType, String reason) {
        long deleteStart = PracticeAiMetrics.startNanos();
        try {
            cacheRepository.deleteByCacheKey(cacheKey);
            recordRlCache(PracticeAiMetrics.CacheOperation.DELETE,
                    PracticeAiMetrics.CacheOutcome.SUCCESS, deleteStart);
            log.warn("[ReadingListeningCache] Deleted cache entry operation=cache-delete questionId={} skill={} category={}",
                    questionId, normalize(skillType), reason);
        } catch (Exception ex) {
            recordRlCache(PracticeAiMetrics.CacheOperation.DELETE,
                    PracticeAiMetrics.CacheOutcome.FAILURE, deleteStart);
            log.warn("[ReadingListeningCache] Delete failed operation=cache-delete questionId={} skill={} category={} exception={}",
                    questionId, normalize(skillType), reason, exceptionCategory(ex));
        }
    }

    private void recordRlCache(PracticeAiMetrics.CacheOperation operation,
                               PracticeAiMetrics.CacheOutcome outcome,
                               long startNanos) {
        metrics.recordCacheOperation(
                PracticeAiMetrics.CacheType.RL_EXPLANATION,
                operation,
                outcome,
                PracticeAiMetrics.elapsedSince(startNanos));
    }

    private void recordRlProvider(PracticeAiMetrics.ProviderOutcome outcome, long startNanos) {
        metrics.recordProviderOperation(
                PracticeAiMetrics.ProviderFeature.RL_EXPLANATION,
                outcome,
                PracticeAiMetrics.elapsedSince(startNanos));
    }

    private boolean metadataMatches(QuestionExplanationCache cache, CacheKeyParts keyParts) {
        return keyParts.cacheKey().equals(cache.getCacheKey())
                && keyParts.questionHash().equals(cache.getQuestionHash())
                && keyParts.model().equals(cache.getAiModel())
                && keyParts.promptVersion().equals(cache.getPromptVersion())
                && keyParts.schemaVersion().equals(cache.getSchemaVersion())
                && keyParts.language().equals(cache.getExplanationLanguage());
    }

    boolean isValidExplanationJson(String explanationJson) {
        if (explanationJson == null || explanationJson.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(explanationJson);
            if (!root.isObject()) {
                return false;
            }
            if (!isTextual(root, "meaningVi")
                    || !isTextual(root, "evidenceQuote")
                    || !isTextual(root, "correctReasonVi")
                    || !isTextual(root, "relatedTranslationVi")) {
                return false;
            }
            JsonNode eliminatedOptions = root.path("eliminatedOptions");
            if (!eliminatedOptions.isArray()) {
                return false;
            }
            for (JsonNode option : eliminatedOptions) {
                if (!option.isObject() || !isTextual(option, "optionKey") || !isTextual(option, "reasonVi")) {
                    return false;
                }
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean isTextual(JsonNode node, String fieldName) {
        return node.has(fieldName) && node.get(fieldName).isTextual();
    }

    CacheKeyParts buildCacheKeyParts(PracticeQuestion question, String passageText,
                                     String skillType, String optionLabelMode) {
        String questionHash = sha256(framed(
                normalize(question.getPrompt()),
                normalize(question.getOptionsJson()),
                normalize(question.getAnswerKey()),
                normalize(passageText),
                normalize(skillType),
                normalize(question.getQuestionType()),
                normalize(optionLabelMode)
        ));
        String model = normalize(explanationClient.model());
        String promptVersion = normalize(explanationClient.promptVersion());
        String schemaVersion = normalize(explanationClient.schemaVersion());
        String language = normalize(explanationClient.explanationLanguage());
        String cacheKey = sha256(framed(
                normalize(String.valueOf(question.getId())),
                questionHash,
                model,
                promptVersion,
                schemaVersion,
                language
        ));
        return new CacheKeyParts(cacheKey, questionHash, model, promptVersion, schemaVersion, language);
    }

    static String framed(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            String normalized = value == null ? "" : value;
            int byteLength = normalized.getBytes(StandardCharsets.UTF_8).length;
            builder.append(byteLength).append(':').append(normalized);
        }
        return builder.toString();
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    static String sha256(String material) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String exceptionCategory(Exception ex) {
        return ex == null ? "unknown" : ex.getClass().getSimpleName();
    }

    record CacheKeyParts(String cacheKey, String questionHash, String model,
                         String promptVersion, String schemaVersion, String language) {
    }
}
