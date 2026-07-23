package com.ksh.features.practice.result;

import com.ksh.features.practice.ai.writing.WritingRubricCriterion;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailPolarity;
import com.ksh.features.practice.dto.PracticeDtos.WritingDiagnosticTarget;
import com.ksh.features.practice.dto.PracticeDtos.WritingDiagnosticTargetKind;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded backend-owned descriptor hierarchy for the current Writing Detail UI.
 *
 * <p>The category families reserve an honest place for future typed Korean
 * diagnostics. Only active {@link WritingRubricCriterion} ids are registered as
 * current feature descriptors. The future-family codes are planning metadata,
 * never findings and never score-bearing subcriteria.</p>
 */
final class WritingDiagnosticDescriptorRegistry {

    static final String SEAM_ID = "ksh-writing-detail-diagnostics-seam-v1";
    static final String SEAM_STATE = "BOUNDED_CURRENT_EVIDENCE";
    static final String SCOPE_NOTE_VI =
            "Phạm vi chẩn đoán có giới hạn; chỉ hiển thị phát hiện có bằng chứng.";
    static final String SCOPE_NOTE_KO =
            "진단 범위는 제한되어 있으며 근거가 확인된 항목만 표시합니다.";

    private static final List<CategoryDescriptor> CATEGORIES = List.of(
            category(
                    "TASK_CONTENT",
                    "Yêu cầu & nội dung",
                    "과제·내용",
                    1,
                    "TASK_FULFILLMENT",
                    "CONTENT_RELEVANCE",
                    "CONTENT_DEVELOPMENT"),
            category(
                    "DISCOURSE",
                    "Tổ chức & liên kết diễn ngôn",
                    "담화 구성·응집성",
                    2,
                    "DISCOURSE_STRUCTURE",
                    "CONNECTIVES",
                    "COHESION",
                    "REFERENCE_ELLIPSIS"),
            category(
                    "MORPHOSYNTAX",
                    "Hình thái & cú pháp",
                    "형태·통사",
                    3,
                    "PARTICLE_CASE_TOPIC",
                    "ENDING_CONJUGATION_SPEECH_LEVEL",
                    "TENSE_ASPECT_MODALITY_NEGATION",
                    "PREDICATE_VALENCY_AGREEMENT",
                    "CLAUSE_QUOTATION_NOMINALIZATION",
                    "PASSIVE_CAUSATIVE",
                    "WORD_ORDER"),
            category(
                    "LEXICO_SEMANTIC",
                    "Từ vựng & kết hợp từ",
                    "어휘·연어",
                    4,
                    "LEXICAL_SENSE_PRECISION",
                    "COLLOCATION",
                    "SINO_KOREAN_VOCABULARY",
                    "DOMAIN_VOCABULARY",
                    "NATURALNESS",
                    "REPETITION"),
            category(
                    "SOCIOLINGUISTIC_PRAGMATIC",
                    "Văn phong & dụng học",
                    "문체·화용",
                    5,
                    "GENRE_REGISTER",
                    "HONORIFIC",
                    "PRAGMATICS"),
            category(
                    "ORTHOGRAPHY",
                    "Chính tả & cách chữ",
                    "맞춤법·띄어쓰기",
                    6,
                    "SPELLING",
                    "SPACING",
                    "PUNCTUATION"),
            category(
                    "LENGTH_FORMAT",
                    "Dung lượng & định dạng",
                    "분량·형식",
                    7,
                    "LENGTH",
                    "FORMAT")
    );

    private static final Map<String, CategoryDescriptor> CATEGORY_BY_CODE =
            categoryIndex();
    private static final List<FeatureDescriptor> FEATURES = activeFeatures();
    private static final Map<String, FeatureDescriptor> FEATURE_BY_CODE =
            featureIndex();

    private WritingDiagnosticDescriptorRegistry() {
    }

    static List<CategoryDescriptor> categories() {
        return CATEGORIES;
    }

    static List<FeatureDescriptor> features() {
        return FEATURES;
    }

    static Resolution resolve(
            WritingRubricCriterion rawCriterion,
            String taskType,
            ResultDetailPolarity polarity,
            AuthoritativeTarget authoritativeTarget
    ) {
        if (rawCriterion == null || polarity == null
                || !rawCriterion.activeForProvider()
                || !rawCriterion.polarity().name().equals(polarity.name())
                || !rawCriterion.appliesTo(taskType)) {
            return null;
        }
        FeatureDescriptor feature = FEATURE_BY_CODE.get(rawCriterion.id());
        if (feature == null) {
            return null;
        }

        String taskIdentity = taskIdentity(taskType);
        WritingDiagnosticTarget target;
        String parentCriterionId;
        String scoreEffect;
        if (isClozeTask(taskIdentity)) {
            if (authoritativeTarget == null
                    || authoritativeTarget.kind() != WritingDiagnosticTargetKind.BLANK
                    || authoritativeTarget.blankId() == null
                    || authoritativeTarget.blankId().isBlank()
                    || authoritativeTarget.blankIndex() == null
                    || authoritativeTarget.blankIndex() < 1
                    || authoritativeTarget.blankIndex() > 2) {
                return null;
            }
            target = new WritingDiagnosticTarget(
                    WritingDiagnosticTargetKind.BLANK,
                    authoritativeTarget.blankId(),
                    authoritativeTarget.blankIndex());
            parentCriterionId = clozeParent(feature, authoritativeTarget.blankIndex());
            scoreEffect = parentCriterionId == null
                    ? "DIAGNOSTIC_ONLY"
                    : "PARENT_LINKED";
        } else {
            if (authoritativeTarget != null
                    && authoritativeTarget.kind() != WritingDiagnosticTargetKind.WHOLE_ANSWER) {
                return null;
            }
            target = new WritingDiagnosticTarget(
                    WritingDiagnosticTargetKind.WHOLE_ANSWER, null, null);
            parentCriterionId = longFormParent(feature.category().code());
            scoreEffect = parentCriterionId == null
                    ? "DIAGNOSTIC_ONLY"
                    : "PARENT_LINKED";
        }

        String applicability = "WRITING_" + taskIdentity;
        String descriptorId = feature.code() + "_" + applicability;
        if (target.kind() == WritingDiagnosticTargetKind.BLANK) {
            descriptorId += "_BLANK_" + target.blankIndex();
        }
        return new Resolution(
                descriptorId,
                feature,
                parentCriterionId,
                scoreEffect,
                applicability,
                feature.stableOrder() * 2 + polarityOffset(polarity),
                target);
    }

    static AuthoritativeTarget wholeAnswerTarget() {
        return new AuthoritativeTarget(
                WritingDiagnosticTargetKind.WHOLE_ANSWER, null, null);
    }

    static AuthoritativeTarget blankTarget(String blankId, int blankIndex) {
        return new AuthoritativeTarget(
                WritingDiagnosticTargetKind.BLANK, blankId, blankIndex);
    }

    private static List<FeatureDescriptor> activeFeatures() {
        return Arrays.stream(WritingRubricCriterion.values())
                .filter(WritingRubricCriterion::activeForProvider)
                .map(criterion -> {
                    CategoryDescriptor category = CATEGORY_BY_CODE.get(
                            categoryCode(criterion));
                    Set<String> tasks = Set.copyOf(
                            List.of("Q51", "Q52", "Q53", "Q54", "GENERAL").stream()
                                    .filter(criterion::appliesTo)
                                    .toList());
                    return new FeatureDescriptor(
                            criterion.id(),
                            category,
                            criterion.vietnameseLabel(),
                            criterion.koreanLabel(),
                            category.stableOrder() * 1_000 + criterion.ordinal() + 1,
                            tasks);
                })
                .toList();
    }

    private static String categoryCode(WritingRubricCriterion criterion) {
        if (criterion == WritingRubricCriterion.W_CLOZE_CONTEXT_FIT) {
            return "TASK_CONTENT";
        }
        if (criterion == WritingRubricCriterion.W_CONNECTIVE_ENDING_ACCURACY
                || criterion == WritingRubricCriterion.W_CLOZE_GRAMMAR_COMPATIBILITY) {
            return "MORPHOSYNTAX";
        }
        if (criterion == WritingRubricCriterion.W_SENTENCE_COMPLETION_NATURALNESS) {
            return "LEXICO_SEMANTIC";
        }
        if (criterion == WritingRubricCriterion.W_CLOZE_REGISTER_MATCH) {
            return "SOCIOLINGUISTIC_PRAGMATIC";
        }
        return switch (criterion.category()) {
            case CONTENT -> "TASK_CONTENT";
            case ORGANIZATION -> "DISCOURSE";
            case GRAMMAR -> "MORPHOSYNTAX";
            case VOCABULARY, GENERAL_LANGUAGE -> "LEXICO_SEMANTIC";
            case REGISTER -> "SOCIOLINGUISTIC_PRAGMATIC";
            case SPELLING_SPACING -> "ORTHOGRAPHY";
            case LENGTH, FORMAT -> "LENGTH_FORMAT";
            case CLOZE -> throw new IllegalStateException(
                    "Unmapped active cloze Writing criterion: " + criterion.id());
        };
    }

    private static String longFormParent(String categoryCode) {
        return switch (categoryCode) {
            case "TASK_CONTENT" -> "W_CONTENT_TASK_ACHIEVEMENT";
            case "DISCOURSE" -> "W_ORGANIZATION_COHERENCE";
            case "MORPHOSYNTAX", "LEXICO_SEMANTIC",
                    "SOCIOLINGUISTIC_PRAGMATIC", "ORTHOGRAPHY" ->
                    "W_LANGUAGE_EXPRESSION";
            case "LENGTH_FORMAT" -> null;
            default -> null;
        };
    }

    private static String clozeParent(
            FeatureDescriptor feature,
            int blankIndex
    ) {
        String parentFamily = switch (feature.category().code()) {
            case "TASK_CONTENT", "DISCOURSE" -> "CONTEXT";
            case "MORPHOSYNTAX" -> "GRAMMAR";
            case "LEXICO_SEMANTIC", "SOCIOLINGUISTIC_PRAGMATIC",
                    "ORTHOGRAPHY" -> "EXPRESSION";
            case "LENGTH_FORMAT" -> null;
            default -> null;
        };
        return parentFamily == null
                ? null
                : "W_CLOZE_BLANK_" + blankIndex + "_" + parentFamily;
    }

    private static String taskIdentity(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            return "GENERAL";
        }
        return "Q51_52".equals(taskType) ? "Q51_52_COMPAT" : taskType;
    }

    private static boolean isClozeTask(String taskIdentity) {
        return "Q51".equals(taskIdentity)
                || "Q52".equals(taskIdentity)
                || "Q51_52_COMPAT".equals(taskIdentity);
    }

    private static int polarityOffset(ResultDetailPolarity polarity) {
        return polarity == ResultDetailPolarity.STRENGTH ? 1 : 2;
    }

    private static CategoryDescriptor category(
            String code,
            String labelVi,
            String labelKo,
            int stableOrder,
            String... futureTypedFamilies
    ) {
        return new CategoryDescriptor(
                code,
                labelVi,
                labelKo,
                stableOrder,
                List.of(futureTypedFamilies));
    }

    private static Map<String, CategoryDescriptor> categoryIndex() {
        Map<String, CategoryDescriptor> result = new LinkedHashMap<>();
        for (CategoryDescriptor descriptor : CATEGORIES) {
            if (result.put(descriptor.code(), descriptor) != null) {
                throw new IllegalStateException(
                        "Duplicate Writing diagnostic category: " + descriptor.code());
            }
        }
        return Map.copyOf(result);
    }

    private static Map<String, FeatureDescriptor> featureIndex() {
        Map<String, FeatureDescriptor> result = new LinkedHashMap<>();
        for (FeatureDescriptor descriptor : FEATURES) {
            if (result.put(descriptor.code(), descriptor) != null) {
                throw new IllegalStateException(
                        "Duplicate Writing diagnostic feature: " + descriptor.code());
            }
        }
        return Map.copyOf(result);
    }

    record CategoryDescriptor(
            String code,
            String labelVi,
            String labelKo,
            int stableOrder,
            List<String> futureTypedFamilies
    ) {
        CategoryDescriptor {
            futureTypedFamilies = List.copyOf(futureTypedFamilies);
        }
    }

    record FeatureDescriptor(
            String code,
            CategoryDescriptor category,
            String labelVi,
            String labelKo,
            int stableOrder,
            Set<String> taskApplicability
    ) {
        FeatureDescriptor {
            taskApplicability = Set.copyOf(taskApplicability);
        }
    }

    record AuthoritativeTarget(
            WritingDiagnosticTargetKind kind,
            String blankId,
            Integer blankIndex
    ) {
    }

    record Resolution(
            String id,
            FeatureDescriptor feature,
            String parentCriterionId,
            String scoreEffect,
            String applicability,
            int stableOrder,
            WritingDiagnosticTarget target
    ) {
    }
}
