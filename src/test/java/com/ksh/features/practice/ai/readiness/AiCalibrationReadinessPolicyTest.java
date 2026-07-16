package com.ksh.features.practice.ai.readiness;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiCalibrationReadinessPolicyTest {

    private final AiCalibrationReadinessPolicy policy = new AiCalibrationReadinessPolicy();

    @Test
    void requiresMinimumFiveSpeakingAndFiveWritingFixtures() {
        AiReadinessReport report = policy.assessFixtureFramework(List.of());

        assertThat(report.blockers())
                .extracting(AiReadinessIssue::code)
                .contains("MINIMUM_SPEAKING_FIXTURES_MISSING",
                        "MINIMUM_WRITING_FIXTURES_MISSING");
    }

    @Test
    void acceptsMvpFixtureFrameworkWithRangesAndTeacherReviewDebtWarnings() {
        AiReadinessReport report = policy.assessFixtureFramework(mvpFixtures(false));

        assertThat(report.blockers()).isEmpty();
        assertThat(report.warnings())
                .extracting(AiReadinessIssue::code)
                .containsOnly("TEACHER_REVIEW_PENDING");
        assertThat(report.rolloutAllowed()).isTrue();
    }

    @Test
    void exactScoreIsRejectedBecauseAiOutputIsNotDeterministicEnough() {
        List<AiCalibrationFixture> fixtures = new ArrayList<>(mvpFixtures(true));
        fixtures.set(0, fixture("speaking-exact", AiCalibrationSkill.SPEAKING, "SPEAKING_GENERAL",
                "70", "70", true));

        AiReadinessReport report = policy.assessFixtureFramework(fixtures);

        assertThat(report.blockers())
                .extracting(AiReadinessIssue::code)
                .contains("EXACT_SCORE_NOT_ALLOWED");
    }

    @Test
    void invalidRangeIsRejected() {
        List<AiCalibrationFixture> fixtures = new ArrayList<>(mvpFixtures(true));
        fixtures.set(1, fixture("writing-invalid", AiCalibrationSkill.WRITING, "Q54",
                "95", "105", true));

        AiReadinessReport report = policy.assessFixtureFramework(fixtures);

        assertThat(report.blockers())
                .extracting(AiReadinessIssue::code)
                .contains("INVALID_EXPECTED_SCORE_RANGE");
    }

    private static List<AiCalibrationFixture> mvpFixtures(boolean teacherReviewed) {
        return List.of(
                fixture("speaking-1", AiCalibrationSkill.SPEAKING, "SPEAKING_GENERAL", "65", "80", teacherReviewed),
                fixture("speaking-2", AiCalibrationSkill.SPEAKING, "SPEAKING_LOW_CONFIDENCE", "35", "55", teacherReviewed),
                fixture("speaking-3", AiCalibrationSkill.SPEAKING, "SPEAKING_TEXT_FALLBACK", "40", "65", teacherReviewed),
                fixture("speaking-4", AiCalibrationSkill.SPEAKING, "SPEAKING_OFF_TOPIC", "0", "20", teacherReviewed),
                fixture("speaking-5", AiCalibrationSkill.SPEAKING, "SPEAKING_STRONG", "80", "95", teacherReviewed),
                fixture("writing-1", AiCalibrationSkill.WRITING, "Q51", "55", "75", teacherReviewed),
                fixture("writing-2", AiCalibrationSkill.WRITING, "Q52", "55", "75", teacherReviewed),
                fixture("writing-3", AiCalibrationSkill.WRITING, "Q53", "60", "80", teacherReviewed),
                fixture("writing-4", AiCalibrationSkill.WRITING, "Q54", "65", "85", teacherReviewed),
                fixture("writing-5", AiCalibrationSkill.WRITING, "WRITING_OFF_TOPIC", "0", "20", teacherReviewed)
        );
    }

    private static AiCalibrationFixture fixture(
            String fixtureId,
            AiCalibrationSkill skill,
            String taskType,
            String min,
            String max,
            boolean teacherReviewed
    ) {
        return new AiCalibrationFixture(
                fixtureId,
                skill,
                taskType,
                "prompt-v1",
                "rubric-v1",
                "schema-v1",
                "model-under-test",
                new BigDecimal(min),
                new BigDecimal(max),
                "qualitative-band",
                teacherReviewed);
    }
}
