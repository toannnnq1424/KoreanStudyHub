package com.ksh.features.practice.assessment;

public enum CanonicalQuestionType {
    SINGLE_CHOICE(true),
    MULTIPLE_CHOICE(true),
    TRUE_FALSE_NOT_GIVEN(true),
    FILL_BLANK(true),
    MATCHING(true),
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
