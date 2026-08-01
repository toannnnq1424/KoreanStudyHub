package com.ksh.features.practice.fixtures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.WritingTaskType;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.ObjectiveExplanationStrategyRegistry;
import com.ksh.features.practice.assessment.WritingBlankContract;
import com.ksh.features.practice.assessment.WritingBlankContractVerifier;
import com.ksh.features.practice.service.PracticeAttemptAnswerCodec;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticePre14UiAcceptanceScenarioManifestTest {

    private static final Set<String> LEGACY_ALIASES = Set.of(
            "EVIDENCE_ONLY",
            "ELIMINATE_ALL_INCORRECT",
            "FULL_CONTEXT_THEN_ANSWER",
            "HYBRID",
            "CLAIM_EVIDENCE_RELATION",
            "CONSTRAINTS_AND_EVIDENCE");
    private static final Set<String> PREMIUM_SPEAKING_SUBCRITERIA =
            Set.of(
                    "S_CONTENT_RELEVANCE",
                    "S_CONTENT_PROMPT_COVERAGE",
                    "S_CONTENT_SPECIFICITY_EXAMPLES",
                    "S_GRAMMAR_PARTICLES",
                    "S_GRAMMAR_TENSE_ASPECT",
                    "S_GRAMMAR_ENDINGS",
                    "S_GRAMMAR_SENTENCE_STRUCTURE",
                    "S_GRAMMAR_HONORIFIC_REGISTER",
                    "S_GRAMMAR_CONNECTORS",
                    "S_VOCAB_TOPIC_WORDS",
                    "S_VOCAB_NATURAL_EXPRESSIONS",
                    "S_VOCAB_REPETITION_CONTROL",
                    "S_VOCAB_WORD_CHOICE",
                    "S_COHERENCE_ORGANIZATION",
                    "S_COHERENCE_LOGICAL_FLOW",
                    "S_COHERENCE_DISCOURSE_MARKERS");

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void manifestCoversEveryRequiredDimensionWithoutSecrets() throws Exception {
        JsonNode root = readManifest();

        assertThat(root.path("schemaVersion").asText())
                .isEqualTo("practice-pre14-ui-acceptance-scenarios-v3");
        assertThat(root.path("fixtureAuthority").asText())
                .isEqualTo("DEV_TEST_ONLY");
        assertThat(root.path("safety").path("productionMigration").asBoolean())
                .isFalse();
        assertThat(root.path("safety").path("realProviderCalls").asInt())
                .isZero();
        assertThat(root.path("safety").path("containsSecrets").asBoolean())
                .isFalse();

        assertStrategyRegistryAndCompatibilityMatrix(root);
        assertBrowserObjectiveScenarios(root);
        assertObjectiveInteractionCapabilities(root);
        assertWritingAndSpeakingScenarios(root);
        assertWritingBlankContractScenarios(root);
        assertLecturerScenarios(root);

        String serialized = mapper.writeValueAsString(root);
        assertThat(serialized.toLowerCase())
                .doesNotContain("password", "api_key", "apikey", "bearer ");
        LEGACY_ALIASES.forEach(alias ->
                assertThat(serialized)
                        .doesNotContain("\"strategy\":\"" + alias + "\""));
    }

    private void assertStrategyRegistryAndCompatibilityMatrix(JsonNode root) {
        JsonNode registry = root.path("objectiveStrategyRegistry");
        assertThat(registry.path("registryVersion").asText())
                .isEqualTo(ObjectiveExplanationStrategyRegistry
                        .CURRENT_REGISTRY_VERSION);
        assertThat(registry.path("strategyVersion").asText())
                .isEqualTo(ObjectiveExplanationStrategyRegistry
                        .STRATEGY_VERSION);

        Set<String> productionActive = new LinkedHashSet<>();
        Set<String> productionBlocked = new LinkedHashSet<>();
        ObjectiveExplanationStrategyRegistry.catalog().forEach(entry -> {
            if (entry.selectable()) {
                productionActive.add(entry.code().name());
            } else {
                productionBlocked.add(entry.code().name());
                assertThat(entry.unsupportedReason()).isNotBlank();
            }
        });
        assertThat(productionActive).hasSize(11);
        assertThat(textValues(registry.path("active")))
                .containsExactlyInAnyOrderElementsOf(productionActive);
        assertThat(productionBlocked).hasSize(9);
        assertThat(values(registry.path("blocked"), "code"))
                .containsExactlyInAnyOrderElementsOf(productionBlocked);
        registry.path("blocked").forEach(entry ->
                assertThat(entry.path("reason").asText()).isNotBlank());

        JsonNode matrix = root.path("objectiveCompatibilityMatrix");
        assertThat(textValues(matrix.path("skills")))
                .containsExactlyInAnyOrder("READING", "LISTENING");
        assertThat(textValues(matrix.path("sourceModes")))
                .containsExactlyInAnyOrder(
                        "GROUP_SOURCE", "STANDALONE_NO_PASSAGE");

        int expandedCells = 0;
        Set<String> cellIdentities = new HashSet<>();
        JsonNode byType = matrix.path("strategiesByQuestionType");
        for (CanonicalQuestionType type : Set.of(
                CanonicalQuestionType.SINGLE_CHOICE,
                CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN,
                CanonicalQuestionType.FILL_BLANK,
                CanonicalQuestionType.MULTIPLE_ANSWER,
                CanonicalQuestionType.MATCHING)) {
            Set<String> manifestStrategies =
                    textValues(byType.path(type.name()));
            Set<String> productionStrategies = new LinkedHashSet<>();
            ObjectiveExplanationStrategyRegistry.options(type).forEach(
                    option -> productionStrategies.add(option.code()));
            assertThat(manifestStrategies)
                    .containsExactlyInAnyOrderElementsOf(
                            productionStrategies);
            for (String skill : textValues(matrix.path("skills"))) {
                for (String sourceMode :
                        textValues(matrix.path("sourceModes"))) {
                    for (String strategy : manifestStrategies) {
                        ObjectiveExplanationStrategyRegistry.requireSelection(
                                type,
                                ObjectiveExplanationStrategyRegistry
                                        .CURRENT_REGISTRY_VERSION,
                                strategy,
                                ObjectiveExplanationStrategyRegistry
                                        .STRATEGY_VERSION);
                        assertThat(cellIdentities.add(
                                skill + "|" + sourceMode + "|"
                                        + type + "|" + strategy))
                                .isTrue();
                        expandedCells++;
                    }
                }
            }
        }
        assertThat(expandedCells).isEqualTo(128);
        assertThat(cellIdentities).hasSize(128);
        assertThat(matrix.path("expectedExpandedCellCount").asInt())
                .isEqualTo(128);
        assertThat(root.path("counts")
                .path("objectiveCompatibilityCells").asInt()).isEqualTo(128);
    }

    private void assertBrowserObjectiveScenarios(JsonNode root) {
        JsonNode objective = root.path("objectiveScenarios");
        assertThat(objective).hasSize(28);
        Set<String> ids = new HashSet<>();
        Set<Long> questionIds = new HashSet<>();
        for (JsonNode scenario : objective) {
            assertThat(ids.add(scenario.path("id").asText())).isTrue();
            assertThat(questionIds.add(
                    scenario.path("questionId").asLong())).isTrue();
            CanonicalQuestionType type = CanonicalQuestionType.valueOf(
                    scenario.path("questionType").asText());
            ObjectiveExplanationStrategyRegistry.requireSelection(
                    type,
                    ObjectiveExplanationStrategyRegistry
                            .CURRENT_REGISTRY_VERSION,
                    scenario.path("strategy").asText(),
                    ObjectiveExplanationStrategyRegistry.STRATEGY_VERSION);
            assertThat(scenario.path("overviewUrl").asText())
                    .isEqualTo(
                            "/practice/attempts/"
                                    + scenario.path("attemptId").asLong()
                                    + "/result");
            assertThat(scenario.path("detailUrl").asText())
                    .isEqualTo(
                            "/practice/attempts/"
                                    + scenario.path("attemptId").asLong()
                                    + "/result/detail");
            assertThat(scenario.path("anchor").asText())
                    .isEqualTo(
                            "#objective-question-"
                                    + scenario.path("questionId").asLong());
        }
        Set<Long> expectedQuestionIds = new HashSet<>();
        for (long id = 14101; id <= 14114; id++) {
            expectedQuestionIds.add(id);
        }
        for (long id = 14201; id <= 14214; id++) {
            expectedQuestionIds.add(id);
        }
        assertThat(questionIds)
                .containsExactlyInAnyOrderElementsOf(expectedQuestionIds);
        assertThat(values(objective, "skill"))
                .containsExactlyInAnyOrder("READING", "LISTENING");
        assertThat(values(objective, "sourceMode"))
                .containsExactlyInAnyOrder(
                        "GROUP_SOURCE", "STANDALONE_NO_PASSAGE");
        assertThat(values(objective, "questionType"))
                .containsExactlyInAnyOrder(
                        "SINGLE_CHOICE",
                        "TRUE_FALSE_NOT_GIVEN",
                        "FILL_BLANK",
                        "MULTIPLE_ANSWER",
                        "MATCHING");
        assertThat(values(objective, "strategy"))
                .containsExactlyInAnyOrderElementsOf(textValues(
                        root.path("objectiveStrategyRegistry")
                                .path("active")));
        assertThat(values(objective, "answerState"))
                .containsExactlyInAnyOrder(
                        "SELECTED_CORRECT",
                        "SELECTED_WRONG",
                        "CORRECT_NOT_SELECTED",
                        "UNANSWERED");

        for (String skill : Set.of("READING", "LISTENING")) {
            for (String sourceMode : Set.of(
                    "GROUP_SOURCE", "STANDALONE_NO_PASSAGE")) {
                Set<String> types = new HashSet<>();
                objective.forEach(scenario -> {
                    if (skill.equals(scenario.path("skill").asText())
                            && sourceMode.equals(
                            scenario.path("sourceMode").asText())) {
                        types.add(scenario.path("questionType").asText());
                    }
                });
                assertThat(types).contains(
                        "SINGLE_CHOICE",
                        "TRUE_FALSE_NOT_GIVEN",
                        "FILL_BLANK");
                if ("GROUP_SOURCE".equals(sourceMode)) {
                    assertThat(types).contains("MATCHING");
                } else {
                    assertThat(types).contains("MULTIPLE_ANSWER");
                }
            }
        }
    }

    private void assertObjectiveInteractionCapabilities(JsonNode root) {
        JsonNode capabilities = root.path("objectiveInteractionCapabilities");
        assertThat(capabilities).hasSize(4);
        assertThat(values(capabilities, "code"))
                .containsExactlyInAnyOrder(
                        "MULTIPLE_ANSWER",
                        "MATCHING",
                        "PINNED_SHARED_MATERIAL",
                        "LOCAL_HELPER_DRAWER");
        capabilities.forEach(capability -> {
            assertThat(capability.path("status").asText())
                    .isEqualTo("AVAILABLE");
            assertThat(capability.path("authority").asText())
                    .isNotBlank();
        });

        JsonNode player = root.path("objectivePlayerScenario");
        assertThat(player.path("attemptId").asLong()).isEqualTo(14800L);
        assertThat(player.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(player.path("url").asText())
                .isEqualTo("/practice/attempts/14800");
        assertThat(player.path("questionIds")).hasSize(14);
        assertThat(textValues(player.path("requiredCapabilities")))
                .containsExactlyInAnyOrder(
                        "MULTIPLE_ANSWER",
                        "MATCHING",
                        "PINNED_SHARED_MATERIAL");
        assertThat(root.path("counts")
                .path("objectivePlayerAttempts").asInt()).isEqualTo(1);
    }

    private void assertWritingAndSpeakingScenarios(JsonNode root) {
        JsonNode writing = root.path("writingScenarios");
        assertThat(writing).hasSize(6);
        assertThat(values(writing, "case"))
                .containsExactlyInAnyOrder(
                        "MIXED",
                        "NO_DIAGNOSTIC",
                        "PARTIAL",
                        "FULL",
                        "PENDING",
                        "UNAVAILABLE");
        for (JsonNode scenario : writing) {
            assertThat(scenario.path("overviewUrl").asText())
                    .isEqualTo(
                            "/practice/attempts/"
                                    + scenario.path("attemptId").asLong()
                                    + "/result");
            assertThat(scenario.path("questions")).hasSize(4);
            assertThat(values(scenario.path("questions"), "task"))
                    .containsExactlyInAnyOrder("Q51", "Q52", "Q53", "Q54");
            for (JsonNode question : scenario.path("questions")) {
                assertThat(question.path("detailUrl").asText())
                        .contains("/result/detail?questionId=");
                assertThat(question.path("responseAuthority").asText())
                        .isNotBlank();
            }
        }
        JsonNode mixed = findByValue(writing, "case", "MIXED");
        assertThat(findByValue(mixed.path("questions"), "task", "Q53")
                .path("rawScore").asInt()).isEqualTo(28);
        assertThat(findByValue(mixed.path("questions"), "task", "Q53")
                .path("performanceLevel").asText()).isEqualTo("GOOD");
        assertThat(findByValue(mixed.path("questions"), "task", "Q54")
                .path("rawScore").asInt()).isEqualTo(41);
        assertThat(findByValue(mixed.path("questions"), "task", "Q54")
                .path("performanceLevel").asText()).isEqualTo("GOOD");

        JsonNode premiumWritingContract =
                root.path("premiumWritingContract");
        assertThat(textValues(
                premiumWritingContract.path("requiredTabs")))
                .containsExactly(
                        "Tổng quan",
                        "Điểm mạnh",
                        "Cần cải thiện",
                        "Bài nâng cấp",
                        "Mẫu");
        assertThat(premiumWritingContract.path("teacherSample")
                .path("contractVersion").asText())
                .isEqualTo("ksh-teacher-sample-v1");
        assertThat(premiumWritingContract.path("teacherSample")
                .path("source").asText())
                .isEqualTo("TEACHER_AUTHORED");
        assertThat(premiumWritingContract.path("teacherSample")
                .path("authorRole").asText())
                .isEqualTo("LECTURER");
        assertThat(textValues(premiumWritingContract.path(
                "q51StrengthFeaturesPerBlank")))
                .containsExactlyInAnyOrder(
                        "W_ACCURATE_SPELLING_SPACING",
                        "W_FORMAL_REGISTER_CONSISTENCY",
                        "W_FORMAL_VOCABULARY_USAGE",
                        "W_NATURAL_KOREAN_EXPRESSIONS",
                        "W_CLOZE_CONTEXT_FIT",
                        "W_CONNECTIVE_ENDING_ACCURACY",
                        "W_SENTENCE_COMPLETION_NATURALNESS");
        assertThat(textValues(premiumWritingContract.path(
                "q52ImprovementFeaturesPerBlank")))
                .containsExactlyInAnyOrder(
                        "W_CLOZE_GRAMMAR_COMPATIBILITY",
                        "W_CLOZE_REGISTER_MATCH",
                        "W_VOCABULARY_ERRORS",
                        "W_GRAMMAR_ERRORS",
                        "W_PARTICLE_ERRORS",
                        "W_AWKWARD_UNNATURAL_EXPRESSIONS",
                        "W_SENTENCE_STRUCTURE_ISSUES",
                        "W_REGISTER_CONSISTENCY_ISSUES",
                        "W_SPELLING_SPACING_ERRORS");
        assertThat(textValues(premiumWritingContract.path(
                "structuredBlankTargets").path("Q51")))
                .containsExactly("q51-b1", "q51-b2");
        assertThat(textValues(premiumWritingContract.path(
                "structuredBlankTargets").path("Q52")))
                .containsExactly("q52-b1", "q52-b2");
        assertThat(textValues(premiumWritingContract.path(
                "q53StrengthFeatures"))).hasSize(12);
        assertThat(textValues(premiumWritingContract.path(
                "q54ImprovementFeatures"))).hasSize(14);
        assertThat(textValues(premiumWritingContract.path(
                "q54UniqueStrengthFeatures")))
                .containsExactlyInAnyOrder(
                        "W_CLEAR_THESIS_OR_MAIN_IDEA",
                        "W_RELEVANT_EXAMPLES_OR_REASONS");
        assertThat(textValues(premiumWritingContract.path(
                "q53UniqueImprovementFeatures")))
                .containsExactlyInAnyOrder(
                        "W_TASK_REQUIREMENT_MISSING",
                        "W_Q53_DATA_FLOW_ISSUES");

        JsonNode premiumWriting =
                root.path("premiumWritingScenarios");
        Map<String, Integer> premiumWritingCounts = Map.of(
                "Q51_ALL_STRENGTHS", 14,
                "Q52_ALL_IMPROVEMENTS", 18,
                "Q53_ALL_STRENGTHS", 12,
                "Q54_ALL_IMPROVEMENTS", 14,
                "Q54_UNIQUE_STRENGTHS", 2,
                "Q53_UNIQUE_IMPROVEMENTS", 2);
        Map<String, Integer> premiumWritingPercentages = Map.of(
                "Q51_ALL_STRENGTHS", 100,
                "Q52_ALL_IMPROVEMENTS", 90,
                "Q53_ALL_STRENGTHS", 100,
                "Q54_ALL_IMPROVEMENTS", 43,
                "Q54_UNIQUE_STRENGTHS", 100,
                "Q53_UNIQUE_IMPROVEMENTS", 72);
        assertThat(premiumWriting).hasSize(
                premiumWritingCounts.size());
        assertThat(values(premiumWriting, "case"))
                .containsExactlyInAnyOrderElementsOf(
                        premiumWritingCounts.keySet());
        int expectedWritingChips = 0;
        for (JsonNode scenario : premiumWriting) {
            String fixtureCase = scenario.path("case").asText();
            int expectedChips = scenario.path(
                    "expectedChipCount").asInt();
            expectedWritingChips += expectedChips;
            assertThat(expectedChips)
                    .isEqualTo(premiumWritingCounts.get(fixtureCase));
            assertThat(scenario.path("expectedPercentage").asInt())
                    .isEqualTo(
                            premiumWritingPercentages.get(fixtureCase));
            assertThat(scenario.path("expectedOperations")).isNotEmpty();
            assertThat(scenario.path("teacherSampleRequired").asBoolean())
                    .isTrue();
            long attemptId = scenario.path("attemptId").asLong();
            long questionId = scenario.path("questionId").asLong();
            assertThat(scenario.path("overviewUrl").asText())
                    .isEqualTo(
                            "/practice/attempts/" + attemptId + "/result");
            assertThat(scenario.path("detailUrl").asText())
                    .isEqualTo(
                            "/practice/attempts/" + attemptId
                                    + "/result/detail?questionId="
                                    + questionId);
        }
        assertThat(expectedWritingChips).isEqualTo(62);
        assertThat(root.path("counts").path(
                "premiumWritingAttempts").asInt()).isEqualTo(6);
        assertThat(root.path("counts").path(
                "premiumWritingExpectedChipInstances").asInt())
                .isEqualTo(62);

        assertThat(root.path("speakingScenarios")).hasSize(2);
        root.path("speakingScenarios").forEach(scenario ->
                assertThat(scenario.path("acousticStatus").asText())
                        .isEqualTo("NOT_SCORABLE"));

        JsonNode premiumContract =
                root.path("premiumSpeakingContract");
        assertThat(textValues(
                premiumContract.path("transcriptGroundedSubcriteria")))
                .containsExactlyInAnyOrderElementsOf(
                        PREMIUM_SPEAKING_SUBCRITERIA);
        assertThat(premiumContract.path(
                "withoutVerifiedAudio").asText())
                .isEqualTo("NOT_SCORABLE");
        assertThat(textValues(
                premiumContract.path("acousticCriteria")))
                .containsExactlyInAnyOrder(
                        "S_FLUENCY",
                        "S_PRONUNCIATION_DELIVERY");
        assertThat(premiumContract.path("overviewCapabilities")
                .path("HOLISTIC_SCORE").asText())
                .isEqualTo("NOT_SCORABLE");
        assertThat(premiumContract.path("overviewCapabilities")
                .path("CRITERION_RADAR").asText())
                .isEqualTo("AVAILABLE");
        assertThat(premiumContract.path("overviewCapabilities")
                .path("PART_PERFORMANCE").asText())
                .isEqualTo("AVAILABLE");
        assertThat(premiumContract.path("overviewCapabilities")
                .path("NAMED_CRITERION_SUBMETRICS").asText())
                .isEqualTo("AVAILABLE");
        assertThat(textValues(premiumContract.path("requiredTabs")))
                .containsExactly(
                        "Tổng quan",
                        "Điểm mạnh",
                        "Cần cải thiện",
                        "Bài nâng cấp",
                        "Mẫu");
        assertThat(premiumContract.path("teacherSample")
                .path("contractVersion").asText())
                .isEqualTo("ksh-speaking-teacher-sample-v1");
        assertThat(premiumContract.path("teacherSample")
                .path("source").asText())
                .isEqualTo("TEACHER_AUTHORED");
        assertThat(premiumContract.path("teacherSample")
                .path("authorRole").asText())
                .isEqualTo("LECTURER");
        assertThat(premiumContract.path("teacherSample")
                .path("persistedField").asText())
                .isEqualTo("speaking_teacher_samples_by_question");

        JsonNode premium = root.path("premiumSpeakingScenarios");
        assertThat(premium).hasSize(4);
        assertThat(values(premium, "case"))
                .containsExactlyInAnyOrder(
                        "PREMIUM_ALL_STRENGTHS",
                        "PREMIUM_ALL_IMPROVEMENTS",
                        "PREMIUM_IMPROVEMENTS_COMPANION",
                        "PREMIUM_STRENGTHS_COMPANION");
        premium.forEach(scenario -> {
            long attemptId = scenario.path("attemptId").asLong();
            long questionId = scenario.path("questionId").asLong();
            assertThat(scenario.path("expectedChipCount").asInt())
                    .isEqualTo(PREMIUM_SPEAKING_SUBCRITERIA.size());
            assertThat(scenario.path("expectedFindingCount").asInt())
                    .isEqualTo(
                            PREMIUM_SPEAKING_SUBCRITERIA.size()
                                    + ("STRENGTH".equals(
                                    scenario.path("expectedPolarity").asText())
                                    ? 1 : 0));
            assertThat(scenario.path("repeatedFeature").asText())
                    .contains("S_VOCAB_REPETITION_CONTROL");
            assertThat(scenario.path("expectedFrequency").asInt())
                    .isEqualTo(2);
            if ("NEEDS_IMPROVEMENT".equals(
                    scenario.path("expectedPolarity").asText())) {
                assertThat(scenario.path(
                        "expectedRepeatedOperation").asText())
                        .isEqualTo("REDUNDANT");
            }
            assertThat(scenario.path("overviewUrl").asText())
                    .isEqualTo(
                            "/practice/attempts/" + attemptId + "/result");
            assertThat(scenario.path("detailUrl").asText())
                    .isEqualTo(
                            "/practice/attempts/" + attemptId
                                    + "/result/detail?questionId="
                                    + questionId);
            assertThat(scenario.path("acousticStatus").asText())
                    .isEqualTo("NOT_SCORABLE");
            assertThat(scenario.path(
                    "teacherSampleRequired").asBoolean()).isTrue();
        });
    }

    @Test
    void structuredWritingBlankFixturesAreTaskNativeAndFailClosed()
            throws Exception {
        WritingBlankContract.QuestionResponse question =
                structuredQuestion(WritingTaskType.Q51);
        WritingBlankContract.AnswerAuthority authority =
                structuredAuthority(WritingTaskType.Q51);
        WritingBlankContractVerifier.verifyAuthority(question, authority);

        WritingBlankContract.LearnerResponse exact =
                learner(
                        WritingTaskType.Q51,
                        "도서관에서 공부합니다",
                        "주 2회/온라인; 복습");
        WritingBlankContract.LearnerResponse alternative =
                learner(
                        WritingTaskType.Q51,
                        "도서관에서 학습합니다",
                        "월·수 저녁에 복습합니다");
        WritingBlankContractVerifier.verifyLearnerResponse(question, exact);
        WritingBlankContractVerifier.verifyLearnerResponse(
                question, alternative);

        WritingBlankContractVerifier.verifyEvaluation(
                question,
                evaluation(
                        WritingTaskType.Q51,
                        WritingBlankContract.Verdict.CORRECT,
                        WritingBlankContract.Verdict.CORRECT));
        WritingBlankContractVerifier.verifyEvaluation(
                question,
                evaluation(
                        WritingTaskType.Q51,
                        WritingBlankContract.Verdict.PARTIAL,
                        WritingBlankContract.Verdict.INCORRECT));
        WritingBlankContractVerifier.verifyEvaluation(
                question,
                evaluation(
                        WritingTaskType.Q51,
                        WritingBlankContract.Verdict.EMPTY,
                        WritingBlankContract.Verdict.CORRECT));

        WritingBlankContract.LearnerResponse swapped =
                new WritingBlankContract.LearnerResponse(
                        WritingBlankContract.LEARNER_SCHEMA_VERSION,
                        WritingTaskType.Q51,
                        WritingBlankContract.RESPONSE_MODE,
                        List.of(
                                new WritingBlankContract.LearnerBlankAnswer(
                                        "q51-b2", "두 번째"),
                                new WritingBlankContract.LearnerBlankAnswer(
                                        "q51-b1", "첫 번째")));
        assertThatThrownBy(() ->
                WritingBlankContractVerifier.verifyLearnerResponse(
                        question, swapped))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("swapped");

        PracticeAttemptAnswerCodec codec =
                new PracticeAttemptAnswerCodec(mapper);
        PracticeAttemptAnswerCodec.DecodedAnswers current =
                new PracticeAttemptAnswerCodec.DecodedAnswers(
                        Map.of(),
                        Map.of("14351", exact),
                        false,
                        false);
        PracticeAttemptAnswerCodec.DecodedAnswers decoded =
                codec.read(codec.write(current), Map.of(14351L, question));
        assertThat(decoded.historicalDocument()).isFalse();
        assertThat(decoded.legacyEssayShape()).isFalse();
        assertThat(decoded.writingBlankAnswers()).containsKey("14351");

        PracticeAttemptAnswerCodec.DecodedAnswers legacy =
                codec.read(
                        "{\"14351\":\"Không tự ý tách / hay ; trong bài cũ\"}",
                        Map.of(14351L, question));
        assertThat(legacy.historicalDocument()).isTrue();
        assertThat(legacy.legacyEssayShape()).isTrue();
        assertThat(legacy.textAnswers()).containsEntry(
                "14351",
                "Không tự ý tách / hay ; trong bài cũ");
    }

    private void assertWritingBlankContractScenarios(JsonNode root) {
        JsonNode scenarios = root.path("writingBlankContractScenarios");
        assertThat(scenarios).hasSize(10);
        assertThat(values(scenarios, "case"))
                .containsExactlyInAnyOrder(
                        "EXACT_ACCEPTED",
                        "ALTERNATIVE_ACCEPTED",
                        "NEAR_MISS_PARTIAL",
                        "INCORRECT",
                        "EMPTY",
                        "SWAPPED_FAIL_CLOSED",
                        "MULTIPLE_ACCEPTED_VARIANTS",
                        "LITERAL_SLASH",
                        "LITERAL_SEMICOLON",
                        "LEGACY_ESSAY_READ_ONLY");
        scenarios.forEach(scenario -> {
            assertThat(scenario.path("taskType").asText())
                    .isIn("Q51", "Q52");
            assertThat(scenario.path("responseMode").asText())
                    .isIn(
                            WritingBlankContract.RESPONSE_MODE,
                            WritingBlankContract
                                    .AUTHORING_MODE_LEGACY_READ_ONLY);
            assertThat(scenario.path("expectedDisposition").asText())
                    .isIn(
                            "ACCEPT",
                            "FAIL_CLOSED",
                            "READ_ONLY_COMPATIBILITY");
        });
        assertThat(root.path("counts")
                .path("writingBlankContractScenarios").asInt())
                .isEqualTo(10);
    }

    private static WritingBlankContract.QuestionResponse structuredQuestion(
            WritingTaskType taskType) {
        String prefix = taskType.name().toLowerCase();
        return new WritingBlankContract.QuestionResponse(
                WritingBlankContract.RESPONSE_SCHEMA_VERSION,
                WritingBlankContract.RESPONSE_MODE,
                taskType,
                List.of(
                        new WritingBlankContract.BlankDefinition(
                                prefix + "-b1", 1,
                                "첫 번째 문맥을 완성하십시오."),
                        new WritingBlankContract.BlankDefinition(
                                prefix + "-b2", 2,
                                "두 번째 문맥을 완성하십시오.")));
    }

    private static WritingBlankContract.AnswerAuthority structuredAuthority(
            WritingTaskType taskType) {
        String prefix = taskType.name().toLowerCase();
        return new WritingBlankContract.AnswerAuthority(
                WritingBlankContract.AUTHORITY_SCHEMA_VERSION,
                taskType,
                WritingBlankContract.NORMALIZATION,
                WritingBlankContract.WHITESPACE_POLICY,
                List.of(
                        new WritingBlankContract.BlankAuthority(
                                prefix + "-b1",
                                1,
                                List.of(
                                        new WritingBlankContract.AcceptedAnswer(
                                                "도서관에서 공부합니다",
                                                WritingBlankContract
                                                        .Equivalence.EXACT,
                                                "",
                                                List.of()),
                                        new WritingBlankContract.AcceptedAnswer(
                                                "도서관에서 학습합니다",
                                                WritingBlankContract
                                                        .Equivalence
                                                        .SEMANTIC_EQUIVALENT,
                                                "Cùng giữ nghĩa học tại thư viện.",
                                                List.of(
                                                        "AUTH-EV-"
                                                                + prefix
                                                                + "-B1")))),
                        new WritingBlankContract.BlankAuthority(
                                prefix + "-b2",
                                2,
                                List.of(
                                        new WritingBlankContract.AcceptedAnswer(
                                                "주 2회/온라인; 복습",
                                                WritingBlankContract
                                                        .Equivalence.EXACT,
                                                "",
                                                List.of()),
                                        new WritingBlankContract.AcceptedAnswer(
                                                "월·수 저녁에 복습합니다",
                                                WritingBlankContract
                                                        .Equivalence
                                                        .SEMANTIC_EQUIVALENT,
                                                "Giữ đúng lịch và hành động ôn tập.",
                                                List.of(
                                                        "AUTH-EV-"
                                                                + prefix
                                                                + "-B2"))))));
    }

    private static WritingBlankContract.LearnerResponse learner(
            WritingTaskType taskType,
            String first,
            String second) {
        String prefix = taskType.name().toLowerCase();
        return new WritingBlankContract.LearnerResponse(
                WritingBlankContract.LEARNER_SCHEMA_VERSION,
                taskType,
                WritingBlankContract.RESPONSE_MODE,
                List.of(
                        new WritingBlankContract.LearnerBlankAnswer(
                                prefix + "-b1", first),
                        new WritingBlankContract.LearnerBlankAnswer(
                                prefix + "-b2", second)));
    }

    private static WritingBlankContract.Evaluation evaluation(
            WritingTaskType taskType,
            WritingBlankContract.Verdict first,
            WritingBlankContract.Verdict second) {
        String prefix = taskType.name().toLowerCase();
        return new WritingBlankContract.Evaluation(
                WritingBlankContract.EVALUATION_SCHEMA_VERSION,
                taskType,
                List.of(
                        blankEvaluation(prefix + "-b1", 1, first),
                        blankEvaluation(prefix + "-b2", 2, second)));
    }

    private static WritingBlankContract.BlankEvaluation blankEvaluation(
            String blankId,
            int ordinal,
            WritingBlankContract.Verdict verdict) {
        boolean deducted = verdict == WritingBlankContract.Verdict.PARTIAL
                || verdict == WritingBlankContract.Verdict.INCORRECT;
        return new WritingBlankContract.BlankEvaluation(
                blankId,
                ordinal,
                verdict,
                deducted ? List.of("EV-" + blankId) : List.of(),
                deducted ? List.of("F-" + blankId) : List.of(),
                deducted ? "표현을 문맥에 맞게 고치십시오." : "",
                deducted ? "DEDUCTION" : "NONE");
    }

    private void assertLecturerScenarios(JsonNode root) {
        assertThat(root.path("lecturerScenarios")).hasSize(2);
        root.path("lecturerScenarios").forEach(scenario -> {
            assertThat(scenario.path("questionCount").asInt()).isEqualTo(12);
            assertThat(scenario.path("activeStrategies").asInt())
                    .isEqualTo(11);
            assertThat(scenario.path("url").asText())
                    .startsWith("/practice/manage/drafts/");
        });
    }

    private JsonNode readManifest() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/practice/pre14-ui-acceptance-scenarios.json")) {
            assertThat(input).isNotNull();
            return mapper.readTree(input);
        }
    }

    private static Set<String> values(JsonNode array, String field) {
        Set<String> values = new HashSet<>();
        array.forEach(node -> values.add(node.path(field).asText()));
        return values;
    }

    private static Set<String> textValues(JsonNode array) {
        Set<String> values = new LinkedHashSet<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    private static JsonNode findByValue(
            JsonNode array,
            String field,
            String value) {
        for (JsonNode node : array) {
            if (value.equals(node.path(field).asText())) {
                return node;
            }
        }
        throw new AssertionError(
                "Manifest row not found: " + field + "=" + value);
    }
}
