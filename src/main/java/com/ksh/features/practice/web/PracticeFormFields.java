package com.ksh.features.practice.web;

/**
 * Form field names/prefixes used by the practice player and submit/save paths.
 */
public final class PracticeFormFields {
    public static final String SECTION_ID = "sectionId";
    public static final String MODE = "mode";
    public static final String ANSWER_PREFIX = "answer_";

    private PracticeFormFields() {
    }

    public static String answerKey(Long questionId) {
        return ANSWER_PREFIX + questionId;
    }

    public static boolean isAnswerField(String key) {
        return key != null && key.startsWith(ANSWER_PREFIX);
    }

    public static String questionIdFromAnswerField(String key) {
        if (!isAnswerField(key)) {
            throw new IllegalArgumentException("Not a practice answer field");
        }
        return key.substring(ANSWER_PREFIX.length());
    }
}
