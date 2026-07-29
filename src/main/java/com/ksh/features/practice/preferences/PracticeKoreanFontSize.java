package com.ksh.features.practice.preferences;

import java.util.List;

/**
 * Server-owned allowlist for Korean learning-content text scaling.
 *
 * <p>The scale changes presentation only. It never changes stored text,
 * normalization, evidence offsets, answer identity, or scoring.
 */
public enum PracticeKoreanFontSize {
    DEFAULT("Mặc định", "100%"),
    LARGE("Lớn", "115%"),
    EXTRA_LARGE("Rất lớn", "130%");

    public static final PracticeKoreanFontSize DEFAULT_VALUE = DEFAULT;
    public static final List<PracticeKoreanFontSize> ALLOWED = List.of(values());

    private final String label;
    private final String percentLabel;

    PracticeKoreanFontSize(String label, String percentLabel) {
        this.label = label;
        this.percentLabel = percentLabel;
    }

    public String label() {
        return label;
    }

    public String percentLabel() {
        return percentLabel;
    }
}
