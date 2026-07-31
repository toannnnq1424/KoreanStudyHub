package com.ksh.features.ai.flashcardgen;

import com.ksh.features.ai.questiongen.DocumentTextExtractor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Narrows uploaded learning material before it reaches the flashcard prompt.
 *
 * <p>The shared {@link DocumentTextExtractor} deliberately returns document order.
 * That is appropriate for question generation, but a TOPIK booklet starts with a
 * cover, administrative rules and listening pages. Flashcard generation should begin
 * at the reading section instead of spending its context window on those pages.
 */
@Component
public class KoreanFlashcardMaterialSelector {

    private static final Pattern TOPIK_READING_START = Pattern.compile(
            "(?is)TOPIK\\s*[ⅠI1]*\\s*읽기\\s*\\(\\s*31\\s*번");
    private static final Pattern READING_RANGE_START = Pattern.compile(
            "(?is)읽기\\s*\\(\\s*31\\s*번\\s*[～~\\-]\\s*70\\s*번");
    private static final Pattern TOPIK_HEADER_LINE = Pattern.compile(
            "(?m)^\\s*제\\s*\\d+\\s*회[^\\r\\n]*한국어능력시험[^\\r\\n]*$");
    private static final Pattern STANDALONE_PAGE_NUMBER = Pattern.compile(
            "(?m)^\\s*\\d{1,3}\\s*$");
    private static final Pattern EXCESS_BLANK_LINES = Pattern.compile("(\\R\\s*){3,}");

    private final DocumentTextExtractor textExtractor;

    public KoreanFlashcardMaterialSelector(DocumentTextExtractor textExtractor) {
        this.textExtractor = textExtractor;
    }

    public String select(MultipartFile file, String pastedText) {
        String material = file != null && !file.isEmpty()
                ? textExtractor.extract(file)
                : textExtractor.normalizePastedText(pastedText);
        return focusLearningContent(material);
    }

    static String focusLearningContent(String material) {
        String focused = material;
        int readingStart = findReadingStart(focused);
        if (readingStart >= 0) {
            focused = focused.substring(readingStart);
        }
        focused = TOPIK_HEADER_LINE.matcher(focused).replaceAll("");
        focused = STANDALONE_PAGE_NUMBER.matcher(focused).replaceAll("");
        focused = EXCESS_BLANK_LINES.matcher(focused).replaceAll(System.lineSeparator());
        return focused.trim();
    }

    private static int findReadingStart(String material) {
        Matcher exact = TOPIK_READING_START.matcher(material);
        if (exact.find()) {
            return exact.start();
        }
        Matcher range = READING_RANGE_START.matcher(material);
        return range.find() ? range.start() : -1;
    }
}
