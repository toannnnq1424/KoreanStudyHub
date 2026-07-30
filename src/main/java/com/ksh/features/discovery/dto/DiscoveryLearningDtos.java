package com.ksh.features.discovery.dto;

public final class DiscoveryLearningDtos {

    private DiscoveryLearningDtos() {
    }

    public record DictionaryLookup(
            boolean found,
            boolean dictionaryConfigured,
            String word,
            String pronunciation,
            String meaningVi,
            String partOfSpeech,
            String dictionaryUrl
    ) {
    }

    public record SaveVocabularyRequest(
            Long deckId,
            String word,
            String pronunciation,
            String meaningVi,
            String partOfSpeech,
            String dictionaryUrl
    ) {
    }

    public record SaveVocabularyResult(
            Long deckId,
            Long cardId,
            String deckTitle,
            String deckUrl,
            boolean alreadySaved
    ) {
    }
}
