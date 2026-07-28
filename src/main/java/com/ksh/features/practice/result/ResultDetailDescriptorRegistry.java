package com.ksh.features.practice.result;

import com.ksh.features.practice.ai.speaking.SpeakingRubricCriterion;
import com.ksh.features.practice.ai.writing.WritingRubricCriterion;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailPolarity;

/**
 * Backend-owned KSH Korean descriptor policy for Result Detail navigation.
 * Provider display labels are deliberately not accepted by this boundary.
 */
final class ResultDetailDescriptorRegistry {

    private ResultDetailDescriptorRegistry() {
    }

    static Definition writing(
            WritingRubricCriterion rawCriterion,
            String taskType,
            ResultDetailPolarity polarity
    ) {
        WritingDiagnosticDescriptorRegistry.Resolution resolution =
                WritingDiagnosticDescriptorRegistry.resolve(
                        rawCriterion,
                        taskType,
                        polarity,
                        isClozeTask(taskType)
                                ? null
                                : WritingDiagnosticDescriptorRegistry.wholeAnswerTarget());
        if (resolution == null || resolution.parentCriterionId() == null) {
            return null;
        }
        return new Definition(
                resolution.id(),
                resolution.feature().labelVi(),
                resolution.feature().labelKo(),
                resolution.parentCriterionId(),
                resolution.applicability(),
                resolution.stableOrder());
    }

    static Definition speaking(
            SpeakingRubricCriterion parentCriterion,
            String subCriterionId,
            ResultDetailPolarity polarity
    ) {
        if (parentCriterion == null
                || !parentCriterion.transcriptGrounded()
                || !parentCriterion.ownsSubcriterion(subCriterionId)) {
            return null;
        }
        SpeakingSubcriterion descriptor = speakingSubcriterion(subCriterionId.trim());
        if (descriptor == null || descriptor.parentCriterion() != parentCriterion) {
            return null;
        }
        return new Definition(
                "D_" + subCriterionId.trim() + "_" + polarity.name(),
                descriptor.labelVi(),
                descriptor.labelKo(),
                parentCriterion.id(),
                "SPEAKING_TRANSCRIPT_ONLY",
                (parentCriterion.ordinal() + 1) * 1_000
                        + descriptor.order() * 2
                        + polarityOffset(polarity));
    }

    static SpeakingFamily speakingFamily(String subCriterionId) {
        if (subCriterionId == null || speakingSubcriterion(subCriterionId.trim()) == null) {
            return null;
        }
        String normalized = subCriterionId.trim();
        if (normalized.startsWith("S_CONTENT_")) {
            return new SpeakingFamily(
                    "TASK_RESPONSE_RELEVANCE",
                    "Hoàn thành nhiệm vụ và mức độ phù hợp",
                    "과제 수행과 응답 적합성",
                    1);
        }
        if (normalized.startsWith("S_COHERENCE_")) {
            return new SpeakingFamily(
                    "DISCOURSE_ORGANIZATION",
                    "Diễn ngôn và tổ chức ý",
                    "담화와 내용 구성",
                    2);
        }
        if ("S_GRAMMAR_HONORIFIC_REGISTER".equals(normalized)) {
            return new SpeakingFamily(
                    "SOCIOLINGUISTIC_REGISTER_PRAGMATICS",
                    "Kính ngữ, văn phong và ngữ dụng",
                    "높임법·문체와 화용",
                    5);
        }
        if (normalized.startsWith("S_GRAMMAR_")) {
            return new SpeakingFamily(
                    "MORPHOSYNTAX",
                    "Hình thái, cú pháp và kiểm soát câu",
                    "형태·통사와 문장 통제",
                    3);
        }
        if (normalized.startsWith("S_VOCAB_")) {
            return new SpeakingFamily(
                    "LEXICON_COLLOCATION",
                    "Từ vựng, kết hợp từ và cách diễn đạt",
                    "어휘·연어와 표현",
                    4);
        }
        return null;
    }

    static String scoreLabelVi(String criterionId) {
        return switch (criterionId == null ? "" : criterionId) {
            case "W_CONTENT_TASK_ACHIEVEMENT" -> "Hoàn thành nhiệm vụ và nội dung";
            case "W_ORGANIZATION_COHERENCE" -> "Cấu trúc và mạch lạc";
            case "W_LANGUAGE_EXPRESSION" -> "Ngôn ngữ và biểu đạt";
            case "W_CLOZE_BLANK_1_CONTEXT" -> "Ô 1 - Nội dung và ngữ cảnh";
            case "W_CLOZE_BLANK_1_GRAMMAR" -> "Ô 1 - Ngữ pháp và cấu trúc";
            case "W_CLOZE_BLANK_1_EXPRESSION" -> "Ô 1 - Biểu đạt và độ tự nhiên";
            case "W_CLOZE_BLANK_2_CONTEXT" -> "Ô 2 - Nội dung và ngữ cảnh";
            case "W_CLOZE_BLANK_2_GRAMMAR" -> "Ô 2 - Ngữ pháp và cấu trúc";
            case "W_CLOZE_BLANK_2_EXPRESSION" -> "Ô 2 - Biểu đạt và độ tự nhiên";
            case "S_CONTENT_TASK_FULFILLMENT", "S_GRAMMAR_SENTENCE_CONTROL",
                    "S_VOCABULARY_EXPRESSIONS", "S_COHERENCE_ORGANIZATION",
                    "S_FLUENCY", "S_PRONUNCIATION_DELIVERY" -> speakingLabelVi(criterionId);
            default -> "Tiêu chí chấm chính thức";
        };
    }

    static String scoreLabelKo(String criterionId) {
        return switch (criterionId == null ? "" : criterionId) {
            case "W_CONTENT_TASK_ACHIEVEMENT" -> "과제 수행과 내용";
            case "W_ORGANIZATION_COHERENCE" -> "구성과 일관성";
            case "W_LANGUAGE_EXPRESSION" -> "언어와 표현";
            case "W_CLOZE_BLANK_1_CONTEXT" -> "1번 빈칸 - 내용과 맥락";
            case "W_CLOZE_BLANK_1_GRAMMAR" -> "1번 빈칸 - 문법과 구조";
            case "W_CLOZE_BLANK_1_EXPRESSION" -> "1번 빈칸 - 표현과 자연스러움";
            case "W_CLOZE_BLANK_2_CONTEXT" -> "2번 빈칸 - 내용과 맥락";
            case "W_CLOZE_BLANK_2_GRAMMAR" -> "2번 빈칸 - 문법과 구조";
            case "W_CLOZE_BLANK_2_EXPRESSION" -> "2번 빈칸 - 표현과 자연스러움";
            case "S_CONTENT_TASK_FULFILLMENT", "S_GRAMMAR_SENTENCE_CONTROL",
                    "S_VOCABULARY_EXPRESSIONS", "S_COHERENCE_ORGANIZATION",
                    "S_FLUENCY", "S_PRONUNCIATION_DELIVERY" -> speakingLabelKo(criterionId);
            default -> null;
        };
    }

    static String evidenceAvailability(String evidenceScope) {
        return switch (evidenceScope == null ? "" : evidenceScope) {
            case "TEXT_SPAN" -> "EXACT_TEXT_AVAILABLE";
            case "WHOLE_ANSWER" -> "WHOLE_ANSWER_AVAILABLE";
            case "TASK_METADATA" -> "TASK_EVIDENCE_AVAILABLE";
            default -> null;
        };
    }

    private static String speakingLabelVi(String criterionId) {
        return switch (criterionId == null ? "" : criterionId) {
            case "S_CONTENT_TASK_FULFILLMENT" -> "Nội dung và hoàn thành yêu cầu";
            case "S_GRAMMAR_SENTENCE_CONTROL" -> "Ngữ pháp và kiểm soát câu";
            case "S_VOCABULARY_EXPRESSIONS" -> "Từ vựng và cách diễn đạt";
            case "S_COHERENCE_ORGANIZATION" -> "Mạch lạc và tổ chức ý";
            case "S_FLUENCY" -> "Độ lưu loát";
            case "S_PRONUNCIATION_DELIVERY" -> "Phát âm và cách thể hiện";
            default -> "Tiêu chí ngôn ngữ";
        };
    }

    private static String speakingLabelKo(String criterionId) {
        return switch (criterionId == null ? "" : criterionId) {
            case "S_CONTENT_TASK_FULFILLMENT" -> "내용 및 과제 수행";
            case "S_GRAMMAR_SENTENCE_CONTROL" -> "문법 및 문장 통제";
            case "S_VOCABULARY_EXPRESSIONS" -> "어휘 및 표현";
            case "S_COHERENCE_ORGANIZATION" -> "일관성과 구성";
            case "S_FLUENCY" -> "유창성";
            case "S_PRONUNCIATION_DELIVERY" -> "발음 및 전달";
            default -> null;
        };
    }

    private static SpeakingSubcriterion speakingSubcriterion(String subCriterionId) {
        return switch (subCriterionId) {
            case "S_CONTENT_RELEVANCE" -> new SpeakingSubcriterion(
                    SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT,
                    "Bám sát đề", "주제 관련성", 1);
            case "S_CONTENT_PROMPT_COVERAGE" -> new SpeakingSubcriterion(
                    SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT,
                    "Bao phủ yêu cầu", "요구 사항 충족", 2);
            case "S_CONTENT_SPECIFICITY_EXAMPLES" -> new SpeakingSubcriterion(
                    SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT,
                    "Mức độ cụ thể và ví dụ", "구체성과 예시", 3);
            case "S_GRAMMAR_PARTICLES" -> new SpeakingSubcriterion(
                    SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                    "Tiểu từ", "조사", 1);
            case "S_GRAMMAR_TENSE_ASPECT" -> new SpeakingSubcriterion(
                    SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                    "Thì và thể", "시제와 상", 2);
            case "S_GRAMMAR_ENDINGS" -> new SpeakingSubcriterion(
                    SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                    "Đuôi câu và vĩ tố", "문장 종결형과 어미", 3);
            case "S_GRAMMAR_SENTENCE_STRUCTURE" -> new SpeakingSubcriterion(
                    SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                    "Cấu trúc câu", "문장 구조", 4);
            case "S_GRAMMAR_HONORIFIC_REGISTER" -> new SpeakingSubcriterion(
                    SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                    "Kính ngữ và văn phong", "높임법과 문체", 5);
            case "S_GRAMMAR_CONNECTORS" -> new SpeakingSubcriterion(
                    SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                    "Liên kết", "연결 표현", 6);
            case "S_VOCAB_TOPIC_WORDS" -> new SpeakingSubcriterion(
                    SpeakingRubricCriterion.VOCABULARY_EXPRESSIONS,
                    "Từ vựng theo chủ đề", "주제 어휘", 1);
            case "S_VOCAB_NATURAL_EXPRESSIONS" -> new SpeakingSubcriterion(
                    SpeakingRubricCriterion.VOCABULARY_EXPRESSIONS,
                    "Diễn đạt tự nhiên", "자연스러운 표현", 2);
            case "S_VOCAB_REPETITION_CONTROL" -> new SpeakingSubcriterion(
                    SpeakingRubricCriterion.VOCABULARY_EXPRESSIONS,
                    "Kiểm soát lặp từ", "어휘 반복 조절", 3);
            case "S_VOCAB_WORD_CHOICE" -> new SpeakingSubcriterion(
                    SpeakingRubricCriterion.VOCABULARY_EXPRESSIONS,
                    "Lựa chọn từ", "어휘 선택", 4);
            case "S_COHERENCE_ORGANIZATION" -> new SpeakingSubcriterion(
                    SpeakingRubricCriterion.COHERENCE_ORGANIZATION,
                    "Tổ chức ý", "내용 구성", 1);
            case "S_COHERENCE_LOGICAL_FLOW" -> new SpeakingSubcriterion(
                    SpeakingRubricCriterion.COHERENCE_ORGANIZATION,
                    "Mạch logic", "논리적 흐름", 2);
            case "S_COHERENCE_DISCOURSE_MARKERS" -> new SpeakingSubcriterion(
                    SpeakingRubricCriterion.COHERENCE_ORGANIZATION,
                    "Dấu hiệu diễn ngôn", "담화 표지", 3);
            default -> null;
        };
    }

    private static int polarityOffset(ResultDetailPolarity polarity) {
        return polarity == ResultDetailPolarity.STRENGTH ? 1 : 2;
    }

    private static String normalizedTaskType(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            return "GENERAL";
        }
        return "Q51_52".equals(taskType) ? "Q51" : taskType;
    }

    private static int writingTaskOrder(String taskType) {
        return switch (normalizedTaskType(taskType)) {
            case "Q51" -> 1_000;
            case "Q52" -> 2_000;
            case "Q53" -> 3_000;
            case "Q54" -> 4_000;
            default -> 5_000;
        };
    }

    private static boolean isClozeTask(String taskType) {
        String normalized = normalizedTaskType(taskType);
        return "Q51".equals(normalized) || "Q52".equals(normalized);
    }

    record Definition(
            String id,
            String labelVi,
            String labelKo,
            String parentCriterionId,
            String applicability,
            int stableOrder
    ) {
    }

    record SpeakingFamily(
            String code,
            String labelVi,
            String labelKo,
            int stableOrder
    ) {
    }

    private record SpeakingSubcriterion(
            SpeakingRubricCriterion parentCriterion,
            String labelVi,
            String labelKo,
            int order
    ) {
    }
}
