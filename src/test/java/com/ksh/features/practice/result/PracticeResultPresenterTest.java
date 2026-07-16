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
import com.ksh.features.practice.dto.PracticeDtos.ResultEvaluationBand;
import com.ksh.features.practice.dto.PracticeDtos.ResultFeedbackAvailability;
import com.ksh.features.practice.dto.PracticeDtos.ResultScoreSummary;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingResultPayload;
import com.ksh.features.practice.dto.PracticeDtos.WritingResultPayload;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.service.PracticePublishedVersionService;
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
    void objectiveOverviewPreservesPartialPendingUnansweredAndUnscorableStates() {
        AssessmentContractCodec codec = mock(AssessmentContractCodec.class);
        QuestionTypeResolver typeResolver = mock(QuestionTypeResolver.class);
        AssessmentScoringEngine scoringEngine = mock(AssessmentScoringEngine.class);
        QuestionExplanationReadService explanations = mock(QuestionExplanationReadService.class);
        ObjectiveResultPresenter presenter = new ObjectiveResultPresenter(
                codec, typeResolver, scoringEngine, explanations);
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
        assertThat(result.score().available()).isTrue();
    }

    @Test
    void koreanWritingKeepsOfficialRubricAndAddsFourNonDuplicatingAnalysisLensesForLongForm() {
        PracticeQuestionVersion q51 = writingQuestion(151L, 51, WritingTaskType.Q51);
        PracticeQuestionVersion q53 = writingQuestion(153L, 53, WritingTaskType.Q53);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {
                  "153": {
                    "raw_score": 24,
                    "raw_score_max": 30,
                    "score_available": true,
                    "summary_vi": "Bài viết hoàn thành đúng nhiệm vụ.",
                    "rubric_scores": [
                      {"criterionId":"W_CONTENT_TASK_ACHIEVEMENT","score":10,"maxScore":12,"feedback":"Đủ ý chính"},
                      {"criterionId":"W_ORGANIZATION_COHERENCE","score":7,"maxScore":9,"feedback":"Bố cục rõ"},
                      {"criterionId":"W_LANGUAGE_EXPRESSION","score":7,"maxScore":9,"feedback":"Diễn đạt phù hợp"}
                    ]
                  }
                }
                """);
        WritingResultPresenter presenter = writingPresenter();

        PracticeResultPresenter.Presentation result = presenter.present(context(
                "WRITING",
                List.of(q51, q53),
                Map.of("151", "short answer", "153", "long answer"),
                attempt));
        WritingResultPayload payload = (WritingResultPayload) result.payload();

        assertThat(payload.tasks()).hasSize(2);
        assertThat(payload.tasks().get(0).officialCriteria()).hasSize(6);
        assertThat(payload.tasks().get(0).analysisLenses()).isEmpty();
        assertThat(payload.tasks().get(1).officialCriteria())
                .extracting(criterion -> criterion.label())
                .containsExactly(
                        "Hoàn thành nhiệm vụ và Nội dung",
                        "Cấu trúc và Mạch lạc",
                        "Ngôn ngữ và Biểu đạt");
        assertThat(payload.tasks().get(1).analysisLenses())
                .extracting(lens -> lens.label())
                .containsExactly(
                        "Nhiệm vụ và Nội dung",
                        "Cấu trúc và mạch lạc",
                        "Từ vựng và Diễn đạt",
                        "Ngữ pháp và Độ chính xác");
        assertThat(payload.tasks().get(1).analysisLenses().subList(2, 4))
                .allSatisfy(lens -> {
                    assertThat(lens.countedSeparately()).isFalse();
                    assertThat(lens.band()).isEqualTo(ResultEvaluationBand.UNAVAILABLE);
                    assertThat(lens.score()).isNull();
                });
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
        assertThat(payload.tasks().get(0).officialCriteria())
                .allSatisfy(criterion -> {
                    assertThat(criterion.score()).isNull();
                    assertThat(criterion.band()).isEqualTo(ResultEvaluationBand.UNAVAILABLE);
                });
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
        assertThat(payload.tasks().get(0).officialCriteria())
                .allSatisfy(criterion -> assertThat(criterion.score()).isNull());
    }

    @Test
    void speakingOverviewAggregatesAllSegmentsWithoutReturningPerQuestionPanels() throws Exception {
        PracticeQuestionVersion first = speakingQuestion(201L);
        PracticeQuestionVersion second = speakingQuestion(202L);
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getAiFeedbackJson()).thenReturn("""
                {
                  "speaking_feedback_by_question": {
                    "201": {
                      "summary_vi":"Ý chính rõ nhưng cần nói liền mạch hơn.",
                      "score":4,
                      "rubric_scores":[
                        {"score":4,"feedback":"Nội dung 1"},{"score":4,"feedback":"Ngữ pháp 1"},
                        {"score":4,"feedback":"Từ vựng 1"},{"score":4,"feedback":"Mạch lạc 1"},
                        {"score":4,"feedback":"Lưu loát 1"},{"score":4,"feedback":"Phát âm 1"}
                      ]
                    },
                    "202": {
                      "summary_vi":"Diễn đạt phù hợp và có tiến bộ.",
                      "score":5,
                      "rubric_scores":[
                        {"score":5,"feedback":"Nội dung 2"},{"score":5,"feedback":"Ngữ pháp 2"},
                        {"score":5,"feedback":"Từ vựng 2"},{"score":5,"feedback":"Mạch lạc 2"},
                        {"score":5,"feedback":"Lưu loát 2"},{"score":5,"feedback":"Phát âm 2"}
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

        PracticeResultPresenter.Presentation result = presenter.present(context(
                "SPEAKING",
                List.of(first, second),
                Map.of("201", "first transcript", "202", "second transcript"),
                attempt));
        SpeakingResultPayload payload = (SpeakingResultPayload) result.payload();

        assertThat(payload.coveredSegments()).isEqualTo(2);
        assertThat(payload.totalSegments()).isEqualTo(2);
        assertThat(payload.overallSummaries()).hasSize(2);
        assertThat(payload.criteria()).hasSize(6)
                .allSatisfy(criterion -> assertThat(criterion.coveredSegments()).isEqualTo(2));
        assertThat(payload.criteria().stream()
                .filter(criterion -> criterion.criterionId().equals("S_FLUENCY")
                        || criterion.criterionId().equals("S_PRONUNCIATION_DELIVERY")))
                .allMatch(criterion -> criterion.advisoryOnly());
        assertThat(payload.evidenceMode()).isEqualTo("TRANSCRIPT_ONLY");
        assertThat(payload.holisticScore().available()).isTrue();
        assertThat(objectMapper.writeValueAsString(payload)).doesNotContain("questionId");
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
        assertThat(((SpeakingResultPayload) result.payload()).holisticScore().available()).isFalse();
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

        assertThat(result.feedback().state()).isEqualTo("READY");
        assertThat(payload.coveredSegments()).isEqualTo(2);
        assertThat(payload.totalSegments()).isEqualTo(2);
        assertThat(payload.overallSummaries())
                .contains("Phần nói rõ ý.", "Phần trả lời viết lịch sử đã được giữ lại.");
        assertThat(payload.criteria())
                .allSatisfy(criterion -> assertThat(criterion.coveredSegments()).isEqualTo(1));
        assertThat(objectMapper.writeValueAsString(payload)).doesNotContain("questionId");
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

        assertThat(result.feedback().state()).isEqualTo("READY");
        assertThat(payload.coveredSegments()).isEqualTo(1);
        assertThat(payload.overallSummaries())
                .containsExactly("Phản hồi phẳng của câu lịch sử vẫn khả dụng.");
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
        PracticeQuestionVersion question = mock(PracticeQuestionVersion.class);
        when(question.getId()).thenReturn(id);
        when(question.getQuestionId()).thenReturn(id);
        when(question.getQuestionType()).thenReturn("SINGLE_CHOICE");
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
        when(question.getQuestionType()).thenReturn("SPEAKING");
        return question;
    }

    private static PracticeQuestionVersion speakingLegacyEssayQuestion(Long id) {
        PracticeQuestionVersion question = mock(PracticeQuestionVersion.class);
        when(question.getId()).thenReturn(id + 1000);
        when(question.getQuestionId()).thenReturn(id);
        when(question.getQuestionType()).thenReturn("ESSAY");
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
