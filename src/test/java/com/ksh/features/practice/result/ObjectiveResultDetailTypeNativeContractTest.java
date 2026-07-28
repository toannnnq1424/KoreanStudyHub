package com.ksh.features.practice.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.PracticeSectionVersion;
import com.ksh.entities.PracticeSetVersion;
import com.ksh.entities.PracticeTestVersion;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService.BlankExplanation;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService.EvidenceTranslation;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService.FillBlankExplanation;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService.ObjectiveExplanationArtifact;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService.OptionRationale;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService.SingleChoiceExplanation;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService.TextEvidence;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService.TfngExplanation;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.AssessmentScoringEngine;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.LearnerAnswer;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.assessment.ScoringPolicyCode;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveDetailPayload;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveFillBlankDetail;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveImageEvidenceRef;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveQuestionDetail;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveResultPayload;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveSingleChoiceDetail;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveSourceGroup;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveTfngDetail;
import com.ksh.features.practice.dto.PracticeDtos.PracticeAttemptResultView;
import com.ksh.features.practice.dto.PracticeDtos.ResultAnswerDistribution;
import com.ksh.features.practice.dto.PracticeDtos.ResultFeedbackAvailability;
import com.ksh.features.practice.dto.PracticeDtos.ResultScoreSummary;
import com.ksh.features.practice.service.PracticeVersionSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObjectiveResultDetailTypeNativeContractTest {

    @Test
    void objectiveQuestionDtoDiscriminatorIsSealedToTheThreeCanonicalTypes() {
        assertThat(ObjectiveQuestionDetail.class.getPermittedSubclasses())
                .extracting(Class::getSimpleName)
                .containsExactlyInAnyOrder(
                        "ObjectiveSingleChoiceDetail",
                        "ObjectiveFillBlankDetail",
                        "ObjectiveTfngDetail");
        assertThat(ObjectiveImageEvidenceRef.class.getRecordComponents())
                .extracting(component -> component.getName())
                .contains("assetDigest", "imageIndex", "regionMode")
                .doesNotContain("pageIndex");
    }

    @Test
    void objectiveDetailUsesExactlyThreeTypedDiscriminatorsAndBackendAnswerAuthority() {
        AssessmentContractCodec codec = mock(AssessmentContractCodec.class);
        QuestionExplanationReadService explanations = mock(QuestionExplanationReadService.class);
        ObjectiveResultPresenter presenter = new ObjectiveResultPresenter(
                codec,
                new QuestionTypeResolver(),
                new AssessmentScoringEngine(),
                explanations,
                new ObjectMapper());

        PracticeQuestionVersion single = question(
                101L, 1001L, 1, "SINGLE_CHOICE", "Chọn đáp án.");
        PracticeQuestionVersion fill = question(
                102L, 1002L, 2, "FILL_BLANK", "Điền vào chỗ trống.");
        PracticeQuestionVersion tfng = question(
                103L, 1003L, 3, "TRUE_FALSE_NOT_GIVEN", "서울은 한국의 수도이다.");

        QuestionContent singleContent = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(
                        new QuestionContent.Option("stable-a", "서울"),
                        new QuestionContent.Option("stable-b", "부산")),
                List.of());
        AnswerSpec singleSpec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.SINGLE_CHOICE,
                List.of("stable-a"),
                null,
                List.of(),
                ScoringPolicyCode.ALL_OR_NOTHING);
        LearnerAnswer singleAnswer = new LearnerAnswer(
                LearnerAnswer.SCHEMA_VERSION,
                CanonicalQuestionType.SINGLE_CHOICE,
                List.of("foreign-option"),
                null,
                Map.of(),
                null);

        QuestionContent fillContent = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(),
                List.of(new QuestionContent.Blank("stable-blank", "서울은 ___입니다.")));
        AnswerSpec fillSpec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.FILL_BLANK,
                List.of(),
                null,
                List.of(new AnswerSpec.BlankAnswer(
                        "stable-blank", List.of("수도", "首都"))),
                ScoringPolicyCode.NORMALIZED_EXACT);
        LearnerAnswer fillAnswer = new LearnerAnswer(
                LearnerAnswer.SCHEMA_VERSION,
                CanonicalQuestionType.FILL_BLANK,
                List.of(),
                null,
                Map.of("stable-blank", "수도"),
                null);

        QuestionContent tfngContent = QuestionContent.empty();
        AnswerSpec tfngSpec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN,
                List.of(),
                "FALSE",
                List.of(),
                ScoringPolicyCode.ALL_OR_NOTHING);
        LearnerAnswer tfngAnswer = new LearnerAnswer(
                LearnerAnswer.SCHEMA_VERSION,
                CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN,
                List.of(),
                "TRUE",
                Map.of(),
                null);

        when(codec.readQuestionContent("content-101", CanonicalQuestionType.SINGLE_CHOICE))
                .thenReturn(singleContent);
        when(codec.readAnswerSpec("answer-101", singleContent)).thenReturn(singleSpec);
        when(codec.readLearnerAnswer("{learner-single}")).thenReturn(singleAnswer);
        when(codec.readQuestionContent("content-102", CanonicalQuestionType.FILL_BLANK))
                .thenReturn(fillContent);
        when(codec.readAnswerSpec("answer-102", fillContent)).thenReturn(fillSpec);
        when(codec.readLearnerAnswer("{learner-fill}")).thenReturn(fillAnswer);
        when(codec.readQuestionContent(
                "content-103", CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN))
                .thenReturn(tfngContent);
        when(codec.readAnswerSpec("answer-103", tfngContent)).thenReturn(tfngSpec);
        when(codec.readLearnerAnswer("{learner-tfng}")).thenReturn(tfngAnswer);

        TextEvidence evidence = new TextEvidence(
                "e1", "TEXT_SPAN", "ANSWER_RATIONALE", "PASSAGE",
                "서울", 0, 2);
        when(explanations.readObjective(101L, CanonicalQuestionType.SINGLE_CHOICE))
                .thenReturn(Optional.of(new ObjectiveExplanationArtifact(
                        "v3",
                        CanonicalQuestionType.SINGLE_CHOICE,
                        "Nghĩa",
                        "Lý do đúng",
                        List.of(evidence),
                        List.of(new EvidenceTranslation("e1", "Seoul")),
                        new SingleChoiceExplanation(
                                "option_2",
                                List.of(
                                        new OptionRationale(
                                                "option_1", "Phương án đúng", List.of("e1")),
                                        new OptionRationale(
                                                "option_2", "Phương án bị loại", List.of("e1")))),
                        201L)));
        when(explanations.readObjective(102L, CanonicalQuestionType.FILL_BLANK))
                .thenReturn(Optional.of(new ObjectiveExplanationArtifact(
                        "v3",
                        CanonicalQuestionType.FILL_BLANK,
                        "Nghĩa",
                        "Lý do",
                        List.of(evidence),
                        List.of(),
                        new FillBlankExplanation(List.of(new BlankExplanation(
                                "blank_1",
                                "Danh từ phù hợp ngữ cảnh.",
                                "Chỉ địa vị hành chính.",
                                "Dùng danh từ.",
                                "Sắc thái trung tính.",
                                List.of("e1")))),
                        202L)));
        when(explanations.readObjective(
                103L, CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN))
                .thenReturn(Optional.of(new ObjectiveExplanationArtifact(
                        "v3",
                        CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN,
                        "Nghĩa",
                        "Lý do",
                        List.of(evidence),
                        List.of(),
                        new TfngExplanation(
                                "Nguồn mâu thuẫn với mệnh đề.",
                                "TRUE không áp dụng.",
                                "FALSE là đáp án chính thức.",
                                "NOT_GIVEN không áp dụng.",
                                ""),
                        203L)));

        PracticeResultContext context = context(
                List.of(single, fill, tfng),
                Map.of(
                        "1001", "{learner-single}",
                        "1002", "{learner-fill}",
                        "1003", "{learner-tfng}"));
        PracticeAttemptResultView overview = mock(PracticeAttemptResultView.class);
        when(overview.payload()).thenReturn(new ObjectiveResultPayload(List.of()));
        when(overview.score()).thenReturn(score());
        when(overview.answers()).thenReturn(answers());
        when(overview.feedback()).thenReturn(feedback());

        ObjectiveDetailPayload detail = (ObjectiveDetailPayload) presenter.presentDetail(
                context, overview, null);

        assertThat(detail.questions()).hasSize(3);
        assertThat(detail.questions().get(0)).isInstanceOf(ObjectiveSingleChoiceDetail.class);
        assertThat(detail.questions().get(1)).isInstanceOf(ObjectiveFillBlankDetail.class);
        assertThat(detail.questions().get(2)).isInstanceOf(ObjectiveTfngDetail.class);
        ObjectiveSingleChoiceDetail singleDetail =
                (ObjectiveSingleChoiceDetail) detail.questions().get(0);
        assertThat(singleDetail.options()).extracting(option -> option.optionId())
                .containsExactly("stable-a", "stable-b");
        assertThat(singleDetail.options()).noneMatch(option -> option.learnerSelected());
        assertThat(singleDetail.options()).extracting(option -> option.optionId())
                .doesNotContain("foreign-option");
        assertThat(singleDetail.options()).filteredOn(option -> option.correct())
                .singleElement()
                .satisfies(option -> assertThat(option.optionId()).isEqualTo("stable-a"));
        assertThat(singleDetail.explanation().evidenceTranslations())
                .singleElement()
                .satisfies(translation -> {
                    assertThat(translation.evidenceId()).isEqualTo("e1");
                    assertThat(translation.label()).isEqualTo("Dịch đoạn liên quan");
                });
        ObjectiveFillBlankDetail fillDetail =
                (ObjectiveFillBlankDetail) detail.questions().get(1);
        assertThat(fillDetail.blanks().get(0).acceptedValues())
                .containsExactly("수도", "首都");
        ObjectiveTfngDetail tfngDetail =
                (ObjectiveTfngDetail) detail.questions().get(2);
        assertThat(tfngDetail.officialValue()).isEqualTo("FALSE");
        assertThat(tfngDetail.relation()).isEqualTo("CONTRADICTED");
        assertThat(tfngDetail.officialValueLabelVi()).isEqualTo("Sai");
        assertThat(tfngDetail.officialValueLabelKo()).isEqualTo("틀림");
        assertThat(tfngDetail.relationLabelVi()).isEqualTo("Trái với nguồn");
        assertThat(tfngDetail.relationLabelKo()).isEqualTo("근거와 모순됨");
        assertThat(tfngDetail.alternatives()).extracting(alternative -> alternative.label())
                .containsExactly("TRUE", "NOT_GIVEN");
        assertThat(tfngDetail.alternatives()).extracting(alternative -> alternative.labelVi())
                .containsExactly("Đúng", "Không có thông tin");
        assertThat(detail.sourceGroups()).singleElement().satisfies(source ->
                assertThat(source.questionVersionIds()).containsExactly(101L, 102L, 103L));
        assertThat(detail.constructRegistryState()).isEqualTo(
                "DEFERRED_PRE_PHASE_14_REGISTRY");

        ObjectiveSourceGroup source = detail.sourceGroups().get(0);
        assertThat(source.provenanceLabelVi()).isEqualTo("Nguồn đề đã xuất bản và khóa");
        assertThat(source.provenanceLabelKo()).isEqualTo("게시 후 잠긴 출제 자료");
        ObjectiveSourceGroup duplicateNavigation = new ObjectiveSourceGroup(
                source.sourceId() + "-duplicate",
                source.groupVersionId(),
                source.label(),
                source.sourceKind(),
                source.instruction(),
                source.passageText(),
                source.transcriptText(),
                source.imageUrl(),
                source.audioUrl(),
                source.provenance(),
                source.transcriptEvidenceScope(),
                List.of(101L));
        assertThatThrownBy(() -> new ObjectiveDetailPayload(
                detail.score(),
                detail.answers(),
                detail.feedback(),
                detail.summary(),
                List.of(source, duplicateNavigation),
                detail.questions(),
                detail.constructRegistryState(),
                detail.constructRegistryNote()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("navigation must be unique");
    }

    private static PracticeQuestionVersion question(
            Long versionId,
            Long questionId,
            int questionNo,
            String type,
            String prompt) {
        PracticeQuestionVersion question = mock(PracticeQuestionVersion.class);
        when(question.getId()).thenReturn(versionId);
        when(question.getQuestionId()).thenReturn(questionId);
        when(question.getQuestionNo()).thenReturn(questionNo);
        when(question.getDisplayOrder()).thenReturn(questionNo);
        when(question.getQuestionType()).thenReturn(type);
        when(question.getPrompt()).thenReturn(prompt);
        when(question.getQuestionContentJson()).thenReturn("content-" + versionId);
        when(question.getAnswerSpecJson()).thenReturn("answer-" + versionId);
        when(question.getPoints()).thenReturn(BigDecimal.ONE);
        return question;
    }

    private static PracticeResultContext context(
            List<PracticeQuestionVersion> questions,
            Map<String, String> answers) {
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getSkill()).thenReturn("READING");
        PracticeSetVersion set = mock(PracticeSetVersion.class);
        when(set.getTitle()).thenReturn("Bộ đề");
        PracticeSectionVersion section = mock(PracticeSectionVersion.class);
        when(section.getInstructions()).thenReturn("서울 본문");
        return new PracticeResultContext(
                attempt,
                new PracticeVersionSnapshot(
                        mock(PracticePublishedVersion.class),
                        set,
                        mock(PracticeTestVersion.class),
                        section,
                        List.of(),
                        questions),
                answers,
                score());
    }

    private static ResultScoreSummary score() {
        return new ResultScoreSummary(
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(3),
                BigDecimal.valueOf(66.67),
                "EARNED_POINTS",
                "Điểm đạt được",
                null);
    }

    private static ResultAnswerDistribution answers() {
        return new ResultAnswerDistribution(1, 0, 2, 0, 0, 0, 3, 3);
    }

    private static ResultFeedbackAvailability feedback() {
        return new ResultFeedbackAvailability("READY", "Đã sẵn sàng", 3, 3);
    }
}
