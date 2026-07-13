package com.ksh.features.practice.assessment;

import com.ksh.entities.WritingTaskType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PracticeContentRules {

    public static final String RULES_VERSION = "ksh-practice-r1";
    public static final int MIN_SINGLE_CHOICE_OPTIONS = 2;
    public static final int MAX_SINGLE_CHOICE_OPTIONS = 8;

    private static final Map<AssessmentSkill, List<CanonicalQuestionType>> ALLOWED_TYPES = allowedTypes();
    private static final List<WritingTaskType> REQUIRED_WRITING_TASKS =
            List.of(WritingTaskType.Q51, WritingTaskType.Q52,
                    WritingTaskType.Q53, WritingTaskType.Q54);

    public List<CanonicalQuestionType> allowedTypes(AssessmentSkill skill) {
        require(skill, "practice skill");
        return ALLOWED_TYPES.getOrDefault(skill, List.of());
    }

    public void requireAllowed(AssessmentSkill skill, CanonicalQuestionType type) {
        require(skill, "practice skill");
        require(type, "question type");
        if (!ALLOWED_TYPES.getOrDefault(skill, List.of()).contains(type)) {
            throw new IllegalArgumentException(
                    "Question type " + type + " is not allowed for skill " + skill);
        }
    }

    public ScoringPolicyCode scoringPolicy(CanonicalQuestionType type) {
        require(type, "question type");
        return switch (type) {
            case SINGLE_CHOICE, TRUE_FALSE_NOT_GIVEN -> ScoringPolicyCode.ALL_OR_NOTHING;
            case FILL_BLANK -> ScoringPolicyCode.NORMALIZED_EXACT;
            case ESSAY, SPEAKING -> ScoringPolicyCode.PROFILE_BASED;
        };
    }

    public int minOptions(CanonicalQuestionType type) {
        return type == CanonicalQuestionType.SINGLE_CHOICE ? MIN_SINGLE_CHOICE_OPTIONS : 0;
    }

    public int maxOptions(CanonicalQuestionType type) {
        return type == CanonicalQuestionType.SINGLE_CHOICE ? MAX_SINGLE_CHOICE_OPTIONS : 0;
    }

    public Set<WritingTaskType> requiredWritingTasks() {
        return Set.copyOf(REQUIRED_WRITING_TASKS);
    }

    public List<WritingTaskType> requiredWritingTasksInOrder() {
        return REQUIRED_WRITING_TASKS;
    }

    public int writingQuestionNumber(WritingTaskType taskType) {
        require(taskType, "writing task type");
        if (!REQUIRED_WRITING_TASKS.contains(taskType)) {
            throw new IllegalArgumentException("Writing chỉ hỗ trợ Q51, Q52, Q53 và Q54.");
        }
        return Integer.parseInt(taskType.name().substring(1));
    }

    private static Map<AssessmentSkill, List<CanonicalQuestionType>> allowedTypes() {
        Map<AssessmentSkill, List<CanonicalQuestionType>> rules = new EnumMap<>(AssessmentSkill.class);
        rules.put(AssessmentSkill.READING, List.of(
                CanonicalQuestionType.SINGLE_CHOICE,
                CanonicalQuestionType.FILL_BLANK,
                CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN));
        rules.put(AssessmentSkill.LISTENING, List.of(
                CanonicalQuestionType.SINGLE_CHOICE,
                CanonicalQuestionType.FILL_BLANK,
                CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN));
        rules.put(AssessmentSkill.WRITING, List.of(CanonicalQuestionType.ESSAY));
        rules.put(AssessmentSkill.SPEAKING, List.of(CanonicalQuestionType.SPEAKING));
        return Map.copyOf(rules);
    }

    private static <T> T require(T value, String label) {
        if (value == null) {
            throw new IllegalArgumentException("Missing " + label);
        }
        return value;
    }
}
