package com.ksh.features.discovery.entity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum NewsCategory {
    CULTURE("Văn hóa", "culture", "문"),
    FOOD("Ẩm thực", "food", "맛"),
    ENTERTAINMENT("Phim ảnh", "entertainment", "영"),
    SCHOLARSHIP("Học bổng", "scholarship", "꿈");

    private final String label;
    private final String slug;
    private final String glyph;

    NewsCategory(String label, String slug, String glyph) {
        this.label = label;
        this.slug = slug;
        this.glyph = glyph;
    }

    public String getLabel() {
        return label;
    }

    public String getSlug() {
        return slug;
    }

    public String getGlyph() {
        return glyph;
    }

    public static Optional<NewsCategory> fromSlug(String value) {
        if (value == null || value.isBlank() || "all".equalsIgnoreCase(value)) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(category -> category.slug.equals(normalized)
                        || category.name().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }
}
