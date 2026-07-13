package com.ksh.features.practice.assessment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssessmentAuthoringCatalogServiceTest {

    private final PracticeContentRules rules = new PracticeContentRules();
    private final AssessmentAuthoringCatalogService service =
            new AssessmentAuthoringCatalogService(rules);

    @Test
    void catalogIsCodeOwnedAndExposesOneImplicitKshTemplate() {
        AssessmentAuthoringCatalogService.AuthoringCatalog catalog = service.catalog();

        assertThat(catalog.schemaVersion()).isEqualTo("practice-authoring-catalog-v2");
        assertThat(catalog.templates()).containsExactly(service.defaultTemplate());
        assertThat(service.defaultTemplate().code()).isEqualTo("KSH_DEFAULT");
    }

    @Test
    void catalogUsesTheFixedFiveTypeSkillMatrix() {
        AssessmentAuthoringCatalogService.ExamTemplatePolicy template = service.defaultTemplate();

        assertThat(template.requireSkill("READING").questionTypes())
                .containsExactly("SINGLE_CHOICE", "FILL_BLANK", "TRUE_FALSE_NOT_GIVEN");
        assertThat(template.requireSkill("LISTENING").questionTypes())
                .containsExactly("SINGLE_CHOICE", "FILL_BLANK", "TRUE_FALSE_NOT_GIVEN");
        assertThat(template.requireSkill("WRITING").questionTypes()).containsExactly("ESSAY");
        assertThat(template.requireSkill("SPEAKING").questionTypes()).containsExactly("SPEAKING");
        assertThat(template.skills()).containsOnlyKeys("READING", "LISTENING", "WRITING", "SPEAKING");
        assertThat(template.requireSkill("READING").questionPolicies()).containsOnlyKeys(
                "SINGLE_CHOICE", "FILL_BLANK", "TRUE_FALSE_NOT_GIVEN");
    }
}
