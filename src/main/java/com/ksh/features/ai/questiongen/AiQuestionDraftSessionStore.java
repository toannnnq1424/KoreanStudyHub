package com.ksh.features.ai.questiongen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.DraftQuestion;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.Preview;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Persists and atomically consumes short-lived AI previews. */
@Service
public class AiQuestionDraftSessionStore {

    static final long TTL_MINUTES = 10;
    static final String MSG_SESSION_EXPIRED =
            "Phiên sinh câu hỏi đã hết hạn, vui lòng sinh lại";

    private static final TypeReference<List<DraftQuestion>> QUESTION_LIST =
            new TypeReference<>() {
            };

    private final AiQuestionDraftSessionRepository repository;
    private final ObjectMapper objectMapper;

    public AiQuestionDraftSessionStore(AiQuestionDraftSessionRepository repository,
                                       ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Preview save(Long actorId, Long testId, List<DraftQuestion> questions) {
        LocalDateTime now = now();
        String id = UUID.randomUUID().toString();
        AiQuestionDraftSessionEntity entity = new AiQuestionDraftSessionEntity(
                id, actorId, testId, writeQuestions(questions), now,
                now.plusMinutes(TTL_MINUTES));
        repository.saveAndFlush(entity);
        return new Preview(id, List.copyOf(questions));
    }

    /**
     * Locks the preview row. The caller's transaction owns the lock until its exam
     * mutation and consumed marker either both commit or both roll back.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LoadedSession requireForUpdate(String rawId, Long actorId, Long testId) {
        String id = canonicalId(rawId);
        AiQuestionDraftSessionEntity entity = repository
                .findOwnedForUpdate(id, actorId, testId)
                .filter(candidate -> candidate.isPendingAt(now()))
                .orElseThrow(AiQuestionDraftSessionStore::expired);
        return new LoadedSession(entity, readQuestions(entity.getQuestionsJson()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void consume(LoadedSession session) {
        session.entity().markConsumed(now());
        repository.save(session.entity());
    }

    private String writeQuestions(List<DraftQuestion> questions) {
        try {
            return objectMapper.writeValueAsString(questions);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không thể lưu phiên xem trước câu hỏi AI", ex);
        }
    }

    private List<DraftQuestion> readQuestions(String json) {
        try {
            List<DraftQuestion> questions = objectMapper.readValue(json, QUESTION_LIST);
            if (questions == null || questions.isEmpty()) {
                throw expired();
            }
            return List.copyOf(questions);
        } catch (JsonProcessingException ex) {
            throw expired();
        }
    }

    private static String canonicalId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw expired();
        }
        try {
            String normalized = rawId.trim().toLowerCase(Locale.ROOT);
            String canonical = UUID.fromString(normalized).toString();
            if (!canonical.equals(normalized)) {
                throw expired();
            }
            return canonical;
        } catch (IllegalArgumentException ex) {
            throw expired();
        }
    }

    private static IllegalArgumentException expired() {
        return new IllegalArgumentException(MSG_SESSION_EXPIRED);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public record LoadedSession(AiQuestionDraftSessionEntity entity,
                                List<DraftQuestion> questions) {
    }
}
