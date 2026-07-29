package com.ksh.features.practice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.PracticeSectionVersion;
import com.ksh.entities.PracticeSetVersion;
import com.ksh.entities.PracticeTestVersion;
import com.ksh.entities.WritingTaskType;
import com.ksh.features.practice.ai.writing.WritingEvaluationResult;
import com.ksh.features.practice.ai.writing.WritingAssessmentPolicyBundle;
import com.ksh.features.practice.ai.writing.WritingFeedbackCompatibilityReader;
import com.ksh.features.practice.dto.PracticeDtos;
import com.ksh.features.practice.dto.PracticeDtos.HeatmapCell;
import com.ksh.features.practice.dto.PracticeDtos.LearningProgressOverview;
import com.ksh.features.practice.dto.PracticeDtos.PracticeAnalytics;
import com.ksh.features.practice.dto.PracticeDtos.PracticeProgressPageData;
import com.ksh.features.practice.dto.PracticeDtos.PracticeResultSummary;
import com.ksh.features.practice.dto.PracticeDtos.ProgressAttemptCounts;
import com.ksh.features.practice.dto.PracticeDtos.ProgressAvailability;
import com.ksh.features.practice.dto.PracticeDtos.ProgressCoverage;
import com.ksh.features.practice.dto.PracticeDtos.ProgressExclusion;
import com.ksh.features.practice.dto.PracticeDtos.ProgressExclusionReason;
import com.ksh.features.practice.dto.PracticeDtos.ProgressLevelFact;
import com.ksh.features.practice.dto.PracticeDtos.ProgressNumericFact;
import com.ksh.features.practice.dto.PracticeDtos.ProgressObservationWindow;
import com.ksh.features.practice.dto.PracticeDtos.ProgressPageState;
import com.ksh.features.practice.dto.PracticeDtos.ScoreTrendPoint;
import com.ksh.features.practice.dto.PracticeDtos.SkillMetric;
import com.ksh.features.practice.dto.PracticeDtos.WritingTaskProgressSeam;
import com.ksh.features.practice.dto.PracticeDtos.WritingTaskScoreCohort;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticePublishedVersionRepository;
import com.ksh.features.practice.repository.PracticeQuestionVersionRepository;
import com.ksh.features.practice.repository.PracticeSectionVersionRepository;
import com.ksh.features.practice.repository.PracticeSetVersionRepository;
import com.ksh.features.practice.repository.PracticeTestVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The single read-only owner of learner progress page data.
 *
 * <p>All-time counts and Objective facts come from repository projections.
 * Writing task cohorts use the matching immutable question-version evidence.
 * Recent history and chart detail use a separately labelled bounded window
 * and never define an all-time total.</p>
 */
@Service
@Transactional(readOnly = true)
public class PracticeProgressService {

    static final int RECENT_DETAIL_LIMIT = 100;
    static final int WRITING_DETAIL_LIMIT = 500;
    private static final int RECENT_HISTORY_LIMIT = 30;
    private static final int OVERVIEW_HISTORY_LIMIT = 8;
    private static final int HEATMAP_DAYS = 84;
    private static final String OBJECTIVE_PROFILE = "OBJECTIVE_EARNED_OVER_POSSIBLE_V1";
    private static final String DURATION_PROFILE = "ELAPSED_WALL_CLOCK_1_TO_239_MINUTES_V1";
    private static final String WRITING_TASK_NATIVE_CONTRACT = "TASK_NATIVE_RUBRIC_V1";
    private static final String WRITING_EVALUATION_ENGINE =
            "KSH_WRITING_EVALUATOR_V2";
    private static final String WRITING_TASK_COHORT_PROFILE =
            "WRITING_TASK_COHORTS_ONLY_V1";
    private static final List<String> SKILLS =
            List.of("READING", "LISTENING", "WRITING", "SPEAKING");
    private static final PracticeAttemptStatePolicy ATTEMPT_STATE =
            PracticeAttemptStatePolicy.INSTANCE;

    private final PracticeAttemptRepository attemptRepository;
    private final PracticePublishedVersionRepository publishedVersionRepository;
    private final PracticeSetVersionRepository setVersionRepository;
    private final PracticeTestVersionRepository testVersionRepository;
    private final PracticeSectionVersionRepository sectionVersionRepository;
    private final PracticeQuestionVersionRepository questionVersionRepository;
    private final WritingFeedbackCompatibilityReader writingFeedbackReader;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public PracticeProgressService(
            PracticeAttemptRepository attemptRepository,
            PracticePublishedVersionRepository publishedVersionRepository,
            PracticeSetVersionRepository setVersionRepository,
            PracticeTestVersionRepository testVersionRepository,
            PracticeSectionVersionRepository sectionVersionRepository,
            PracticeQuestionVersionRepository questionVersionRepository,
            WritingFeedbackCompatibilityReader writingFeedbackReader,
            ObjectMapper objectMapper
    ) {
        this(
                attemptRepository,
                publishedVersionRepository,
                setVersionRepository,
                testVersionRepository,
                sectionVersionRepository,
                questionVersionRepository,
                writingFeedbackReader,
                objectMapper,
                Clock.systemDefaultZone());
    }

    PracticeProgressService(
            PracticeAttemptRepository attemptRepository,
            PracticePublishedVersionRepository publishedVersionRepository,
            PracticeSetVersionRepository setVersionRepository,
            PracticeTestVersionRepository testVersionRepository,
            PracticeSectionVersionRepository sectionVersionRepository,
            PracticeQuestionVersionRepository questionVersionRepository,
            WritingFeedbackCompatibilityReader writingFeedbackReader,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.attemptRepository = attemptRepository;
        this.publishedVersionRepository = publishedVersionRepository;
        this.setVersionRepository = setVersionRepository;
        this.testVersionRepository = testVersionRepository;
        this.sectionVersionRepository = sectionVersionRepository;
        this.questionVersionRepository = questionVersionRepository;
        this.writingFeedbackReader = writingFeedbackReader;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public PracticeProgressPageData getProgressPageData(
            Long userId,
            String displayName,
            String avatarUrl
    ) {
        LocalDateTime asOf = LocalDateTime.now(clock);
        PracticeAttemptRepository.ProgressAllTimeProjection allTime =
                attemptRepository.findProgressAllTime(
                        userId, PracticeAttempt.STATUS_DISCARDED);
        Map<String, PracticeAttemptRepository.ProgressSkillProjection> skillRows =
                attemptRepository.findProgressAllTimeBySkill(
                                userId, PracticeAttempt.STATUS_DISCARDED).stream()
                        .filter(row -> row.getSkill() != null)
                        .collect(Collectors.toMap(
                                row -> row.getSkill().trim().toUpperCase(),
                                Function.identity(),
                                (left, right) -> left,
                                LinkedHashMap::new));
        List<PracticeAttempt> recent = attemptRepository.findRecentProgressAttempts(
                userId,
                PracticeAttempt.STATUS_DISCARDED,
                PageRequest.of(0, RECENT_DETAIL_LIMIT));
        List<PracticeAttempt> writingSource =
                attemptRepository.findProgressWritingAttempts(
                        userId,
                        PracticeAttempt.STATUS_DISCARDED,
                        PageRequest.of(0, WRITING_DETAIL_LIMIT + 1));
        boolean writingSourceTruncated =
                writingSource.size() > WRITING_DETAIL_LIMIT;
        List<PracticeAttempt> writingAttempts = writingSource.stream()
                .limit(WRITING_DETAIL_LIMIT)
                .toList();
        BoundedWritingSource writingWindow =
                boundedWritingSource(
                        writingAttempts, writingSourceTruncated);

        long total = number(allTime == null ? null : allTime.getActivityCount());
        long completed = number(allTime == null ? null : allTime.getCompletedCount());
        long inProgress = number(allTime == null ? null : allTime.getInProgressCount());
        long other = number(allTime == null ? null : allTime.getOtherCount());
        LocalDateTime allTimeAsOf =
                allTime == null || allTime.getAsOf() == null
                        ? asOf
                        : allTime.getAsOf();
        ProgressAttemptCounts counts =
                new ProgressAttemptCounts(total, completed, inProgress, other);

        ProgressObservationWindow allTimeWindow = new ProgressObservationWindow(
                "ALL_TIME",
                "Toàn bộ hoạt động đã lưu",
                false,
                null,
                total,
                false,
                allTime == null ? null : allTime.getObservedFrom(),
                allTime == null ? null : allTime.getObservedTo(),
                allTimeAsOf,
                allTime == null ? null : allTime.getObservedTo());
        ProgressObservationWindow recentWindow = recentWindow(recent, total, asOf);

        long validDurationCount =
                number(allTime == null ? null : allTime.getValidDurationCount());
        long excludedDurationCount =
                number(allTime == null ? null : allTime.getExcludedDurationCount());
        ProgressCoverage durationCoverage = coverage(
                completed,
                validDurationCount,
                excludedDurationCount,
                ProgressExclusionReason.MISSING_OR_INVALID_DURATION);
        Long validMinutes = validDurationCount == 0
                ? null
                : number(allTime == null ? null : allTime.getTotalValidMinutes());
        ProgressNumericFact durationFact = numericFact(
                validDurationCount == 0
                        ? ProgressAvailability.UNAVAILABLE
                        : excludedDurationCount == 0
                                ? ProgressAvailability.AVAILABLE
                                : ProgressAvailability.PARTIAL,
                validMinutes == null ? null : BigDecimal.valueOf(validMinutes),
                validMinutes == null ? null : BigDecimal.valueOf(validMinutes),
                null,
                "MINUTES",
                DURATION_PROFILE,
                validDurationCount,
                completed,
                allTimeWindow,
                durationCoverage);

        ProgressCoverage pageCoverage = pageCoverage(total, completed, inProgress, other);
        ProgressLevelFact levelFact = new ProgressLevelFact(
                ProgressAvailability.UNAVAILABLE,
                null,
                null,
                allTimeWindow,
                coverage(
                        total,
                        0,
                        total,
                        ProgressExclusionReason.UNSUPPORTED_SCORE_PROFILE));

        List<SkillMetric> allTimeMetrics = SKILLS.stream()
                .map(skill -> allTimeSkillMetric(
                        skill, skillRows.get(skill), asOf))
                .toList();
        VersionIdentitySnapshot writingIdentities =
                loadVersionIdentities(writingAttempts);
        WritingProgressAggregate writingProgress =
                writingProgress(
                        writingAttempts,
                        writingIdentities,
                        writingWindow,
                        asOf);
        VersionIdentitySnapshot identities = loadVersionIdentities(recent);
        RecentObjective recentObjective =
                recentObjective(recent, recentWindow, identities);
        ProgressNumericFact recentScoreFact = recentObjective.fact();

        List<PracticeResultSummary> history = recent.stream()
                .limit(RECENT_HISTORY_LIMIT)
                .map(attempt -> toSummary(attempt, identities, recentWindow))
                .toList();
        List<PracticeResultSummary> overviewHistory = history.stream()
                .limit(OVERVIEW_HISTORY_LIMIT)
                .toList();

        LocalDateTime weeklyStart = asOf.toLocalDate().minusDays(6).atStartOfDay();
        List<PracticeAttempt> currentWeek = recent.stream()
                .filter(attempt -> activityAt(attempt) != null)
                .filter(attempt -> !activityAt(attempt).isBefore(weeklyStart))
                .filter(attempt -> !activityAt(attempt).isAfter(asOf))
                .toList();
        List<SkillMetric> recentMetrics = SKILLS.stream()
                .map(skill -> recentSkillMetric(
                        skill, currentWeek, recentWindow, identities))
                .toList();
        List<ScoreTrendPoint> trend = recent.stream()
                .filter(attempt -> eligibleObjectiveAttempt(attempt, identities))
                .map(attempt -> toTrendPoint(attempt, identities, recentWindow))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ScoreTrendPoint::date))
                .toList();

        LearningProgressOverview overview = new LearningProgressOverview(
                displayName,
                avatarUrl,
                null,
                total,
                completed,
                validMinutes,
                decimalValue(recentScoreFact.value()),
                allTimeMetrics,
                heatmap(recent, asOf.toLocalDate()),
                overviewHistory,
                counts,
                levelFact,
                durationFact,
                recentScoreFact,
                allTimeWindow,
                recentWindow,
                pageCoverage);

        ProgressPageState state = new ProgressPageState(
                ProgressAvailability.AVAILABLE, null, null);
        PracticeAnalytics analytics = new PracticeAnalytics(
                recentMetrics,
                trend,
                List.of(),
                List.of(),
                history,
                writingProgress.taskSeams(),
                writingProgress.attemptCoverage(),
                recentWindow,
                state);
        return new PracticeProgressPageData(overview, analytics, state);
    }

    private SkillMetric allTimeSkillMetric(
            String skill,
            PracticeAttemptRepository.ProgressSkillProjection row,
            LocalDateTime asOf
    ) {
        long activityCount = number(row == null ? null : row.getActivityCount());
        LocalDateTime skillAsOf =
                row == null || row.getAsOf() == null ? asOf : row.getAsOf();
        ProgressObservationWindow window = new ProgressObservationWindow(
                "ALL_TIME_SKILL_" + skill,
                "Toàn bộ hoạt động kỹ năng " + PracticeDtos.getSkillLabel(skill),
                false,
                null,
                activityCount,
                false,
                row == null ? null : row.getObservedFrom(),
                row == null ? null : row.getObservedTo(),
                skillAsOf,
                row == null ? null : row.getObservedTo());
        ProgressAttemptCounts counts = new ProgressAttemptCounts(
                activityCount,
                number(row == null ? null : row.getCompletedCount()),
                number(row == null ? null : row.getInProgressCount()),
                number(row == null ? null : row.getOtherCount()));
        ProgressNumericFact scoreFact = skillScoreFact(skill, row, window);
        ProgressNumericFact deltaFact = unavailableComparison(activityCount, window);
        return new SkillMetric(
                skill,
                PracticeDtos.getSkillLabel(skill),
                decimalValue(scoreFact.value()),
                activityCount,
                null,
                counts,
                scoreFact,
                deltaFact,
                window,
                scoreFact.coverage());
    }

    private ProgressNumericFact skillScoreFact(
            String skill,
            PracticeAttemptRepository.ProgressSkillProjection row,
            ProgressObservationWindow window
    ) {
        long activityCount = number(row == null ? null : row.getActivityCount());
        long completedCount = number(row == null ? null : row.getCompletedCount());
        if ("SPEAKING".equals(skill)) {
            ProgressCoverage coverage = nonNumericSkillCoverage(
                    activityCount,
                    completedCount,
                    ProgressExclusionReason.SPEAKING_NUMERIC_AGGREGATION_NOT_SUPPORTED);
            return numericFact(
                    ProgressAvailability.NOT_SCORABLE,
                    null, null, null, null, "SPEAKING_ACTIVITY_COVERAGE_ONLY_V1",
                    0, activityCount, window, coverage);
        }
        if ("WRITING".equals(skill)) {
            ProgressCoverage coverage = nonNumericSkillCoverage(
                    activityCount,
                    completedCount,
                    ProgressExclusionReason
                            .WRITING_SKILL_AGGREGATION_REQUIRES_TASK_COHORT);
            return numericFact(
                    ProgressAvailability.NOT_SCORABLE,
                    null, null, null, null, WRITING_TASK_COHORT_PROFILE,
                    0, activityCount, window, coverage);
        }

        long eligible = number(row == null ? null : row.getEligibleScoreCount());
        long excluded = number(row == null ? null : row.getExcludedScoreCount());
        BigDecimal earned = row == null ? null : row.getEarnedPoints();
        BigDecimal possible = row == null ? null : row.getPossiblePoints();
        BigDecimal percentage = percentage(earned, possible);
        ProgressCoverage coverage = coverage(
                completedCount,
                eligible,
                excluded,
                excluded > 0
                        ? ProgressExclusionReason.LEGACY_UNVERIFIED
                        : ProgressExclusionReason.NO_ELIGIBLE_SCORE);
        ProgressAvailability availability = eligible == 0 || percentage == null
                ? ProgressAvailability.UNAVAILABLE
                : excluded == 0
                        ? ProgressAvailability.AVAILABLE
                        : ProgressAvailability.PARTIAL;
        return numericFact(
                availability,
                percentage,
                earned,
                possible,
                "PERCENTAGE",
                OBJECTIVE_PROFILE,
                eligible,
                activityCount,
                window,
                coverage);
    }

    private SkillMetric recentSkillMetric(
            String skill,
            List<PracticeAttempt> recent,
            ProgressObservationWindow sourceWindow,
            VersionIdentitySnapshot identities
    ) {
        List<PracticeAttempt> skillAttempts = recent.stream()
                .filter(attempt -> skill.equalsIgnoreCase(attempt.getSkill()))
                .toList();
        ProgressObservationWindow skillWindow = boundedWindow(
                "CURRENT_7_DAYS_" + skill + "_WITHIN_RECENT_DETAIL_LAST_100",
                "Cửa sổ 7 ngày của kỹ năng "
                        + PracticeDtos.getSkillLabel(skill)
                        + " trong nguồn tối đa 100 hoạt động gần đây",
                skillAttempts,
                sourceWindow.truncated(),
                sourceWindow.asOf());
        long completed = skillAttempts.stream().filter(this::completed).count();
        long inProgress = skillAttempts.stream()
                .filter(attempt ->
                        ATTEMPT_STATE.isCanonicalResumable(
                                attempt,
                                hasCoherentVersionIdentity(
                                        attempt, identities)))
                .count();
        long other = skillAttempts.size() - completed - inProgress;
        ProgressNumericFact scoreFact;
        if ("READING".equals(skill) || "LISTENING".equals(skill)) {
            scoreFact = recentObjective(skillAttempts, skillWindow, identities).fact();
        } else {
            scoreFact = skillScoreFact(skill, null, skillWindow);
            ProgressCoverage coverage = nonNumericSkillCoverage(
                    skillAttempts.size(),
                    completed,
                    "SPEAKING".equals(skill)
                            ? ProgressExclusionReason.SPEAKING_NUMERIC_AGGREGATION_NOT_SUPPORTED
                            : ProgressExclusionReason
                                    .WRITING_SKILL_AGGREGATION_REQUIRES_TASK_COHORT);
            scoreFact = numericFact(
                    scoreFact.availability(),
                    null, null, null, null, scoreFact.profileId(),
                    0, skillAttempts.size(), skillWindow, coverage);
        }
        return new SkillMetric(
                skill,
                PracticeDtos.getSkillLabel(skill),
                decimalValue(scoreFact.value()),
                skillAttempts.size(),
                null,
                new ProgressAttemptCounts(
                        skillAttempts.size(), completed, inProgress, other),
                scoreFact,
                unavailableComparison(skillAttempts.size(), skillWindow),
                skillWindow,
                scoreFact.coverage());
    }

    private RecentObjective recentObjective(
            List<PracticeAttempt> attempts,
            ProgressObservationWindow window,
            VersionIdentitySnapshot identities
    ) {
        List<PracticeAttempt> objective = attempts.stream()
                .filter(attempt -> eligibleObjectiveAttempt(attempt, identities))
                .toList();
        BigDecimal earned = objective.stream()
                .map(PracticeAttempt::getEarnedPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal possible = objective.stream()
                .map(PracticeAttempt::getTotalPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long completedObjective = attempts.stream()
                .filter(attempt -> isObjective(attempt.getSkill()))
                .filter(this::completed)
                .count();
        long excluded = completedObjective - objective.size();
        ProgressCoverage coverage = coverage(
                completedObjective,
                objective.size(),
                excluded,
                excluded > 0
                        ? ProgressExclusionReason.LEGACY_UNVERIFIED
                        : ProgressExclusionReason.NO_ELIGIBLE_SCORE);
        BigDecimal value = percentage(earned, possible);
        ProgressAvailability availability = objective.isEmpty() || value == null
                ? ProgressAvailability.UNAVAILABLE
                : excluded == 0
                        ? ProgressAvailability.AVAILABLE
                        : ProgressAvailability.PARTIAL;
        return new RecentObjective(numericFact(
                availability,
                value,
                objective.isEmpty() ? null : earned,
                objective.isEmpty() ? null : possible,
                "PERCENTAGE",
                OBJECTIVE_PROFILE,
                objective.size(),
                attempts.size(),
                window,
                coverage));
    }

    private boolean eligibleObjectiveAttempt(
            PracticeAttempt attempt,
            VersionIdentitySnapshot identities
    ) {
        return completed(attempt)
                && isObjective(attempt.getSkill())
                && hasCoherentVersionIdentity(attempt, identities)
                && "EARNED_POINTS".equals(attempt.getScoreUnit())
                && attempt.getEarnedPoints() != null
                && attempt.getTotalPoints() != null
                && attempt.getTotalPoints().signum() > 0;
    }

    private boolean completed(PracticeAttempt attempt) {
        return ATTEMPT_STATE.isCompleted(attempt);
    }

    private boolean isObjective(String skill) {
        return "READING".equalsIgnoreCase(skill) || "LISTENING".equalsIgnoreCase(skill);
    }

    private ProgressObservationWindow recentWindow(
            List<PracticeAttempt> recent,
            long allTimeCount,
            LocalDateTime asOf
    ) {
        List<LocalDateTime> observed = recent.stream()
                .map(this::activityAt)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        return new ProgressObservationWindow(
                "RECENT_DETAIL_LAST_100",
                "Chi tiết gần đây, tối đa 100 hoạt động",
                true,
                RECENT_DETAIL_LIMIT,
                recent.size(),
                allTimeCount > recent.size(),
                observed.isEmpty() ? null : observed.get(0),
                observed.isEmpty() ? null : observed.get(observed.size() - 1),
                asOf,
                observed.isEmpty() ? null : observed.get(observed.size() - 1));
    }

    private ProgressObservationWindow boundedWindow(
            String code,
            String label,
            List<PracticeAttempt> attempts,
            boolean sourceTruncated,
            LocalDateTime asOf
    ) {
        List<LocalDateTime> observed = attempts.stream()
                .map(this::activityAt)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        return new ProgressObservationWindow(
                code,
                label,
                true,
                RECENT_DETAIL_LIMIT,
                attempts.size(),
                sourceTruncated,
                observed.isEmpty() ? null : observed.get(0),
                observed.isEmpty() ? null : observed.get(observed.size() - 1),
                asOf,
                observed.isEmpty() ? null : observed.get(observed.size() - 1));
    }

    private List<HeatmapCell> heatmap(List<PracticeAttempt> recent, LocalDate asOfDate) {
        LocalDate first = asOfDate.minusDays(HEATMAP_DAYS - 1L);
        Map<LocalDate, MutableHeatmapCell> cells = new LinkedHashMap<>();
        for (int offset = HEATMAP_DAYS - 1; offset >= 0; offset--) {
            cells.put(asOfDate.minusDays(offset), new MutableHeatmapCell());
        }
        for (PracticeAttempt attempt : recent) {
            LocalDateTime activityAt = activityAt(attempt);
            if (activityAt == null
                    || activityAt.toLocalDate().isBefore(first)
                    || activityAt.toLocalDate().isAfter(asOfDate)) {
                continue;
            }
            MutableHeatmapCell cell = cells.get(activityAt.toLocalDate());
            cell.attemptCount++;
            Long minutes = validDurationMinutes(attempt, activityAt);
            if (minutes != null) {
                cell.totalMinutes += minutes;
                cell.validDurationCount++;
            } else if (completed(attempt)) {
                cell.excludedDurationCount++;
            } else {
                cell.inapplicableDurationCount++;
            }
        }
        return cells.entrySet().stream()
                .map(entry -> toHeatmapCell(entry.getKey(), entry.getValue()))
                .toList();
    }

    private HeatmapCell toHeatmapCell(LocalDate date, MutableHeatmapCell cell) {
        List<ProgressExclusion> exclusions = new ArrayList<>();
        if (cell.excludedDurationCount > 0) {
            exclusions.add(new ProgressExclusion(
                    ProgressExclusionReason.MISSING_OR_INVALID_DURATION,
                    cell.excludedDurationCount));
        }
        if (cell.inapplicableDurationCount > 0) {
            exclusions.add(new ProgressExclusion(
                    ProgressExclusionReason
                            .DURATION_NOT_APPLICABLE_FOR_INCOMPLETE_ACTIVITY,
                    cell.inapplicableDurationCount));
        }
        if (cell.attemptCount == 0) {
            exclusions.add(new ProgressExclusion(
                    ProgressExclusionReason.NO_ACTIVITY,
                    0));
        }
        long excludedCount =
                cell.excludedDurationCount + cell.inapplicableDurationCount;
        ProgressCoverage durationCoverage = new ProgressCoverage(
                cell.attemptCount,
                cell.validDurationCount,
                excludedCount,
                exclusions);
        return new HeatmapCell(
                date.toString(),
                cell.attemptCount,
                cell.validDurationCount == 0 ? null : cell.totalMinutes,
                durationCoverage);
    }

    private Long validDurationMinutes(PracticeAttempt attempt, LocalDateTime activityAt) {
        if (!completed(attempt) || attempt.getStartedAt() == null || activityAt == null) {
            return null;
        }
        long minutes = ChronoUnit.MINUTES.between(attempt.getStartedAt(), activityAt);
        return minutes > 0 && minutes < 240 ? minutes : null;
    }

    private ScoreTrendPoint toTrendPoint(
            PracticeAttempt attempt,
            VersionIdentitySnapshot identities,
            ProgressObservationWindow window
    ) {
        BigDecimal value = percentage(attempt.getEarnedPoints(), attempt.getTotalPoints());
        LocalDateTime activityAt = activityAt(attempt);
        if (value == null || activityAt == null) {
            return null;
        }
        ProgressCoverage coverage = coverage(1, 1, 0, ProgressExclusionReason.NO_ELIGIBLE_SCORE);
        return new ScoreTrendPoint(
                activityAt.toString(),
                attempt.getSkill(),
                value.doubleValue(),
                title(attempt, identities).title(),
                attempt.getId(),
                numericFact(
                        ProgressAvailability.AVAILABLE,
                        value,
                        attempt.getEarnedPoints(),
                        attempt.getTotalPoints(),
                        "PERCENTAGE",
                        OBJECTIVE_PROFILE,
                        1,
                        1,
                        window,
                        coverage));
    }

    private VersionIdentitySnapshot loadVersionIdentities(List<PracticeAttempt> recent) {
        Map<Long, PracticePublishedVersion> publishedVersions = loadById(
                publishedVersionRepository.findAllById(
                        distinctIds(recent, PracticeAttempt::getPublishedVersionId)),
                PracticePublishedVersion::getId);
        Map<Long, PracticeSetVersion> sets = loadById(
                setVersionRepository.findAllById(distinctIds(recent, PracticeAttempt::getSetVersionId)),
                PracticeSetVersion::getId);
        Map<Long, PracticeTestVersion> tests = loadById(
                testVersionRepository.findAllById(distinctIds(recent, PracticeAttempt::getTestVersionId)),
                PracticeTestVersion::getId);
        Map<Long, PracticeSectionVersion> sections = loadById(
                sectionVersionRepository.findAllById(
                        distinctIds(recent, PracticeAttempt::getSectionVersionId)),
                PracticeSectionVersion::getId);
        return new VersionIdentitySnapshot(publishedVersions, sets, tests, sections);
    }

    private <T> Map<Long, T> loadById(Iterable<T> values, Function<T, Long> id) {
        Map<Long, T> result = new LinkedHashMap<>();
        for (T value : values) {
            result.put(id.apply(value), value);
        }
        return result;
    }

    private List<Long> distinctIds(
            List<PracticeAttempt> attempts,
            Function<PracticeAttempt, Long> id
    ) {
        return attempts.stream().map(id).filter(Objects::nonNull).distinct().toList();
    }

    private PracticeResultSummary toSummary(
            PracticeAttempt attempt,
            VersionIdentitySnapshot identities,
            ProgressObservationWindow window
    ) {
        IdentityTitle identity = title(attempt, identities);
        boolean objective = eligibleObjectiveAttempt(attempt, identities);
        boolean coherentVersionIdentity =
                hasCoherentVersionIdentity(attempt, identities);
        PracticeAttemptStatePolicy.Presentation presentation =
                ATTEMPT_STATE.presentation(
                        attempt,
                        coherentVersionIdentity);
        return new PracticeResultSummary(
                attempt.getId(),
                identity.title(),
                attempt.getSkill(),
                objective ? attempt.getEarnedPoints() : null,
                objective ? attempt.getTotalPoints() : null,
                attempt.getSubmittedAt(),
                activityAt(attempt),
                attempt.getStatus(),
                presentation.code(),
                presentation.resumeAttemptId() != null,
                ATTEMPT_STATE.isResultEligible(
                        attempt, coherentVersionIdentity),
                attempt.getSetId(),
                attempt.getTestId(),
                attempt.getSectionId(),
                attempt.getPublishedVersionId(),
                attempt.getSetVersionId(),
                attempt.getTestVersionId(),
                attempt.getSectionVersionId(),
                identity.availability(),
                identity.reason(),
                attemptScoreFact(attempt, objective, identity, window));
    }

    private ProgressNumericFact attemptScoreFact(
            PracticeAttempt attempt,
            boolean eligibleObjective,
            IdentityTitle identity,
            ProgressObservationWindow window
    ) {
        if ("SPEAKING".equalsIgnoreCase(attempt.getSkill())) {
            ProgressCoverage coverage = nonNumericSkillCoverage(
                    1,
                    completed(attempt) ? 1 : 0,
                    ProgressExclusionReason.SPEAKING_NUMERIC_AGGREGATION_NOT_SUPPORTED);
            return numericFact(
                    ProgressAvailability.NOT_SCORABLE,
                    null, null, null, null, "SPEAKING_ACTIVITY_COVERAGE_ONLY_V1",
                    0, 1, window, coverage);
        }
        if ("WRITING".equalsIgnoreCase(attempt.getSkill())) {
            ProgressCoverage coverage = nonNumericSkillCoverage(
                    1,
                    completed(attempt) ? 1 : 0,
                    ProgressExclusionReason
                            .WRITING_SKILL_AGGREGATION_REQUIRES_TASK_COHORT);
            return numericFact(
                    ProgressAvailability.NOT_SCORABLE,
                    null, null, null, null, WRITING_TASK_COHORT_PROFILE,
                    0, 1, window, coverage);
        }
        if (!eligibleObjective) {
            ProgressExclusionReason reason =
                    identity.availability() == ProgressAvailability.AVAILABLE
                            ? ProgressExclusionReason.NO_ELIGIBLE_SCORE
                            : identity.reason();
            ProgressCoverage coverage = coverage(1, 0, 1, reason);
            return numericFact(
                    ProgressAvailability.UNAVAILABLE,
                    null, null, null, "PERCENTAGE", OBJECTIVE_PROFILE,
                    0, 1, window, coverage);
        }
        BigDecimal value = percentage(attempt.getEarnedPoints(), attempt.getTotalPoints());
        ProgressCoverage coverage =
                coverage(1, 1, 0, ProgressExclusionReason.NO_ELIGIBLE_SCORE);
        return numericFact(
                ProgressAvailability.AVAILABLE,
                value,
                attempt.getEarnedPoints(),
                attempt.getTotalPoints(),
                "PERCENTAGE",
                OBJECTIVE_PROFILE,
                1,
                1,
                window,
                coverage);
    }

    private IdentityTitle title(
            PracticeAttempt attempt,
            VersionIdentitySnapshot identities
    ) {
        if (!hasCompleteVersionLock(attempt)) {
            return new IdentityTitle(
                    historicalTitle(attempt),
                    ProgressAvailability.PARTIAL,
                    ProgressExclusionReason.INCOMPLETE_VERSION_LOCK);
        }
        PracticeSetVersion set = identities.sets().get(attempt.getSetVersionId());
        PracticeTestVersion test = identities.tests().get(attempt.getTestVersionId());
        PracticeSectionVersion section =
                identities.sections().get(attempt.getSectionVersionId());
        if (!identityMatches(
                attempt,
                identities.publishedVersions().get(attempt.getPublishedVersionId()),
                set,
                test,
                section)) {
            return new IdentityTitle(
                    historicalTitle(attempt),
                    ProgressAvailability.PARTIAL,
                    ProgressExclusionReason.LEGACY_UNVERIFIED);
        }
        List<String> parts = new ArrayList<>();
        addTitle(parts, set.getTitle());
        addTitle(parts, test.getTitle());
        addTitle(parts, section.getTitle());
        return new IdentityTitle(
                String.join(" - ", parts),
                ProgressAvailability.AVAILABLE,
                null);
    }

    private boolean identityMatches(
            PracticeAttempt attempt,
            PracticePublishedVersion publishedVersion,
            PracticeSetVersion set,
            PracticeTestVersion test,
            PracticeSectionVersion section
    ) {
        return publishedVersion != null
                && set != null
                && test != null
                && section != null
                && Objects.equals(
                        publishedVersion.getId(), attempt.getPublishedVersionId())
                && Objects.equals(publishedVersion.getSetId(), attempt.getSetId())
                && Objects.equals(set.getId(), attempt.getSetVersionId())
                && Objects.equals(set.getPublishedVersionId(), attempt.getPublishedVersionId())
                && Objects.equals(set.getSetId(), attempt.getSetId())
                && Objects.equals(test.getId(), attempt.getTestVersionId())
                && Objects.equals(test.getPublishedVersionId(), attempt.getPublishedVersionId())
                && Objects.equals(test.getSetVersionId(), attempt.getSetVersionId())
                && Objects.equals(test.getTestId(), attempt.getTestId())
                && Objects.equals(section.getId(), attempt.getSectionVersionId())
                && Objects.equals(section.getPublishedVersionId(), attempt.getPublishedVersionId())
                && Objects.equals(section.getTestVersionId(), attempt.getTestVersionId())
                && Objects.equals(section.getSectionId(), attempt.getSectionId())
                && section.getSkill() != null
                && attempt.getSkill() != null
                && section.getSkill().equalsIgnoreCase(attempt.getSkill());
    }

    private boolean hasCoherentVersionIdentity(
            PracticeAttempt attempt,
            VersionIdentitySnapshot identities
    ) {
        if (!hasCompleteVersionLock(attempt)) {
            return false;
        }
        return identityMatches(
                attempt,
                identities.publishedVersions().get(attempt.getPublishedVersionId()),
                identities.sets().get(attempt.getSetVersionId()),
                identities.tests().get(attempt.getTestVersionId()),
                identities.sections().get(attempt.getSectionVersionId()));
    }

    private void addTitle(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value);
        }
    }

    private String historicalTitle(PracticeAttempt attempt) {
        return attempt.getId() == null
                ? "Lượt luyện tập lịch sử"
                : "Lượt luyện tập lịch sử #" + attempt.getId();
    }

    private boolean hasCompleteVersionLock(PracticeAttempt attempt) {
        return ATTEMPT_STATE.versionLockState(attempt)
                == PracticeAttemptStatePolicy.VersionLockState.COMPLETE;
    }

    private LocalDateTime activityAt(PracticeAttempt attempt) {
        return PracticeAttemptStatePolicy.activityAt(attempt);
    }

    private WritingProgressAggregate writingProgress(
            List<PracticeAttempt> attempts,
            VersionIdentitySnapshot identities,
            BoundedWritingSource sourceWindow,
            LocalDateTime asOf
    ) {
        List<Long> sectionVersionIds = attempts.stream()
                .filter(attempt -> hasCoherentVersionIdentity(attempt, identities))
                .map(PracticeAttempt::getSectionVersionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<PracticeQuestionVersion> questionVersions = sectionVersionIds.isEmpty()
                ? List.of()
                : questionVersionRepository
                        .findBySectionVersionIdInOrderBySectionVersionIdAscDisplayOrderAscQuestionNoAscIdAsc(
                                sectionVersionIds);
        Map<Long, List<PracticeQuestionVersion>> questionsBySection =
                questionVersions.stream().collect(Collectors.groupingBy(
                        PracticeQuestionVersion::getSectionVersionId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        Map<WritingTaskType, MutableWritingTask> taskStates = new LinkedHashMap<>();
        for (WritingTaskType task : WritingTaskType.values()) {
            taskStates.put(task, new MutableWritingTask(task));
        }

        long eligibleAttempts = 0;
        Map<ProgressExclusionReason, Long> attemptExclusions = new LinkedHashMap<>();
        for (PracticeAttempt attempt : attempts) {
            WritingAttemptAnalysis analysis = analyzeWritingAttempt(
                    attempt, identities, questionsBySection, taskStates);
            if (analysis.eligible()) {
                eligibleAttempts++;
            } else {
                increment(attemptExclusions, analysis.reason());
            }
        }

        ProgressCoverage attemptCoverage = coverageFromReasons(
                attempts.size(),
                eligibleAttempts,
                attemptExclusions);
        List<WritingTaskProgressSeam> taskSeams = taskStates.values().stream()
                .map(state -> state.toDto(sourceWindow, asOf))
                .toList();
        return new WritingProgressAggregate(taskSeams, attemptCoverage);
    }

    private BoundedWritingSource boundedWritingSource(
            List<PracticeAttempt> attempts,
            boolean truncated
    ) {
        List<LocalDateTime> observations = attempts.stream()
                .map(this::activityAt)
                .filter(Objects::nonNull)
                .toList();
        LocalDateTime observedFrom = observations.stream()
                .min(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime observedTo = observations.stream()
                .max(LocalDateTime::compareTo)
                .orElse(null);
        return new BoundedWritingSource(
                WRITING_DETAIL_LIMIT,
                attempts.size(),
                truncated,
                observedFrom,
                observedTo);
    }

    private WritingAttemptAnalysis analyzeWritingAttempt(
            PracticeAttempt attempt,
            VersionIdentitySnapshot identities,
            Map<Long, List<PracticeQuestionVersion>> questionsBySection,
            Map<WritingTaskType, MutableWritingTask> taskStates
    ) {
        if (!hasCoherentVersionIdentity(attempt, identities)) {
            return WritingAttemptAnalysis.excluded(
                    hasCompleteVersionLock(attempt)
                            ? ProgressExclusionReason.LEGACY_UNVERIFIED
                            : ProgressExclusionReason.INCOMPLETE_VERSION_LOCK);
        }

        List<PracticeQuestionVersion> sectionQuestions =
                questionsBySection.getOrDefault(
                        attempt.getSectionVersionId(), List.of());
        List<PracticeQuestionVersion> questions = sectionQuestions.stream()
                .filter(question -> Objects.equals(
                        question.getPublishedVersionId(),
                        attempt.getPublishedVersionId()))
                .toList();
        if (questions.isEmpty() && !sectionQuestions.isEmpty()) {
            return WritingAttemptAnalysis.excluded(
                    ProgressExclusionReason.LEGACY_UNVERIFIED);
        }
        List<PracticeQuestionVersion> writingQuestions = questions.stream()
                .filter(question -> PracticeQuestion.TYPE_ESSAY.equals(
                                question.getQuestionType())
                        || question.getWritingTaskType() != null)
                .toList();
        if (writingQuestions.isEmpty()) {
            return WritingAttemptAnalysis.excluded(
                    ProgressExclusionReason.WRITING_TASK_IDENTITY_MISSING);
        }

        JsonNode root = null;
        ProgressExclusionReason payloadReason = null;
        if (attempt.getAiFeedbackJson() == null
                || attempt.getAiFeedbackJson().isBlank()) {
            payloadReason = ProgressExclusionReason.WRITING_SCORE_EVIDENCE_MISSING;
        } else {
            try {
                root = objectMapper.readTree(attempt.getAiFeedbackJson());
                if (root == null || !root.isObject()) {
                    payloadReason =
                            ProgressExclusionReason.WRITING_SCORE_EVIDENCE_MALFORMED;
                } else if (writingFeedbackReader.isLegacyFlatFeedback(root)) {
                    payloadReason =
                            ProgressExclusionReason.WRITING_LEGACY_SCORE_EVIDENCE;
                }
            } catch (Exception ex) {
                payloadReason =
                        ProgressExclusionReason.WRITING_SCORE_EVIDENCE_MALFORMED;
            }
        }

        int eligible = 0;
        int excluded = 0;
        ProgressExclusionReason firstReason = null;
        for (PracticeQuestionVersion question : writingQuestions) {
            WritingTaskType immutableTask = question.getWritingTaskType();
            if (immutableTask == null) {
                excluded++;
                if (firstReason == null) {
                    firstReason =
                            ProgressExclusionReason.WRITING_TASK_IDENTITY_MISSING;
                }
                continue;
            }

            MutableWritingTask taskState = taskStates.get(immutableTask);
            if (!PracticeQuestion.TYPE_ESSAY.equals(question.getQuestionType())) {
                taskState.exclude(
                        ProgressExclusionReason.WRITING_TASK_IDENTITY_MISMATCH);
                excluded++;
                if (firstReason == null) {
                    firstReason =
                            ProgressExclusionReason.WRITING_TASK_IDENTITY_MISMATCH;
                }
                continue;
            }
            if (!completed(attempt)) {
                taskState.exclude(
                        ProgressExclusionReason
                                .SCORE_NOT_APPLICABLE_FOR_INCOMPLETE_ACTIVITY);
                excluded++;
                if (firstReason == null) {
                    firstReason = ProgressExclusionReason
                            .SCORE_NOT_APPLICABLE_FOR_INCOMPLETE_ACTIVITY;
                }
                continue;
            }
            if (payloadReason != null) {
                taskState.exclude(payloadReason);
                excluded++;
                if (firstReason == null) {
                    firstReason = payloadReason;
                }
                continue;
            }

            JsonNode entry = question.getQuestionId() == null
                    ? null
                    : root.get(String.valueOf(question.getQuestionId()));
            WritingEvidence evidence =
                    writingEvidence(question, immutableTask, entry);
            if (evidence.reason() != null) {
                taskState.exclude(evidence.reason());
                excluded++;
                if (firstReason == null) {
                    firstReason = evidence.reason();
                }
            } else {
                taskState.include(evidence);
                eligible++;
            }
        }

        if (eligible > 0 && excluded == 0) {
            return WritingAttemptAnalysis.included();
        }
        return WritingAttemptAnalysis.excluded(
                firstReason == null
                        ? ProgressExclusionReason.WRITING_TASK_IDENTITY_MISSING
                        : firstReason);
    }

    private WritingEvidence writingEvidence(
            PracticeQuestionVersion question,
            WritingTaskType immutableTask,
            JsonNode entry
    ) {
        WritingFeedbackCompatibilityReader.EntryResult parsed =
                writingFeedbackReader.parseStoredEntry(entry);
        if (parsed.status() == WritingFeedbackCompatibilityReader.Status.MISSING) {
            return WritingEvidence.excluded(
                    ProgressExclusionReason.WRITING_SCORE_EVIDENCE_MISSING);
        }
        if (parsed.status() != WritingFeedbackCompatibilityReader.Status.VALID_CURRENT
                || parsed.value() == null) {
            return WritingEvidence.excluded(
                    ProgressExclusionReason.WRITING_SCORE_EVIDENCE_MALFORMED);
        }

        WritingEvaluationResult value = parsed.value();
        if (value.taskType() == null || value.taskType().isBlank()) {
            return WritingEvidence.excluded(
                    ProgressExclusionReason.WRITING_TASK_IDENTITY_MISSING);
        }
        if (!immutableTask.name().equals(value.taskType())) {
            return WritingEvidence.excluded(
                    ProgressExclusionReason.WRITING_TASK_IDENTITY_MISMATCH);
        }
        if (!value.scoreAvailableFlag()) {
            return WritingEvidence.excluded(
                    ProgressExclusionReason.WRITING_EVALUATION_NOT_SCORE_BEARING);
        }
        if ("LEGACY_EVALUATED".equals(value.evaluationStatus())
                || "LEGACY".equals(value.evaluationSource())
                || WritingFeedbackCompatibilityReader.LEGACY_SCORING_CONTRACT
                .equals(value.scoringContract())
                || value.scoringContract() == null
                || value.scoringContract().isBlank()
                || !WritingAssessmentPolicyBundle.POLICY_BUNDLE_ID.equals(
                value.policyBundleId())) {
            return WritingEvidence.excluded(
                    ProgressExclusionReason.WRITING_LEGACY_SCORE_EVIDENCE);
        }
        if (!hasExactCurrentWritingScoreShape(entry)) {
            return WritingEvidence.excluded(
                    ProgressExclusionReason.WRITING_SCORE_EVIDENCE_MALFORMED);
        }
        if (!WritingAssessmentPolicyBundle
                .hasExactCurrentScoreProvenance(value)
                || !WRITING_TASK_NATIVE_CONTRACT.equals(value.scoringContract())
                || !WRITING_EVALUATION_ENGINE.equals(value.engine())) {
            return WritingEvidence.excluded(
                    ProgressExclusionReason.WRITING_SCORING_PROFILE_UNSUPPORTED);
        }
        if (value.rawScore() == null
                || value.rawScoreMax() == null
                || question.getPoints() == null
                || question.getPoints().signum() <= 0
                || value.rawScoreMax().compareTo(question.getPoints()) != 0) {
            return WritingEvidence.excluded(
                    ProgressExclusionReason.WRITING_MAXIMUM_MISMATCH);
        }

        String policyBundleId = value.policyBundleId();
        String profileId = "WRITING:"
                + value.scoringContract()
                + ":"
                + value.engine()
                + (policyBundleId == null ? "" : ":BUNDLE=" + policyBundleId);
        return WritingEvidence.eligible(
                value.rawScore(),
                value.rawScoreMax().stripTrailingZeros(),
                profileId,
                policyBundleId);
    }

    private static boolean hasExactCurrentWritingScoreShape(JsonNode entry) {
        return entry != null
                && entry.isObject()
                && entry.path("task_type").isTextual()
                && entry.path("engine").isTextual()
                && entry.path("scoring_contract").isTextual()
                && entry.path("policy_bundle_id").isTextual()
                && entry.path("evaluation_status").isTextual()
                && entry.path("evaluation_source").isTextual()
                && entry.path("evaluation_reason").isTextual()
                && entry.path("evaluation_retryable").isBoolean()
                && entry.path("score_available").isBoolean()
                && entry.path("score_available").asBoolean()
                && entry.path("raw_score").isNumber()
                && entry.path("raw_score_max").isNumber();
    }

    private ProgressCoverage coverageFromReasons(
            long activity,
            long eligible,
            Map<ProgressExclusionReason, Long> reasons
    ) {
        long excluded = Math.max(0, activity - eligible);
        List<ProgressExclusion> exclusions = reasons.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Comparator.comparing(
                        (Map.Entry<ProgressExclusionReason, Long> entry) ->
                                entry.getKey().name()))
                .map(entry -> new ProgressExclusion(entry.getKey(), entry.getValue()))
                .toList();
        if (activity == 0) {
            exclusions = List.of(new ProgressExclusion(
                    ProgressExclusionReason.NO_ACTIVITY, 0));
        }
        return new ProgressCoverage(activity, eligible, excluded, exclusions);
    }

    private void increment(
            Map<ProgressExclusionReason, Long> reasons,
            ProgressExclusionReason reason
    ) {
        reasons.merge(reason, 1L, Long::sum);
    }

    private ProgressNumericFact unavailableComparison(
            long activityCount,
            ProgressObservationWindow window
    ) {
        ProgressCoverage coverage = new ProgressCoverage(
                activityCount,
                0,
                activityCount,
                activityCount == 0
                        ? List.of(new ProgressExclusion(
                                ProgressExclusionReason.NO_ACTIVITY, 0))
                        : List.of(new ProgressExclusion(
                                ProgressExclusionReason.COMPARISON_SAMPLE_UNAVAILABLE,
                                activityCount)));
        return numericFact(
                ProgressAvailability.UNAVAILABLE,
                null, null, null, "PERCENTAGE_POINTS", null,
                0, activityCount, window, coverage);
    }

    private ProgressCoverage pageCoverage(
            long total,
            long completed,
            long inProgress,
            long other
    ) {
        if (total == 0) {
            return coverage(0, 0, 0, ProgressExclusionReason.NO_ACTIVITY);
        }
        List<ProgressExclusion> exclusions = new ArrayList<>();
        if (inProgress > 0) {
            exclusions.add(new ProgressExclusion(
                    ProgressExclusionReason.NO_ELIGIBLE_SCORE, inProgress));
        }
        if (other > 0) {
            exclusions.add(new ProgressExclusion(
                    ProgressExclusionReason.LEGACY_UNVERIFIED, other));
        }
        return new ProgressCoverage(total, completed, inProgress + other, exclusions);
    }

    private ProgressCoverage coverage(
            long activity,
            long eligible,
            long excluded,
            ProgressExclusionReason reason
    ) {
        List<ProgressExclusion> exclusions = excluded > 0 || activity == 0
                ? List.of(new ProgressExclusion(reason, excluded))
                : List.of();
        return new ProgressCoverage(activity, eligible, excluded, exclusions);
    }

    private ProgressCoverage nonNumericSkillCoverage(
            long activityCount,
            long completedCount,
            ProgressExclusionReason completedReason
    ) {
        long completed = Math.max(0, Math.min(activityCount, completedCount));
        long incomplete = activityCount - completed;
        List<ProgressExclusion> exclusions = new ArrayList<>();
        if (completed > 0 || activityCount == 0) {
            exclusions.add(new ProgressExclusion(completedReason, completed));
        }
        if (incomplete > 0) {
            exclusions.add(new ProgressExclusion(
                    ProgressExclusionReason.SCORE_NOT_APPLICABLE_FOR_INCOMPLETE_ACTIVITY,
                    incomplete));
        }
        return new ProgressCoverage(activityCount, 0, activityCount, exclusions);
    }

    private ProgressNumericFact numericFact(
            ProgressAvailability availability,
            BigDecimal value,
            BigDecimal numerator,
            BigDecimal denominator,
            String unit,
            String profile,
            long sampleSize,
            long activityCount,
            ProgressObservationWindow window,
            ProgressCoverage coverage
    ) {
        return new ProgressNumericFact(
                availability,
                value,
                numerator,
                denominator,
                unit,
                profile,
                sampleSize,
                activityCount,
                window,
                coverage);
    }

    private BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() <= 0) {
            return null;
        }
        return numerator.multiply(BigDecimal.valueOf(100))
                .divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private Double decimalValue(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private long number(Long value) {
        return value == null ? 0 : value;
    }

    private record RecentObjective(ProgressNumericFact fact) {}

    private record IdentityTitle(
            String title,
            ProgressAvailability availability,
            ProgressExclusionReason reason
    ) {}

    private record VersionIdentitySnapshot(
            Map<Long, PracticePublishedVersion> publishedVersions,
            Map<Long, PracticeSetVersion> sets,
            Map<Long, PracticeTestVersion> tests,
            Map<Long, PracticeSectionVersion> sections
    ) {}

    private record WritingProgressAggregate(
            List<WritingTaskProgressSeam> taskSeams,
            ProgressCoverage attemptCoverage
    ) {}

    private record WritingAttemptAnalysis(
            boolean eligible,
            ProgressExclusionReason reason
    ) {
        private static WritingAttemptAnalysis included() {
            return new WritingAttemptAnalysis(true, null);
        }

        private static WritingAttemptAnalysis excluded(
                ProgressExclusionReason reason
        ) {
            return new WritingAttemptAnalysis(false, reason);
        }
    }

    private record WritingEvidence(
            BigDecimal earned,
            BigDecimal maximum,
            String profileId,
            String policyBundleId,
            ProgressExclusionReason reason
    ) {
        private static WritingEvidence eligible(
                BigDecimal earned,
                BigDecimal maximum,
                String profileId,
                String policyBundleId
        ) {
            return new WritingEvidence(
                    earned, maximum, profileId, policyBundleId, null);
        }

        private static WritingEvidence excluded(
                ProgressExclusionReason reason
        ) {
            return new WritingEvidence(null, null, null, null, reason);
        }
    }

    private record WritingCohortKey(
            String profileId,
            String policyBundleId,
            BigDecimal maximum
    ) {}

    private final class MutableWritingTask {
        private final WritingTaskType task;
        private final Map<WritingCohortKey, MutableWritingCohort> cohorts =
                new LinkedHashMap<>();
        private final Map<ProgressExclusionReason, Long> exclusions =
                new LinkedHashMap<>();
        private long activityCount;
        private long eligibleCount;

        private MutableWritingTask(WritingTaskType task) {
            this.task = task;
        }

        private void include(WritingEvidence evidence) {
            activityCount++;
            eligibleCount++;
            WritingCohortKey key = new WritingCohortKey(
                    evidence.profileId(),
                    evidence.policyBundleId(),
                    evidence.maximum());
            cohorts.computeIfAbsent(key, ignored -> new MutableWritingCohort(key))
                    .include(evidence.earned());
        }

        private void exclude(ProgressExclusionReason reason) {
            activityCount++;
            exclusions.merge(reason, 1L, Long::sum);
        }

        private WritingTaskProgressSeam toDto(
                BoundedWritingSource sourceWindow,
                LocalDateTime asOf
        ) {
            long excludedCount = activityCount - eligibleCount;
            List<ProgressExclusion> exclusionRows = exclusions.entrySet().stream()
                    .sorted(Comparator.comparing(
                            (Map.Entry<ProgressExclusionReason, Long> entry) ->
                                    entry.getKey().name()))
                    .map(entry -> new ProgressExclusion(
                            entry.getKey(), entry.getValue()))
                    .toList();
            if (activityCount == 0) {
                exclusionRows = List.of(new ProgressExclusion(
                        ProgressExclusionReason.NO_ACTIVITY, 0));
            }
            ProgressCoverage taskCoverage = new ProgressCoverage(
                    activityCount,
                    eligibleCount,
                    excludedCount,
                    exclusionRows);
            ProgressObservationWindow taskWindow =
                    new ProgressObservationWindow(
                            "RECENT_WRITING_SOURCE_" + task.name(),
                            "Nguồn chọn chung: tối đa "
                                    + sourceWindow.limit()
                                    + " hoạt động Writing gần nhất; phân tích tác vụ "
                                    + task.name(),
                            true,
                            sourceWindow.limit(),
                            sourceWindow.returnedCount(),
                            sourceWindow.truncated(),
                            sourceWindow.observedFrom(),
                            sourceWindow.observedTo(),
                            asOf,
                            sourceWindow.observedTo());
            List<Map.Entry<WritingCohortKey, MutableWritingCohort>> ordered =
                    cohorts.entrySet().stream()
                            .sorted(Comparator
                                    .comparing((Map.Entry<
                                                    WritingCohortKey,
                                                    MutableWritingCohort> entry) ->
                                            entry.getKey().profileId())
                                    .thenComparing(entry ->
                                            entry.getKey().maximum()))
                            .toList();
            List<WritingTaskScoreCohort> cohortRows = new ArrayList<>();
            for (int index = 0; index < ordered.size(); index++) {
                cohortRows.add(ordered.get(index).getValue().toDto(
                        task, index + 1, sourceWindow, asOf));
            }
            ProgressAvailability availability = eligibleCount == 0
                    ? ProgressAvailability.UNAVAILABLE
                    : excludedCount == 0
                            ? ProgressAvailability.AVAILABLE
                            : ProgressAvailability.PARTIAL;
            return new WritingTaskProgressSeam(
                    task.name(),
                    "Câu " + task.name().substring(1),
                    availability,
                    cohortRows,
                    taskWindow,
                    taskCoverage);
        }
    }

    private final class MutableWritingCohort {
        private final WritingCohortKey key;
        private BigDecimal earned = BigDecimal.ZERO;
        private BigDecimal possible = BigDecimal.ZERO;
        private long sampleSize;

        private MutableWritingCohort(WritingCohortKey key) {
            this.key = key;
        }

        private void include(BigDecimal value) {
            earned = earned.add(value);
            possible = possible.add(key.maximum());
            sampleSize++;
        }

        private WritingTaskScoreCohort toDto(
                WritingTaskType task,
                int cohortNumber,
                BoundedWritingSource sourceWindow,
                LocalDateTime asOf
        ) {
            ProgressObservationWindow window = new ProgressObservationWindow(
                    "RECENT_WRITING_SOURCE_"
                            + task.name()
                            + "_COHORT_"
                            + cohortNumber,
                    "Nguồn chọn chung: tối đa "
                            + sourceWindow.limit()
                            + " hoạt động Writing gần nhất; cohort "
                            + task.name()
                            + " / "
                            + key.profileId()
                            + " / max "
                            + decimalIdentity(key.maximum()),
                    true,
                    sourceWindow.limit(),
                    sourceWindow.returnedCount(),
                    sourceWindow.truncated(),
                    sourceWindow.observedFrom(),
                    sourceWindow.observedTo(),
                    asOf,
                    sourceWindow.observedTo());
            ProgressCoverage cohortCoverage = new ProgressCoverage(
                    sampleSize, sampleSize, 0, List.of());
            ProgressNumericFact scoreFact = numericFact(
                    ProgressAvailability.AVAILABLE,
                    percentage(earned, possible),
                    earned,
                    possible,
                    "PERCENTAGE",
                    key.profileId(),
                    sampleSize,
                    sampleSize,
                    window,
                    cohortCoverage);
            String cohortId = task.name()
                    + "::"
                    + key.profileId()
                    + "::MAX="
                    + decimalIdentity(key.maximum());
            return new WritingTaskScoreCohort(
                    cohortId,
                    task.name(),
                    key.profileId(),
                    key.policyBundleId(),
                    key.maximum(),
                    scoreFact);
        }
    }

    private String decimalIdentity(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private record BoundedWritingSource(
            int limit,
            long returnedCount,
            boolean truncated,
            LocalDateTime observedFrom,
            LocalDateTime observedTo
    ) {}

    private static final class MutableHeatmapCell {
        private int attemptCount;
        private long totalMinutes;
        private long validDurationCount;
        private long excludedDurationCount;
        private long inapplicableDurationCount;
    }
}
