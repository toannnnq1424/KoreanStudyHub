package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "question_explanation_cache")
public class QuestionExplanationCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cache_key", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String cacheKey;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "test_id")
    private Long testId;

    @Column(name = "skill_type", nullable = false, length = 20)
    private String skillType;

    @Column(name = "question_type", nullable = false, length = 40)
    private String questionType;

    @Column(name = "question_hash", nullable = false, length = 64)
    private String questionHash;

    @Column(name = "correct_answer", length = 500)
    private String correctAnswer;

    @Column(name = "explanation_json", nullable = false, columnDefinition = "JSON")
    private String explanationJson;

    @Column(name = "ai_model", nullable = false, length = 100)
    private String aiModel;

    @Column(name = "prompt_version", nullable = false, length = 32)
    private String promptVersion;

    @Column(name = "schema_version", nullable = false, length = 32)
    private String schemaVersion;

    @Column(name = "explanation_language", nullable = false, length = 16)
    private String explanationLanguage;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected QuestionExplanationCache() {
    }

    public QuestionExplanationCache(String cacheKey, Long questionId, Long testId, String skillType, String questionType,
                                    String questionHash, String correctAnswer, String explanationJson, String aiModel,
                                    String promptVersion, String schemaVersion, String explanationLanguage) {
        this.cacheKey = cacheKey;
        this.questionId = questionId;
        this.testId = testId;
        this.skillType = skillType;
        this.questionType = questionType;
        this.questionHash = questionHash;
        this.correctAnswer = correctAnswer;
        this.explanationJson = explanationJson;
        this.aiModel = aiModel;
        this.promptVersion = promptVersion;
        this.schemaVersion = schemaVersion;
        this.explanationLanguage = explanationLanguage;
    }

    public Long getId() {
        return id;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public Long getTestId() {
        return testId;
    }

    public String getSkillType() {
        return skillType;
    }

    public String getQuestionType() {
        return questionType;
    }

    public String getQuestionHash() {
        return questionHash;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public String getExplanationJson() {
        return explanationJson;
    }

    public void setExplanationJson(String explanationJson) {
        this.explanationJson = explanationJson;
    }

    public String getAiModel() {
        return aiModel;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public String getExplanationLanguage() {
        return explanationLanguage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
