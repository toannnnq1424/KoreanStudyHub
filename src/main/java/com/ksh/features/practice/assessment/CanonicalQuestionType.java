package com.ksh.features.practice.assessment;

public enum CanonicalQuestionType {
    SINGLE_CHOICE(true),
    MULTIPLE_ANSWER(true),
    MATCHING(true),
    TRUE_FALSE_NOT_GIVEN(true),
    FILL_BLANK(true),
    ESSAY(false),
    SPEAKING(false);

    private final boolean objective;

    CanonicalQuestionType(boolean objective) {
        this.objective = objective;
    }

    public boolean isObjective() {
        return objective;
    }
}
