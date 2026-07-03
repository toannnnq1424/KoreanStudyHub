package com.ksh.entities;

public enum PracticeCategory {
    TOPIK_I("TOPIK I"),
    TOPIK_II("TOPIK II"),
    EXTENDED_PRACTICE("Bài tập mở rộng"),
    GENERAL_KOREAN("Tiếng Hàn tổng hợp"),
    CUSTOM("Tự cấu hình"),
    UNCLASSIFIED("Chưa phân loại");

    private final String label;

    PracticeCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static PracticeCategory fromString(String val) {
        if (val == null || val.isBlank()) {
            return UNCLASSIFIED;
        }
        try {
            return PracticeCategory.valueOf(val.trim());
        } catch (IllegalArgumentException e) {
            return UNCLASSIFIED;
        }
    }
}
