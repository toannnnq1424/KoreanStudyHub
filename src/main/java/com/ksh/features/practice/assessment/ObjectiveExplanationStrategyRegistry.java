package com.ksh.features.practice.assessment;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Practice-owned authority for lecturer-selected Reading/Listening explanation
 * strategies.
 *
 * <p>The registry is deliberately independent from provider wire formats. A
 * published question owns one explicit strategy selection. The selected
 * strategy controls an allowlisted typed renderer; providers never choose a
 * strategy and never return HTML.</p>
 *
 * <p>Registry v1 remains readable for immutable published versions. New
 * drafts use v2. Unsupported catalog entries are visible to audit and product
 * tooling, but are never returned as selectable options.</p>
 */
public final class ObjectiveExplanationStrategyRegistry {

    public static final String LEGACY_REGISTRY_VERSION =
            "rl-explanation-strategy-registry-v1";
    /**
     * Compatibility constant retained for existing v1 callers. New authoring
     * surfaces must use {@link #CURRENT_REGISTRY_VERSION}.
     */
    public static final String REGISTRY_VERSION =
            LEGACY_REGISTRY_VERSION;
    public static final String CURRENT_REGISTRY_VERSION =
            "rl-explanation-strategy-registry-v2";
    public static final String STRATEGY_VERSION = "v1";

    private static final Map<CanonicalQuestionType, Set<Code>> LEGACY_ALLOWED =
            Map.of(
                    CanonicalQuestionType.SINGLE_CHOICE, Set.of(
                            Code.EVIDENCE_ONLY,
                            Code.ELIMINATE_ALL_INCORRECT,
                            Code.FULL_CONTEXT_THEN_ANSWER,
                            Code.HYBRID),
                    CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN, Set.of(
                            Code.CLAIM_EVIDENCE_RELATION),
                    CanonicalQuestionType.FILL_BLANK, Set.of(
                            Code.CONSTRAINTS_AND_EVIDENCE));

    private static final Map<CanonicalQuestionType, Set<Code>> ALLOWED =
            Map.of(
                    CanonicalQuestionType.SINGLE_CHOICE, Set.of(
                            Code.EXACT_EVIDENCE_ONLY,
                            Code.FULL_SOURCE_INLINE_HIGHLIGHT,
                            Code.QUESTION_EVIDENCE_TRANSLATION_TABLE,
                            Code.MCQ_OPTION_ELIMINATION,
                            Code.EVIDENCE_AND_ELIMINATION,
                            Code.KEYWORD_PARAPHRASE_BRIDGE,
                            Code.BILINGUAL_STEP_BY_STEP),
                    CanonicalQuestionType.MULTIPLE_ANSWER, Set.of(
                            Code.EXACT_EVIDENCE_ONLY,
                            Code.FULL_SOURCE_INLINE_HIGHLIGHT,
                            Code.QUESTION_EVIDENCE_TRANSLATION_TABLE,
                            Code.EVIDENCE_AND_ELIMINATION,
                            Code.KEYWORD_PARAPHRASE_BRIDGE,
                            Code.BILINGUAL_STEP_BY_STEP),
                    CanonicalQuestionType.MATCHING, Set.of(
                            Code.EXACT_EVIDENCE_ONLY,
                            Code.FULL_SOURCE_INLINE_HIGHLIGHT,
                            Code.QUESTION_EVIDENCE_TRANSLATION_TABLE,
                            Code.MATCHING_MATRIX,
                            Code.KEYWORD_PARAPHRASE_BRIDGE,
                            Code.BILINGUAL_STEP_BY_STEP),
                    CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN, Set.of(
                            Code.EXACT_EVIDENCE_ONLY,
                            Code.FULL_SOURCE_INLINE_HIGHLIGHT,
                            Code.QUESTION_EVIDENCE_TRANSLATION_TABLE,
                            Code.TFNG_CONTRADICTION_TABLE,
                            Code.NOT_GIVEN_BOUNDARY,
                            Code.KEYWORD_PARAPHRASE_BRIDGE,
                            Code.BILINGUAL_STEP_BY_STEP),
                    CanonicalQuestionType.FILL_BLANK, Set.of(
                            Code.EXACT_EVIDENCE_ONLY,
                            Code.FULL_SOURCE_INLINE_HIGHLIGHT,
                            Code.QUESTION_EVIDENCE_TRANSLATION_TABLE,
                            Code.FILL_SLOT_GRAMMAR_ANALYSIS,
                            Code.KEYWORD_PARAPHRASE_BRIDGE,
                            Code.BILINGUAL_STEP_BY_STEP));

    private static final Map<Code, CatalogEntry> CATALOG = catalogEntries();

    private ObjectiveExplanationStrategyRegistry() {
    }

    public static Selection requireSelection(
            CanonicalQuestionType questionType,
            String registryVersion,
            String strategyCode,
            String strategyVersion) {
        Selection selection = new Selection(
                registryVersion,
                strategyCode,
                strategyVersion);
        requireAllowed(questionType, selection);
        return selection;
    }

    public static void requireAllowed(
            CanonicalQuestionType questionType,
            Selection selection) {
        Map<CanonicalQuestionType, Set<Code>> allowed =
                isLegacy(selection) ? LEGACY_ALLOWED : ALLOWED;
        if (questionType == null
                || selection == null
                || !allowed.getOrDefault(questionType, Set.of())
                        .contains(selection.code())) {
            throw new IllegalArgumentException(
                    "Explanation strategy is not allowed for question type "
                            + questionType);
        }
    }

    /**
     * Answer-aware authoring boundary. Strategy compatibility is not only a
     * question-type concern: answer-dependent layouts must be backed by the
     * same canonical IDs and answer authority that will be published.
     */
    public static void requireAllowed(
            CanonicalQuestionType questionType,
            Selection selection,
            QuestionContent content,
            AnswerSpec answerSpec) {
        requireAllowed(questionType, selection);
        if (content == null || answerSpec == null) {
            throw new IllegalArgumentException(
                    "Explanation strategy requires canonical question and answer authority");
        }
        if (answerSpec.questionType() != questionType) {
            throw new IllegalArgumentException(
                    "Explanation strategy answer type does not match question type");
        }

        switch (questionType) {
            case SINGLE_CHOICE ->
                    requireOptionAuthority(content, answerSpec, 1);
            case MULTIPLE_ANSWER ->
                    requireOptionAuthority(content, answerSpec, 2);
            case MATCHING ->
                    requireMatchingAuthority(content, answerSpec);
            case TRUE_FALSE_NOT_GIVEN ->
                    requireTfngAuthority(selection, answerSpec);
            case FILL_BLANK ->
                    requireBlankAuthority(content, answerSpec);
            case ESSAY, SPEAKING -> {
                // These question families do not use the R/L strategy catalog.
            }
        }
    }

    /**
     * Runtime boundary check. It prevents a source-dependent layout from
     * reaching generation when the immutable source is absent, and prevents
     * option renderers from being selected for optionless questions.
     */
    public static void requireAllowed(
            CanonicalQuestionType questionType,
            Selection selection,
            AssessmentStimulus stimulus,
            QuestionContent content) {
        requireAllowed(questionType, selection);
        requireRuntimeEvidence(selection, stimulus, content);
    }

    /**
     * Runtime generation boundary with canonical answer authority. New
     * generation callers must use this overload so an answer-incompatible
     * strategy can never reach a provider request.
     */
    public static void requireAllowed(
            CanonicalQuestionType questionType,
            Selection selection,
            AssessmentStimulus stimulus,
            QuestionContent content,
            AnswerSpec answerSpec) {
        requireAllowed(questionType, selection, content, answerSpec);
        requireRuntimeEvidence(selection, stimulus, content);
    }

    private static void requireRuntimeEvidence(
            Selection selection,
            AssessmentStimulus stimulus,
            QuestionContent content) {
        if (isLegacy(selection)) {
            return;
        }
        CatalogEntry entry = CATALOG.get(selection.code());
        if (entry == null || !entry.selectable()) {
            throw new IllegalArgumentException(
                    "Explanation strategy is not selectable");
        }
        if (entry.requiredEvidence().contains("SOURCE_TEXT")
                && (stimulus == null || !stimulus.hasUsableEvidence())) {
            throw new IllegalArgumentException(
                    "Explanation strategy requires immutable source text");
        }
        if (entry.requiredEvidence().contains("OPTION_IDS")
                && (content == null || content.options().isEmpty())) {
            throw new IllegalArgumentException(
                    "Explanation strategy requires stable option IDs");
        }
        if (entry.requiredEvidence().contains("BLANK_IDS")
                && (content == null || content.blanks().isEmpty())) {
            throw new IllegalArgumentException(
                    "Explanation strategy requires stable blank IDs");
        }
    }

    private static void requireOptionAuthority(
            QuestionContent content,
            AnswerSpec answerSpec,
            int minimumCorrectOptions) {
        Set<String> optionIds = stableIds(
                content.options().stream()
                        .map(QuestionContent.Option::id)
                        .toList(),
                "option");
        List<String> correctOptionIds = answerSpec.correctOptionIds();
        if (correctOptionIds.size() < minimumCorrectOptions
                || !stableIds(correctOptionIds, "correct option")
                .stream().allMatch(optionIds::contains)) {
            throw new IllegalArgumentException(
                    "Explanation strategy requires canonical correct option IDs");
        }
    }

    private static void requireMatchingAuthority(
            QuestionContent content,
            AnswerSpec answerSpec) {
        Set<String> optionIds = stableIds(
                content.options().stream()
                        .map(QuestionContent.Option::id)
                        .toList(),
                "matching candidate");
        Set<String> targetIds = stableIds(
                content.blanks().stream()
                        .map(QuestionContent.Blank::id)
                        .toList(),
                "matching target");
        Set<String> answerTargetIds = stableIds(
                answerSpec.blanks().stream()
                        .map(AnswerSpec.BlankAnswer::blankId)
                        .toList(),
                "matching answer target");
        if (optionIds.size() < 2 || targetIds.isEmpty()
                || !targetIds.equals(answerTargetIds)) {
            throw new IllegalArgumentException(
                    "Explanation strategy matching IDs do not match canonical authority");
        }
        for (AnswerSpec.BlankAnswer answer : answerSpec.blanks()) {
            if (answer.acceptedValues().size() != 1
                    || !optionIds.contains(answer.acceptedValues().get(0))) {
                throw new IllegalArgumentException(
                        "Explanation strategy requires one canonical candidate per target");
            }
        }
    }

    private static void requireTfngAuthority(
            Selection selection,
            AnswerSpec answerSpec) {
        String correctValue = answerSpec.correctValue() == null
                ? ""
                : answerSpec.correctValue().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("TRUE", "FALSE", "NOT_GIVEN").contains(correctValue)) {
            throw new IllegalArgumentException(
                    "Explanation strategy requires a canonical TFNG answer");
        }
        if (selection.code() == Code.NOT_GIVEN_BOUNDARY
                && !"NOT_GIVEN".equals(correctValue)) {
            throw new IllegalArgumentException(
                    "NOT_GIVEN_BOUNDARY requires NOT_GIVEN as the canonical answer");
        }
    }

    private static void requireBlankAuthority(
            QuestionContent content,
            AnswerSpec answerSpec) {
        Set<String> contentBlankIds = stableIds(
                content.blanks().stream()
                        .map(QuestionContent.Blank::id)
                        .toList(),
                "blank");
        Set<String> answerBlankIds = stableIds(
                answerSpec.blanks().stream()
                        .map(AnswerSpec.BlankAnswer::blankId)
                        .toList(),
                "blank answer");
        if (contentBlankIds.isEmpty()
                || !contentBlankIds.equals(answerBlankIds)) {
            throw new IllegalArgumentException(
                    "Explanation strategy blank IDs do not match canonical answer authority");
        }
        for (AnswerSpec.BlankAnswer answer : answerSpec.blanks()) {
            if (answer.acceptedValues().isEmpty()
                    || answer.acceptedValues().stream()
                    .anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException(
                        "Explanation strategy requires accepted answers for every blank ID");
            }
        }
    }

    private static Set<String> stableIds(
            List<String> values,
            String label) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank() || !ids.add(value)) {
                throw new IllegalArgumentException(
                        "Explanation strategy requires unique stable " + label + " IDs");
            }
        }
        return Set.copyOf(ids);
    }

    public static List<Option> options(CanonicalQuestionType questionType) {
        return ALLOWED.getOrDefault(questionType, Set.of()).stream()
                .sorted(Comparator.comparingInt(Code::order))
                .map(ObjectiveExplanationStrategyRegistry::option)
                .toList();
    }

    /**
     * Complete catalog for editor discovery and audit. Unsupported entries
     * stay explicit with a reason instead of pretending to fit every question.
     */
    public static List<CatalogEntry> catalog() {
        return CATALOG.values().stream()
                .sorted(Comparator.comparingInt(
                        entry -> entry.code().order()))
                .toList();
    }

    private static Option option(Code code) {
        CatalogEntry entry = CATALOG.get(code);
        return new Option(
                code.name(),
                entry.labelVi(),
                entry.descriptionVi(),
                entry.categoryVi(),
                entry.rendererCode(),
                entry.requiredEvidence(),
                CURRENT_REGISTRY_VERSION,
                STRATEGY_VERSION);
    }

    private static boolean isLegacy(Selection selection) {
        return selection != null
                && LEGACY_REGISTRY_VERSION.equals(
                        selection.registryVersion());
    }

    private static Map<Code, CatalogEntry> catalogEntries() {
        Map<Code, CatalogEntry> entries = new LinkedHashMap<>();
        add(entries, Code.EXACT_EVIDENCE_ONLY,
                "Bằng chứng",
                "Chỉ dùng bằng chứng chính xác",
                "Đáp án và span nguồn ngắn nhất đủ thẩm quyền.",
                "EXACT_EVIDENCE_ONLY",
                Set.of("SOURCE_TEXT", "EVIDENCE_IDS"),
                Set.of(CanonicalQuestionType.SINGLE_CHOICE,
                        CanonicalQuestionType.MULTIPLE_ANSWER,
                        CanonicalQuestionType.MATCHING,
                        CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN,
                        CanonicalQuestionType.FILL_BLANK),
                true, "");
        add(entries, Code.FULL_SOURCE_INLINE_HIGHLIGHT,
                "Bằng chứng",
                "Toàn nguồn và tô sáng nội tuyến",
                "Giữ toàn văn passage/transcript, đánh số câu, tô từ khóa, "
                        + "bằng chứng và dịch nghĩa đúng span.",
                "FULL_SOURCE_INLINE_HIGHLIGHT",
                Set.of("SOURCE_TEXT", "UTF16_OFFSETS", "TRANSLATIONS"),
                Set.of(CanonicalQuestionType.SINGLE_CHOICE,
                        CanonicalQuestionType.MULTIPLE_ANSWER,
                        CanonicalQuestionType.MATCHING,
                        CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN,
                        CanonicalQuestionType.FILL_BLANK),
                true, "");
        add(entries, Code.QUESTION_EVIDENCE_TRANSLATION_TABLE,
                "Bảng đối chiếu",
                "Câu hỏi · vùng thông tin · dịch nghĩa",
                "Bảng đối chiếu câu hỏi với span nguồn và hàng dịch nghĩa.",
                "QUESTION_EVIDENCE_TRANSLATION_TABLE",
                Set.of("SOURCE_TEXT", "EVIDENCE_IDS", "TRANSLATIONS"),
                Set.of(CanonicalQuestionType.SINGLE_CHOICE,
                        CanonicalQuestionType.MULTIPLE_ANSWER,
                        CanonicalQuestionType.MATCHING,
                        CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN,
                        CanonicalQuestionType.FILL_BLANK),
                true, "");
        add(entries, Code.MCQ_OPTION_ELIMINATION,
                "Trắc nghiệm",
                "Loại từng phương án MCQ",
                "Chứng minh đáp án đúng và nêu lý do riêng cho mọi distractor.",
                "MCQ_OPTION_ELIMINATION",
                Set.of("SOURCE_TEXT", "OPTION_IDS", "EVIDENCE_IDS"),
                Set.of(CanonicalQuestionType.SINGLE_CHOICE),
                true, "");
        add(entries, Code.EVIDENCE_AND_ELIMINATION,
                "Trắc nghiệm",
                "Bằng chứng và loại trừ",
                "Kết hợp bảng evidence với lý do loại từng phương án.",
                "EVIDENCE_AND_ELIMINATION",
                Set.of("SOURCE_TEXT", "OPTION_IDS", "EVIDENCE_IDS",
                        "TRANSLATIONS"),
                Set.of(CanonicalQuestionType.SINGLE_CHOICE,
                        CanonicalQuestionType.MULTIPLE_ANSWER),
                true, "");
        add(entries, Code.TFNG_CONTRADICTION_TABLE,
                "Đúng · Sai · Không có thông tin",
                "Bảng đối chiếu TFNG",
                "So claim với nguồn và giải thích vì sao loại hai trạng thái còn lại.",
                "TFNG_CONTRADICTION_TABLE",
                Set.of("SOURCE_TEXT", "EVIDENCE_IDS"),
                Set.of(CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN),
                true, "");
        add(entries, Code.NOT_GIVEN_BOUNDARY,
                "Đúng · Sai · Không có thông tin",
                "Ranh giới Không có thông tin",
                "Tách điều nguồn thực sự nói khỏi phần claim tự bổ sung.",
                "NOT_GIVEN_BOUNDARY",
                Set.of("SOURCE_TEXT", "EVIDENCE_IDS"),
                Set.of(CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN),
                true, "");
        add(entries, Code.FILL_SLOT_GRAMMAR_ANALYSIS,
                "Điền chỗ trống",
                "Phân tích ràng buộc chỗ trống",
                "Ngữ cảnh trái/phải, từ loại, ngữ pháp, giới hạn từ và span đáp án.",
                "FILL_SLOT_GRAMMAR_ANALYSIS",
                Set.of("SOURCE_TEXT", "BLANK_IDS", "EVIDENCE_IDS"),
                Set.of(CanonicalQuestionType.FILL_BLANK),
                true, "");
        add(entries, Code.KEYWORD_PARAPHRASE_BRIDGE,
                "Ngôn ngữ",
                "Cầu nối từ khóa và diễn đạt tương đương",
                "Từ khóa câu hỏi đến paraphrase trong nguồn và quyết định đáp án.",
                "KEYWORD_PARAPHRASE_BRIDGE",
                Set.of("SOURCE_TEXT", "UTF16_OFFSETS", "TRANSLATIONS"),
                Set.of(CanonicalQuestionType.SINGLE_CHOICE,
                        CanonicalQuestionType.MULTIPLE_ANSWER,
                        CanonicalQuestionType.MATCHING,
                        CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN,
                        CanonicalQuestionType.FILL_BLANK),
                true, "");
        add(entries, Code.MATCHING_MATRIX,
                "Dạng ghép", "Ma trận ghép nối",
                "Đối chiếu từng target với đúng một nhãn A–H và span bằng chứng thẩm quyền.",
                "MATCHING_MATRIX",
                Set.of("SOURCE_TEXT", "OPTION_IDS", "BLANK_IDS", "EVIDENCE_IDS"),
                Set.of(CanonicalQuestionType.MATCHING),
                true, "");
        addUnsupported(entries, Code.PARAGRAPH_PURPOSE_MAP,
                "Dạng ghép", "Bản đồ mục đích đoạn", "PARAGRAPH_PURPOSE_MAP",
                "KSH chưa persist paragraph/speaker-purpose IDs trong immutable question.");
        addUnsupported(entries, Code.SEQUENCE_TIMELINE,
                "Trình tự", "Dòng thời gian trình tự", "SEQUENCE_TIMELINE",
                "KSH chưa có canonical ORDERING/SEQUENCE answer contract.");
        addUnsupported(entries, Code.CAUSE_EFFECT_CHAIN,
                "Suy luận", "Chuỗi nguyên nhân và kết quả", "CAUSE_EFFECT_CHAIN",
                "KSH chưa persist typed causal-node/link authority.");
        addUnsupported(entries, Code.COMPARE_CONTRAST_TABLE,
                "Suy luận", "Bảng so sánh và đối chiếu", "COMPARE_CONTRAST_TABLE",
                "KSH chưa persist typed comparison entities/axes.");
        addUnsupported(entries, Code.REFERENCE_RESOLUTION,
                "Ngôn ngữ", "Giải quyết từ quy chiếu", "REFERENCE_RESOLUTION",
                "KSH chưa persist antecedent/reference link authority.");
        addUnsupported(entries, Code.DISTRACTOR_TRAP_ANALYSIS,
                "Trắc nghiệm", "Phân tích bẫy distractor", "DISTRACTOR_TRAP_ANALYSIS",
                "Strict option rationale chưa có trapCode allowlist.");
        addUnsupported(entries, Code.SPEAKER_INTENT_AND_ATTITUDE,
                "Listening", "Ý định và thái độ người nói",
                "SPEAKER_INTENT_AND_ATTITUDE",
                "Không suy luận tone/attitude khi chưa có trusted audio evidence.");
        addUnsupported(entries, Code.TIMESTAMP_TURN_MAP,
                "Listening", "Bản đồ lượt nói và timestamp", "TIMESTAMP_TURN_MAP",
                "Immutable transcript chưa có verified timestamp alignment.");
        add(entries, Code.BILINGUAL_STEP_BY_STEP,
                "Hướng dẫn",
                "Từng bước song ngữ",
                "Các bước tiếng Việt ngắn gọn nhưng giữ span tiếng Hàn chính xác.",
                "BILINGUAL_STEP_BY_STEP",
                Set.of("SOURCE_TEXT", "EVIDENCE_IDS", "TRANSLATIONS"),
                Set.of(CanonicalQuestionType.SINGLE_CHOICE,
                        CanonicalQuestionType.MULTIPLE_ANSWER,
                        CanonicalQuestionType.MATCHING,
                        CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN,
                        CanonicalQuestionType.FILL_BLANK),
                true, "");
        addUnsupported(entries, Code.HYBRID_TEACHER_GUIDED,
                "Hướng dẫn", "Kết hợp block do giảng viên định hướng",
                "HYBRID_TEACHER_GUIDED",
                "Chưa có persisted ordered block-composition contract.");
        return Map.copyOf(entries);
    }

    private static void add(
            Map<Code, CatalogEntry> entries,
            Code code,
            String categoryVi,
            String labelVi,
            String descriptionVi,
            String rendererCode,
            Set<String> requiredEvidence,
            Set<CanonicalQuestionType> questionTypes,
            boolean selectable,
            String unsupportedReason) {
        entries.put(code, new CatalogEntry(
                code,
                categoryVi,
                labelVi,
                descriptionVi,
                rendererCode,
                requiredEvidence.stream().sorted().toList(),
                questionTypes.stream().map(Enum::name).sorted().toList(),
                selectable,
                unsupportedReason,
                CURRENT_REGISTRY_VERSION,
                STRATEGY_VERSION));
    }

    private static void addUnsupported(
            Map<Code, CatalogEntry> entries,
            Code code,
            String categoryVi,
            String labelVi,
            String rendererCode,
            String reason) {
        add(entries, code, categoryVi, labelVi, reason, rendererCode,
                Set.of(), Set.of(), false, reason);
    }

    public enum GenerationFamily {
        EVIDENCE,
        OPTION_ELIMINATION,
        FULL_CONTEXT,
        EVIDENCE_AND_ELIMINATION,
        TFNG_RELATION,
        FILL_CONSTRAINTS
    }

    public enum Code {
        EXACT_EVIDENCE_ONLY(10, GenerationFamily.EVIDENCE),
        FULL_SOURCE_INLINE_HIGHLIGHT(20, GenerationFamily.FULL_CONTEXT),
        QUESTION_EVIDENCE_TRANSLATION_TABLE(30, GenerationFamily.FULL_CONTEXT),
        MCQ_OPTION_ELIMINATION(40, GenerationFamily.OPTION_ELIMINATION),
        EVIDENCE_AND_ELIMINATION(
                50, GenerationFamily.EVIDENCE_AND_ELIMINATION),
        TFNG_CONTRADICTION_TABLE(60, GenerationFamily.TFNG_RELATION),
        NOT_GIVEN_BOUNDARY(70, GenerationFamily.TFNG_RELATION),
        FILL_SLOT_GRAMMAR_ANALYSIS(80, GenerationFamily.FILL_CONSTRAINTS),
        KEYWORD_PARAPHRASE_BRIDGE(90, GenerationFamily.FULL_CONTEXT),
        MATCHING_MATRIX(100, GenerationFamily.EVIDENCE),
        PARAGRAPH_PURPOSE_MAP(110, GenerationFamily.EVIDENCE),
        SEQUENCE_TIMELINE(120, GenerationFamily.EVIDENCE),
        CAUSE_EFFECT_CHAIN(130, GenerationFamily.EVIDENCE),
        COMPARE_CONTRAST_TABLE(140, GenerationFamily.EVIDENCE),
        REFERENCE_RESOLUTION(150, GenerationFamily.EVIDENCE),
        DISTRACTOR_TRAP_ANALYSIS(160, GenerationFamily.OPTION_ELIMINATION),
        SPEAKER_INTENT_AND_ATTITUDE(170, GenerationFamily.EVIDENCE),
        TIMESTAMP_TURN_MAP(180, GenerationFamily.EVIDENCE),
        BILINGUAL_STEP_BY_STEP(190, GenerationFamily.FULL_CONTEXT),
        HYBRID_TEACHER_GUIDED(
                200, GenerationFamily.EVIDENCE_AND_ELIMINATION),

        // Immutable v1 compatibility aliases. Never offered for new drafts.
        EVIDENCE_ONLY(1010, GenerationFamily.EVIDENCE),
        ELIMINATE_ALL_INCORRECT(1020, GenerationFamily.OPTION_ELIMINATION),
        FULL_CONTEXT_THEN_ANSWER(1030, GenerationFamily.FULL_CONTEXT),
        HYBRID(1040, GenerationFamily.EVIDENCE_AND_ELIMINATION),
        CLAIM_EVIDENCE_RELATION(1050, GenerationFamily.TFNG_RELATION),
        CONSTRAINTS_AND_EVIDENCE(1060, GenerationFamily.FILL_CONSTRAINTS);

        private final int order;
        private final GenerationFamily generationFamily;

        Code(int order, GenerationFamily generationFamily) {
            this.order = order;
            this.generationFamily = generationFamily;
        }

        int order() {
            return order;
        }

        public GenerationFamily generationFamily() {
            return generationFamily;
        }

        public static Code parse(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(
                        "Explanation strategy code is required");
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Unsupported explanation strategy code: " + value,
                        exception);
            }
        }
    }

    public record Selection(
            String registryVersion,
            String strategyCode,
            String strategyVersion
    ) {
        public Selection {
            if ((!CURRENT_REGISTRY_VERSION.equals(registryVersion)
                    && !LEGACY_REGISTRY_VERSION.equals(registryVersion))
                    || strategyCode == null
                    || strategyCode.isBlank()
                    || !STRATEGY_VERSION.equals(strategyVersion)) {
                throw new IllegalArgumentException(
                        "Explanation strategy selection is incomplete");
            }
            Code parsed = Code.parse(strategyCode);
            boolean legacyCode = parsed.order() >= 1000;
            if (LEGACY_REGISTRY_VERSION.equals(registryVersion)
                    != legacyCode) {
                throw new IllegalArgumentException(
                        "Explanation strategy code/version mismatch");
            }
            strategyCode = parsed.name();
        }

        public Code code() {
            return Code.parse(strategyCode);
        }

        public GenerationFamily generationFamily() {
            return code().generationFamily();
        }
    }

    public record Option(
            String code,
            String labelVi,
            String descriptionVi,
            String categoryVi,
            String rendererCode,
            List<String> requiredEvidence,
            String registryVersion,
            String strategyVersion
    ) {
    }

    public record CatalogEntry(
            Code code,
            String categoryVi,
            String labelVi,
            String descriptionVi,
            String rendererCode,
            List<String> requiredEvidence,
            List<String> supportedQuestionTypes,
            boolean selectable,
            String unsupportedReason,
            String registryVersion,
            String strategyVersion
    ) {
    }
}
