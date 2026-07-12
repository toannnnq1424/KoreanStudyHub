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
    void phase11SchemaCarriesAuthoringStimulusScoreMultiTestAndPdfTargetContracts() {
        String currentVersion = jdbcTemplate.queryForObject("""
                SELECT version
                FROM flyway_schema_history
                WHERE success = 1
                ORDER BY installed_rank DESC
                LIMIT 1
                """, String.class);
        assertThat(currentVersion).isEqualTo("28");

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
        assertColumns("practice_pdf_import_sessions",
                "assessment_program_code", "assessment_program_version_id",
                "exam_template_code", "target_test_no", "target_skill",
                "target_lesson_code");
        assertColumns("assessment_programs", "enabled");
        assertColumns("assessment_exam_templates", "program_code");
        assertColumns("assessment_exam_template_versions", "program_version_id");

        Integer scenarioRoots = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM assessment_exam_templates", Integer.class);
        Integer versionedScenarioRoots = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT t.code)
                FROM assessment_exam_templates t
                JOIN assessment_programs p ON p.code = t.program_code
                JOIN assessment_exam_template_versions v
                  ON v.template_code = t.code
                 AND v.program_version_id IS NOT NULL
                WHERE JSON_VALID(t.config_json) = 1
                """, Integer.class);
        assertThat(scenarioRoots).isNotNull().isPositive();
        assertThat(versionedScenarioRoots).isEqualTo(scenarioRoots);

        Integer invalidActiveScenarioPointers = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM assessment_exam_templates t
                JOIN assessment_exam_template_versions v ON v.id = t.active_version_id
                JOIN assessment_program_versions p ON p.id = v.program_version_id
                WHERE v.template_code <> t.code
                   OR p.program_code <> t.program_code
                   OR t.program_version_id <> v.program_version_id
                """, Integer.class);
        assertThat(invalidActiveScenarioPointers).isZero();

        Integer unboundPdfImportSessions = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM practice_pdf_import_sessions
                WHERE assessment_program_code IS NULL
                   OR assessment_program_version_id IS NULL
                   OR exam_template_code IS NULL
                """, Integer.class);
        assertThat(unboundPdfImportSessions).isZero();

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

    @Test
    void phase12SchemaKeepsAttemptHistoryOnImmutableVersionsAndInstallsGovernanceBoundaries() {
        Integer legacyLiveTestForeignKey = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name = 'practice_attempts'
                  AND constraint_name = 'fk_pa_test'
                  AND constraint_type = 'FOREIGN KEY'
                """, Integer.class);
        assertThat(legacyLiveTestForeignKey).isZero();

        List<String> immutableAttemptForeignKeys = jdbcTemplate.queryForList("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name = 'practice_attempts'
                  AND constraint_type = 'FOREIGN KEY'
                """, String.class);
        assertThat(immutableAttemptForeignKeys).contains(
                "fk_pa_published_version",
                "fk_pa_set_version",
                "fk_pa_test_version",
                "fk_pa_section_version");

        Integer governanceTables = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                    'practice_authoring_collaborations',
                    'practice_governance_audit_events',
                    'assessment_exam_template_versions',
                    'practice_material_references',
                    'practice_asset_lifecycle_tasks'
                  )
                """, Integer.class);
        assertThat(governanceTables).isEqualTo(5);

        Integer phase12Permissions = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM permissions
                WHERE feature_key IN (
                    'practice.create', 'practice.read', 'practice.edit', 'practice.publish',
                    'practice.archive', 'practice.lock', 'practice.restore',
                    'practice.material.manage', 'practice.media.review',
                    'practice.governance.manage', 'practice.override'
                )
                """, Integer.class);
        assertThat(phase12Permissions).isEqualTo(11);

        Integer materialReferenceUniqueIndexes = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT index_name)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'practice_material_references'
                  AND index_name IN ('uk_practice_material_draft_ref', 'uk_practice_material_version_ref')
                  AND non_unique = 0
                """, Integer.class);
        assertThat(materialReferenceUniqueIndexes).isEqualTo(2);
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
