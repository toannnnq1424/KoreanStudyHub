package com.ksh.features.practice.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeQuestionGroupVersion;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.PracticeSectionVersion;
import com.ksh.entities.PracticeSetVersion;
import com.ksh.entities.PracticeTestVersion;
import com.ksh.entities.WritingTaskType;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService;
import com.ksh.features.practice.ai.speaking.SpeakingAssessmentPolicyBundle;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationNormalizer;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationResult;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationTestFixtures;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluatorCapability;
import com.ksh.features.practice.ai.speaking.SpeakingFeedbackContractParser;
import com.ksh.features.practice.ai.speaking.SpeakingRubricCriterion;
import com.ksh.features.practice.ai.writing.WritingFeedbackContractParser;
import com.ksh.features.practice.ai.writing.WritingFeedbackViewMapper;
import com.ksh.features.practice.ai.writing.WritingContractTestFixtures;
import com.ksh.features.practice.ai.writing.WritingDiagnosticContract;
import com.ksh.features.practice.ai.writing.WritingEvaluationNormalizer;
import com.ksh.features.practice.ai.writing.WritingScoreAnchorPolicy;
import com.ksh.features.practice.ai.writing.WritingScoringPolicy;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.AssessmentScoreResult;
import com.ksh.features.practice.assessment.AssessmentScoreStatus;
import com.ksh.features.practice.assessment.AssessmentScoringEngine;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.LearnerAnswer;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.assessment.ScoringPolicyCode;
import com.ksh.features.practice.assessment.WritingBlankContract;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveResultPayload;
import com.ksh.features.practice.dto.PracticeDtos.PracticeAttemptResultView;
import com.ksh.features.practice.dto.PracticeDtos.ResultAttemptIdentity;
import com.ksh.features.practice.dto.PracticeDtos.ResultFeedbackAvailability;
import com.ksh.features.practice.dto.PracticeDtos.ResultEvaluationBand;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailScoreCriterion;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailPolarity;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailSpanMembership;
import com.ksh.features.practice.dto.PracticeDtos.ResultPerformanceLevel;
import com.ksh.features.practice.dto.PracticeDtos.ResultOverviewCapabilityAvailability;
import com.ksh.features.practice.dto.PracticeDtos.ResultScoreSummary;
import com.ksh.features.practice.dto.PracticeDtos.ResultState;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingCriterionResult;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingDetailPayload;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingMediaView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingResultPayload;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingTextSegment;
import com.ksh.features.practice.dto.PracticeDtos.WritingDetailPayload;
import com.ksh.features.practice.dto.PracticeDtos.WritingResultPayload;
import com.ksh.features.practice.dto.PracticeDtos.WritingTaskResult;
import com.ksh.features.practice.dto.PracticeDtos.WritingTextSegment;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.service.PracticeAttemptAnswerCodec;
import com.ksh.features.practice.service.PracticeAttemptStatePolicy;
import com.ksh.features.practice.service.PracticePublishedVersionService;
import com.ksh.features.practice.service.PracticeSpeakingMediaService;
import com.ksh.features.practice.service.PracticeVersionSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PracticeResultPresenterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void resultStateDoesNotDescribeTranscriptOnlySpeakingAsFullyScored() {
        PracticeAttempt speaking = mock(PracticeAttempt.class);
        when(speaking.getStatus()).thenReturn(PracticeAttempt.STATUS_GRADED);
        when(speaking.getSkill()).thenReturn("SPEAKING");

        PracticeAttempt writing = mock(PracticeAttempt.class);
        when(writing.getStatus()).thenReturn(PracticeAttempt.STATUS_GRADED);
        when(writing.getSkill()).thenReturn("WRITING");

        assertThat(PracticeResultAssembler.resultState(speaking).label())
                .isEqualTo("Đã xử lý phản hồi");
        assertThat(PracticeResultAssembler.resultState(writing).label())
                .isEqualTo("Đã chấm");
    }

    @Test
    void objectiveOverviewPreservesPartialPendingUnansweredAndUnscorableStates() {
        AssessmentContractCodec codec = mock(AssessmentContractCodec.class);
        QuestionTypeResolver typeResolver = mock(QuestionTypeResolver.class);
        AssessmentScoringEngine scoringEngine = mock(AssessmentScoringEngine.class);
        QuestionExplanationReadService explanations = mock(QuestionExplanationReadService.class);
        ObjectiveResultPresenter presenter = new ObjectiveResultPresenter(
                codec, typeResolver, scoringEngine, explanations, objectMapper);
        List<PracticeQuestionVersion> questions = List.of(
                objectiveQuestion(101L),
                objectiveQuestion(102L),
                objectiveQuestion(103L),
                objectiveQuestion(104L));
        QuestionContent content = QuestionContent.empty();
        AnswerSpec spec = mock(AnswerSpec.class);
        LearnerAnswer answer = mock(LearnerAnswer.class);
        when(typeResolver.resolve(anyString())).thenReturn(CanonicalQuestionType.SINGLE_CHOICE);
        when(codec.adaptLegacyContent(any(), anyString())).thenReturn(content);
        when(codec.adaptLegacyAnswerSpec(anyString(), any(), any())).thenReturn(spec);
        when(codec.adaptLegacyLearnerAnswer(anyString(), anyString(), any())).thenReturn(answer);
        when(scoringEngine.score(any(), any(), any()))
                .thenReturn(score(AssessmentScoreStatus.PARTIALLY_CORRECT, "0.5", "1"))
                .thenReturn(score(AssessmentScoreStatus.NOT_ANSWERED, "0", "1"))
                .thenReturn(score(AssessmentScoreStatus.PENDING_AI, "0", "1"))
                .thenThrow(new IllegalStateException("malformed published contract"));
        when(explanations.availability(List.of(101L, 102L, 103L, 104L)))
                .thenReturn(new ResultFeedbackAvailability("PENDING", "Preparing", 0, 4));

        PracticeResultPresenter.Presentation result = presenter.present(context(
                "READING",
                questions,
                Map.of("101", "one", "102", "", "103", "pending", "104", "bad"),
                null));

        assertThat(result.answers().partial()).isEqualTo(1);
        assertThat(result.answers().notAnswered()).isEqualTo(1);
        assertThat(result.answers().pending()).isEqualTo(1);
        assertThat(result.answers().unscorable()).isEqualTo(1);
        assertThat(result.answers().total()).isEqualTo(4);
        assertThat(result.answers().scoredDenominator()).isEqualTo(2);
        assertThat(result.feedback().state()).isEqualTo("PENDING");
        assertThat(result.feedback().label()).isEqualTo("Giải thích đáp án đang được chuẩn bị");
        assertThat(result.score().available()).isTrue();
    }

    @Test
    void objectiveScoreRateUsesEarnedOverPossiblePointsInsteadOfAnswerAccuracy() {
        AssessmentContractCodec codec = mock(AssessmentContractCodec.class);
        QuestionTypeResolver typeResolver = mock(QuestionTypeResolver.class);
        AssessmentScoringEngine scoringEngine = mock(AssessmentScoringEngine.class);
        QuestionExplanationReadService explanations = mock(QuestionExplanationReadService.class);
        ObjectiveResultPresenter presenter = new ObjectiveResultPresenter(
                codec, typeResolver, scoringEngine, explanations, objectMapper);
        List<PracticeQuestionVersion> questions = List.of(
                objectiveQuestion(111L),
                objectiveQuestion(112L));
        when(typeResolver.resolve(anyString())).thenReturn(CanonicalQuestionType.SINGLE_CHOICE);
        when(codec.adaptLegacyContent(any(), anyString())).thenReturn(QuestionContent.empty());
        when(codec.adaptLegacyAnswerSpec(anyString(), any(), any())).thenReturn(mock(AnswerSpec.class));
        when(codec.adaptLegacyLearnerAnswer(anyString(), anyString(), any()))
                .thenReturn(mock(LearnerAnswer.class));
        when(scoringEngine.score(any(), any(), any()))
                .thenReturn(score(AssessmentScoreStatus.PARTIALLY_CORRECT, "1", "2"))
                .thenReturn(score(AssessmentScoreStatus.CORRECT, "4", "4"));
        when(explanations.availability(List.of(111L, 112L)))
                .thenReturn(new ResultFeedbackAvailability("READY", "stale label", 2, 2));

        PracticeResultPresenter.Presentation result = presenter.present(context(
                "LISTENING",
                questions,
                Map.of("111", "partial", "112", "correct"),
                null));
        ObjectiveResultPayload payload = (ObjectiveResultPayload) result.payload();

        assertThat(payload.breakdown()).singleElement().satisfies(row -> {
            assertThat(row.answers().correct()).isEqualTo(1);
            assertThat(row.answers().partial()).isEqualTo(1);
            assertThat(row.pointsDisplay()).isEqualTo("5/6");
            assertThat(row.scoreRatePercentage()).isEqualByComparingTo("83.33");
            assertThat(row.scoreRateDisplay()).isEqualTo("83.33%");
        });
        assertThat(payload.groups()).singleElement().satisfies(group -> {
            assertThat(group.displayLabel()).isEqualTo("Phần nghe 1");
            assertThat(group.sourceLabel())
                    .isEqualTo("Nhóm câu hỏi đã khóa");
            assertThat(group.firstQuestionId()).isEqualTo(111L);
            assertThat(group.questionTypeLabels())
                    .containsExactly("Trắc nghiệm một đáp án");
            assertThat(group.answers().total()).isEqualTo(2);
            assertThat(group.pointsDisplay()).isEqualTo("5/6");
            assertThat(group.scoreRateDisplay()).isEqualTo("83.33%");
        });
    }

    @Test
    void objectiveAliasesAndUnsupportedTypesFailClosed() {
        AssessmentContractCodec codec = mock(AssessmentContractCodec.class);
        QuestionTypeResolver typeResolver = new QuestionTypeResolver();
        AssessmentScoringEngine scoringEngine = mock(AssessmentScoringEngine.class);
        QuestionExplanationReadService explanations = mock(QuestionExplanationReadService.class);
        ObjectiveResultPresenter presenter = new ObjectiveResultPresenter(
                codec, typeResolver, scoringEngine, explanations, objectMapper);
        List<PracticeQuestionVersion> questions = List.of(
                objectiveQuestion(115L, "SINGLE_CHOICE"),
                objectiveQuestion(116L, "TRUE_FALSE_NOT_GIVEN"),
                objectiveQuestion(117L, "FILL_BLANK"),
                objectiveQuestion(118L, "MCQ"),
                objectiveQuestion(119L, "MCQ_SINGLE"),
                objectiveQuestion(120L, "TFNG"),
                objectiveQuestion(121L, "GAP_FILL"),
                objectiveQuestion(122L, "ALIEN_LEGACY_TYPE"));
        when(codec.adaptLegacyContent(any(), anyString())).thenReturn(QuestionContent.empty());
        when(codec.adaptLegacyAnswerSpec(anyString(), any(), any())).thenReturn(mock(AnswerSpec.class));
        when(codec.adaptLegacyLearnerAnswer(anyString(), anyString(), any()))
                .thenReturn(mock(LearnerAnswer.class));
        when(scoringEngine.score(any(), any(), any()))
                .thenReturn(score(AssessmentScoreStatus.CORRECT, "1", "1"));
        when(explanations.availability(List.of(115L, 116L, 117L, 118L, 119L, 120L, 121L, 122L)))
                .thenReturn(new ResultFeedbackAvailability("UNAVAILABLE", "stale", 0, 8));

        PracticeResultPresenter.Presentation result = presenter.present(context(
                "READING",
                questions,
                Map.of("115", "a", "116", "b", "117", "c", "118", "d",
                        "119", "e", "120", "f", "121", "g", "122", "h"),
                null));
        ObjectiveResultPayload payload = (ObjectiveResultPayload) result.payload();

        assertThat(payload.breakdown()).hasSize(4);
        assertThat(payload.breakdown().get(0).questionType()).isEqualTo("SINGLE_CHOICE");
        assertThat(payload.breakdown().get(0).label()).isEqualTo("Trắc nghiệm một đáp án");
        assertThat(payload.breakdown().get(0).answers().total()).isEqualTo(1);
        assertThat(payload.breakdown().get(1).questionType()).isEqualTo("TRUE_FALSE_NOT_GIVEN");
        assertThat(payload.breakdown().get(1).label())
                .isEqualTo("Đúng, sai hoặc không có thông tin");
        assertThat(payload.breakdown().get(1).answers().total()).isEqualTo(1);
        assertThat(payload.breakdown().get(2).questionType()).isEqualTo("FILL_BLANK");
        assertThat(payload.breakdown().get(2).label()).isEqualTo("Điền từ");
        assertThat(payload.breakdown().get(2).answers().total()).isEqualTo(1);
        assertThat(payload.breakdown().get(3).questionType()).isEqualTo("UNSCORABLE");
        assertThat(payload.breakdown().get(3).label()).isEqualTo("Loại câu hỏi không thể chấm");
        assertThat(payload.breakdown().get(3).answers().unscorable()).isEqualTo(5);
        assertThat(payload.breakdown())
                .extracting(row -> row.questionType())
                .doesNotContain("MCQ", "MCQ_SINGLE", "TFNG", "GAP_FILL",
                        "ALIEN_LEGACY_TYPE");
    }

    @Test
    void objectiveOverviewLabelsExtendedTypesAndScoresBlankAnswersAsUnanswered() {
        AssessmentContractCodec codec = mock(AssessmentContractCodec.class);
        QuestionTypeResolver typeResolver = new QuestionTypeResolver();
        AssessmentScoringEngine scoringEngine = mock(AssessmentScoringEngine.class);
        QuestionExplanationReadService explanations = mock(QuestionExplanationReadService.class);
        ObjectiveResultPresenter presenter = new ObjectiveResultPresenter(
                codec, typeResolver, scoringEngine, explanations, objectMapper);
        PracticeQuestionVersion multiple = objectiveQuestion(123L, "MULTIPLE_ANSWER");
        PracticeQuestionVersion matching = objectiveQuestion(124L, "MATCHING");
        QuestionContent content = QuestionContent.empty();
        when(codec.adaptLegacyContent(any(), anyString())).thenReturn(content);
        when(codec.adaptLegacyAnswerSpec(anyString(), any(), any()))
                .thenReturn(mock(AnswerSpec.class));
        when(scoringEngine.score(any(), any(), any()))
                .thenReturn(score(AssessmentScoreStatus.NOT_ANSWERED, "0", "1"));
        when(explanations.availability(List.of(123L, 124L)))
                .thenReturn(new ResultFeedbackAvailability("UNAVAILABLE", "stale", 0, 2));

        PracticeResultPresenter.Presentation result = presenter.present(context(
                "READING", List.of(multiple, matching), Map.of(), null));
        ObjectiveResultPayload payload = (ObjectiveResultPayload) result.payload();

        assertThat(payload.breakdown())
                .extracting(row -> row.label())
                .containsExactly("Trắc nghiệm nhiều đáp án", "Ghép thông tin A–H");
        assertThat(result.answers().notAnswered()).isEqualTo(2);
        assertThat(result.answers().unscorable()).isZero();
    }

    @Test
    void objectiveScoreRateRemainsUnavailableForZeroDenominatorAndNullableSummary() {
        AssessmentContractCodec codec = mock(AssessmentContractCodec.class);
        QuestionTypeResolver typeResolver = mock(QuestionTypeResolver.class);
        AssessmentScoringEngine scoringEngine = mock(AssessmentScoringEngine.class);
        QuestionExplanationReadService explanations = mock(QuestionExplanationReadService.class);
        ObjectiveResultPresenter presenter = new ObjectiveResultPresenter(
                codec, typeResolver, scoringEngine, explanations, objectMapper);
        PracticeQuestionVersion question = objectiveQuestion(121L);
        when(typeResolver.resolve(anyString())).thenReturn(CanonicalQuestionType.SINGLE_CHOICE);
        when(codec.adaptLegacyContent(any(), anyString())).thenReturn(QuestionContent.empty());
        when(codec.adaptLegacyAnswerSpec(anyString(), any(), any())).thenReturn(mock(AnswerSpec.class));
        when(codec.adaptLegacyLearnerAnswer(anyString(), anyString(), any()))
                .thenReturn(mock(LearnerAnswer.class));
        when(scoringEngine.score(any(), any(), any()))
                .thenReturn(score(AssessmentScoreStatus.PENDING_AI, "0", "0"));
        when(explanations.availability(List.of(121L)))
                .thenReturn(new ResultFeedbackAvailability("UNAVAILABLE", "stale label", 0, 1));

        PracticeResultPresenter.Presentation result = presenter.present(context(
                "READING", List.of(question), Map.of("121", "pending"), null));
        ObjectiveResultPayload payload = (ObjectiveResultPayload) result.payload();
        ResultScoreSummary unavailable = new ResultScoreSummary(
                null, null, null, null, "EARNED_POINTS", "Điểm đạt được", null);
        ResultScoreSummary earnedWithoutStoredScore = new ResultScoreSummary(
                null, BigDecimal.valueOf(5), null, null,
                "EARNED_POINTS", "Điểm đạt được", null);

        assertThat(payload.breakdown()).singleElement().satisfies(row -> {
            assertThat(row.pointsDisplay()).isEqualTo("0/0");
            assertThat(row.scoreRatePercentage()).isNull();
            assertThat(row.scoreRateDisplay()).isNull();
        });
        assertThat(unavailable.available()).isFalse();
        assertThat(unavailable.primaryDisplay()).isNull();
        assertThat(unavailable.pointsDisplay()).isNull();
        assertThat(earnedWithoutStoredScore.available()).isTrue();
        assertThat(earnedWithoutStoredScore.primaryDisplay()).isEqualTo("5");
        assertThat(earnedWithoutStoredScore.pointsDisplay()).isNull();
    }

    @Test
    void objectiveFeedbackPreservesEveryExplanationLifecycleStateWithLearnerCopy() {
        AssessmentContractCodec codec = mock(AssessmentContractCodec.class);
        QuestionTypeResolver typeResolver = mock(QuestionTypeResolver.class);
        AssessmentScoringEngine scoringEngine = mock(AssessmentScoringEngine.class);
        QuestionExplanationReadService explanations = mock(QuestionExplanationReadService.class);
        ObjectiveResultPresenter presenter = new ObjectiveResultPresenter(
                codec, typeResolver, scoringEngine, explanations, objectMapper);
        PracticeQuestionVersion question = objectiveQuestion(131L);
        when(typeResolver.resolve(anyString())).thenReturn(CanonicalQuestionType.SINGLE_CHOICE);
        when(codec.adaptLegacyContent(any(), anyString())).thenReturn(QuestionContent.empty());
        when(codec.adaptLegacyAnswerSpec(anyString(), any(), any())).thenReturn(mock(AnswerSpec.class));
        when(codec.adaptLegacyLearnerAnswer(anyString(), anyString(), any()))
                .thenReturn(mock(LearnerAnswer.class));
        when(scoringEngine.score(any(), any(), any()))
                .thenReturn(score(AssessmentScoreStatus.CORRECT, "1", "1"));
        when(explanations.availability(List.of(131L))).thenReturn(
                new ResultFeedbackAvailability("READY", "stale", 1, 1),
                new ResultFeedbackAvailability("PARTIAL", "stale", 1, 2),
                new ResultFeedbackAvailability("PENDING", "stale", 0, 1),
                new ResultFeedbackAvailability("FAILED", "stale", 0, 1),
                new ResultFeedbackAvailability("UNAVAILABLE", "stale", 0, 1));
        List<String> states = List.of("READY", "PARTIAL", "PENDING", "FAILED", "UNAVAILABLE");
        List<String> labels = List.of(
                "Giải thích đáp án đã sẵn sàng",
                "Một phần giải thích đáp án đã sẵn sàng",
                "Giải thích đáp án đang được chuẩn bị",
                "Chưa thể cung cấp giải thích đáp án",
                "Đề này hiện chưa có giải thích đáp án");

        for (int index = 0; index < states.size(); index++) {
            PracticeResultPresenter.Presentation result = presenter.present(context(
                    "READING", List.of(question), Map.of("131", "answer"), null));
            assertThat(result.feedback().state()).isEqualTo(states.get(index));
            assertThat(result.feedback().label()).isEqualTo(labels.get(index));
        }
    }

    @Test
    void koreanWritingKeepsTaskNativeRubricsAndUsesScorelessDiagnosticLensesForLongForm() throws Exception {
        PracticeQuestionVersion q51 = writingQuestion(151L, 51, WritingTaskType.Q51);
        PracticeQuestionVersion q52 = writingQuestion(152L, 52, WritingTaskType.Q52);
        PracticeQuestionVersion q53 = writingQuestion(153L, 53, WritingTaskType.Q53);
        PracticeQuestionVersion q54 = writingQuestion(154L, 54, WritingTaskType.Q54);
        String longKoreanPrompt = "한국 사회의 변화가 개인과 공동체에 미치는 영향을 설명하고 "
                + "구체적인 근거를 들어 자신의 견해를 논리적으로 서술하십시오. ".repeat(8);
        when(q54.getPrompt()).thenReturn(longKoreanPrompt);
        String q51Answer = "short answer";
        String q52Answer = "second short answer";
        String q53Answer = "정책 변화의 영향을 설명하는 공식 표현입니다.";
        String q54Answer = "공동체 변화에 관한 더 긴 답안입니다.";
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn(writingFeedback(Map.of(
                151L, currentWritingEvaluation(
                        "Q51", q51Answer, 2, 2, 1, 2, 2, 1),
                152L, currentWritingEvaluation(
                        "Q52", q52Answer, 2, 2, 1, 2, 2, 1),
                153L, currentWritingEvaluation(
                        "Q53", q53Answer, 10, 7, 7),
                154L, currentWritingEvaluation(
                        "Q54", q54Answer, 16, 12, 12))));
        WritingResultPresenter presenter = writingPresenter();

        PracticeResultPresenter.Presentation result = presenter.present(context(
                "WRITING",
                List.of(q51, q52, q53, q54),
                Map.of("151", q51Answer, "152", q52Answer,
                        "153", q53Answer,
                        "154", q54Answer),
                attempt));
        WritingResultPayload payload = (WritingResultPayload) result.payload();

        assertThat(payload.tasks()).hasSize(4);
        assertThat(payload.tasks())
                .extracting(task -> task.questionId())
                .containsExactly(151L, 152L, 153L, 154L);
        assertThat(payload.tasks().subList(0, 2)).allSatisfy(task -> {
            assertThat(task.clozeTask()).isTrue();
            assertThat(task.officialCriteria())
                    .extracting(criterion -> criterion.maxScore())
                    .containsExactly(
                            BigDecimal.valueOf(2), BigDecimal.valueOf(2), BigDecimal.ONE,
                            BigDecimal.valueOf(2), BigDecimal.valueOf(2), BigDecimal.ONE);
            assertThat(task.criterionGroups())
                    .extracting(group -> group.label())
                    .containsExactly(
                            "Nội dung và ngữ cảnh",
                            "Ngữ pháp và cấu trúc",
                            "Biểu đạt và độ tự nhiên");
            assertThat(task.criterionGroups())
                    .allSatisfy(group ->
                            assertThat(group.criteria()).hasSize(2));
            assertThat(task.officialCriteria())
                    .allSatisfy(criterion -> {
                        assertThat(criterion.performanceLevel())
                                .isEqualTo("EXCELLENT");
                        assertThat(criterion.performanceLabel())
                                .isEqualTo("Xuất sắc");
                        assertThat(criterion.performanceLabelKo())
                                .isEqualTo("우수");
                    });
            assertThat(task.performanceLevel().code())
                    .isEqualTo("EXCELLENT");
            assertThat(task.performanceLevel().labelVi())
                    .isEqualTo("Xuất sắc");
            assertThat(task.performanceLevel().labelKo())
                    .isEqualTo("우수");
            assertThat(task.analysisLenses()).isEmpty();
        });
        assertThat(payload.tasks().get(2).score().pointsDisplay()).isEqualTo("24/30");
        assertThat(payload.tasks().get(2).performanceLevel().code())
                .isEqualTo("GOOD");
        assertThat(payload.tasks().get(2).performanceLevel().labelVi())
                .isEqualTo("Tốt");
        assertThat(payload.tasks().get(2).performanceLevel().labelKo())
                .isEqualTo("좋음");
        assertThat(payload.tasks().get(2).officialCriteria())
                .extracting(criterion -> criterion.label())
                .containsExactly(
                        "Hoàn thành nhiệm vụ và Nội dung",
                        "Cấu trúc và Mạch lạc",
                        "Ngôn ngữ và Biểu đạt");
        assertThat(payload.tasks().get(2).officialCriteria())
                .extracting(criterion -> criterion.performanceLevel())
                .containsExactly("GOOD", "MODEST", "MODEST");
        assertThat(payload.tasks().get(2).analysisLenses())
                .extracting(lens -> lens.label())
                .containsExactly(
                        "Nhiệm vụ và Nội dung",
                        "Cấu trúc và mạch lạc",
                        "Từ vựng và Diễn đạt",
                        "Ngữ pháp và Độ chính xác");
        assertThat(payload.tasks().get(2).analysisLenses().get(0).evidence())
                .contains("Cần bổ sung yêu cầu nội dung còn thiếu.");
        assertThat(payload.tasks().get(2).analysisLenses().get(2).evidence())
                .contains("Cần điều chỉnh lựa chọn từ tại đúng vị trí.");
        assertThat(objectMapper.writeValueAsString(payload.tasks().get(2).analysisLenses()))
                .doesNotContain("\"score\"", "\"maxScore\"", "\"percentage\"", "\"band\"",
                        "\"countedSeparately\"");
        assertThat(payload.tasks().get(3).score().pointsDisplay()).isEqualTo("40/50");
        assertThat(payload.tasks().get(3).performanceLevel().code())
                .isEqualTo("GOOD");
        assertThat(payload.tasks().get(3).prompt()).isEqualTo(longKoreanPrompt);
        assertThat(payload.tasks().get(3).officialCriteria())
                .extracting(criterion -> criterion.maxScore())
                .containsExactly(BigDecimal.valueOf(20), BigDecimal.valueOf(15), BigDecimal.valueOf(15));
        assertThat(payload.tasks()).allSatisfy(task -> assertThat(task.detailAvailable()).isTrue());

        PracticeResultContext detailContext = context(
                "WRITING",
                List.of(q51, q52, q53, q54),
                Map.of("151", q51Answer, "152", q52Answer,
                        "153", q53Answer, "154", q54Answer),
                attempt);
        WritingDetailPayload detail = (WritingDetailPayload) presenter.presentDetail(
                detailContext,
                overview("WRITING", result),
                153L);
        assertThat(detail.scoreCriteria())
                .filteredOn(row -> row.questionId().equals(151L))
                .allSatisfy(row -> {
                    assertThat(row.performanceLevel().code())
                            .isEqualTo("EXCELLENT");
                    assertThat(row.performanceLevel().labelVi())
                            .isEqualTo("Xuất sắc");
                    assertThat(row.performanceLevel().labelKo())
                            .isEqualTo("우수");
                });
        assertThat(detail.scoreCriteria())
                .filteredOn(row -> row.questionId().equals(153L))
                .extracting(row -> row.performanceLevel().code())
                .containsExactly("GOOD", "MODEST", "MODEST");
        assertThat(detail.scoreCriteria())
                .filteredOn(row -> row.questionId().equals(154L))
                .extracting(row -> row.performanceLevel().code())
                .containsExactly("GOOD", "GOOD", "GOOD");
        assertThat(objectMapper.writeValueAsString(detail))
                .doesNotContain(WritingScoringPolicy.PROFILE_ID);
    }

    @Test
    void writingPerformanceProjectionKeepsUnavailableAndNotScorableDistinct() {
        ResultDetailScoreCriterion unavailable = new ResultDetailScoreCriterion(
                153L,
                "W_LANGUAGE_EXPRESSION",
                "Ngôn ngữ và Biểu đạt",
                "언어와 표현",
                null,
                null,
                "UNAVAILABLE",
                1);
        ResultDetailScoreCriterion notScorable = new ResultDetailScoreCriterion(
                153L,
                "W_LANGUAGE_EXPRESSION",
                "Ngôn ngữ và Biểu đạt",
                "언어와 표현",
                null,
                null,
                "NOT_SCORABLE",
                2);
        WritingScoreAnchorPolicy.PerformanceLevel q51Partial =
                WritingScoreAnchorPolicy.requirePerformanceLevel(1, 2);

        assertThat(unavailable.performanceLevel())
                .isEqualTo(ResultPerformanceLevel.unavailableView());
        assertThat(notScorable.performanceLevel())
                .isEqualTo(ResultPerformanceLevel.notScorableView());
        assertThat(q51Partial).isEqualTo(
                WritingScoreAnchorPolicy.PerformanceLevel.MODEST);
        assertThat(q51Partial.labelVi()).isEqualTo("Đang phát triển");
        assertThat(q51Partial.labelKo()).isEqualTo("보통");
    }

    @Test
    void writingStoredPerformanceLevelMismatchFailsClosedToUnavailable() throws Exception {
        PracticeQuestionVersion question =
                writingQuestion(153L, 53, WritingTaskType.Q53);
        String learnerAnswer = "정책 변화의 영향을 설명하는 공식 표현입니다.";
        String valid = currentWritingEvaluation(
                "Q53", learnerAnswer, 10, 7, 7);
        ObjectNode tampered = (ObjectNode) objectMapper.readTree(valid);
        ((ObjectNode) tampered.withArray("rubric_scores").get(0))
                .put("performanceLevel", "LIMITED");
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn(
                writingFeedback(Map.of(153L, tampered.toString())));

        PracticeResultPresenter.Presentation result = writingPresenter().present(
                context("WRITING", List.of(question),
                        Map.of("153", learnerAnswer), attempt));
        WritingTaskResult task =
                ((WritingResultPayload) result.payload()).tasks().get(0);

        assertThat(task.score().available()).isFalse();
        assertThat(task.performanceLevel().code()).isEqualTo("UNAVAILABLE");
        assertThat(task.performanceLevel().labelVi()).isEqualTo("Chưa khả dụng");
        assertThat(task.performanceLevel().labelKo()).isEqualTo("평가 불가");
        assertThat(task.officialCriteria()).isEmpty();
    }

    @Test
    void writingDetailRendersOnlyAuthoritativeLedgerFindings() {
        PracticeQuestionVersion question = writingQuestion(153L, 53, WritingTaskType.Q53);
        String learnerAnswer = "학생은 문법 오류를 고칩니다.";
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        String normalized =
                WritingContractTestFixtures.normalizedFeedback(
                        objectMapper,
                        "Q53",
                        learnerAnswer,
                        envelope -> {
                            WritingContractTestFixtures.addEvidence(
                                    envelope,
                                    "WEV-STRENGTH",
                                    learnerAnswer,
                                    "학생",
                                    learnerAnswer.indexOf("학생"));
                            WritingContractTestFixtures.addEvidence(
                                    envelope,
                                    "WEV-IMPROVEMENT",
                                    learnerAnswer,
                                    "문법 오류",
                                    learnerAnswer.indexOf("문법 오류"));
                            WritingContractTestFixtures.addFinding(
                                    envelope,
                                    "WF-STRENGTH",
                                    "STRENGTH",
                                    "KEEP",
                                    "W_FORMAL_VOCABULARY_USAGE",
                                    "WORD_CHOICE",
                                    "W_LANGUAGE_EXPRESSION",
                                    "WEV-STRENGTH",
                                    List.of(),
                                    "Từ vựng có bằng chứng trực tiếp.",
                                    "",
                                    "MODERATE");
                            WritingContractTestFixtures.addFinding(
                                    envelope,
                                    "WF-IMPROVEMENT",
                                    "IMPROVEMENT",
                                    "REPLACE",
                                    "W_GRAMMAR_ERRORS",
                                    "MORPHOLOGY_PARTICLES",
                                    "W_LANGUAGE_EXPRESSION",
                                    "WEV-IMPROVEMENT",
                                    List.of(),
                                    "Cần sửa ngữ pháp.",
                                    "문법을 고칩니다",
                                    "MINOR");
                            ObjectNode language =
                                    WritingContractTestFixtures.rubric(
                                            envelope,
                                            "W_LANGUAGE_EXPRESSION");
                            language.put("score", 7);
                            WritingContractTestFixtures.replaceIds(
                                    language,
                                    "evidenceIds",
                                    "WEV-STRENGTH",
                                    "WEV-IMPROVEMENT");
                            WritingContractTestFixtures.replaceIds(
                                    language,
                                    "findingIds",
                                    "WF-STRENGTH",
                                    "WF-IMPROVEMENT");
                        });
        when(attempt.getAiFeedbackJson()).thenReturn(
                writingFeedback(Map.of(153L, normalized)));
        WritingResultPresenter presenter = writingPresenter();
        PracticeResultContext context = context(
                "WRITING", List.of(question), Map.of("153", learnerAnswer), attempt);
        PracticeResultPresenter.Presentation presentation = presenter.present(context);

        WritingDetailPayload detail = (WritingDetailPayload) presenter.presentDetail(
                context, overview("WRITING", presentation), null);

        assertThat(detail.scoreProfileId()).isEqualTo(WritingScoringPolicy.PROFILE_ID);
        assertThat(detail.diagnosticSeamId())
                .isEqualTo(WritingDiagnosticDescriptorRegistry.SEAM_ID);
        assertThat(detail.diagnosticSeamState())
                .isEqualTo("BOUNDED_CURRENT_EVIDENCE");
        assertThat(detail.diagnosticAvailability()).isEqualTo("AVAILABLE");
        assertThat(detail.taskCoverage())
                .extracting(row -> row.requirementId())
                .containsExactly(
                        "Q53_FOUR_TRANSPORT_MODES",
                        "Q53_DATA_2024",
                        "Q53_DATA_2026",
                        "Q53_MAIN_CHANGES",
                        "Q53_PLAUSIBLE_CAUSE",
                        "Q53_LENGTH_200_300");
        assertThat(detail.taskCoverage())
                .allSatisfy(row -> {
                    assertThat(row.questionId()).isEqualTo(153L);
                    assertThat(row.status()).isEqualTo("NOT_MET");
                    assertThat(row.labelVi()).isNotBlank();
                    assertThat(row.evidenceIds()).isEmpty();
                    assertThat(row.evidenceCount()).isZero();
                });
        assertThat(detail.diagnosticFindings()).hasSize(2);
        assertThat(detail.filterChips()).hasSize(25);
        assertThat(detail.filterChips())
                .filteredOn(chip -> chip.count() > 0)
                .hasSize(2);
        assertThat(detail.filterChips())
                .filteredOn(chip -> chip.count() == 0)
                .hasSize(23)
                .allSatisfy(chip -> assertThat(chip.evidenceAvailability())
                        .isEqualTo("NO_FINDING"));
        assertThat(detail.diagnosticGroups())
                .extracting(group -> group.categoryCode())
                .contains("MORPHOSYNTAX", "LEXICO_SEMANTIC");
        assertThat(detail.filterChips())
                .filteredOn(chip -> chip.id().equals(
                        "W_FORMAL_VOCABULARY_USAGE_WRITING_Q53"))
                .singleElement()
                .satisfies(chip -> {
                    assertThat(chip.count()).isEqualTo(1);
                    assertThat(chip.parentCriterionId())
                            .isEqualTo("W_LANGUAGE_EXPRESSION");
                    assertThat(chip.scoreEffect()).isEqualTo("PARENT_LINKED");
                    assertThat(chip.countedSeparately()).isFalse();
                });
        assertThat(detail.filterChips())
                .filteredOn(chip -> chip.id().equals("W_GRAMMAR_ERRORS_WRITING_Q53"))
                .singleElement()
                .satisfies(chip -> {
                    assertThat(chip.count()).isEqualTo(1);
                    assertThat(chip.parentCriterionId())
                            .isEqualTo("W_LANGUAGE_EXPRESSION");
                    assertThat(chip.scoreEffect()).isEqualTo("PARENT_LINKED");
                    assertThat(chip.countedSeparately()).isFalse();
                });
        assertThat(detail.diagnosticFindings()).allSatisfy(finding -> {
            assertThat(finding.target().kind().name())
                    .isEqualTo("TEXT_SPAN");
            assertThat(detail.filterChips())
                    .extracting(chip -> chip.id())
                    .contains(finding.descriptorId());
            assertThat(finding.subtype()).isNotBlank();
            assertThat(finding.impact()).isIn(
                    "MINOR", "MODERATE", "MAJOR", "BLOCKING");
            assertThat(finding.frequency()).isPositive();
            assertThat(finding.confidence())
                    .isBetween(BigDecimal.ZERO, BigDecimal.ONE);
            assertThat(finding.observability())
                    .isIn("DIRECT", "INFERRED_BOUNDED");
            if (finding.parentCriterionId() == null) {
                assertThat(finding.scoreEffect()).isEqualTo("DIAGNOSTIC_ONLY");
            } else {
                assertThat(detail.tasks().get(0).officialCriteria())
                        .extracting(criterion -> criterion.criterionId())
                        .contains(finding.parentCriterionId());
            }
        });
    }

    @Test
    void writingDetailBuildsExactTrustedLearnerAnswerSegmentsWithoutHtml() {
        String learnerAnswer = "학생은 문법 오류를 고칩니다.";
        String evidence = "문법 오류";
        int start = learnerAnswer.indexOf(evidence);

        WritingDetailPayload detail =
                writingDetailWithCurrentFinding(
                        learnerAnswer, evidence, start);

        assertThat(detail.learnerAnswerSegments())
                .extracting(WritingTextSegment::text)
                .containsExactly("학생은 ", evidence, "를 고칩니다.");
        assertThat(detail.learnerAnswerSegments().stream()
                .map(WritingTextSegment::text)
                .collect(java.util.stream.Collectors.joining()))
                .isEqualTo(learnerAnswer);
        assertThat(detail.learnerAnswerSegments())
                .filteredOn(WritingTextSegment::annotated)
                .singleElement()
                .satisfies(segment -> {
                    assertThat(segment.annotationId()).isEqualTo("ann-grammar");
                    assertThat(segment.kind()).isEqualTo("NEEDS_IMPROVEMENT");
                    assertThat(segment.categoryCode()).isEqualTo("MORPHOSYNTAX");
                    assertThat(segment.criterionId()).isEqualTo("W_GRAMMAR_ERRORS");
                    assertThat(segment.featureId())
                            .isEqualTo("W_GRAMMAR_ERRORS_WRITING_Q53");
                    assertThat(segment.explanationVi()).isEqualTo("Cần sửa ngữ pháp");
                    assertThat(segment.correctionKo()).isEqualTo("문법을 고칩니다");
                });
        assertThat(objectMapper.valueToTree(detail.learnerAnswerSegments()).toString())
                .doesNotContain("html", "markup");
    }

    @Test
    void writingDetailFailsClosedToPlainAnswerForMalformedOrOverlappingAnnotations() {
        String learnerAnswer = "abcdef";
        List<String> unsafeAnnotations = List.of(
                """
                [
                  {
                    "id":"ann-mismatch",
                    "kind":"need",
                    "criterionId":"W_GRAMMAR_ERRORS",
                    "start":0,
                    "end":3,
                    "evidence":"abX",
                    "explanationVi":"Sai evidence",
                    "correction":"교정"
                  }
                ]
                """,
                """
                [
                  {
                    "id":"ann-strength",
                    "kind":"strength",
                    "criterionId":"W_NATURAL_KOREAN_EXPRESSIONS",
                    "start":0,
                    "end":4,
                    "evidence":"abcd",
                    "explanationVi":"Điểm mạnh",
                    "correction":""
                  },
                  {
                    "id":"ann-need",
                    "kind":"need",
                    "criterionId":"W_GRAMMAR_ERRORS",
                    "start":2,
                    "end":6,
                    "evidence":"cdef",
                    "explanationVi":"Cần sửa",
                    "correction":"교정"
                  }
                ]
                """);

        for (String annotations : unsafeAnnotations) {
            WritingDetailPayload detail = writingDetailWithAnnotations(
                    learnerAnswer, annotations);

            assertThat(detail.learnerAnswerSegments())
                    .singleElement()
                    .satisfies(segment -> {
                        assertThat(segment.text()).isEqualTo(learnerAnswer);
                        assertThat(segment.annotated()).isFalse();
                        assertThat(segment.annotationId()).isNull();
                        assertThat(segment.featureId()).isNull();
                    });
        }
    }

    @Test
    void writingDetailDoesNotInventClozeBlankParentsForGenericStoredFindings() {
        PracticeQuestionVersion q51 = writingQuestion(151L, 51, WritingTaskType.Q51);
        PracticeQuestionVersion q52 = writingQuestion(152L, 52, WritingTaskType.Q52);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn(writingFeedback(Map.of(
                151L, currentWritingEvaluation(
                        "Q51", "문맥 문법", 2, 2, 1, 2, 2, 1),
                152L, currentWritingEvaluation(
                        "Q52", "문맥 문법", 2, 2, 1, 2, 2, 1))));
        WritingResultPresenter presenter = writingPresenter();
        PracticeResultContext context = context(
                "WRITING",
                List.of(q51, q52),
                Map.of("151", "문맥 문법", "152", "문맥 문법"),
                attempt);
        PracticeResultPresenter.Presentation presentation = presenter.present(context);

        WritingDetailPayload detail = (WritingDetailPayload) presenter.presentDetail(
                context, overview("WRITING", presentation), null);

        assertThat(detail.tasks()).hasSize(2).allSatisfy(task -> {
            assertThat(task.feedback().ready()).isTrue();
            assertThat(task.officialCriteria()).hasSize(6);
        });
        assertThat(detail.scoreCriteria()).hasSize(12)
                .allSatisfy(criterion -> assertThat(criterion.feedbackVi())
                        .isNotBlank());
        assertThat(detail.diagnosticFindings()).isEmpty();
        assertThat(detail.filterChips()).isEmpty();
        assertThat(detail.diagnosticAvailability())
                .isEqualTo("BLANK_IDENTITY_UNAVAILABLE");
    }

    @Test
    void writingDetailMapsCanonicalSourceOffsetsIntoTheAuthoritativeBlank() throws Exception {
        PracticeQuestionVersion q52 = writingQuestion(
                152L, 52, WritingTaskType.Q52);
        WritingBlankContract.QuestionResponse authority =
                new WritingBlankContract.QuestionResponse(
                        WritingBlankContract.RESPONSE_SCHEMA_VERSION,
                        WritingBlankContract.RESPONSE_MODE,
                        WritingTaskType.Q52,
                        List.of(
                                new WritingBlankContract.BlankDefinition(
                                        "q52-b1", 1, "첫째"),
                                new WritingBlankContract.BlankDefinition(
                                        "q52-b2", 2, "둘째")));
        QuestionContent content = new QuestionContent(
                QuestionContent.SCHEMA_VERSION_V3,
                List.of(),
                List.of(),
                null,
                null,
                null,
                authority,
                "ko");
        when(q52.getQuestionContentJson()).thenReturn(
                objectMapper.writeValueAsString(content));

        WritingBlankContract.LearnerResponse response =
                new WritingBlankContract.LearnerResponse(
                        WritingBlankContract.LEARNER_SCHEMA_VERSION,
                        WritingTaskType.Q52,
                        WritingBlankContract.RESPONSE_MODE,
                        List.of(
                                new WritingBlankContract.LearnerBlankAnswer(
                                        "q52-b1", "학생는 학교에 갑니다"),
                                new WritingBlankContract.LearnerBlankAnswer(
                                        "q52-b2", "친구를 만납니다")));
        String learnerSource = objectMapper.writeValueAsString(response);
        String evidence = "학생는";
        int evidenceStart = learnerSource.indexOf(evidence);
        String normalized = currentWritingEvaluation(
                "Q52",
                learnerSource,
                envelope -> {
                    WritingContractTestFixtures.addEvidence(
                            envelope,
                            "WEV-BLANK-1",
                            learnerSource,
                            evidence,
                            evidenceStart);
                    WritingContractTestFixtures.addFinding(
                            envelope,
                            "WF-BLANK-1",
                            "IMPROVEMENT",
                            "REPLACE",
                            "W_CLOZE_GRAMMAR_COMPATIBILITY",
                            "ENDINGS_CONJUGATION",
                            "W_CLOZE_BLANK_1_GRAMMAR",
                            "WEV-BLANK-1",
                            List.of("CLOZE_BLANK_1_CONTEXT"),
                            "Tiểu từ chủ đề chưa tương thích.",
                            "학생은",
                            "MODERATE");
                    WritingContractTestFixtures.replaceIds(
                            WritingContractTestFixtures.rubric(
                                    envelope,
                                    "W_CLOZE_BLANK_1_GRAMMAR"),
                            "findingIds",
                            "WF-BLANK-1");
                    ObjectNode coverage =
                            WritingContractTestFixtures.coverage(
                                    envelope,
                                    "CLOZE_BLANK_1_CONTEXT");
                    coverage.put("status", "NOT_MET");
                    coverage.putArray("evidenceIds");
                },
                2, 1, 1, 2, 2, 1);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn(
                writingFeedback(Map.of(152L, normalized)));
        WritingResultPresenter presenter = writingPresenter();
        PracticeResultContext context = context(
                "WRITING",
                List.of(q52),
                Map.of("152", learnerSource),
                attempt);
        PracticeResultPresenter.Presentation presentation =
                presenter.present(context);

        WritingDetailPayload detail = (WritingDetailPayload)
                presenter.presentDetail(
                        context,
                        overview("WRITING", presentation),
                        152L);

        assertThat(detail.structuredBlankAnswers()).hasSize(2);
        assertThat(detail.diagnosticAvailability()).isEqualTo("AVAILABLE");
        assertThat(detail.diagnosticFindings())
                .extracting(finding -> finding.findingId())
                .containsExactly("WF-BLANK-1");
        assertThat(detail.structuredBlankAnswers().get(0).segments())
                .extracting(WritingTextSegment::text)
                .containsExactly("학생는", " 학교에 갑니다");
        assertThat(detail.structuredBlankAnswers().get(0).segments())
                .filteredOn(WritingTextSegment::annotated)
                .singleElement()
                .satisfies(segment -> {
                    assertThat(segment.annotationId())
                            .isEqualTo("WF-BLANK-1");
                    assertThat(segment.text()).isEqualTo(evidence);
                    assertThat(segment.correctionKo()).isEqualTo("학생은");
                });
        assertThat(detail.structuredBlankAnswers().get(1).segments())
                .singleElement()
                .satisfies(segment -> assertThat(segment.annotated())
                        .isFalse());
    }

    @Test
    void writingDetailKeepsLearnerUpgradeProvenanceAndIgnoresCurrentEvaluatorSample() {
        PracticeQuestionVersion q53 = writingQuestion(153L, 53, WritingTaskType.Q53);
        PracticeQuestionVersion q54 = writingQuestion(154L, 54, WritingTaskType.Q54);
        String q53Answer = "학생은 문법 오류를 고칩니다.";
        String q54Answer = "선택하지 않은 원문";
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        String q53Evaluation = currentWritingEvaluation(
                "Q53",
                q53Answer,
                envelope -> {
                    WritingContractTestFixtures.addEvidence(
                            envelope,
                            "WEV-UPGRADE",
                            q53Answer,
                            "문법 오류",
                            q53Answer.indexOf("문법 오류"));
                    WritingContractTestFixtures.addFinding(
                            envelope,
                            "WF-UPGRADE",
                            "IMPROVEMENT",
                            "REPLACE",
                            "W_GRAMMAR_ERRORS",
                            "MORPHOLOGY_PARTICLES",
                            "W_LANGUAGE_EXPRESSION",
                            "WEV-UPGRADE",
                            List.of(),
                            "Diễn đạt rõ hành động sửa.",
                            "문법 오류를 바로잡습니다",
                            "MINOR");
                    ObjectNode language =
                            WritingContractTestFixtures.rubric(
                                    envelope,
                                    "W_LANGUAGE_EXPRESSION");
                    WritingContractTestFixtures.replaceIds(
                            language,
                            "evidenceIds",
                            "WEV-BASE",
                            "WEV-UPGRADE");
                    WritingContractTestFixtures.replaceIds(
                            language,
                            "findingIds",
                            "WF-LANGUAGE",
                            "WF-UPGRADE");
                    ObjectNode upgrade =
                            (ObjectNode) envelope.path("upgradedAnswer");
                    upgrade.put("content",
                            "학생은 문법 오류를 바로잡습니다.");
                    ObjectNode rewrite =
                            upgrade.withArray("rewrites").addObject();
                    rewrite.putArray("findingIds")
                            .add("WF-UPGRADE");
                    rewrite.put("evidenceId", "WEV-UPGRADE");
                    rewrite.put("replacementKo",
                            "문법 오류를 바로잡습니다");
                    rewrite.put("reasonVi",
                            "Diễn đạt rõ hành động sửa.");
                },
                10, 7, 7);
        when(attempt.getAiFeedbackJson()).thenReturn(writingFeedback(Map.of(
                153L, q53Evaluation,
                154L, currentWritingEvaluation(
                        "Q54", q54Answer, 16, 12, 12))));
        WritingResultPresenter presenter = writingPresenter();
        PracticeResultContext context = context(
                "WRITING",
                List.of(q53, q54),
                Map.of(
                        "153", q53Answer,
                        "154", q54Answer),
                attempt);
        PracticeResultPresenter.Presentation presentation = presenter.present(context);

        WritingDetailPayload detail = (WritingDetailPayload) presenter.presentDetail(
                context, overview("WRITING", presentation), 153L);

        assertThat(detail.activeQuestionId()).isEqualTo(153L);
        assertThat(detail.upgrade().questionId()).isEqualTo(153L);
        assertThat(detail.upgrade().learnerDerivedUpgrade().available()).isTrue();
        assertThat(detail.upgrade().learnerDerivedUpgrade().content())
                .isEqualTo("학생은 문법 오류를 바로잡습니다.");
        assertThat(detail.upgrade().significantRewrites())
                .singleElement()
                .satisfies(rewrite -> {
                    assertThat(rewrite.original()).isEqualTo("문법 오류");
                    assertThat(rewrite.upgraded()).isEqualTo("문법 오류를 바로잡습니다");
                    assertThat(rewrite.reason()).isEqualTo("Diễn đạt rõ hành động sửa.");
        });
        assertThat(detail.upgrade().evaluatorSample().available()).isFalse();
        assertThat(detail.upgrade().evaluatorSample().content()).isEmpty();
        assertThat(detail.upgrade().evaluatorSample().provenance())
                .isEqualTo("NOT_PROVIDED_BY_CURRENT_EVALUATOR");
        assertThat(objectMapper.valueToTree(detail.upgrade()).toString())
                .doesNotContain(
                        "선택하지 않은 답안",
                        "평가기가 만든 참고 답안",
                        "teacherReference");
    }

    @Test
    void writingDetailDoesNotPromoteMalformedOrPendingQualitativeArtifacts() {
        PracticeQuestionVersion question = writingQuestion(153L, 53, WritingTaskType.Q53);
        PracticeAttempt legacyAttempt = mock(PracticeAttempt.class);
        when(legacyAttempt.getAiFeedbackJson()).thenReturn("""
                {
                  "raw_score":24,"raw_score_max":30,"score_available":true,
                  "rubric_scores":[
                    {"criterionId":"W_CONTENT_TASK_ACHIEVEMENT","score":10,"maxScore":12},
                    {"criterionId":"W_ORGANIZATION_COHERENCE","score":7,"maxScore":9},
                    {"criterionId":"W_LANGUAGE_EXPRESSION","score":7,"maxScore":9}
                  ],
                  "upgraded_answer":"레거시 업그레이드",
                  "sample_answer":"레거시 참고 답안"
                }
                """);
        WritingResultPresenter presenter = writingPresenter();
        PracticeResultContext legacyContext = context(
                "WRITING",
                List.of(question),
                Map.of("153", "학생 답안"),
                legacyAttempt);
        PracticeResultPresenter.Presentation legacyPresentation =
                presenter.present(legacyContext);

        WritingDetailPayload legacyDetail = (WritingDetailPayload) presenter.presentDetail(
                legacyContext, overview("WRITING", legacyPresentation), null);

        assertThat(legacyDetail.upgrade().learnerDerivedUpgrade().available()).isFalse();
        assertThat(legacyDetail.upgrade().evaluatorSample().available()).isFalse();
        assertThat(legacyDetail.diagnosticFindings()).isEmpty();
        assertThat(legacyDetail.tasks()).singleElement().satisfies(task -> {
            assertThat(task.feedback().state()).isEqualTo("FAILED");
            assertThat(task.score().available()).isFalse();
            assertThat(task.officialCriteria()).isEmpty();
        });
        assertThat(legacyDetail.diagnosticAvailability())
                .isEqualTo("FEEDBACK_UNAVAILABLE");

        PracticeAttempt pendingAttempt = mock(PracticeAttempt.class);
        when(pendingAttempt.getAiFeedbackJson()).thenReturn("""
                {"153":{
                  "evaluation_status":"PROCESSING",
                  "score_available":false,
                  "task_type":"Q53",
                  "upgraded_answer":"대기 중 산출물",
                  "sample_answer":"대기 중 참고 답안"
                }}
                """);
        PracticeResultContext pendingContext = context(
                "WRITING",
                List.of(question),
                Map.of("153", "학생 답안"),
                pendingAttempt);
        PracticeResultPresenter.Presentation pendingPresentation =
                presenter.present(pendingContext);

        WritingDetailPayload pendingDetail = (WritingDetailPayload) presenter.presentDetail(
                pendingContext, overview("WRITING", pendingPresentation), null);

        assertThat(pendingDetail.upgrade().learnerDerivedUpgrade().available()).isFalse();
        assertThat(pendingDetail.upgrade().evaluatorSample().available()).isFalse();
        assertThat(pendingDetail.tasks()).singleElement().satisfies(task -> {
            assertThat(task.feedback().state()).isEqualTo("PENDING");
            assertThat(task.feedback().label())
                    .doesNotContain("Không thể xác minh contract");
            assertThat(task.score().available()).isFalse();
            assertThat(task.officialCriteria()).isEmpty();
        });
        assertThat(pendingDetail.feedback().state()).isEqualTo("PENDING");
        assertThat(pendingDetail.diagnosticAvailability()).isEqualTo("FEEDBACK_UNAVAILABLE");
    }

    @Test
    void writingDetailFailsClosedWhenCurrentTrustMarkersAreMissingOrMismatched() {
        PracticeQuestionVersion question = writingQuestion(153L, 53, WritingTaskType.Q53);
        List<String> untrustedEntries = List.of(
                """
                {"153":{
                  "raw_score":24,"raw_score_max":30,"score_available":true,
                  "rubric_scores":[
                    {"criterionId":"W_CONTENT_TASK_ACHIEVEMENT","score":10,"maxScore":12},
                    {"criterionId":"W_ORGANIZATION_COHERENCE","score":7,"maxScore":9},
                    {"criterionId":"W_LANGUAGE_EXPRESSION","score":7,"maxScore":9}
                  ],
                  "upgraded_answer":"유형 없는 산출물"
                }}
                """,
                """
                {"153":{
                  "raw_score":24,"raw_score_max":30,"score_available":true,
                  "task_type":"Q54",
                  "rubric_scores":[
                    {"criterionId":"W_CONTENT_TASK_ACHIEVEMENT","score":10,"maxScore":12},
                    {"criterionId":"W_ORGANIZATION_COHERENCE","score":7,"maxScore":9},
                    {"criterionId":"W_LANGUAGE_EXPRESSION","score":7,"maxScore":9}
                  ],
                  "upgraded_answer":"잘못된 유형 산출물"
                }}
                """,
                """
                {"153":{
                  "raw_score":24,"raw_score_max":30,"score_available":true,
                  "task_type":"Q53",
                  "scoring_contract":"TASK_NATIVE_RUBRIC_V1",
                  "policy_bundle_id":"STALE_WRITING_BUNDLE",
                  "engine":"KSH_WRITING_EVALUATOR_V2",
                  "evaluation_status":"EVALUATED","evaluation_source":"PROVIDER",
                  "evaluation_reason":"NONE","evaluation_retryable":false,
                  "rubric_scores":[
                    {"criterionId":"W_CONTENT_TASK_ACHIEVEMENT","score":10,"maxScore":12},
                    {"criterionId":"W_ORGANIZATION_COHERENCE","score":7,"maxScore":9},
                    {"criterionId":"W_LANGUAGE_EXPRESSION","score":7,"maxScore":9}
                  ],
                  "upgraded_answer":"오래된 정책 산출물"
                }}
                """);

        for (String storedFeedback : untrustedEntries) {
            PracticeAttempt attempt = mock(PracticeAttempt.class);
            when(attempt.getAiFeedbackJson()).thenReturn(storedFeedback);
            WritingResultPresenter presenter = writingPresenter();
            PracticeResultContext context = context(
                    "WRITING",
                    List.of(question),
                    Map.of("153", "학생 답안"),
                    attempt);
            PracticeResultPresenter.Presentation presentation = presenter.present(context);

            WritingDetailPayload detail = (WritingDetailPayload) presenter.presentDetail(
                    context, overview("WRITING", presentation), null);

            assertThat(detail.scoreCriteria()).isEmpty();
            assertThat(detail.tasks()).singleElement().satisfies(task -> {
                assertThat(task.score().available()).isFalse();
                assertThat(task.feedback().state()).isEqualTo("FAILED");
                assertThat(task.officialCriteria()).isEmpty();
            });
            assertThat(detail.feedback().state()).isEqualTo("FAILED");
            assertThat(detail.diagnosticFindings()).isEmpty();
            assertThat(detail.diagnosticAvailability())
                    .isEqualTo("FEEDBACK_UNAVAILABLE");
            assertThat(detail.upgrade().learnerDerivedUpgrade().available()).isFalse();
            assertThat(detail.upgrade().evaluatorSample().available()).isFalse();
        }
    }

    @Test
    void historicalWritingFillBlankUsesLockedAnswerSpecWithoutAiFeedback() {
        PracticeQuestionVersion q51 = writingQuestion(151L, 51, WritingTaskType.Q51);
        when(q51.getQuestionType()).thenReturn("FILL_BLANK");
        when(q51.getOptionsJson()).thenReturn("[]");
        when(q51.getAnswerKey()).thenReturn("서울");
        when(q51.getPoints()).thenReturn(BigDecimal.TEN);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn(null);

        PracticeResultPresenter.Presentation result = writingPresenter().present(context(
                "WRITING",
                List.of(q51),
                Map.of("151", "서울"),
                attempt));
        WritingResultPayload payload = (WritingResultPayload) result.payload();

        assertThat(result.feedback().state()).isEqualTo("READY");
        assertThat(result.answers().scoredDenominator()).isEqualTo(1);
        assertThat(payload.tasks().get(0).score().pointsDisplay()).isEqualTo("10/10");
        assertThat(payload.tasks().get(0).officialCriteria()).isEmpty();
        assertThat(payload.tasks().get(0).analysisLenses()).isEmpty();
        assertThat(payload.tasks().get(0).detailAvailable()).isFalse();
    }

    @Test
    void writingPendingFeedbackRemainsPendingAndIsNotCountedAsFailure() {
        PracticeQuestionVersion question = writingQuestion(153L, 53, WritingTaskType.Q53);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {"153":{"evaluation_status":"PROCESSING","score_available":false}}
                """);
        WritingResultPresenter presenter = writingPresenter();

        PracticeResultPresenter.Presentation result = presenter.present(context(
                "WRITING",
                List.of(question),
                Map.of("153", "Bài viết đang được chấm"),
                attempt));

        assertThat(result.feedback().state()).isEqualTo("PENDING");
        assertThat(result.answers().pending()).isEqualTo(1);
        assertThat(result.answers().unscorable()).isZero();
        assertThat(result.answers().scoredDenominator()).isZero();
        assertThat(result.score().available()).isFalse();
        WritingResultPayload payload = (WritingResultPayload) result.payload();
        assertThat(payload.tasks().get(0).evaluated()).isFalse();
        assertThat(payload.tasks().get(0).officialCriteria()).isEmpty();
        assertThat(payload.tasks().get(0).analysisLenses()).isEmpty();
    }

    @Test
    void currentDeterministicInvalidWritingAnswerKeepsAuthoritativeZeroScoreReady() {
        PracticeQuestionVersion question =
                writingQuestion(153L, 53, WritingTaskType.Q53);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn(writingFeedback(Map.of(
                153L,
                new WritingEvaluationNormalizer(objectMapper)
                        .spamResponse("Q53", "가"))));

        PracticeResultPresenter.Presentation result =
                writingPresenter().present(context(
                        "WRITING",
                        List.of(question),
                        Map.of("153", "가"),
                        attempt));
        WritingResultPayload payload =
                (WritingResultPayload) result.payload();

        assertThat(result.feedback().state()).isEqualTo("READY");
        assertThat(result.answers().scoredDenominator()).isEqualTo(1);
        assertThat(result.answers().pending()).isZero();
        assertThat(result.answers().unscorable()).isZero();
        assertThat(payload.tasks()).singleElement().satisfies(task -> {
            assertThat(task.feedback().state()).isEqualTo("READY");
            assertThat(task.score().available()).isTrue();
            assertThat(task.score().pointsDisplay()).isEqualTo("0/30");
            assertThat(task.officialCriteria()).hasSize(3);
        });
    }

    @Test
    void staleDeterministicInvalidReasonCannotAuthorizeReadyWritingScore() {
        PracticeQuestionVersion question =
                writingQuestion(153L, 53, WritingTaskType.Q53);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {"153":{
                  "raw_score":0,"raw_score_max":30,"score_available":true,
                  "task_type":"Q53",
                  "scoring_contract":"TASK_NATIVE_RUBRIC_V1",
                  "policy_bundle_id":"KSH_WRITING_POLICY_BUNDLE_V2",
                  "engine":"KSH_WRITING_EVALUATOR_V2",
                  "evaluation_status":"INVALID_LEARNER_RESPONSE",
                  "evaluation_source":"BACKEND_RULE",
                  "evaluation_reason":"EMPTY_OR_TOO_SHORT",
                  "evaluation_retryable":false}}
                """);

        PracticeResultPresenter.Presentation result =
                writingPresenter().present(context(
                        "WRITING",
                        List.of(question),
                        Map.of("153", "가"),
                        attempt));
        WritingResultPayload payload =
                (WritingResultPayload) result.payload();

        assertThat(result.feedback().state()).isEqualTo("FAILED");
        assertThat(result.answers().scoredDenominator()).isZero();
        assertThat(result.answers().unscorable()).isEqualTo(1);
        assertThat(result.score().available()).isFalse();
        assertThat(payload.tasks()).singleElement().satisfies(task -> {
            assertThat(task.feedback().state()).isEqualTo("FAILED");
            assertThat(task.score().available()).isFalse();
            assertThat(task.officialCriteria()).isEmpty();
        });
    }

    @Test
    void retryableProviderEnvelopeCannotAuthorizeReadyWritingScore() {
        PracticeQuestionVersion question =
                writingQuestion(153L, 53, WritingTaskType.Q53);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {"153":{
                  "raw_score":20,"raw_score_max":30,"score_available":true,
                  "task_type":"Q53",
                  "scoring_contract":"TASK_NATIVE_RUBRIC_V1",
                  "policy_bundle_id":"KSH_WRITING_POLICY_BUNDLE_V2",
                  "engine":"KSH_WRITING_EVALUATOR_V2",
                  "evaluation_status":"EVALUATED",
                  "evaluation_source":"PROVIDER",
                  "evaluation_reason":"NONE",
                  "evaluation_retryable":true}}
                """);

        PracticeResultPresenter.Presentation result =
                writingPresenter().present(context(
                        "WRITING",
                        List.of(question),
                        Map.of("153", "한국어 답안"),
                        attempt));
        WritingResultPayload payload =
                (WritingResultPayload) result.payload();

        assertThat(result.feedback().state()).isEqualTo("FAILED");
        assertThat(result.answers().scoredDenominator()).isZero();
        assertThat(result.answers().unscorable()).isEqualTo(1);
        assertThat(result.score().available()).isFalse();
        assertThat(payload.tasks()).singleElement().satisfies(task -> {
            assertThat(task.feedback().state())
                    .isEqualTo("FAILED");
            assertThat(task.score().available()).isFalse();
            assertThat(task.officialCriteria()).isEmpty();
        });
    }

    @Test
    void missingOrMistypedWritingProvenanceCannotAuthorizeScoreDetailOrUpgrade() {
        PracticeQuestionVersion question =
                writingQuestion(153L, 53, WritingTaskType.Q53);
        String exactCurrent = """
                {"153":{
                  "raw_score":20,"raw_score_max":30,"score_available":true,
                  "task_type":"Q53",
                  "scoring_contract":"TASK_NATIVE_RUBRIC_V1",
                  "policy_bundle_id":"KSH_WRITING_POLICY_BUNDLE_V2",
                  "engine":"KSH_WRITING_EVALUATOR_V2",
                  "evaluation_status":"EVALUATED",
                  "evaluation_source":"PROVIDER",
                  "evaluation_reason":"NONE",
                  "evaluation_retryable":false,
                  "rubric_scores":[
                    {"criterionId":"W_CONTENT_TASK_ACHIEVEMENT","score":8,"maxScore":12},
                    {"criterionId":"W_ORGANIZATION_COHERENCE","score":6,"maxScore":9},
                    {"criterionId":"W_LANGUAGE_EXPRESSION","score":6,"maxScore":9}
                  ],
                  "upgraded_answer":"검증되지 않은 업그레이드"}}
                """;
        List<String> malformedProvenance = List.of(
                exactCurrent.replace(
                        "\"evaluation_reason\":\"NONE\",", ""),
                exactCurrent.replace(
                        "\"evaluation_reason\":\"NONE\"",
                        "\"evaluation_reason\":7"),
                exactCurrent.replace(
                        "\"evaluation_retryable\":false,", ""),
                exactCurrent.replace(
                        "\"evaluation_retryable\":false",
                        "\"evaluation_retryable\":\"false\""));

        for (String storedFeedback : malformedProvenance) {
            PracticeAttempt attempt = mock(PracticeAttempt.class);
            when(attempt.getAiFeedbackJson()).thenReturn(storedFeedback);
            WritingResultPresenter presenter = writingPresenter();
            PracticeResultContext context = context(
                    "WRITING",
                    List.of(question),
                    Map.of("153", "한국어 답안"),
                    attempt);

            PracticeResultPresenter.Presentation presentation =
                    presenter.present(context);
            WritingResultPayload payload =
                    (WritingResultPayload) presentation.payload();
            WritingDetailPayload detail =
                    (WritingDetailPayload) presenter.presentDetail(
                            context,
                            overview("WRITING", presentation),
                            153L);

            assertThat(presentation.feedback().state())
                    .isEqualTo("FAILED");
            assertThat(presentation.score().available()).isFalse();
            assertThat(payload.tasks()).singleElement().satisfies(task -> {
                assertThat(task.feedback().state())
                        .isEqualTo("FAILED");
                assertThat(task.score().available()).isFalse();
                assertThat(task.officialCriteria()).isEmpty();
            });
            assertThat(detail.scoreCriteria()).isEmpty();
            assertThat(detail.upgrade().learnerDerivedUpgrade().available())
                    .isFalse();
        }
    }

    @Test
    void writingUnavailableFeedbackDoesNotFabricateScoreRubricOrDiagnostics() {
        PracticeQuestionVersion question = writingQuestion(154L, 54, WritingTaskType.Q54);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {"154":{"evaluation_status":"EVALUATION_UNAVAILABLE",
                  "evaluation_reason":"PROVIDER_UNAVAILABLE","score_available":false}}
                """);

        PracticeResultPresenter.Presentation result = writingPresenter().present(context(
                "WRITING",
                List.of(question),
                Map.of("154", "Bài viết đã được nộp"),
                attempt));
        WritingResultPayload payload = (WritingResultPayload) result.payload();

        assertThat(result.feedback().state()).isEqualTo("UNAVAILABLE");
        assertThat(result.answers().unscorable()).isEqualTo(1);
        assertThat(payload.tasks().get(0).answered()).isTrue();
        assertThat(payload.tasks().get(0).evaluated()).isFalse();
        assertThat(payload.tasks().get(0).score().available()).isFalse();
        assertThat(payload.tasks().get(0).score().scaleLabel()).isEqualTo("Thang điểm 50");
        assertThat(payload.tasks().get(0).officialCriteria()).isEmpty();
        assertThat(payload.tasks().get(0).analysisLenses()).isEmpty();
    }

    @Test
    void writingRejectsRubricRowsWhoseMaxDoesNotMatchTheTaskPolicy() {
        PracticeQuestionVersion question = writingQuestion(153L, 53, WritingTaskType.Q53);
        String learnerAnswer = "Bài viết đã nộp";
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        String invalid = currentWritingEvaluation(
                "Q53", learnerAnswer, 10, 7, 7);
        when(attempt.getAiFeedbackJson()).thenReturn(
                writingFeedback(Map.of(
                        153L,
                        tamperWritingRubricMaximum(
                                invalid,
                                "W_CONTENT_TASK_ACHIEVEMENT",
                                10))));

        PracticeResultPresenter.Presentation result = writingPresenter().present(context(
                "WRITING", List.of(question), Map.of("153", learnerAnswer), attempt));
        WritingResultPayload payload = (WritingResultPayload) result.payload();

        assertThat(result.feedback().state()).isEqualTo("FAILED");
        assertThat(payload.tasks().get(0).score().available()).isFalse();
        assertThat(payload.tasks().get(0).score().scaleLabel()).isEqualTo("Thang điểm 30");
        assertThat(payload.tasks().get(0).officialCriteria()).isEmpty();
        assertThat(payload.tasks().get(0).analysisLenses()).isEmpty();
    }

    @Test
    void malformedWritingObjectRemainsNeutralAndUnscorable() {
        PracticeQuestionVersion question = writingQuestion(153L, 53, WritingTaskType.Q53);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("{\"153\":{\"summary_vi\":\"Không đủ contract\"}}");
        WritingResultPresenter presenter = writingPresenter();

        PracticeResultPresenter.Presentation result = presenter.present(context(
                "WRITING",
                List.of(question),
                Map.of("153", "Bài viết đã nộp"),
                attempt));
        WritingResultPayload payload = (WritingResultPayload) result.payload();

        assertThat(result.feedback().state()).isEqualTo("FAILED");
        assertThat(result.answers().unscorable()).isEqualTo(1);
        assertThat(payload.tasks().get(0).score().available()).isFalse();
        assertThat(payload.tasks().get(0).summary()).isNull();
        assertThat(payload.tasks().get(0).officialCriteria()).isEmpty();
        assertThat(payload.tasks().get(0).analysisLenses()).isEmpty();
    }

    @Test
    void malformedStoredWritingJsonFailsInsteadOfRemainingPendingForever() {
        PracticeQuestionVersion question = writingQuestion(153L, 53, WritingTaskType.Q53);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("{not-json");

        PracticeResultPresenter.Presentation result = writingPresenter().present(context(
                "WRITING",
                List.of(question),
                Map.of("153", "Bài viết đã nộp"),
                attempt));

        assertThat(result.feedback().state()).isEqualTo("FAILED");
        assertThat(result.answers().pending()).isZero();
        assertThat(result.answers().unscorable()).isEqualTo(1);
        assertThat(result.score().available()).isFalse();
    }

    @Test
    void unansweredWritingTaskIsUnavailableAndNeverUsesStoredFeedback() {
        PracticeQuestionVersion question = writingQuestion(153L, 53, WritingTaskType.Q53);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {"153":{"raw_score":30,"raw_score_max":30,"score_available":true,
                  "rubric_scores":[
                    {"criterionId":"W_CONTENT_TASK_ACHIEVEMENT","score":12,"maxScore":12},
                    {"criterionId":"W_ORGANIZATION_COHERENCE","score":9,"maxScore":9},
                    {"criterionId":"W_LANGUAGE_EXPRESSION","score":9,"maxScore":9}
                  ]}}
                """);
        WritingResultPresenter presenter = writingPresenter();

        PracticeResultPresenter.Presentation result = presenter.present(context(
                "WRITING",
                List.of(question),
                Map.of("153", ""),
                attempt));
        WritingResultPayload payload = (WritingResultPayload) result.payload();

        assertThat(result.feedback().state()).isEqualTo("UNAVAILABLE");
        assertThat(result.answers().notAnswered()).isEqualTo(1);
        assertThat(result.score().available()).isFalse();
        assertThat(payload.tasks().get(0).feedback().state()).isEqualTo("UNAVAILABLE");
        assertThat(payload.tasks().get(0).score().available()).isFalse();
        assertThat(payload.tasks().get(0).officialCriteria()).isEmpty();
        assertThat(payload.tasks().get(0).analysisLenses()).isEmpty();
    }

    @Test
    void speakingOverviewAggregatesAllSegmentsWithoutReturningPerQuestionPanels() throws Exception {
        PracticeQuestionVersion first = speakingQuestion(201L);
        PracticeQuestionVersion second = speakingQuestion(202L);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        SpeakingEvaluationResult firstResult =
                SpeakingEvaluationTestFixtures.currentResult(
                        objectMapper,
                        "first transcript",
                        new BigDecimal("16"),
                        provider -> {
                            provider.put("overall_summary",
                                    "Ý chính rõ và đúng chủ đề.");
                            provider.withArray("action_plan")
                                    .addObject()
                                    .put("criterion_id",
                                            "S_GRAMMAR_SENTENCE_CONTROL")
                                    .put("sub_criterion_id",
                                            "S_GRAMMAR_PARTICLES")
                                    .put("title",
                                            "Ôn trợ từ theo bản chép lời")
                                    .put("instruction",
                                            "Sửa ba câu dùng trợ từ chưa phù hợp.")
                                    .put("reason",
                                            "Củng cố kiểm soát câu.")
                                    .put("priority", "HIGH");
                        });
        SpeakingEvaluationResult secondResult =
                SpeakingEvaluationTestFixtures.currentResult(
                        objectMapper,
                        "second transcript",
                        new BigDecimal("18"),
                        provider -> provider.put("overall_summary",
                                "Diễn đạt phù hợp và có tiến bộ."));
        when(attempt.getAiFeedbackJson()).thenReturn(speakingFeedback(
                Map.of(201L, firstResult, 202L, secondResult)));
        SpeakingResultPresenter presenter = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackContractParser());

        PracticeResultContext speakingContext = context(
                "SPEAKING",
                List.of(first, second),
                Map.of("201", "first transcript", "202", "second transcript"),
                attempt);
        PracticeResultPresenter.Presentation result = presenter.present(speakingContext);
        SpeakingResultPayload payload = (SpeakingResultPayload) result.payload();

        assertThat(payload.coveredSegments()).isEqualTo(2);
        assertThat(payload.totalSegments()).isEqualTo(2);
        assertThat(payload.overallSummaries()).singleElement()
                .asString()
                .contains(
                        "2 phần trả lời",
                        "bằng chứng trong bản chép lời",
                        "Phát âm/Thể hiện chưa thể chấm");
        assertThat(payload.overallSummaries())
                .noneMatch(summary -> summary.contains(
                        "Ý chính rõ")
                        || summary.contains("có tiến bộ"));
        assertThat(payload.strengths()).hasSize(2)
                .allSatisfy(finding -> {
                    assertThat(finding.findingId()).isNotBlank();
                    assertThat(finding.evidenceId()).isNotBlank();
                    assertThat(finding.questionId()).isIn(201L, 202L);
                });
        assertThat(payload.needsImprovement()).isEmpty();
        assertThat(payload.criteria()).hasSize(6);
        assertThat(payload.criteria().stream()
                .filter(criterion -> !criterion.criterionId().equals("S_FLUENCY")
                        && !criterion.criterionId().equals("S_PRONUNCIATION_DELIVERY")))
                .allSatisfy(criterion -> {
                    assertThat(criterion.coveredSegments()).isEqualTo(2);
                    assertThat(criterion.availability()).isEqualTo("SCORED");
                });
        assertThat(payload.criteria().stream()
                .filter(criterion -> criterion.criterionId().equals("S_FLUENCY")
                        || criterion.criterionId().equals("S_PRONUNCIATION_DELIVERY")))
                .allSatisfy(criterion -> {
                    assertThat(criterion.availability()).isEqualTo("NOT_SCORABLE");
                    assertThat(criterion.score()).isNull();
                    assertThat(criterion.percentage()).isNull();
                    assertThat(criterion.scoreDisplay()).isNull();
                });
        assertThat(payload.evidenceMode()).isEqualTo("TRANSCRIPT_ONLY");
        assertThat(payload.evaluatorCapability()).isEqualTo("TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION");
        assertThat(payload.contractTrust()).isEqualTo("CURRENT_VERIFIED");
        assertThat(payload.policyBundleId())
                .isEqualTo(SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID);
        assertThat(payload.policyBundleFingerprint())
                .isEqualTo(SpeakingAssessmentPolicyBundle.fingerprint());
        assertThat(payload.profileState()).isEqualTo("READY");
        assertThat(payload.holisticScoreAvailable()).isFalse();
        assertThat(payload.holisticScore().available()).isFalse();
        assertThat(payload.overviewCapability("HOLISTIC_SCORE").availability())
                .isEqualTo(ResultOverviewCapabilityAvailability.NOT_SCORABLE);
        assertThat(payload.overviewCapability("CRITERION_RADAR").availability())
                .isEqualTo(ResultOverviewCapabilityAvailability.AVAILABLE);
        assertThat(payload.overviewCapability("PART_PERFORMANCE").availability())
                .isEqualTo(ResultOverviewCapabilityAvailability.AVAILABLE);
        assertThat(payload.overviewCapability("NAMED_CRITERION_SUBMETRICS").availability())
                .isEqualTo(ResultOverviewCapabilityAvailability.AVAILABLE);
        assertThat(payload.radarAxes()).hasSize(4)
                .allSatisfy(axis -> {
                    assertThat(axis.scored()).isTrue();
                    assertThat(axis.percentage()).isEqualByComparingTo(
                            axis.earned().multiply(BigDecimal.valueOf(100))
                                    .divide(axis.possible(), 2,
                                            java.math.RoundingMode.HALF_UP));
                    assertThat(axis.performanceLevel().scored()).isTrue();
                });
        assertThat(payload.radarPolygonPoints()).isNotBlank();
        assertThat(payload.unavailableAcousticAxes()).hasSize(2)
                .allSatisfy(axis -> {
                    assertThat(axis.availability()).isEqualTo("NOT_SCORABLE");
                    assertThat(axis.earned()).isNull();
                    assertThat(axis.percentage()).isNull();
                });
        assertThat(payload.questionPerformance()).hasSize(2)
                .allSatisfy(question -> {
                    assertThat(question.ready()).isTrue();
                    assertThat(question.languageCriteria()).hasSize(4);
                });
        assertThat(payload.submetricPerformance()).isNotEmpty()
                .allSatisfy(submetric -> {
                    assertThat(submetric.anchorLevel().scored()).isTrue();
                    assertThat(submetric.findingCount()).isPositive();
                    assertThat(submetric.questionIds()).isNotEmpty();
                });
        assertThat(payload.actionPlan()).isEmpty();
        assertThat(objectMapper.writeValueAsString(payload))
                .doesNotContain(
                        "Ôn trợ từ theo bản chép lời",
                        "Ý chính rõ và đúng chủ đề",
                        "Diễn đạt phù hợp và có tiến bộ");

        SpeakingDetailPayload selected = (SpeakingDetailPayload) presenter.presentDetail(
                speakingContext, overview("SPEAKING", result), 202L);
        assertThat(selected.activeQuestionId()).isEqualTo(202L);
        assertThat(selected.tasks()).hasSize(2);
        assertThat(selected.tasks())
                .filteredOn(task ->
                        task.questionId().equals(
                                selected.activeQuestionId()))
                .singleElement()
                .satisfies(task -> assertThat(task.summary())
                        .startsWith("Đã xác minh ")
                        .contains(
                                "bằng chứng bản chép lời",
                                "nhận xét nguyên tử",
                                "Tiêu chí âm học chưa chấm")
                        .doesNotContain(
                                "Ý chính rõ",
                                "có tiến bộ"));
        assertThat(selected.tasks())
                .filteredOn(task -> !task.questionId().equals(selected.activeQuestionId()))
                .singleElement()
                .satisfies(task -> {
                    assertThat(task.prompt()).isEmpty();
                    assertThat(task.learnerSubmissionText()).isEmpty();
                    assertThat(task.summary()).isEmpty();
                    assertThat(task.submissionState()).isEqualTo("NAVIGATION_ONLY");
                    assertThat(task.evaluationState()).isEqualTo("NAVIGATION_ONLY");
                });
        assertThat(selected.evidence().transcriptText()).isEqualTo("second transcript");
        assertThat(selected.scoreCriteria()).allSatisfy(criterion ->
                assertThat(criterion.questionId()).isEqualTo(202L));
        assertThat(selected.scoreCriteria())
                .filteredOn(criterion -> criterion.criterionId()
                        .equals("S_CONTENT_TASK_FULFILLMENT"))
                .singleElement()
                .satisfies(criterion -> {
                    assertThat(criterion.score()).isEqualByComparingTo("18");
                    assertThat(criterion.feedbackVi()).isNotBlank();
                });
    }

    @Test
    void speakingPerformanceLevelsUseEachCriterionOwnDenominatorAndKeepAudioNa() {
        SpeakingCriterionResult fourteenOfTwenty = new SpeakingCriterionResult(
                "S_CONTENT_TASK_FULFILLMENT", "Nội dung",
                new BigDecimal("20"), new BigDecimal("14"),
                new BigDecimal("70"), 1, 1,
                ResultEvaluationBand.fromPercentage(new BigDecimal("70")),
                null, false, "SCORED", false);
        SpeakingCriterionResult twelveOfFifteen = new SpeakingCriterionResult(
                "S_VOCABULARY_EXPRESSIONS", "Từ vựng",
                new BigDecimal("15"), new BigDecimal("12"),
                new BigDecimal("80"), 1, 1,
                ResultEvaluationBand.fromPercentage(new BigDecimal("80")),
                null, false, "SCORED", false);
        SpeakingCriterionResult acousticUnavailable = new SpeakingCriterionResult(
                "S_FLUENCY", "Độ lưu loát",
                null, null, null, 0, 1,
                ResultEvaluationBand.UNAVAILABLE,
                null, false, "NOT_SCORABLE", true);

        assertThat(fourteenOfTwenty.performanceLevel().code()).isEqualTo("GOOD");
        assertThat(twelveOfFifteen.performanceLevel().code()).isEqualTo("EXCELLENT");
        assertThat(acousticUnavailable.performanceLevel().code())
                .isEqualTo("NOT_SCORABLE");
        assertThat(acousticUnavailable.percentage()).isNull();
    }

    @Test
    void speakingDetailGroupsSelectedValidatedFindingsByKshFamilyAndSubcriterion() {
        PracticeQuestionVersion question = speakingQuestion(201L);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        String transcript = "저는 학교에 가요 그리고 친구를 만나요";
        when(attempt.getAiFeedbackJson()).thenReturn(currentSpeakingFeedback(
                transcript,
                List.of(
                        speakingFinding(
                                "SF-PARTICLE-PLACE",
                                "SEV-PARTICLE-PLACE",
                                SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                                "S_GRAMMAR_PARTICLES",
                                "학교에",
                                transcript.indexOf("학교에"),
                                "needs_improvement",
                                "REPLACE",
                                "GRAMMAR",
                                "MEDIUM",
                                "Cần kiểm tra tiểu từ chỉ nơi chốn.",
                                "학교에서"),
                        speakingFinding(
                                "SF-PARTICLE-OBJECT",
                                "SEV-PARTICLE-OBJECT",
                                SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                                "S_GRAMMAR_PARTICLES",
                                "친구를",
                                transcript.indexOf("친구를"),
                                "needs_improvement",
                                "REPLACE",
                                "GRAMMAR",
                                "MEDIUM",
                                "Cần củng cố tiểu từ tân ngữ.",
                                "친구와"),
                        speakingFinding(
                                "SF-ENDING",
                                "SEV-ENDING",
                                SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                                "S_GRAMMAR_ENDINGS",
                                "가요",
                                transcript.indexOf("가요"),
                                "needs_improvement",
                                "REPLACE",
                                "GRAMMAR",
                                "MEDIUM",
                                "Cần thống nhất đuôi câu.",
                                "갑니다"))));
        SpeakingResultPresenter presenter = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackContractParser());
        PracticeResultContext context = context(
                "SPEAKING",
                List.of(question),
                Map.of("201", transcript),
                attempt);
        PracticeResultPresenter.Presentation presentation = presenter.present(context);

        SpeakingDetailPayload detail = (SpeakingDetailPayload) presenter.presentDetail(
                context, overview("SPEAKING", presentation), null);

        assertThat(detail.activeQuestionId()).isEqualTo(201L);
        assertThat(detail.evidence().transcriptMediaBinding()).isEqualTo("UNVERIFIED");
        assertThat(detail.diagnosticGroups()).hasSize(5);
        assertThat(detail.diagnosticGroups())
                .filteredOn(group -> group.categoryCode().equals("MORPHOSYNTAX"))
                .singleElement().satisfies(group -> {
            assertThat(group.categoryCode()).isEqualTo("MORPHOSYNTAX");
            assertThat(group.labelVi()).contains("Hình thái");
            assertThat(group.labelKo()).contains("형태");
        });
        assertThat(detail.diagnosticFindings()).hasSize(3);
        assertThat(detail.diagnosticFindings()).allSatisfy(finding ->
                assertThat(finding.questionId()).isEqualTo(201L));
        assertThat(detail.filterChips()).hasSize(32);
        assertThat(detail.filterChips())
                .filteredOn(chip -> chip.id().contains("S_GRAMMAR_PARTICLES")
                        && chip.polarity() == ResultDetailPolarity.NEEDS_IMPROVEMENT)
                .singleElement()
                .satisfies(chip -> {
                    assertThat(chip.labelVi()).isEqualTo("Tiểu từ");
                    assertThat(chip.labelKo()).isEqualTo("조사");
                    assertThat(chip.parentCriterionId())
                            .isEqualTo("S_GRAMMAR_SENTENCE_CONTROL");
                    assertThat(chip.count()).isEqualTo(2);
                    assertThat(chip.countedSeparately()).isFalse();
                });
        assertThat(detail.filterChips())
                .filteredOn(chip -> chip.id().contains("S_GRAMMAR_ENDINGS")
                        && chip.polarity() == ResultDetailPolarity.NEEDS_IMPROVEMENT)
                .singleElement()
                .satisfies(chip -> {
                    assertThat(chip.labelVi()).isEqualTo("Đuôi câu và vĩ tố");
                    assertThat(chip.labelKo()).isEqualTo("문장 종결형과 어미");
                    assertThat(chip.parentCriterionId())
                            .isEqualTo("S_GRAMMAR_SENTENCE_CONTROL");
                    assertThat(chip.count()).isEqualTo(1);
                    assertThat(chip.countedSeparately()).isFalse();
                });
        assertThat(detail.filterChips())
                .filteredOn(chip -> chip.count() == 0)
                .hasSize(30)
                .allSatisfy(chip -> assertThat(chip.evidenceAvailability())
                        .isEqualTo("NO_FINDING"));
        assertThat(detail.filterChips())
                .extracting(chip -> chip.id())
                .doesNotHaveDuplicates();
        assertThat(detail.hasUpgradeForDescriptor(
                "D_S_GRAMMAR_PARTICLES_NEEDS_IMPROVEMENT")).isTrue();
        assertThat(detail.hasUpgradeForDescriptor(
                "D_S_GRAMMAR_ENDINGS_NEEDS_IMPROVEMENT")).isTrue();
        assertThat(detail.hasUpgradeForDescriptor(
                "D_S_VOCAB_TOPIC_WORDS_NEEDS_IMPROVEMENT")).isFalse();
        assertThat(detail.hasUpgradeForGroup("MORPHOSYNTAX")).isTrue();
        assertThat(detail.hasUpgradeForGroup("LEXICON_COLLOCATION")).isFalse();
        assertThat(detail.scoreCriteria())
                .filteredOn(criterion -> criterion.criterionId()
                .equals("S_GRAMMAR_SENTENCE_CONTROL"))
                .hasSize(1);
    }

    @Test
    void speakingDetailSegmentsOnlyNonOverlappingCurrentTranscriptAnnotations() {
        PracticeQuestionVersion question = speakingQuestion(201L);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        SpeakingResultPresenter presenter = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackContractParser());

        when(attempt.getAiFeedbackJson()).thenReturn(currentSpeakingFeedback(
                "alpha beta gamma",
                List.of(
                        speakingFinding(
                                "SF-GRAMMAR-1", "SEV-GRAMMAR-SPAN-1",
                                SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                                "S_GRAMMAR_PARTICLES", "alpha", 0,
                                "needs_improvement", "REPLACE",
                                "GRAMMAR", "MEDIUM",
                                "Cần sửa tiểu từ.", "알파"),
                        speakingFinding(
                                "SF-VOCAB-1", "SEV-VOCAB-SPAN-1",
                                SpeakingRubricCriterion.VOCABULARY_EXPRESSIONS,
                                "S_VOCAB_TOPIC_WORDS", "gamma", 11,
                                "strength", "KEEP",
                                "VOCABULARY", "LOW",
                                "Từ vựng theo chủ đề phù hợp.", ""))));
        PracticeResultContext currentContext = context(
                "SPEAKING",
                List.of(question),
                Map.of("201", "AUDIO_SUBMITTED"),
                attempt);
        PracticeResultPresenter.Presentation currentPresentation =
                presenter.present(currentContext);
        SpeakingResultPayload currentOverview =
                (SpeakingResultPayload) currentPresentation.payload();

        assertThat(currentOverview.strengths()).singleElement()
                .satisfies(finding -> {
                    assertThat(finding.findingId())
                            .isEqualTo("SF-VOCAB-1");
                    assertThat(finding.evidenceId())
                            .isEqualTo("SEV-VOCAB-SPAN-1");
                    assertThat(finding.exactText())
                            .isEqualTo("gamma");
                });
        assertThat(currentOverview.needsImprovement()).singleElement()
                .satisfies(finding -> {
                    assertThat(finding.findingId())
                            .isEqualTo("SF-GRAMMAR-1");
                    assertThat(finding.evidenceId())
                            .isEqualTo("SEV-GRAMMAR-SPAN-1");
                    assertThat(finding.exactText())
                            .isEqualTo("alpha");
                });
        assertThat(currentOverview.actionPlan()).singleElement()
                .satisfies(item -> {
                    assertThat(item.findingId())
                            .isEqualTo("SF-GRAMMAR-1");
                    assertThat(item.evidenceId())
                            .isEqualTo("SEV-GRAMMAR-SPAN-1");
                    assertThat(item.instructionVi())
                            .contains("alpha", "알파");
                });

        SpeakingDetailPayload current = (SpeakingDetailPayload) presenter.presentDetail(
                currentContext, overview("SPEAKING", currentPresentation), 201L);

        assertThat(current.transcriptSegments())
                .extracting(SpeakingTextSegment::text)
                .containsExactly("alpha", " beta ", "gamma");
        assertThat(current.transcriptSegments().stream()
                .map(SpeakingTextSegment::text)
                .collect(java.util.stream.Collectors.joining()))
                .isEqualTo(current.evidence().transcriptText());
        assertThat(current.transcriptSegments())
                .filteredOn(SpeakingTextSegment::annotated)
                .satisfiesExactly(
                        segment -> {
                            assertThat(segment.kind()).isEqualTo("NEEDS_IMPROVEMENT");
                            assertThat(segment.descriptorId())
                                    .isEqualTo(
                                            "D_S_GRAMMAR_PARTICLES_NEEDS_IMPROVEMENT");
                            assertThat(segment.featureId())
                                    .isEqualTo("S_GRAMMAR_PARTICLES");
                            assertThat(segment.explanationVi())
                                    .isEqualTo("Cần sửa tiểu từ.");
                            assertThat(segment.correctionKo()).isEqualTo("알파");
                        },
                        segment -> {
                            assertThat(segment.kind()).isEqualTo("STRENGTH");
                            assertThat(segment.descriptorId())
                                    .isEqualTo("D_S_VOCAB_TOPIC_WORDS_STRENGTH");
                            assertThat(segment.featureId())
                                    .isEqualTo("S_VOCAB_TOPIC_WORDS");
                            assertThat(segment.correctionKo()).isNull();
                        });
        assertThat(current.transcriptSegments())
                .filteredOn(SpeakingTextSegment::annotated)
                .allSatisfy(segment -> assertThat(segment.memberships())
                        .singleElement()
                        .satisfies(membership -> {
                            assertThat(membership.startOffset()).isNotNegative();
                            assertThat(membership.endOffset())
                                    .isGreaterThan(membership.startOffset());
                            assertThat(membership.findingId()).isNotBlank();
                            assertThat(membership.evidenceId()).isNotBlank();
                            assertThat(membership.scopedDisplayNumber())
                                    .isEqualTo(1);
                        }));

        when(attempt.getAiFeedbackJson()).thenReturn(currentSpeakingFeedback(
                "alpha beta gamma",
                List.of(
                        speakingFinding(
                                "SF-GRAMMAR-OVERLAP",
                                "SEV-GRAMMAR-OVERLAP",
                                SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                                "S_GRAMMAR_PARTICLES", "alpha beta", 0,
                                "needs_improvement", "REPLACE",
                                "GRAMMAR", "MEDIUM",
                                "Cần sửa tiểu từ.", "알파 베타"),
                        speakingFinding(
                                "SF-VOCAB-OVERLAP",
                                "SEV-VOCAB-OVERLAP",
                                SpeakingRubricCriterion.VOCABULARY_EXPRESSIONS,
                                "S_VOCAB_TOPIC_WORDS", "beta gamma", 6,
                                "strength", "KEEP",
                                "VOCABULARY", "LOW",
                                "Từ vựng theo chủ đề phù hợp.", ""))));
        PracticeResultPresenter.Presentation overlapPresentation =
                presenter.present(currentContext);

        SpeakingDetailPayload overlap = (SpeakingDetailPayload) presenter.presentDetail(
                currentContext, overview("SPEAKING", overlapPresentation), 201L);

        assertThat(overlap.transcriptSegments().stream()
                .map(SpeakingTextSegment::text)
                .collect(java.util.stream.Collectors.joining()))
                .isEqualTo("alpha beta gamma");
        assertThat(overlap.transcriptSegments())
                .extracting(SpeakingTextSegment::text)
                .containsExactly("alpha ", "beta", " gamma");
        assertThat(overlap.transcriptSegments().get(1).memberships())
                .extracting(ResultDetailSpanMembership::findingId)
                .containsExactly(
                        "SF-GRAMMAR-OVERLAP",
                        "SF-VOCAB-OVERLAP");
        assertThat(overlap.transcriptSegments().get(1).memberships())
                .extracting(ResultDetailSpanMembership::scopedDisplayNumber)
                .containsExactly(1, 1);
    }

    @Test
    void speakingDetailTreatsAudioSubmissionMarkerAsSourceStateNotTranscriptText() {
        PracticeQuestionVersion question = speakingQuestion(201L);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        PracticeResultContext context = context(
                "SPEAKING",
                List.of(question),
                Map.of("201", "AUDIO_SUBMITTED"),
                attempt);
        SpeakingResultPresenter presenter = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackContractParser());
        PracticeResultPresenter.Presentation presentation = presenter.present(context);

        SpeakingDetailPayload detail = (SpeakingDetailPayload) presenter.presentDetail(
                context, overview("SPEAKING", presentation), null);

        assertThat(detail.evidenceMode()).isEqualTo("RECORDING_SOURCE_ONLY");
        assertThat(detail.tasks()).singleElement().satisfies(task -> {
            assertThat(task.learnerSubmissionText()).isBlank();
            assertThat(task.submissionState())
                    .isEqualTo("AUDIO_SOURCE_TRANSCRIPT_UNAVAILABLE");
        });
        assertThat(detail.evidence().transcriptAvailable()).isFalse();
        assertThat(detail.evidence().transcriptText()).isBlank();
        assertThat(detail.evidence().recordingState())
                .isEqualTo("SUBMISSION_MARKER_ONLY");
        assertThat(detail.scoreCriteria().subList(4, 6)).allSatisfy(criterion ->
                assertThat(criterion.availability()).isEqualTo("NOT_SCORABLE"));
    }

    @Test
    void speakingDetailExposesOnlyOwnerBoundReadyPlaybackWithoutClaimingAcousticScoring() {
        PracticeQuestionVersion question = speakingQuestion(201L);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getId()).thenReturn(501L);
        when(attempt.getUserId()).thenReturn(601L);
        PracticeSpeakingMediaService mediaService = mock(PracticeSpeakingMediaService.class);
        when(mediaService.findReadyMediaViewsForOwner(601L, 501L)).thenReturn(List.of(
                new SpeakingMediaView(
                        701L,
                        201L,
                        "READY",
                        4096L,
                        32000L,
                        "audio/webm",
                        "/practice/attempts/501/questions/201/speaking-media/701/content",
                        1L)));
        PracticeResultContext context = context(
                "SPEAKING",
                List.of(question),
                Map.of("201", "AUDIO_SUBMITTED"),
                attempt);
        SpeakingResultPresenter presenter = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackContractParser(),
                mediaService,
                true);
        PracticeResultPresenter.Presentation presentation = presenter.present(context);

        SpeakingDetailPayload detail = (SpeakingDetailPayload) presenter.presentDetail(
                context, overview("SPEAKING", presentation), null);

        assertThat(detail.evidence().recordingState())
                .isEqualTo("READY_OWNER_BOUND_RECORDING");
        assertThat(detail.evidence().playbackAvailable()).isTrue();
        assertThat(detail.evidence().playbackPath()).endsWith("/content");
        assertThat(detail.evidence().transcriptMediaBinding())
                .isEqualTo("NOT_APPLICABLE");
        assertThat(detail.evidence().acousticEvidenceAvailability())
                .isEqualTo("NOT_SCORABLE");
        assertThat(detail.evaluatorCapability()).isEqualTo("LEGACY_UNKNOWN");
    }

    @Test
    void speakingPartialCoverageKeepsMissingSegmentsOutOfScoresAndCoverage() {
        PracticeQuestionVersion ready = speakingQuestion(201L);
        PracticeQuestionVersion pending = speakingQuestion(202L);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn(
                speakingFeedbackWithPending(
                        201L,
                        SpeakingEvaluationTestFixtures.currentResult(
                                objectMapper,
                                "ready transcript",
                                new BigDecimal("16")),
                        202L));

        PracticeResultPresenter.Presentation result = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackContractParser()).present(context(
                "SPEAKING", List.of(ready, pending),
                Map.of("201", "ready transcript", "202", "pending transcript"), attempt));
        SpeakingResultPayload payload = (SpeakingResultPayload) result.payload();

        assertThat(result.feedback().state()).isEqualTo("PARTIAL");
        assertThat(result.score().available()).isFalse();
        assertThat(payload.profileState()).isEqualTo("PARTIAL");
        assertThat(payload.coveredSegments()).isEqualTo(1);
        assertThat(payload.totalSegments()).isEqualTo(2);
        assertThat(payload.criteria().stream().filter(SpeakingCriterionResult::scored))
                .hasSize(4)
                .allSatisfy(criterion -> {
                    assertThat(criterion.coveredSegments()).isEqualTo(1);
                    assertThat(criterion.totalSegments()).isEqualTo(2);
                });
        assertThat(payload.criteria().stream().filter(SpeakingCriterionResult::notScorable))
                .hasSize(2)
                .allSatisfy(criterion -> assertThat(criterion.score()).isNull());
    }

    @Test
    void speakingCurrentLowConfidenceTranscriptKeepsProvenanceWithoutCreatingScoresOrCoverage() {
        PracticeQuestionVersion question = speakingQuestion(201L);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        SpeakingEvaluationResult lowConfidence =
                SpeakingEvaluationTestFixtures.currentResult(
                        objectMapper,
                        "들은 문장",
                        new BigDecimal("16"),
                        provider -> provider.put(
                                "transcript_confidence", 0.31));
        when(attempt.getAiFeedbackJson()).thenReturn(
                speakingFeedback(201L, lowConfidence));

        PracticeResultPresenter.Presentation result = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackContractParser()).present(context(
                "SPEAKING", List.of(question), Map.of("201", "들은 문장"), attempt));
        SpeakingResultPayload payload = (SpeakingResultPayload) result.payload();

        assertThat(result.feedback().state()).isEqualTo("LOW_CONFIDENCE");
        assertThat(result.feedback().label()).isEqualTo("Bản chép lời có độ tin cậy thấp");
        assertThat(result.answers().unscorable()).isEqualTo(1);
        assertThat(result.answers().correct()).isZero();
        assertThat(result.answers().partial()).isZero();
        assertThat(result.answers().incorrect()).isZero();
        assertThat(result.answers().scoredDenominator()).isZero();
        assertThat(result.score().available()).isFalse();
        assertThat(payload.profileState()).isEqualTo("LOW_CONFIDENCE");
        assertThat(payload.coveredSegments()).isZero();
        assertThat(payload.evaluatorCapability())
                .isEqualTo("TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION");
        assertThat(payload.evidenceMode()).isEqualTo("TRANSCRIPT_ONLY");
        assertThat(payload.evidenceContractVersion())
                .isEqualTo(
                        SpeakingEvaluatorCapability
                                .TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION
                                .contractVersion());
        assertThat(payload.contractTrust()).isEqualTo("CURRENT_VERIFIED");
        assertThat(payload.policyBundleId())
                .isEqualTo(SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID);
        assertThat(payload.policyBundleFingerprint())
                .isEqualTo(SpeakingAssessmentPolicyBundle.fingerprint());
        assertThat(payload.holisticScoreAvailable()).isFalse();
        assertThat(payload.holisticScore().available()).isFalse();
        assertThat(payload.profileTitle()).isEqualTo("Bản chép lời có độ tin cậy thấp");
        assertThat(payload.profileStateDescription()).contains("không đủ tin cậy để chấm tiêu chí");
        assertThat(payload.evidenceSourceLabel()).contains("độ tin cậy thấp");
        assertThat(payload.evidenceNote()).contains("không được dùng để chấm tiêu chí");
        assertThat(payload.actionPlan()).isEmpty();
        assertThat(payload.criteria().subList(0, 4)).allSatisfy(criterion -> {
            assertThat(criterion.coveredSegments()).isZero();
            assertThat(criterion.availability()).isEqualTo("UNAVAILABLE");
            assertThat(criterion.score()).isNull();
            assertThat(criterion.weight()).isNull();
            assertThat(criterion.percentage()).isNull();
        });
        assertThat(payload.criteria().subList(4, 6)).allSatisfy(criterion -> {
            assertThat(criterion.availability()).isEqualTo("NOT_SCORABLE");
            assertThat(criterion.score()).isNull();
            assertThat(criterion.weight()).isNull();
            assertThat(criterion.percentage()).isNull();
        });
    }


    @Test
    void speakingReservedDirectAudioCapabilityFailsClosedUntilGovernedFlagsAreEnabled() {
        PracticeQuestionVersion question = speakingQuestion(201L);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {
                  "_contract":"speaking_ai_v1",
                  "speaking_feedback_by_question":{"201":{
                    "evaluationStatus":"EVALUATED","scoreAvailable":true,"source":"PROVIDER",
                    "evaluatorCapability":"AUDIO_DIRECT_FULL_RESERVED",
                    "evidenceMode":"DIRECT_AUDIO_AND_TRANSCRIPT",
                    "evidenceContractVersion":"speaking-evidence-future-audio-direct-reserved",
                    "contractTrust":"CURRENT_VERIFIED","overallScore":92,
                    "rubricScores":[
                      {"criterion":"S_CONTENT_TASK_FULFILLMENT","score":18,"maxScore":20,"availability":"SCORED"},
                      {"criterion":"S_GRAMMAR_SENTENCE_CONTROL","score":18,"maxScore":20,"availability":"SCORED"},
                      {"criterion":"S_VOCABULARY_EXPRESSIONS","score":14,"maxScore":15,"availability":"SCORED"},
                      {"criterion":"S_COHERENCE_ORGANIZATION","score":14,"maxScore":15,"availability":"SCORED"},
                      {"criterion":"S_FLUENCY","score":14,"maxScore":15,"availability":"SCORED"},
                      {"criterion":"S_PRONUNCIATION_DELIVERY","score":14,"maxScore":15,"availability":"SCORED"}
                    ]
                  }}
                }
                """);

        PracticeResultPresenter.Presentation result = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackContractParser()).present(context(
                "SPEAKING", List.of(question), Map.of("201", "submitted transcript"), attempt));
        SpeakingResultPayload payload = (SpeakingResultPayload) result.payload();

        assertThat(result.feedback().state()).isEqualTo("FAILED");
        assertThat(result.score().available()).isFalse();
        assertThat(payload.profileState()).isEqualTo("FAILED");
        assertThat(payload.evaluatorCapability()).isEqualTo("LEGACY_UNKNOWN");
        assertThat(payload.evidenceMode()).isEqualTo("UNKNOWN");
        assertThat(payload.policyBundleId()).isNull();
        assertThat(payload.policyBundleFingerprint()).isNull();
        assertThat(payload.holisticScoreAvailable()).isFalse();
        assertThat(payload.criteria()).allSatisfy(criterion -> {
            assertThat(criterion.availability()).isEqualTo("UNAVAILABLE");
            assertThat(criterion.score()).isNull();
            assertThat(criterion.weight()).isNull();
        });
    }

    @Test
    void speakingPendingFeedbackRemainsPendingAndIsNotCountedAsFailure() {
        PracticeQuestionVersion question = speakingQuestion(201L);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {"speaking_feedback_by_question":{"201":{"evaluation_status":"PROCESSING"}}}
                """);
        SpeakingResultPresenter presenter = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackContractParser());

        PracticeResultPresenter.Presentation result = presenter.present(context(
                "SPEAKING",
                List.of(question),
                Map.of("201", "Bản chép lời đang được chấm"),
                attempt));

        assertThat(result.feedback().state()).isEqualTo("PENDING");
        assertThat(result.answers().pending()).isEqualTo(1);
        assertThat(result.answers().unscorable()).isZero();
        assertThat(result.answers().scoredDenominator()).isZero();
        assertThat(result.score().available()).isFalse();
        SpeakingResultPayload payload = (SpeakingResultPayload) result.payload();
        assertThat(payload.holisticScore().available()).isFalse();
        assertThat(payload.profileState()).isEqualTo("PENDING");
        assertThat(payload.evaluatorCapability()).isEqualTo("LEGACY_UNKNOWN");
        assertThat(payload.evidenceMode()).isEqualTo("UNKNOWN");
        assertThat(payload.evidenceContractVersion()).isNull();
        assertThat(payload.contractTrust()).isEqualTo("LEGACY_UNVERIFIED");
        assertThat(payload.policyBundleId()).isNull();
        assertThat(payload.policyBundleFingerprint()).isNull();
        assertThat(payload.criteria()).allSatisfy(criterion -> {
            assertThat(criterion.availability()).isEqualTo("UNAVAILABLE");
            assertThat(criterion.score()).isNull();
            assertThat(criterion.weight()).isNull();
        });
    }

    @Test
    void speakingMissingFeedbackRemainsPendingWithoutFabricatingZero() {
        PracticeQuestionVersion question = speakingQuestion(201L);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn(null);
        when(attempt.getAnalysisStatus())
                .thenReturn(PracticeAttempt.ANALYSIS_QUEUED);

        PracticeResultPresenter.Presentation result = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackContractParser()).present(context(
                "SPEAKING", List.of(question), Map.of("201", "submitted transcript"), attempt));
        SpeakingResultPayload payload = (SpeakingResultPayload) result.payload();

        assertThat(result.feedback().state()).isEqualTo("PENDING");
        assertThat(result.score().available()).isFalse();
        assertThat(payload.profileState()).isEqualTo("PENDING");
        assertThat(payload.coveredSegments()).isZero();
        assertThat(payload.evaluatorCapability()).isEqualTo("LEGACY_UNKNOWN");
        assertThat(payload.evidenceMode()).isEqualTo("UNKNOWN");
        assertThat(payload.evidenceContractVersion()).isNull();
        assertThat(payload.contractTrust()).isEqualTo("LEGACY_UNVERIFIED");
        assertThat(payload.criteria()).allSatisfy(criterion -> {
            assertThat(criterion.availability()).isEqualTo("UNAVAILABLE");
            assertThat(criterion.score()).isNull();
            assertThat(criterion.scoreDisplay()).isNull();
        });
    }

    @Test
    void speakingCurrentContractFailureIsUnavailableRatherThanZero() {
        PracticeQuestionVersion question = speakingQuestion(201L);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        SpeakingEvaluationResult unavailable =
                SpeakingEvaluationTestFixtures.currentResult(
                        objectMapper,
                        "submitted transcript",
                        new BigDecimal("16"),
                        provider -> provider.put(
                                "evaluation_status",
                                "EVALUATION_UNAVAILABLE"));
        when(attempt.getAiFeedbackJson()).thenReturn(
                speakingFeedback(201L, unavailable));

        PracticeResultPresenter.Presentation result = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackContractParser()).present(context(
                "SPEAKING", List.of(question), Map.of("201", "submitted transcript"), attempt));
        SpeakingResultPayload payload = (SpeakingResultPayload) result.payload();

        assertThat(result.feedback().state()).isEqualTo("UNAVAILABLE");
        assertThat(result.score().available()).isFalse();
        assertThat(payload.profileState()).isEqualTo("UNAVAILABLE");
        assertThat(payload.coveredSegments()).isZero();
        assertThat(payload.criteria()).allSatisfy(criterion -> {
            assertThat(criterion.availability()).isEqualTo("UNAVAILABLE");
            assertThat(criterion.score()).isNull();
            assertThat(criterion.scoreDisplay()).isNull();
        });
    }


    @Test
    void speakingOverviewRejectsUnknownFeedbackContract() {
        PracticeQuestionVersion question = speakingQuestion(201L);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {
                  "_contract":"speaking_future_v99",
                  "speaking_feedback_by_question":{
                    "201":{"score":9,"summary_vi":"Không được tin cậy"}
                  }
                }
                """);
        SpeakingResultPresenter presenter = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackContractParser());

        PracticeResultPresenter.Presentation result = presenter.present(context(
                "SPEAKING",
                List.of(question),
                Map.of("201", "submitted transcript"),
                attempt));

        assertThat(result.feedback().state()).isEqualTo("FAILED");
        assertThat(result.answers().unscorable()).isEqualTo(1);
        assertThat(result.score().available()).isFalse();
        SpeakingResultPayload payload = (SpeakingResultPayload) result.payload();
        assertThat(payload.legacyUnverifiedSegments()).isZero();
        assertThat(payload.evaluatorCapability()).isEqualTo("LEGACY_UNKNOWN");
        assertThat(payload.contractTrust()).isEqualTo("LEGACY_UNVERIFIED");
        assertThat(payload.holisticScoreAvailable()).isFalse();
    }

    @Test
    void malformedStoredSpeakingJsonFailsInsteadOfRemainingPendingForever() {
        PracticeQuestionVersion question = speakingQuestion(201L);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("[not-json");
        SpeakingResultPresenter presenter = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackContractParser());

        PracticeResultPresenter.Presentation result = presenter.present(context(
                "SPEAKING",
                List.of(question),
                Map.of("201", "submitted transcript"),
                attempt));

        assertThat(result.feedback().state()).isEqualTo("FAILED");
        assertThat(result.answers().pending()).isZero();
        assertThat(result.answers().unscorable()).isEqualTo(1);
        assertThat(result.score().available()).isFalse();
    }

    @Test
    void assemblerRequiresExactlyOnePresenterForTheLockedAttemptSkill() {
        PracticeAttemptRepository attempts = mock(PracticeAttemptRepository.class);
        PracticePublishedVersionService versions = mock(PracticePublishedVersionService.class);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempts.findByIdAndUserId(1L, 2L)).thenReturn(Optional.of(attempt));
        when(attempt.getStatus()).thenReturn(PracticeAttempt.STATUS_SUBMITTED);
        when(attempt.getSkill()).thenReturn("READING");
        when(attempt.getSetId()).thenReturn(1L);
        when(attempt.getTestId()).thenReturn(10L);
        when(attempt.getSectionId()).thenReturn(20L);
        when(attempt.getPublishedVersionId()).thenReturn(10L);
        when(attempt.getSetVersionId()).thenReturn(11L);
        when(attempt.getTestVersionId()).thenReturn(12L);
        when(attempt.getSectionVersionId()).thenReturn(13L);
        when(versions.hasCoherentAttemptIdentity(attempt)).thenReturn(true);
        PracticeVersionSnapshot lockedSnapshot = snapshot("READING", List.of());
        when(versions.snapshot(10L, 11L, 12L, 13L))
                .thenReturn(Optional.of(lockedSnapshot));

        PracticeResultAssembler missing = new PracticeResultAssembler(
                attempts, versions, objectMapper,
                new PracticeAttemptAnswerCodec(objectMapper), List.of());
        assertThatThrownBy(() -> missing.assemble(1L, 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đúng một result presenter");

        PracticeResultPresenter first = mock(PracticeResultPresenter.class);
        PracticeResultPresenter second = mock(PracticeResultPresenter.class);
        when(first.supports("READING")).thenReturn(true);
        when(second.supports("READING")).thenReturn(true);
        PracticeResultAssembler ambiguous = new PracticeResultAssembler(
                attempts, versions, objectMapper,
                new PracticeAttemptAnswerCodec(objectMapper),
                List.of(first, second));
        assertThatThrownBy(() -> ambiguous.assemble(1L, 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đúng một result presenter");
    }

    @Test
    void overviewRejectsIncoherentIdentityBeforeSnapshotOrPresenter() {
        PracticeAttemptRepository attempts =
                mock(PracticeAttemptRepository.class);
        PracticePublishedVersionService versions =
                mock(PracticePublishedVersionService.class);
        PracticeResultPresenter presenter =
                mock(PracticeResultPresenter.class);
        PracticeAttempt attempt =
                new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        attempt.markSubmitted(BigDecimal.ONE, BigDecimal.TEN, "{}");
        when(attempts.findByIdAndUserId(77L, 2L))
                .thenReturn(Optional.of(attempt));
        when(versions.hasCoherentAttemptIdentity(attempt))
                .thenReturn(false);

        PracticeResultAssembler assembler = new PracticeResultAssembler(
                attempts,
                versions,
                objectMapper,
                new PracticeAttemptAnswerCodec(objectMapper),
                List.of(presenter));

        assertThatThrownBy(() -> assembler.assemble(77L, 2L))
                .isInstanceOf(
                        PracticeAttemptStatePolicy
                                .PracticeResultNotAvailableException.class)
                .hasMessageContaining("không nhất quán");
        verify(versions, never()).snapshot(any(), any(), any(), any());
        verifyNoInteractions(presenter);
    }

    @Test
    void overviewRechecksAttemptSourceIdentityBeforePresenter() {
        PracticeAttemptRepository attempts =
                mock(PracticeAttemptRepository.class);
        PracticePublishedVersionService versions =
                mock(PracticePublishedVersionService.class);
        PracticeResultPresenter presenter =
                mock(PracticeResultPresenter.class);
        PracticeAttempt attempt =
                new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        attempt.markSubmitted(BigDecimal.ONE, BigDecimal.TEN, "{}");
        when(attempts.findByIdAndUserId(77L, 2L))
                .thenReturn(Optional.of(attempt));
        when(versions.hasCoherentAttemptIdentity(attempt))
                .thenReturn(true);
        PracticeVersionSnapshot wrongSet =
                snapshot("READING", List.of());
        when(wrongSet.setVersion().getSetId()).thenReturn(999L);
        when(versions.snapshot(100L, 101L, 102L, 103L))
                .thenReturn(Optional.of(wrongSet));

        PracticeResultAssembler assembler = new PracticeResultAssembler(
                attempts,
                versions,
                objectMapper,
                new PracticeAttemptAnswerCodec(objectMapper),
                List.of(presenter));

        assertThatThrownBy(() -> assembler.assemble(77L, 2L))
                .isInstanceOf(
                        PracticeAttemptStatePolicy
                                .PracticeResultNotAvailableException.class)
                .hasMessageContaining("không nhất quán");
        verify(versions).snapshot(100L, 101L, 102L, 103L);
        verifyNoInteractions(presenter);
    }

    private WritingResultPresenter writingPresenter() {
        QuestionTypeResolver typeResolver = new QuestionTypeResolver();
        return new WritingResultPresenter(
                objectMapper,
                new WritingFeedbackViewMapper(),
                new WritingFeedbackContractParser(objectMapper),
                new AssessmentContractCodec(objectMapper, typeResolver),
                typeResolver,
                new AssessmentScoringEngine());
    }

    private WritingDetailPayload writingDetailWithAnnotations(
            String learnerAnswer,
            String annotationsJson
    ) {
        String normalized = currentWritingEvaluation(
                "Q53", learnerAnswer, 10, 7, 7);
        try {
            ObjectNode entry =
                    (ObjectNode) objectMapper.readTree(normalized);
            entry.set("annotations",
                    objectMapper.readTree(annotationsJson));
            return writingDetailFromEvaluation(
                    learnerAnswer, entry.toString());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private WritingDetailPayload writingDetailWithCurrentFinding(
            String learnerAnswer,
            String evidence,
            int startOffset
    ) {
        String normalized =
                WritingContractTestFixtures.normalizedFeedback(
                        objectMapper,
                        "Q53",
                        learnerAnswer,
                        envelope -> {
                            WritingContractTestFixtures.addEvidence(
                                    envelope,
                                    "WEV-GRAMMAR",
                                    learnerAnswer,
                                    evidence,
                                    startOffset);
                            WritingContractTestFixtures.addFinding(
                                    envelope,
                                    "ann-grammar",
                                    "IMPROVEMENT",
                                    "REPLACE",
                                    "W_GRAMMAR_ERRORS",
                                    "MORPHOLOGY_PARTICLES",
                                    "W_LANGUAGE_EXPRESSION",
                                    "WEV-GRAMMAR",
                                    List.of(),
                                    "Cần sửa ngữ pháp",
                                    "문법을 고칩니다",
                                    "MINOR");
                            ObjectNode language =
                                    WritingContractTestFixtures.rubric(
                                            envelope,
                                            "W_LANGUAGE_EXPRESSION");
                            WritingContractTestFixtures.replaceIds(
                                    language,
                                    "findingIds",
                                    "ann-grammar");
                        });
        return writingDetailFromEvaluation(
                learnerAnswer, normalized);
    }

    private WritingDetailPayload writingDetailFromEvaluation(
            String learnerAnswer,
            String normalized
    ) {
        PracticeQuestionVersion question =
                writingQuestion(153L, 53, WritingTaskType.Q53);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn(
                writingFeedback(Map.of(153L, normalized)));
        WritingResultPresenter presenter = writingPresenter();
        PracticeResultContext context = context(
                "WRITING",
                List.of(question),
                Map.of("153", learnerAnswer),
                attempt);
        PracticeResultPresenter.Presentation presentation =
                presenter.present(context);
        return (WritingDetailPayload) presenter.presentDetail(
                context, overview("WRITING", presentation), 153L);
    }

    private String currentWritingEvaluation(
            String taskType,
            String learnerAnswer,
            int... scores
    ) {
        return currentWritingEvaluation(
                taskType, learnerAnswer, envelope -> {
                }, scores);
    }

    private String currentWritingEvaluation(
            String taskType,
            String learnerAnswer,
            Consumer<ObjectNode> additional,
            int... scores
    ) {
        return WritingContractTestFixtures.normalizedFeedback(
                objectMapper,
                taskType,
                learnerAnswer,
                envelope -> {
                    String baseText = learnerAnswer.substring(0, 1);
                    WritingContractTestFixtures.addEvidence(
                            envelope,
                            "WEV-BASE",
                            learnerAnswer,
                            baseText,
                            0);
                    List<JsonNode> rubrics = new java.util.ArrayList<>();
                    envelope.withArray("rubricScores")
                            .forEach(rubrics::add);
                    if (scores.length != rubrics.size()) {
                        throw new IllegalArgumentException(
                                "Fixture scores must cover every Writing rubric");
                    }
                    if ("Q51".equals(taskType)
                            || "Q52".equals(taskType)) {
                        for (JsonNode coverage
                                : envelope.withArray("taskCoverage")) {
                            ((ObjectNode) coverage).put("status", "MET");
                            WritingContractTestFixtures.replaceIds(
                                    (ObjectNode) coverage,
                                    "evidenceIds",
                                    "WEV-BASE");
                        }
                        for (int index = 0;
                             index < rubrics.size();
                             index++) {
                            ObjectNode row = (ObjectNode) rubrics.get(index);
                            row.put("score", scores[index]);
                            WritingContractTestFixtures.replaceIds(
                                    row, "evidenceIds", "WEV-BASE");
                        }
                        additional.accept(envelope);
                        return;
                    }

                    String languageEvidence = learnerAnswer.substring(1, 2);
                    WritingContractTestFixtures.addEvidence(
                            envelope,
                            "WEV-LANGUAGE",
                            learnerAnswer,
                            languageEvidence,
                            1);
                    WritingContractTestFixtures.addFinding(
                            envelope,
                            "WF-LANGUAGE",
                            "IMPROVEMENT",
                            "REPLACE",
                            "W_VOCABULARY_ERRORS",
                            "WORD_CHOICE",
                            "W_LANGUAGE_EXPRESSION",
                            "WEV-LANGUAGE",
                            List.of(),
                            "Cần điều chỉnh lựa chọn từ tại đúng vị trí.",
                            "교정",
                            "MINOR");
                    if ("Q53".equals(taskType)) {
                        WritingContractTestFixtures.addFinding(
                                envelope,
                                "WF-CONTENT",
                                "IMPROVEMENT",
                                "MISSING",
                                "W_TASK_REQUIREMENT_MISSING",
                                "REQUIREMENT_COVERAGE",
                                "W_CONTENT_TASK_ACHIEVEMENT",
                                null,
                                List.of("Q53_FOUR_TRANSPORT_MODES"),
                                "Cần bổ sung yêu cầu nội dung còn thiếu.",
                                "",
                                "MODERATE");
                        WritingContractTestFixtures.addFinding(
                                envelope,
                                "WF-ORGANIZATION",
                                "IMPROVEMENT",
                                "MISSING",
                                "W_LOGICAL_FLOW_ISSUES",
                                "LOGICAL_RELATION",
                                "W_ORGANIZATION_COHERENCE",
                                null,
                                List.of(),
                                "Cần làm rõ quan hệ logic.",
                                "",
                                "MODERATE");
                    }
                    for (int index = 0;
                         index < rubrics.size();
                         index++) {
                        ObjectNode row = (ObjectNode) rubrics.get(index);
                        row.put("score", scores[index]);
                        WritingContractTestFixtures.replaceIds(
                                row, "evidenceIds", "WEV-BASE");
                        String criterionId =
                                row.path("criterionId").asText();
                        if ("W_CONTENT_TASK_ACHIEVEMENT"
                                .equals(criterionId)
                                && "Q53".equals(taskType)) {
                            WritingContractTestFixtures.replaceIds(
                                    row, "findingIds", "WF-CONTENT");
                        } else if ("W_ORGANIZATION_COHERENCE"
                                .equals(criterionId)
                                && "Q53".equals(taskType)) {
                            WritingContractTestFixtures.replaceIds(
                                    row,
                                    "findingIds",
                                    "WF-ORGANIZATION");
                        } else if ("W_LANGUAGE_EXPRESSION"
                                .equals(criterionId)) {
                            WritingContractTestFixtures.replaceIds(
                                    row, "findingIds", "WF-LANGUAGE");
                        }
                    }
                    additional.accept(envelope);
                });
    }

    private String writingFeedback(Map<Long, String> evaluations) {
        ObjectNode root = objectMapper.createObjectNode();
        evaluations.forEach((questionId, evaluation) -> {
            try {
                root.set(String.valueOf(questionId),
                        objectMapper.readTree(evaluation));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
        return json(root);
    }

    private String tamperWritingRubricMaximum(
            String evaluation,
            String criterionId,
            int maximum
    ) {
        try {
            ObjectNode root =
                    (ObjectNode) objectMapper.readTree(evaluation);
            for (JsonNode row : root.withArray("rubric_scores")) {
                if (criterionId.equals(
                        row.path("criterionId").asText())) {
                    ((ObjectNode) row).put("maxScore", maximum);
                    return root.toString();
                }
            }
            throw new IllegalArgumentException(
                    "Unknown Writing rubric fixture");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String currentSpeakingFeedback(
            String transcript,
            List<SpeakingEvaluationTestFixtures.FindingFixture> findings
    ) {
        return speakingFeedback(
                201L,
                SpeakingEvaluationTestFixtures.currentResultWithFindings(
                        objectMapper,
                        transcript,
                        new BigDecimal("16"),
                        findings));
    }

    private static SpeakingEvaluationTestFixtures.FindingFixture
    speakingFinding(
            String findingId,
            String evidenceId,
            SpeakingRubricCriterion criterion,
            String subcriterionId,
            String exactText,
            int startOffset,
            String annotationType,
            String operation,
            String category,
            String severity,
            String explanationVi,
            String suggestionKo
    ) {
        return new SpeakingEvaluationTestFixtures.FindingFixture(
                findingId,
                evidenceId,
                criterion,
                subcriterionId,
                exactText,
                startOffset,
                annotationType,
                operation,
                category,
                severity,
                new BigDecimal("0.91"),
                explanationVi,
                suggestionKo);
    }

    private String speakingFeedback(
            long questionId,
            SpeakingEvaluationResult result
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("_contract", "speaking_ai_v1");
        root.putObject("speaking_feedback_by_question")
                .set(String.valueOf(questionId),
                        objectMapper.valueToTree(result));
        return json(root);
    }

    private String speakingFeedback(
            Map<Long, SpeakingEvaluationResult> results
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("_contract", "speaking_ai_v1");
        ObjectNode entries = root.putObject(
                "speaking_feedback_by_question");
        results.forEach((questionId, result) -> entries.set(
                String.valueOf(questionId),
                objectMapper.valueToTree(result)));
        return json(root);
    }

    private String speakingFeedbackWithPending(
            long readyQuestionId,
            SpeakingEvaluationResult ready,
            long pendingQuestionId
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("_contract", "speaking_ai_v1");
        ObjectNode entries = root.putObject(
                "speaking_feedback_by_question");
        entries.set(String.valueOf(readyQuestionId),
                objectMapper.valueToTree(ready));
        entries.putObject(String.valueOf(pendingQuestionId))
                .put("evaluationStatus", "PROCESSING");
        return json(root);
    }


    private String json(ObjectNode root) {
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static PracticeAttemptResultView overview(
            String skill,
            PracticeResultPresenter.Presentation presentation
    ) {
        return new PracticeAttemptResultView(
                new ResultAttemptIdentity(
                        1L, 2L, 3L, 4L, 5L, 6L, "Bộ đề",
                        7L, "Bài thi", 8L, "Phần thi", skill, skill),
                new ResultState("GRADED", "Đã chấm"),
                presentation.score(),
                presentation.answers(),
                presentation.feedback(),
                null,
                null,
                null,
                presentation.payload());
    }

    private PracticeResultContext context(
            String skill,
            List<PracticeQuestionVersion> questions,
            Map<String, String> answers,
            PracticeAttempt suppliedAttempt) {
        PracticeAttempt attempt = suppliedAttempt == null ? mock(PracticeAttempt.class) : suppliedAttempt;
        return new PracticeResultContext(
                attempt,
                snapshot(skill, questions),
                answers,
                new ResultScoreSummary(
                        BigDecimal.valueOf(80),
                        null,
                        null,
                        BigDecimal.valueOf(80),
                        "PERCENTAGE",
                        "Thang 100",
                        null));
    }

    private static PracticeVersionSnapshot snapshot(
            String skill,
            List<PracticeQuestionVersion> questions) {
        PracticePublishedVersion published =
                mock(PracticePublishedVersion.class);
        when(published.getId()).thenReturn(10L);
        PracticeSetVersion set = mock(PracticeSetVersion.class);
        when(set.getId()).thenReturn(11L);
        when(set.getSetId()).thenReturn(1L);
        PracticeTestVersion test = mock(PracticeTestVersion.class);
        when(test.getId()).thenReturn(12L);
        when(test.getTestId()).thenReturn(10L);
        PracticeSectionVersion section = mock(PracticeSectionVersion.class);
        when(section.getId()).thenReturn(13L);
        when(section.getSectionId()).thenReturn(20L);
        when(section.getSkill()).thenReturn(skill);
        List<PracticeQuestionGroupVersion> groups = List.of();
        if (("READING".equals(skill) || "LISTENING".equals(skill))
                && !questions.isEmpty()) {
            PracticeQuestionGroupVersion group =
                    mock(PracticeQuestionGroupVersion.class);
            when(group.getId()).thenReturn(901L);
            when(group.getGroupId()).thenReturn(801L);
            when(group.getDisplayOrder()).thenReturn(0);
            when(group.getGroupLabel()).thenReturn("Nhóm câu hỏi đã khóa");
            questions.forEach(question ->
                    when(question.getGroupVersionId()).thenReturn(901L));
            groups = List.of(group);
        }
        return new PracticeVersionSnapshot(
                published,
                set,
                test,
                section,
                groups,
                questions);
    }

    private static PracticeQuestionVersion objectiveQuestion(Long id) {
        return objectiveQuestion(id, "SINGLE_CHOICE");
    }

    private static PracticeQuestionVersion objectiveQuestion(Long id, String questionType) {
        PracticeQuestionVersion question = mock(PracticeQuestionVersion.class);
        when(question.getId()).thenReturn(id);
        when(question.getQuestionId()).thenReturn(id);
        when(question.getQuestionType()).thenReturn(questionType);
        when(question.getPoints()).thenReturn(BigDecimal.ONE);
        return question;
    }

    private static PracticeQuestionVersion writingQuestion(
            Long id,
            int questionNo,
            WritingTaskType taskType) {
        PracticeQuestionVersion question = mock(PracticeQuestionVersion.class);
        when(question.getId()).thenReturn(id + 1000);
        when(question.getQuestionId()).thenReturn(id);
        when(question.getQuestionNo()).thenReturn(questionNo);
        when(question.getQuestionType()).thenReturn("ESSAY");
        when(question.getWritingTaskType()).thenReturn(taskType);
        when(question.getPrompt()).thenReturn("Writing prompt " + questionNo);
        return question;
    }

    private static PracticeQuestionVersion speakingQuestion(Long id) {
        PracticeQuestionVersion question = mock(PracticeQuestionVersion.class);
        when(question.getId()).thenReturn(id + 1000);
        when(question.getQuestionId()).thenReturn(id);
        when(question.getQuestionNo()).thenReturn(id.intValue());
        when(question.getQuestionType()).thenReturn("SPEAKING");
        when(question.getPrompt()).thenReturn("Speaking prompt " + id);
        when(question.getDisplayOrder()).thenReturn(id.intValue());
        return question;
    }


    private static AssessmentScoreResult score(
            AssessmentScoreStatus status,
            String earned,
            String possible) {
        return new AssessmentScoreResult(
                status,
                new BigDecimal(earned),
                new BigDecimal(possible),
                ScoringPolicyCode.NORMALIZED_EXACT,
                0,
                1);
    }
}
