package com.ksh.features.practice.result;

import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeSectionVersion;
import com.ksh.entities.PracticeSetVersion;
import com.ksh.entities.PracticeTestVersion;
import com.ksh.features.practice.ai.speaking.SpeakingRubricCriterion;
import com.ksh.features.practice.ai.writing.WritingRubricCriterion;
import com.ksh.features.practice.ai.writing.WritingScoringPolicy;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveDetailPayload;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveResultPayload;
import com.ksh.features.practice.dto.PracticeDtos.PracticeAttemptResultView;
import com.ksh.features.practice.dto.PracticeDtos.PracticeResultDetailView;
import com.ksh.features.practice.dto.PracticeDtos.ResultAnswerDistribution;
import com.ksh.features.practice.dto.PracticeDtos.ResultAttemptIdentity;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailDiagnosticFinding;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailFilterChip;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailPolarity;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailScoreCriterion;
import com.ksh.features.practice.dto.PracticeDtos.ResultFeedbackAvailability;
import com.ksh.features.practice.dto.PracticeDtos.ResultScoreSummary;
import com.ksh.features.practice.dto.PracticeDtos.ResultState;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingDetailPayload;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingAnswerArtifact;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingDiagnosticGroup;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingEvidenceView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingResultPayload;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingTaskDetail;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingUpgradeView;
import com.ksh.features.practice.dto.PracticeDtos.WritingDetailPayload;
import com.ksh.features.practice.dto.PracticeDtos.WritingAnswerArtifact;
import com.ksh.features.practice.dto.PracticeDtos.WritingDiagnosticChip;
import com.ksh.features.practice.dto.PracticeDtos.WritingDiagnosticFinding;
import com.ksh.features.practice.dto.PracticeDtos.WritingDiagnosticTarget;
import com.ksh.features.practice.dto.PracticeDtos.WritingDiagnosticTargetKind;
import com.ksh.features.practice.dto.PracticeDtos.WritingResultPayload;
import com.ksh.features.practice.service.PracticeVersionSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PracticeResultDetailContractTest {

    @Test
    void assemblerRequiresExactlyOneDetailPresenterAndReturnsItsTypedKind() {
        PracticeResultAssembler overviewAssembler = mock(PracticeResultAssembler.class);
        PracticeResultContext context = context("WRITING");
        PracticeAttemptResultView overview = overview("WRITING");
        WritingDetailPayload payload = new WritingDetailPayload(
                feedback(),
                List.of(),
                null,
                List.of(),
                WritingScoringPolicy.PROFILE_ID,
                WritingDiagnosticDescriptorRegistry.SEAM_ID,
                WritingDiagnosticDescriptorRegistry.SEAM_STATE,
                WritingDiagnosticDescriptorRegistry.SCOPE_NOTE_VI,
                WritingDiagnosticDescriptorRegistry.SCOPE_NOTE_KO,
                "NO_DETAIL_TASK",
                "Không có nhiệm vụ Viết phù hợp để hiển thị chi tiết.",
                "상세 결과를 표시할 수 있는 쓰기 과제가 없습니다.",
                List.of(),
                null);
        PracticeResultDetailPresenter presenter = mock(PracticeResultDetailPresenter.class);
        when(overviewAssembler.loadContext(11L, 22L)).thenReturn(context);
        when(overviewAssembler.assemble(context)).thenReturn(overview);
        when(presenter.supports("WRITING")).thenReturn(true);
        when(presenter.presentDetail(context, overview, null)).thenReturn(payload);

        PracticeResultDetailAssembler assembler = new PracticeResultDetailAssembler(
                overviewAssembler, List.of(presenter));
        PracticeResultDetailView detail = assembler.assemble(11L, 22L, null);

        assertThat(detail.screenKind().name()).isEqualTo("WRITING_DETAIL");
        assertThat(detail.payload()).isSameAs(payload);
        assertThat(detail.schemaVersion()).isEqualTo("practice-result-detail-v1");

        PracticeResultDetailAssembler missing = new PracticeResultDetailAssembler(
                overviewAssembler, List.of());
        assertThatThrownBy(() -> missing.assemble(11L, 22L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đúng một Result Detail presenter");

        PracticeResultDetailPresenter duplicate = mock(PracticeResultDetailPresenter.class);
        when(duplicate.supports("WRITING")).thenReturn(true);
        PracticeResultDetailAssembler ambiguous = new PracticeResultDetailAssembler(
                overviewAssembler, List.of(presenter, duplicate));
        assertThatThrownBy(() -> ambiguous.assemble(11L, 22L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đúng một Result Detail presenter");
    }

    @Test
    void questionIdIsRejectedOutsideWritingBeforeDetailPresentation() {
        PracticeResultAssembler overviewAssembler = mock(PracticeResultAssembler.class);
        PracticeResultContext context = context("READING");
        PracticeAttemptResultView overview = overview("READING");
        when(overviewAssembler.loadContext(11L, 22L)).thenReturn(context);
        when(overviewAssembler.assemble(context)).thenReturn(overview);

        PracticeResultDetailAssembler assembler = new PracticeResultDetailAssembler(
                overviewAssembler, List.of());

        assertThatThrownBy(() -> assembler.assemble(11L, 22L, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("questionId");
    }

    @Test
    void questionIdIsForwardedForSpeakingDetailSelection() {
        PracticeResultAssembler overviewAssembler = mock(PracticeResultAssembler.class);
        PracticeResultContext context = context("SPEAKING");
        PracticeAttemptResultView overview = overview("SPEAKING");
        SpeakingDetailPayload payload = speakingDetailPayload(
                "TRANSCRIPT_ONLY",
                speakingCriteria("TRANSCRIPT_ONLY"),
                List.of(),
                List.of());
        PracticeResultDetailPresenter presenter = mock(PracticeResultDetailPresenter.class);
        when(overviewAssembler.loadContext(11L, 22L)).thenReturn(context);
        when(overviewAssembler.assemble(context)).thenReturn(overview);
        when(presenter.supports("SPEAKING")).thenReturn(true);
        when(presenter.presentDetail(context, overview, 77L)).thenReturn(payload);

        PracticeResultDetailAssembler assembler = new PracticeResultDetailAssembler(
                overviewAssembler, List.of(presenter));

        assertThat(assembler.assemble(11L, 22L, 77L).payload()).isSameAs(payload);
    }

    @Test
    void envelopeRejectsPayloadThatDoesNotMatchImmutableSkill() {
        ObjectiveDetailPayload objective = new ObjectiveDetailPayload(
                score(),
                answers(),
                feedback(),
                new ObjectiveResultPayload(List.of()),
                List.of(),
                List.of(),
                "DEFERRED_PRE_PHASE_14_REGISTRY",
                "Chưa có registry construct đã duyệt.");

        assertThatThrownBy(() -> new PracticeResultDetailView(
                identity("WRITING"), new ResultState("GRADED", "Đã chấm"), objective))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match immutable attempt skill");
    }

    @Test
    void writingDescriptorsRequireClozeBlankBindingAndKeepEssayParentAuthority() {
        for (String taskType : List.of("Q51", "Q52", "Q51_52")) {
            assertThat(ResultDetailDescriptorRegistry.writing(
                    WritingRubricCriterion.W_CLOZE_CONTEXT_FIT,
                    taskType,
                    ResultDetailPolarity.STRENGTH)).isNull();
            assertThat(ResultDetailDescriptorRegistry.writing(
                    WritingRubricCriterion.W_GRAMMAR_ERRORS,
                    taskType,
                    ResultDetailPolarity.NEEDS_IMPROVEMENT)).isNull();
        }

        ResultDetailDescriptorRegistry.Definition q53 =
                ResultDetailDescriptorRegistry.writing(
                        WritingRubricCriterion.W_GRAMMAR_ERRORS,
                        "Q53",
                        ResultDetailPolarity.NEEDS_IMPROVEMENT);
        assertThat(q53).isNotNull();
        assertThat(q53.parentCriterionId()).isEqualTo("W_LANGUAGE_EXPRESSION");
        assertThat(q53.applicability()).isEqualTo("WRITING_Q53");
    }

    @Test
    void writingTypedDiagnosticAndArtifactCodesFailClosed() {
        assertThatThrownBy(() -> new WritingDiagnosticChip(
                "chip",
                "Nhãn",
                "라벨",
                ResultDetailPolarity.STRENGTH,
                null,
                "UNKNOWN_EFFECT",
                "WRITING_Q53",
                1,
                1,
                false,
                "WHOLE_ANSWER_AVAILABLE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score effect");

        assertThatThrownBy(() -> new WritingDiagnosticFinding(
                153L,
                "finding",
                "MORPHOSYNTAX",
                "Hình thái & cú pháp",
                "형태·통사",
                3,
                "W_GRAMMAR_ERRORS",
                "Lỗi ngữ pháp",
                "문법 오류",
                3001,
                ResultDetailPolarity.NEEDS_IMPROVEMENT,
                "W_LANGUAGE_EXPRESSION",
                "PARENT_LINKED",
                "WRITING_Q53",
                new WritingDiagnosticTarget(
                        WritingDiagnosticTargetKind.WHOLE_ANSWER, null, null),
                "EXACT_TEXT_AVAILABLE",
                "PROVIDER_GUESSED_SCOPE",
                "문법",
                "Giải thích",
                "교정",
                null,
                null,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incomplete");

        assertThatThrownBy(() -> new WritingAnswerArtifact(
                "평가 답안",
                "AVAILABLE",
                "PROVIDER_LABELLED_TEACHER",
                "Bài tham khảo",
                "참고 답안"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provenance");

        WritingAnswerArtifact evaluator = new WritingAnswerArtifact(
                "평가 답안",
                "AVAILABLE",
                "EVALUATOR_GENERATED_NOT_TEACHER_REFERENCE",
                "Bài tham khảo do bộ đánh giá tạo",
                "평가기가 생성한 참고 답안");
        assertThat(evaluator.available()).isTrue();
        assertThat(evaluator.provenance())
                .isEqualTo("EVALUATOR_GENERATED_NOT_TEACHER_REFERENCE");
    }

    @Test
    void transcriptSpeakingBindsSixCanonicalRowsDiagnosticsAndChipsToSelectedQuestion() {
        List<ResultDetailScoreCriterion> criteria = speakingCriteria("TRANSCRIPT_ONLY");
        ResultDetailDescriptorRegistry.Definition particles =
                ResultDetailDescriptorRegistry.speaking(
                        SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                        "S_GRAMMAR_PARTICLES",
                        ResultDetailPolarity.NEEDS_IMPROVEMENT);
        ResultDetailDescriptorRegistry.Definition endings =
                ResultDetailDescriptorRegistry.speaking(
                        SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                        "S_GRAMMAR_ENDINGS",
                        ResultDetailPolarity.NEEDS_IMPROVEMENT);

        assertThat(particles.id()).isNotEqualTo(endings.id());
        assertThat(particles.labelVi()).isEqualTo("Tiểu từ");
        assertThat(particles.labelKo()).isEqualTo("조사");
        assertThat(endings.labelVi()).isEqualTo("Đuôi câu và vĩ tố");
        assertThat(endings.labelKo()).isEqualTo("문장 종결형과 어미");
        assertThat(particles.parentCriterionId())
                .isEqualTo("S_GRAMMAR_SENTENCE_CONTROL");
        assertThat(endings.parentCriterionId())
                .isEqualTo("S_GRAMMAR_SENTENCE_CONTROL");
        assertThat(particles.labelVi()).isNotEqualTo(
                ResultDetailDescriptorRegistry.scoreLabelVi(
                        "S_GRAMMAR_SENTENCE_CONTROL"));
        assertThat(particles.labelKo()).isNotEqualTo(
                ResultDetailDescriptorRegistry.scoreLabelKo(
                        "S_GRAMMAR_SENTENCE_CONTROL"));
        assertThat(particles.stableOrder()).isLessThan(endings.stableOrder());
        assertThat(ResultDetailDescriptorRegistry.speaking(
                SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                "S_UNKNOWN",
                ResultDetailPolarity.NEEDS_IMPROVEMENT)).isNull();
        assertThat(ResultDetailDescriptorRegistry.speaking(
                SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                " ",
                ResultDetailPolarity.NEEDS_IMPROVEMENT)).isNull();
        assertThat(ResultDetailDescriptorRegistry.speaking(
                SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT,
                "S_GRAMMAR_PARTICLES",
                ResultDetailPolarity.NEEDS_IMPROVEMENT)).isNull();
        assertThat(ResultDetailDescriptorRegistry.speaking(
                SpeakingRubricCriterion.FLUENCY,
                "S_GRAMMAR_PARTICLES",
                ResultDetailPolarity.NEEDS_IMPROVEMENT)).isNull();

        ResultDetailDiagnosticFinding selectedFinding = diagnostic(
                77L, particles, ResultDetailPolarity.NEEDS_IMPROVEMENT);
        ResultDetailFilterChip particlesChip = new ResultDetailFilterChip(
                particles.id(),
                particles.labelVi(),
                particles.labelKo(),
                ResultDetailPolarity.NEEDS_IMPROVEMENT,
                particles.parentCriterionId(),
                particles.applicability(),
                particles.stableOrder(),
                1,
                false,
                "EXACT_TEXT_AVAILABLE");
        SpeakingDetailPayload selected = speakingDetailPayload(
                "TRANSCRIPT_ONLY",
                criteria,
                List.of(selectedFinding),
                List.of(particlesChip));
        assertThat(selected.activeQuestionId()).isEqualTo(77L);
        assertThat(selected.scoreCriteria()).allSatisfy(criterion ->
                assertThat(criterion.questionId()).isEqualTo(77L));
        assertThat(selected.diagnosticFindings()).containsExactly(selectedFinding);

        ResultDetailDiagnosticFinding foreignFinding = diagnostic(
                78L, particles, ResultDetailPolarity.NEEDS_IMPROVEMENT);
        assertThatThrownBy(() -> speakingDetailPayload(
                "TRANSCRIPT_ONLY",
                criteria,
                List.of(foreignFinding),
                List.of(particlesChip)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selected immutable task");

        ResultDetailFilterChip acoustic = new ResultDetailFilterChip(
                "D_S_FLUENCY_NEEDS_IMPROVEMENT",
                "Độ lưu loát",
                "유창성",
                ResultDetailPolarity.NEEDS_IMPROVEMENT,
                "S_FLUENCY",
                "SPEAKING_TRANSCRIPT_ONLY",
                9,
                1,
                false,
                "AVAILABLE");
        ResultDetailDiagnosticFinding acousticFinding =
                new ResultDetailDiagnosticFinding(
                        77L,
                        "finding-acoustic",
                        acoustic.id(),
                        ResultDetailPolarity.NEEDS_IMPROVEMENT,
                        "S_FLUENCY",
                        "SPEAKING_TRANSCRIPT_ONLY",
                        "TASK_EVIDENCE_AVAILABLE",
                        "TASK_METADATA",
                        "",
                        "Không được dùng phát hiện này trong hồ sơ chỉ có bản chép lời.",
                        "");
        assertThatThrownBy(() -> speakingDetailPayload(
                "TRANSCRIPT_ONLY",
                criteria,
                List.of(acousticFinding),
                List.of(acoustic)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("acoustic diagnostic chips");
    }

    @Test
    void speakingDescriptorRegistryCoversTheBoundedSixteenKshSubcriteria() {
        Map<SpeakingRubricCriterion, List<String>> boundedSubcriteria = Map.of(
                SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT, List.of(
                        "S_CONTENT_RELEVANCE",
                        "S_CONTENT_PROMPT_COVERAGE",
                        "S_CONTENT_SPECIFICITY_EXAMPLES"),
                SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL, List.of(
                        "S_GRAMMAR_PARTICLES",
                        "S_GRAMMAR_TENSE_ASPECT",
                        "S_GRAMMAR_ENDINGS",
                        "S_GRAMMAR_SENTENCE_STRUCTURE",
                        "S_GRAMMAR_HONORIFIC_REGISTER",
                        "S_GRAMMAR_CONNECTORS"),
                SpeakingRubricCriterion.VOCABULARY_EXPRESSIONS, List.of(
                        "S_VOCAB_TOPIC_WORDS",
                        "S_VOCAB_NATURAL_EXPRESSIONS",
                        "S_VOCAB_REPETITION_CONTROL",
                        "S_VOCAB_WORD_CHOICE"),
                SpeakingRubricCriterion.COHERENCE_ORGANIZATION, List.of(
                        "S_COHERENCE_ORGANIZATION",
                        "S_COHERENCE_LOGICAL_FLOW",
                        "S_COHERENCE_DISCOURSE_MARKERS"));

        assertThat(boundedSubcriteria.values().stream().mapToInt(List::size).sum())
                .isEqualTo(16);
        boundedSubcriteria.forEach((parent, subcriteria) -> {
            int previousOrder = 0;
            for (String subcriterionId : subcriteria) {
                ResultDetailDescriptorRegistry.Definition descriptor =
                        ResultDetailDescriptorRegistry.speaking(
                                parent, subcriterionId, ResultDetailPolarity.STRENGTH);
                assertThat(descriptor).isNotNull();
                assertThat(descriptor.id()).contains(subcriterionId);
                assertThat(descriptor.parentCriterionId()).isEqualTo(parent.id());
                assertThat(descriptor.labelVi()).isNotBlank()
                        .isNotEqualTo(ResultDetailDescriptorRegistry.scoreLabelVi(parent.id()));
                assertThat(descriptor.labelKo()).isNotBlank()
                        .isNotEqualTo(ResultDetailDescriptorRegistry.scoreLabelKo(parent.id()));
                assertThat(descriptor.stableOrder()).isGreaterThan(previousOrder);
                previousOrder = descriptor.stableOrder();
            }
        });
        assertThat(ResultDetailDescriptorRegistry
                .speakingFamily("S_CONTENT_RELEVANCE").code())
                .isEqualTo("TASK_RESPONSE_RELEVANCE");
        assertThat(ResultDetailDescriptorRegistry
                .speakingFamily("S_COHERENCE_LOGICAL_FLOW").code())
                .isEqualTo("DISCOURSE_ORGANIZATION");
        assertThat(ResultDetailDescriptorRegistry
                .speakingFamily("S_GRAMMAR_PARTICLES").code())
                .isEqualTo("MORPHOSYNTAX");
        assertThat(ResultDetailDescriptorRegistry
                .speakingFamily("S_VOCAB_NATURAL_EXPRESSIONS").code())
                .isEqualTo("LEXICON_COLLOCATION");
        assertThat(ResultDetailDescriptorRegistry
                .speakingFamily("S_GRAMMAR_HONORIFIC_REGISTER").code())
                .isEqualTo("SOCIOLINGUISTIC_REGISTER_PRAGMATICS");
        assertThat(ResultDetailDescriptorRegistry.speakingFamily("S_UNKNOWN")).isNull();
    }

    @Test
    void acousticGuardDoesNotCloseASeparateFutureDirectAudioContract() {
        ResultDetailFilterChip futureAcoustic = new ResultDetailFilterChip(
                "D_S_FLUENCY_FUTURE",
                "Độ lưu loát",
                "유창성",
                ResultDetailPolarity.NEEDS_IMPROVEMENT,
                "S_FLUENCY",
                "SPEAKING_DIRECT_AUDIO",
                9,
                1,
                false,
                "DIRECT_AUDIO_AVAILABLE");

        SpeakingDetailPayload future = speakingDetailPayload(
                "DIRECT_AUDIO_AND_TRANSCRIPT",
                speakingCriteria("DIRECT_AUDIO_AND_TRANSCRIPT"),
                List.of(new ResultDetailDiagnosticFinding(
                        77L,
                        "finding-future-acoustic",
                        futureAcoustic.id(),
                        ResultDetailPolarity.NEEDS_IMPROVEMENT,
                        "S_FLUENCY",
                        "SPEAKING_DIRECT_AUDIO",
                        "DIRECT_AUDIO_AVAILABLE",
                        "TASK_METADATA",
                        "",
                        "Future governed direct-audio finding.",
                        "")),
                List.of(futureAcoustic));

        assertThat(future.filterChips()).containsExactly(futureAcoustic);
    }

    private static SpeakingDetailPayload speakingDetailPayload(
            String evidenceMode,
            List<ResultDetailScoreCriterion> criteria,
            List<ResultDetailDiagnosticFinding> findings,
            List<ResultDetailFilterChip> chips
    ) {
        SpeakingDiagnosticGroup group = findings.isEmpty() && chips.isEmpty()
                ? null
                : new SpeakingDiagnosticGroup(
                chips.stream().anyMatch(chip -> "S_FLUENCY".equals(
                        chip.parentCriterionId()))
                        ? "FLUENCY_RHYTHM"
                        : "MORPHOSYNTAX",
                chips.stream().anyMatch(chip -> "S_FLUENCY".equals(
                        chip.parentCriterionId()))
                        ? "Độ lưu loát và nhịp điệu"
                        : "Hình thái, cú pháp và kiểm soát câu",
                chips.stream().anyMatch(chip -> "S_FLUENCY".equals(
                        chip.parentCriterionId()))
                        ? "유창성과 리듬"
                        : "형태·통사와 문장 통제",
                chips.stream().anyMatch(chip -> "S_FLUENCY".equals(
                        chip.parentCriterionId())) ? 6 : 3,
                findings.stream().filter(finding ->
                        finding.polarity() == ResultDetailPolarity.STRENGTH).toList(),
                findings.stream().filter(finding ->
                        finding.polarity()
                                == ResultDetailPolarity.NEEDS_IMPROVEMENT).toList(),
                chips.stream().filter(chip ->
                        chip.polarity() == ResultDetailPolarity.STRENGTH).toList(),
                chips.stream().filter(chip ->
                        chip.polarity()
                                == ResultDetailPolarity.NEEDS_IMPROVEMENT).toList());
        return new SpeakingDetailPayload(
                feedback(),
                List.of(new SpeakingTaskDetail(
                        77L,
                        1077L,
                        1,
                        "SPEAKING",
                        "CANONICAL_SPEAKING",
                        "한국어로 답하세요.",
                        "",
                        "AUDIO_SOURCE_WITH_AUTHORITATIVE_TRANSCRIPT",
                        "READY",
                        "Tóm tắt")),
                77L,
                "KSH_TRANSCRIPT_GROUNDED_LANGUAGE_CRITERIA_V1",
                "READY",
                evidenceMode,
                "TRANSCRIPT_ONLY".equals(evidenceMode)
                        ? "TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION"
                        : "DIRECT_AUDIO_GOVERNED",
                "Phạm vi bằng chứng được khóa.",
                "LANGUAGE_CRITERIA_AVAILABLE_NO_TASK_TOTAL",
                criteria,
                new SpeakingEvidenceView(
                        77L,
                        "증거",
                        "AVAILABLE",
                        "CURRENT_AUTHORITATIVE_TRANSCRIPT",
                        "UNVERIFIED",
                        "UNAVAILABLE",
                        null,
                        null,
                        null,
                        "",
                        "",
                        false,
                        "TRANSCRIPT_ONLY".equals(evidenceMode)
                                ? "NOT_SCORABLE"
                                : "AVAILABLE_GOVERNED_DIRECT_AUDIO"),
                findings.isEmpty() ? "NO_VALIDATED_EVIDENCE" : "AVAILABLE",
                "Phạm vi chẩn đoán Nói có giới hạn.",
                "말하기 진단 범위는 제한됩니다.",
                "Chỉ dùng bằng chứng chính xác.",
                "정확한 근거만 사용합니다.",
                group == null ? List.of() : List.of(group),
                new SpeakingUpgradeView(
                        77L,
                        new SpeakingAnswerArtifact(
                                "",
                                "UNAVAILABLE",
                                "LEARNER_TRANSCRIPT_DERIVED_EVALUATOR_OUTPUT",
                                "Bài nói nâng cấp",
                                "개선 말하기"),
                        List.of(),
                        new SpeakingAnswerArtifact(
                                "",
                                "UNAVAILABLE",
                                "EVALUATOR_GENERATED_NOT_TEACHER_REFERENCE",
                                "Câu trả lời mẫu do bộ đánh giá tạo",
                                "평가기가 생성한 예시 답변")));
    }

    private static ResultDetailDiagnosticFinding diagnostic(
            Long questionId,
            ResultDetailDescriptorRegistry.Definition descriptor,
            ResultDetailPolarity polarity
    ) {
        return new ResultDetailDiagnosticFinding(
                questionId,
                "finding-1",
                descriptor.id(),
                polarity,
                descriptor.parentCriterionId(),
                descriptor.applicability(),
                "EXACT_TEXT_AVAILABLE",
                "TEXT_SPAN",
                "증거",
                "Giải thích",
                "교정");
    }

    private static List<ResultDetailScoreCriterion> speakingCriteria(String evidenceMode) {
        return List.of(
                speakingCriterion("S_CONTENT_TASK_FULFILLMENT", 1, "16", "20", "SCORED"),
                speakingCriterion("S_GRAMMAR_SENTENCE_CONTROL", 2, "16", "20", "SCORED"),
                speakingCriterion("S_VOCABULARY_EXPRESSIONS", 3, "12", "15", "SCORED"),
                speakingCriterion("S_COHERENCE_ORGANIZATION", 4, "12", "15", "SCORED"),
                speakingCriterion("S_FLUENCY", 5,
                        "TRANSCRIPT_ONLY".equals(evidenceMode) ? null : "12",
                        "TRANSCRIPT_ONLY".equals(evidenceMode) ? null : "15",
                        "TRANSCRIPT_ONLY".equals(evidenceMode) ? "NOT_SCORABLE" : "SCORED"),
                speakingCriterion("S_PRONUNCIATION_DELIVERY", 6,
                        "TRANSCRIPT_ONLY".equals(evidenceMode) ? null : "12",
                        "TRANSCRIPT_ONLY".equals(evidenceMode) ? null : "15",
                        "TRANSCRIPT_ONLY".equals(evidenceMode) ? "NOT_SCORABLE" : "SCORED"));
    }

    private static ResultDetailScoreCriterion speakingCriterion(
            String id,
            int order,
            String score,
            String max,
            String availability
    ) {
        return new ResultDetailScoreCriterion(
                77L,
                id,
                ResultDetailDescriptorRegistry.scoreLabelVi(id),
                ResultDetailDescriptorRegistry.scoreLabelKo(id),
                score == null ? null : new BigDecimal(score),
                max == null ? null : new BigDecimal(max),
                availability,
                order);
    }

    private static PracticeAttemptResultView overview(String skill) {
        return new PracticeAttemptResultView(
                identity(skill),
                new ResultState("GRADED", "Đã chấm"),
                score(),
                answers(),
                feedback(),
                null,
                null,
                null,
                switch (skill) {
                    case "READING", "LISTENING" -> new ObjectiveResultPayload(List.of());
                    case "WRITING" -> new WritingResultPayload(List.of());
                    case "SPEAKING" -> new SpeakingResultPayload(
                            score().unavailableView(), 0, 0, "UNAVAILABLE", "UNKNOWN",
                            "not available", List.of(), List.of(), List.of(), List.of(),
                            List.of(), "LEGACY_UNKNOWN", null, "LEGACY_UNVERIFIED",
                            false, 0);
                    default -> throw new IllegalArgumentException(skill);
                });
    }

    private static ResultAttemptIdentity identity(String skill) {
        return new ResultAttemptIdentity(
                11L, 1L, 2L, 3L, 4L, 5L, "Bộ đề", 6L, "Test",
                7L, "Phần thi", skill, skill);
    }

    private static ResultScoreSummary score() {
        return new ResultScoreSummary(
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.valueOf(100), "EARNED_POINTS", "Điểm đạt được", null);
    }

    private static ResultAnswerDistribution answers() {
        return new ResultAnswerDistribution(1, 0, 0, 0, 0, 0, 1, 1);
    }

    private static ResultFeedbackAvailability feedback() {
        return new ResultFeedbackAvailability("READY", "Đã sẵn sàng", 1, 1);
    }

    private static PracticeResultContext context(String skill) {
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        when(attempt.getSkill()).thenReturn(skill);
        return new PracticeResultContext(
                attempt,
                new PracticeVersionSnapshot(
                        mock(PracticePublishedVersion.class),
                        mock(PracticeSetVersion.class),
                        mock(PracticeTestVersion.class),
                        mock(PracticeSectionVersion.class),
                        List.of(),
                        List.of()),
                Map.of(),
                score());
    }
}
