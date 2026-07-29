package com.ksh.features.practice.service;

import com.ksh.entities.PracticeAttempt;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeAttemptStatePolicyTest {

    private final PracticeAttemptStatePolicy policy =
            new PracticeAttemptStatePolicy();

    @Test
    void lifecycleAndAnalysisMatrixKeepsOneCanonicalInterpretation() {
        PracticeAttempt canonical = attempt(1L, "READING", true);
        PracticeAttempt incomplete = attempt(2L, "READING", false);
        PracticeAttempt incompatible = attempt(3L, "READING", true);
        incompatible.setVersionCompatibilityStatus("STALE");
        PracticeAttempt submitted = attempt(4L, "READING", true);
        submitted.markSubmitted(BigDecimal.ONE, BigDecimal.TEN, "{}");
        PracticeAttempt queued = attempt(5L, "WRITING", true);
        queued.markSubmitted(null, BigDecimal.TEN, "{}");
        queued.setAnalysisStatus(PracticeAttempt.ANALYSIS_QUEUED);
        PracticeAttempt processing = attempt(6L, "WRITING", true);
        processing.markSubmitted(null, BigDecimal.TEN, "{}");
        processing.setAnalysisStatus(PracticeAttempt.ANALYSIS_PROCESSING);
        PracticeAttempt succeeded = attempt(7L, "WRITING", true);
        succeeded.markSubmitted(null, BigDecimal.TEN, "{}");
        succeeded.setAnalysisStatus(PracticeAttempt.ANALYSIS_SUCCEEDED);
        PracticeAttempt objectiveFailed = attempt(8L, "LISTENING", true);
        objectiveFailed.markSubmitted(BigDecimal.ONE, BigDecimal.TEN, "{}");
        objectiveFailed.markAnalysisFailed("PROVIDER");
        PracticeAttempt subjectiveFailed = attempt(9L, "WRITING", true);
        subjectiveFailed.markSubmitted(null, BigDecimal.TEN, "{}");
        subjectiveFailed.markAnalysisFailed("PROVIDER");
        PracticeAttempt graded = attempt(10L, "WRITING", true);
        graded.markGraded(BigDecimal.TEN, BigDecimal.TEN, "{}", "{}");
        PracticeAttempt discarded = attempt(11L, "READING", true);
        discarded.discard(LocalDateTime.parse("2026-07-25T12:00:00"));
        PracticeAttempt historical = attempt(12L, "WRITING", false);
        historical.markSubmitted(null, BigDecimal.TEN, "{}");
        PracticeAttempt unavailable = attempt(13L, "READING", true);
        unavailable.setStatus("UNKNOWN");

        assertThat(policy.presentation(canonical, true).state())
                .isEqualTo(PracticeAttemptStatePolicy.DisplayState.IN_PROGRESS);
        assertThat(policy.presentation(canonical, true).resumeAttemptId()).isEqualTo(1L);
        assertThat(policy.presentation(incomplete, true).state())
                .isEqualTo(PracticeAttemptStatePolicy.DisplayState.STALE);
        assertThat(policy.presentation(incompatible, true).state())
                .isEqualTo(PracticeAttemptStatePolicy.DisplayState.STALE);
        assertThat(policy.presentation(submitted, true).state())
                .isEqualTo(PracticeAttemptStatePolicy.DisplayState.SUBMITTED);
        assertThat(policy.presentation(queued, true).state())
                .isEqualTo(PracticeAttemptStatePolicy.DisplayState.SCORING);
        assertThat(policy.presentation(processing, true).state())
                .isEqualTo(PracticeAttemptStatePolicy.DisplayState.SCORING);
        assertThat(policy.presentation(succeeded, true).state())
                .isEqualTo(PracticeAttemptStatePolicy.DisplayState.SCORED);
        assertThat(policy.presentation(objectiveFailed, true).state())
                .isEqualTo(PracticeAttemptStatePolicy.DisplayState.PARTIAL);
        assertThat(policy.presentation(subjectiveFailed, true).state())
                .isEqualTo(PracticeAttemptStatePolicy.DisplayState.FAILED);
        assertThat(policy.presentation(graded, true).state())
                .isEqualTo(PracticeAttemptStatePolicy.DisplayState.SCORED);
        assertThat(policy.presentation(discarded, true).state())
                .isEqualTo(PracticeAttemptStatePolicy.DisplayState.DISCARDED);
        assertThat(policy.presentation(unavailable, true).state())
                .isEqualTo(PracticeAttemptStatePolicy.DisplayState.UNAVAILABLE);

        assertThat(policy.isCanonicalResumable(canonical, true)).isTrue();
        assertThat(policy.isCanonicalResumable(canonical, false)).isFalse();
        assertThat(policy.isCanonicalResumable(incomplete, true)).isFalse();
        assertThat(policy.isCanonicalResumable(incompatible, true)).isFalse();
        assertThat(policy.isCompleted(submitted)).isTrue();
        assertThat(policy.isCompleted(graded)).isTrue();
        assertThat(policy.isActive(discarded)).isFalse();
        assertThat(policy.isUnavailable(unavailable)).isTrue();
        assertThat(policy.resultEligibility(incomplete, false))
                .isEqualTo(
                        PracticeAttemptStatePolicy.ResultEligibility.NOT_TERMINAL);
        assertThat(policy.resultEligibility(submitted, true))
                .isEqualTo(
                        PracticeAttemptStatePolicy.ResultEligibility
                                .ELIGIBLE_CANONICAL);
        assertThat(policy.resultEligibility(submitted, false))
                .isEqualTo(
                        PracticeAttemptStatePolicy.ResultEligibility
                                .INCONSISTENT_VERSION_IDENTITY);
        assertThat(policy.resultEligibility(historical, true))
                .isEqualTo(
                        PracticeAttemptStatePolicy.ResultEligibility
                                .INCOMPLETE_VERSION_LOCK);
        assertThat(policy.isResultEligible(historical, true)).isFalse();
    }

    @Test
    void playerAndResultAccessRequireCompatibleCoherentImmutableIdentity() {
        PracticeAttempt canonical = attempt(30L, "READING", true);
        assertThat(policy.resumeEligibility(canonical, true).eligible())
                .isTrue();

        PracticeAttempt incomplete = attempt(31L, "READING", false);
        assertThat(policy.resumeEligibility(incomplete, true).rejection())
                .isEqualTo(
                        PracticeAttemptStatePolicy.ResumeRejection
                                .INCOMPLETE_VERSION_LOCK);

        PracticeAttempt incompatible = attempt(32L, "READING", true);
        incompatible.setVersionCompatibilityStatus("STALE");
        assertThat(policy.resumeEligibility(incompatible, true).rejection())
                .isEqualTo(
                        PracticeAttemptStatePolicy.ResumeRejection
                                .INCOMPATIBLE_VERSION);

        PracticeAttempt expired = attempt(33L, "READING", true);
        expired.setDeadlineAt(LocalDateTime.now().minusSeconds(1));
        assertThat(policy.resumeEligibility(expired, true).rejection())
                .isEqualTo(
                        PracticeAttemptStatePolicy.ResumeRejection
                                .DEADLINE_EXPIRED);

        assertThat(policy.resumeEligibility(canonical, false).rejection())
                .isEqualTo(
                        PracticeAttemptStatePolicy.ResumeRejection
                                .INCONSISTENT_VERSION_IDENTITY);
        assertThatThrownBy(() ->
                policy.resumeEligibility(incomplete, true).requireEligible())
                .isInstanceOf(
                        PracticeAttemptStatePolicy
                                .PracticeAttemptResumeNotAllowedException.class)
                .hasMessageContaining("bắt đầu lượt mới");

        incomplete.markSubmitted(BigDecimal.ONE, BigDecimal.TEN, "{}");
        incompatible.markSubmitted(BigDecimal.ONE, BigDecimal.TEN, "{}");
        assertThat(policy.resultEligibility(incomplete, true))
                .isEqualTo(
                        PracticeAttemptStatePolicy.ResultEligibility
                                .INCOMPLETE_VERSION_LOCK);
        assertThat(policy.resultEligibility(incompatible, true))
                .isEqualTo(
                        PracticeAttemptStatePolicy.ResultEligibility
                                .INCOMPATIBLE_VERSION);
        assertThatThrownBy(() ->
                policy.requireResultEligible(canonical, true))
                .isInstanceOf(
                        PracticeAttemptStatePolicy
                                .PracticeResultNotAvailableException.class)
                .hasMessageContaining("sau khi bài làm đã được nộp");
    }

    @Test
    void activityOrderUsesSubmittedThenUpdatedThenCreatedAndIdTieBreak() {
        LocalDateTime common =
                LocalDateTime.parse("2026-07-25T12:00:00");
        PracticeAttempt lowerId = attempt(10L, "READING", true);
        PracticeAttempt higherId = attempt(11L, "READING", true);
        setTime(lowerId, "createdAt", common.minusHours(3));
        setTime(lowerId, "updatedAt", common);
        setTime(higherId, "createdAt", common.minusHours(4));
        setTime(higherId, "updatedAt", common);

        assertThat(policy.newest(List.of(lowerId, higherId)).getId())
                .isEqualTo(11L);

        PracticeAttempt submittedWins = attempt(9L, "READING", true);
        setTime(submittedWins, "createdAt", common.minusDays(2));
        setTime(submittedWins, "updatedAt", common.minusDays(1));
        setTime(submittedWins, "submittedAt", common.plusMinutes(1));
        assertThat(policy.newest(List.of(higherId, submittedWins)).getId())
                .isEqualTo(9L);
    }

    @Test
    void reEvaluationEligibilityIsTerminalVersionedAndActionSpecific() {
        PracticeAttempt writing = attempt(20L, "WRITING", true);
        writing.markGraded(BigDecimal.TEN, BigDecimal.TEN, "{}", "{}");
        assertThat(policy.reEvaluationEligibility(
                        writing,
                        PracticeAttemptStatePolicy.ReEvaluationAction
                                .FULL_ATTEMPT).eligible())
                .isTrue();
        assertThat(policy.reEvaluationEligibility(
                        writing,
                        PracticeAttemptStatePolicy.ReEvaluationAction
                                .SINGLE_WRITING_QUESTION).eligible())
                .isTrue();

        PracticeAttempt inProgress = attempt(21L, "WRITING", true);
        assertThat(policy.reEvaluationEligibility(
                        inProgress,
                        PracticeAttemptStatePolicy.ReEvaluationAction
                                .FULL_ATTEMPT).rejection())
                .isEqualTo(
                        PracticeAttemptStatePolicy.ReEvaluationRejection
                                .NOT_TERMINAL);

        PracticeAttempt speaking = attempt(22L, "SPEAKING", true);
        speaking.markGraded(null, BigDecimal.TEN, "{}", "{}");
        assertThat(policy.reEvaluationEligibility(
                        speaking,
                        PracticeAttemptStatePolicy.ReEvaluationAction
                                .FULL_ATTEMPT).rejection())
                .isEqualTo(
                        PracticeAttemptStatePolicy.ReEvaluationRejection
                                .UNSUPPORTED_ACTION);

        assertThatThrownBy(() ->
                policy.requireCoherentReEvaluationIdentity(false))
                .isInstanceOf(
                        PracticeAttemptStatePolicy
                                .PracticeReEvaluationNotAllowedException.class)
                .hasMessageContaining("không nhất quán");
    }

    private PracticeAttempt attempt(Long id, String skill, boolean lock) {
        PracticeAttempt attempt =
                new PracticeAttempt(7L, 1L, 2L, skill, 3L);
        if (lock) {
            attempt.lockPublishedVersion(101L, 102L, 103L, 104L);
        }
        ReflectionTestUtils.setField(attempt, "id", id);
        return attempt;
    }

    private void setTime(
            PracticeAttempt attempt,
            String field,
            LocalDateTime value
    ) {
        ReflectionTestUtils.setField(attempt, field, value);
    }
}
