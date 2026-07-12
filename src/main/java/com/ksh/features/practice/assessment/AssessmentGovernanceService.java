package com.ksh.features.practice.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.assessment.persistence.AssessmentExamTemplate;
import com.ksh.features.practice.assessment.persistence.AssessmentExamTemplateVersion;
import com.ksh.features.practice.assessment.persistence.AssessmentProgram;
import com.ksh.features.practice.assessment.persistence.AssessmentProgramSkillPolicy;
import com.ksh.features.practice.assessment.persistence.AssessmentProgramVersion;
import com.ksh.features.practice.assessment.persistence.AssessmentPromptProfile;
import com.ksh.features.practice.assessment.persistence.AssessmentQuestionTypePolicy;
import com.ksh.features.practice.assessment.persistence.AssessmentRubricProfile;
import com.ksh.features.practice.assessment.persistence.AssessmentScoringProfile;
import com.ksh.features.practice.assessment.repository.AssessmentExamTemplateRepository;
import com.ksh.features.practice.assessment.repository.AssessmentExamTemplateVersionRepository;
import com.ksh.features.practice.assessment.repository.AssessmentProgramRepository;
import com.ksh.features.practice.assessment.repository.AssessmentProgramSkillPolicyRepository;
import com.ksh.features.practice.assessment.repository.AssessmentProgramVersionRepository;
import com.ksh.features.practice.assessment.repository.AssessmentPromptProfileRepository;
import com.ksh.features.practice.assessment.repository.AssessmentQuestionTypePolicyRepository;
import com.ksh.features.practice.assessment.repository.AssessmentRubricProfileRepository;
import com.ksh.features.practice.assessment.repository.AssessmentScoringProfileRepository;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.governance.PracticeGovernanceAuditService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AssessmentGovernanceService {

    private final AssessmentProgramRepository programRepository;
    private final AssessmentProgramVersionRepository programVersionRepository;
    private final AssessmentProgramSkillPolicyRepository skillPolicyRepository;
    private final AssessmentQuestionTypePolicyRepository questionPolicyRepository;
    private final AssessmentExamTemplateRepository templateRepository;
    private final AssessmentExamTemplateVersionRepository templateVersionRepository;
    private final AssessmentScoringProfileRepository scoringProfileRepository;
    private final AssessmentPromptProfileRepository promptProfileRepository;
    private final AssessmentRubricProfileRepository rubricProfileRepository;
    private final PracticeAuthorizationService authorizationService;
    private final PracticeGovernanceAuditService auditService;
    private final ObjectMapper objectMapper;

    public AssessmentGovernanceService(
            AssessmentProgramRepository programRepository,
            AssessmentProgramVersionRepository programVersionRepository,
            AssessmentProgramSkillPolicyRepository skillPolicyRepository,
            AssessmentQuestionTypePolicyRepository questionPolicyRepository,
            AssessmentExamTemplateRepository templateRepository,
            AssessmentExamTemplateVersionRepository templateVersionRepository,
            AssessmentScoringProfileRepository scoringProfileRepository,
            AssessmentPromptProfileRepository promptProfileRepository,
            AssessmentRubricProfileRepository rubricProfileRepository,
            PracticeAuthorizationService authorizationService,
            PracticeGovernanceAuditService auditService,
            ObjectMapper objectMapper) {
        this.programRepository = programRepository;
        this.programVersionRepository = programVersionRepository;
        this.skillPolicyRepository = skillPolicyRepository;
        this.questionPolicyRepository = questionPolicyRepository;
        this.templateRepository = templateRepository;
        this.templateVersionRepository = templateVersionRepository;
        this.scoringProfileRepository = scoringProfileRepository;
        this.promptProfileRepository = promptProfileRepository;
        this.rubricProfileRepository = rubricProfileRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AssessmentProgramVersion createProgramVersion(String rawProgramCode,
                                                         ProgramVersionRequest request,
                                                         Long actorId) {
        requireGovernance(actorId);
        String programCode = code(rawProgramCode, 40);
        programRepository.findByCodeForUpdate(programCode)
                .orElseThrow(() -> new EntityNotFoundException("Chứng chỉ không tồn tại."));
        validateProgramRequest(request);
        AssessmentProgramVersion version = programVersionRepository.saveAndFlush(
                new AssessmentProgramVersion(programCode,
                        programVersionRepository.maxVersionNumber(programCode) + 1,
                        required(request.displayName(), "Tên chứng chỉ"),
                        "INACTIVE",
                        required(request.defaultLanguage(), "Ngôn ngữ mặc định")));
        for (SkillPolicyRequest skill : request.skills()) {
            String skillCode = enumCode(AssessmentSkill.class, skill.skillCode(), "kỹ năng");
            String delivery = enumCode(AssessmentDeliveryMode.class, skill.deliveryMode(),
                    "delivery mode");
            skillPolicyRepository.save(new AssessmentProgramSkillPolicy(
                    version.getId(), skillCode, skill.enabled(), delivery));
        }
        List<QuestionPolicyRequest> questionTypes = request.questionTypes() == null
                ? List.of() : request.questionTypes();
        for (QuestionPolicyRequest question : questionTypes) {
            String skillCode = enumCode(AssessmentSkill.class, question.skillCode(), "kỹ năng");
            String questionType = enumCode(CanonicalQuestionType.class,
                    question.questionType(), "dạng câu hỏi");
            String scoringPolicy = enumCode(ScoringPolicyCode.class,
                    question.defaultScoringPolicyCode(), "scoring policy");
            validateProfileReferences(question.scoringProfileId(), question.promptProfileId(),
                    question.rubricProfileId(), skillCode);
            questionPolicyRepository.save(new AssessmentQuestionTypePolicy(
                    version.getId(), skillCode, questionType, question.enabled(),
                    scoringPolicy, question.scoringProfileId(), question.promptProfileId(),
                    question.rubricProfileId()));
        }
        auditService.record("PROGRAM_VERSION_CREATED", "PROGRAM_VERSION", version.getId(),
                null, actorId, null, false, null, null, json(request));
        return version;
    }

    @Transactional
    public AssessmentProgramVersion activateProgramVersion(String rawProgramCode,
                                                           Long versionId, Long actorId) {
        requireGovernance(actorId);
        String programCode = code(rawProgramCode, 40);
        AssessmentProgram program = programRepository.findByCodeForUpdate(programCode)
                .orElseThrow(() -> new EntityNotFoundException("Chứng chỉ không tồn tại."));
        AssessmentProgramVersion target = programVersionRepository.findById(versionId)
                .filter(version -> programCode.equals(version.getProgramCode()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Phiên bản chứng chỉ không tồn tại."));
        validateProgramVersionForActivation(target.getId());
        Long previousId = program.getActiveVersionId();
        if (previousId != null && !previousId.equals(versionId)) {
            programVersionRepository.findById(previousId).ifPresent(previous -> {
                previous.deactivate();
                programVersionRepository.save(previous);
            });
        }
        target.activate();
        programVersionRepository.save(target);
        program.activateVersion(target.getId());
        programRepository.save(program);
        auditService.record("PROGRAM_VERSION_ACTIVATED", "PROGRAM_VERSION", target.getId(),
                null, actorId, null, false, null,
                idJson("previousActiveVersionId", previousId),
                idJson("activeVersionId", target.getId()));
        return target;
    }

    @Transactional
    public AssessmentExamTemplateVersion createTemplateVersion(String rawTemplateCode,
                                                               String configJson,
                                                               Long actorId) {
        requireGovernance(actorId);
        String templateCode = code(rawTemplateCode, 80);
        templateRepository.findByCodeForUpdate(templateCode)
                .orElseThrow(() -> new EntityNotFoundException("Mẫu đề không tồn tại."));
        validateTemplateConfig(configJson);
        AssessmentExamTemplateVersion version = templateVersionRepository.save(
                new AssessmentExamTemplateVersion(templateCode,
                        templateVersionRepository.maxVersionNumber(templateCode) + 1,
                        configJson, actorId));
        auditService.record("EXAM_TEMPLATE_VERSION_CREATED", "EXAM_TEMPLATE_VERSION",
                version.getId(), null, actorId, null, false, null, null, configJson);
        return version;
    }

    @Transactional
    public AssessmentExamTemplateVersion activateTemplateVersion(String rawTemplateCode,
                                                                 Long versionId,
                                                                 Long actorId) {
        requireGovernance(actorId);
        String templateCode = code(rawTemplateCode, 80);
        AssessmentExamTemplate template = templateRepository.findByCodeForUpdate(templateCode)
                .orElseThrow(() -> new EntityNotFoundException("Mẫu đề không tồn tại."));
        AssessmentExamTemplateVersion target = templateVersionRepository.findById(versionId)
                .filter(version -> templateCode.equals(version.getTemplateCode()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Phiên bản mẫu đề không tồn tại."));
        Long previousId = template.getActiveVersionId();
        if (previousId != null && !previousId.equals(versionId)) {
            templateVersionRepository.findById(previousId).ifPresent(previous -> {
                previous.archive();
                templateVersionRepository.save(previous);
            });
        }
        target.activate(actorId);
        templateVersionRepository.save(target);
        template.activateVersion(target.getId(), target.getConfigJson());
        templateRepository.save(template);
        auditService.record("EXAM_TEMPLATE_VERSION_ACTIVATED", "EXAM_TEMPLATE_VERSION",
                target.getId(), null, actorId, null, false, null,
                idJson("previousActiveVersionId", previousId),
                idJson("activeVersionId", target.getId()));
        return target;
    }

    @Transactional
    public AssessmentScoringProfile createScoringProfile(String rawCode,
                                                         String configJson,
                                                         Long actorId) {
        requireGovernance(actorId);
        validateJsonObject(configJson, "Scoring profile");
        String profileCode = code(rawCode, 100);
        AssessmentScoringProfile profile = scoringProfileRepository.save(
                new AssessmentScoringProfile(profileCode,
                        scoringProfileRepository.maxVersionNumber(profileCode) + 1,
                        configJson, actorId));
        auditService.record("SCORING_PROFILE_CREATED", "SCORING_PROFILE", profile.getId(),
                null, actorId, null, false, null, null, configJson);
        return profile;
    }

    @Transactional
    public AssessmentPromptProfile createPromptProfile(String rawCode,
                                                       PromptProfileRequest request,
                                                       Long actorId) {
        requireGovernance(actorId);
        String profileCode = code(rawCode, 100);
        String skill = enumCode(AssessmentSkill.class, request.skillCode(), "kỹ năng");
        AssessmentPromptProfile profile = promptProfileRepository.save(
                new AssessmentPromptProfile(profileCode,
                        promptProfileRepository.maxVersionNumber(profileCode) + 1,
                        skill, blank(request.taskType()), blank(request.compatibilityAdapter()),
                        required(request.systemRules(), "System rules"), actorId));
        auditService.record("PROMPT_PROFILE_CREATED", "PROMPT_PROFILE", profile.getId(),
                null, actorId, null, false, null, null, json(request));
        return profile;
    }

    @Transactional
    public AssessmentRubricProfile createRubricProfile(String rawCode,
                                                       RubricProfileRequest request,
                                                       Long actorId) {
        requireGovernance(actorId);
        validateJsonObject(request.configJson(), "Rubric profile");
        String profileCode = code(rawCode, 100);
        String skill = enumCode(AssessmentSkill.class, request.skillCode(), "kỹ năng");
        AssessmentRubricProfile profile = rubricProfileRepository.save(
                new AssessmentRubricProfile(profileCode,
                        rubricProfileRepository.maxVersionNumber(profileCode) + 1,
                        skill, blank(request.taskType()), request.configJson(), actorId));
        auditService.record("RUBRIC_PROFILE_CREATED", "RUBRIC_PROFILE", profile.getId(),
                null, actorId, null, false, null, null, json(request));
        return profile;
    }

    @Transactional
    public void activateProfile(ProfileKind kind, Long profileId, Long actorId) {
        requireGovernance(actorId);
        switch (kind) {
            case SCORING -> {
                AssessmentScoringProfile profile = scoringProfileRepository.findById(profileId)
                        .orElseThrow(() -> new EntityNotFoundException("Profile không tồn tại."));
                profile.activate();
                scoringProfileRepository.save(profile);
            }
            case PROMPT -> {
                AssessmentPromptProfile profile = promptProfileRepository.findById(profileId)
                        .orElseThrow(() -> new EntityNotFoundException("Profile không tồn tại."));
                profile.activate();
                promptProfileRepository.save(profile);
            }
            case RUBRIC -> {
                AssessmentRubricProfile profile = rubricProfileRepository.findById(profileId)
                        .orElseThrow(() -> new EntityNotFoundException("Profile không tồn tại."));
                profile.activate();
                rubricProfileRepository.save(profile);
            }
        }
        auditService.record(kind.name() + "_PROFILE_ACTIVATED", kind.name() + "_PROFILE",
                profileId, null, actorId, null, false, null, null,
                "{\"governanceStatus\":\"ACTIVE\"}");
    }

    private void validateProgramRequest(ProgramVersionRequest request) {
        if (request == null || request.skills() == null || request.skills().isEmpty()) {
            throw new IllegalArgumentException("Phiên bản chứng chỉ phải có chính sách kỹ năng.");
        }
        List<QuestionPolicyRequest> questions = request.questionTypes() == null
                ? List.of() : request.questionTypes();
        Set<String> skills = new HashSet<>();
        Set<String> enabledSkills = new HashSet<>();
        for (SkillPolicyRequest skill : request.skills()) {
            if (skill == null) {
                throw new IllegalArgumentException("Chính sách kỹ năng không được để trống.");
            }
            String normalized = enumCode(AssessmentSkill.class, skill.skillCode(), "kỹ năng");
            if (!skills.add(normalized)) {
                throw new IllegalArgumentException("Kỹ năng bị lặp: " + normalized);
            }
            if (skill.enabled()) enabledSkills.add(normalized);
            enumCode(AssessmentDeliveryMode.class, skill.deliveryMode(), "delivery mode");
        }
        if (enabledSkills.isEmpty()) {
            throw new IllegalArgumentException(
                    "Phiên bản chứng chỉ phải có ít nhất một kỹ năng được bật.");
        }
        Set<String> policies = new HashSet<>();
        Set<String> enabledPolicySkills = new HashSet<>();
        for (QuestionPolicyRequest question : questions) {
            if (question == null) {
                throw new IllegalArgumentException("Chính sách dạng câu hỏi không được để trống.");
            }
            String skill = enumCode(AssessmentSkill.class, question.skillCode(), "kỹ năng");
            if (!skills.contains(skill)) {
                throw new IllegalArgumentException(
                        "Question policy tham chiếu kỹ năng chưa khai báo: " + skill);
            }
            String type = enumCode(CanonicalQuestionType.class, question.questionType(),
                    "dạng câu hỏi");
            if (!policies.add(skill + ":" + type)) {
                throw new IllegalArgumentException("Question policy bị lặp: " + skill + "/" + type);
            }
            if (question.enabled()) enabledPolicySkills.add(skill);
            enumCode(ScoringPolicyCode.class, question.defaultScoringPolicyCode(),
                    "scoring policy");
        }
        for (String skill : enabledSkills) {
            if (!enabledPolicySkills.contains(skill)) {
                throw new IllegalArgumentException(
                        "Kỹ năng " + skill + " chưa có dạng câu hỏi được bật.");
            }
        }
    }

    private void validateProgramVersionForActivation(Long programVersionId) {
        List<AssessmentProgramSkillPolicy> enabledSkills = skillPolicyRepository
                .findByProgramVersionIdOrderBySkillCodeAsc(programVersionId).stream()
                .filter(AssessmentProgramSkillPolicy::isEnabled)
                .toList();
        if (enabledSkills.isEmpty()) {
            throw new IllegalArgumentException(
                    "Phiên bản chứng chỉ phải có ít nhất một kỹ năng được bật.");
        }
        List<AssessmentQuestionTypePolicy> enabledQuestions = questionPolicyRepository
                .findByProgramVersionIdOrderBySkillCodeAscCanonicalQuestionTypeAsc(programVersionId)
                .stream()
                .filter(AssessmentQuestionTypePolicy::isEnabled)
                .toList();
        for (AssessmentProgramSkillPolicy skill : enabledSkills) {
            boolean hasQuestionType = enabledQuestions.stream()
                    .anyMatch(question -> skill.getSkillCode().equals(question.getSkillCode()));
            if (!hasQuestionType) {
                throw new IllegalArgumentException(
                        "Kỹ năng " + skill.getSkillCode()
                                + " chưa có dạng câu hỏi được bật.");
            }
        }
        for (AssessmentQuestionTypePolicy question : enabledQuestions) {
            validateProfileReferences(question.getScoringProfileId(),
                    question.getPromptProfileId(), question.getRubricProfileId(),
                    question.getSkillCode());
        }
    }

    private void validateProfileReferences(Long scoringProfileId,
                                           Long promptProfileId,
                                           Long rubricProfileId,
                                           String expectedSkillCode) {
        if (scoringProfileId != null) {
            scoringProfileRepository.findById(scoringProfileId)
                    .filter(AssessmentScoringProfile::isEnabled)
                    .filter(profile -> "ACTIVE".equals(profile.getGovernanceStatus()))
                    .orElseThrow(() -> new IllegalArgumentException("Scoring profile chưa active."));
        }
        if (promptProfileId != null) {
            promptProfileRepository.findById(promptProfileId)
                    .filter(AssessmentPromptProfile::isEnabled)
                    .filter(profile -> "ACTIVE".equals(profile.getGovernanceStatus()))
                    .filter(profile -> expectedSkillCode == null
                            || expectedSkillCode.equals(profile.getSkillCode()))
                    .orElseThrow(() -> new IllegalArgumentException("Prompt profile chưa active."));
        }
        if (rubricProfileId != null) {
            rubricProfileRepository.findById(rubricProfileId)
                    .filter(AssessmentRubricProfile::isEnabled)
                    .filter(profile -> "ACTIVE".equals(profile.getGovernanceStatus()))
                    .filter(profile -> expectedSkillCode == null
                            || expectedSkillCode.equals(profile.getSkillCode()))
                    .orElseThrow(() -> new IllegalArgumentException("Rubric profile chưa active."));
        }
    }

    private void validateTemplateConfig(String configJson) {
        JsonNode root = validateJsonObject(configJson, "Exam template");
        if (!"assessment-template-v1".equals(root.path("schemaVersion").asText())) {
            throw new IllegalArgumentException("Exam template phải dùng assessment-template-v1.");
        }
        if (!root.path("skills").isObject() || root.path("skills").isEmpty()) {
            throw new IllegalArgumentException("Exam template phải khai báo skills.");
        }
        root.path("skills").fieldNames().forEachRemaining(skill ->
                enumCode(AssessmentSkill.class, skill, "kỹ năng"));
        root.path("skills").fields().forEachRemaining(entry -> {
            JsonNode skillConfig = entry.getValue();
            JsonNode questionTypes = skillConfig.path("questionTypes");
            if (!skillConfig.isObject() || !questionTypes.isArray()
                    || questionTypes.isEmpty()) {
                throw new IllegalArgumentException(
                        "Kỹ năng " + entry.getKey() + " phải có questionTypes.");
            }
            Set<String> uniqueTypes = new HashSet<>();
            for (JsonNode type : questionTypes) {
                String normalized = enumCode(
                        CanonicalQuestionType.class, type.asText(), "dạng câu hỏi");
                if (!uniqueTypes.add(normalized)) {
                    throw new IllegalArgumentException(
                            "Dạng câu hỏi bị lặp trong " + entry.getKey() + ": " + normalized);
                }
            }
        });
    }

    private JsonNode validateJsonObject(String json, String label) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isObject()) throw new IllegalArgumentException(label + " phải là JSON object.");
            return root;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(label + " không phải JSON hợp lệ.", exception);
        }
    }

    private void requireGovernance(Long actorId) {
        authorizationService.requireGlobal(actorId, PracticeAction.GOVERNANCE_MANAGE);
    }

    public void requireAccess(Long actorId) {
        requireGovernance(actorId);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể ghi audit governance.", exception);
        }
    }

    private static String idJson(String key, Long value) {
        return "{\"" + key + "\":" + (value == null ? "null" : value) + "}";
    }

    private static String code(String value, int maxLength) {
        String normalized = required(value, "Mã").toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_-]", "_");
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Mã vượt quá " + maxLength + " ký tự.");
        }
        return normalized;
    }

    private static <E extends Enum<E>> String enumCode(Class<E> type, String value,
                                                        String label) {
        try {
            return Enum.valueOf(type, required(value, label).toUpperCase(Locale.ROOT)).name();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Giá trị " + label + " không hợp lệ: " + value,
                    exception);
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " là bắt buộc.");
        }
        return value.trim();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public enum ProfileKind { SCORING, PROMPT, RUBRIC }

    public record ProgramVersionRequest(String displayName, String defaultLanguage,
                                        List<SkillPolicyRequest> skills,
                                        List<QuestionPolicyRequest> questionTypes) {
    }

    public record SkillPolicyRequest(String skillCode, boolean enabled,
                                     String deliveryMode) {
    }

    public record QuestionPolicyRequest(String skillCode, String questionType,
                                        boolean enabled, String defaultScoringPolicyCode,
                                        Long scoringProfileId, Long promptProfileId,
                                        Long rubricProfileId) {
    }

    public record PromptProfileRequest(String skillCode, String taskType,
                                       String compatibilityAdapter, String systemRules) {
    }

    public record RubricProfileRequest(String skillCode, String taskType,
                                       String configJson) {
    }
}
