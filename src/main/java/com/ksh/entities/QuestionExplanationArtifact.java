package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "question_explanation_artifacts")
public class QuestionExplanationArtifact {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String fingerprint;

    @Column(name = "legacy_cache_id")
    private Long legacyCacheId;

    @Column(nullable = false, length = 20)
    private String skill;

    @Column(name = "question_type", nullable = false, length = 40)
    private String questionType;

    @Column(name = "assessment_schema_version", nullable = false, length = 40)
    private String assessmentSchemaVersion;

    @Column(name = "provider_model", nullable = false, length = 100)
    private String providerModel;

    @Column(name = "prompt_version", nullable = false, length = 40)
    private String promptVersion;

    @Column(name = "response_schema_version", nullable = false, length = 40)
    private String responseSchemaVersion;

    @Column(name = "explanation_language", nullable = false, length = 16)
    private String explanationLanguage;

    @Column(name = "question_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String questionHash;

    @Column(name = "stimulus_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String stimulusHash;

    @Column(name = "answer_spec_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String answerSpecHash;

    @Column(name = "media_bundle_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String mediaBundleHash;

    @Column(name = "input_contract_json", nullable = false, columnDefinition = "JSON")
    private String inputContractJson;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "explanation_json", columnDefinition = "JSON")
    private String explanationJson;

    @Column(name = "error_category", length = 64)
    private String errorCategory;

    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected QuestionExplanationArtifact() {
    }

    public void markReady(String explanationJson, LocalDateTime now) {
        this.status = STATUS_READY;
        this.explanationJson = explanationJson;
        this.errorCategory = null;
        this.lastErrorMessage = null;
        this.readyAt = now;
        this.failedAt = null;
    }

    public void markPending() {
        this.status = STATUS_PENDING;
        this.explanationJson = null;
        this.errorCategory = null;
        this.lastErrorMessage = null;
        this.readyAt = null;
        this.failedAt = null;
    }

    public void markFailed(String category, String message, LocalDateTime now) {
        this.status = STATUS_FAILED;
        this.explanationJson = null;
        this.errorCategory = category;
        this.lastErrorMessage = truncate(message);
        this.readyAt = null;
        this.failedAt = now;
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    public Long getId() { return id; }
    public String getFingerprint() { return fingerprint; }
    public Long getLegacyCacheId() { return legacyCacheId; }
    public String getSkill() { return skill; }
    public String getQuestionType() { return questionType; }
    public String getAssessmentSchemaVersion() { return assessmentSchemaVersion; }
    public String getProviderModel() { return providerModel; }
    public String getPromptVersion() { return promptVersion; }
    public String getResponseSchemaVersion() { return responseSchemaVersion; }
    public String getExplanationLanguage() { return explanationLanguage; }
    public String getQuestionHash() { return questionHash; }
    public String getStimulusHash() { return stimulusHash; }
    public String getAnswerSpecHash() { return answerSpecHash; }
    public String getMediaBundleHash() { return mediaBundleHash; }
    public String getInputContractJson() { return inputContractJson; }
    public String getStatus() { return status; }
    public String getExplanationJson() { return explanationJson; }
    public String getErrorCategory() { return errorCategory; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public LocalDateTime getReadyAt() { return readyAt; }
    public LocalDateTime getFailedAt() { return failedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
