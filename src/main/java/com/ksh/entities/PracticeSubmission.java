package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "practice_submissions")
public class PracticeSubmission {

    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_GRADED = "GRADED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "set_id", nullable = false)
    private Long setId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String status = STATUS_SUBMITTED;

    @Column(precision = 6, scale = 2)
    private BigDecimal score;

    @Column(name = "total_points", precision = 6, scale = 2)
    private BigDecimal totalPoints;

    @Column(name = "answers_json", columnDefinition = "JSON")
    private String answersJson;

    @Column(name = "ai_feedback_json", columnDefinition = "JSON")
    private String aiFeedbackJson;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PracticeSubmission() {
    }

    public PracticeSubmission(Long setId, Long userId, BigDecimal score,
                              BigDecimal totalPoints, String answersJson,
                              String aiFeedbackJson) {
        this.setId = setId;
        this.userId = userId;
        this.score = score;
        this.totalPoints = totalPoints;
        this.answersJson = answersJson;
        this.aiFeedbackJson = aiFeedbackJson;
        this.status = aiFeedbackJson == null ? STATUS_SUBMITTED : STATUS_GRADED;
        this.startedAt = LocalDateTime.now();
        this.submittedAt = LocalDateTime.now();
    }

    public void updateEvaluation(BigDecimal score, BigDecimal totalPoints, String answersJson, String aiFeedbackJson) {
        this.score = score;
        this.totalPoints = totalPoints;
        this.answersJson = answersJson;
        this.aiFeedbackJson = aiFeedbackJson;
        this.status = aiFeedbackJson == null ? STATUS_SUBMITTED : STATUS_GRADED;
        this.submittedAt = LocalDateTime.now();
    }

    @PrePersist
    void onPersist() {
        LocalDateTime now = LocalDateTime.now();
        if (startedAt == null) startedAt = now;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public void setAnswersJson(String answersJson) {
        this.answersJson = answersJson;
    }

    public void setAiFeedbackJson(String aiFeedbackJson) {
        this.aiFeedbackJson = aiFeedbackJson;
    }

    public Long getId() {
        return id;
    }

    public Long getSetId() {
        return setId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getScore() {
        return score;
    }

    public BigDecimal getTotalPoints() {
        return totalPoints;
    }

    public String getAnswersJson() {
        return answersJson;
    }

    public String getAiFeedbackJson() {
        return aiFeedbackJson;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
