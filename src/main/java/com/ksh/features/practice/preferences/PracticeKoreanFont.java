package com.ksh.features.practice.preferences;

import java.util.List;

/**
 * Server-owned allowlist for Korean learning-content typography.
 *
 * <p>The values are presentation preferences only. They never participate in
 * answer text, evidence offsets, immutable artifact identities, or scoring.
 */
public enum PracticeKoreanFont {
    NANUM_MYEONGJO("Nanum Myeongjo", "Serif",
            "Serif rõ nét, phù hợp đọc đoạn dài"),
    DIPHYLLEIA("Diphylleia", "Serif",
            "Serif thanh, giàu khoảng thở"),
    GOWUN_BATANG("Gowun Batang", "Serif",
            "Batang mở, dễ đọc trên màn hình"),
    NOTO_SERIF_KR("Noto Serif KR", "Serif",
            "Serif trung tính, phủ glyph rộng"),

    NANUM_GOTHIC("Nanum Gothic", "Sans",
            "Sans gọn, quen mắt trên màn hình"),
    GOTHIC_A1("Gothic A1", "Sans",
            "Sans trung tính cho đoạn văn dài"),
    GOWUN_DODUM("Gowun Dodum", "Sans",
            "Dodum mềm, nét đều và thân thiện"),
    ORBIT("Orbit", "Sans",
            "Nét hình học gọn và khác biệt"),
    SUNFLOWER("Sunflower", "Sans",
            "Nét mềm, cân bằng khi đọc câu vừa"),

    BLACK_AND_WHITE_PICTURE("Black And White Picture", "Display",
            "Display thô mộc, tương phản cao"),
    GUGI("Gugi", "Display",
            "Hình học cá tính, dễ phân biệt tiêu đề"),
    POOR_STORY("Poor Story", "Display",
            "Nét truyện tranh tự nhiên"),
    SINGLE_DAY("Single Day", "Display",
            "Nét mảnh, ngẫu hứng cho câu ngắn"),

    GAEGU("Gaegu", "Viết tay",
            "Nét viết tay, tạo cảm giác gần gũi"),
    HI_MELODY("Hi Melody", "Viết tay",
            "Nét viết tay nhẹ, khoảng chữ thoáng"),
    NANUM_GOTHIC_CODING("Nanum Gothic Coding", "Viết tay",
            "Nét đơn cách, dễ phân biệt ký tự"),
    NANUM_PEN_SCRIPT("Nanum Pen Script", "Viết tay",
            "Nét bút bi tự nhiên, gần vở học");

    public static final PracticeKoreanFont DEFAULT = NANUM_MYEONGJO;
    public static final List<PracticeKoreanFont> ALLOWED = List.of(values());

    private final String label;
    private final String categoryLabel;
    private final String description;

    PracticeKoreanFont(
            String label,
            String categoryLabel,
            String description) {
        this.label = label;
        this.categoryLabel = categoryLabel;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String categoryLabel() {
        return categoryLabel;
    }

    public String description() {
        return description;
    }
}
