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

    public static String writingBlankAnswerKey(
            Long questionId, String blankId) {
        if (questionId == null || questionId <= 0
                || blankId == null
                || !blankId.matches("q5[12]-b[12]")) {
            throw new IllegalArgumentException(
                    "Invalid Writing blank form identity");
        }
        return ANSWER_PREFIX + questionId + "__blank_" + blankId;
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
