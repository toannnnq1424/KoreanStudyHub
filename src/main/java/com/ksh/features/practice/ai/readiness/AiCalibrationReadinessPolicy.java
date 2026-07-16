package com.ksh.features.practice.ai.readiness;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class AiCalibrationReadinessPolicy {

    public static final int MINIMUM_SPEAKING_FIXTURES = 5;
    public static final int MINIMUM_WRITING_FIXTURES = 5;

    public AiReadinessReport assessFixtureFramework(List<AiCalibrationFixture> fixtures) {
        List<AiCalibrationFixture> safeFixtures = fixtures == null ? List.of() : List.copyOf(fixtures);
        List<AiReadinessIssue> issues = new ArrayList<>();
        Map<AiCalibrationSkill, Integer> counts = counts(safeFixtures);

        requireMinimum(counts, AiCalibrationSkill.SPEAKING, MINIMUM_SPEAKING_FIXTURES, issues);
        requireMinimum(counts, AiCalibrationSkill.WRITING, MINIMUM_WRITING_FIXTURES, issues);

        for (AiCalibrationFixture fixture : safeFixtures) {
            validateRange(fixture, issues);
            if (!fixture.teacherReviewed()) {
                issues.add(AiReadinessIssue.warning(
                        "TEACHER_REVIEW_PENDING",
                        "Fixture " + fixture.fixtureId() + " chưa có teacher review thật; có thể giữ debt tới Phase 15.",
                        "Phase 15"));
            }
        }

        return new AiReadinessReport("ai-calibration-fixtures", issues);
    }

    private static Map<AiCalibrationSkill, Integer> counts(List<AiCalibrationFixture> fixtures) {
        Map<AiCalibrationSkill, Integer> counts = new EnumMap<>(AiCalibrationSkill.class);
        for (AiCalibrationSkill skill : AiCalibrationSkill.values()) {
            counts.put(skill, 0);
        }
        for (AiCalibrationFixture fixture : fixtures) {
            counts.computeIfPresent(fixture.skill(), (ignored, count) -> count + 1);
        }
        return counts;
    }

    private static void requireMinimum(
            Map<AiCalibrationSkill, Integer> counts,
            AiCalibrationSkill skill,
            int minimum,
            List<AiReadinessIssue> issues
    ) {
        int count = counts.getOrDefault(skill, 0);
        if (count < minimum) {
            issues.add(AiReadinessIssue.blocker(
                    "MINIMUM_" + skill.name() + "_FIXTURES_MISSING",
                    skill.name() + " cần tối thiểu " + minimum + " calibration fixtures cho 8F-B; hiện có " + count + ".",
                    "8F-B"));
        }
    }

    private static void validateRange(AiCalibrationFixture fixture, List<AiReadinessIssue> issues) {
        BigDecimal min = fixture.expectedMinPercentage();
        BigDecimal max = fixture.expectedMaxPercentage();
        if (min.compareTo(BigDecimal.ZERO) < 0 || max.compareTo(BigDecimal.valueOf(100)) > 0
                || min.compareTo(max) > 0) {
            issues.add(AiReadinessIssue.blocker(
                    "INVALID_EXPECTED_SCORE_RANGE",
                    "Fixture " + fixture.fixtureId() + " phải dùng expected score range trong khoảng 0..100.",
                    "8F-B"));
            return;
        }
        if (min.compareTo(max) == 0) {
            issues.add(AiReadinessIssue.blocker(
                    "EXACT_SCORE_NOT_ALLOWED",
                    "Fixture " + fixture.fixtureId() + " không được dùng exact score cứng; hãy dùng range hoặc qualitative band.",
                    "8F-B"));
        }
    }
}
