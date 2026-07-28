package com.ksh.features.practice.result;

import com.ksh.features.practice.ai.writing.WritingRubricCriterion;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailPolarity;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WritingDiagnosticDescriptorRegistryTest {

    @Test
    void exposesSevenStableLocalizedKshCategoriesAndFutureFamilyReservations() {
        List<WritingDiagnosticDescriptorRegistry.CategoryDescriptor> categories =
                WritingDiagnosticDescriptorRegistry.categories();

        assertThat(categories)
                .extracting(WritingDiagnosticDescriptorRegistry.CategoryDescriptor::code)
                .containsExactly(
                        "TASK_CONTENT",
                        "DISCOURSE",
                        "MORPHOSYNTAX",
                        "LEXICO_SEMANTIC",
                        "SOCIOLINGUISTIC_PRAGMATIC",
                        "ORTHOGRAPHY",
                        "LENGTH_FORMAT");
        assertThat(categories)
                .extracting(WritingDiagnosticDescriptorRegistry.CategoryDescriptor::labelVi)
                .containsExactly(
                        "Yêu cầu & nội dung",
                        "Tổ chức & liên kết diễn ngôn",
                        "Hình thái & cú pháp",
                        "Từ vựng & kết hợp từ",
                        "Văn phong & dụng học",
                        "Chính tả & cách chữ",
                        "Dung lượng & định dạng");
        assertThat(categories)
                .extracting(WritingDiagnosticDescriptorRegistry.CategoryDescriptor::labelKo)
                .containsExactly(
                        "과제·내용",
                        "담화 구성·응집성",
                        "형태·통사",
                        "어휘·연어",
                        "문체·화용",
                        "맞춤법·띄어쓰기",
                        "분량·형식");
        assertThat(categories)
                .extracting(WritingDiagnosticDescriptorRegistry.CategoryDescriptor::stableOrder)
                .containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(categories.stream()
                .flatMap(category -> category.futureTypedFamilies().stream()))
                .contains(
                        "PARTICLE_CASE_TOPIC",
                        "ENDING_CONJUGATION_SPEECH_LEVEL",
                        "TENSE_ASPECT_MODALITY_NEGATION",
                        "PREDICATE_VALENCY_AGREEMENT",
                        "CLAUSE_QUOTATION_NOMINALIZATION",
                        "PASSIVE_CAUSATIVE",
                        "WORD_ORDER",
                        "CONNECTIVES",
                        "REFERENCE_ELLIPSIS",
                        "SPELLING",
                        "SPACING",
                        "PUNCTUATION",
                        "COLLOCATION",
                        "SINO_KOREAN_VOCABULARY",
                        "DOMAIN_VOCABULARY",
                        "NATURALNESS",
                        "REPETITION",
                        "GENRE_REGISTER",
                        "HONORIFIC",
                        "PRAGMATICS",
                        "LENGTH",
                        "FORMAT");
    }

    @Test
    void registersOnlyUniqueActiveCurrentCriterionIdsWithBackendLabels() {
        List<WritingDiagnosticDescriptorRegistry.FeatureDescriptor> features =
                WritingDiagnosticDescriptorRegistry.features();

        assertThat(new HashSet<>(features.stream()
                .map(WritingDiagnosticDescriptorRegistry.FeatureDescriptor::code)
                .toList())).hasSameSizeAs(features);
        assertThat(features)
                .extracting(WritingDiagnosticDescriptorRegistry.FeatureDescriptor::code)
                .contains("W_GRAMMAR_ERRORS")
                .doesNotContain(
                        "W_SENTENCE_VARIETY",
                        "W_REGISTER_HONORIFIC_ACCURACY",
                        "W_APPROPRIATE_VOCABULARY_USAGE",
                        "W_WON_GO_JI");
        WritingDiagnosticDescriptorRegistry.FeatureDescriptor grammar = features.stream()
                .filter(feature -> feature.code().equals("W_GRAMMAR_ERRORS"))
                .findFirst()
                .orElseThrow();
        assertThat(grammar.category().code()).isEqualTo("MORPHOSYNTAX");
        assertThat(grammar.labelVi()).isEqualTo("Lỗi ngữ pháp");
        assertThat(grammar.labelKo()).isEqualTo("문법 오류");
        assertThat(grammar.labelVi())
                .doesNotContain("thì", "hô ứng", "bị động", "sai khiến");
    }

    @Test
    void longFormParentsAreTaskNativeAndLengthRemainsDiagnosticOnly() {
        WritingDiagnosticDescriptorRegistry.Resolution content = resolve(
                WritingRubricCriterion.W_TASK_REQUIREMENT_COVERAGE,
                "Q53",
                ResultDetailPolarity.STRENGTH);
        WritingDiagnosticDescriptorRegistry.Resolution discourse = resolve(
                WritingRubricCriterion.W_LOGICAL_FLOW_ISSUES,
                "Q53",
                ResultDetailPolarity.NEEDS_IMPROVEMENT);
        WritingDiagnosticDescriptorRegistry.Resolution grammar = resolve(
                WritingRubricCriterion.W_GRAMMAR_ERRORS,
                "Q53",
                ResultDetailPolarity.NEEDS_IMPROVEMENT);
        WritingDiagnosticDescriptorRegistry.Resolution length = resolve(
                WritingRubricCriterion.W_LENGTH_REQUIREMENT_MET,
                "Q53",
                ResultDetailPolarity.STRENGTH);

        assertThat(content.parentCriterionId())
                .isEqualTo("W_CONTENT_TASK_ACHIEVEMENT");
        assertThat(discourse.parentCriterionId())
                .isEqualTo("W_ORGANIZATION_COHERENCE");
        assertThat(grammar.parentCriterionId())
                .isEqualTo("W_LANGUAGE_EXPRESSION");
        assertThat(length.parentCriterionId()).isNull();
        assertThat(length.scoreEffect()).isEqualTo("DIAGNOSTIC_ONLY");
        assertThat(content.target().kind().name()).isEqualTo("WHOLE_ANSWER");
    }

    @Test
    void clozeRequiresAuthoritativeBlankAndKeepsQ51Q52Identity() {
        assertThat(WritingDiagnosticDescriptorRegistry.resolve(
                WritingRubricCriterion.W_CLOZE_CONTEXT_FIT,
                "Q51",
                ResultDetailPolarity.STRENGTH,
                null)).isNull();

        WritingDiagnosticDescriptorRegistry.Resolution q51Context =
                WritingDiagnosticDescriptorRegistry.resolve(
                        WritingRubricCriterion.W_CLOZE_CONTEXT_FIT,
                        "Q51",
                        ResultDetailPolarity.STRENGTH,
                        WritingDiagnosticDescriptorRegistry.blankTarget("blank-a", 1));
        WritingDiagnosticDescriptorRegistry.Resolution q52Grammar =
                WritingDiagnosticDescriptorRegistry.resolve(
                        WritingRubricCriterion.W_GRAMMAR_ERRORS,
                        "Q52",
                        ResultDetailPolarity.NEEDS_IMPROVEMENT,
                        WritingDiagnosticDescriptorRegistry.blankTarget("blank-b", 2));
        WritingDiagnosticDescriptorRegistry.Resolution compatibility =
                WritingDiagnosticDescriptorRegistry.resolve(
                        WritingRubricCriterion.W_GRAMMAR_ERRORS,
                        "Q51_52",
                        ResultDetailPolarity.NEEDS_IMPROVEMENT,
                        WritingDiagnosticDescriptorRegistry.blankTarget("blank-c", 1));

        assertThat(q51Context.parentCriterionId())
                .isEqualTo("W_CLOZE_BLANK_1_CONTEXT");
        assertThat(q51Context.applicability()).isEqualTo("WRITING_Q51");
        assertThat(q52Grammar.parentCriterionId())
                .isEqualTo("W_CLOZE_BLANK_2_GRAMMAR");
        assertThat(q52Grammar.applicability()).isEqualTo("WRITING_Q52");
        assertThat(compatibility.applicability())
                .isEqualTo("WRITING_Q51_52_COMPAT");
        assertThat(q51Context.target().blankId()).isEqualTo("blank-a");
        assertThat(q52Grammar.target().blankId()).isEqualTo("blank-b");
    }

    private static WritingDiagnosticDescriptorRegistry.Resolution resolve(
            WritingRubricCriterion criterion,
            String taskType,
            ResultDetailPolarity polarity
    ) {
        return WritingDiagnosticDescriptorRegistry.resolve(
                criterion,
                taskType,
                polarity,
                WritingDiagnosticDescriptorRegistry.wholeAnswerTarget());
    }
}
