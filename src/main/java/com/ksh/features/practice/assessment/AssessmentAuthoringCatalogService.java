package com.ksh.features.practice.assessment;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compatibility facade for authoring consumers. The catalog is code-owned and
 * no longer resolves certificate, program, template, or profile rows from DB.
 */
@Service
public class AssessmentAuthoringCatalogService {

    public static final String SCHEMA_VERSION = "practice-authoring-catalog-v2";
    public static final String DEFAULT_TEMPLATE_CODE = "KSH_DEFAULT";

    private final PracticeContentRules rules;
    private final ExamTemplatePolicy defaultTemplate;

    public AssessmentAuthoringCatalogService(PracticeContentRules rules) {
        this.rules = rules;
        this.defaultTemplate = buildDefaultTemplate();
    }

    public AuthoringCatalog catalog() {
        return new AuthoringCatalog(SCHEMA_VERSION, List.of(defaultTemplate));
    }

    public ExamTemplatePolicy defaultTemplate() {
        return defaultTemplate;
    }

    private ExamTemplatePolicy buildDefaultTemplate() {
        Map<String, SkillAuthoringPolicy> skills = new LinkedHashMap<>();
        for (AssessmentSkill skill : AssessmentSkill.values()) {
            Map<String, QuestionAuthoringPolicy> questionPolicies = new LinkedHashMap<>();
            for (CanonicalQuestionType type : rules.allowedTypes(skill)) {
                ScoringPolicyCode scoringPolicy = rules.scoringPolicy(type);
                questionPolicies.put(type.name(), new QuestionAuthoringPolicy(
                        type.name(),
                        scoringPolicy.name(),
                        List.of(scoringPolicy.name()),
                        rules.minOptions(type),
                        rules.maxOptions(type)
                ));
            }
            skills.put(skill.name(), new SkillAuthoringPolicy(
                    defaultDuration(skill),
                    defaultPoints(skill),
                    questionPolicies.keySet().stream().toList(),
                    true,
                    Map.copyOf(questionPolicies),
                    true
            ));
        }
        return new ExamTemplatePolicy(
                DEFAULT_TEMPLATE_CODE,
                "Đề luyện tập KSH",
                Map.copyOf(skills)
        );
    }

    private static int defaultDuration(AssessmentSkill skill) {
        return switch (skill) {
            case READING, LISTENING -> 40;
            case WRITING -> 50;
            case SPEAKING -> 30;
        };
    }

    private static BigDecimal defaultPoints(AssessmentSkill skill) {
        return switch (skill) {
            case WRITING, SPEAKING -> BigDecimal.valueOf(100);
            case READING, LISTENING -> BigDecimal.ONE;
        };
    }

    public record AuthoringCatalog(String schemaVersion, List<ExamTemplatePolicy> templates) {
        public AuthoringCatalog {
            templates = templates == null ? List.of() : List.copyOf(templates);
        }
    }

    public record ExamTemplatePolicy(
            String code,
            String displayName,
            Map<String, SkillAuthoringPolicy> skills
    ) {
        public ExamTemplatePolicy {
            skills = skills == null ? Map.of() : Map.copyOf(skills);
        }

        public SkillAuthoringPolicy requireSkill(String rawSkill) {
            String skill = rawSkill == null ? "" : rawSkill.trim().toUpperCase(Locale.ROOT);
            SkillAuthoringPolicy policy = skills.get(skill);
            if (policy == null) {
                throw new IllegalArgumentException("Unsupported KSH practice skill: " + skill);
            }
            return policy;
        }
    }

    public record SkillAuthoringPolicy(
            Integer durationMinutes,
            BigDecimal defaultPoints,
            List<String> questionTypes,
            boolean pointsEditable,
            Map<String, QuestionAuthoringPolicy> questionPolicies,
            boolean excelImportEnabled
    ) {
        public SkillAuthoringPolicy(Integer durationMinutes,
                                    BigDecimal defaultPoints,
                                    List<String> questionTypes) {
            this(durationMinutes, defaultPoints, questionTypes, true, Map.of(), true);
        }

        public SkillAuthoringPolicy {
            questionTypes = questionTypes == null ? List.of() : List.copyOf(questionTypes);
            questionPolicies = questionPolicies == null ? Map.of() : Map.copyOf(questionPolicies);
        }

        public QuestionAuthoringPolicy questionPolicy(String rawType) {
            return questionPolicies.get(rawType == null ? "" : rawType.trim().toUpperCase(Locale.ROOT));
        }
    }

    public record QuestionAuthoringPolicy(
            String questionType,
            String defaultScoringPolicyCode,
            List<String> allowedScoringPolicyCodes,
            Integer minOptions,
            Integer maxOptions
    ) {
        public QuestionAuthoringPolicy {
            allowedScoringPolicyCodes = allowedScoringPolicyCodes == null
                    ? List.of()
                    : List.copyOf(allowedScoringPolicyCodes);
            minOptions = minOptions == null || minOptions < 0 ? 0 : minOptions;
            maxOptions = maxOptions == null || maxOptions < minOptions ? minOptions : maxOptions;
        }
    }
}
