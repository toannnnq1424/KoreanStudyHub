package com.ksh.features.practice.ai.readiness;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiRolloutReadinessChecklistTest {

    private final AiRolloutReadinessChecklist checklist = new AiRolloutReadinessChecklist();

    @Test
    void rolloutIsBlockedUntilManualUat8GAnd8HAreResolved() {
        AiReadinessReport report = checklist.assessLiveSpeakingRollout(
                List.of(new AiReadinessReport("clean-prerequisite", List.of())),
                false,
                false,
                false);

        assertThat(report.rolloutAllowed()).isFalse();
        assertThat(report.blockers())
                .extracting(AiReadinessIssue::code)
                .contains("MANUAL_UAT_PENDING", "PHASE_8G_NOT_CLOSED", "PHASE_8H_NOT_CLOSED");
    }

    @Test
    void prerequisiteBlockersRemainVisibleInFinalChecklist() {
        AiReadinessReport provider = new AiReadinessReport("provider", List.of(
                AiReadinessIssue.blocker("MISSING_EVALUATOR_API_KEY", "Thiếu key.", "8F-A")));

        AiReadinessReport report = checklist.assessLiveSpeakingRollout(
                List.of(provider),
                true,
                true,
                true);

        assertThat(report.blockers())
                .extracting(AiReadinessIssue::code)
                .containsExactly("MISSING_EVALUATOR_API_KEY");
    }

    @Test
    void cleanPrerequisitesAndResolvedLaterPhasesAllowExplicitApprovalGate() {
        AiReadinessReport report = checklist.assessLiveSpeakingRollout(
                List.of(
                        new AiReadinessReport("provider", List.of()),
                        new AiReadinessReport("calibration", List.of(AiReadinessIssue.warning(
                                "TEACHER_REVIEW_PENDING", "Debt tới Phase 15.", "Phase 15")))),
                true,
                true,
                true);

        assertThat(report.blockers()).isEmpty();
        assertThat(report.rolloutAllowed()).isTrue();
        assertThat(report.issues())
                .extracting(AiReadinessIssue::code)
                .contains("LIVE_SPEAKING_AI_READY_FOR_EXPLICIT_USER_APPROVAL");
    }

    @Test
    void checklistDoesNotCarryProviderSecretsOrLearnerContent() {
        AiReadinessReport report = checklist.assessLiveSpeakingRollout(List.of(), true, true, true);

        assertThat(report.toString())
                .doesNotContain("Bearer", "SECRET", "learner answer", "transcript", "storage/key");
    }
}
