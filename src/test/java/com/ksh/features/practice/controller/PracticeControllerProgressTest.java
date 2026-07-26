package com.ksh.features.practice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.practice.dto.PracticeDtos.PracticeAnalytics;
import com.ksh.features.practice.dto.PracticeDtos.PracticeProgressPageData;
import com.ksh.features.practice.dto.PracticeDtos.LearningProgressOverview;
import com.ksh.features.practice.dto.PracticeDtos.ProgressAttemptCounts;
import com.ksh.features.practice.dto.PracticeDtos.ProgressAvailability;
import com.ksh.features.practice.dto.PracticeDtos.ProgressCoverage;
import com.ksh.features.practice.dto.PracticeDtos.ProgressExclusion;
import com.ksh.features.practice.dto.PracticeDtos.ProgressExclusionReason;
import com.ksh.features.practice.dto.PracticeDtos.ProgressFilterState;
import com.ksh.features.practice.dto.PracticeDtos.ProgressNumericFact;
import com.ksh.features.practice.dto.PracticeDtos.ProgressObservationWindow;
import com.ksh.features.practice.dto.PracticeDtos.ProgressPageState;
import com.ksh.features.practice.dto.PracticeDtos.ProgressSkillFilter;
import com.ksh.features.practice.dto.PracticeDtos.ProgressWritingTaskFilter;
import com.ksh.features.practice.dto.PracticeDtos.ScoreTrendPoint;
import com.ksh.features.practice.dto.PracticeDtos.SkillMetric;
import com.ksh.features.practice.dto.PracticeDtos.WritingTaskProgressSeam;
import com.ksh.features.practice.dto.PracticeDtos.WritingTaskScoreCohort;
import com.ksh.features.practice.repository.PracticeSectionRepository;
import com.ksh.features.practice.result.PracticeResultAssembler;
import com.ksh.features.practice.result.PracticeResultDetailAssembler;
import com.ksh.features.practice.service.PracticeAttemptDiscardService;
import com.ksh.features.practice.service.PracticeCatalogService;
import com.ksh.features.practice.service.PracticeDetailPageService;
import com.ksh.features.practice.service.PracticeLearnerAccessService;
import com.ksh.features.practice.service.PracticeProgressService;
import com.ksh.features.practice.service.PracticeService;
import com.ksh.features.practice.service.PracticeSpeakingMediaService;
import com.ksh.features.practice.web.PracticeModelAttributes;
import com.ksh.security.KshUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PracticeControllerProgressTest {

    @Test
    void progressNormalizesUnknownFilterAndTabWithoutEchoingRawValues() {
        PracticeProgressService progressService = mock(PracticeProgressService.class);
        UserRepository userRepository = mock(UserRepository.class);
        KshUserDetails principal = mock(KshUserDetails.class);
        when(principal.getId()).thenReturn(7L);
        when(principal.getFullName()).thenReturn("Learner");
        when(userRepository.findById(7L)).thenReturn(Optional.empty());
        PracticeProgressPageData data = PracticeProgressPageData.unavailable(
                "Learner", "", ProgressExclusionReason.PAGE_DATA_UNAVAILABLE);
        when(progressService.getProgressPageData(7L, "Learner", "")).thenReturn(data);
        PracticeController controller =
                controller(progressService, userRepository, new ObjectMapper());
        ExtendedModelMap model = new ExtendedModelMap();

        controller.progress(
                principal,
                "<script>",
                "SPEAKING%3Cscript%3E",
                "Q51",
                "untrusted-profile",
                model);

        ProgressFilterState filter =
                (ProgressFilterState) model.get(PracticeModelAttributes.PROGRESS_FILTER);
        assertThat(filter.tab()).isEqualTo("overview");
        assertThat(filter.skill()).isEqualTo(ProgressSkillFilter.ALL);
        assertThat(filter.writingTask()).isEqualTo(ProgressWritingTaskFilter.ALL);
        assertThat(filter.profileId()).isEqualTo("ALL");
        assertThat(filter.profileOptions()).isEmpty();
        assertThat(model.values()).noneMatch(value ->
                value != null && value.toString().contains("<script>"));

        ExtendedModelMap writingModel = new ExtendedModelMap();
        controller.progress(
                principal,
                "overview",
                "WRITING",
                "Q99<script>",
                "untrusted-profile",
                writingModel);
        ProgressFilterState writingFilter =
                (ProgressFilterState) writingModel.get(
                        PracticeModelAttributes.PROGRESS_FILTER);
        assertThat(writingFilter.skill()).isEqualTo(ProgressSkillFilter.WRITING);
        assertThat(writingFilter.writingTask())
                .isEqualTo(ProgressWritingTaskFilter.ALL);
        assertThat(writingFilter.profileId()).isEqualTo("ALL");
        assertThat(writingModel.values()).noneMatch(value ->
                value != null && value.toString().contains("<script>"));
    }

    @Test
    void writingFilterAcceptsOnlyCanonicalCohortOptionsAndSelectsThatCohort() {
        PracticeProgressService progressService = mock(PracticeProgressService.class);
        UserRepository userRepository = mock(UserRepository.class);
        KshUserDetails principal = mock(KshUserDetails.class);
        when(principal.getId()).thenReturn(7L);
        when(principal.getFullName()).thenReturn("Learner");
        when(userRepository.findById(7L)).thenReturn(Optional.empty());
        PracticeProgressPageData base = PracticeProgressPageData.unavailable(
                "Learner", "", ProgressExclusionReason.NO_ACTIVITY);
        String cohortId = "Q53::TASK_NATIVE_RUBRIC_V1::30";
        WritingTaskScoreCohort cohort = new WritingTaskScoreCohort(
                cohortId,
                "Q53",
                "TASK_NATIVE_RUBRIC_V1",
                null,
                BigDecimal.valueOf(30),
                base.overview().recentScoreFact());
        WritingTaskProgressSeam seam = new WritingTaskProgressSeam(
                "Q53",
                "Câu 53",
                ProgressAvailability.AVAILABLE,
                List.of(cohort),
                base.overview().allTimeWindow(),
                base.overview().coverage());
        ProgressPageState availableState =
                new ProgressPageState(ProgressAvailability.AVAILABLE, null, null);
        PracticeAnalytics analytics = new PracticeAnalytics(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(seam), base.analytics().writingAttemptCoverage(),
                base.analytics().recentDetailWindow(), availableState);
        PracticeProgressPageData page =
                new PracticeProgressPageData(base.overview(), analytics, availableState);
        when(progressService.getProgressPageData(7L, "Learner", "")).thenReturn(page);
        PracticeController controller =
                controller(progressService, userRepository, new ObjectMapper());
        ExtendedModelMap model = new ExtendedModelMap();

        controller.progress(
                principal,
                "test-practice",
                "WRITING",
                "Q53",
                cohortId,
                model);

        ProgressFilterState filter =
                (ProgressFilterState) model.get(PracticeModelAttributes.PROGRESS_FILTER);
        assertThat(filter.tab()).isEqualTo("test-practice");
        assertThat(filter.skill()).isEqualTo(ProgressSkillFilter.WRITING);
        assertThat(filter.writingTask()).isEqualTo(ProgressWritingTaskFilter.Q53);
        assertThat(filter.profileId()).isEqualTo(cohortId);
        assertThat(filter.profileOptions()).singleElement()
                .satisfies(option -> {
                    assertThat(option.id()).isEqualTo(cohortId);
                    assertThat(option.labelVi()).contains("Câu 53", "30 điểm");
                    assertThat(option.labelKo()).contains("53번", "30점");
                });
        PracticeAnalytics selected =
                (PracticeAnalytics) model.get(PracticeModelAttributes.ANALYTICS);
        assertThat(selected.writingTaskSeams()).singleElement()
                .satisfies(task -> assertThat(task.cohorts()).containsExactly(cohort));
    }

    @Test
    void activityWithNoSelectedSkillDataUsesTypedFilterEmptyState() {
        PracticeProgressService progressService = mock(PracticeProgressService.class);
        UserRepository userRepository = mock(UserRepository.class);
        KshUserDetails principal = mock(KshUserDetails.class);
        when(principal.getId()).thenReturn(7L);
        when(principal.getFullName()).thenReturn("Learner");
        when(userRepository.findById(7L)).thenReturn(Optional.empty());
        PracticeProgressPageData base = PracticeProgressPageData.unavailable(
                "Learner", "", ProgressExclusionReason.NO_ACTIVITY);
        LearningProgressOverview source = base.overview();
        LearningProgressOverview overview = new LearningProgressOverview(
                source.studentName(), source.avatarUrl(), source.currentLevel(),
                1, 0, source.totalPracticeMinutes(), source.recentAverageScore(),
                List.of(), source.heatmap(), source.recentHistory(),
                new ProgressAttemptCounts(1, 0, 1, 0),
                source.levelFact(), source.durationFact(), source.recentScoreFact(),
                source.allTimeWindow(), source.recentDetailWindow(), source.coverage());
        ProgressPageState availableState =
                new ProgressPageState(ProgressAvailability.AVAILABLE, null, null);
        PracticeAnalytics analytics = new PracticeAnalytics(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                base.analytics().writingAttemptCoverage(),
                base.analytics().recentDetailWindow(), availableState);
        when(progressService.getProgressPageData(7L, "Learner", ""))
                .thenReturn(new PracticeProgressPageData(
                        overview, analytics, availableState));
        PracticeController controller =
                controller(progressService, userRepository, new ObjectMapper());
        ExtendedModelMap model = new ExtendedModelMap();

        controller.progress(
                principal, "overview", "READING", "Q53", "raw", model);

        ProgressFilterState filter =
                (ProgressFilterState) model.get(PracticeModelAttributes.PROGRESS_FILTER);
        assertThat(filter.availability()).isEqualTo(ProgressAvailability.UNAVAILABLE);
        assertThat(filter.reason()).isEqualTo(ProgressExclusionReason.FILTER_NO_DATA);
        assertThat(filter.skill()).isEqualTo(ProgressSkillFilter.READING);
        assertThat(filter.writingTask()).isEqualTo(ProgressWritingTaskFilter.ALL);
        assertThat(filter.profileId()).isEqualTo("ALL");
    }

    @Test
    void partialObjectiveFactKeepsValueEarnedPossibleAndCoverageForPresentation()
            throws Exception {
        PracticeProgressService progressService = mock(PracticeProgressService.class);
        UserRepository userRepository = mock(UserRepository.class);
        KshUserDetails principal = mock(KshUserDetails.class);
        when(principal.getId()).thenReturn(7L);
        when(principal.getFullName()).thenReturn("Learner");
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        ProgressObservationWindow window = new ProgressObservationWindow(
                "ALL_TIME_READING",
                "Toàn bộ lịch sử Đọc",
                false,
                null,
                3,
                false,
                null,
                null,
                null,
                null);
        ProgressCoverage coverage = new ProgressCoverage(
                3,
                2,
                1,
                List.of(new ProgressExclusion(
                        ProgressExclusionReason.LEGACY_UNVERIFIED,
                        1)));
        ProgressNumericFact partial = new ProgressNumericFact(
                ProgressAvailability.PARTIAL,
                BigDecimal.valueOf(75),
                BigDecimal.valueOf(3),
                BigDecimal.valueOf(4),
                "PERCENTAGE",
                "OBJECTIVE_EARNED_OVER_POSSIBLE_V1",
                2,
                3,
                window,
                coverage);
        ProgressNumericFact partialWithoutValue = new ProgressNumericFact(
                ProgressAvailability.PARTIAL,
                null,
                BigDecimal.valueOf(3),
                BigDecimal.valueOf(4),
                "PERCENTAGE",
                "OBJECTIVE_EARNED_OVER_POSSIBLE_V1",
                2,
                3,
                window,
                coverage);
        assertThat(partial.renderableValue()).isTrue();
        assertThat(partial.partialCoverage()).isTrue();
        assertThat(partialWithoutValue.renderableValue()).isFalse();
        assertThat(partialWithoutValue.partialCoverage()).isFalse();

        PracticeProgressPageData empty = PracticeProgressPageData.unavailable(
                "Learner", "", ProgressExclusionReason.NO_ACTIVITY);
        ProgressAttemptCounts counts = new ProgressAttemptCounts(3, 3, 0, 0);
        SkillMetric reading = new SkillMetric(
                "READING",
                "Đọc",
                75.0,
                3,
                null,
                counts,
                partial,
                empty.overview().recentScoreFact(),
                window,
                coverage);
        ProgressPageState availableState =
                new ProgressPageState(ProgressAvailability.AVAILABLE, null, null);
        LearningProgressOverview overview = new LearningProgressOverview(
                "Learner",
                "",
                null,
                3,
                3,
                null,
                75.0,
                List.of(reading),
                List.of(),
                List.of(),
                counts,
                empty.overview().levelFact(),
                empty.overview().durationFact(),
                partial,
                window,
                window,
                coverage);
        PracticeAnalytics analytics = new PracticeAnalytics(
                List.of(reading),
                List.of(new ScoreTrendPoint(
                        "2026-07-25T10:00:00",
                        "READING",
                        75.0,
                        "Bài Đọc",
                        11L,
                        partial)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                empty.analytics().writingAttemptCoverage(),
                window,
                availableState);
        when(progressService.getProgressPageData(7L, "Learner", ""))
                .thenReturn(new PracticeProgressPageData(
                        overview,
                        analytics,
                        availableState));
        PracticeController controller =
                controller(progressService, userRepository, new ObjectMapper());
        ExtendedModelMap model = new ExtendedModelMap();

        controller.progress(
                principal,
                "overview",
                "READING",
                "ALL",
                "ALL",
                model);

        ProgressFilterState filter =
                (ProgressFilterState) model.get(PracticeModelAttributes.PROGRESS_FILTER);
        assertThat(filter.skill()).isEqualTo(ProgressSkillFilter.READING);
        assertThat(filter.writingTask()).isEqualTo(ProgressWritingTaskFilter.ALL);
        assertThat(filter.profileId()).isEqualTo("ALL");
        assertThat(filter.availability()).isEqualTo(ProgressAvailability.AVAILABLE);
        LearningProgressOverview presented =
                (LearningProgressOverview) model.get(PracticeModelAttributes.OVERVIEW);
        assertThat(presented.recentScoreFact()).isSameAs(partial);
        assertThat(presented.skillMetrics()).singleElement()
                .satisfies(metric -> {
                    assertThat(metric.scoreFact().availability())
                            .isEqualTo(ProgressAvailability.PARTIAL);
                    assertThat(metric.scoreFact().value())
                            .isEqualByComparingTo("75");
                    assertThat(metric.scoreFact().numerator())
                            .isEqualByComparingTo("3");
                    assertThat(metric.scoreFact().denominator())
                            .isEqualByComparingTo("4");
                    assertThat(metric.scoreFact().coverage())
                            .isEqualTo(coverage);
                });
        PracticeAnalytics presentedAnalytics =
                (PracticeAnalytics) model.get(PracticeModelAttributes.ANALYTICS);
        assertThat(presentedAnalytics.weeklySkillMetrics()).containsExactly(reading);
        assertThat(presentedAnalytics.scoreTrend()).singleElement()
                .satisfies(point -> assertThat(point.scoreFact()).isSameAs(partial));

        JsonNode overviewJson = new ObjectMapper().readTree(
                (String) model.get(PracticeModelAttributes.OVERVIEW_JSON));
        JsonNode scoreFact = overviewJson.path("skillMetrics").get(0).path("scoreFact");
        assertThat(scoreFact.path("availability").asText()).isEqualTo("PARTIAL");
        assertThat(scoreFact.path("value").decimalValue()).isEqualByComparingTo("75");
        assertThat(scoreFact.path("numerator").decimalValue()).isEqualByComparingTo("3");
        assertThat(scoreFact.path("denominator").decimalValue()).isEqualByComparingTo("4");
        assertThat(scoreFact.path("coverage").path("eligibleCount").asLong()).isEqualTo(2);
        assertThat(scoreFact.path("coverage").path("excludedCount").asLong()).isEqualTo(1);
        assertThat(scoreFact.path("coverage").path("exclusions").get(0)
                .path("reason").asText()).isEqualTo("LEGACY_UNVERIFIED");
        JsonNode analyticsJson = new ObjectMapper().readTree(
                (String) model.get(PracticeModelAttributes.ANALYTICS_JSON));
        JsonNode weeklyScoreFact = analyticsJson.path("weeklySkillMetrics")
                .get(0)
                .path("scoreFact");
        assertThat(weeklyScoreFact.path("availability").asText()).isEqualTo("PARTIAL");
        assertThat(weeklyScoreFact.path("numerator").decimalValue())
                .isEqualByComparingTo("3");
        assertThat(weeklyScoreFact.path("denominator").decimalValue())
                .isEqualByComparingTo("4");
        assertThat(weeklyScoreFact.path("coverage").path("eligibleCount").asLong())
                .isEqualTo(2);
        assertThat(weeklyScoreFact.path("coverage").path("excludedCount").asLong())
                .isEqualTo(1);
    }

    @Test
    void objectMapperFailureReplacesModelAndBothJsonPayloadsWithTypedUnavailableState()
            throws Exception {
        PracticeProgressService progressService = mock(PracticeProgressService.class);
        UserRepository userRepository = mock(UserRepository.class);
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        KshUserDetails principal = mock(KshUserDetails.class);
        when(principal.getId()).thenReturn(7L);
        when(principal.getFullName()).thenReturn("Learner");
        when(userRepository.findById(7L)).thenReturn(Optional.empty());
        PracticeProgressPageData base = PracticeProgressPageData.unavailable(
                "Learner", "", ProgressExclusionReason.PAGE_DATA_UNAVAILABLE);
        PracticeAnalytics staleAnalytics = new PracticeAnalytics(
                base.analytics().weeklySkillMetrics(),
                List.of(new ScoreTrendPoint(
                        "2026-07-25T01:00:00",
                        "READING",
                        100.0,
                        "Must be replaced",
                        99L,
                        null)),
                base.analytics().questionTypePerf(),
                base.analytics().highlights(),
                base.analytics().history(),
                base.analytics().writingTaskSeams(),
                base.analytics().writingAttemptCoverage(),
                base.analytics().recentDetailWindow(),
                new ProgressPageState(ProgressAvailability.AVAILABLE, null, null));
        when(progressService.getProgressPageData(7L, "Learner", ""))
                .thenReturn(new PracticeProgressPageData(
                        base.overview(),
                        staleAnalytics,
                        staleAnalytics.state()));
        when(failingMapper.writeValueAsString(any()))
                .thenThrow(new IllegalStateException("forced serialization failure"));

        PracticeController controller =
                controller(progressService, userRepository, failingMapper);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.progress(
                principal,
                "test-practice",
                "WRITING",
                "Q53",
                "not-a-canonical-profile",
                model);

        assertThat(view).isEqualTo("practice/progress");
        PracticeProgressPageData expected = PracticeProgressPageData.unavailable(
                "Learner",
                "",
                ProgressExclusionReason.SERIALIZATION_UNAVAILABLE);
        assertThat(model.get(PracticeModelAttributes.OVERVIEW))
                .isEqualTo(expected.overview());
        assertThat(model.get(PracticeModelAttributes.ANALYTICS))
                .isEqualTo(expected.analytics());
        assertThat(((PracticeAnalytics) model.get(PracticeModelAttributes.ANALYTICS))
                .scoreTrend()).isEmpty();
        assertThat(model.get(PracticeModelAttributes.PROGRESS_STATE))
                .isEqualTo(expected.state());
        ProgressFilterState filter =
                (ProgressFilterState) model.get(PracticeModelAttributes.PROGRESS_FILTER);
        assertThat(filter.tab()).isEqualTo("test-practice");
        assertThat(filter.skill()).isEqualTo(ProgressSkillFilter.WRITING);
        assertThat(filter.writingTask()).isEqualTo(ProgressWritingTaskFilter.Q53);
        assertThat(filter.profileId()).isEqualTo("ALL");
        assertThat(filter.availability()).isEqualTo(ProgressAvailability.UNAVAILABLE);
        assertThat(filter.reason())
                .isEqualTo(ProgressExclusionReason.SERIALIZATION_UNAVAILABLE);

        String overviewJson =
                (String) model.get(PracticeModelAttributes.OVERVIEW_JSON);
        String analyticsJson =
                (String) model.get(PracticeModelAttributes.ANALYTICS_JSON);
        assertThat(overviewJson).isNotEqualTo("{}");
        assertThat(analyticsJson).isNotEqualTo("{}");

        ObjectMapper parser = new ObjectMapper();
        JsonNode overview = parser.readTree(overviewJson);
        JsonNode analytics = parser.readTree(analyticsJson);
        assertThat(overview.path("totalPracticeMinutes").isNull()).isTrue();
        assertThat(overview.path("recentAverageScore").isNull()).isTrue();
        assertThat(overview.path("skillMetrics").isArray()).isTrue();
        assertThat(overview.path("skillMetrics").size()).isZero();
        assertThat(overview.path("heatmap").isArray()).isTrue();
        assertThat(overview.path("heatmap").size()).isZero();
        assertThat(overview.path("recentHistory").isArray()).isTrue();
        assertThat(overview.path("recentHistory").size()).isZero();
        assertThat(overview.path("attemptCounts").has("total")).isTrue();
        assertThat(overview.path("attemptCounts").path("total").asLong()).isZero();
        assertThat(overview.path("attemptCounts").has("completed")).isTrue();
        assertThat(overview.path("attemptCounts").path("completed").asLong()).isZero();
        assertThat(overview.path("attemptCounts").has("inProgress")).isTrue();
        assertThat(overview.path("attemptCounts").path("inProgress").asLong()).isZero();
        assertThat(overview.path("attemptCounts").has("other")).isTrue();
        assertThat(overview.path("attemptCounts").path("other").asLong()).isZero();
        assertThat(overview.path("coverage").path("exclusions").get(0)
                .path("reason").asText())
                .isEqualTo("SERIALIZATION_UNAVAILABLE");
        assertCompleteWindow(overview.path("allTimeWindow"));
        assertCompleteWindow(overview.path("recentDetailWindow"));
        assertCompleteLevelFact(
                overview.path("levelFact"), "SERIALIZATION_UNAVAILABLE");
        assertCompleteNumericFact(
                overview.path("durationFact"), "SERIALIZATION_UNAVAILABLE");
        assertCompleteNumericFact(
                overview.path("recentScoreFact"), "SERIALIZATION_UNAVAILABLE");
        assertThat(analytics.path("scoreTrend").isArray()).isTrue();
        assertThat(analytics.path("scoreTrend").size()).isZero();
        assertThat(analytics.path("weeklySkillMetrics").isArray()).isTrue();
        assertThat(analytics.path("weeklySkillMetrics").size()).isZero();
        assertThat(analytics.path("questionTypePerf").isArray()).isTrue();
        assertThat(analytics.path("questionTypePerf").size()).isZero();
        assertThat(analytics.path("highlights").isArray()).isTrue();
        assertThat(analytics.path("highlights").size()).isZero();
        assertThat(analytics.path("history").isArray()).isTrue();
        assertThat(analytics.path("history").size()).isZero();
        assertThat(analytics.path("writingTaskSeams").isArray()).isTrue();
        assertThat(analytics.path("writingTaskSeams").size()).isZero();
        assertCompleteCoverage(
                analytics.path("writingAttemptCoverage"),
                "SERIALIZATION_UNAVAILABLE");
        assertCompleteWindow(analytics.path("recentDetailWindow"));
        assertThat(analytics.path("state").path("availability").asText())
                .isEqualTo("UNAVAILABLE");
        assertThat(analytics.path("state").path("reason").asText())
                .isEqualTo("SERIALIZATION_UNAVAILABLE");
        assertThat(analytics.path("state").path("retryHint").asText())
                .isEqualTo("RELOAD");
    }

    @Test
    void pageDataFailureCreatesReloadableTypedPageStateAndCompleteJson()
            throws Exception {
        PracticeProgressService progressService = mock(PracticeProgressService.class);
        UserRepository userRepository = mock(UserRepository.class);
        KshUserDetails principal = mock(KshUserDetails.class);
        when(principal.getId()).thenReturn(7L);
        when(principal.getFullName()).thenReturn("Learner");
        when(userRepository.findById(7L)).thenReturn(Optional.empty());
        when(progressService.getProgressPageData(7L, "Learner", ""))
                .thenThrow(new IllegalStateException("forced page-data failure"));
        PracticeController controller =
                controller(progressService, userRepository, new ObjectMapper());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.progress(principal, "overview", model);

        assertThat(view).isEqualTo("practice/progress");
        ProgressPageState state =
                (ProgressPageState) model.get(PracticeModelAttributes.PROGRESS_STATE);
        assertThat(state.availability()).isEqualTo(ProgressAvailability.UNAVAILABLE);
        assertThat(state.reason())
                .isEqualTo(ProgressExclusionReason.PAGE_DATA_UNAVAILABLE);
        assertThat(state.retryHint()).isEqualTo("RELOAD");
        JsonNode overview = new ObjectMapper().readTree(
                (String) model.get(PracticeModelAttributes.OVERVIEW_JSON));
        JsonNode analytics = new ObjectMapper().readTree(
                (String) model.get(PracticeModelAttributes.ANALYTICS_JSON));
        assertCompleteWindow(overview.path("allTimeWindow"));
        assertCompleteWindow(overview.path("recentDetailWindow"));
        assertCompleteNumericFact(
                overview.path("durationFact"), "PAGE_DATA_UNAVAILABLE");
        assertCompleteNumericFact(
                overview.path("recentScoreFact"), "PAGE_DATA_UNAVAILABLE");
        assertThat(analytics.path("scoreTrend").size()).isZero();
        assertThat(analytics.path("history").size()).isZero();
        assertCompleteCoverage(
                analytics.path("writingAttemptCoverage"),
                "PAGE_DATA_UNAVAILABLE");
        assertCompleteWindow(analytics.path("recentDetailWindow"));
        assertThat(analytics.path("state").path("reason").asText())
                .isEqualTo("PAGE_DATA_UNAVAILABLE");
        assertThat(analytics.path("state").path("retryHint").asText())
                .isEqualTo("RELOAD");
    }

    private PracticeController controller(
            PracticeProgressService progressService,
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        return new PracticeController(
                mock(PracticeService.class),
                progressService,
                mock(PracticeCatalogService.class),
                mock(PracticeDetailPageService.class),
                mock(PracticeLearnerAccessService.class),
                mock(PracticeAttemptDiscardService.class),
                mock(PracticeSpeakingMediaService.class),
                mock(PracticeResultAssembler.class),
                mock(PracticeResultDetailAssembler.class),
                userRepository,
                objectMapper,
                mock(PracticeSectionRepository.class),
                false,
                false);
    }

    private static void assertCompleteLevelFact(JsonNode fact, String reason) {
        assertThat(fact.path("availability").asText()).isEqualTo("UNAVAILABLE");
        assertThat(fact.has("value")).isTrue();
        assertThat(fact.path("value").isNull()).isTrue();
        assertThat(fact.has("profileId")).isTrue();
        assertThat(fact.path("profileId").isNull()).isTrue();
        assertCompleteWindow(fact.path("observationWindow"));
        assertCompleteCoverage(fact.path("coverage"), reason);
    }

    private static void assertCompleteNumericFact(JsonNode fact, String reason) {
        assertThat(fact.path("availability").asText()).isEqualTo("UNAVAILABLE");
        assertThat(fact.has("value")).isTrue();
        assertThat(fact.path("value").isNull()).isTrue();
        assertThat(fact.has("numerator")).isTrue();
        assertThat(fact.path("numerator").isNull()).isTrue();
        assertThat(fact.has("denominator")).isTrue();
        assertThat(fact.path("denominator").isNull()).isTrue();
        assertThat(fact.has("unit")).isTrue();
        assertThat(fact.path("unit").isNull()).isTrue();
        assertThat(fact.has("profileId")).isTrue();
        assertThat(fact.path("profileId").isNull()).isTrue();
        assertThat(fact.has("sampleSize")).isTrue();
        assertThat(fact.path("sampleSize").asLong()).isZero();
        assertThat(fact.has("activityCount")).isTrue();
        assertThat(fact.path("activityCount").asLong()).isZero();
        assertCompleteWindow(fact.path("observationWindow"));
        assertCompleteCoverage(fact.path("coverage"), reason);
    }

    private static void assertCompleteWindow(JsonNode window) {
        assertThat(window.path("code").asText()).isEqualTo("UNAVAILABLE");
        assertThat(window.path("label").asText())
                .isEqualTo("Dữ liệu tiến độ chưa khả dụng");
        assertThat(window.has("bounded")).isTrue();
        assertThat(window.path("bounded").asBoolean()).isTrue();
        assertThat(window.has("limit")).isTrue();
        assertThat(window.path("limit").asInt()).isZero();
        assertThat(window.has("returnedCount")).isTrue();
        assertThat(window.path("returnedCount").asLong()).isZero();
        assertThat(window.has("truncated")).isTrue();
        assertThat(window.path("truncated").asBoolean()).isFalse();
        assertThat(window.has("observedFrom")).isTrue();
        assertThat(window.path("observedFrom").isNull()).isTrue();
        assertThat(window.has("observedTo")).isTrue();
        assertThat(window.path("observedTo").isNull()).isTrue();
        assertThat(window.has("asOf")).isTrue();
        assertThat(window.path("asOf").isNull()).isTrue();
        assertThat(window.has("lastObservedAt")).isTrue();
        assertThat(window.path("lastObservedAt").isNull()).isTrue();
    }

    private static void assertCompleteCoverage(JsonNode coverage, String reason) {
        assertThat(coverage.has("activityCount")).isTrue();
        assertThat(coverage.path("activityCount").asLong()).isZero();
        assertThat(coverage.has("eligibleCount")).isTrue();
        assertThat(coverage.path("eligibleCount").asLong()).isZero();
        assertThat(coverage.has("excludedCount")).isTrue();
        assertThat(coverage.path("excludedCount").asLong()).isZero();
        assertThat(coverage.path("exclusions").isArray()).isTrue();
        assertThat(coverage.path("exclusions").size()).isEqualTo(1);
        assertThat(coverage.path("exclusions").get(0).path("reason").asText())
                .isEqualTo(reason);
        assertThat(coverage.path("exclusions").get(0).path("activityCount").asLong())
                .isZero();
    }
}
