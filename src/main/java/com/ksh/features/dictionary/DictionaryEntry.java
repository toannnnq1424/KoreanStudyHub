package com.ksh.features.dictionary;

/**
 * A Korean Basic Dictionary result used by the shared lookup and Flashcard flow.
 *
 * <p>This is intentionally independent of any content/discovery surface so a
 * selected Korean word can be looked up anywhere in the non-Practice product.
 */
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
