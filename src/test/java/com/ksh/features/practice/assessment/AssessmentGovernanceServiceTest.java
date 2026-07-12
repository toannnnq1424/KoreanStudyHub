package com.ksh.features.practice.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.assessment.AssessmentGovernanceService.ProgramVersionRequest;
import com.ksh.features.practice.assessment.AssessmentGovernanceService.QuestionPolicyRequest;
import com.ksh.features.practice.assessment.AssessmentGovernanceService.SkillPolicyRequest;
import com.ksh.features.practice.assessment.persistence.AssessmentExamTemplate;
import com.ksh.features.practice.assessment.persistence.AssessmentExamTemplateVersion;
import com.ksh.features.practice.assessment.persistence.AssessmentProgram;
import com.ksh.features.practice.assessment.persistence.AssessmentProgramSkillPolicy;
import com.ksh.features.practice.assessment.persistence.AssessmentProgramVersion;
import com.ksh.features.practice.assessment.persistence.AssessmentPromptProfile;
import com.ksh.features.practice.assessment.persistence.AssessmentQuestionTypePolicy;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;

class AssessmentGovernanceServiceTest {

    private final AssessmentProgramRepository programRepository = mock(AssessmentProgramRepository.class);
    private final AssessmentProgramVersionRepository versionRepository = mock(AssessmentProgramVersionRepository.class);
    private final AssessmentProgramSkillPolicyRepository skillRepository = mock(AssessmentProgramSkillPolicyRepository.class);
    private final AssessmentQuestionTypePolicyRepository questionRepository = mock(AssessmentQuestionTypePolicyRepository.class);
    private final AssessmentExamTemplateRepository templateRepository = mock(AssessmentExamTemplateRepository.class);
    private final AssessmentExamTemplateVersionRepository templateVersionRepository = mock(AssessmentExamTemplateVersionRepository.class);
    private final AssessmentScoringProfileRepository scoringRepository = mock(AssessmentScoringProfileRepository.class);
    private final AssessmentPromptProfileRepository promptRepository = mock(AssessmentPromptProfileRepository.class);
    private final AssessmentRubricProfileRepository rubricRepository = mock(AssessmentRubricProfileRepository.class);
    private final PracticeAuthorizationService authorizationService = mock(PracticeAuthorizationService.class);
    private final PracticeGovernanceAuditService auditService = mock(PracticeGovernanceAuditService.class);

    private AssessmentGovernanceService service;

    @BeforeEach
    void setUp() {
        service = new AssessmentGovernanceService(programRepository, versionRepository,
                skillRepository, questionRepository, templateRepository,
                templateVersionRepository, scoringRepository, promptRepository,
                rubricRepository, authorizationService, auditService, new ObjectMapper());
    }

    @Test
    void createsImmutableProgramVersionWithSkillAndQuestionPolicies() throws Exception {
        allowGovernance(1L);
        when(programRepository.findByCodeForUpdate("TOPIK"))
                .thenReturn(Optional.of(new AssessmentProgram("TOPIK", 4L)));
        when(versionRepository.maxVersionNumber("TOPIK")).thenReturn(2);
        when(versionRepository.saveAndFlush(any(AssessmentProgramVersion.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 30L));

        ProgramVersionRequest request = new ProgramVersionRequest(
                "TOPIK", "ko",
                List.of(new SkillPolicyRequest("reading", true, "both")),
                List.of(new QuestionPolicyRequest("reading", "single_choice", true,
                        "all_or_nothing", null, null, null)));

        AssessmentProgramVersion version = service.createProgramVersion("topik", request, 1L);

        assertEquals(3, version.getVersionNumber());
        assertEquals("INACTIVE", version.getStatus());
        verify(skillRepository).save(any(AssessmentProgramSkillPolicy.class));
        verify(questionRepository).save(any(AssessmentQuestionTypePolicy.class));
        verify(auditService).record(eq("PROGRAM_VERSION_CREATED"),
                eq("PROGRAM_VERSION"), eq(30L), isNull(), eq(1L), isNull(),
                eq(false), isNull(), isNull(), anyString());
    }

    @Test
    void rejectsDuplicateSkillPolicyBeforePersistingVersion() {
        allowGovernance(1L);
        when(programRepository.findByCodeForUpdate("TOPIK"))
                .thenReturn(Optional.of(new AssessmentProgram("TOPIK", 4L)));
        ProgramVersionRequest request = new ProgramVersionRequest(
                "TOPIK", "ko",
                List.of(
                        new SkillPolicyRequest("READING", true, "BOTH"),
                        new SkillPolicyRequest("reading", true, "both")),
                List.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.createProgramVersion("TOPIK", request, 1L));
        verify(versionRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsEnabledSkillWithoutEnabledQuestionTypeBeforePersistingVersion() {
        allowGovernance(1L);
        when(programRepository.findByCodeForUpdate("TOPIK"))
                .thenReturn(Optional.of(new AssessmentProgram("TOPIK", 4L)));
        ProgramVersionRequest request = new ProgramVersionRequest(
                "TOPIK", "ko",
                List.of(new SkillPolicyRequest("READING", true, "BOTH")),
                null);

        assertThrows(IllegalArgumentException.class,
                () -> service.createProgramVersion("TOPIK", request, 1L));

        verify(versionRepository, never()).saveAndFlush(any());
    }

    @Test
    void activatingProgramVersionDeactivatesPreviousWithoutDeletingHistory() throws Exception {
        allowGovernance(1L);
        AssessmentProgram program = new AssessmentProgram("TOPIK", 7L);
        AssessmentProgramVersion previous = withId(
                new AssessmentProgramVersion("TOPIK", 1, "Old", "ACTIVE", "ko"), 7L);
        AssessmentProgramVersion target = withId(
                new AssessmentProgramVersion("TOPIK", 2, "New", "INACTIVE", "ko"), 8L);
        when(programRepository.findByCodeForUpdate("TOPIK")).thenReturn(Optional.of(program));
        when(versionRepository.findById(7L)).thenReturn(Optional.of(previous));
        when(versionRepository.findById(8L)).thenReturn(Optional.of(target));
        when(skillRepository.findByProgramVersionIdOrderBySkillCodeAsc(8L))
                .thenReturn(List.of(new AssessmentProgramSkillPolicy(
                        8L, "READING", true, "SKILL_SPECIFIC")));
        when(questionRepository.findByProgramVersionIdOrderBySkillCodeAscCanonicalQuestionTypeAsc(8L))
                .thenReturn(List.of(new AssessmentQuestionTypePolicy(
                        8L, "READING", "SINGLE_CHOICE", true,
                        "ALL_OR_NOTHING", null, null, null)));

        service.activateProgramVersion("TOPIK", 8L, 1L);

        assertEquals("INACTIVE", previous.getStatus());
        assertEquals("ACTIVE", target.getStatus());
        assertEquals(8L, program.getActiveVersionId());
        verify(versionRepository).save(previous);
        verify(versionRepository).save(target);
        verify(programRepository).save(program);
    }

    @Test
    void templateVersionRequiresSupportedSchemaAndArchivesPriorActiveVersion() throws Exception {
        allowGovernance(1L);
        AssessmentExamTemplate template = mock(AssessmentExamTemplate.class);
        when(template.getActiveVersionId()).thenReturn(7L);
        when(templateRepository.findByCodeForUpdate("TOPIK_II"))
                .thenReturn(Optional.of(template));
        when(templateVersionRepository.maxVersionNumber("TOPIK_II")).thenReturn(1);
        when(templateVersionRepository.save(any(AssessmentExamTemplateVersion.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 8L));

        assertThrows(IllegalArgumentException.class,
                () -> service.createTemplateVersion("TOPIK_II", "{\"skills\":{}}", 1L));

        String config = "{\"schemaVersion\":\"assessment-template-v1\"," +
                "\"skills\":{\"READING\":{\"questionTypes\":[\"SINGLE_CHOICE\"]}}}";
        AssessmentExamTemplateVersion created = service.createTemplateVersion(
                "TOPIK_II", config, 1L);
        assertEquals(2, created.getVersionNumber());

        AssessmentExamTemplateVersion previous = withId(
                new AssessmentExamTemplateVersion("TOPIK_II", 1, config, 1L), 7L);
        previous.activate(1L);
        when(templateVersionRepository.findById(7L)).thenReturn(Optional.of(previous));
        when(templateVersionRepository.findById(8L)).thenReturn(Optional.of(created));

        service.activateTemplateVersion("TOPIK_II", 8L, 1L);

        assertEquals(AssessmentExamTemplateVersion.STATUS_ARCHIVED, previous.getStatus());
        assertEquals(AssessmentExamTemplateVersion.STATUS_ACTIVE, created.getStatus());
        verify(template).activateVersion(8L, config);
    }

    @Test
    void activationRejectsProgramVersionReferencingDraftProfile() throws Exception {
        allowGovernance(1L);
        AssessmentProgram program = new AssessmentProgram("TOPIK", 7L);
        AssessmentProgramVersion target = withId(
                new AssessmentProgramVersion("TOPIK", 2, "New", "INACTIVE", "ko"), 8L);
        AssessmentScoringProfile draftProfile = mock(AssessmentScoringProfile.class);
        when(draftProfile.isEnabled()).thenReturn(true);
        when(draftProfile.getGovernanceStatus()).thenReturn("DRAFT");
        when(programRepository.findByCodeForUpdate("TOPIK")).thenReturn(Optional.of(program));
        when(versionRepository.findById(8L)).thenReturn(Optional.of(target));
        when(skillRepository.findByProgramVersionIdOrderBySkillCodeAsc(8L))
                .thenReturn(List.of(new AssessmentProgramSkillPolicy(
                        8L, "READING", true, "SKILL_SPECIFIC")));
        when(questionRepository.findByProgramVersionIdOrderBySkillCodeAscCanonicalQuestionTypeAsc(8L))
                .thenReturn(List.of(new AssessmentQuestionTypePolicy(
                        8L, "READING", "SINGLE_CHOICE", true,
                        "ALL_OR_NOTHING", 40L, null, null)));
        when(scoringRepository.findById(40L)).thenReturn(Optional.of(draftProfile));

        assertThrows(IllegalArgumentException.class,
                () -> service.activateProgramVersion("TOPIK", 8L, 1L));

        assertEquals("INACTIVE", target.getStatus());
        assertEquals(7L, program.getActiveVersionId());
        verify(versionRepository, never()).save(target);
        verify(programRepository, never()).save(program);
    }

    @Test
    void activationRejectsActivePromptProfileFromAnotherSkill() throws Exception {
        allowGovernance(1L);
        AssessmentProgram program = new AssessmentProgram("TOPIK", 7L);
        AssessmentProgramVersion target = withId(
                new AssessmentProgramVersion("TOPIK", 2, "New", "INACTIVE", "ko"), 8L);
        AssessmentPromptProfile writingPrompt = mock(AssessmentPromptProfile.class);
        when(writingPrompt.isEnabled()).thenReturn(true);
        when(writingPrompt.getGovernanceStatus()).thenReturn("ACTIVE");
        when(writingPrompt.getSkillCode()).thenReturn("WRITING");
        when(programRepository.findByCodeForUpdate("TOPIK")).thenReturn(Optional.of(program));
        when(versionRepository.findById(8L)).thenReturn(Optional.of(target));
        when(skillRepository.findByProgramVersionIdOrderBySkillCodeAsc(8L))
                .thenReturn(List.of(new AssessmentProgramSkillPolicy(
                        8L, "READING", true, "SKILL_SPECIFIC")));
        when(questionRepository.findByProgramVersionIdOrderBySkillCodeAscCanonicalQuestionTypeAsc(8L))
                .thenReturn(List.of(new AssessmentQuestionTypePolicy(
                        8L, "READING", "SINGLE_CHOICE", true,
                        "ALL_OR_NOTHING", null, 41L, null)));
        when(promptRepository.findById(41L)).thenReturn(Optional.of(writingPrompt));

        assertThrows(IllegalArgumentException.class,
                () -> service.activateProgramVersion("TOPIK", 8L, 1L));

        assertEquals("INACTIVE", target.getStatus());
        verify(versionRepository, never()).save(target);
        verify(programRepository, never()).save(program);
    }

    @Test
    void scoringProfileIsDraftUntilExplicitActivation() throws Exception {
        allowGovernance(1L);
        when(scoringRepository.maxVersionNumber("TOPIK_SCORE")).thenReturn(3);
        when(scoringRepository.save(any(AssessmentScoringProfile.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 40L));

        AssessmentScoringProfile profile = service.createScoringProfile(
                "topik score", "{\"scale\":300}", 1L);

        assertEquals(4, profile.getVersionNumber());
        assertFalse(profile.isEnabled());
        assertEquals("DRAFT", profile.getGovernanceStatus());
        when(scoringRepository.findById(40L)).thenReturn(Optional.of(profile));

        service.activateProfile(AssessmentGovernanceService.ProfileKind.SCORING, 40L, 1L);

        assertTrue(profile.isEnabled());
        assertEquals("ACTIVE", profile.getGovernanceStatus());
        verify(auditService).record(eq("SCORING_PROFILE_ACTIVATED"),
                eq("SCORING_PROFILE"), eq(40L), isNull(), eq(1L), isNull(),
                eq(false), isNull(), isNull(), anyString());
    }

    private void allowGovernance(Long actorId) {
        doNothing().when(authorizationService)
                .requireGlobal(actorId, PracticeAction.GOVERNANCE_MANAGE);
    }

    private static <T> T withId(T entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
        return entity;
    }
}
