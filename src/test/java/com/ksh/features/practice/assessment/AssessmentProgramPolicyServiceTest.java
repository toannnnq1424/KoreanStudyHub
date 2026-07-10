package com.ksh.features.practice.assessment;

import com.ksh.features.practice.assessment.persistence.AssessmentProgram;
import com.ksh.features.practice.assessment.persistence.AssessmentProgramSkillPolicy;
import com.ksh.features.practice.assessment.persistence.AssessmentProgramVersion;
import com.ksh.features.practice.assessment.persistence.AssessmentQuestionTypePolicy;
import com.ksh.features.practice.assessment.persistence.AssessmentScoringProfile;
import com.ksh.features.practice.assessment.repository.AssessmentProgramRepository;
import com.ksh.features.practice.assessment.repository.AssessmentProgramSkillPolicyRepository;
import com.ksh.features.practice.assessment.repository.AssessmentProgramVersionRepository;
import com.ksh.features.practice.assessment.repository.AssessmentPromptProfileRepository;
import com.ksh.features.practice.assessment.repository.AssessmentQuestionTypePolicyRepository;
import com.ksh.features.practice.assessment.repository.AssessmentRubricProfileRepository;
import com.ksh.features.practice.assessment.repository.AssessmentScoringProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssessmentProgramPolicyServiceTest {

    private AssessmentProgramRepository programRepository;
    private AssessmentProgramVersionRepository versionRepository;
    private AssessmentProgramSkillPolicyRepository skillPolicyRepository;
    private AssessmentQuestionTypePolicyRepository questionPolicyRepository;
    private AssessmentScoringProfileRepository scoringProfileRepository;
    private AssessmentProgramPolicyService service;

    @BeforeEach
    void setUp() {
        programRepository = mock(AssessmentProgramRepository.class);
        versionRepository = mock(AssessmentProgramVersionRepository.class);
        skillPolicyRepository = mock(AssessmentProgramSkillPolicyRepository.class);
        questionPolicyRepository = mock(AssessmentQuestionTypePolicyRepository.class);
        scoringProfileRepository = mock(AssessmentScoringProfileRepository.class);
        service = new AssessmentProgramPolicyService(
                programRepository,
                versionRepository,
                skillPolicyRepository,
                questionPolicyRepository,
                scoringProfileRepository,
                mock(AssessmentPromptProfileRepository.class),
                mock(AssessmentRubricProfileRepository.class));
    }

    @Test
    void resolvesActiveTopikReadingSingleChoicePolicy() {
        AssessmentProgram program = new AssessmentProgram("TOPIK", 10L);
        AssessmentProgramVersion version = mock(AssessmentProgramVersion.class);
        when(version.getId()).thenReturn(10L);
        when(version.getProgramCode()).thenReturn("TOPIK");
        when(version.getVersionNumber()).thenReturn(1);
        when(version.getStatus()).thenReturn("ACTIVE");
        AssessmentProgramSkillPolicy skillPolicy = new AssessmentProgramSkillPolicy(
                10L, "READING", true, "SKILL_SPECIFIC");
        AssessmentQuestionTypePolicy questionPolicy = new AssessmentQuestionTypePolicy(
                10L, "READING", "SINGLE_CHOICE", true,
                "ALL_OR_NOTHING", 20L, null, null);
        AssessmentScoringProfile scoringProfile = mock(AssessmentScoringProfile.class);
        when(scoringProfile.isEnabled()).thenReturn(true);
        when(scoringProfile.getCode()).thenReturn("TOPIK_SINGLE_CHOICE");
        when(scoringProfile.getVersionNumber()).thenReturn(1);

        when(programRepository.findById("TOPIK")).thenReturn(Optional.of(program));
        when(versionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(skillPolicyRepository.findByProgramVersionIdAndSkillCode(10L, "READING"))
                .thenReturn(Optional.of(skillPolicy));
        when(questionPolicyRepository.findByProgramVersionIdAndSkillCodeAndCanonicalQuestionType(
                10L, "READING", "SINGLE_CHOICE"))
                .thenReturn(Optional.of(questionPolicy));
        when(scoringProfileRepository.findById(20L)).thenReturn(Optional.of(scoringProfile));

        ResolvedAssessmentPolicy resolved = service.resolve(
                " topik ", AssessmentSkill.READING, CanonicalQuestionType.SINGLE_CHOICE);

        assertThat(resolved.programCode()).isEqualTo("TOPIK");
        assertThat(resolved.programVersion()).isEqualTo(1);
        assertThat(resolved.deliveryMode()).isEqualTo(AssessmentDeliveryMode.SKILL_SPECIFIC);
        assertThat(resolved.scoringPolicyCode()).isEqualTo(ScoringPolicyCode.ALL_OR_NOTHING);
        assertThat(resolved.scoringProfile()).isEqualTo(new ProfileReference("TOPIK_SINGLE_CHOICE", 1));
    }

    @Test
    void missingOrDisabledQuestionTypePolicyFailsClosed() {
        AssessmentProgram program = new AssessmentProgram("CUSTOM", 11L);
        AssessmentProgramVersion version = mock(AssessmentProgramVersion.class);
        when(version.getId()).thenReturn(11L);
        when(version.getProgramCode()).thenReturn("CUSTOM");
        when(version.getVersionNumber()).thenReturn(1);
        when(version.getStatus()).thenReturn("ACTIVE");
        when(programRepository.findById("CUSTOM")).thenReturn(Optional.of(program));
        when(versionRepository.findById(11L)).thenReturn(Optional.of(version));
        when(skillPolicyRepository.findByProgramVersionIdAndSkillCode(11L, "READING"))
                .thenReturn(Optional.of(new AssessmentProgramSkillPolicy(
                        11L, "READING", true, "SKILL_SPECIFIC")));
        when(questionPolicyRepository.findByProgramVersionIdAndSkillCodeAndCanonicalQuestionType(
                11L, "READING", "MULTIPLE_CHOICE"))
                .thenReturn(Optional.of(new AssessmentQuestionTypePolicy(
                        11L, "READING", "MULTIPLE_CHOICE", false,
                        "ALL_OR_NOTHING", null, null, null)));

        assertThatThrownBy(() -> service.resolve(
                "CUSTOM", AssessmentSkill.READING, CanonicalQuestionType.MULTIPLE_CHOICE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not enabled");
    }

    @Test
    void inactiveReferencedProfileFailsClosed() {
        AssessmentProgram program = new AssessmentProgram("TOPIK", 10L);
        AssessmentProgramVersion version = mock(AssessmentProgramVersion.class);
        when(version.getId()).thenReturn(10L);
        when(version.getProgramCode()).thenReturn("TOPIK");
        when(version.getVersionNumber()).thenReturn(1);
        when(version.getStatus()).thenReturn("ACTIVE");
        when(programRepository.findById("TOPIK")).thenReturn(Optional.of(program));
        when(versionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(skillPolicyRepository.findByProgramVersionIdAndSkillCode(10L, "READING"))
                .thenReturn(Optional.of(new AssessmentProgramSkillPolicy(
                        10L, "READING", true, "SKILL_SPECIFIC")));
        when(questionPolicyRepository.findByProgramVersionIdAndSkillCodeAndCanonicalQuestionType(
                10L, "READING", "SINGLE_CHOICE"))
                .thenReturn(Optional.of(new AssessmentQuestionTypePolicy(
                        10L, "READING", "SINGLE_CHOICE", true,
                        "ALL_OR_NOTHING", 20L, null, null)));
        AssessmentScoringProfile inactive = mock(AssessmentScoringProfile.class);
        when(inactive.isEnabled()).thenReturn(false);
        when(scoringProfileRepository.findById(20L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.resolve(
                "TOPIK", AssessmentSkill.READING, CanonicalQuestionType.SINGLE_CHOICE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not active");
    }
}
