package com.ksh.features.ai.questiongen;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

interface AiQuestionDraftSessionRepository
        extends JpaRepository<AiQuestionDraftSessionEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from AiQuestionDraftSessionEntity s
            where s.id = :id and s.actorId = :actorId and s.testId = :testId
            """)
    Optional<AiQuestionDraftSessionEntity> findOwnedForUpdate(
            @Param("id") String id,
            @Param("actorId") Long actorId,
            @Param("testId") Long testId);

    @Modifying
    @Query(value = """
            DELETE FROM ai_question_draft_sessions
            WHERE expires_at < :cutoff
            ORDER BY expires_at ASC
            LIMIT 500
            """, nativeQuery = true)
    int deleteExpiredBatch(@Param("cutoff") LocalDateTime cutoff);
}
