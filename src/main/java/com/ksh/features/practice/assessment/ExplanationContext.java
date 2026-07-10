package com.ksh.features.practice.assessment;

public record ExplanationContext(
        String schemaVersion,
        Long questionId,
        Long questionVersionId,
        Integer questionNo,
        String programCode,
        AssessmentSkill skill,
        CanonicalQuestionType questionType,
        String prompt,
        QuestionContent questionContent,
        AnswerSpec answerSpec,
        LearnerAnswer learnerAnswer,
        AssessmentStimulus stimulus,
        String teacherExplanation,
        String explanationLanguage,
        String optionLabelMode,
        ProfileReference promptProfile
) {
    public static final String SCHEMA_VERSION = "explanation-context-v1";

    public ExplanationContext {
        schemaVersion = schemaVersion == null ? SCHEMA_VERSION : schemaVersion;
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported explanation context schema: " + schemaVersion);
        }
        if (questionId == null || programCode == null || programCode.isBlank()
                || skill == null || questionType == null || prompt == null
                || questionContent == null || answerSpec == null || stimulus == null) {
            throw new IllegalArgumentException("Explanation context is incomplete");
        }
        if (answerSpec.questionType() != questionType) {
            throw new IllegalArgumentException("Explanation answer spec type does not match question type");
        }
        explanationLanguage = explanationLanguage == null || explanationLanguage.isBlank()
                ? "vi"
                : explanationLanguage;
        optionLabelMode = optionLabelMode == null || optionLabelMode.isBlank()
                ? "NUMERIC"
                : optionLabelMode;
    }
}
