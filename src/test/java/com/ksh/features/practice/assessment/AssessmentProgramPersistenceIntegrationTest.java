package com.ksh.features.practice.assessment;

import com.ksh.features.practice.assessment.repository.AssessmentProgramRepository;
import com.ksh.features.practice.assessment.repository.AssessmentProgramVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AssessmentProgramPersistenceIntegrationTest {

    @Autowired
    private AssessmentProgramRepository programRepository;

    @Autowired
    private AssessmentProgramVersionRepository versionRepository;

    @Autowired
    private AssessmentProgramPolicyService policyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedsTopikAndCustomWithActiveImmutableVersions() {
        assertThat(programRepository.findById("TOPIK")).isPresent();
        assertThat(programRepository.findById("CUSTOM")).isPresent();
        Long activeVersionId = programRepository.findById("TOPIK").orElseThrow().getActiveVersionId();
        assertThat(versionRepository.findById(activeVersionId)).get()
                .extracting(version -> version.getVersionNumber(), version -> version.getStatus())
                .containsExactly(1, "ACTIVE");

        ResolvedAssessmentPolicy reading = policyService.resolve(
                "TOPIK", AssessmentSkill.READING, CanonicalQuestionType.SINGLE_CHOICE);
        assertThat(reading.scoringPolicyCode()).isEqualTo(ScoringPolicyCode.ALL_OR_NOTHING);
        assertThat(reading.scoringProfile()).isEqualTo(new ProfileReference("TOPIK_SINGLE_CHOICE", 1));

        assertThatThrownBy(() -> policyService.resolve(
                "TOPIK", AssessmentSkill.READING, CanonicalQuestionType.MULTIPLE_CHOICE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not enabled");
        ResolvedAssessmentPolicy customMultipleChoice = policyService.resolve(
                "CUSTOM", AssessmentSkill.READING, CanonicalQuestionType.MULTIPLE_CHOICE);
        assertThat(customMultipleChoice.scoringPolicyCode()).isEqualTo(ScoringPolicyCode.ALL_OR_NOTHING);
        assertThat(customMultipleChoice.scoringProfile()).isNull();
    }

    @Test
    void versionedConfigurationTablesHaveRequiredUniqueConstraints() {
        assertUniqueIndex("assessment_program_versions", "uk_apv_program_version");
        assertUniqueIndex("assessment_scoring_profiles", "uk_ascp_code_version");
        assertUniqueIndex("assessment_prompt_profiles", "uk_app_code_version");
        assertUniqueIndex("assessment_rubric_profiles", "uk_arp_code_version");
        assertUniqueIndex("assessment_question_type_policies", "uk_aqtp_program_skill_type");
    }

    private void assertUniqueIndex(String table, String index) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                  AND non_unique = 0
                """, Integer.class, table, index);
        assertThat(count).isNotNull().isPositive();
    }
}
