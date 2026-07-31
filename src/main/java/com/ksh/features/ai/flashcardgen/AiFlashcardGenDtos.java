package com.ksh.features.ai.flashcardgen;

import java.util.List;

public final class AiFlashcardGenDtos {

    private AiFlashcardGenDtos() {
    }

    public record GenerateRequest(int count, String language) {
    }

    public record GeneratedCardRow(String front, String back) {
    }

    public record GenerateResult(List<GeneratedCardRow> cards, int count) {
    }
}
