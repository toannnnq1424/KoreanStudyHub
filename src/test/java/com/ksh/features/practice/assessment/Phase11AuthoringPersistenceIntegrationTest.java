package com.ksh.features.practice.assessment;

import com.ksh.features.practice.assessment.persistence.AssessmentExamTemplate;
import com.ksh.features.practice.assessment.repository.AssessmentExamTemplateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class Phase11AuthoringPersistenceIntegrationTest {

    @Autowired
    private AssessmentExamTemplateRepository templateRepository;

    @Autowired
    private AssessmentProgramPolicyService policyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void phase11SeedsExamTemplatesAndFlexibleCustomPolicies() {
        assertThat(templateRepository.findAll())
                .extracting(AssessmentExamTemplate::getCode)
                .containsExactlyInAnyOrder("TOPIK_I", "TOPIK_II", "CUSTOM_FLEXIBLE");

        AssessmentExamTemplate custom = templateRepository.findById("CUSTOM_FLEXIBLE").orElseThrow();
        assertThat(custom.isEnabled()).isTrue();
        assertThat(custom.getConfigJson()).contains(
                "assessment-template-v1",
                "maxTests",
                "maxQuestions",
                "excelImportEnabled",
                "MULTIPLE_CHOICE",
                "TRUE_FALSE_NOT_GIVEN",
                "FILL_BLANK",
                "MATCHING");

        List<CanonicalQuestionType> objectiveTypes = List.of(
                CanonicalQuestionType.SINGLE_CHOICE,
                CanonicalQuestionType.MULTIPLE_CHOICE,
                CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN,
                CanonicalQuestionType.FILL_BLANK,
                CanonicalQuestionType.MATCHING);
        assertThat(objectiveTypes)
                .allSatisfy(type -> assertThat(policyService.resolve(
                        "CUSTOM", AssessmentSkill.READING, type).questionType()).isEqualTo(type))
                .allSatisfy(type -> assertThat(policyService.resolve(
                        "CUSTOM", AssessmentSkill.LISTENING, type).questionType()).isEqualTo(type));

        ResolvedAssessmentPolicy essay = policyService.resolve(
                "CUSTOM", AssessmentSkill.WRITING, CanonicalQuestionType.ESSAY);
        assertThat(essay.scoringProfile()).isEqualTo(new ProfileReference("CUSTOM_ESSAY", 1));
        assertThat(essay.promptProfile()).isEqualTo(new ProfileReference("CUSTOM_ESSAY", 1));
        assertThat(essay.rubricProfile()).isEqualTo(new ProfileReference("CUSTOM_ESSAY", 1));

        ResolvedAssessmentPolicy speaking = policyService.resolve(
                "CUSTOM", AssessmentSkill.SPEAKING, CanonicalQuestionType.SPEAKING);
        assertThat(speaking.scoringProfile()).isEqualTo(new ProfileReference("CUSTOM_SPEAKING", 1));

        assertThatThrownBy(() -> policyService.resolve(
                "TOPIK", AssessmentSkill.READING, CanonicalQuestionType.MULTIPLE_CHOICE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not enabled");
    }

    @Test
    void phase11SchemaCarriesAuthoringStimulusScoreAndMultiTestContracts() {
        String currentVersion = jdbcTemplate.queryForObject("""
                SELECT version
                FROM flyway_schema_history
                WHERE success = 1
                ORDER BY installed_rank DESC
                LIMIT 1
                """, String.class);
        assertThat(currentVersion).isEqualTo("26");

        Integer disabledExcelSkills = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM assessment_exam_templates
                WHERE (JSON_CONTAINS_PATH(config_json, 'one', '$.skills.WRITING')
                       AND JSON_EXTRACT(config_json, '$.skills.WRITING.excelImportEnabled') <> TRUE)
                   OR (JSON_CONTAINS_PATH(config_json, 'one', '$.skills.SPEAKING')
                       AND JSON_EXTRACT(config_json, '$.skills.SPEAKING.excelImportEnabled') <> TRUE)
                """, Integer.class);
        assertThat(disabledExcelSkills).isZero();

        assertColumns("practice_drafts",
                "draft_schema_version", "assessment_program_code",
                "assessment_program_version_id", "exam_template_code");
        assertColumns("practice_sets", "assessment_program_version_id", "exam_template_code");
        assertColumns("practice_set_versions", "assessment_program_version_id", "exam_template_code");
        assertColumns("practice_question_groups",
                "stimulus_type", "passage_text", "transcript_text",
                "image_url", "stimulus_provenance_json");
        assertColumns("practice_question_group_versions",
                "stimulus_type", "passage_text", "transcript_text",
                "image_url", "stimulus_provenance_json");
        assertColumns("practice_attempts", "score_unit", "earned_points", "score_percentage");

        Integer uniqueTemplateBindings = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'assessment_exam_templates'
                  AND index_name = 'uk_aet_program_category'
                  AND non_unique = 0
                """, Integer.class);
        assertThat(uniqueTemplateBindings).isNotNull().isPositive();

        Integer nullableLegacyBindings = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name IN ('practice_sets', 'practice_set_versions')
                  AND column_name IN ('assessment_program_version_id', 'exam_template_code')
                  AND is_nullable = 'YES'
                """, Integer.class);
        assertThat(nullableLegacyBindings).isEqualTo(4);

        Integer legacyTestVersionForeignKey = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name = 'practice_test_versions'
                  AND constraint_name = 'fk_ptv_test'
                  AND constraint_type = 'FOREIGN KEY'
                """, Integer.class);
        assertThat(legacyTestVersionForeignKey).isZero();

        Integer staleDraftContracts = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM practice_drafts
                WHERE draft_schema_version <> 'practice-draft-v3'
                """, Integer.class);
        assertThat(staleDraftContracts).isZero();
    }

    private void assertColumns(String tableName, String... expectedColumns) {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ?
                """, String.class, tableName);
        assertThat(columns).contains(expectedColumns);
    }
}
