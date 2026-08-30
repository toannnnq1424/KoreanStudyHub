package com.ksh.entities;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LearningProgressEngagementTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 10, 12, 0);

    @Test
    void exactHeartbeatBoundaryAccruesButLateGapDoesNot() {
        LearningProgress progress = new LearningProgress(7L, 9L);

        progress.checkpointEngagement(LearningProgress.TAB_CONTENT, true,
                true, true, true, START);
        progress.checkpointEngagement(LearningProgress.TAB_CONTENT, true,
                true, true, true, START.plusSeconds(15));
        assertThat(progress.getContentEngagedSeconds()).isEqualTo(15);

        progress.checkpointEngagement(LearningProgress.TAB_CONTENT, true,
                true, true, true, START.plusSeconds(31));
        assertThat(progress.getContentEngagedSeconds()).isEqualTo(15);
    }

    @Test
    void duplicateCheckpointIsIdempotentAndPauseStopsHiddenTime() {
        LearningProgress progress = new LearningProgress(7L, 9L);
        progress.checkpointEngagement(LearningProgress.TAB_VIDEO, true,
                true, true, true, START);
        progress.checkpointEngagement(LearningProgress.TAB_VIDEO, true,
                true, true, true, START);
        assertThat(progress.getVideoEngagedSeconds()).isZero();

        progress.checkpointEngagement(LearningProgress.TAB_VIDEO, false,
                true, true, true, START.plusSeconds(5));
        assertThat(progress.getVideoEngagedSeconds()).isEqualTo(5);
        progress.checkpointEngagement(LearningProgress.TAB_VIDEO, true,
                true, true, true, START.plusSeconds(65));
        assertThat(progress.getVideoEngagedSeconds()).isEqualTo(5);
    }

    @Test
    void switchingTabsCreditsPreviousSelectionAndUnlocksManualCompletion() {
        LearningProgress progress = new LearningProgress(7L, 9L);
        LocalDateTime now = START;
        now = accrueMinute(progress, LearningProgress.TAB_CONTENT, now,
                true, true, true);
        now = accrueMinute(progress, LearningProgress.TAB_VIDEO, now,
                true, true, true);
        accrueMinute(progress, LearningProgress.TAB_ATTACHMENTS, now,
                true, true, true);

        assertThat(progress.getContentEngagedSeconds()).isEqualTo(60);
        assertThat(progress.getVideoEngagedSeconds()).isEqualTo(60);
        assertThat(progress.getAttachmentsEngagedSeconds()).isEqualTo(60);
        assertThat(progress.isChecklistComplete(true, true, true)).isTrue();
        assertThat(progress.isCompleted()).isFalse();
        assertThat(progress.getProgressPercent()).isEqualByComparingTo("100");

        assertThat(progress.reconcileChecklistCompletion(
                true, true, true, now.plusSeconds(1))).isTrue();
        assertThat(progress.isCompleted()).isTrue();
    }

    @Test
    void unavailableTabsAreSatisfiedWithoutFabricatingTheirSeconds() {
        LearningProgress progress = new LearningProgress(7L, 9L);
        accrueMinute(progress, LearningProgress.TAB_CONTENT, START,
                true, false, false);

        assertThat(progress.getContentEngagedSeconds()).isEqualTo(60);
        assertThat(progress.getVideoEngagedSeconds()).isZero();
        assertThat(progress.getAttachmentsEngagedSeconds()).isZero();
        assertThat(progress.isChecklistComplete(true, false, false)).isTrue();
        assertThat(progress.isCompleted()).isFalse();
        assertThat(progress.reconcileChecklistCompletion(
                true, false, false, START.plusSeconds(61))).isTrue();
    }

    @Test
    void stalePreviousTabDoesNotAccrueAfterItBecomesUnavailable() {
        LearningProgress progress = new LearningProgress(7L, 9L);
        progress.checkpointEngagement(LearningProgress.TAB_VIDEO, true,
                false, true, false, START);

        progress.checkpointEngagement(LearningProgress.TAB_CONTENT, true,
                true, false, false, START.plusSeconds(10));

        assertThat(progress.getVideoEngagedSeconds()).isZero();
        assertThat(progress.getActiveEngagementTab())
                .isEqualTo(LearningProgress.TAB_CONTENT);
    }

    @Test
    void absentVideoCannotBankTimeToInheritWhenAddedLater() {
        LearningProgress progress = new LearningProgress(7L, 9L);

        progress.checkpointEngagement(LearningProgress.TAB_VIDEO, true,
                true, false, false, START);
        progress.checkpointEngagement(LearningProgress.TAB_VIDEO, true,
                true, false, false, START.plusSeconds(10));
        assertThat(progress.getVideoEngagedSeconds()).isZero();
        assertThat(progress.getActiveEngagementTab()).isNull();

        // Adding VIDEO starts a fresh server timer; neither heartbeat sent
        // while it was absent becomes evidence.
        progress.checkpointEngagement(LearningProgress.TAB_VIDEO, true,
                true, true, false, START.plusSeconds(12));
        assertThat(progress.getVideoEngagedSeconds()).isZero();
        assertThat(progress.getActiveEngagementTab())
                .isEqualTo(LearningProgress.TAB_VIDEO);

        progress.checkpointEngagement(LearningProgress.TAB_VIDEO, true,
                true, true, false, START.plusSeconds(17));
        assertThat(progress.getVideoEngagedSeconds()).isEqualTo(5);
    }

    @Test
    void newlyApplicableTabInvalidatesEarlierCompletionUntilNewEvidenceExists() {
        LearningProgress progress = new LearningProgress(7L, 9L);
        LocalDateTime now = accrueMinute(progress, LearningProgress.TAB_CONTENT,
                START, true, false, false);
        assertThat(progress.reconcileChecklistCompletion(
                true, false, false, now.plusSeconds(1))).isTrue();

        progress.reconcileChecklistProgress(
                true, true, false, now.plusSeconds(2));

        assertThat(progress.isCompleted()).isFalse();
        assertThat(progress.getCompletedAt()).isNull();
        assertThat(progress.getProgressPercent()).isEqualByComparingTo("50");
    }

    @Test
    void invalidTabAndMissingTimestampFailClosed() {
        LearningProgress progress = new LearningProgress(7L, 9L);
        assertThatThrownBy(() -> progress.checkpointEngagement("SCORE", true,
                true, true, true, START))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> progress.checkpointEngagement(
                LearningProgress.TAB_CONTENT, true,
                true, true, true, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static LocalDateTime accrueMinute(LearningProgress progress,
                                              String tab,
                                              LocalDateTime start,
                                              boolean contentApplicable,
                                              boolean videoApplicable,
                                              boolean attachmentsApplicable) {
        progress.checkpointEngagement(tab, true,
                contentApplicable, videoApplicable, attachmentsApplicable, start);
        LocalDateTime now = start;
        for (int i = 0; i < 4; i++) {
            now = now.plusSeconds(15);
            progress.checkpointEngagement(tab, true,
                    contentApplicable, videoApplicable, attachmentsApplicable, now);
        }
        progress.checkpointEngagement(tab, false,
                contentApplicable, videoApplicable, attachmentsApplicable, now);
        return now;
    }
}
