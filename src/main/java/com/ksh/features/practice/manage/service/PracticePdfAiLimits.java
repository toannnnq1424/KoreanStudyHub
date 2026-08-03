package com.ksh.features.practice.manage.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PracticePdfAiLimits {

    private final int maxSelectedPages;
    private final int maxTextCharacters;

    public PracticePdfAiLimits(
            @Value("${app.practice.pdf-ai.max-selected-pages:50}")
            int maxSelectedPages,
            @Value("${app.practice.pdf-ai.max-text-characters:1000000}")
            int maxTextCharacters) {
        this.maxSelectedPages = bounded(maxSelectedPages, 1, 200);
        this.maxTextCharacters = bounded(maxTextCharacters, 10_000, 2_000_000);
    }

    public int maxSelectedPages() {
        return maxSelectedPages;
    }

    public int maxTextCharacters() {
        return maxTextCharacters;
    }

    private static int bounded(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

}
