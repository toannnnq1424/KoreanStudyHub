package com.ksh.features.ai.questiongen;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/** Durable, short-lived preview that makes confirm atomic across application nodes. */
@Entity
@Table(name = "ai_question_draft_sessions")
public class AiQuestionDraftSessionEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONSUMED = "CONSUMED";

    @Id
    @Column(length = 36, columnDefinition = "CHAR(36)")
    private String id;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "test_id", nullable = false)
    private Long testId;

    @Column(name = "questions_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String questionsJson;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected AiQuestionDraftSessionEntity() {
    }

    AiQuestionDraftSessionEntity(String id, Long actorId, Long testId, String questionsJson,
                                 LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.id = id;
        this.actorId = actorId;
        this.testId = testId;
        this.questionsJson = questionsJson;
        this.status = STATUS_PENDING;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public void markConsumed(LocalDateTime now) {
        this.status = STATUS_CONSUMED;
        this.consumedAt = now;
    }

    public boolean isPendingAt(LocalDateTime now) {
        return STATUS_PENDING.equals(status) && expiresAt.isAfter(now);
    }

    public String getId() {
        return id;
    }

    public String getQuestionsJson() {
        return questionsJson;
    }
}
