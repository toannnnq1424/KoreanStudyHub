package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A per-skill attempt by a learner. Each attempt belongs to exactly one
 * skill (READING, LISTENING, WRITING, SPEAKING) — never MIXED.
 *
 * Replaces the old PracticeSubmission for the new per-skill flow.
 * Old PracticeSubmission records are kept for legacy compatibility.
 */
@Entity
@Table(name = "practice_attempts")
public class PracticeAttempt {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_GRADED = "GRADED";

    public static final String ANALYSIS_NOT_REQUESTED = "NOT_REQUESTED";
    public static final String ANALYSIS_QUEUED = "QUEUED";
    public static final String ANALYSIS_PROCESSING = "PROCESSING";
    public static final String ANALYSIS_SUCCEEDED = "SUCCEEDED";
    public static final String ANALYSIS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "set_id", nullable = false)
    private Long setId;

    @Column(name = "test_id", nullable = false)
    private Long testId;

    @Column(nullable = false, length = 20)
    private String skill;

    @Column(name = "section_id")
    private Long sectionId;

    @Column(nullable = false, length = 30)
    private String status = STATUS_IN_PROGRESS;

    @Column(name = "analysis_status", nullable = false, length = 30)
    private String analysisStatus = ANALYSIS_NOT_REQUESTED;

    @Column(precision = 6, scale = 2)
    private BigDecimal score;

    @Column(name = "total_points", precision = 6, scale = 2)
    private BigDecimal totalPoints;

    @Column(name = "answers_json", columnDefinition = "JSON")
    private String answersJson;

    @Column(name = "ai_feedback_json", columnDefinition = "JSON")
    private String aiFeedbackJson;

    @Column(name = "analysis_requested_at")
    private LocalDateTime analysisRequestedAt;

    @Column(name = "analysis_completed_at")
    private LocalDateTime analysisCompletedAt;

    @Column(name = "analysis_usage_id")
    private Long analysisUsageId;

    @Column(name = "analysis_engine", length = 50)
    private String analysisEngine;

    @Column(name = "analysis_error_code", length = 100)
    private String analysisErrorCode;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PracticeAttempt() {
    }

    public PracticeAttempt(Long userId, Long setId, Long testId, String skill, Long sectionId) {
        this.userId = userId;
        this.setId = setId;
        this.testId = testId;
        this.skill = skill;
        this.sectionId = sectionId;
        this.status = STATUS_IN_PROGRESS;
        this.analysisStatus = ANALYSIS_NOT_REQUESTED;
        this.startedAt = LocalDateTime.now();
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

    // --- Score update helpers ---

    public void markSubmitted(BigDecimal score, BigDecimal totalPoints, String answersJson) {
        this.score = score;
        this.totalPoints = totalPoints;
        this.answersJson = answersJson;
        this.status = STATUS_SUBMITTED;
        this.submittedAt = LocalDateTime.now();
    }

    public void markGraded(BigDecimal score, BigDecimal totalPoints,
                           String answersJson, String aiFeedbackJson) {
        this.score = score;
        this.totalPoints = totalPoints;
        this.answersJson = answersJson;
        this.aiFeedbackJson = aiFeedbackJson;
        this.status = STATUS_GRADED;
        this.submittedAt = LocalDateTime.now();
    }

    public void markAnalysisSucceeded(BigDecimal score, String aiFeedbackJson,
                                       Long usageId, String engine) {
        this.score = score;
        this.aiFeedbackJson = aiFeedbackJson;
        this.analysisStatus = ANALYSIS_SUCCEEDED;
        this.analysisCompletedAt = LocalDateTime.now();
        this.analysisUsageId = usageId;
        this.analysisEngine = engine;
        this.status = STATUS_GRADED;
    }

    public void markAnalysisFailed(String errorCode, Long usageId) {
        this.analysisStatus = ANALYSIS_FAILED;
        this.analysisCompletedAt = LocalDateTime.now();
        this.analysisErrorCode = errorCode;
        this.analysisUsageId = usageId;
    }

    public boolean isObjectiveSkill() {
        return "READING".equals(skill) || "LISTENING".equals(skill);
    }

    public boolean isSubjectiveSkill() {
        return "WRITING".equals(skill) || "SPEAKING".equals(skill);
    }

    // --- Getters / Setters ---

    public Long getId() { return id; }
    public Long getLockVersion() { return lockVersion; }
    public Long getUserId() { return userId; }
    public Long getSetId() { return setId; }
    public Long getTestId() { return testId; }
    public String getSkill() { return skill; }
    public Long getSectionId() { return sectionId; }
    public String getStatus() { return status; }
    public String getAnalysisStatus() { return analysisStatus; }
    public BigDecimal getScore() { return score; }
    public BigDecimal getTotalPoints() { return totalPoints; }
    public String getAnswersJson() { return answersJson; }
    public String getAiFeedbackJson() { return aiFeedbackJson; }
    public LocalDateTime getAnalysisRequestedAt() { return analysisRequestedAt; }
    public LocalDateTime getAnalysisCompletedAt() { return analysisCompletedAt; }
    public Long getAnalysisUsageId() { return analysisUsageId; }
    public String getAnalysisEngine() { return analysisEngine; }
    public String getAnalysisErrorCode() { return analysisErrorCode; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setStatus(String status) { this.status = status; }
    public void setLockVersion(Long lockVersion) { this.lockVersion = lockVersion; }
    public void setAnalysisStatus(String analysisStatus) { this.analysisStatus = analysisStatus; }
    public void setScore(BigDecimal score) { this.score = score; }
    public void setTotalPoints(BigDecimal totalPoints) { this.totalPoints = totalPoints; }
    public void setAnswersJson(String answersJson) { this.answersJson = answersJson; }
    public void setAiFeedbackJson(String aiFeedbackJson) { this.aiFeedbackJson = aiFeedbackJson; }
    public void setAnalysisRequestedAt(LocalDateTime analysisRequestedAt) { this.analysisRequestedAt = analysisRequestedAt; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
}
