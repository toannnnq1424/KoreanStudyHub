package com.ksh.features.practice.service;

import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.QuestionExplanationCache;
import com.ksh.features.practice.ai.OpenAiProperties;
import com.ksh.features.practice.ai.ReadingListeningExplanationClient;
import com.ksh.features.practice.ai.ReadingListeningMockExplanationService;
import com.ksh.features.practice.repository.QuestionExplanationCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-question explanation lookup and caching for Reading/Listening skills.
 *
 * <h3>Cache strategy</h3>
 * <ul>
 *   <li>Cache <em>hit</em>  → return stored {@code explanation_json} immediately.</li>
 *   <li>Cache <em>miss</em> → call AI; if AI succeeds, persist to DB; return real JSON.</li>
 *   <li>AI <em>unavailable</em> (quota, network, timeout) → generate a mock explanation
 *       on the fly from question data. Mock is NOT cached, so the next page visit will
 *       retry the AI call and cache the result if it succeeds.</li>
 * </ul>
 *
 * <h3>Concurrency</h3>
 * Uses a {@link ConcurrentHashMap}-based per-questionId lock (instead of
 * {@link String#intern()}) to prevent concurrent duplicate AI calls for the
 * same question without risking intern-pool saturation.
 */
@Service
public class ReadingListeningExplanationService {

    private static final Logger log = LoggerFactory.getLogger(ReadingListeningExplanationService.class);

    private final QuestionExplanationCacheRepository cacheRepository;
    private final ReadingListeningExplanationClient explanationClient;
    private final ReadingListeningMockExplanationService mockExplanationService;
    private final OpenAiProperties openAiProperties;
 
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private ReadingListeningExplanationService self;
 
    /** Per-question-ID lock objects. Prevents thundering herd on cache miss. */
    private final ConcurrentHashMap<Long, Object> questionLocks = new ConcurrentHashMap<>();

    public ReadingListeningExplanationService(QuestionExplanationCacheRepository cacheRepository,
                                              ReadingListeningExplanationClient explanationClient,
                                              ReadingListeningMockExplanationService mockExplanationService,
                                              OpenAiProperties openAiProperties) {
        this.cacheRepository = cacheRepository;
        this.explanationClient = explanationClient;
        this.mockExplanationService = mockExplanationService;
        this.openAiProperties = openAiProperties;
    }

    /**
     * Returns an explanation JSON for the given question.
     *
     * <p>The {@code @Transactional} scope is intentionally narrow: only the
     * DB reads/writes are transactional. The AI HTTP call happens outside the
     * try-catch of the transaction interceptor because {@link ReadingListeningExplanationClient#explain}
     * never throws — it returns {@code null} on failure.
     *
     * @param question    the question to explain
     * @param passageText the reading passage or listening transcript (may be empty)
     * @param skillType   "READING" or "LISTENING"
     * @param testId      the practice set ID (used for cache record)
     * @return explanation JSON string, never null
     */
    @Transactional
    public String getOrCreateExplanation(PracticeQuestion question, String passageText,
                                         String skillType, Long testId, String optionLabelMode) {
        String hash = buildQuestionHash(question, passageText, optionLabelMode);
        String correctAnswer = question.getAnswerKey() != null ? question.getAnswerKey().trim() : "";

        // ── Fast-path: unsynchronized read ──────────────────────────────────
        Optional<QuestionExplanationCache> cached = cacheRepository
                .findByQuestionIdAndQuestionHashAndCorrectAnswer(question.getId(), hash, correctAnswer);
        if (cached.isPresent()) {
            log.info("[ReadingListeningCache] Hit questionId={}", question.getId());
            return cached.get().getExplanationJson();
        }

        // ── Slow-path: per-question lock to deduplicate concurrent requests ─
        Object lock = questionLocks.computeIfAbsent(question.getId(), k -> new Object());
        synchronized (lock) {
            try {
                // Double-check inside lock
                cached = cacheRepository.findByQuestionIdAndQuestionHashAndCorrectAnswer(
                        question.getId(), hash, correctAnswer);
                if (cached.isPresent()) {
                    log.info("[ReadingListeningCache] Hit (double-check) questionId={}", question.getId());
                    return cached.get().getExplanationJson();
                }

                // ── Call AI ─────────────────────────────────────────────────
                log.info("[ReadingListeningCache] Miss questionId={}, calling AI...", question.getId());
                // explain() returns null when AI is unavailable — never throws
                String aiJson = explanationClient.explain(question, passageText, skillType, optionLabelMode);

                if (aiJson != null) {
                    // AI succeeded → persist to cache so future visits are instant
                    try {
                        self.persistCache(question, testId, skillType, hash, correctAnswer, aiJson);
                    } catch (Exception ex) {
                        log.warn("[ReadingListeningCache] Miss-handled write propagation: {}", ex.getMessage());
                    }
                    return aiJson;
                }

                // ── AI failed → use mock, do NOT cache ──────────────────────
                String mockReason = (openAiProperties.apiKey() == null || openAiProperties.apiKey().isBlank())
                        ? "chưa cấu hình API key"
                        : "hạn ngạch API tạm thời đã hết — thử lại sau";
                log.info("[ReadingListeningCache] AI unavailable for questionId={}, using mock (reason: {})",
                        question.getId(), mockReason);
                return mockExplanationService.explain(question, passageText, skillType, optionLabelMode, mockReason);

            } finally {
                // Remove the lock to avoid accumulating stale entries over time.
                // Safe because the ConcurrentHashMap will create a new one if needed later.
                questionLocks.remove(question.getId(), lock);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void persistCache(PracticeQuestion question, Long testId, String skillType,
                               String hash, String correctAnswer, String aiJson) {
        try {
            QuestionExplanationCache record = new QuestionExplanationCache(
                    question.getId(),
                    testId,
                    skillType,
                    question.getQuestionType(),
                    hash,
                    correctAnswer,
                    aiJson,
                    openAiProperties.evaluatorModel()
            );
            cacheRepository.saveAndFlush(record);
            log.info("[ReadingListeningCache] Cached questionId={}", question.getId());
        } catch (Exception ex) {
            // Non-fatal: log and continue. The explanation is still returned to the user.
            log.warn("[ReadingListeningCache] Failed to save cache for questionId={}: {}",
                    question.getId(), ex.getMessage());
        }
    }

    private String buildQuestionHash(PracticeQuestion q, String passageText, String optionLabelMode) {
        try {
            final String EXPLANATION_PROMPT_VERSION = "v2";
            String raw = (q.getPrompt() != null ? q.getPrompt() : "") + "|"
                    + (q.getOptionsJson() != null ? q.getOptionsJson() : "") + "|"
                    + (q.getAnswerKey() != null ? q.getAnswerKey() : "") + "|"
                    + (passageText != null ? passageText : "") + "|"
                    + (optionLabelMode != null ? optionLabelMode : "") + "|"
                    + EXPLANATION_PROMPT_VERSION;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "default-hash-" + q.getId();
        }
    }
}
