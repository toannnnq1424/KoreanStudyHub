package com.ksh.features.practice.ai;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public enum WritingRubricCriterion {
    W_ADVANCED_GRAMMAR_STRUCTURES("Ngữ pháp nâng cao chính xác", "고급 문법", Polarity.STRENGTH,
            FindingCategory.GRAMMAR, scopes(EvidenceScope.TEXT_SPAN), tasks("Q53", "Q54", "GENERAL"), true, null,
            "Ghi nhận cấu trúc ngữ pháp trung-cao cấp được dùng chính xác."),
    W_ACCURATE_SPELLING_SPACING("Chính tả và cách chữ chính xác", "맞춤법 및 띄어쓰기 정확성", Polarity.STRENGTH,
            FindingCategory.SPELLING_SPACING, scopes(EvidenceScope.TEXT_SPAN), tasks("Q51", "Q52", "Q53", "Q54", "GENERAL"), true, null,
            "Ghi nhận một đoạn có chính tả và cách chữ chính xác."),
    W_FORMAL_REGISTER_CONSISTENCY("Đồng nhất văn phong viết", "문체 일관성", Polarity.STRENGTH,
            FindingCategory.REGISTER, scopes(EvidenceScope.TEXT_SPAN, EvidenceScope.WHOLE_ANSWER), tasks("Q51", "Q52", "Q53", "Q54", "GENERAL"), true, null,
            "Ghi nhận văn phong viết nhất quán, không trộn lẫn đuôi câu nói."),
    W_FORMAL_VOCABULARY_USAGE("Từ vựng văn viết phù hợp", "문어체 어휘 사용", Polarity.STRENGTH,
            FindingCategory.VOCABULARY, scopes(EvidenceScope.TEXT_SPAN), tasks("Q51", "Q52", "Q53", "Q54", "GENERAL"), true, null,
            "Ghi nhận từ vựng trang trọng, chính xác và phù hợp ngữ cảnh."),
    W_TOPIC_SPECIFIC_EXPRESSIONS("Từ vựng và cụm từ theo chủ đề", "주제별 어휘 및 연어", Polarity.STRENGTH,
            FindingCategory.VOCABULARY, scopes(EvidenceScope.TEXT_SPAN), tasks("Q53", "Q54", "GENERAL"), true, null,
            "Ghi nhận từ Hán-Hàn hoặc collocation trung-cao cấp đúng chủ đề."),
    W_NATURAL_KOREAN_EXPRESSIONS("Diễn đạt tiếng Hàn tự nhiên", "자연스러운 한국어 표현", Polarity.STRENGTH,
            FindingCategory.GENERAL_LANGUAGE, scopes(EvidenceScope.TEXT_SPAN), tasks("Q51", "Q52", "Q53", "Q54", "GENERAL"), true, null,
            "Ghi nhận cách diễn đạt tự nhiên, không sao chép máy móc."),
    W_LENGTH_REQUIREMENT_MET("Dung lượng bài phù hợp", "분량 요건 충족", Polarity.STRENGTH,
            FindingCategory.LENGTH, scopes(EvidenceScope.WHOLE_ANSWER), tasks("Q53", "Q54"), true, null,
            "Ghi nhận toàn bài đạt phạm vi ký tự của dạng câu hỏi."),
    W_SENTENCE_PATTERN_VARIETY("Sự đa dạng cấu trúc câu", "문장 구조의 다양성", Polarity.STRENGTH,
            FindingCategory.GRAMMAR, scopes(EvidenceScope.TEXT_SPAN, EvidenceScope.WHOLE_ANSWER), tasks("Q53", "Q54", "GENERAL"), true, null,
            "Ghi nhận việc sử dụng nhiều mẫu câu phù hợp mà không lặp máy móc."),

    W_TASK_REQUIREMENT_COVERAGE("Bao phủ yêu cầu đề bài", "과제 요구 충족", Polarity.STRENGTH,
            FindingCategory.CONTENT, scopes(EvidenceScope.WHOLE_ANSWER), tasks("Q53", "Q54", "GENERAL"), true, null,
            "Ghi nhận bài làm bao phủ những yêu cầu có thể xác minh từ đề bài."),
    W_CLEAR_THESIS_OR_MAIN_IDEA("Luận điểm hoặc ý chính rõ ràng", "명확한 주제문", Polarity.STRENGTH,
            FindingCategory.CONTENT, scopes(EvidenceScope.WHOLE_ANSWER), tasks("Q54", "GENERAL"), true, null,
            "Ghi nhận luận điểm hoặc ý chính rõ ràng trong toàn bài."),
    W_RELEVANT_EXAMPLES_OR_REASONS("Lý do hoặc ví dụ phù hợp", "적절한 근거와 예시", Polarity.STRENGTH,
            FindingCategory.CONTENT, scopes(EvidenceScope.WHOLE_ANSWER), tasks("Q54", "GENERAL"), true, null,
            "Ghi nhận lý do hoặc ví dụ hỗ trợ trực tiếp cho ý chính."),
    W_ACCURATE_DATA_DESCRIPTION("Mô tả dữ liệu rõ ràng", "정확한 자료 설명", Polarity.STRENGTH,
            FindingCategory.CONTENT, scopes(EvidenceScope.TEXT_SPAN, EvidenceScope.WHOLE_ANSWER), tasks("Q53"), true, null,
            "Ghi nhận cách mô tả số liệu hoặc xu hướng hiển thị trong prompt; không suy diễn dữ liệu ngoài prompt."),
    W_LOGICAL_ORGANIZATION("Tổ chức bài logic", "논리적인 글의 구성", Polarity.STRENGTH,
            FindingCategory.ORGANIZATION, scopes(EvidenceScope.WHOLE_ANSWER), tasks("Q53", "Q54", "GENERAL"), true, null,
            "Ghi nhận trình tự và bố cục logic của toàn bài."),
    W_EFFECTIVE_TRANSITIONS("Chuyển ý hiệu quả", "효과적인 전환 표현", Polarity.STRENGTH,
            FindingCategory.ORGANIZATION, scopes(EvidenceScope.TEXT_SPAN, EvidenceScope.WHOLE_ANSWER), tasks("Q53", "Q54", "GENERAL"), true, null,
            "Ghi nhận từ nối hoặc cách chuyển ý giúp bài mạch lạc."),

    W_CLOZE_CONTEXT_FIT("Phù hợp ngữ cảnh chỗ trống", "빈칸 문맥 적합성", Polarity.STRENGTH,
            FindingCategory.CLOZE, scopes(EvidenceScope.TEXT_SPAN), tasks("Q51", "Q52"), true, null,
            "Ghi nhận câu hoàn thành phù hợp logic trước và sau chỗ trống."),
    W_CONNECTIVE_ENDING_ACCURACY("Vĩ tố liên kết chính xác", "연결 어미의 정확성", Polarity.STRENGTH,
            FindingCategory.CLOZE, scopes(EvidenceScope.TEXT_SPAN), tasks("Q51", "Q52"), true, null,
            "Ghi nhận vĩ tố liên kết chính xác trong câu hoàn thành."),
    W_SENTENCE_COMPLETION_NATURALNESS("Câu hoàn thành tự nhiên", "문장 완성의 자연스러움", Polarity.STRENGTH,
            FindingCategory.CLOZE, scopes(EvidenceScope.TEXT_SPAN), tasks("Q51", "Q52"), true, null,
            "Ghi nhận phần hoàn thành ngắn gọn và tự nhiên."),
    W_CLOZE_GRAMMAR_COMPATIBILITY("Ngữ pháp tương thích ngữ cảnh", "빈칸 문법 호응", Polarity.NEEDS_IMPROVEMENT,
            FindingCategory.CLOZE, scopes(EvidenceScope.TEXT_SPAN), tasks("Q51", "Q52"), true, null,
            "Chỉ ra cấu trúc ngữ pháp không tương thích với ngữ cảnh chỗ trống."),
    W_CLOZE_REGISTER_MATCH("Văn phong chỗ trống không phù hợp", "빈칸 문체 불일치", Polarity.NEEDS_IMPROVEMENT,
            FindingCategory.CLOZE, scopes(EvidenceScope.TEXT_SPAN), tasks("Q51", "Q52"), true, null,
            "Chỉ ra phần hoàn thành không khớp văn phong của ngữ cảnh."),

    W_TASK_REQUIREMENT_MISSING("Thiếu yêu cầu đề bài", "과제 요구 누락", Polarity.NEEDS_IMPROVEMENT,
            FindingCategory.CONTENT, scopes(EvidenceScope.WHOLE_ANSWER), tasks("Q53", "GENERAL"), true, null,
            "Chỉ ra yêu cầu hiển thị rõ trong prompt nhưng bài làm chưa đáp ứng; Q54 structured requirement checking được defer."),
    W_OFF_TOPIC_OR_WEAK_RELEVANCE("Lạc đề hoặc liên quan yếu", "주제 이탈 또는 연관성 부족", Polarity.NEEDS_IMPROVEMENT,
            FindingCategory.CONTENT, scopes(EvidenceScope.WHOLE_ANSWER), tasks("Q53", "Q54", "GENERAL"), true, null,
            "Chỉ ra toàn bài hoặc phần lớn nội dung không bám đề."),
    W_INSUFFICIENT_IDEA_DEVELOPMENT("Ý chưa được phát triển", "내용 전개 부족", Polarity.NEEDS_IMPROVEMENT,
            FindingCategory.CONTENT, scopes(EvidenceScope.WHOLE_ANSWER), tasks("Q54", "GENERAL"), true, null,
            "Chỉ ra ý chính thiếu lý do, giải thích hoặc ví dụ hỗ trợ."),
    W_UNSUPPORTED_CLAIM("Nhận định thiếu hỗ trợ", "근거가 부족한 주장", Polarity.NEEDS_IMPROVEMENT,
            FindingCategory.CONTENT, scopes(EvidenceScope.WHOLE_ANSWER), tasks("Q54", "GENERAL"), true, null,
            "Chỉ ra nhận định quan trọng không có lý do hoặc ví dụ hỗ trợ."),
    W_FABRICATED_OR_INACCURATE_DATA("Dữ liệu bịa đặt hoặc không chính xác", "조작되거나 부정확한 자료", Polarity.NEEDS_IMPROVEMENT,
            FindingCategory.CONTENT, scopes(EvidenceScope.TASK_METADATA), tasks("Q53"), false, null,
            "Chỉ dùng khi có dữ liệu nhiệm vụ có cấu trúc và có thể xác minh."),
    W_WEAK_PARAGRAPH_ORGANIZATION("Bố cục đoạn văn yếu", "단락 구성 미흡", Polarity.NEEDS_IMPROVEMENT,
            FindingCategory.ORGANIZATION, scopes(EvidenceScope.WHOLE_ANSWER), tasks("Q54", "GENERAL"), true, null,
            "Chỉ ra bố cục đoạn không hỗ trợ trình tự lập luận."),
    W_LOGICAL_FLOW_ISSUES("Mạch logic chưa rõ", "논리적 흐름 문제", Polarity.NEEDS_IMPROVEMENT,
            FindingCategory.ORGANIZATION, scopes(EvidenceScope.WHOLE_ANSWER), tasks("Q53", "Q54", "GENERAL"), true, null,
            "Chỉ ra trình tự ý bị đứt quãng hoặc đảo lộn."),
    W_TRANSITION_DEVICE_ISSUES("Chuyển ý chưa phù hợp", "전환 표현 문제", Polarity.NEEDS_IMPROVEMENT,
            FindingCategory.ORGANIZATION, scopes(EvidenceScope.TEXT_SPAN, EvidenceScope.WHOLE_ANSWER), tasks("Q53", "Q54", "GENERAL"), true, null,
            "Chỉ ra từ nối hoặc quan hệ chuyển ý không phù hợp."),
    W_Q53_DATA_FLOW_ISSUES("Trình tự mô tả dữ liệu chưa rõ", "Q53 자료 전개 문제", Polarity.NEEDS_IMPROVEMENT,
            FindingCategory.ORGANIZATION, scopes(EvidenceScope.WHOLE_ANSWER), tasks("Q53"), true, null,
            "Chỉ ra việc sắp xếp mô tả dữ liệu hoặc xu hướng thiếu logic."),

    W_VOCABULARY_ERRORS("Lỗi từ vựng", "어휘 오류", Polarity.NEEDS_IMPROVEMENT,
            FindingCategory.VOCABULARY, scopes(EvidenceScope.TEXT_SPAN), tasks("Q51", "Q52", "Q53", "Q54", "GENERAL"), true, null, "Bắt lỗi dùng từ sai nghĩa hoặc sai ngữ cảnh."),
    W_GRAMMAR_ERRORS("Lỗi ngữ pháp", "문법 오류", Polarity.NEEDS_IMPROVEMENT,
            FindingCategory.GRAMMAR, scopes(EvidenceScope.TEXT_SPAN), tasks("Q51", "Q52", "Q53", "Q54", "GENERAL"), true, null, "Bắt lỗi vĩ tố, thì hoặc cấu trúc ngữ pháp."),
    W_PARTICLE_ERRORS("Lỗi tiểu từ", "조사 오류", Polarity.NEEDS_IMPROVEMENT,
            FindingCategory.GRAMMAR, scopes(EvidenceScope.TEXT_SPAN), tasks("Q51", "Q52", "Q53", "Q54", "GENERAL"), true, null, "Bắt lỗi sai hoặc thiếu tiểu từ."),
    W_REPETITIVE_WORDS_EXPRESSIONS("Lặp từ và cụm từ", "반복 표현", Polarity.NEEDS_IMPROVEMENT,
            FindingCategory.VOCABULARY, scopes(EvidenceScope.TEXT_SPAN, EvidenceScope.WHOLE_ANSWER), tasks("Q53", "Q54", "GENERAL"), true, null, "Bắt việc lặp từ hoặc cấu trúc làm nghèo diễn đạt."),
    W_AWKWARD_UNNATURAL_EXPRESSIONS("Diễn đạt gượng gạo", "부자연스러운 표현", Polarity.NEEDS_IMPROVEMENT,
            FindingCategory.GENERAL_LANGUAGE, scopes(EvidenceScope.TEXT_SPAN), tasks("Q51", "Q52", "Q53", "Q54", "GENERAL"), true, null, "Bắt câu hoặc cụm từ thiếu tự nhiên."),
    W_SENTENCE_STRUCTURE_ISSUES("Vấn đề cấu trúc câu", "문장 구조 문제", Polarity.NEEDS_IMPROVEMENT,
            FindingCategory.GRAMMAR, scopes(EvidenceScope.TEXT_SPAN), tasks("Q51", "Q52", "Q53", "Q54", "GENERAL"), true, null, "Bắt câu thiếu thành phần hoặc có cấu trúc mất cân đối."),
    W_REGISTER_CONSISTENCY_ISSUES("Bất nhất quán văn phong", "문체 일관성 문제", Polarity.NEEDS_IMPROVEMENT,
            FindingCategory.REGISTER, scopes(EvidenceScope.TEXT_SPAN, EvidenceScope.WHOLE_ANSWER), tasks("Q51", "Q52", "Q53", "Q54", "GENERAL"), true, null, "Bắt lỗi trộn văn nói/văn viết hoặc đuôi câu không nhất quán."),
    W_SPELLING_SPACING_ERRORS("Lỗi chính tả và cách chữ", "맞춤법 및 띄어쓰기 오류", Polarity.NEEDS_IMPROVEMENT,
            FindingCategory.SPELLING_SPACING, scopes(EvidenceScope.TEXT_SPAN), tasks("Q51", "Q52", "Q53", "Q54", "GENERAL"), true, null, "Bắt lỗi chính tả, dấu câu hoặc cách chữ."),

    W_SENTENCE_VARIETY("Số lượng ký tự / dung lượng bài", "원고지 분량", Polarity.STRENGTH,
            FindingCategory.LENGTH, scopes(EvidenceScope.TEXT_SPAN), tasks("Q53", "Q54"), false, W_LENGTH_REQUIREMENT_MET, "Legacy: dung lượng bài, không phải sự đa dạng mẫu câu."),
    W_REGISTER_HONORIFIC_ACCURACY("Đồng nhất đuôi câu văn viết", "해라체/느낌체 일치", Polarity.STRENGTH,
            FindingCategory.REGISTER, scopes(EvidenceScope.TEXT_SPAN), tasks("Q51", "Q52", "Q53", "Q54", "GENERAL"), false, W_FORMAL_REGISTER_CONSISTENCY, "Legacy: written-register consistency."),
    W_APPROPRIATE_VOCABULARY_USAGE("Loại bỏ ngôn ngữ nói", "구어체 배제", Polarity.STRENGTH,
            FindingCategory.VOCABULARY, scopes(EvidenceScope.TEXT_SPAN), tasks("Q51", "Q52", "Q53", "Q54", "GENERAL"), false, W_FORMAL_VOCABULARY_USAGE, "Legacy: formal vocabulary usage."),
    W_WON_GO_JI("Quy tắc giấy kẻ ô Won-go-ji", "원고지 작성법", Polarity.STRENGTH,
            FindingCategory.FORMAT, scopes(EvidenceScope.TASK_METADATA), tasks("Q53", "Q54"), false, null, "Historical/read-only until authoritative format evidence exists.");

    public enum Polarity { STRENGTH, NEEDS_IMPROVEMENT }
    public enum FindingCategory { CONTENT, ORGANIZATION, GRAMMAR, VOCABULARY, REGISTER, SPELLING_SPACING, LENGTH, FORMAT, CLOZE, GENERAL_LANGUAGE }
    public enum EvidenceScope { TEXT_SPAN, WHOLE_ANSWER, TASK_METADATA }

    private static final String Q51 = "Q51";
    private static final String Q52 = "Q52";
    private static final String Q53 = "Q53";
    private static final String Q54 = "Q54";
    private static final String GENERAL = "GENERAL";

    private final String vietnameseLabel;
    private final String koreanLabel;
    private final Polarity polarity;
    private final FindingCategory category;
    private final Set<EvidenceScope> evidenceScopes;
    private final Set<String> taskTypes;
    private final boolean activeForProvider;
    private final WritingRubricCriterion canonicalCriterion;
    private final String rule;

    WritingRubricCriterion(String vietnameseLabel, String koreanLabel, Polarity polarity,
                           FindingCategory category, Set<EvidenceScope> evidenceScopes,
                           Set<String> taskTypes, boolean activeForProvider,
                           WritingRubricCriterion canonicalCriterion, String rule) {
        this.vietnameseLabel = vietnameseLabel;
        this.koreanLabel = koreanLabel;
        this.polarity = polarity;
        this.category = category;
        this.evidenceScopes = Set.copyOf(evidenceScopes);
        this.taskTypes = Set.copyOf(taskTypes);
        this.activeForProvider = activeForProvider;
        this.canonicalCriterion = canonicalCriterion == null ? this : canonicalCriterion;
        this.rule = rule;
    }

    public String id() { return name(); }
    public String canonicalId() { return canonicalCriterion.name(); }
    public String vietnameseLabel() { return vietnameseLabel; }
    public String koreanLabel() { return koreanLabel; }
    public Polarity polarity() { return polarity; }
    public FindingCategory category() { return category; }
    public Set<EvidenceScope> evidenceScopes() { return evidenceScopes; }
    public boolean supports(EvidenceScope scope) { return evidenceScopes.contains(scope); }
    public boolean appliesTo(String taskType) { return taskTypes.contains(normalizeTaskType(taskType)); }
    public boolean activeForProvider() { return activeForProvider; }
    public boolean legacyOnly() { return !activeForProvider; }
    public WritingRubricCriterion canonical() { return canonicalCriterion; }
    public double weight() { return 1.0; }
    public String rule() { return rule; }

    public static List<WritingRubricCriterion> activeForTask(String taskType) {
        return Arrays.stream(values()).filter(WritingRubricCriterion::activeForProvider)
                .filter(c -> c.appliesTo(taskType)).toList();
    }

    public static List<WritingRubricCriterion> strengths() {
        return Arrays.stream(values()).filter(c -> c.polarity == Polarity.STRENGTH).toList();
    }

    public static List<WritingRubricCriterion> needsImprovement() {
        return Arrays.stream(values()).filter(c -> c.polarity == Polarity.NEEDS_IMPROVEMENT).toList();
    }

    public static WritingRubricCriterion parse(String value) {
        if (value == null || value.isBlank()) return null;
        try { return valueOf(value.trim()); } catch (IllegalArgumentException ignored) { return null; }
    }

    private static String normalizeTaskType(String taskType) {
        if ("Q51_52".equals(taskType)) return Q51;
        return taskType == null ? GENERAL : taskType;
    }

    private static Set<EvidenceScope> scopes(EvidenceScope first, EvidenceScope... rest) {
        EnumSet<EvidenceScope> values = EnumSet.of(first, rest);
        return values;
    }

    private static Set<String> tasks(String... values) { return Set.of(values); }
}
