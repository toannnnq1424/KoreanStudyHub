package com.ksh.features.practice.assessment;

import com.ksh.features.practice.assessment.persistence.AssessmentProgram;
import com.ksh.features.practice.assessment.persistence.AssessmentProgramSkillPolicy;
import com.ksh.features.practice.assessment.persistence.AssessmentProgramVersion;
import com.ksh.features.practice.assessment.persistence.AssessmentPromptProfile;
import com.ksh.features.practice.assessment.persistence.AssessmentQuestionTypePolicy;
import com.ksh.features.practice.assessment.persistence.AssessmentRubricProfile;
import com.ksh.features.practice.assessment.persistence.AssessmentScoringProfile;
import com.ksh.features.practice.assessment.repository.AssessmentProgramRepository;
import com.ksh.features.practice.assessment.repository.AssessmentProgramSkillPolicyRepository;
import com.ksh.features.practice.assessment.repository.AssessmentProgramVersionRepository;
import com.ksh.features.practice.assessment.repository.AssessmentPromptProfileRepository;
import com.ksh.features.practice.assessment.repository.AssessmentQuestionTypePolicyRepository;
import com.ksh.features.practice.assessment.repository.AssessmentRubricProfileRepository;
import com.ksh.features.practice.assessment.repository.AssessmentScoringProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AssessmentProgramPolicyService {

    private final AssessmentProgramRepository programRepository;
    private final AssessmentProgramVersionRepository programVersionRepository;
    private final AssessmentProgramSkillPolicyRepository skillPolicyRepository;
    private final AssessmentQuestionTypePolicyRepository questionTypePolicyRepository;
    private final AssessmentScoringProfileRepository scoringProfileRepository;
    private final AssessmentPromptProfileRepository promptProfileRepository;
    private final AssessmentRubricProfileRepository rubricProfileRepository;

    public AssessmentProgramPolicyService(
            AssessmentProgramRepository programRepository,
            AssessmentProgramVersionRepository programVersionRepository,
            AssessmentProgramSkillPolicyRepository skillPolicyRepository,
            AssessmentQuestionTypePolicyRepository questionTypePolicyRepository,
            AssessmentScoringProfileRepository scoringProfileRepository,
            AssessmentPromptProfileRepository promptProfileRepository,
            AssessmentRubricProfileRepository rubricProfileRepository) {
        this.programRepository = programRepository;
        this.programVersionRepository = programVersionRepository;
        this.skillPolicyRepository = skillPolicyRepository;
        this.questionTypePolicyRepository = questionTypePolicyRepository;
        this.scoringProfileRepository = scoringProfileRepository;
        this.promptProfileRepository = promptProfileRepository;
        this.rubricProfileRepository = rubricProfileRepository;
    }

    @Transactional(readOnly = true)
    public ResolvedAssessmentPolicy resolve(String rawProgramCode,
                                            AssessmentSkill skill,
                                            CanonicalQuestionType questionType) {
        String programCode = normalizeProgramCode(rawProgramCode);
        require(skill, "assessment skill");
        require(questionType, "canonical question type");

        AssessmentProgram program = programRepository.findById(programCode)
                .orElseThrow(() -> unsupported(programCode, skill, questionType));
        if (program.getActiveVersionId() == null) {
            throw unsupported(programCode, skill, questionType);
        }
        AssessmentProgramVersion version = programVersionRepository.findById(program.getActiveVersionId())
                .filter(candidate -> programCode.equals(candidate.getProgramCode()))
                .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
                .orElseThrow(() -> unsupported(programCode, skill, questionType));

        AssessmentProgramSkillPolicy skillPolicy = skillPolicyRepository
                .findByProgramVersionIdAndSkillCode(version.getId(), skill.name())
                .filter(AssessmentProgramSkillPolicy::isEnabled)
                .orElseThrow(() -> unsupported(programCode, skill, questionType));
        AssessmentDeliveryMode deliveryMode = enumValue(
                AssessmentDeliveryMode.class,
                skillPolicy.getDeliveryMode(),
                "delivery mode");

        AssessmentQuestionTypePolicy questionPolicy = questionTypePolicyRepository
                .findByProgramVersionIdAndSkillCodeAndCanonicalQuestionType(
                        version.getId(), skill.name(), questionType.name())
                .filter(AssessmentQuestionTypePolicy::isEnabled)
                .orElseThrow(() -> unsupported(programCode, skill, questionType));

        ScoringPolicyCode scoringPolicyCode = enumValue(
                ScoringPolicyCode.class,
                questionPolicy.getDefaultScoringPolicyCode(),
                "scoring policy");

        return new ResolvedAssessmentPolicy(
                programCode,
                version.getId(),
                version.getVersionNumber(),
                skill,
                deliveryMode,
                questionType,
                scoringPolicyCode,
                scoringProfile(questionPolicy.getScoringProfileId()),
                promptProfile(questionPolicy.getPromptProfileId()),
                rubricProfile(questionPolicy.getRubricProfileId())
        );
    }

    private ProfileReference scoringProfile(Long id) {
        if (id == null) {
            return null;
        }
        AssessmentScoringProfile profile = scoringProfileRepository.findById(id)
                .filter(AssessmentScoringProfile::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("Assessment scoring profile is not active"));
        return new ProfileReference(profile.getCode(), profile.getVersionNumber());
    }

    private ProfileReference promptProfile(Long id) {
        if (id == null) {
            return null;
        }
        AssessmentPromptProfile profile = promptProfileRepository.findById(id)
                .filter(AssessmentPromptProfile::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("Assessment prompt profile is not active"));
        return new ProfileReference(profile.getCode(), profile.getVersionNumber());
    }

    private ProfileReference rubricProfile(Long id) {
        if (id == null) {
            return null;
        }
        AssessmentRubricProfile profile = rubricProfileRepository.findById(id)
                .filter(AssessmentRubricProfile::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("Assessment rubric profile is not active"));
        return new ProfileReference(profile.getCode(), profile.getVersionNumber());
    }

    private static String normalizeProgramCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing assessment program code");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static IllegalArgumentException unsupported(String programCode,
                                                        AssessmentSkill skill,
                                                        CanonicalQuestionType questionType) {
        return new IllegalArgumentException(
                "Assessment policy is not enabled for program=" + programCode
                        + ", skill=" + skill + ", questionType=" + questionType);
    }

    private static <T> T require(T value, String label) {
        if (value == null) {
            throw new IllegalArgumentException("Missing " + label);
        }
        return value;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> enumType, String value, String label) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unsupported " + label + ": " + value, exception);
        }
    }
}
