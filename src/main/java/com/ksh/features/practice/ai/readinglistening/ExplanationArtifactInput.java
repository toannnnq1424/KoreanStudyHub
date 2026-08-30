package com.ksh.features.practice.ai.readinglistening;

import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentSkill;
import com.ksh.features.practice.assessment.AssessmentStimulus;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.ObjectiveExplanationStrategyRegistry;
import com.ksh.features.practice.assessment.QuestionContent;

import java.util.List;

public record ExplanationArtifactInput(
        String schemaVersion,
        AssessmentSkill skill,
        CanonicalQuestionType questionType,
        String prompt,
        String instruction,
        QuestionContent questionContent,
        AnswerSpec answerSpec,
        AssessmentStimulus stimulus,
        String teacherExplanation,
        String optionLabelMode,
        String explanationLanguage,
        ObjectiveExplanationStrategyRegistry.Selection explanationStrategy,
        List<MediaDescriptor> media,
        String readinessIssue
) {
    public static final String SCHEMA_VERSION = "rl-explanation-input-v3";

    public ExplanationArtifactInput {
        schemaVersion = schemaVersion == null ? SCHEMA_VERSION : schemaVersion;
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported explanation input schema: " + schemaVersion);
        }
        if (skill == null || questionType == null || prompt == null
                || questionContent == null || answerSpec == null || stimulus == null) {
            throw new IllegalArgumentException("Explanation artifact input is incomplete");
        }
        instruction = instruction == null ? "" : instruction;
        teacherExplanation = teacherExplanation == null ? "" : teacherExplanation;
        optionLabelMode = optionLabelMode == null || optionLabelMode.isBlank()
                ? "NUMERIC"
                : optionLabelMode;
        explanationLanguage = explanationLanguage == null || explanationLanguage.isBlank()
                ? "vi"
                : explanationLanguage;
        if (readinessIssue == null || readinessIssue.isBlank()) {
            ObjectiveExplanationStrategyRegistry.requireAllowed(
                    questionType,
                    explanationStrategy,
                    stimulus,
                    questionContent,
                    answerSpec);
        } else {
            // A terminal readiness artifact still needs canonical question and
            // answer authority for a stable fingerprint. Missing approved
            // evidence is precisely what this state records, so do not reject
            // it again at the runtime-evidence boundary.
            ObjectiveExplanationStrategyRegistry.requireAllowed(
                    questionType,
                    explanationStrategy,
                    questionContent,
                    answerSpec);
        }
        media = media == null ? List.of() : List.copyOf(media);
    }

    public record MediaDescriptor(
            String role,
            String kind,
            String sha256,
            String mimeType,
            Long sizeBytes
    ) {
    }
}
