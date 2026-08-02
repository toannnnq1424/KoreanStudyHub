package com.ksh.features.practice.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeQuestionGroupVersion;
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
import com.ksh.features.practice.assessment.ObjectiveExplanationStrategyRegistry;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.assessment.ScoringPolicyCode;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveDetailPayload;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveDetailCapabilityCode;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveDetailCapabilityState;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveFillBlankDetail;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveImageEvidenceRef;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveMatchingDetail;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveMultipleAnswerDetail;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveOptionState;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveOptionResult;
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
    void objectiveQuestionDtoDiscriminatorIsSealedToTheFiveCanonicalTypes() {
        assertThat(ObjectiveQuestionDetail.class.getPermittedSubclasses())
                .extracting(Class::getSimpleName)
                .containsExactlyInAnyOrder(
                        "ObjectiveSingleChoiceDetail",
                        "ObjectiveMultipleAnswerDetail",
                        "ObjectiveMatchingDetail",
                        "ObjectiveFillBlankDetail",
                        "ObjectiveTfngDetail");
        assertThat(ObjectiveImageEvidenceRef.class.getRecordComponents())
                .extracting(component -> component.getName())
                .contains("assetDigest", "imageIndex", "regionMode")
                .doesNotContain("pageIndex");
    }

    @Test
    void pendingObjectiveSelectionUsesBlueStateWithoutRevealingCorrectness() {
        ObjectiveOptionResult selected = new ObjectiveOptionResult(
                "stable-a",
                "A",
                "선택",
                null,
                true,
                false,
                ObjectiveOptionState.USER_SELECTED_PENDING,
                "",
                "PENDING_REVEAL",
                List.of());
        ObjectiveOptionResult neutral = new ObjectiveOptionResult(
                "stable-b",
                "B",
                "대기",
                null,
                false,
                true,
                ObjectiveOptionState.UNSELECTED_PENDING,
                "",
                "PENDING_REVEAL",
                List.of());

        assertThat(selected.statusLabelVi()).isEqualTo("Bạn đã chọn");
        assertThat(selected.hasStatusLabel()).isTrue();
        assertThat(neutral.statusLabelVi()).isBlank();
        assertThat(neutral.hasStatusLabel()).isFalse();
    }

    @Test
    void objectiveDetailUsesFiveTypedDiscriminatorsAndBackendAnswerAuthority() {
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
        PracticeQuestionVersion multiple = question(
                104L, 1004L, 4, "MULTIPLE_ANSWER", "맞는 것을 모두 고르십시오.");
        PracticeQuestionVersion matching = question(
                105L, 1005L, 5, "MATCHING", "각 설명을 항목과 연결하십시오.");

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

        QuestionContent multipleContent = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(
                        new QuestionContent.Option("multi-a", "아침에 공부합니다."),
                        new QuestionContent.Option("multi-b", "밤에 수영합니다."),
                        new QuestionContent.Option("multi-c", "책을 읽습니다.")),
                List.of());
        AnswerSpec multipleSpec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.MULTIPLE_ANSWER,
                List.of("multi-a", "multi-c"),
                null,
                List.of(),
                ScoringPolicyCode.ALL_OR_NOTHING);
        LearnerAnswer multipleAnswer = new LearnerAnswer(
                LearnerAnswer.SCHEMA_VERSION,
                CanonicalQuestionType.MULTIPLE_ANSWER,
                List.of("multi-a", "multi-b"),
                null,
                Map.of(),
                null);

        QuestionContent matchingContent = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(
                        new QuestionContent.Option("match-a", "서울"),
                        new QuestionContent.Option("match-b", "부산"),
                        new QuestionContent.Option("match-c", "제주")),
                List.of(
                        new QuestionContent.Blank("target-1", "대한민국의 수도"),
                        new QuestionContent.Blank("target-2", "한라산이 있는 섬")));
        AnswerSpec matchingSpec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.MATCHING,
                List.of(),
                null,
                List.of(
                        new AnswerSpec.BlankAnswer("target-1", List.of("match-a")),
                        new AnswerSpec.BlankAnswer("target-2", List.of("match-c"))),
                ScoringPolicyCode.NORMALIZED_EXACT);
        LearnerAnswer matchingAnswer = new LearnerAnswer(
                LearnerAnswer.SCHEMA_VERSION,
                CanonicalQuestionType.MATCHING,
                List.of(),
                null,
                Map.of("target-1", "match-b", "target-2", "match-c"),
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
        when(codec.readQuestionContent(
                "content-104", CanonicalQuestionType.MULTIPLE_ANSWER))
                .thenReturn(multipleContent);
        when(codec.readAnswerSpec("answer-104", multipleContent))
                .thenReturn(multipleSpec);
        when(codec.readLearnerAnswer("{learner-multiple}"))
                .thenReturn(multipleAnswer);
        when(codec.readQuestionContent("content-105", CanonicalQuestionType.MATCHING))
                .thenReturn(matchingContent);
        when(codec.readAnswerSpec("answer-105", matchingContent))
                .thenReturn(matchingSpec);
        when(codec.readLearnerAnswer("{learner-matching}"))
                .thenReturn(matchingAnswer);
        when(explanations.readObjective(104L, CanonicalQuestionType.MULTIPLE_ANSWER))
                .thenReturn(Optional.empty());
        when(explanations.readObjective(105L, CanonicalQuestionType.MATCHING))
                .thenReturn(Optional.empty());

        TextEvidence evidence = new TextEvidence(
                "e1", "TEXT_SPAN", "ANSWER_RATIONALE", "PASSAGE",
                "서울", 0, 2);
        when(explanations.readObjective(101L, CanonicalQuestionType.SINGLE_CHOICE))
                .thenReturn(Optional.of(new ObjectiveExplanationArtifact(
                        "v3",
                        CanonicalQuestionType.SINGLE_CHOICE,
                        ObjectiveExplanationStrategyRegistry.CURRENT_REGISTRY_VERSION,
                        "EXACT_EVIDENCE_ONLY",
                        ObjectiveExplanationStrategyRegistry.STRATEGY_VERSION,
                        "Nghĩa",
                        "Lý do đúng",
                        List.of(),
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
                        ObjectiveExplanationStrategyRegistry.CURRENT_REGISTRY_VERSION,
                        "FILL_SLOT_GRAMMAR_ANALYSIS",
                        ObjectiveExplanationStrategyRegistry.STRATEGY_VERSION,
                        "Nghĩa",
                        "Lý do",
                        List.of(),
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
                        ObjectiveExplanationStrategyRegistry.CURRENT_REGISTRY_VERSION,
                        "TFNG_CONTRADICTION_TABLE",
                        ObjectiveExplanationStrategyRegistry.STRATEGY_VERSION,
                        "Nghĩa",
                        "Lý do",
                        List.of(),
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
                List.of(single, fill, tfng, multiple, matching),
                Map.of(
                        "1001", "{learner-single}",
                        "1002", "{learner-fill}",
                        "1003", "{learner-tfng}",
                        "1004", "{learner-multiple}",
                        "1005", "{learner-matching}"));
        PracticeAttemptResultView overview = mock(PracticeAttemptResultView.class);
        when(overview.payload()).thenReturn(new ObjectiveResultPayload(List.of()));
        when(overview.score()).thenReturn(score());
        when(overview.answers()).thenReturn(answers());
        when(overview.feedback()).thenReturn(feedback());

        ObjectiveDetailPayload detail = (ObjectiveDetailPayload) presenter.presentDetail(
                context, overview, null);

        assertThat(detail.questions()).hasSize(5);
        assertThat(detail.questions().get(0)).isInstanceOf(ObjectiveSingleChoiceDetail.class);
        assertThat(detail.questions().get(1)).isInstanceOf(ObjectiveFillBlankDetail.class);
        assertThat(detail.questions().get(2)).isInstanceOf(ObjectiveTfngDetail.class);
        assertThat(detail.questions().get(3)).isInstanceOf(ObjectiveMultipleAnswerDetail.class);
        assertThat(detail.questions().get(4)).isInstanceOf(ObjectiveMatchingDetail.class);
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
        ObjectiveMultipleAnswerDetail multipleDetail =
                (ObjectiveMultipleAnswerDetail) detail.questions().get(3);
        assertThat(multipleDetail.options())
                .filteredOn(option -> option.state() == ObjectiveOptionState.SELECTED_INCORRECT)
                .extracting(ObjectiveOptionResult::optionId)
                .containsExactly("multi-b");
        assertThat(multipleDetail.options())
                .filteredOn(ObjectiveOptionResult::correct)
                .extracting(ObjectiveOptionResult::optionId)
                .containsExactly("multi-a", "multi-c");
        ObjectiveMatchingDetail matchingDetail =
                (ObjectiveMatchingDetail) detail.questions().get(4);
        assertThat(matchingDetail.matches())
                .extracting(match -> match.correct())
                .containsExactly(false, true);
        assertThat(matchingDetail.matches().get(0).learnerCandidateLabel())
                .isEqualTo("B");
        assertThat(matchingDetail.matches().get(0).officialCandidateLabel())
                .isEqualTo("A");
        assertThat(detail.sourceGroups()).singleElement().satisfies(source ->
                assertThat(source.questionVersionIds())
                        .containsExactly(101L, 102L, 103L, 104L, 105L));
        assertThat(detail.groups()).singleElement().satisfies(group -> {
            assertThat(group.legacyFallback()).isTrue();
            assertThat(group.displayLabel()).isEqualTo(
                    "Nhóm dữ liệu cũ chưa phân nhóm");
            assertThat(group.questions()).hasSize(5);
        });
        assertThat(detail.constructRegistryState()).isEqualTo(
                "DEFERRED_PRE_PHASE_14_REGISTRY");
        assertThat(detail.capabilities())
                .extracting(capability -> capability.code())
                .containsExactlyInAnyOrder(
                        ObjectiveDetailCapabilityCode.MULTIPLE_ANSWER,
                        ObjectiveDetailCapabilityCode.MATCHING,
                        ObjectiveDetailCapabilityCode.PINNED_SHARED_MATERIAL,
                        ObjectiveDetailCapabilityCode.LOCAL_HELPER_DRAWER);
        assertThat(detail.capabilities())
                .allSatisfy(capability -> {
                    assertThat(capability.state()).isEqualTo(
                            ObjectiveDetailCapabilityState.AVAILABLE);
                    assertThat(capability.reasonVi()).isBlank();
                });

        ObjectiveSourceGroup source = detail.sourceGroups().get(0);
        assertThat(source.provenanceLabelVi()).isEqualTo("Nguồn đề đã khóa");
        assertThat(source.provenanceLabelKo()).isEqualTo("잠긴 출제 자료");

        PracticeResultContext unansweredExtendedTypes = context(
                List.of(multiple, matching),
                Map.of());
        ObjectiveDetailPayload unansweredDetail = (ObjectiveDetailPayload)
                presenter.presentDetail(unansweredExtendedTypes, overview, null);
        assertThat(unansweredDetail.questions()).hasSize(2);
        assertThat(((ObjectiveMultipleAnswerDetail) unansweredDetail.questions().get(0))
                .unanswered()).isTrue();
        assertThat(unansweredDetail.questions().get(0).core().scoreState())
                .isEqualTo("NOT_ANSWERED");
        assertThat(unansweredDetail.questions().get(1).core().scoreState())
                .isEqualTo("NOT_ANSWERED");

        assertThatThrownBy(() -> new ObjectiveDetailPayload(
                detail.score(),
                detail.answers(),
                detail.feedback(),
                detail.summary(),
                List.of(detail.groups().get(0), detail.groups().get(0)),
                detail.capabilities(),
                detail.constructRegistryState(),
                detail.constructRegistryNote()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("group navigation must be unique");
    }

    @Test
    void listeningResultPreservesImmutableGroupHierarchyOrderAndOptionStates() {
        AssessmentContractCodec codec = mock(AssessmentContractCodec.class);
        QuestionExplanationReadService explanations =
                mock(QuestionExplanationReadService.class);
        ObjectiveResultPresenter presenter = new ObjectiveResultPresenter(
                codec,
                new QuestionTypeResolver(),
                new AssessmentScoringEngine(),
                explanations,
                new ObjectMapper());

        PracticeQuestionGroupVersion firstGroup = group(
                901L, 801L, 0, "Phần nghe 1",
                "/audio/group-1.mp3", "첫 번째 대본", null);
        PracticeQuestionGroupVersion secondGroup = group(
                902L, 802L, 1, "Phần nghe 2",
                "/audio/group-2.mp3", "두 번째 대본", null);
        PracticeQuestionVersion first = question(
                201L, 2001L, 1, "SINGLE_CHOICE", "첫 번째 질문");
        PracticeQuestionVersion second = question(
                202L, 2002L, 2, "SINGLE_CHOICE", "두 번째 질문");
        PracticeQuestionVersion third = question(
                203L, 2003L, 3, "SINGLE_CHOICE", "세 번째 질문");
        when(first.getGroupVersionId()).thenReturn(901L);
        when(second.getGroupVersionId()).thenReturn(901L);
        when(third.getGroupVersionId()).thenReturn(902L);
        when(first.getDisplayOrder()).thenReturn(0);
        when(second.getDisplayOrder()).thenReturn(1);
        when(third.getDisplayOrder()).thenReturn(2);

        stubSingleChoice(codec, explanations, first, "stable-b");
        stubSingleChoice(codec, explanations, second, "stable-a");
        stubSingleChoice(codec, explanations, third, null);

        PracticeResultContext context = context(
                "LISTENING",
                List.of(secondGroup, firstGroup),
                List.of(third, second, first),
                Map.of(
                        "2001", "{learner-201}",
                        "2002", "{learner-202}"));
        PracticeAttemptResultView overview = mock(PracticeAttemptResultView.class);
        when(overview.payload()).thenReturn(new ObjectiveResultPayload(List.of()));
        when(overview.score()).thenReturn(score());
        when(overview.answers()).thenReturn(answers());
        when(overview.feedback()).thenReturn(feedback());

        ObjectiveDetailPayload detail = (ObjectiveDetailPayload) presenter.presentDetail(
                context, overview, null);

        assertThat(detail.groups()).extracting(group -> group.groupVersionId())
                .containsExactly(901L, 902L);
        assertThat(detail.groups()).extracting(group -> group.groupId())
                .containsExactly(801L, 802L);
        assertThat(detail.groups()).extracting(group -> group.groupOrder())
                .containsExactly(0, 1);
        assertThat(detail.groups().get(0).questions())
                .extracting(question -> question.core().questionVersionId())
                .containsExactly(201L, 202L);
        assertThat(detail.groups().get(0).questions())
                .extracting(question -> question.core().questionOrder())
                .containsExactly(0, 1);
        assertThat(detail.groups()).extracting(group -> group.source().audioUrl())
                .containsExactly("/audio/group-1.mp3", "/audio/group-2.mp3");
        assertThat(detail.groups()).extracting(group -> group.source().transcriptText())
                .containsExactly("첫 번째 대본", "두 번째 대본");
        assertThat(detail.groups()).allSatisfy(group -> {
            assertThat(group.source().questionVersionIds())
                    .containsExactlyElementsOf(group.questions().stream()
                            .map(question -> question.core().questionVersionId())
                            .toList());
            assertThat(group.source().hasAudio()).isTrue();
            assertThat(group.source().hasApprovedTranscript()).isTrue();
        });

        ObjectiveSingleChoiceDetail selectedCorrect =
                (ObjectiveSingleChoiceDetail) detail.groups().get(0).questions().get(0);
        assertThat(selectedCorrect.options())
                .filteredOn(option -> option.learnerSelected())
                .singleElement()
                .satisfies(option -> {
                    assertThat(option.state()).isEqualTo(ObjectiveOptionState.CORRECT);
                    assertThat(option.statusLabelVi()).isEqualTo("Bạn đã chọn · Đúng");
                });

        ObjectiveSingleChoiceDetail selectedWrong =
                (ObjectiveSingleChoiceDetail) detail.groups().get(0).questions().get(1);
        assertThat(selectedWrong.options())
                .filteredOn(option -> option.state()
                        == ObjectiveOptionState.SELECTED_INCORRECT)
                .singleElement()
                .satisfies(option -> assertThat(option.statusLabelVi())
                        .isEqualTo("Bạn đã chọn · Sai"));
        assertThat(selectedWrong.options())
                .filteredOn(option -> option.state() == ObjectiveOptionState.CORRECT)
                .singleElement()
                .satisfies(option -> assertThat(option.statusLabelVi())
                        .isEqualTo("Đáp án đúng"));

        ObjectiveSingleChoiceDetail unanswered =
                (ObjectiveSingleChoiceDetail) detail.groups().get(1).questions().get(0);
        assertThat(unanswered.options())
                .filteredOn(option -> option.state() == ObjectiveOptionState.CORRECT)
                .singleElement();
        assertThat(unanswered.options())
                .filteredOn(option -> option.state()
                        == ObjectiveOptionState.UNSELECTED_INCORRECT)
                .hasSize(1);
        assertThat(unanswered.options()).noneMatch(option -> option.learnerSelected());
        assertThat(unanswered.answered()).isFalse();
        assertThat(unanswered.unanswered()).isTrue();
        assertThat(selectedCorrect.answered()).isTrue();
        assertThat(selectedCorrect.unanswered()).isFalse();
    }

    @Test
    void readingPassageGroupSingletonGroupAndLegacyFallbackRemainDistinct() {
        AssessmentContractCodec codec = mock(AssessmentContractCodec.class);
        QuestionExplanationReadService explanations =
                mock(QuestionExplanationReadService.class);
        ObjectiveResultPresenter presenter = new ObjectiveResultPresenter(
                codec,
                new QuestionTypeResolver(),
                new AssessmentScoringEngine(),
                explanations,
                new ObjectMapper());
        PracticeQuestionGroupVersion passageGroup = group(
                911L, 811L, 0, "Bài đọc 1",
                null, null, "하나의 지문이 여러 문항을 소유합니다.");
        PracticeQuestionVersion first = question(
                301L, 3001L, 1, "SINGLE_CHOICE", "첫 질문");
        PracticeQuestionVersion second = question(
                302L, 3002L, 2, "SINGLE_CHOICE", "둘째 질문");
        when(first.getGroupVersionId()).thenReturn(911L);
        when(second.getGroupVersionId()).thenReturn(911L);
        when(first.getDisplayOrder()).thenReturn(0);
        when(second.getDisplayOrder()).thenReturn(1);
        stubSingleChoice(codec, explanations, first, null);
        stubSingleChoice(codec, explanations, second, null);

        PracticeAttemptResultView overview = mock(PracticeAttemptResultView.class);
        when(overview.payload()).thenReturn(new ObjectiveResultPayload(List.of()));
        when(overview.score()).thenReturn(score());
        when(overview.answers()).thenReturn(answers());
        when(overview.feedback()).thenReturn(feedback());

        ObjectiveDetailPayload grouped = (ObjectiveDetailPayload) presenter.presentDetail(
                context(
                        "READING",
                        List.of(passageGroup),
                        List.of(second, first),
                        Map.of()),
                overview,
                null);
        assertThat(grouped.groups()).singleElement().satisfies(group -> {
            assertThat(group.groupVersionId()).isEqualTo(911L);
            assertThat(group.legacyFallback()).isFalse();
            assertThat(group.source().passageText())
                    .isEqualTo("하나의 지문이 여러 문항을 소유합니다.");
            assertThat(group.questions())
                    .extracting(question -> question.core().questionVersionId())
                    .containsExactly(301L, 302L);
        });

        PracticeQuestionGroupVersion singletonGroup = group(
                912L, 812L, 1, "Câu đọc độc lập",
                null, null, null);
        PracticeQuestionVersion standalone = question(
                303L, 3003L, 3, "SINGLE_CHOICE", "독립 문항");
        when(standalone.getGroupVersionId()).thenReturn(912L);
        when(standalone.getDisplayOrder()).thenReturn(0);
        stubSingleChoice(codec, explanations, standalone, null);
        ObjectiveDetailPayload singleton = (ObjectiveDetailPayload) presenter.presentDetail(
                context("READING", List.of(singletonGroup), List.of(standalone), Map.of()),
                overview,
                null);
        assertThat(singleton.groups()).singleElement().satisfies(group -> {
            assertThat(group.groupVersionId()).isEqualTo(912L);
            assertThat(group.groupId()).isEqualTo(812L);
            assertThat(group.legacyFallback()).isFalse();
            assertThat(group.questions()).singleElement();
            assertThat(group.source().hasPassage()).isFalse();
        });

        PracticeQuestionVersion legacyQuestion = question(
                304L, 3004L, 4, "SINGLE_CHOICE", "이전 데이터 문항");
        when(legacyQuestion.getDisplayOrder()).thenReturn(0);
        stubSingleChoice(codec, explanations, legacyQuestion, null);
        ObjectiveDetailPayload legacy = (ObjectiveDetailPayload) presenter.presentDetail(
                context("READING", List.of(), List.of(legacyQuestion), Map.of()),
                overview,
                null);
        assertThat(legacy.groups()).singleElement().satisfies(group -> {
            assertThat(group.legacyFallback()).isTrue();
            assertThat(group.groupVersionId()).isNull();
            assertThat(group.groupId()).isNull();
            assertThat(group.questions()).singleElement();
        });
    }

    @Test
    void danglingImmutableGroupIdentityFailsClosedInsteadOfDroppingQuestion() {
        AssessmentContractCodec codec = mock(AssessmentContractCodec.class);
        QuestionExplanationReadService explanations =
                mock(QuestionExplanationReadService.class);
        ObjectiveResultPresenter presenter = new ObjectiveResultPresenter(
                codec,
                new QuestionTypeResolver(),
                new AssessmentScoringEngine(),
                explanations,
                new ObjectMapper());
        PracticeQuestionGroupVersion publishedGroup = group(
                911L, 811L, 0, "Bài đọc 1",
                null, null, "본문");
        PracticeQuestionVersion dangling = question(
                913L, 3005L, 2, "SINGLE_CHOICE", "그룹 소유권 오류");
        when(dangling.getGroupVersionId()).thenReturn(999L);
        stubSingleChoice(codec, explanations, dangling, null);

        PracticeAttemptResultView overview = mock(PracticeAttemptResultView.class);
        when(overview.payload()).thenReturn(new ObjectiveResultPayload(List.of()));
        when(overview.score()).thenReturn(score());
        when(overview.answers()).thenReturn(answers());
        when(overview.feedback()).thenReturn(feedback());

        assertThatThrownBy(() -> presenter.presentDetail(
                context(
                        "READING",
                        List.of(publishedGroup),
                        List.of(dangling),
                        Map.of()),
                overview,
                null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("immutable group ownership is missing")
                .hasMessageContaining("913");
    }

    @Test
    void listeningTranscriptIsRedactedUnlessImmutableProvenanceApprovesIt() {
        AssessmentContractCodec codec = mock(AssessmentContractCodec.class);
        QuestionExplanationReadService explanations =
                mock(QuestionExplanationReadService.class);
        ObjectiveResultPresenter presenter = new ObjectiveResultPresenter(
                codec,
                new QuestionTypeResolver(),
                new AssessmentScoringEngine(),
                explanations,
                new ObjectMapper());
        PracticeQuestionGroupVersion unapprovedGroup = group(
                921L, 821L, 0, "Phần nghe riêng tư",
                "/audio/authorized.mp3", "대외비 대본", null);
        when(unapprovedGroup.getStimulusProvenanceJson()).thenReturn(
                "{\"source\":\"PUBLISHED_IMMUTABLE_SNAPSHOT\",\"approved\":false}");
        PracticeQuestionVersion question = question(
                922L, 822L, 1, "SINGLE_CHOICE", "질문");
        when(question.getGroupVersionId()).thenReturn(921L);
        stubSingleChoice(codec, explanations, question, null);

        PracticeAttemptResultView overview = mock(PracticeAttemptResultView.class);
        when(overview.payload()).thenReturn(new ObjectiveResultPayload(List.of()));
        when(overview.score()).thenReturn(score());
        when(overview.answers()).thenReturn(answers());
        when(overview.feedback()).thenReturn(feedback());

        ObjectiveDetailPayload detail = (ObjectiveDetailPayload) presenter.presentDetail(
                context(
                        "LISTENING",
                        List.of(unapprovedGroup),
                        List.of(question),
                        Map.of()),
                overview,
                null);

        assertThat(detail.groups()).singleElement().satisfies(group -> {
            assertThat(group.source().hasAudio()).isTrue();
            assertThat(group.source().hasApprovedTranscript()).isFalse();
            assertThat(group.source().transcriptText()).isEmpty();
            assertThat(group.source().transcriptEvidenceScope())
                    .isEqualTo("Không có bản chép lời được phê duyệt.");
        });
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
        when(question.getGroupVersionId()).thenReturn(null);
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
        return context("READING", List.of(), questions, answers);
    }

    private static PracticeResultContext context(
            String skill,
            List<PracticeQuestionGroupVersion> groups,
            List<PracticeQuestionVersion> questions,
            Map<String, String> answers) {
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getSkill()).thenReturn(skill);
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
                        groups,
                        questions),
                answers,
                score());
    }

    private static PracticeQuestionGroupVersion group(
            Long groupVersionId,
            Long groupId,
            int order,
            String label,
            String audio,
            String transcript,
            String passage) {
        PracticeQuestionGroupVersion group =
                mock(PracticeQuestionGroupVersion.class);
        when(group.getId()).thenReturn(groupVersionId);
        when(group.getGroupId()).thenReturn(groupId);
        when(group.getDisplayOrder()).thenReturn(order);
        when(group.getGroupLabel()).thenReturn(label);
        when(group.getInstruction()).thenReturn("Hướng dẫn nhóm");
        when(group.getAudioUrl()).thenReturn(audio);
        when(group.getTranscriptText()).thenReturn(transcript);
        when(group.getPassageText()).thenReturn(passage);
        when(group.getStimulusProvenanceJson()).thenReturn(
                "{\"source\":\"PUBLISHED_IMMUTABLE_SNAPSHOT\",\"approved\":true}");
        return group;
    }

    private static void stubSingleChoice(
            AssessmentContractCodec codec,
            QuestionExplanationReadService explanations,
            PracticeQuestionVersion question,
            String learnerOptionId) {
        QuestionContent content = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(
                        new QuestionContent.Option("stable-a", "첫째"),
                        new QuestionContent.Option("stable-b", "둘째")),
                List.of());
        AnswerSpec spec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.SINGLE_CHOICE,
                List.of("stable-b"),
                null,
                List.of(),
                ScoringPolicyCode.ALL_OR_NOTHING);
        when(codec.readQuestionContent(
                "content-" + question.getId(),
                CanonicalQuestionType.SINGLE_CHOICE)).thenReturn(content);
        when(codec.readAnswerSpec(
                "answer-" + question.getId(), content)).thenReturn(spec);
        if (learnerOptionId != null) {
            when(codec.readLearnerAnswer(
                    "{learner-" + question.getId() + "}"))
                    .thenReturn(new LearnerAnswer(
                            LearnerAnswer.SCHEMA_VERSION,
                            CanonicalQuestionType.SINGLE_CHOICE,
                            List.of(learnerOptionId),
                            null,
                            Map.of(),
                            null));
        } else {
            when(codec.adaptLegacyLearnerAnswer(
                    "SINGLE_CHOICE", "", content))
                    .thenReturn(new LearnerAnswer(
                            LearnerAnswer.SCHEMA_VERSION,
                            CanonicalQuestionType.SINGLE_CHOICE,
                            List.of(),
                            null,
                            Map.of(),
                            null));
        }
        when(explanations.readObjective(
                question.getId(), CanonicalQuestionType.SINGLE_CHOICE))
                .thenReturn(Optional.empty());
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
