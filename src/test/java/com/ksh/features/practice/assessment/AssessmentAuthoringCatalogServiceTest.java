package com.ksh.features.practice.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.assessment.persistence.AssessmentExamTemplate;
import com.ksh.features.practice.assessment.persistence.AssessmentProgramVersion;
import com.ksh.features.practice.assessment.persistence.AssessmentProgram;
import com.ksh.features.practice.assessment.repository.AssessmentExamTemplateRepository;
import com.ksh.features.practice.assessment.repository.AssessmentProgramRepository;
import com.ksh.features.practice.assessment.repository.AssessmentProgramVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssessmentAuthoringCatalogServiceTest {

    @Test
    void catalogExposesTemplateBoundScoringChoicesAndApprovedProfiles() throws Exception {
        AssessmentExamTemplateRepository templates = mock(AssessmentExamTemplateRepository.class);
        AssessmentProgramVersionRepository versions = mock(AssessmentProgramVersionRepository.class);
        AssessmentProgramPolicyService policies = mock(AssessmentProgramPolicyService.class);
        AssessmentExamTemplate entity = templateEntity();
        AssessmentProgramVersion version = new AssessmentProgramVersion(
                "CUSTOM", 1, "Custom", "ACTIVE", "ko");
        ReflectionTestUtils.setField(version, "id", 12L);
        when(templates.findByCodeAndEnabledTrue("CUSTOM_FLEXIBLE")).thenReturn(Optional.of(entity));
        when(versions.findById(12L)).thenReturn(Optional.of(version));
        when(policies.resolve("CUSTOM", AssessmentSkill.READING, CanonicalQuestionType.MULTIPLE_CHOICE))
                .thenReturn(new ResolvedAssessmentPolicy(
                        "CUSTOM", 12L, 1, AssessmentSkill.READING,
                        AssessmentDeliveryMode.SKILL_SPECIFIC,
                        CanonicalQuestionType.MULTIPLE_CHOICE,
                        ScoringPolicyCode.ALL_OR_NOTHING,
                        null,
                        new ProfileReference("CUSTOM_PROMPT", 2),
                        new ProfileReference("CUSTOM_RUBRIC", 3)));
        AssessmentAuthoringCatalogService service = new AssessmentAuthoringCatalogService(
                templates, versions, policies, new ObjectMapper());

        AssessmentAuthoringCatalogService.ExamTemplatePolicy template =
                service.requireTemplate("custom_flexible");
        AssessmentAuthoringCatalogService.SkillAuthoringPolicy reading = template.requireSkill("reading");
        AssessmentAuthoringCatalogService.QuestionAuthoringPolicy multiple =
                reading.questionPolicy("MULTIPLE_CHOICE");

        assertThat(reading.pointsEditable()).isTrue();
        assertThat(multiple.allowedScoringPolicyCodes()).containsExactly(
                "ALL_OR_NOTHING", "PARTIAL_BY_CORRECT_OPTION_WITH_WRONG_ZERO");
        assertThat(multiple.promptProfile()).isEqualTo(new ProfileReference("CUSTOM_PROMPT", 2));
        assertThat(multiple.rubricProfile()).isEqualTo(new ProfileReference("CUSTOM_RUBRIC", 3));
    }

    @Test
    void archivedProgramIsNotExposedByAuthoringCatalog() throws Exception {
        AssessmentExamTemplateRepository templates = mock(AssessmentExamTemplateRepository.class);
        AssessmentProgramVersionRepository versions = mock(AssessmentProgramVersionRepository.class);
        AssessmentProgramRepository programs = mock(AssessmentProgramRepository.class);
        AssessmentProgramPolicyService policies = mock(AssessmentProgramPolicyService.class);
        AssessmentExamTemplate entity = templateEntity();
        ReflectionTestUtils.setField(entity, "programCode", "CUSTOM");
        AssessmentProgram program = new AssessmentProgram("CUSTOM", 12L);
        program.setEnabled(false);
        when(templates.findByEnabledTrueOrderByDisplayNameAsc()).thenReturn(List.of(entity));
        when(programs.findById("CUSTOM")).thenReturn(Optional.of(program));
        AssessmentAuthoringCatalogService service = new AssessmentAuthoringCatalogService(
                templates, null, versions, programs, policies, new ObjectMapper());

        AssessmentAuthoringCatalogService.AuthoringCatalog catalog = service.catalog();

        assertThat(catalog.templates()).isEmpty();
    }

    private static AssessmentExamTemplate templateEntity() throws Exception {
        var constructor = AssessmentExamTemplate.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        AssessmentExamTemplate template = constructor.newInstance();
        ReflectionTestUtils.setField(template, "code", "CUSTOM_FLEXIBLE");
        ReflectionTestUtils.setField(template, "programVersionId", 12L);
        ReflectionTestUtils.setField(template, "displayName", "Custom");
        ReflectionTestUtils.setField(template, "categoryCode", "CUSTOM");
        ReflectionTestUtils.setField(template, "enabled", true);
        ReflectionTestUtils.setField(template, "configJson", """
                {
                  "schemaVersion":"assessment-template-v1",
                  "skills":{
                    "READING":{
                      "durationMinutes":40,
                      "defaultPoints":1,
                      "pointsEditable":true,
                      "questionTypes":["MULTIPLE_CHOICE"],
                      "scoringPolicies":{
                        "MULTIPLE_CHOICE":[
                          "ALL_OR_NOTHING",
                          "PARTIAL_BY_CORRECT_OPTION_WITH_WRONG_ZERO"
                        ]
                      }
                    }
                  }
                }
                """);
        return template;
    }
}
