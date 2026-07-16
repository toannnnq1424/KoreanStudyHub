package com.ksh.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "practice_ai_request_audits")
public class PracticeAiRequestAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "model")
    private String model;

    @Column(name = "strategy")
    private String strategy;

    @Column(name = "sent_text_chars", nullable = false)
    private Integer sentTextChars = 0;

    @Column(name = "sent_image_count", nullable = false)
    private Integer sentImageCount = 0;

    @Column(name = "sent_image_bytes", nullable = false)
    private Long sentImageBytes = 0L;

    @Column(name = "payload_summary_json", columnDefinition = "LONGTEXT")
    private String payloadSummaryJson;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PracticeAiRequestAudit() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public Integer getSentTextChars() {
        return sentTextChars;
    }

    public void setSentTextChars(Integer sentTextChars) {
        this.sentTextChars = sentTextChars;
    }

    public Integer getSentImageCount() {
        return sentImageCount;
    }

    public void setSentImageCount(Integer sentImageCount) {
        this.sentImageCount = sentImageCount;
    }

    public Long getSentImageBytes() {
        return sentImageBytes;
    }

    public void setSentImageBytes(Long sentImageBytes) {
        this.sentImageBytes = sentImageBytes;
    }

    public String getPayloadSummaryJson() {
        return payloadSummaryJson;
    }

    public void setPayloadSummaryJson(String payloadSummaryJson) {
        this.payloadSummaryJson = payloadSummaryJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
