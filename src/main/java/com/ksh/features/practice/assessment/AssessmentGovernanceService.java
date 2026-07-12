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

import java.util.Comparator;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    public AssessmentProgram createProgramRoot(String rawProgramCode, Long actorId) {
        requireGovernance(actorId);
        String programCode = code(rawProgramCode, 40);
        if (programRepository.existsById(programCode)) {
            throw new IllegalArgumentException("Mã chứng chỉ đã tồn tại.");
        }
        AssessmentProgram program = programRepository.save(
                new AssessmentProgram(programCode, null));
        auditService.record("PROGRAM_CREATED", "PROGRAM", null,
                null, actorId, null, false, null, null,
                "{\"programCode\":\"" + programCode + "\"}");
        return program;
    }

    @Transactional
    public AssessmentProgram setProgramEnabled(String rawProgramCode,
                                               boolean enabled,
                                               Long actorId,
                                               String rawReason) {
        requireGovernance(actorId);
        String reason = governanceReason(rawReason);
        String programCode = code(rawProgramCode, 40);
        AssessmentProgram program = programRepository.findByCodeForUpdate(programCode)
                .orElseThrow(() -> new EntityNotFoundException("Chứng chỉ không tồn tại."));
        if (enabled) {
            if (program.getActiveVersionId() == null) {
                throw new IllegalStateException(
                        "Chứng chỉ phải có phiên bản active trước khi bật.");
            }
            validateProgramVersionForActivation(program.getActiveVersionId());
            resolveTemplateActivations(programCode, program.getActiveVersionId());
        }
        boolean previous = program.isEnabled();
        program.setEnabled(enabled);
        programRepository.save(program);
        auditService.record(enabled ? "PROGRAM_ENABLED" : "PROGRAM_DISABLED",
                "PROGRAM", null, null, actorId, null, false, reason,
                "{\"programCode\":\"" + programCode
                        + "\",\"enabled\":" + previous + "}",
                "{\"programCode\":\"" + programCode
                        + "\",\"enabled\":" + enabled + "}");
        return program;
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
            validateRequiredProfileReferences(question.enabled(), scoringPolicy,
                    question.scoringProfileId(), question.promptProfileId(),
                    question.rubricProfileId());
            validateProfileReferences(question.scoringProfileId(), question.promptProfileId(),
                    question.rubricProfileId(), skillCode);
            questionPolicyRepository.save(new AssessmentQuestionTypePolicy(
                    version.getId(), skillCode, questionType, question.enabled(),
                    scoringPolicy, question.scoringProfileId(), question.promptProfileId(),
                    question.rubricProfileId()));
        }
        stageTemplateVersions(programCode, version.getId(), actorId);
        auditService.record("PROGRAM_VERSION_CREATED", "PROGRAM_VERSION", version.getId(),
                null, actorId, null, false, null, null, json(request));
        return version;
    }

    @Transactional
    public AssessmentProgramVersion activateProgramVersion(String rawProgramCode,
                                                           Long versionId, Long actorId,
                                                           String rawReason) {
        requireGovernance(actorId);
        String reason = governanceReason(rawReason);
        String programCode = code(rawProgramCode, 40);
        AssessmentProgram program = programRepository.findByCodeForUpdate(programCode)
                .orElseThrow(() -> new EntityNotFoundException("Chứng chỉ không tồn tại."));
        AssessmentProgramVersion target = programVersionRepository.findById(versionId)
                .filter(version -> programCode.equals(version.getProgramCode()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Phiên bản chứng chỉ không tồn tại."));
        validateProgramVersionForActivation(target.getId());
        List<TemplateActivation> templateActivations =
                resolveTemplateActivations(programCode, target.getId());
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
        for (TemplateActivation activation : templateActivations) {
            activateTemplateCandidate(activation.template(), activation.version(), actorId);
        }
        auditService.record("PROGRAM_VERSION_ACTIVATED", "PROGRAM_VERSION", target.getId(),
                null, actorId, null, false, reason,
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
        AssessmentExamTemplate template = templateRepository.findByCodeForUpdate(templateCode)
                .orElseThrow(() -> new EntityNotFoundException("Mẫu đề không tồn tại."));
        return createTemplateVersionLocked(template, template.getProgramVersionId(),
                configJson, actorId);
    }

    @Transactional
    public AssessmentExamTemplateVersion createTemplateVersion(String rawTemplateCode,
                                                               Long programVersionId,
                                                               String configJson,
                                                               Long actorId) {
        requireGovernance(actorId);
        String templateCode = code(rawTemplateCode, 80);
        AssessmentExamTemplate template = templateRepository.findByCodeForUpdate(templateCode)
                .orElseThrow(() -> new EntityNotFoundException("Mẫu đề không tồn tại."));
        return createTemplateVersionLocked(template, programVersionId, configJson, actorId);
    }

    private AssessmentExamTemplateVersion createTemplateVersionLocked(
            AssessmentExamTemplate template, Long programVersionId,
            String configJson, Long actorId) {
        if (programVersionId == null) {
            throw new IllegalArgumentException("Phiên bản chứng chỉ là bắt buộc.");
        }
        validateTemplateConfig(configJson);
        validateTemplateAgainstProgramVersion(
                template.getProgramCode(), programVersionId, configJson);
        AssessmentExamTemplateVersion version = templateVersionRepository.save(
                new AssessmentExamTemplateVersion(template.getCode(), programVersionId,
                        templateVersionRepository.maxVersionNumber(template.getCode()) + 1,
                        configJson, actorId));
        auditService.record("EXAM_TEMPLATE_VERSION_CREATED", "EXAM_TEMPLATE_VERSION",
                version.getId(), null, actorId, null, false, null, null, configJson);
        return version;
    }

    @Transactional
    public AssessmentExamTemplate createTemplateRoot(String rawProgramCode,
                                                     TemplateRootRequest request,
                                                     Long actorId) {
        requireGovernance(actorId);
        String programCode = code(rawProgramCode, 40);
        AssessmentProgram program = programRepository.findByCodeForUpdate(programCode)
                .orElseThrow(() -> new EntityNotFoundException("Chứng chỉ không tồn tại."));
        if (program.getActiveVersionId() == null) {
            throw new IllegalStateException(
                    "Chứng chỉ phải có phiên bản active trước khi tạo kịch bản.");
        }
        String templateCode = code(request.code(), 80);
        if (templateRepository.existsById(templateCode)) {
            throw new IllegalArgumentException("Mã kịch bản đã tồn tại.");
        }
        String displayName = required(request.displayName(), "Tên kịch bản");
        String categoryCode = request.categoryCode() == null
                || request.categoryCode().isBlank()
                ? templateCode : code(request.categoryCode(), 50);
        validateTemplateConfig(request.configJson());
        validateTemplateAgainstProgramVersion(
                programCode, program.getActiveVersionId(), request.configJson());
        AssessmentExamTemplate template = templateRepository.save(
                new AssessmentExamTemplate(templateCode, programCode,
                        program.getActiveVersionId(), displayName, categoryCode,
                        request.enabled(), request.configJson()));
        AssessmentExamTemplateVersion version = templateVersionRepository.save(
                new AssessmentExamTemplateVersion(templateCode,
                        program.getActiveVersionId(), 1, request.configJson(), actorId));
        if (request.enabled()) {
            version.activate(actorId);
            templateVersionRepository.save(version);
            template.activateVersion(version.getId(), program.getActiveVersionId(),
                    request.configJson());
            templateRepository.save(template);
        }
        auditService.record("EXAM_TEMPLATE_CREATED", "EXAM_TEMPLATE", null,
                null, actorId, null, false, null, null,
                "{\"templateCode\":\"" + templateCode
                        + "\",\"programCode\":\"" + programCode + "\"}");
        return template;
    }

    @Transactional
    public AssessmentExamTemplateVersion activateTemplateVersion(String rawTemplateCode,
                                                                 Long versionId,
                                                                 Long actorId,
                                                                 String rawReason) {
        requireGovernance(actorId);
        String reason = governanceReason(rawReason);
        String templateCode = code(rawTemplateCode, 80);
        AssessmentExamTemplate template = templateRepository.findByCodeForUpdate(templateCode)
                .orElseThrow(() -> new EntityNotFoundException("Mẫu đề không tồn tại."));
        AssessmentExamTemplateVersion target = templateVersionRepository.findById(versionId)
                .filter(version -> templateCode.equals(version.getTemplateCode()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Phiên bản mẫu đề không tồn tại."));
        AssessmentProgram program = programRepository.findById(template.getProgramCode())
                .orElseThrow(() -> new EntityNotFoundException("Chứng chỉ không tồn tại."));
        if (target.getProgramVersionId() == null
                || !target.getProgramVersionId().equals(program.getActiveVersionId())) {
            throw new IllegalArgumentException(
                    "Kịch bản chỉ được activate với phiên bản chứng chỉ hiện hành.");
        }
        validateTemplateAgainstProgramVersion(template.getProgramCode(),
                target.getProgramVersionId(), target.getConfigJson());
        Long previousId = template.getActiveVersionId();
        activateTemplateCandidate(template, target, actorId);
        auditService.record("EXAM_TEMPLATE_VERSION_ACTIVATED", "EXAM_TEMPLATE_VERSION",
                target.getId(), null, actorId, null, false, reason,
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
    public void activateProfile(ProfileKind kind, Long profileId, Long actorId,
                                String rawReason) {
        requireGovernance(actorId);
        String reason = governanceReason(rawReason);
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
                profileId, null, actorId, null, false, reason, null,
                "{\"governanceStatus\":\"ACTIVE\"}");
    }

    private void stageTemplateVersions(String programCode, Long programVersionId,
                                       Long actorId) {
        for (AssessmentExamTemplate candidate :
                templateRepository.findByProgramCodeOrderByDisplayNameAsc(programCode)) {
            AssessmentExamTemplate template = templateRepository
                    .findByCodeForUpdate(candidate.getCode())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Kịch bản không tồn tại: " + candidate.getCode()));
            String configJson = activeTemplateConfig(template);
            templateVersionRepository.save(new AssessmentExamTemplateVersion(
                    template.getCode(), programVersionId,
                    templateVersionRepository.maxVersionNumber(template.getCode()) + 1,
                    configJson, actorId));
        }
    }

    private String activeTemplateConfig(AssessmentExamTemplate template) {
        if (template.getActiveVersionId() == null) {
            return template.getConfigJson();
        }
        return templateVersionRepository.findById(template.getActiveVersionId())
                .filter(version -> template.getCode().equals(version.getTemplateCode()))
                .map(AssessmentExamTemplateVersion::getConfigJson)
                .orElse(template.getConfigJson());
    }

    private List<TemplateActivation> resolveTemplateActivations(
            String programCode, Long programVersionId) {
        List<TemplateActivation> result = new ArrayList<>();
        for (AssessmentExamTemplate candidate : templateRepository
                .findByProgramCodeAndEnabledTrueOrderByDisplayNameAsc(programCode)) {
            AssessmentExamTemplate template = templateRepository
                    .findByCodeForUpdate(candidate.getCode())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Kịch bản không tồn tại: " + candidate.getCode()));
            AssessmentExamTemplateVersion version = templateVersionRepository
                    .findByTemplateCodeAndProgramVersionIdOrderByVersionNumberDesc(
                            template.getCode(), programVersionId).stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Kịch bản " + template.getCode()
                                    + " chưa có version tương thích với chứng chỉ đã chọn."));
            validateTemplateAgainstProgramVersion(
                    programCode, programVersionId, version.getConfigJson());
            result.add(new TemplateActivation(template, version));
        }
        return List.copyOf(result);
    }

    private void activateTemplateCandidate(AssessmentExamTemplate template,
                                           AssessmentExamTemplateVersion target,
                                           Long actorId) {
        Long previousId = template.getActiveVersionId();
        if (previousId != null && !previousId.equals(target.getId())) {
            templateVersionRepository.findById(previousId).ifPresent(previous -> {
                previous.archive();
                templateVersionRepository.save(previous);
            });
        }
        target.activate(actorId);
        templateVersionRepository.save(target);
        template.activateVersion(target.getId(), target.getProgramVersionId(),
                target.getConfigJson());
        templateRepository.save(template);
    }

    private void validateTemplateAgainstProgramVersion(
            String expectedProgramCode, Long programVersionId, String configJson) {
        AssessmentProgramVersion version = programVersionRepository.findById(programVersionId)
                .filter(item -> expectedProgramCode != null
                        && expectedProgramCode.equals(item.getProgramCode()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Phiên bản chứng chỉ không thuộc kịch bản."));
        JsonNode config = validateJsonObject(configJson, "Exam template");
        Map<String, AssessmentProgramSkillPolicy> skills = new LinkedHashMap<>();
        for (AssessmentProgramSkillPolicy policy : skillPolicyRepository
                .findByProgramVersionIdOrderBySkillCodeAsc(version.getId())) {
            skills.put(policy.getSkillCode(), policy);
        }
        Set<String> enabledQuestionTypes = new HashSet<>();
        for (AssessmentQuestionTypePolicy policy : questionPolicyRepository
                .findByProgramVersionIdOrderBySkillCodeAscCanonicalQuestionTypeAsc(
                        version.getId())) {
            if (policy.isEnabled()) {
                enabledQuestionTypes.add(
                        policy.getSkillCode() + ":" + policy.getCanonicalQuestionType());
            }
        }
        int[] enabledSkillCount = {0};
        config.path("skills").fields().forEachRemaining(entry -> {
            JsonNode skillConfig = entry.getValue();
            if (skillConfig.has("enabled") && !skillConfig.path("enabled").asBoolean()) {
                return;
            }
            AssessmentProgramSkillPolicy skillPolicy = skills.get(entry.getKey());
            if (skillPolicy == null || !skillPolicy.isEnabled()) {
                throw new IllegalArgumentException(
                        "Kỹ năng " + entry.getKey()
                                + " không được bật trong phiên bản chứng chỉ.");
            }
            enabledSkillCount[0]++;
            for (JsonNode type : skillConfig.path("questionTypes")) {
                String normalized = enumCode(CanonicalQuestionType.class,
                        type.asText(), "dạng câu hỏi");
                if (!enabledQuestionTypes.contains(entry.getKey() + ":" + normalized)) {
                    throw new IllegalArgumentException(
                            "Dạng câu hỏi " + entry.getKey() + "/" + normalized
                                    + " không được bật trong phiên bản chứng chỉ.");
                }
            }
        });
        if (enabledSkillCount[0] == 0) {
            throw new IllegalArgumentException(
                    "Kịch bản phải có ít nhất một kỹ năng được bật.");
        }
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
            String scoringPolicy = enumCode(ScoringPolicyCode.class,
                    question.defaultScoringPolicyCode(), "scoring policy");
            validateRequiredProfileReferences(question.enabled(), scoringPolicy,
                    question.scoringProfileId(), question.promptProfileId(),
                    question.rubricProfileId());
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
            validateRequiredProfileReferences(true,
                    question.getDefaultScoringPolicyCode(),
                    question.getScoringProfileId(), question.getPromptProfileId(),
                    question.getRubricProfileId());
            validateProfileReferences(question.getScoringProfileId(),
                    question.getPromptProfileId(), question.getRubricProfileId(),
                    question.getSkillCode());
        }
    }

    private void validateRequiredProfileReferences(boolean enabled,
                                                   String scoringPolicy,
                                                   Long scoringProfileId,
                                                   Long promptProfileId,
                                                   Long rubricProfileId) {
        if (!enabled || !ScoringPolicyCode.PROFILE_BASED.name().equals(scoringPolicy)) {
            return;
        }
        if (scoringProfileId == null || promptProfileId == null || rubricProfileId == null) {
            throw new IllegalArgumentException(
                    "Dạng PROFILE_BASED phải chọn đủ scoring, prompt và rubric profile.");
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

    @Transactional(readOnly = true)
    public GovernanceCatalog governanceCatalog(Long actorId) {
        requireGovernance(actorId);
        List<ProgramView> programs = programRepository.findAllByOrderByCodeAsc().stream()
                .map(program -> new ProgramView(
                        program.getCode(),
                        program.isEnabled(),
                        program.getActiveVersionId(),
                        programVersionRepository
                                .findByProgramCodeOrderByVersionNumberDesc(program.getCode())
                                .stream().map(this::programVersionView).toList(),
                        templateRepository.findByProgramCodeOrderByDisplayNameAsc(
                                        program.getCode()).stream()
                                .map(this::templateView).toList()))
                .toList();
        List<ProfileView> profiles = new ArrayList<>();
        scoringProfileRepository.findAll().forEach(profile -> profiles.add(new ProfileView(
                ProfileKind.SCORING.name(), profile.getId(), profile.getCode(),
                profile.getVersionNumber(), profile.getGovernanceStatus(), profile.isEnabled(),
                null, null, null)));
        promptProfileRepository.findAll().forEach(profile -> profiles.add(new ProfileView(
                ProfileKind.PROMPT.name(), profile.getId(), profile.getCode(),
                profile.getVersionNumber(), profile.getGovernanceStatus(), profile.isEnabled(),
                profile.getSkillCode(), profile.getTaskType(),
                profile.getCompatibilityAdapter())));
        rubricProfileRepository.findAll().forEach(profile -> profiles.add(new ProfileView(
                ProfileKind.RUBRIC.name(), profile.getId(), profile.getCode(),
                profile.getVersionNumber(), profile.getGovernanceStatus(), profile.isEnabled(),
                profile.getSkillCode(), profile.getTaskType(), null)));
        profiles.sort(Comparator.comparing(ProfileView::kind)
                .thenComparing(ProfileView::code)
                .thenComparing(ProfileView::versionNumber, Comparator.reverseOrder()));
        return new GovernanceCatalog(programs, List.copyOf(profiles));
    }

    @Transactional
    public AssessmentExamTemplate setTemplateEnabled(String rawTemplateCode,
                                                     boolean enabled, Long actorId,
                                                     String rawReason) {
        requireGovernance(actorId);
        String reason = governanceReason(rawReason);
        String templateCode = code(rawTemplateCode, 80);
        AssessmentExamTemplate template = templateRepository.findByCodeForUpdate(templateCode)
                .orElseThrow(() -> new EntityNotFoundException("Kịch bản không tồn tại."));
        if (enabled) {
            if (template.getActiveVersionId() == null) {
                throw new IllegalStateException(
                        "Kịch bản phải có phiên bản active trước khi bật.");
            }
            AssessmentProgram program = programRepository.findById(template.getProgramCode())
                    .orElseThrow(() -> new EntityNotFoundException("Chứng chỉ không tồn tại."));
            AssessmentExamTemplateVersion active = templateVersionRepository
                    .findById(template.getActiveVersionId())
                    .filter(item -> AssessmentExamTemplateVersion.STATUS_ACTIVE
                            .equals(item.getStatus()))
                    .orElseThrow(() -> new IllegalStateException(
                            "Phiên bản kịch bản active không hợp lệ."));
            if (!active.getProgramVersionId().equals(program.getActiveVersionId())) {
                throw new IllegalStateException(
                        "Kịch bản chưa tương thích với phiên bản chứng chỉ hiện hành.");
            }
            validateTemplateAgainstProgramVersion(template.getProgramCode(),
                    active.getProgramVersionId(), active.getConfigJson());
        }
        template.setEnabled(enabled);
        templateRepository.save(template);
        auditService.record(enabled ? "EXAM_TEMPLATE_ENABLED" : "EXAM_TEMPLATE_DISABLED",
                "EXAM_TEMPLATE", null, null, actorId, null, false, reason, null,
                "{\"templateCode\":\"" + templateCode + "\"}");
        return template;
    }

    private ProgramVersionView programVersionView(AssessmentProgramVersion version) {
        List<SkillPolicyView> skills = skillPolicyRepository
                .findByProgramVersionIdOrderBySkillCodeAsc(version.getId()).stream()
                .map(item -> new SkillPolicyView(item.getSkillCode(), item.isEnabled(),
                        item.getDeliveryMode()))
                .toList();
        List<QuestionPolicyView> questions = questionPolicyRepository
                .findByProgramVersionIdOrderBySkillCodeAscCanonicalQuestionTypeAsc(
                        version.getId()).stream()
                .map(item -> new QuestionPolicyView(item.getSkillCode(),
                        item.getCanonicalQuestionType(), item.isEnabled(),
                        item.getDefaultScoringPolicyCode(), item.getScoringProfileId(),
                        item.getPromptProfileId(), item.getRubricProfileId()))
                .toList();
        return new ProgramVersionView(version.getId(), version.getVersionNumber(),
                version.getDisplayName(), version.getStatus(), version.getDefaultLanguage(),
                skills, questions);
    }

    private TemplateView templateView(AssessmentExamTemplate template) {
        return new TemplateView(template.getCode(), template.getDisplayName(),
                template.getCategoryCode(), template.isEnabled(),
                template.getActiveVersionId(), template.getProgramVersionId(),
                templateVersionRepository
                        .findByTemplateCodeOrderByVersionNumberDesc(template.getCode()).stream()
                        .map(version -> new TemplateVersionView(version.getId(),
                                version.getProgramVersionId(), version.getVersionNumber(),
                                version.getStatus(), version.getConfigJson(),
                                version.getCreatedBy(), version.getActivatedBy(),
                                version.getCreatedAt(), version.getActivatedAt()))
                        .toList());
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

    private static String governanceReason(String value) {
        String normalized = required(value, "Lý do thay đổi governance");
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("Lý do thay đổi governance vượt quá 500 ký tự.");
        }
        return normalized;
    }

    public enum ProfileKind { SCORING, PROMPT, RUBRIC }

    private record TemplateActivation(AssessmentExamTemplate template,
                                      AssessmentExamTemplateVersion version) {
    }

    public record TemplateRootRequest(String code, String displayName,
                                      String categoryCode, boolean enabled,
                                      String configJson) {
    }

    public record GovernanceCatalog(List<ProgramView> programs,
                                    List<ProfileView> profiles) {
    }

    public record ProgramView(String code, boolean enabled, Long activeVersionId,
                              List<ProgramVersionView> versions,
                              List<TemplateView> templates) {
    }

    public record ProgramVersionView(Long id, Integer versionNumber, String displayName,
                                     String status, String defaultLanguage,
                                     List<SkillPolicyView> skills,
                                     List<QuestionPolicyView> questionTypes) {
    }

    public record SkillPolicyView(String skillCode, boolean enabled,
                                  String deliveryMode) {
    }

    public record QuestionPolicyView(String skillCode, String questionType,
                                     boolean enabled, String scoringPolicy,
                                     Long scoringProfileId, Long promptProfileId,
                                     Long rubricProfileId) {
    }

    public record TemplateView(String code, String displayName, String categoryCode,
                               boolean enabled, Long activeVersionId,
                               Long compatibilityProgramVersionId,
                               List<TemplateVersionView> versions) {
    }

    public record TemplateVersionView(Long id, Long programVersionId,
                                      Integer versionNumber, String status,
                                      String configJson, Long createdBy,
                                      Long activatedBy,
                                      java.time.LocalDateTime createdAt,
                                      java.time.LocalDateTime activatedAt) {
    }

    public record ProfileView(String kind, Long id, String code,
                              Integer versionNumber, String status,
                              boolean enabled, String skillCode,
                              String taskType, String compatibilityAdapter) {
    }

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
