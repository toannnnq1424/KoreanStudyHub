package com.ksh.features.practice.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.assessment.persistence.AssessmentExamTemplate;
import com.ksh.features.practice.assessment.persistence.AssessmentProgramVersion;
import com.ksh.features.practice.assessment.persistence.AssessmentExamTemplateVersion;
import com.ksh.features.practice.assessment.repository.AssessmentExamTemplateRepository;
import com.ksh.features.practice.assessment.repository.AssessmentExamTemplateVersionRepository;
import com.ksh.features.practice.assessment.repository.AssessmentProgramVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AssessmentAuthoringCatalogService {

    public static final String SCHEMA_VERSION = "assessment-authoring-catalog-v1";

    private final AssessmentExamTemplateRepository templateRepository;
    private final AssessmentExamTemplateVersionRepository templateVersionRepository;
    private final AssessmentProgramVersionRepository programVersionRepository;
    private final AssessmentProgramPolicyService policyService;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public AssessmentAuthoringCatalogService(
            AssessmentExamTemplateRepository templateRepository,
            AssessmentExamTemplateVersionRepository templateVersionRepository,
            AssessmentProgramVersionRepository programVersionRepository,
            AssessmentProgramPolicyService policyService,
            ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.templateVersionRepository = templateVersionRepository;
        this.programVersionRepository = programVersionRepository;
        this.policyService = policyService;
        this.objectMapper = objectMapper;
    }

    public AssessmentAuthoringCatalogService(
            AssessmentExamTemplateRepository templateRepository,
            AssessmentProgramVersionRepository programVersionRepository,
            AssessmentProgramPolicyService policyService,
            ObjectMapper objectMapper) {
        this(templateRepository, null, programVersionRepository, policyService, objectMapper);
    }

    @Transactional(readOnly = true)
    public AuthoringCatalog catalog() {
        List<ExamTemplatePolicy> templates = templateRepository.findByEnabledTrueOrderByDisplayNameAsc().stream()
                .map(this::toPolicy)
                .toList();
        return new AuthoringCatalog(SCHEMA_VERSION, templates);
    }

    @Transactional(readOnly = true)
    public ExamTemplatePolicy requireTemplate(String code) {
        String normalized = normalizeTemplateCode(code);
        return templateRepository.findByCodeAndEnabledTrue(normalized)
                .map(this::toPolicy)
                .orElseThrow(() -> new IllegalArgumentException("Exam template is not enabled: " + normalized));
    }

    public static String defaultTemplateForCategory(String category) {
        if (category == null) {
            return "CUSTOM_FLEXIBLE";
        }
        return switch (category.trim().toUpperCase(Locale.ROOT)) {
            case "TOPIK_I" -> "TOPIK_I";
            case "TOPIK_II", "TOPIK_MIXED" -> "TOPIK_II";
            default -> "CUSTOM_FLEXIBLE";
        };
    }

    public static String normalizeTemplateCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Exam template code is required");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private ExamTemplatePolicy toPolicy(AssessmentExamTemplate template) {
        AssessmentProgramVersion version = programVersionRepository.findById(template.getProgramVersionId())
                .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
                .orElseThrow(() -> new IllegalStateException(
                        "Exam template references an inactive program version: " + template.getCode()));
        JsonNode config;
        try {
            config = objectMapper.readTree(activeTemplateConfig(template));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid exam template config: " + template.getCode(), exception);
        }
        if (!"assessment-template-v1".equals(config.path("schemaVersion").asText())) {
            throw new IllegalStateException("Unsupported exam template schema: " + template.getCode());
        }

        Map<String, SkillAuthoringPolicy> skills = new LinkedHashMap<>();
        JsonNode skillConfig = config.path("skills");
        skillConfig.fields().forEachRemaining(entry -> {
            AssessmentSkill skill;
            try {
                skill = AssessmentSkill.valueOf(entry.getKey());
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Unsupported skill in template " + template.getCode());
            }
            JsonNode node = entry.getValue();
            if (node.has("enabled") && !node.path("enabled").asBoolean()) {
                return;
            }
            List<String> enabledTypes = new ArrayList<>();
            Map<String, QuestionAuthoringPolicy> questionPolicies = new LinkedHashMap<>();
            for (JsonNode typeNode : node.path("questionTypes")) {
                CanonicalQuestionType type;
                try {
                    type = CanonicalQuestionType.valueOf(typeNode.asText());
                    ResolvedAssessmentPolicy resolved = policyService.resolve(
                            version.getProgramCode(), skill, type);
                    enabledTypes.add(type.name());
                    List<String> scoringPolicies = scoringPolicies(node, type, resolved.scoringPolicyCode());
                    JsonNode questionRule = node.path("questionRules").path(type.name());
                    questionPolicies.put(type.name(), new QuestionAuthoringPolicy(
                            type.name(),
                            resolved.scoringPolicyCode().name(),
                            scoringPolicies,
                            resolved.scoringProfile(),
                            resolved.promptProfile(),
                            resolved.rubricProfile(),
                            nonNegative(questionRule.path("minOptions"), defaultMinOptions(type)),
                            nonNegative(questionRule.path("maxOptions"), defaultMaxOptions(type))
                    ));
                } catch (IllegalArgumentException ignored) {
                    // Database policy is the source of truth; stale template entries are not exposed.
                }
            }
            if (!enabledTypes.isEmpty()) {
                skills.put(skill.name(), new SkillAuthoringPolicy(
                        node.path("durationMinutes").asInt(40),
                        decimal(node.path("defaultPoints"), BigDecimal.ONE),
                        List.copyOf(enabledTypes),
                        node.has("pointsEditable")
                                ? node.path("pointsEditable").asBoolean()
                                : !"TOPIK".equals(version.getProgramCode()),
                        Map.copyOf(questionPolicies),
                        positive(node.path("maxQuestions"), 200),
                        node.has("excelImportEnabled")
                                ? node.path("excelImportEnabled").asBoolean()
                                : true
                ));
            }
        });
        if (skills.isEmpty()) {
            throw new IllegalStateException("Exam template has no enabled authoring skills: " + template.getCode());
        }
        return new ExamTemplatePolicy(
                template.getCode(),
                template.getDisplayName(),
                template.getCategoryCode(),
                version.getProgramCode(),
                version.getId(),
                version.getVersionNumber(),
                Map.copyOf(skills),
                positive(config.path("maxTests"), 20)
        );
    }

    private String activeTemplateConfig(AssessmentExamTemplate template) {
        if (templateVersionRepository == null || template.getActiveVersionId() == null) {
            return template.getConfigJson();
        }
        AssessmentExamTemplateVersion version = templateVersionRepository
                .findById(template.getActiveVersionId())
                .filter(candidate -> template.getCode().equals(candidate.getTemplateCode()))
                .filter(candidate -> AssessmentExamTemplateVersion.STATUS_ACTIVE
                        .equals(candidate.getStatus()))
                .orElseThrow(() -> new IllegalStateException(
                        "Exam template active version is invalid: " + template.getCode()));
        return version.getConfigJson();
    }

    private static int positive(JsonNode node, int fallback) {
        int value = node != null && node.canConvertToInt() ? node.asInt() : fallback;
        return value > 0 ? value : fallback;
    }

    private static int nonNegative(JsonNode node, int fallback) {
        int value = node != null && node.canConvertToInt() ? node.asInt() : fallback;
        return value >= 0 ? value : fallback;
    }

    private static int defaultMinOptions(CanonicalQuestionType type) {
        return type == CanonicalQuestionType.SINGLE_CHOICE || type == CanonicalQuestionType.MULTIPLE_CHOICE
                ? 2 : 0;
    }

    private static int defaultMaxOptions(CanonicalQuestionType type) {
        return type == CanonicalQuestionType.SINGLE_CHOICE || type == CanonicalQuestionType.MULTIPLE_CHOICE
                ? 8 : 0;
    }

    private static BigDecimal decimal(JsonNode node, BigDecimal fallback) {
        return node == null || !node.isNumber() ? fallback : node.decimalValue();
    }

    private static List<String> scoringPolicies(JsonNode skillConfig,
                                                CanonicalQuestionType type,
                                                ScoringPolicyCode fallback) {
        List<String> result = new ArrayList<>();
        JsonNode configured = skillConfig.path("scoringPolicies").path(type.name());
        if (configured.isArray()) {
            for (JsonNode node : configured) {
                try {
                    result.add(ScoringPolicyCode.valueOf(node.asText()).name());
                } catch (IllegalArgumentException ignored) {
                    // Invalid template values are not exposed.
                }
            }
        }
        if (!result.contains(fallback.name())) result.add(0, fallback.name());
        return List.copyOf(new java.util.LinkedHashSet<>(result));
    }

    public record AuthoringCatalog(String schemaVersion, List<ExamTemplatePolicy> templates) {
        public AuthoringCatalog {
            templates = templates == null ? List.of() : List.copyOf(templates);
        }
    }

    public record ExamTemplatePolicy(
            String code,
            String displayName,
            String categoryCode,
            String programCode,
            Long programVersionId,
            Integer programVersion,
            Map<String, SkillAuthoringPolicy> skills,
            Integer maxTests
    ) {
        public ExamTemplatePolicy {
            skills = skills == null ? Map.of() : Map.copyOf(skills);
            maxTests = maxTests == null || maxTests <= 0 ? 20 : maxTests;
        }

        public ExamTemplatePolicy(String code,
                                  String displayName,
                                  String categoryCode,
                                  String programCode,
                                  Long programVersionId,
                                  Integer programVersion,
                                  Map<String, SkillAuthoringPolicy> skills) {
            this(code, displayName, categoryCode, programCode, programVersionId,
                    programVersion, skills, 20);
        }

        public SkillAuthoringPolicy requireSkill(String rawSkill) {
            String skill = rawSkill == null ? "" : rawSkill.trim().toUpperCase(Locale.ROOT);
            SkillAuthoringPolicy policy = skills.get(skill);
            if (policy == null) {
                throw new IllegalArgumentException("Skill is not enabled by template " + code + ": " + skill);
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
            Integer maxQuestions,
            boolean excelImportEnabled
    ) {
        public SkillAuthoringPolicy {
            questionTypes = questionTypes == null ? List.of() : List.copyOf(questionTypes);
            questionPolicies = questionPolicies == null ? Map.of() : Map.copyOf(questionPolicies);
            maxQuestions = maxQuestions == null || maxQuestions <= 0 ? 200 : maxQuestions;
        }

        public SkillAuthoringPolicy(Integer durationMinutes,
                                    BigDecimal defaultPoints,
                                    List<String> questionTypes,
                                    boolean pointsEditable,
                                    Map<String, QuestionAuthoringPolicy> questionPolicies) {
            this(durationMinutes, defaultPoints, questionTypes, pointsEditable,
                    questionPolicies, 200, true);
        }

        public SkillAuthoringPolicy(Integer durationMinutes,
                                    BigDecimal defaultPoints,
                                    List<String> questionTypes) {
            this(durationMinutes, defaultPoints, questionTypes, true, Map.of(), 200, true);
        }

        public QuestionAuthoringPolicy questionPolicy(String rawType) {
            return questionPolicies.get(rawType == null ? "" : rawType.trim().toUpperCase(Locale.ROOT));
        }
    }

    public record QuestionAuthoringPolicy(
            String questionType,
            String defaultScoringPolicyCode,
            List<String> allowedScoringPolicyCodes,
            ProfileReference scoringProfile,
            ProfileReference promptProfile,
            ProfileReference rubricProfile,
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

        public QuestionAuthoringPolicy(String questionType,
                                       String defaultScoringPolicyCode,
                                       List<String> allowedScoringPolicyCodes,
                                       ProfileReference scoringProfile,
                                       ProfileReference promptProfile,
                                       ProfileReference rubricProfile) {
            this(questionType, defaultScoringPolicyCode, allowedScoringPolicyCodes,
                    scoringProfile, promptProfile, rubricProfile,
                    defaultMinOptions(CanonicalQuestionType.valueOf(questionType)),
                    defaultMaxOptions(CanonicalQuestionType.valueOf(questionType)));
        }
    }
}
