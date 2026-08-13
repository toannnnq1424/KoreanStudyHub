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
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * JPA entity mapping the {@code learning_progress} table (KSH-4.5).
 *
 * <p>Tracks one student's progress through a single lesson. The unique key
 * {@code idx_lp_user_lesson} guarantees at most one row per (user, lesson);
 * the service relies on that constraint for its idempotent upsert.
 *
 * <p>Status transitions: a freshly opened lesson creates the row as
 * {@link #STATUS_IN_PROGRESS}; server-timed checkpoints unlock the explicit
 * completion command, which advances it once to {@link #STATUS_COMPLETED}.
 * {@code NOT_STARTED} exists in the DB
 * CHECK for completeness but is never written here — a missing row already
 * means "not started".
 */
@Entity
@Table(name = "learning_progress")
public class LearningProgress {

    public static final String STATUS_NOT_STARTED = "NOT_STARTED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";

    public static final String TAB_CONTENT = "CONTENT";
    public static final String TAB_VIDEO = "VIDEO";
    public static final String TAB_ATTACHMENTS = "ATTACHMENTS";
    public static final Set<String> ENGAGEMENT_TABS = Set.of(
            TAB_CONTENT, TAB_VIDEO, TAB_ATTACHMENTS);
    public static final int REQUIRED_SECONDS_PER_TAB = 60;
    /** Heartbeats arriving later than this are treated as an idle/hidden gap. */
    public static final int MAX_HEARTBEAT_GAP_SECONDS = 15;

    private static final BigDecimal PERCENT_NONE = BigDecimal.ZERO;
    private static final BigDecimal PERCENT_FULL = BigDecimal.valueOf(100);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPercent;

    @Column(name = "content_engaged_seconds", nullable = false)
    private int contentEngagedSeconds;

    @Column(name = "video_engaged_seconds", nullable = false)
    private int videoEngagedSeconds;

    @Column(name = "attachments_engaged_seconds", nullable = false)
    private int attachmentsEngagedSeconds;

    @Column(name = "active_engagement_tab", length = 20)
    private String activeEngagementTab;

    @Column(name = "active_engagement_checkpoint_at")
    private LocalDateTime activeEngagementCheckpointAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** JPA-only constructor; do not call from application code. */
    protected LearningProgress() {
    }

    /**
     * Creates a row for a lesson the student has just opened: status
     * {@link #STATUS_IN_PROGRESS}, {@code started_at} = now, percent 0.
     *
     * @param userId   viewing student's id
     * @param lessonId opened lesson's id
     */
    public LearningProgress(Long userId, Long lessonId) {
        this.userId = userId;
        this.lessonId = lessonId;
        this.status = STATUS_IN_PROGRESS;
        this.progressPercent = PERCENT_NONE;
        this.startedAt = LocalDateTime.now();
    }

    // ── Lifecycle hooks ────────────────────────────────────────────────

    @PrePersist
    void onPersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Business helpers ───────────────────────────────────────────────

    /** True when this lesson is marked COMPLETED for the student. */
    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }

    /**
     * Marks the lesson completed: status {@link #STATUS_COMPLETED},
     * {@code completed_at} = now, percent 100. Backfills {@code started_at}
     * when the row was created directly as COMPLETED by trusted migration or
     * test setup.
     */
    public void markCompleted() {
        this.status = STATUS_COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.progressPercent = PERCENT_FULL;
        if (this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }
    }

    /**
     * Reverts a completed lesson back to {@link #STATUS_IN_PROGRESS}:
     * clears {@code completed_at} and resets percent to 0.
     */
    public void revertToInProgress() {
        this.status = STATUS_IN_PROGRESS;
        this.completedAt = null;
        this.progressPercent = PERCENT_NONE;
    }

    /**
     * Records a server-timed visibility heartbeat for one lesson tab.
     *
     * <p>The caller never supplies elapsed time. Time is accrued to the tab
     * that was active at the preceding checkpoint, and only when the server
     * clock gap is positive and at most {@link #MAX_HEARTBEAT_GAP_SECONDS}.
     * A late heartbeat therefore cannot turn an idle/background tab into
     * progress. Each tab is capped at the required 60 seconds.
     *
     * @param selectedTab currently selected tab (CONTENT / VIDEO / ATTACHMENTS)
     * @param active      true while the document is visible; false pauses timing
     * @param now         server timestamp, explicit to keep boundary logic testable
     */
    public void checkpointEngagement(String selectedTab,
                                     boolean active,
                                     boolean contentApplicable,
                                     boolean videoApplicable,
                                     boolean attachmentsApplicable,
                                     LocalDateTime now) {
        requireEngagementTab(selectedTab);
        if (now == null) {
            throw new IllegalArgumentException("Checkpoint time is required");
        }

        // Applicability is resolved from the lesson inside the same locked
        // service transaction as this checkpoint. Never credit a timer whose
        // tab disappeared since the previous heartbeat: otherwise that hidden
        // time would become banked evidence if the tab is added again later.
        if (isTabApplicable(activeEngagementTab, contentApplicable,
                videoApplicable, attachmentsApplicable)) {
            accruePreviousActiveTab(now);
        } else {
            pauseEngagement();
        }

        // A stale client may still send heartbeats for a tab removed from the
        // lesson. Ignore it and keep the timer paused instead of creating an
        // active checkpoint that could accrue after the tab is restored.
        if (active && isTabApplicable(selectedTab, contentApplicable,
                videoApplicable, attachmentsApplicable)) {
            this.activeEngagementTab = selectedTab;
            this.activeEngagementCheckpointAt = now;
        } else {
            pauseEngagement();
        }
        reconcileChecklistProgress(contentApplicable, videoApplicable,
                attachmentsApplicable, now);
    }

    /** Clears a stale timer when the detail view is reopened after an idle gap. */
    public void pauseEngagement() {
        this.activeEngagementTab = null;
        this.activeEngagementCheckpointAt = null;
    }

    /** True only after every applicable server-timed checklist item reaches 60 seconds. */
    public boolean isChecklistComplete(boolean contentApplicable,
                                       boolean videoApplicable,
                                       boolean attachmentsApplicable) {
        return (!contentApplicable || contentEngagedSeconds >= REQUIRED_SECONDS_PER_TAB)
                && (!videoApplicable || videoEngagedSeconds >= REQUIRED_SECONDS_PER_TAB)
                && (!attachmentsApplicable
                    || attachmentsEngagedSeconds >= REQUIRED_SECONDS_PER_TAB);
    }

    /** Reconciles legacy completion requests without allowing a bypass. */
    public boolean reconcileChecklistCompletion(boolean contentApplicable,
                                                 boolean videoApplicable,
                                                 boolean attachmentsApplicable,
                                                 LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("Checkpoint time is required");
        }
        reconcileChecklistProgress(contentApplicable, videoApplicable,
                attachmentsApplicable, now);
        if (!isCompleted() && isChecklistComplete(contentApplicable,
                videoApplicable, attachmentsApplicable)) {
            this.status = STATUS_COMPLETED;
            this.completedAt = now;
            this.progressPercent = PERCENT_FULL;
        }
        return isCompleted();
    }

    /**
     * Recalculates checklist progress without granting completion. Heartbeats
     * may unlock the explicit completion action, but only the guarded
     * completion command may transition the lesson to COMPLETED.
     */
    public void reconcileChecklistProgress(boolean contentApplicable,
                                            boolean videoApplicable,
                                            boolean attachmentsApplicable,
                                            LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("Checkpoint time is required");
        }
        recalculateChecklistProgress(contentApplicable, videoApplicable,
                attachmentsApplicable, now);
    }

    private void accruePreviousActiveTab(LocalDateTime now) {
        if (activeEngagementTab == null || activeEngagementCheckpointAt == null) {
            return;
        }
        long elapsed = ChronoUnit.SECONDS.between(activeEngagementCheckpointAt, now);
        if (elapsed <= 0 || elapsed > MAX_HEARTBEAT_GAP_SECONDS) {
            return;
        }
        int seconds = Math.toIntExact(elapsed);
        switch (activeEngagementTab) {
            case TAB_CONTENT -> contentEngagedSeconds = capped(contentEngagedSeconds, seconds);
            case TAB_VIDEO -> videoEngagedSeconds = capped(videoEngagedSeconds, seconds);
            case TAB_ATTACHMENTS -> attachmentsEngagedSeconds = capped(attachmentsEngagedSeconds, seconds);
            default -> {
                // A future/invalid database value accrues nothing and is replaced below.
            }
        }
    }

    private static int capped(int current, int increment) {
        return Math.min(REQUIRED_SECONDS_PER_TAB, current + increment);
    }

    private static boolean isTabApplicable(String tab,
                                           boolean contentApplicable,
                                           boolean videoApplicable,
                                           boolean attachmentsApplicable) {
        if (tab == null) {
            return false;
        }
        return switch (tab) {
            case TAB_CONTENT -> contentApplicable;
            case TAB_VIDEO -> videoApplicable;
            case TAB_ATTACHMENTS -> attachmentsApplicable;
            default -> false;
        };
    }

    private void recalculateChecklistProgress(boolean contentApplicable,
                                              boolean videoApplicable,
                                              boolean attachmentsApplicable,
                                              LocalDateTime now) {
        int applicableCount = 0;
        int totalSeconds = 0;
        if (contentApplicable) {
            applicableCount++;
            totalSeconds += contentEngagedSeconds;
        }
        if (videoApplicable) {
            applicableCount++;
            totalSeconds += videoEngagedSeconds;
        }
        if (attachmentsApplicable) {
            applicableCount++;
            totalSeconds += attachmentsEngagedSeconds;
        }
        this.progressPercent = applicableCount == 0
                ? PERCENT_FULL
                : BigDecimal.valueOf(totalSeconds)
                        .multiply(PERCENT_FULL)
                        .divide(BigDecimal.valueOf(REQUIRED_SECONDS_PER_TAB
                                        * (long) applicableCount), 2,
                                java.math.RoundingMode.HALF_UP);
        // Completion belongs to the lesson shape that was actually reviewed.
        // If a lecturer later adds another applicable tab, the earlier
        // completion is no longer valid until the new evidence is collected.
        if (isCompleted() && isChecklistComplete(contentApplicable,
                videoApplicable, attachmentsApplicable)) {
            this.progressPercent = PERCENT_FULL;
        } else {
            this.status = STATUS_IN_PROGRESS;
            this.completedAt = null;
        }
        if (this.startedAt == null) {
            this.startedAt = now;
        }
    }

    private static void requireEngagementTab(String tab) {
        if (!ENGAGEMENT_TABS.contains(tab)) {
            throw new IllegalArgumentException("Unsupported lesson engagement tab");
        }
    }

    // ── Getters ────────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getLessonId() {
        return lessonId;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getProgressPercent() {
        return progressPercent;
    }

    public int getContentEngagedSeconds() {
        return contentEngagedSeconds;
    }

    public int getVideoEngagedSeconds() {
        return videoEngagedSeconds;
    }

    public int getAttachmentsEngagedSeconds() {
        return attachmentsEngagedSeconds;
    }

    public String getActiveEngagementTab() {
        return activeEngagementTab;
    }

    public LocalDateTime getActiveEngagementCheckpointAt() {
        return activeEngagementCheckpointAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
