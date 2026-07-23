package com.ksh.features.practice.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.PracticeSectionVersion;
import com.ksh.entities.PracticeSetVersion;
import com.ksh.entities.PracticeTestVersion;
import com.ksh.entities.WritingTaskType;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService;
import com.ksh.features.practice.ai.speaking.SpeakingFeedbackCompatibilityReader;
import com.ksh.features.practice.ai.writing.WritingFeedbackCompatibilityReader;
import com.ksh.features.practice.ai.writing.WritingFeedbackViewMapper;
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
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveResultPayload;
import com.ksh.features.practice.dto.PracticeDtos.PracticeAttemptResultView;
import com.ksh.features.practice.dto.PracticeDtos.ResultAttemptIdentity;
import com.ksh.features.practice.dto.PracticeDtos.ResultFeedbackAvailability;
import com.ksh.features.practice.dto.PracticeDtos.ResultScoreSummary;
import com.ksh.features.practice.dto.PracticeDtos.ResultState;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingCriterionResult;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingDetailPayload;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingMediaView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingResultPayload;
import com.ksh.features.practice.dto.PracticeDtos.WritingDetailPayload;
import com.ksh.features.practice.dto.PracticeDtos.WritingResultPayload;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.service.PracticePublishedVersionService;
import com.ksh.features.practice.service.PracticeSpeakingMediaService;
import com.ksh.features.practice.service.PracticeVersionSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
    }

    @Test
    void objectiveAliasesShareCanonicalGroupsAndUnsupportedTypesFailClosed() {
        AssessmentContractCodec codec = mock(AssessmentContractCodec.class);
        QuestionTypeResolver typeResolver = new QuestionTypeResolver();
        AssessmentScoringEngine scoringEngine = mock(AssessmentScoringEngine.class);
        QuestionExplanationReadService explanations = mock(QuestionExplanationReadService.class);
        ObjectiveResultPresenter presenter = new ObjectiveResultPresenter(
                codec, typeResolver, scoringEngine, explanations, objectMapper);
        List<PracticeQuestionVersion> questions = List.of(
                objectiveQuestion(115L, "MCQ"),
                objectiveQuestion(116L, "MCQ_SINGLE"),
                objectiveQuestion(117L, "SINGLE_CHOICE"),
                objectiveQuestion(118L, "TFNG"),
                objectiveQuestion(119L, "TRUE_FALSE_NOT_GIVEN"),
                objectiveQuestion(120L, "GAP_FILL"),
                objectiveQuestion(121L, "FILL_BLANK"),
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
        assertThat(payload.breakdown().get(0).answers().total()).isEqualTo(3);
        assertThat(payload.breakdown().get(1).questionType()).isEqualTo("TRUE_FALSE_NOT_GIVEN");
        assertThat(payload.breakdown().get(1).label())
                .isEqualTo("Đúng, sai hoặc không có thông tin");
        assertThat(payload.breakdown().get(1).answers().total()).isEqualTo(2);
        assertThat(payload.breakdown().get(2).questionType()).isEqualTo("FILL_BLANK");
        assertThat(payload.breakdown().get(2).label()).isEqualTo("Điền từ");
        assertThat(payload.breakdown().get(2).answers().total()).isEqualTo(2);
        assertThat(payload.breakdown().get(3).questionType()).isEqualTo("UNSCORABLE");
        assertThat(payload.breakdown().get(3).label()).isEqualTo("Loại câu hỏi không thể chấm");
        assertThat(payload.breakdown().get(3).answers().unscorable()).isEqualTo(1);
        assertThat(payload.breakdown())
                .extracting(row -> row.questionType())
                .doesNotContain("MCQ", "MCQ_SINGLE", "TFNG", "GAP_FILL",
                        "ALIEN_LEGACY_TYPE");
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
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {
                  "151": {
                    "raw_score": 8,
                    "raw_score_max": 10,
                    "score_available": true,
                    "task_type": "Q51",
                    "scoring_contract": "TASK_NATIVE_RUBRIC_V1",
                    "engine": "KSH_WRITING_EVALUATOR_V2",
                    "evaluation_status": "EVALUATED",
                    "evaluation_source": "PROVIDER",
                    "rubric_scores": [
                      {"criterionId":"W_CLOZE_BLANK_1_CONTEXT","score":2,"maxScore":2},
                      {"criterionId":"W_CLOZE_BLANK_1_GRAMMAR","score":2,"maxScore":2},
                      {"criterionId":"W_CLOZE_BLANK_1_EXPRESSION","score":1,"maxScore":1},
                      {"criterionId":"W_CLOZE_BLANK_2_CONTEXT","score":1,"maxScore":2},
                      {"criterionId":"W_CLOZE_BLANK_2_GRAMMAR","score":1,"maxScore":2},
                      {"criterionId":"W_CLOZE_BLANK_2_EXPRESSION","score":1,"maxScore":1}
                    ]
                  },
                  "152": {
                    "raw_score": 7,
                    "raw_score_max": 10,
                    "score_available": true,
                    "task_type": "Q52",
                    "scoring_contract": "TASK_NATIVE_RUBRIC_V1",
                    "engine": "KSH_WRITING_EVALUATOR_V2",
                    "evaluation_status": "EVALUATED",
                    "evaluation_source": "PROVIDER",
                    "rubric_scores": [
                      {"criterionId":"W_CLOZE_BLANK_1_CONTEXT","score":2,"maxScore":2},
                      {"criterionId":"W_CLOZE_BLANK_1_GRAMMAR","score":1,"maxScore":2},
                      {"criterionId":"W_CLOZE_BLANK_1_EXPRESSION","score":1,"maxScore":1},
                      {"criterionId":"W_CLOZE_BLANK_2_CONTEXT","score":1,"maxScore":2},
                      {"criterionId":"W_CLOZE_BLANK_2_GRAMMAR","score":1,"maxScore":2},
                      {"criterionId":"W_CLOZE_BLANK_2_EXPRESSION","score":1,"maxScore":1}
                    ]
                  },
                  "153": {
                    "raw_score": 24,
                    "raw_score_max": 30,
                    "score_available": true,
                    "task_type": "Q53",
                    "scoring_contract": "TASK_NATIVE_RUBRIC_V1",
                    "engine": "KSH_WRITING_EVALUATOR_V2",
                    "evaluation_status": "EVALUATED",
                    "evaluation_source": "PROVIDER",
                    "summary_vi": "Bài viết hoàn thành đúng nhiệm vụ.",
                    "rubric_scores": [
                      {"criterionId":"W_CONTENT_TASK_ACHIEVEMENT","score":10,"maxScore":12,"feedback":"Đủ ý chính"},
                      {"criterionId":"W_ORGANIZATION_COHERENCE","score":7,"maxScore":9,"feedback":"Bố cục rõ"},
                      {"criterionId":"W_LANGUAGE_EXPRESSION","score":7,"maxScore":9,"feedback":"Diễn đạt phù hợp"}
                    ],
                    "strengths":[
                      {"criterionId":"W_TASK_REQUIREMENT_COVERAGE","category":"Provider content","explanationVi":"Bao phủ đúng yêu cầu"},
                      {"criterionId":"W_FORMAL_VOCABULARY_USAGE","category":"Provider vocabulary","explanationVi":"Dùng từ phù hợp"}
                    ]
                  },
                  "154": {
                    "raw_score": 40,
                    "raw_score_max": 50,
                    "score_available": true,
                    "task_type": "Q54",
                    "scoring_contract": "TASK_NATIVE_RUBRIC_V1",
                    "engine": "KSH_WRITING_EVALUATOR_V2",
                    "evaluation_status": "EVALUATED",
                    "evaluation_source": "PROVIDER",
                    "rubric_scores": [
                      {"criterionId":"W_CONTENT_TASK_ACHIEVEMENT","score":16,"maxScore":20},
                      {"criterionId":"W_ORGANIZATION_COHERENCE","score":12,"maxScore":15},
                      {"criterionId":"W_LANGUAGE_EXPRESSION","score":12,"maxScore":15}
                    ]
                  }
                }
                """);
        WritingResultPresenter presenter = writingPresenter();

        PracticeResultPresenter.Presentation result = presenter.present(context(
                "WRITING",
                List.of(q51, q52, q53, q54),
                Map.of("151", "short answer", "152", "second short answer",
                        "153", "long answer", "154", "longer answer"),
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
            assertThat(task.analysisLenses()).isEmpty();
        });
        assertThat(payload.tasks().get(2).score().pointsDisplay()).isEqualTo("24/30");
        assertThat(payload.tasks().get(2).officialCriteria())
                .extracting(criterion -> criterion.label())
                .containsExactly(
                        "Hoàn thành nhiệm vụ và Nội dung",
                        "Cấu trúc và Mạch lạc",
                        "Ngôn ngữ và Biểu đạt");
        assertThat(payload.tasks().get(2).analysisLenses())
                .extracting(lens -> lens.label())
                .containsExactly(
                        "Nhiệm vụ và Nội dung",
                        "Cấu trúc và mạch lạc",
                        "Từ vựng và Diễn đạt",
                        "Ngữ pháp và Độ chính xác");
        assertThat(payload.tasks().get(2).analysisLenses().get(0).evidence())
                .contains("Bao phủ đúng yêu cầu");
        assertThat(payload.tasks().get(2).analysisLenses().get(2).evidence())
                .contains("Dùng từ phù hợp");
        assertThat(objectMapper.writeValueAsString(payload.tasks().get(2).analysisLenses()))
                .doesNotContain("\"score\"", "\"maxScore\"", "\"percentage\"", "\"band\"",
                        "\"countedSeparately\"");
        assertThat(payload.tasks().get(3).score().pointsDisplay()).isEqualTo("40/50");
        assertThat(payload.tasks().get(3).prompt()).isEqualTo(longKoreanPrompt);
        assertThat(payload.tasks().get(3).officialCriteria())
                .extracting(criterion -> criterion.maxScore())
                .containsExactly(BigDecimal.valueOf(20), BigDecimal.valueOf(15), BigDecimal.valueOf(15));
        assertThat(payload.tasks()).allSatisfy(task -> assertThat(task.detailAvailable()).isTrue());
    }

    @Test
    void writingDetailCountsOnlyRawFindingsAcceptedByTheKshEvidenceContract() {
        PracticeQuestionVersion question = writingQuestion(153L, 53, WritingTaskType.Q53);
        String learnerAnswer = "학생은 문법 오류를 고칩니다.";
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {"153":{
                  "raw_score":24,"raw_score_max":30,"score_available":true,"task_type":"Q53",
                  "scoring_contract":"TASK_NATIVE_RUBRIC_V1",
                  "engine":"KSH_WRITING_EVALUATOR_V2",
                  "evaluation_status":"EVALUATED","evaluation_source":"PROVIDER",
                  "rubric_scores":[
                    {"criterionId":"W_CONTENT_TASK_ACHIEVEMENT","score":10,"maxScore":12},
                    {"criterionId":"W_ORGANIZATION_COHERENCE","score":7,"maxScore":9},
                    {"criterionId":"W_LANGUAGE_EXPRESSION","score":7,"maxScore":9}
                  ],
                  "strengths":[
                    {"criterionId":"W_SENTENCE_VARIETY","evidenceScope":"WHOLE_ANSWER",
                     "evidence":"","explanationVi":"Inactive legacy alias","correction":""},
                    {"criterionId":"W_LENGTH_REQUIREMENT_MET","evidenceScope":"WHOLE_ANSWER",
                     "evidence":"","explanationVi":"Dung lượng phù hợp","correction":""},
                    {"criterionId":"W_LENGTH_REQUIREMENT_MET","evidenceScope":"WHOLE_ANSWER",
                     "evidence":"provider text","explanationVi":"Invalid whole-answer evidence","correction":""}
                  ],
                  "needs_improvement":[
                    {"criterionId":"W_GRAMMAR_ERRORS","evidenceScope":"TEXT_SPAN",
                     "evidence":"문법 오류","explanationVi":"Cần sửa ngữ pháp","correction":"문법을 고칩니다"},
                    {"criterionId":"W_GRAMMAR_ERRORS","evidenceScope":"TEXT_SPAN",
                     "evidence":"không có trong bài","explanationVi":"Invalid evidence","correction":"교정"},
                    {"criterionId":"W_GRAMMAR_ERRORS","evidence":"문법 오류",
                     "explanationVi":"Missing explicit scope","correction":"교정"},
                    {"criterionId":"W_GRAMMAR_ERRORS","evidenceScope":"TEXT_SPAN",
                     "evidence":"문법 오류","explanationVi":"Missing correction","correction":""}
                  ]
                }}
                """);
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
        assertThat(detail.diagnosticFindings()).hasSize(2);
        assertThat(detail.filterChips()).hasSize(2);
        assertThat(detail.diagnosticGroups())
                .extracting(group -> group.categoryCode())
                .containsExactly("MORPHOSYNTAX", "LENGTH_FORMAT");
        assertThat(detail.filterChips())
                .filteredOn(chip -> chip.id().equals(
                        "W_LENGTH_REQUIREMENT_MET_WRITING_Q53"))
                .singleElement()
                .satisfies(chip -> {
                    assertThat(chip.count()).isEqualTo(1);
                    assertThat(chip.parentCriterionId()).isNull();
                    assertThat(chip.scoreEffect()).isEqualTo("DIAGNOSTIC_ONLY");
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
        assertThat(detail.filterChips())
                .noneSatisfy(chip -> assertThat(chip.id()).contains("W_SENTENCE_VARIETY"));
        assertThat(detail.diagnosticFindings()).allSatisfy(finding -> {
            assertThat(finding.target().kind().name()).isEqualTo("WHOLE_ANSWER");
            assertThat(finding.impact()).isNull();
            assertThat(finding.frequency()).isNull();
            assertThat(finding.confidence()).isNull();
            assertThat(finding.observability()).isNull();
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
    void writingDetailDoesNotInventClozeBlankParentsForGenericStoredFindings() {
        PracticeQuestionVersion q51 = writingQuestion(151L, 51, WritingTaskType.Q51);
        PracticeQuestionVersion q52 = writingQuestion(152L, 52, WritingTaskType.Q52);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {
                  "151":{
                    "raw_score":8,"raw_score_max":10,"score_available":true,"task_type":"Q51",
                    "scoring_contract":"TASK_NATIVE_RUBRIC_V1",
                    "engine":"KSH_WRITING_EVALUATOR_V2",
                    "evaluation_status":"EVALUATED","evaluation_source":"PROVIDER",
                    "rubric_scores":[
                      {"criterionId":"W_CLOZE_BLANK_1_CONTEXT","score":2,"maxScore":2},
                      {"criterionId":"W_CLOZE_BLANK_1_GRAMMAR","score":2,"maxScore":2},
                      {"criterionId":"W_CLOZE_BLANK_1_EXPRESSION","score":1,"maxScore":1},
                      {"criterionId":"W_CLOZE_BLANK_2_CONTEXT","score":1,"maxScore":2},
                      {"criterionId":"W_CLOZE_BLANK_2_GRAMMAR","score":1,"maxScore":2},
                      {"criterionId":"W_CLOZE_BLANK_2_EXPRESSION","score":1,"maxScore":1}
                    ],
                    "strengths":[
                      {"criterionId":"W_CLOZE_CONTEXT_FIT","evidenceScope":"TEXT_SPAN",
                       "evidence":"문맥","explanationVi":"Phù hợp ngữ cảnh","correction":""}
                    ],
                    "needs_improvement":[
                      {"criterionId":"W_GRAMMAR_ERRORS","evidenceScope":"TEXT_SPAN",
                       "evidence":"문법","explanationVi":"Cần sửa ngữ pháp","correction":"교정"}
                    ]
                  },
                  "152":{
                    "raw_score":7,"raw_score_max":10,"score_available":true,"task_type":"Q52",
                    "scoring_contract":"TASK_NATIVE_RUBRIC_V1",
                    "engine":"KSH_WRITING_EVALUATOR_V2",
                    "evaluation_status":"EVALUATED","evaluation_source":"PROVIDER",
                    "rubric_scores":[
                      {"criterionId":"W_CLOZE_BLANK_1_CONTEXT","score":2,"maxScore":2},
                      {"criterionId":"W_CLOZE_BLANK_1_GRAMMAR","score":1,"maxScore":2},
                      {"criterionId":"W_CLOZE_BLANK_1_EXPRESSION","score":1,"maxScore":1},
                      {"criterionId":"W_CLOZE_BLANK_2_CONTEXT","score":1,"maxScore":2},
                      {"criterionId":"W_CLOZE_BLANK_2_GRAMMAR","score":1,"maxScore":2},
                      {"criterionId":"W_CLOZE_BLANK_2_EXPRESSION","score":1,"maxScore":1}
                    ],
                    "strengths":[
                      {"criterionId":"W_CLOZE_CONTEXT_FIT","evidenceScope":"TEXT_SPAN",
                       "evidence":"문맥","explanationVi":"Phù hợp ngữ cảnh","correction":""}
                    ],
                    "needs_improvement":[
                      {"criterionId":"W_GRAMMAR_ERRORS","evidenceScope":"TEXT_SPAN",
                       "evidence":"문법","explanationVi":"Cần sửa ngữ pháp","correction":"교정"}
                    ]
                  }
                }
                """);
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
        assertThat(detail.scoreCriteria()).hasSize(12);
        assertThat(detail.diagnosticFindings()).isEmpty();
        assertThat(detail.filterChips()).isEmpty();
        assertThat(detail.diagnosticAvailability())
                .isEqualTo("BLANK_IDENTITY_UNAVAILABLE");
    }

    @Test
    void writingDetailKeepsUpgradeProvenanceAndRewritesIsolatedToSelectedQuestion() {
        PracticeQuestionVersion q53 = writingQuestion(153L, 53, WritingTaskType.Q53);
        PracticeQuestionVersion q54 = writingQuestion(154L, 54, WritingTaskType.Q54);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {
                  "153":{
                    "raw_score":24,"raw_score_max":30,"score_available":true,"task_type":"Q53",
                    "scoring_contract":"TASK_NATIVE_RUBRIC_V1",
                    "engine":"KSH_WRITING_EVALUATOR_V2",
                    "evaluation_status":"EVALUATED","evaluation_source":"PROVIDER",
                    "rubric_scores":[
                      {"criterionId":"W_CONTENT_TASK_ACHIEVEMENT","score":10,"maxScore":12},
                      {"criterionId":"W_ORGANIZATION_COHERENCE","score":7,"maxScore":9},
                      {"criterionId":"W_LANGUAGE_EXPRESSION","score":7,"maxScore":9}
                    ],
                    "upgraded_answer":"학생은 문법 오류를 바로잡습니다.",
                    "sentence_rewrites":[
                      {"original":"문법 오류","upgraded":"문법 오류를 바로잡습니다","reason":"Diễn đạt rõ hành động sửa."},
                      {"original":"다른 문장","upgraded":"교정","reason":"Không thuộc bài đang chọn."}
                    ],
                    "sample_answer":"평가기가 만든 참고 답안"
                  },
                  "154":{
                    "raw_score":40,"raw_score_max":50,"score_available":true,"task_type":"Q54",
                    "scoring_contract":"TASK_NATIVE_RUBRIC_V1",
                    "engine":"KSH_WRITING_EVALUATOR_V2",
                    "evaluation_status":"EVALUATED","evaluation_source":"PROVIDER",
                    "rubric_scores":[
                      {"criterionId":"W_CONTENT_TASK_ACHIEVEMENT","score":16,"maxScore":20},
                      {"criterionId":"W_ORGANIZATION_COHERENCE","score":12,"maxScore":15},
                      {"criterionId":"W_LANGUAGE_EXPRESSION","score":12,"maxScore":15}
                    ],
                    "upgraded_answer":"선택하지 않은 답안",
                    "sentence_rewrites":[]
                  }
                }
                """);
        WritingResultPresenter presenter = writingPresenter();
        PracticeResultContext context = context(
                "WRITING",
                List.of(q53, q54),
                Map.of(
                        "153", "학생은 문법 오류를 고칩니다.",
                        "154", "선택하지 않은 원문"),
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
        assertThat(detail.upgrade().evaluatorSample().available()).isTrue();
        assertThat(detail.upgrade().evaluatorSample().provenance())
                .isEqualTo("EVALUATOR_GENERATED_NOT_TEACHER_REFERENCE");
        assertThat(objectMapper.valueToTree(detail.upgrade()).toString())
                .doesNotContain("선택하지 않은 답안", "teacherReference");
    }

    @Test
    void writingDetailDoesNotPromoteLegacyFlatOrPendingQualitativeArtifacts() {
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
            assertThat(task.feedback().state()).isEqualTo("LEGACY_UNVERIFIED");
            assertThat(task.score().available()).isFalse();
            assertThat(task.officialCriteria()).isEmpty();
        });
        assertThat(legacyDetail.diagnosticAvailability())
                .isEqualTo("TASK_IDENTITY_UNAVAILABLE");

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
        assertThat(pendingDetail.diagnosticAvailability()).isEqualTo("FEEDBACK_UNAVAILABLE");
    }

    @Test
    void writingDetailFailsClosedWhenCurrentTrustMarkersAreMissingLegacyOrMismatched() {
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
                  "raw_score":8,"raw_score_max":9,"score_available":true,
                  "task_type":"Q53",
                  "scoring_contract":"LEGACY_BAND_V1",
                  "engine":"KSH_WRITING_EVALUATOR_V2",
                  "evaluation_status":"EVALUATED","evaluation_source":"PROVIDER",
                  "rubric_scores":[
                    {"criterionId":"W_CONTENT_TASK_ACHIEVEMENT","score":3,"maxScore":12},
                    {"criterionId":"W_ORGANIZATION_COHERENCE","score":3,"maxScore":9},
                    {"criterionId":"W_LANGUAGE_EXPRESSION","score":2,"maxScore":9}
                  ],
                  "upgraded_answer":"레거시 밴드 산출물"
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
                assertThat(task.feedback().state()).isEqualTo("LEGACY_UNVERIFIED");
                assertThat(task.officialCriteria()).isEmpty();
            });
            assertThat(detail.feedback().state()).isEqualTo("LEGACY_UNVERIFIED");
            assertThat(detail.diagnosticFindings()).isEmpty();
            assertThat(detail.diagnosticAvailability())
                    .isEqualTo("TASK_IDENTITY_UNAVAILABLE");
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
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {"153":{"raw_score":24,"raw_score_max":30,"score_available":true,
                  "task_type":"Q53","scoring_contract":"TASK_NATIVE_RUBRIC_V1",
                  "engine":"KSH_WRITING_EVALUATOR_V2",
                  "evaluation_status":"EVALUATED","evaluation_source":"PROVIDER",
                  "rubric_scores":[
                    {"criterionId":"W_CONTENT_TASK_ACHIEVEMENT","score":10,"maxScore":10},
                    {"criterionId":"W_ORGANIZATION_COHERENCE","score":7,"maxScore":9},
                    {"criterionId":"W_LANGUAGE_EXPRESSION","score":7,"maxScore":9}
                  ]}}
                """);

        PracticeResultPresenter.Presentation result = writingPresenter().present(context(
                "WRITING", List.of(question), Map.of("153", "Bài viết đã nộp"), attempt));
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
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {
                  "_contract":"speaking_ai_v1",
                  "speaking_feedback_by_question": {
                    "201": {
                      "evaluationStatus":"EVALUATED","scoreAvailable":false,"source":"PROVIDER",
                      "evaluatorCapability":"TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION",
                      "evidenceMode":"TRANSCRIPT_ONLY",
                      "evidenceContractVersion":"speaking-evidence-v1-transcript-language-only",
                      "contractTrust":"CURRENT_VERIFIED",
                      "promptVersion":"speaking-eval-v3-transcript-language-only",
                      "rubricVersion":"speaking-rubric-v2-transcript-language-profile",
                      "schemaVersion":"speaking-schema-v2-partial-language-profile",
                      "actuallyHeardTranscript":"first transcript",
                      "overallSummary":"Ý chính rõ và đúng chủ đề.",
                      "actionPlan":[
                        {"criterion":"S_GRAMMAR_SENTENCE_CONTROL","subCriterionId":"S_GRAMMAR_PARTICLES","title":"Ôn trợ từ theo bản chép lời","instruction":"Sửa ba câu dùng trợ từ chưa phù hợp.","reason":"Củng cố kiểm soát câu.","priority":"HIGH"}
                      ],
                      "rubricScores":[
                        {"criterion":"S_CONTENT_TASK_FULFILLMENT","score":16,"maxScore":20,"feedback":"Nội dung 1","availability":"SCORED"},
                        {"criterion":"S_GRAMMAR_SENTENCE_CONTROL","score":16,"maxScore":20,"feedback":"Ngữ pháp 1","availability":"SCORED"},
                        {"criterion":"S_VOCABULARY_EXPRESSIONS","score":12,"maxScore":15,"feedback":"Từ vựng 1","availability":"SCORED"},
                        {"criterion":"S_COHERENCE_ORGANIZATION","score":12,"maxScore":15,"feedback":"Mạch lạc 1","availability":"SCORED"},
                        {"criterion":"S_FLUENCY","score":null,"maxScore":null,"feedback":"Chưa chấm","availability":"NOT_SCORABLE"},
                        {"criterion":"S_PRONUNCIATION_DELIVERY","score":null,"maxScore":null,"feedback":"Chưa chấm","availability":"NOT_SCORABLE"}
                      ]
                    },
                    "202": {
                      "evaluationStatus":"EVALUATED","scoreAvailable":false,"source":"PROVIDER",
                      "evaluatorCapability":"TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION",
                      "evidenceMode":"TRANSCRIPT_ONLY",
                      "evidenceContractVersion":"speaking-evidence-v1-transcript-language-only",
                      "contractTrust":"CURRENT_VERIFIED",
                      "promptVersion":"speaking-eval-v3-transcript-language-only",
                      "rubricVersion":"speaking-rubric-v2-transcript-language-profile",
                      "schemaVersion":"speaking-schema-v2-partial-language-profile",
                      "actuallyHeardTranscript":"second transcript",
                      "overallSummary":"Diễn đạt phù hợp và có tiến bộ.",
                      "rubricScores":[
                        {"criterion":"S_CONTENT_TASK_FULFILLMENT","score":18,"maxScore":20,"feedback":"Nội dung 2","availability":"SCORED"},
                        {"criterion":"S_GRAMMAR_SENTENCE_CONTROL","score":17,"maxScore":20,"feedback":"Ngữ pháp 2","availability":"SCORED"},
                        {"criterion":"S_VOCABULARY_EXPRESSIONS","score":13,"maxScore":15,"feedback":"Từ vựng 2","availability":"SCORED"},
                        {"criterion":"S_COHERENCE_ORGANIZATION","score":13,"maxScore":15,"feedback":"Mạch lạc 2","availability":"SCORED"},
                        {"criterion":"S_FLUENCY","score":null,"maxScore":null,"feedback":"Chưa chấm","availability":"NOT_SCORABLE"},
                        {"criterion":"S_PRONUNCIATION_DELIVERY","score":null,"maxScore":null,"feedback":"Chưa chấm","availability":"NOT_SCORABLE"}
                      ]
                    }
                  }
                }
                """);
        SpeakingResultPresenter presenter = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackCompatibilityReader(),
                new WritingFeedbackCompatibilityReader(objectMapper),
                new WritingFeedbackViewMapper());

        PracticeResultContext speakingContext = context(
                "SPEAKING",
                List.of(first, second),
                Map.of("201", "first transcript", "202", "second transcript"),
                attempt);
        PracticeResultPresenter.Presentation result = presenter.present(speakingContext);
        SpeakingResultPayload payload = (SpeakingResultPayload) result.payload();

        assertThat(payload.coveredSegments()).isEqualTo(2);
        assertThat(payload.totalSegments()).isEqualTo(2);
        assertThat(payload.overallSummaries()).hasSize(2);
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
        assertThat(payload.profileState()).isEqualTo("READY");
        assertThat(payload.holisticScoreAvailable()).isFalse();
        assertThat(payload.holisticScore().available()).isFalse();
        assertThat(payload.actionPlan()).singleElement().satisfies(item -> {
            assertThat(item.criterionId()).isEqualTo("S_GRAMMAR_SENTENCE_CONTROL");
            assertThat(item.subcriterionId()).isEqualTo("S_GRAMMAR_PARTICLES");
        });
        assertThat(objectMapper.writeValueAsString(payload.actionPlan()))
                .doesNotContain("S_FLUENCY", "S_PRONUNCIATION", "ngữ điệu", "phát âm");
        assertThat(objectMapper.writeValueAsString(payload)).doesNotContain("questionId");

        SpeakingDetailPayload selected = (SpeakingDetailPayload) presenter.presentDetail(
                speakingContext, overview("SPEAKING", result), 202L);
        assertThat(selected.activeQuestionId()).isEqualTo(202L);
        assertThat(selected.tasks()).hasSize(2);
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
                .satisfies(criterion -> assertThat(criterion.score())
                        .isEqualByComparingTo("18"));
    }

    @Test
    void speakingDetailGroupsSelectedValidatedFindingsByKshFamilyAndSubcriterion() {
        PracticeQuestionVersion question = speakingQuestion(201L);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {
                  "_contract":"speaking_ai_v1",
                  "speaking_feedback_by_question":{"201":{
                    "evaluationStatus":"EVALUATED","scoreAvailable":false,"source":"PROVIDER",
                    "evaluatorCapability":"TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION",
                    "evidenceMode":"TRANSCRIPT_ONLY",
                    "evidenceContractVersion":"speaking-evidence-v1-transcript-language-only",
                    "contractTrust":"CURRENT_VERIFIED",
                    "promptVersion":"speaking-eval-v3-transcript-language-only",
                    "rubricVersion":"speaking-rubric-v2-transcript-language-profile",
                    "schemaVersion":"speaking-schema-v2-partial-language-profile",
                    "actuallyHeardTranscript":"저는 학교에 가요 그리고 친구를 만나요",
                    "rubricScores":[
                      {"criterion":"S_CONTENT_TASK_FULFILLMENT","score":16,"maxScore":20,"availability":"SCORED"},
                      {"criterion":"S_GRAMMAR_SENTENCE_CONTROL","score":15,"maxScore":20,"availability":"SCORED"},
                      {"criterion":"S_VOCABULARY_EXPRESSIONS","score":12,"maxScore":15,"availability":"SCORED"},
                      {"criterion":"S_COHERENCE_ORGANIZATION","score":11,"maxScore":15,"availability":"SCORED"},
                      {"criterion":"S_FLUENCY","score":null,"maxScore":null,"availability":"NOT_SCORABLE"},
                      {"criterion":"S_PRONUNCIATION_DELIVERY","score":null,"maxScore":null,"availability":"NOT_SCORABLE"}
                    ],
                    "needsImprovement":[
                      {"criterion":"S_GRAMMAR_SENTENCE_CONTROL",
                       "subCriterionId":"S_GRAMMAR_PARTICLES",
                       "evidenceScope":"TEXT_SPAN","evidence":"학교에",
                       "evidenceSource":"TRANSCRIPT",
                       "explanationVi":"Cần kiểm tra tiểu từ chỉ nơi chốn.",
                       "correction":"학교에"},
                      {"criterion":"S_GRAMMAR_SENTENCE_CONTROL",
                       "subCriterionId":"S_GRAMMAR_PARTICLES",
                       "evidenceScope":"TEXT_SPAN","evidence":"친구를",
                       "evidenceSource":"TRANSCRIPT",
                       "explanationVi":"Cần củng cố tiểu từ tân ngữ.",
                       "correction":"친구를"},
                      {"criterion":"S_GRAMMAR_SENTENCE_CONTROL",
                       "subCriterionId":"S_GRAMMAR_ENDINGS",
                       "evidenceScope":"TEXT_SPAN","evidence":"가요",
                       "evidenceSource":"TRANSCRIPT",
                       "explanationVi":"Cần thống nhất đuôi câu.",
                       "correction":"갑니다"}
                    ]
                  }}
                }
                """);
        SpeakingResultPresenter presenter = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackCompatibilityReader(),
                new WritingFeedbackCompatibilityReader(objectMapper),
                new WritingFeedbackViewMapper());
        PracticeResultContext context = context(
                "SPEAKING",
                List.of(question),
                Map.of("201", "저는 학교에 가요 그리고 친구를 만나요"),
                attempt);
        PracticeResultPresenter.Presentation presentation = presenter.present(context);

        SpeakingDetailPayload detail = (SpeakingDetailPayload) presenter.presentDetail(
                context, overview("SPEAKING", presentation), null);

        assertThat(detail.activeQuestionId()).isEqualTo(201L);
        assertThat(detail.evidence().transcriptMediaBinding()).isEqualTo("UNVERIFIED");
        assertThat(detail.diagnosticGroups()).singleElement().satisfies(group -> {
            assertThat(group.categoryCode()).isEqualTo("MORPHOSYNTAX");
            assertThat(group.labelVi()).contains("Hình thái");
            assertThat(group.labelKo()).contains("형태");
        });
        assertThat(detail.diagnosticFindings()).hasSize(3);
        assertThat(detail.diagnosticFindings()).allSatisfy(finding ->
                assertThat(finding.questionId()).isEqualTo(201L));
        assertThat(detail.filterChips()).hasSize(2);
        assertThat(detail.filterChips())
                .filteredOn(chip -> chip.id().contains("S_GRAMMAR_PARTICLES"))
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
                .filteredOn(chip -> chip.id().contains("S_GRAMMAR_ENDINGS"))
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
                .extracting(chip -> chip.id())
                .doesNotHaveDuplicates();
        assertThat(detail.scoreCriteria())
                .filteredOn(criterion -> criterion.criterionId()
                        .equals("S_GRAMMAR_SENTENCE_CONTROL"))
                .hasSize(1);
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
                new SpeakingFeedbackCompatibilityReader(),
                new WritingFeedbackCompatibilityReader(objectMapper),
                new WritingFeedbackViewMapper());
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
                new SpeakingFeedbackCompatibilityReader(),
                new WritingFeedbackCompatibilityReader(objectMapper),
                new WritingFeedbackViewMapper(),
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
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {
                  "_contract":"speaking_ai_v1",
                  "speaking_feedback_by_question":{
                    "201":{
                      "evaluationStatus":"EVALUATED","scoreAvailable":false,"source":"PROVIDER",
                      "evaluatorCapability":"TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION",
                      "evidenceMode":"TRANSCRIPT_ONLY",
                      "evidenceContractVersion":"speaking-evidence-v1-transcript-language-only",
                      "contractTrust":"CURRENT_VERIFIED",
                      "promptVersion":"speaking-eval-v3-transcript-language-only",
                      "rubricVersion":"speaking-rubric-v2-transcript-language-profile",
                      "schemaVersion":"speaking-schema-v2-partial-language-profile",
                      "actuallyHeardTranscript":"ready transcript",
                      "rubricScores":[
                        {"criterion":"S_CONTENT_TASK_FULFILLMENT","score":16,"maxScore":20,"availability":"SCORED"},
                        {"criterion":"S_GRAMMAR_SENTENCE_CONTROL","score":15,"maxScore":20,"availability":"SCORED"},
                        {"criterion":"S_VOCABULARY_EXPRESSIONS","score":12,"maxScore":15,"availability":"SCORED"},
                        {"criterion":"S_COHERENCE_ORGANIZATION","score":11,"maxScore":15,"availability":"SCORED"},
                        {"criterion":"S_FLUENCY","score":null,"maxScore":null,"availability":"NOT_SCORABLE"},
                        {"criterion":"S_PRONUNCIATION_DELIVERY","score":null,"maxScore":null,"availability":"NOT_SCORABLE"}
                      ]
                    },
                    "202":{"evaluationStatus":"PROCESSING"}
                  }
                }
                """);

        PracticeResultPresenter.Presentation result = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackCompatibilityReader(),
                new WritingFeedbackCompatibilityReader(objectMapper),
                new WritingFeedbackViewMapper()).present(context(
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
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {
                  "_contract":"speaking_ai_v1",
                  "speaking_feedback_by_question":{"201":{
                    "evaluationStatus":"TRANSCRIPTION_LOW_CONFIDENCE",
                    "scoreAvailable":false,
                    "source":"PROVIDER",
                    "evaluatorCapability":"TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION",
                    "evidenceMode":"TRANSCRIPT_ONLY",
                    "evidenceContractVersion":"speaking-evidence-v1-transcript-language-only",
                    "contractTrust":"CURRENT_VERIFIED",
                    "promptVersion":"speaking-eval-v3-transcript-language-only",
                    "rubricVersion":"speaking-rubric-v2-transcript-language-profile",
                    "schemaVersion":"speaking-schema-v2-partial-language-profile",
                    "actuallyHeardTranscript":"들은 문장",
                    "transcriptConfidence":0.31,
                    "rubricScores":[],
                    "criterionFeedback":[]
                  }}
                }
                """);

        PracticeResultPresenter.Presentation result = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackCompatibilityReader(),
                new WritingFeedbackCompatibilityReader(objectMapper),
                new WritingFeedbackViewMapper()).present(context(
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
                .isEqualTo("speaking-evidence-v1-transcript-language-only");
        assertThat(payload.contractTrust()).isEqualTo("CURRENT_VERIFIED");
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
    void speakingMixedLowConfidenceTranscriptAndLegacyKeepsAcousticRowsNotScorable() {
        PracticeQuestionVersion speaking = speakingQuestion(201L);
        PracticeQuestionVersion legacyEssay = speakingLegacyEssayQuestion(202L);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {
                  "_contract":"speaking_mixed_v1",
                  "speaking_feedback_by_question":{"201":{
                    "evaluationStatus":"TRANSCRIPTION_LOW_CONFIDENCE",
                    "scoreAvailable":false,
                    "source":"PROVIDER",
                    "evaluatorCapability":"TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION",
                    "evidenceMode":"TRANSCRIPT_ONLY",
                    "evidenceContractVersion":"speaking-evidence-v1-transcript-language-only",
                    "contractTrust":"CURRENT_VERIFIED",
                    "promptVersion":"speaking-eval-v3-transcript-language-only",
                    "rubricVersion":"speaking-rubric-v2-transcript-language-profile",
                    "schemaVersion":"speaking-schema-v2-partial-language-profile",
                    "actuallyHeardTranscript":"들은 문장",
                    "transcriptConfidence":0.31,
                    "rubricScores":[],
                    "criterionFeedback":[]
                  }},
                  "essay_feedback_by_question":{"202":{
                    "raw_score":8,"raw_score_max":10,
                    "summary_vi":"Bản sao lịch sử chỉ để đọc."
                  }}
                }
                """);
        SpeakingResultPresenter presenter = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackCompatibilityReader(),
                new WritingFeedbackCompatibilityReader(objectMapper),
                new WritingFeedbackViewMapper());
        PracticeResultContext context = context(
                "SPEAKING",
                List.of(speaking, legacyEssay),
                Map.of("201", "들은 문장", "202", "legacy written response"),
                attempt);
        PracticeResultPresenter.Presentation presentation = presenter.present(context);
        SpeakingResultPayload payload = (SpeakingResultPayload) presentation.payload();

        assertThat(payload.evidenceMode()).isEqualTo("TRANSCRIPT_ONLY");
        assertThat(payload.contractTrust()).isEqualTo("MIXED_WITH_LEGACY_UNVERIFIED");
        assertThat(payload.criteria().subList(0, 4)).allSatisfy(criterion ->
                assertThat(criterion.availability()).isEqualTo("LEGACY_UNVERIFIED"));
        assertThat(payload.criteria().subList(4, 6)).allSatisfy(criterion -> {
            assertThat(criterion.availability()).isEqualTo("NOT_SCORABLE");
            assertThat(criterion.score()).isNull();
            assertThat(criterion.weight()).isNull();
        });

        SpeakingDetailPayload detail = (SpeakingDetailPayload) presenter.presentDetail(
                context, overview("SPEAKING", presentation), null);
        assertThat(detail.scoreCriteria().subList(4, 6)).allSatisfy(criterion ->
                assertThat(criterion.availability()).isEqualTo("NOT_SCORABLE"));
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
                new SpeakingFeedbackCompatibilityReader(),
                new WritingFeedbackCompatibilityReader(objectMapper),
                new WritingFeedbackViewMapper()).present(context(
                "SPEAKING", List.of(question), Map.of("201", "submitted transcript"), attempt));
        SpeakingResultPayload payload = (SpeakingResultPayload) result.payload();

        assertThat(result.feedback().state()).isEqualTo("FAILED");
        assertThat(result.score().available()).isFalse();
        assertThat(payload.profileState()).isEqualTo("LEGACY_UNVERIFIED");
        assertThat(payload.evaluatorCapability()).isEqualTo("LEGACY_UNKNOWN");
        assertThat(payload.evidenceMode()).isEqualTo("UNKNOWN");
        assertThat(payload.holisticScoreAvailable()).isFalse();
        assertThat(payload.criteria()).allSatisfy(criterion -> {
            assertThat(criterion.availability()).isEqualTo("LEGACY_UNVERIFIED");
            assertThat(criterion.score()).isNull();
            assertThat(criterion.weight()).isNull();
        });
    }

    @Test
    void speakingLegacySixCriterionScoreIsIdentifiedAndNeverUpgraded() {
        PracticeQuestionVersion question = speakingQuestion(201L);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {"speaking_feedback_by_question":{"201":{
                  "score":8,"percentage":88.89,"summary_vi":"Kết quả cũ",
                  "rubric_scores":[
                    {"score":8},{"score":8},{"score":8},
                    {"score":8},{"score":8},{"score":8}
                  ]
                }}}
                """);

        PracticeResultPresenter.Presentation result = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackCompatibilityReader(),
                new WritingFeedbackCompatibilityReader(objectMapper),
                new WritingFeedbackViewMapper()).present(context(
                "SPEAKING", List.of(question), Map.of("201", "legacy transcript"), attempt));
        SpeakingResultPayload payload = (SpeakingResultPayload) result.payload();

        assertThat(result.score().available()).isFalse();
        assertThat(result.feedback().state()).isEqualTo("FAILED");
        assertThat(payload.coveredSegments()).isZero();
        assertThat(payload.legacyUnverifiedSegments()).isEqualTo(1);
        assertThat(payload.evaluatorCapability()).isEqualTo("LEGACY_UNKNOWN");
        assertThat(payload.contractTrust()).isEqualTo("LEGACY_UNVERIFIED");
        assertThat(payload.evidenceContractVersion()).isNull();
        assertThat(payload.holisticScoreAvailable()).isFalse();
        assertThat(payload.profileState()).isEqualTo("LEGACY_UNVERIFIED");
        assertThat(payload.criteria()).allSatisfy(criterion -> {
            assertThat(criterion.availability()).isEqualTo("LEGACY_UNVERIFIED");
            assertThat(criterion.score()).isNull();
            assertThat(criterion.weight()).isNull();
            assertThat(criterion.percentage()).isNull();
            assertThat(criterion.scoreDisplay()).isNull();
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
                new SpeakingFeedbackCompatibilityReader(),
                new WritingFeedbackCompatibilityReader(objectMapper),
                new WritingFeedbackViewMapper());

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

        PracticeResultPresenter.Presentation result = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackCompatibilityReader(),
                new WritingFeedbackCompatibilityReader(objectMapper),
                new WritingFeedbackViewMapper()).present(context(
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
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {
                  "_contract":"speaking_ai_v1",
                  "speaking_feedback_by_question":{"201":{
                    "evaluationStatus":"EVALUATION_UNAVAILABLE","scoreAvailable":false,
                    "source":"PROVIDER",
                    "evaluatorCapability":"TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION",
                    "evidenceMode":"TRANSCRIPT_ONLY",
                    "evidenceContractVersion":"speaking-evidence-v1-transcript-language-only",
                    "contractTrust":"CURRENT_VERIFIED",
                    "promptVersion":"speaking-eval-v3-transcript-language-only",
                    "rubricVersion":"speaking-rubric-v2-transcript-language-profile",
                    "schemaVersion":"speaking-schema-v2-partial-language-profile",
                    "rubricScores":[]
                  }}
                }
                """);

        PracticeResultPresenter.Presentation result = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackCompatibilityReader(),
                new WritingFeedbackCompatibilityReader(objectMapper),
                new WritingFeedbackViewMapper()).present(context(
                "SPEAKING", List.of(question), Map.of("201", "submitted transcript"), attempt));
        SpeakingResultPayload payload = (SpeakingResultPayload) result.payload();

        assertThat(result.feedback().state()).isEqualTo("FAILED");
        assertThat(result.score().available()).isFalse();
        assertThat(payload.profileState()).isEqualTo("FAILED");
        assertThat(payload.coveredSegments()).isZero();
        assertThat(payload.criteria()).allSatisfy(criterion -> {
            assertThat(criterion.availability()).isEqualTo("UNAVAILABLE");
            assertThat(criterion.score()).isNull();
            assertThat(criterion.scoreDisplay()).isNull();
        });
    }

    @Test
    void speakingOverviewIncludesLegacyEssaySegmentWithoutReturningPerQuestionPanels() throws Exception {
        PracticeQuestionVersion speaking = speakingQuestion(201L);
        PracticeQuestionVersion legacyEssay = speakingLegacyEssayQuestion(202L);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {
                  "_contract":"speaking_mixed_v1",
                  "speaking_feedback_by_question":{
                    "201":{
                      "summary_vi":"Phần nói rõ ý.",
                      "score":4,
                      "rubric_scores":[
                        {"score":4,"feedback":"Nội dung"},{"score":4,"feedback":"Ngữ pháp"},
                        {"score":4,"feedback":"Từ vựng"},{"score":4,"feedback":"Mạch lạc"},
                        {"score":4,"feedback":"Lưu loát"},{"score":4,"feedback":"Phát âm"}
                      ]
                    }
                  },
                  "essay_feedback_by_question":{
                    "202":{
                      "raw_score":8,"raw_score_max":10,
                      "summary_vi":"Phần trả lời viết lịch sử đã được giữ lại."
                    }
                  }
                }
                """);
        SpeakingResultPresenter presenter = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackCompatibilityReader(),
                new WritingFeedbackCompatibilityReader(objectMapper),
                new WritingFeedbackViewMapper());

        PracticeResultPresenter.Presentation result = presenter.present(context(
                "SPEAKING",
                List.of(speaking, legacyEssay),
                Map.of("201", "spoken transcript", "202", "legacy written response"),
                attempt));
        SpeakingResultPayload payload = (SpeakingResultPayload) result.payload();

        assertThat(result.feedback().state()).isEqualTo("FAILED");
        assertThat(payload.coveredSegments()).isZero();
        assertThat(payload.totalSegments()).isEqualTo(2);
        assertThat(payload.overallSummaries())
                .containsExactly("Phần trả lời viết lịch sử đã được giữ lại.");
        assertThat(payload.criteria())
                .allSatisfy(criterion -> {
                    assertThat(criterion.coveredSegments()).isZero();
                    assertThat(criterion.availability()).isEqualTo("LEGACY_UNVERIFIED");
                    assertThat(criterion.score()).isNull();
                });
        assertThat(payload.legacyUnverifiedSegments()).isEqualTo(2);
        assertThat(payload.contractTrust()).isEqualTo("LEGACY_UNVERIFIED");
        assertThat(payload.profileState()).isEqualTo("LEGACY_UNVERIFIED");
        assertThat(payload.holisticScoreAvailable()).isFalse();
        assertThat(objectMapper.writeValueAsString(payload)).doesNotContain("questionId");
    }

    @Test
    void speakingMixedCurrentTranscriptAndLegacyEssayCountsOnlyCurrentCoverage() {
        PracticeQuestionVersion speaking = speakingQuestion(211L);
        PracticeQuestionVersion legacyEssay = speakingLegacyEssayQuestion(212L);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {
                  "_contract":"speaking_mixed_v1",
                  "speaking_feedback_by_question":{"211":{
                    "evaluationStatus":"EVALUATED","scoreAvailable":false,"source":"PROVIDER",
                    "evaluatorCapability":"TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION",
                    "evidenceMode":"TRANSCRIPT_ONLY",
                    "evidenceContractVersion":"speaking-evidence-v1-transcript-language-only",
                    "contractTrust":"CURRENT_VERIFIED",
                    "promptVersion":"speaking-eval-v3-transcript-language-only",
                    "rubricVersion":"speaking-rubric-v2-transcript-language-profile",
                    "schemaVersion":"speaking-schema-v2-partial-language-profile",
                    "actuallyHeardTranscript":"current transcript",
                    "rubricScores":[
                      {"criterion":"S_CONTENT_TASK_FULFILLMENT","score":16,"maxScore":20,"availability":"SCORED"},
                      {"criterion":"S_GRAMMAR_SENTENCE_CONTROL","score":15,"maxScore":20,"availability":"SCORED"},
                      {"criterion":"S_VOCABULARY_EXPRESSIONS","score":12,"maxScore":15,"availability":"SCORED"},
                      {"criterion":"S_COHERENCE_ORGANIZATION","score":11,"maxScore":15,"availability":"SCORED"},
                      {"criterion":"S_FLUENCY","score":null,"maxScore":null,"availability":"NOT_SCORABLE"},
                      {"criterion":"S_PRONUNCIATION_DELIVERY","score":null,"maxScore":null,"availability":"NOT_SCORABLE"}
                    ]
                  }},
                  "essay_feedback_by_question":{"212":{
                    "raw_score":8,"raw_score_max":10,
                    "summary_vi":"Bản sao lịch sử chỉ để đọc."
                  }}
                }
                """);

        PracticeResultPresenter.Presentation result = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackCompatibilityReader(),
                new WritingFeedbackCompatibilityReader(objectMapper),
                new WritingFeedbackViewMapper()).present(context(
                "SPEAKING", List.of(speaking, legacyEssay),
                Map.of("211", "current transcript", "212", "legacy written response"), attempt));
        SpeakingResultPayload payload = (SpeakingResultPayload) result.payload();

        assertThat(result.feedback().state()).isEqualTo("PARTIAL");
        assertThat(payload.coveredSegments()).isEqualTo(1);
        assertThat(payload.totalSegments()).isEqualTo(2);
        assertThat(payload.legacyUnverifiedSegments()).isEqualTo(1);
        assertThat(payload.contractTrust()).isEqualTo("MIXED_WITH_LEGACY_UNVERIFIED");
        assertThat(payload.evaluatorCapability())
                .isEqualTo("TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION");
        assertThat(payload.profileState()).isEqualTo("PARTIAL");
        assertThat(payload.criteria().stream().filter(SpeakingCriterionResult::scored))
                .hasSize(4)
                .allSatisfy(row -> assertThat(row.coveredSegments()).isEqualTo(1));
        assertThat(payload.holisticScoreAvailable()).isFalse();
    }

    @Test
    void speakingOverviewKeepsSingleFlatLegacyEssayFeedback() throws Exception {
        PracticeQuestionVersion legacyEssay = speakingLegacyEssayQuestion(202L);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {
                  "score":7,
                  "percentage":77.78,
                  "summary_vi":"Phản hồi phẳng của câu lịch sử vẫn khả dụng."
                }
                """);
        SpeakingResultPresenter presenter = new SpeakingResultPresenter(
                objectMapper,
                new SpeakingFeedbackCompatibilityReader(),
                new WritingFeedbackCompatibilityReader(objectMapper),
                new WritingFeedbackViewMapper());

        PracticeResultPresenter.Presentation result = presenter.present(context(
                "SPEAKING",
                List.of(legacyEssay),
                Map.of("202", "legacy written response"),
                attempt));
        SpeakingResultPayload payload = (SpeakingResultPayload) result.payload();

        assertThat(result.feedback().state()).isEqualTo("FAILED");
        assertThat(payload.coveredSegments()).isZero();
        assertThat(payload.overallSummaries())
                .containsExactly("Phản hồi phẳng của câu lịch sử vẫn khả dụng.");
        assertThat(payload.legacyUnverifiedSegments()).isEqualTo(1);
        assertThat(payload.contractTrust()).isEqualTo("LEGACY_UNVERIFIED");
        assertThat(payload.profileState()).isEqualTo("LEGACY_UNVERIFIED");
        assertThat(payload.criteria()).allSatisfy(criterion -> {
            assertThat(criterion.availability()).isEqualTo("LEGACY_UNVERIFIED");
            assertThat(criterion.score()).isNull();
        });
        assertThat(objectMapper.writeValueAsString(payload)).doesNotContain("questionId");
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
                new SpeakingFeedbackCompatibilityReader(),
                new WritingFeedbackCompatibilityReader(objectMapper),
                new WritingFeedbackViewMapper());

        PracticeResultPresenter.Presentation result = presenter.present(context(
                "SPEAKING",
                List.of(question),
                Map.of("201", "submitted transcript"),
                attempt));

        assertThat(result.feedback().state()).isEqualTo("FAILED");
        assertThat(result.answers().unscorable()).isEqualTo(1);
        assertThat(result.score().available()).isFalse();
        SpeakingResultPayload payload = (SpeakingResultPayload) result.payload();
        assertThat(payload.legacyUnverifiedSegments()).isEqualTo(1);
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
                new SpeakingFeedbackCompatibilityReader(),
                new WritingFeedbackCompatibilityReader(objectMapper),
                new WritingFeedbackViewMapper());

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
        when(attempt.getPublishedVersionId()).thenReturn(10L);
        when(attempt.getSetVersionId()).thenReturn(11L);
        when(attempt.getTestVersionId()).thenReturn(12L);
        when(attempt.getSectionVersionId()).thenReturn(13L);
        PracticeVersionSnapshot lockedSnapshot = snapshot("READING", List.of());
        when(versions.snapshot(10L, 11L, 12L, 13L))
                .thenReturn(Optional.of(lockedSnapshot));

        PracticeResultAssembler missing = new PracticeResultAssembler(
                attempts, versions, objectMapper, List.of());
        assertThatThrownBy(() -> missing.assemble(1L, 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đúng một result presenter");

        PracticeResultPresenter first = mock(PracticeResultPresenter.class);
        PracticeResultPresenter second = mock(PracticeResultPresenter.class);
        when(first.supports("READING")).thenReturn(true);
        when(second.supports("READING")).thenReturn(true);
        PracticeResultAssembler ambiguous = new PracticeResultAssembler(
                attempts, versions, objectMapper, List.of(first, second));
        assertThatThrownBy(() -> ambiguous.assemble(1L, 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đúng một result presenter");
    }

    private WritingResultPresenter writingPresenter() {
        QuestionTypeResolver typeResolver = new QuestionTypeResolver();
        return new WritingResultPresenter(
                objectMapper,
                new WritingFeedbackViewMapper(),
                new WritingFeedbackCompatibilityReader(objectMapper),
                new AssessmentContractCodec(objectMapper, typeResolver),
                typeResolver,
                new AssessmentScoringEngine());
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
        PracticeSectionVersion section = mock(PracticeSectionVersion.class);
        when(section.getSkill()).thenReturn(skill);
        return new PracticeVersionSnapshot(
                mock(PracticePublishedVersion.class),
                mock(PracticeSetVersion.class),
                mock(PracticeTestVersion.class),
                section,
                List.of(),
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

    private static PracticeQuestionVersion speakingLegacyEssayQuestion(Long id) {
        PracticeQuestionVersion question = mock(PracticeQuestionVersion.class);
        when(question.getId()).thenReturn(id + 1000);
        when(question.getQuestionId()).thenReturn(id);
        when(question.getQuestionNo()).thenReturn(id.intValue());
        when(question.getQuestionType()).thenReturn("ESSAY");
        when(question.getPrompt()).thenReturn("Legacy speaking prompt " + id);
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
