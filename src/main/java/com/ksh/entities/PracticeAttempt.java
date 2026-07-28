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
 * Canonical learner attempt aggregate for every practice skill.
 */
@Entity
@Table(name = "practice_attempts")
public class PracticeAttempt {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_GRADED = "GRADED";
    public static final String STATUS_DISCARDED = "DISCARDED";

    public static final String ANALYSIS_NOT_REQUESTED = "NOT_REQUESTED";
    public static final String ANALYSIS_QUEUED = "QUEUED";
    public static final String ANALYSIS_PROCESSING = "PROCESSING";
    public static final String ANALYSIS_SUCCEEDED = "SUCCEEDED";
    public static final String ANALYSIS_FAILED = "FAILED";
    public static final String ANALYSIS_UNAVAILABLE = "UNAVAILABLE";

    public static final int MAX_DEADLINE_RECONCILE_ATTEMPTS = 5;

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

    @Column(name = "published_version_id")
    private Long publishedVersionId;

    @Column(name = "set_version_id")
    private Long setVersionId;

    @Column(name = "test_version_id")
    private Long testVersionId;

    @Column(name = "section_version_id")
    private Long sectionVersionId;

    @Column(name = "version_compatibility_status", length = 40)
    private String versionCompatibilityStatus;

    @Column(name = "version_compatibility_note", length = 500)
    private String versionCompatibilityNote;

    @Column(nullable = false, length = 30)
    private String status = STATUS_IN_PROGRESS;

    @Column(name = "analysis_status", nullable = false, length = 30)
    private String analysisStatus = ANALYSIS_NOT_REQUESTED;

    @Column(precision = 6, scale = 2)
    private BigDecimal score;

    @Column(name = "total_points", precision = 6, scale = 2)
    private BigDecimal totalPoints;

    @Column(name = "score_unit", length = 30)
    private String scoreUnit;

    @Column(name = "earned_points", precision = 8, scale = 2)
    private BigDecimal earnedPoints;

    @Column(name = "score_percentage", precision = 6, scale = 2)
    private BigDecimal scorePercentage;

    @Column(name = "answers_json", columnDefinition = "JSON")
    private String answersJson;

    @Column(name = "ai_feedback_json", columnDefinition = "JSON")
    private String aiFeedbackJson;

    @Column(name = "analysis_requested_at")
    private LocalDateTime analysisRequestedAt;

    @Column(name = "analysis_completed_at")
    private LocalDateTime analysisCompletedAt;

    @Column(name = "analysis_engine", length = 50)
    private String analysisEngine;

    @Column(name = "analysis_error_code", length = 100)
    private String analysisErrorCode;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "deadline_at", nullable = false)
    private LocalDateTime deadlineAt;

    @Column(name = "last_saved_at")
    private LocalDateTime lastSavedAt;

    @Column(name = "deadline_reconcile_attempts", nullable = false)
    private Integer deadlineReconcileAttempts = 0;

    @Column(name = "deadline_reconcile_next_at")
    private LocalDateTime deadlineReconcileNextAt;

    @Column(name = "deadline_reconcile_error_code", length = 100)
    private String deadlineReconcileErrorCode;

    @Column(name = "deadline_reconcile_quarantined_at")
    private LocalDateTime deadlineReconcileQuarantinedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "discarded_at")
    private LocalDateTime discardedAt;

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

    public void lockPublishedVersion(Long publishedVersionId, Long setVersionId,
                                     Long testVersionId, Long sectionVersionId) {
        if (publishedVersionId == null || setVersionId == null || testVersionId == null || sectionVersionId == null) {
            throw new IllegalArgumentException("Published practice version lock is incomplete.");
        }
        this.publishedVersionId = publishedVersionId;
        this.setVersionId = setVersionId;
        this.testVersionId = testVersionId;
        this.sectionVersionId = sectionVersionId;
        this.versionCompatibilityStatus = null;
        this.versionCompatibilityNote = null;
    }

    @PrePersist
    void onPersist() {
        LocalDateTime now = LocalDateTime.now();
        if (startedAt == null) startedAt = now;
        if (deadlineAt == null) deadlineAt = startedAt.plusMinutes(40);
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // --- Score update helpers ---

    public void markSubmitted(BigDecimal score, BigDecimal totalPoints, String answersJson) {
        requireNotDiscarded();
        this.score = score;
        this.totalPoints = totalPoints;
        this.answersJson = answersJson;
        synchronizeScoreContract(score, totalPoints);
        this.status = STATUS_SUBMITTED;
        this.submittedAt = LocalDateTime.now();
    }

    public void markSubmittedForAnalysis(BigDecimal totalPoints, String answersJson,
                                         LocalDateTime now) {
        requireNotDiscarded();
        if (!STATUS_IN_PROGRESS.equals(status)) {
            throw new IllegalStateException(
                    "Only an in-progress attempt can be queued for analysis.");
        }
        LocalDateTime effectiveNow = requireNow(now);
        this.score = null;
        this.totalPoints = totalPoints;
        this.answersJson = answersJson;
        this.aiFeedbackJson = null;
        synchronizeScoreContract(null, totalPoints);
        this.status = STATUS_SUBMITTED;
        this.submittedAt = effectiveNow;
        this.analysisStatus = ANALYSIS_QUEUED;
        this.analysisRequestedAt = effectiveNow;
        this.analysisCompletedAt = null;
        this.analysisEngine = null;
        this.analysisErrorCode = null;
    }

    public void markGraded(BigDecimal score, BigDecimal totalPoints,
                           String answersJson, String aiFeedbackJson) {
        requireNotDiscarded();
        this.score = score;
        this.totalPoints = totalPoints;
        this.answersJson = answersJson;
        this.aiFeedbackJson = aiFeedbackJson;
        synchronizeScoreContract(score, totalPoints);
        this.status = STATUS_GRADED;
        this.submittedAt = LocalDateTime.now();
    }

    public void markAnalysisSucceeded(BigDecimal score, String aiFeedbackJson,
                                      String engine) {
        requireNotDiscarded();
        this.score = score;
        synchronizeScoreContract(score, this.totalPoints);
        this.aiFeedbackJson = aiFeedbackJson;
        this.analysisStatus = ANALYSIS_SUCCEEDED;
        this.analysisCompletedAt = LocalDateTime.now();
        this.analysisEngine = engine;
        this.status = STATUS_GRADED;
    }

    public void markAnalysisSucceeded(BigDecimal score, BigDecimal totalPoints,
                                      String answersJson, String aiFeedbackJson,
                                      String engine, LocalDateTime now) {
        requireNotDiscarded();
        this.score = score;
        this.totalPoints = totalPoints;
        this.answersJson = answersJson;
        this.aiFeedbackJson = aiFeedbackJson;
        synchronizeScoreContract(score, totalPoints);
        this.analysisStatus = ANALYSIS_SUCCEEDED;
        this.analysisCompletedAt = requireNow(now);
        this.analysisEngine = engine;
        this.analysisErrorCode = null;
        this.status = STATUS_GRADED;
    }

    public void markAnalysisProcessing() {
        requireNotDiscarded();
        if (!STATUS_SUBMITTED.equals(status) && !STATUS_GRADED.equals(status)) {
            throw new IllegalStateException(
                    "Only a terminal learner submission can be processed.");
        }
        this.analysisStatus = ANALYSIS_PROCESSING;
        this.analysisCompletedAt = null;
        this.analysisErrorCode = null;
    }

    public void markAnalysisQueued(LocalDateTime now) {
        requireNotDiscarded();
        if (!STATUS_SUBMITTED.equals(status) && !STATUS_GRADED.equals(status)) {
            throw new IllegalStateException(
                    "Only a terminal learner submission can be queued.");
        }
        this.analysisStatus = ANALYSIS_QUEUED;
        this.analysisRequestedAt = requireNow(now);
        this.analysisCompletedAt = null;
        this.analysisErrorCode = null;
    }

    public void markAnalysisUnavailable(BigDecimal totalPoints,
                                        String answersJson,
                                        String aiFeedbackJson,
                                        String errorCode,
                                        boolean preserveExistingResult,
                                        LocalDateTime now) {
        requireNotDiscarded();
        if (!preserveExistingResult) {
            this.score = null;
            this.totalPoints = totalPoints;
            this.answersJson = answersJson;
            this.aiFeedbackJson = aiFeedbackJson;
            synchronizeScoreContract(null, totalPoints);
            this.status = STATUS_SUBMITTED;
        }
        this.analysisStatus = ANALYSIS_UNAVAILABLE;
        this.analysisCompletedAt = requireNow(now);
        this.analysisErrorCode = errorCode;
    }

    public void markAnalysisFailed(String errorCode) {
        requireNotDiscarded();
        this.analysisStatus = ANALYSIS_FAILED;
        this.analysisCompletedAt = LocalDateTime.now();
        this.analysisErrorCode = errorCode;
    }

    public void markAnalysisFailed(String errorCode, LocalDateTime now) {
        requireNotDiscarded();
        this.analysisStatus = ANALYSIS_FAILED;
        this.analysisCompletedAt = requireNow(now);
        this.analysisErrorCode = errorCode;
    }

    public void configureDeadline(Integer durationMinutes, LocalDateTime now) {
        requireNotDiscarded();
        if (!STATUS_IN_PROGRESS.equals(status)) {
            throw new IllegalStateException(
                    "A deadline can only be configured for an in-progress attempt.");
        }
        int effectiveMinutes = durationMinutes == null || durationMinutes <= 0
                ? 40 : durationMinutes;
        this.deadlineAt = requireNow(now).plusMinutes(effectiveMinutes);
    }

    public boolean isExpired(LocalDateTime now) {
        return deadlineAt != null && now != null && !deadlineAt.isAfter(now);
    }

    public void saveAnswers(String answersJson, LocalDateTime now) {
        requireNotDiscarded();
        if (!STATUS_IN_PROGRESS.equals(status)) {
            throw new IllegalStateException(
                    "Only an in-progress attempt can save answers.");
        }
        this.answersJson = answersJson;
        this.lastSavedAt = requireNow(now);
    }

    public void recordDeadlineReconcileFailure(
            String errorCode,
            LocalDateTime now) {
        requireNotDiscarded();
        if (!isDeadlineReconcileDue(now)) {
            throw new IllegalStateException(
                    "Only a due expired attempt can record deadline reconciliation failure.");
        }
        LocalDateTime effectiveNow = requireNow(now);
        int nextAttempt = Math.min(
                MAX_DEADLINE_RECONCILE_ATTEMPTS,
                deadlineReconcileAttempts == null
                        ? 1
                        : deadlineReconcileAttempts + 1);
        deadlineReconcileAttempts = nextAttempt;
        deadlineReconcileErrorCode = truncate(errorCode, 100);
        if (nextAttempt >= MAX_DEADLINE_RECONCILE_ATTEMPTS) {
            deadlineReconcileNextAt = null;
            deadlineReconcileQuarantinedAt = effectiveNow;
            return;
        }
        long delaySeconds = Math.min(
                900L,
                30L * (1L << Math.min(nextAttempt - 1, 4)));
        deadlineReconcileNextAt =
                effectiveNow.plusSeconds(delaySeconds);
        deadlineReconcileQuarantinedAt = null;
    }

    public boolean isDeadlineReconcileQuarantined() {
        return deadlineReconcileQuarantinedAt != null;
    }

    public boolean isDeadlineReconcileDue(LocalDateTime now) {
        return STATUS_IN_PROGRESS.equals(status)
                && isExpired(now)
                && deadlineReconcileQuarantinedAt == null
                && (deadlineReconcileNextAt == null
                || !deadlineReconcileNextAt.isAfter(now));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max
                ? value
                : value.substring(0, max);
    }

    private static LocalDateTime requireNow(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("Attempt transition time is required.");
        }
        return now;
    }

    private void synchronizeScoreContract(BigDecimal compatibilityScore, BigDecimal possiblePoints) {
        if (compatibilityScore == null) {
            this.earnedPoints = null;
            this.scorePercentage = null;
            this.scoreUnit = null;
            return;
        }
        boolean percentageCompatibility = "WRITING".equals(skill) || "SPEAKING".equals(skill);
        if (percentageCompatibility) {
            this.scoreUnit = "PERCENTAGE";
            this.scorePercentage = clampPercentage(compatibilityScore);
            this.earnedPoints = possiblePoints == null || possiblePoints.signum() <= 0
                    ? null
                    : this.scorePercentage.multiply(possiblePoints)
                            .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        } else {
            this.scoreUnit = "EARNED_POINTS";
            BigDecimal nonNegative = compatibilityScore.max(BigDecimal.ZERO);
            this.earnedPoints = possiblePoints == null || possiblePoints.signum() <= 0
                    ? nonNegative
                    : nonNegative.min(possiblePoints);
            this.scorePercentage = possiblePoints == null || possiblePoints.signum() <= 0
                    ? BigDecimal.ZERO
                    : this.earnedPoints.multiply(BigDecimal.valueOf(100))
                            .divide(possiblePoints, 2, java.math.RoundingMode.HALF_UP)
                            .min(BigDecimal.valueOf(100));
        }
    }

    private static BigDecimal clampPercentage(BigDecimal value) {
        return value.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
    }

    public void discard(LocalDateTime discardedAt) {
        if (STATUS_DISCARDED.equals(status)) {
            return;
        }
        if (!STATUS_IN_PROGRESS.equals(status)) {
            throw new IllegalStateException("Only an in-progress attempt can be discarded.");
        }
        if (discardedAt == null) {
            throw new IllegalArgumentException("discardedAt is required.");
        }
        status = STATUS_DISCARDED;
        this.discardedAt = discardedAt;
        score = null;
        totalPoints = null;
        scoreUnit = null;
        earnedPoints = null;
        scorePercentage = null;
        answersJson = null;
        aiFeedbackJson = null;
        analysisStatus = ANALYSIS_NOT_REQUESTED;
        analysisRequestedAt = null;
        analysisCompletedAt = null;
        analysisEngine = null;
        analysisErrorCode = null;
        submittedAt = null;
    }

    private void requireNotDiscarded() {
        if (STATUS_DISCARDED.equals(status)) {
            throw new IllegalStateException("A discarded attempt cannot be changed.");
        }
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
    public Long getPublishedVersionId() { return publishedVersionId; }
    public Long getSetVersionId() { return setVersionId; }
    public Long getTestVersionId() { return testVersionId; }
    public Long getSectionVersionId() { return sectionVersionId; }
    public String getVersionCompatibilityStatus() { return versionCompatibilityStatus; }
    public String getVersionCompatibilityNote() { return versionCompatibilityNote; }
    public String getStatus() { return status; }
    public String getAnalysisStatus() { return analysisStatus; }
    public BigDecimal getScore() { return score; }
    public BigDecimal getTotalPoints() { return totalPoints; }
    public String getScoreUnit() { return scoreUnit; }
    public BigDecimal getEarnedPoints() { return earnedPoints; }
    public BigDecimal getScorePercentage() { return scorePercentage; }
    public String getAnswersJson() { return answersJson; }
    public String getAiFeedbackJson() { return aiFeedbackJson; }
    public LocalDateTime getAnalysisRequestedAt() { return analysisRequestedAt; }
    public LocalDateTime getAnalysisCompletedAt() { return analysisCompletedAt; }
    public String getAnalysisEngine() { return analysisEngine; }
    public String getAnalysisErrorCode() { return analysisErrorCode; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getDeadlineAt() { return deadlineAt; }
    public LocalDateTime getLastSavedAt() { return lastSavedAt; }
    public Integer getDeadlineReconcileAttempts() {
        return deadlineReconcileAttempts;
    }
    public LocalDateTime getDeadlineReconcileNextAt() {
        return deadlineReconcileNextAt;
    }
    public String getDeadlineReconcileErrorCode() {
        return deadlineReconcileErrorCode;
    }
    public LocalDateTime getDeadlineReconcileQuarantinedAt() {
        return deadlineReconcileQuarantinedAt;
    }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getDiscardedAt() { return discardedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setStatus(String status) {
        if (STATUS_DISCARDED.equals(status)) {
            throw new IllegalArgumentException("Use discard() for the discarded transition.");
        }
        requireNotDiscarded();
        this.status = status;
    }
    public void setLockVersion(Long lockVersion) { this.lockVersion = lockVersion; }
    public void setAnalysisStatus(String analysisStatus) { this.analysisStatus = analysisStatus; }
    public void setScore(BigDecimal score) {
        this.score = score;
        synchronizeScoreContract(score, totalPoints);
    }
    public void setTotalPoints(BigDecimal totalPoints) {
        this.totalPoints = totalPoints;
        synchronizeScoreContract(score, totalPoints);
    }
    public void setAnswersJson(String answersJson) { this.answersJson = answersJson; }
    public void setAiFeedbackJson(String aiFeedbackJson) { this.aiFeedbackJson = aiFeedbackJson; }
    public void setAnalysisRequestedAt(LocalDateTime analysisRequestedAt) { this.analysisRequestedAt = analysisRequestedAt; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
    public void setVersionCompatibilityStatus(String versionCompatibilityStatus) { this.versionCompatibilityStatus = versionCompatibilityStatus; }
    public void setVersionCompatibilityNote(String versionCompatibilityNote) { this.versionCompatibilityNote = versionCompatibilityNote; }
    public void setDeadlineAt(LocalDateTime deadlineAt) { this.deadlineAt = deadlineAt; }
}
