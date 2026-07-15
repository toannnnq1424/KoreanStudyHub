package com.ksh.features.practice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.QuestionExplanationCache;
import com.ksh.features.practice.ai.OpenAiProperties;
import com.ksh.features.practice.ai.media.AiImageEvidence;
import com.ksh.features.practice.ai.media.AiQuestionImageResolver;
import com.ksh.features.practice.ai.readinglistening.ReadingListeningExplanationClient;
import com.ksh.features.practice.ai.readinglistening.ReadingListeningMockExplanationService;
import com.ksh.features.practice.ai.metrics.PracticeAiMetrics;
import com.ksh.features.practice.assessment.AssessmentScoringEngine;
import com.ksh.features.practice.assessment.ExplanationContext;
import com.ksh.features.practice.assessment.ExplanationLearnerOverlay;
import com.ksh.features.practice.assessment.LearnerAnswer;
import com.ksh.features.practice.repository.QuestionExplanationCacheRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final AiQuestionImageResolver imageResolver;
    private final AssessmentScoringEngine scoringEngine = new AssessmentScoringEngine();

    /** Per-cache-key lock objects. Prevents duplicate provider calls in one JVM only. */
    private final ConcurrentHashMap<String, Object> cacheLocks = new ConcurrentHashMap<>();

    public ReadingListeningExplanationService(QuestionExplanationCacheRepository cacheRepository,
                                              ReadingListeningExplanationClient explanationClient,
                                              ReadingListeningMockExplanationService mockExplanationService,
                                              OpenAiProperties openAiProperties,
                                              ObjectMapper objectMapper) {
        this(cacheRepository, explanationClient, mockExplanationService, openAiProperties, objectMapper,
                null, PracticeAiMetrics.noop());
    }

    public ReadingListeningExplanationService(QuestionExplanationCacheRepository cacheRepository,
                                              ReadingListeningExplanationClient explanationClient,
                                              ReadingListeningMockExplanationService mockExplanationService,
                                              OpenAiProperties openAiProperties,
                                              ObjectMapper objectMapper,
                                              PracticeAiMetrics metrics) {
        this(cacheRepository, explanationClient, mockExplanationService, openAiProperties, objectMapper,
                null, metrics);
    }

    @Autowired
    public ReadingListeningExplanationService(QuestionExplanationCacheRepository cacheRepository,
                                              ReadingListeningExplanationClient explanationClient,
                                              ReadingListeningMockExplanationService mockExplanationService,
                                              OpenAiProperties openAiProperties,
                                              ObjectMapper objectMapper,
                                              AiQuestionImageResolver imageResolver,
                                              PracticeAiMetrics metrics) {
        this.cacheRepository = cacheRepository;
        this.explanationClient = explanationClient;
        this.mockExplanationService = mockExplanationService;
        this.openAiProperties = openAiProperties;
        this.objectMapper = objectMapper;
        this.imageResolver = imageResolver;
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

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String getOrCreateExplanation(ExplanationContext context, Long testId) {
        return getOrCreateExplanation(context, testId, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String getOrCreateExplanation(ExplanationContext context, Long testId, Long actorId) {
        return getOrCreateExplanation(
                context, testId, actorId, context.questionContent().imageReference());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String getOrCreateExplanation(ExplanationContext context, Long testId, Long actorId,
                                         String imageReference) {
        if (context.skill() != com.ksh.features.practice.assessment.AssessmentSkill.READING
                && context.skill() != com.ksh.features.practice.assessment.AssessmentSkill.LISTENING) {
            throw new IllegalArgumentException("Explanation context skill must be READING or LISTENING");
        }
        if (!context.questionType().isObjective()) {
            throw new IllegalArgumentException("Explanation context question type must be objective");
        }
        AiImageEvidence imageEvidence = imageResolver == null
                ? null
                : imageResolver.resolve(imageReference, actorId).orElse(null);
        if (!context.stimulus().hasUsableEvidence() && imageEvidence == null) {
            return prepareForDisplay(
                    mockExplanationService.explain(context, "insufficient-approved-evidence"),
                    context);
        }

        VersionedCacheKeyParts versionedKey = buildCacheKeyParts(context, imageEvidence);
        CacheKeyParts keyParts = versionedKey.base();
        Optional<String> cached = readValidCache(
                keyParts, context.questionId(), context.skill().name());
        if (cached.isPresent()) {
            log.info("[ReadingListeningCache] Typed hit questionId={} questionVersionId={}",
                    context.questionId(), context.questionVersionId());
            return prepareForDisplay(cached.get(), context);
        }

        Object lock = cacheLocks.computeIfAbsent(keyParts.cacheKey(), key -> new Object());
        synchronized (lock) {
            try {
                cached = readValidCache(keyParts, context.questionId(), context.skill().name());
                if (cached.isPresent()) {
                    return prepareForDisplay(cached.get(), context);
                }
                long providerStart = PracticeAiMetrics.startNanos();
                String aiJson = imageEvidence == null
                        ? explanationClient.explain(context)
                        : explanationClient.explain(context, imageEvidence);
                if (isValidExplanationJson(aiJson)) {
                    recordRlProvider(PracticeAiMetrics.ProviderOutcome.SUCCESS, providerStart);
                    writeVersionedCache(versionedKey, context, testId, aiJson);
                    return prepareForDisplay(aiJson, context);
                }
                recordRlProvider(PracticeAiMetrics.ProviderOutcome.FALLBACK, providerStart);
                String reason = openAiProperties.apiKey() == null || openAiProperties.apiKey().isBlank()
                        ? "api-key-not-configured"
                        : "provider-unavailable";
                return prepareForDisplay(imageEvidence == null
                        ? mockExplanationService.explain(context, reason)
                        : mockExplanationService.explain(context, reason, true), context);
            } finally {
                cacheLocks.remove(keyParts.cacheKey(), lock);
            }
        }
    }

    /**
     * Keeps stable option IDs in the provider/cache contract, but never exposes those
     * internal IDs as learner-facing option labels.
     */
    private String prepareForDisplay(String explanationJson, ExplanationContext context) {
        if (explanationJson == null || explanationJson.isBlank()) {
            return explanationJson;
        }
        try {
            JsonNode parsed = objectMapper.readTree(explanationJson);
            if (!(parsed instanceof ObjectNode root)) {
                return explanationJson;
            }

            Map<String, String> labelsByOptionId = new LinkedHashMap<>();
            List<String> displayLabels = new java.util.ArrayList<>();
            for (int index = 0; index < context.questionContent().options().size(); index++) {
                String label = optionLabel(index, context.optionLabelMode());
                labelsByOptionId.put(context.questionContent().options().get(index).id(), label);
                displayLabels.add(label);
            }

            ArrayNode displayEliminated = objectMapper.createArrayNode();
            JsonNode eliminatedOptions = root.path("eliminatedOptions");
            if (eliminatedOptions.isArray()) {
                for (JsonNode item : eliminatedOptions) {
                    if (!(item instanceof ObjectNode option)) {
                        continue;
                    }
                    String rawKey = option.path("optionKey").asText("").trim();
                    String displayKey = labelsByOptionId.get(rawKey);
                    if (displayKey == null && displayLabels.contains(rawKey)) {
                        displayKey = rawKey;
                    }
                    if (displayKey == null || displayKey.isBlank()) {
                        continue;
                    }
                    ObjectNode displayOption = option.deepCopy();
                    displayOption.put("optionKey", displayKey);
                    displayEliminated.add(displayOption);
                }
            }
            root.set("eliminatedOptions", displayEliminated);

            List<String> correctAnswers = new java.util.ArrayList<>();
            for (String optionId : context.answerSpec().correctOptionIds()) {
                String displayKey = labelsByOptionId.get(optionId);
                if (displayKey != null && !displayKey.isBlank()) {
                    correctAnswers.add(displayKey);
                }
            }
            if (correctAnswers.isEmpty()
                    && context.answerSpec().correctValue() != null
                    && !context.answerSpec().correctValue().isBlank()) {
                correctAnswers.add(context.answerSpec().correctValue());
            }
            if (correctAnswers.isEmpty()) {
                for (com.ksh.features.practice.assessment.AnswerSpec.BlankAnswer blank
                        : context.answerSpec().blanks()) {
                    correctAnswers.addAll(blank.acceptedValues());
                }
            }
            root.put("correctAnswer", String.join(", ", correctAnswers));
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            log.warn("[ReadingListeningCache] Could not prepare explanation display questionId={} exception={}",
                    context.questionId(), exceptionCategory(exception));
            return explanationJson;
        }
    }

    private static String optionLabel(int index, String optionLabelMode) {
        return "ALPHA".equalsIgnoreCase(optionLabelMode)
                ? String.valueOf((char) ('A' + index))
                : String.valueOf(index + 1);
    }

    public ExplanationLearnerOverlay learnerOverlay(ExplanationContext context, BigDecimal possiblePoints) {
        LearnerAnswer learnerAnswer = context.learnerAnswer();
        if (learnerAnswer == null) {
            learnerAnswer = new LearnerAnswer(
                    LearnerAnswer.SCHEMA_VERSION,
                    context.questionType(),
                    java.util.List.of(),
                    null,
                    java.util.Map.of(),
                    null
            );
        }
        return ExplanationLearnerOverlay.from(
                scoringEngine.score(context.answerSpec(), learnerAnswer, possiblePoints));
    }

    Optional<String> readValidCache(CacheKeyParts keyParts, PracticeQuestion question, String skillType) {
        return readValidCache(keyParts, question.getId(), skillType);
    }

    private Optional<String> readValidCache(CacheKeyParts keyParts, Long questionId, String skillType) {
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
                        questionId, normalize(skillType));
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
                    questionId, normalize(skillType));
            deleteCache(keyParts.cacheKey(), questionId, skillType, "malformed-cache");
            return Optional.empty();
        } catch (Exception ex) {
            recordRlCache(PracticeAiMetrics.CacheOperation.LOOKUP,
                    PracticeAiMetrics.CacheOutcome.FAILURE, lookupStart);
            log.warn("[ReadingListeningCache] Read failed; treating as miss operation=cache-read questionId={} skill={} exception={}",
                    questionId, normalize(skillType), exceptionCategory(ex));
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

    private void writeVersionedCache(VersionedCacheKeyParts versionedKey,
                                     ExplanationContext context,
                                     Long testId,
                                     String explanationJson) {
        long writeStart = PracticeAiMetrics.startNanos();
        CacheKeyParts keyParts = versionedKey.base();
        try {
            cacheRepository.upsertVersioned(
                    keyParts.cacheKey(),
                    context.questionId(),
                    context.questionVersionId(),
                    testId,
                    context.skill().name(),
                    context.questionType().name(),
                    keyParts.questionHash(),
                    versionedKey.stimulusHash(),
                    versionedKey.answerSpecHash(),
                    null,
                    explanationJson,
                    keyParts.model(),
                    keyParts.promptVersion(),
                    keyParts.schemaVersion(),
                    keyParts.language()
            );
            recordRlCache(PracticeAiMetrics.CacheOperation.WRITE,
                    PracticeAiMetrics.CacheOutcome.SUCCESS, writeStart);
            log.info("[ReadingListeningCache] Typed cached questionId={} questionVersionId={}",
                    context.questionId(), context.questionVersionId());
        } catch (Exception exception) {
            recordRlCache(PracticeAiMetrics.CacheOperation.WRITE,
                    PracticeAiMetrics.CacheOutcome.FAILURE, writeStart);
            log.warn("[ReadingListeningCache] Typed write failed questionId={} skill={} exception={}",
                    context.questionId(), context.skill(), exceptionCategory(exception));
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
            if (!isNonBlankTextual(root, "meaningVi")
                    || !isTextual(root, "evidenceQuote")
                    || !isNonBlankTextual(root, "correctReasonVi")
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

    private static boolean isNonBlankTextual(JsonNode node, String fieldName) {
        return isTextual(node, fieldName) && !node.get(fieldName).asText().isBlank();
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

    VersionedCacheKeyParts buildCacheKeyParts(ExplanationContext context) {
        return buildCacheKeyParts(context, null);
    }

    VersionedCacheKeyParts buildCacheKeyParts(ExplanationContext context, AiImageEvidence imageEvidence) {
        String stimulusJson = writeIdentityJson(context.stimulus());
        String answerSpecJson = writeIdentityJson(context.answerSpec());
        String contentJson = writeIdentityJson(context.questionContent());
        String stimulusHash = sha256(framed(
                stimulusJson,
                imageEvidence == null ? "" : imageEvidence.sha256()));
        String answerSpecHash = sha256(framed(answerSpecJson));
        String questionHash = sha256(framed(
                normalize(context.schemaVersion()),
                normalize(String.valueOf(context.questionVersionId())),
                context.skill().name(),
                context.questionType().name(),
                normalize(context.prompt()),
                contentJson,
                answerSpecHash,
                stimulusHash,
                normalize(context.teacherExplanation()),
                normalize(context.optionLabelMode())
        ));
        String model = normalize(explanationClient.model());
        String promptVersion = normalize(explanationClient.promptVersion());
        String schemaVersion = normalize(explanationClient.schemaVersion());
        String language = normalize(context.explanationLanguage());
        String cacheKey = sha256(framed(
                normalize(String.valueOf(context.questionId())),
                normalize(String.valueOf(context.questionVersionId())),
                questionHash,
                stimulusHash,
                answerSpecHash,
                model,
                promptVersion,
                schemaVersion,
                language
        ));
        return new VersionedCacheKeyParts(
                new CacheKeyParts(cacheKey, questionHash, model, promptVersion, schemaVersion, language),
                stimulusHash,
                answerSpecHash
        );
    }

    private String writeIdentityJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not serialize explanation cache identity", exception);
        }
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

    record VersionedCacheKeyParts(CacheKeyParts base, String stimulusHash, String answerSpecHash) {
    }
}
