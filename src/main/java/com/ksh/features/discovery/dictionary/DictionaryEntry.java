package com.ksh.features.discovery.dictionary;

public record DictionaryEntry(
        String targetCode,
        String word,
        String pronunciation,
        String partOfSpeech,
        String wordLevel,
        String meaningVi,
        String dictionaryUrl
) {
}
