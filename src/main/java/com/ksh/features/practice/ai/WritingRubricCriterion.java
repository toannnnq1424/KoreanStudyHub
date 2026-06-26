package com.ksh.features.practice.ai;

import java.util.Arrays;
import java.util.List;

public enum WritingRubricCriterion {
    W_ADVANCED_GRAMMAR_STRUCTURES(
            "Cách chữ chính xác / ngữ pháp nâng cao",
            "띄어쓰기 및 고급 문법",
            Polarity.STRENGTH,
            "Nhận diện cách chữ đúng ở cụm khó như 할 수 있다, 할 뿐이다, hoặc cấu trúc ngữ pháp trung-cao cấp được dùng chính xác."),
    W_REGISTER_HONORIFIC_ACCURACY(
            "Đồng nhất đuôi câu văn viết",
            "해라체/느냐체 일치",
            Polarity.STRENGTH,
            "Ghi nhận văn phong viết nghị luận nhất quán, đặc biệt đuôi -ㄴ다/는다, -았다/었다, -기 마련이다 không bị lẫn văn nói."),
    W_APPROPRIATE_VOCABULARY_USAGE(
            "Loại bỏ ngôn ngữ nói",
            "구어체 배제",
            Polarity.STRENGTH,
            "Khen khi người học dùng từ văn viết trang trọng thay cho khẩu ngữ như 매우, 무척, 상당히, 대단히."),
    W_TOPIC_SPECIFIC_EXPRESSIONS(
            "Từ vựng & cụm từ cố định theo chủ đề",
            "어휘 및 연어",
            Polarity.STRENGTH,
            "Đánh giá cao từ Hán-Hàn/collocation trung-cao cấp đúng chủ đề như 소득 창출, 인구 감소 현상, 상호 이해 증진."),
    W_NATURAL_KOREAN_EXPRESSIONS(
            "Diễn đạt lại tự nhiên",
            "제시문 변형 표현",
            Polarity.STRENGTH,
            "Ghi nhận khả năng paraphrase đề bài bằng từ vựng/ngữ pháp cá nhân thay vì chép nguyên văn."),
    W_WON_GO_JI(
            "Quy tắc giấy kẻ ô Won-go-ji",
            "원고지 작성법",
            Polarity.STRENGTH,
            "Chỉ dùng khi có bằng chứng về lùi đầu dòng, dấu câu, số hoặc chữ tiếng Anh theo chuẩn 원고지."),
    W_SENTENCE_VARIETY(
            "Số lượng ký tự / dung lượng bài",
            "원고지 분량",
            Polarity.STRENGTH,
            "Khen khi bài đạt đúng phạm vi ký tự của dạng câu hỏi, ví dụ 200-300자 cho câu 53 hoặc 600-700자 cho câu 54."),

    W_VOCABULARY_ERRORS(
            "Lỗi từ vựng",
            "어휘 오류",
            Polarity.NEEDS_IMPROVEMENT,
            "Bắt lỗi dùng từ sai ngữ cảnh, sai nghĩa, hoặc dùng từ quá sơ cấp ở vị trí cần văn viết nghị luận."),
    W_GRAMMAR_ERRORS(
            "Lỗi ngữ pháp",
            "문법 오류",
            Polarity.NEEDS_IMPROVEMENT,
            "Bắt lỗi vĩ tố liên kết, chia thì, cấu trúc ngữ pháp, bao gồm 호응 오류, 피동/사동 오류, 시제 오류, 존칭 오류."),
    W_PARTICLE_ERRORS(
            "Lỗi tiểu từ",
            "조사 오류",
            Polarity.NEEDS_IMPROVEMENT,
            "Bắt lỗi sai hoặc thiếu 이/가, 을/를, 은/는, 에/에서 và các tiểu từ làm đổi nghĩa câu."),
    W_REPETITIVE_WORDS_EXPRESSIONS(
            "Lặp từ và cụm từ diễn đạt",
            "반복 표현",
            Polarity.NEEDS_IMPROVEMENT,
            "Bắt lỗi lặp cùng từ, cụm từ, đuôi câu hoặc cấu trúc quá nhiều khiến bài nghèo diễn đạt."),
    W_AWKWARD_UNNATURAL_EXPRESSIONS(
            "Diễn đạt gượng gạo, thiếu tự nhiên",
            "부자연스러운 표현",
            Polarity.NEEDS_IMPROVEMENT,
            "Bắt câu dịch thô từ tiếng Việt sang tiếng Hàn, nghe gượng dù không nhất thiết sai ngữ pháp rõ ràng."),
    W_SENTENCE_STRUCTURE_ISSUES(
            "Vấn đề về cấu trúc câu",
            "문장 구조 문제",
            Polarity.NEEDS_IMPROVEMENT,
            "Bắt câu quá dài, thiếu chủ ngữ/vị ngữ, vế câu bất đối xứng hoặc câu đứt mạch."),
    W_REGISTER_CONSISTENCY_ISSUES(
            "Bất nhất quán trong văn phong",
            "문체 일관성 문제",
            Polarity.NEEDS_IMPROVEMENT,
            "Bắt lỗi trộn văn nói/văn viết, đuôi 해요체 trong bài nghị luận, từ khẩu ngữ hoặc ký hiệu không phù hợp."),
    W_SPELLING_SPACING_ERRORS(
            "Lỗi chính tả & cách chữ",
            "맞춤법 및 띄어쓰기 오류",
            Polarity.NEEDS_IMPROVEMENT,
            "Bắt lỗi 맞춤법, 받침, dấu câu, hoặc 띄어쓰기 như 할수있다 thay vì 할 수 있다.");

    public enum Polarity {
        STRENGTH,
        NEEDS_IMPROVEMENT
    }

    private final String vietnameseLabel;
    private final String koreanLabel;
    private final Polarity polarity;
    private final String rule;

    WritingRubricCriterion(String vietnameseLabel, String koreanLabel, Polarity polarity, String rule) {
        this.vietnameseLabel = vietnameseLabel;
        this.koreanLabel = koreanLabel;
        this.polarity = polarity;
        this.rule = rule;
    }

    public String id() {
        return name();
    }

    public String vietnameseLabel() {
        return vietnameseLabel;
    }

    public String koreanLabel() {
        return koreanLabel;
    }

    public Polarity polarity() {
        return polarity;
    }

    public double weight() {
        return 1.0;
    }

    public String rule() {
        return rule;
    }

    public static List<WritingRubricCriterion> strengths() {
        return Arrays.stream(values())
                .filter(c -> c.polarity == Polarity.STRENGTH)
                .toList();
    }

    public static List<WritingRubricCriterion> needsImprovement() {
        return Arrays.stream(values())
                .filter(c -> c.polarity == Polarity.NEEDS_IMPROVEMENT)
                .toList();
    }

    public static WritingRubricCriterion parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
