package com.ksh.features.practice.manage.seed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "app.practice.attempt-evaluation.worker-enabled=false",
        "app.practice.attempt-deadline.worker-enabled=false",
        "app.practice.speaking-media.cleanup-worker-enabled=false",
        "app.practice.speaking-prompt-authoring.worker-enabled=false",
        "app.practice.asset-lifecycle.worker-enabled=false"
})
@EnabledIfEnvironmentVariable(
        named = "RUN_TOPIK35_CANDIDATE_DB_TESTS", matches = "true")
class PracticeTopik35CandidateImporterPersistenceIntegrationTest {

    private static final Path OPERATIONS = Path.of("docs/operations");

    @Autowired PracticeTopik35CandidateImporter importer;
    @Autowired JdbcTemplate jdbc;

    @Test
    void currentCanonicalPackagesCreateThenReuseOneDisabledCandidate() {
        String catalog = jdbc.queryForObject("SELECT DATABASE()", String.class);
        assertThat(catalog).startsWith("ksh_test_topik35_candidate_");
        assertThat(jdbc.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) "
                        + "FROM flyway_schema_history WHERE success = 1",
                Integer.class)).isEqualTo(96);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0",
                Integer.class)).isZero();

        Map<String, Long> before = dataPlaneCounts();
        var first = importer.importCandidate(OPERATIONS, 1L);
        var second = importer.importCandidate(OPERATIONS, 1L);
        Map<String, Long> after = dataPlaneCounts();

        assertThat(first.blockers()).isEmpty();
        assertThat(first.status()).isEqualTo(
                PracticeTopik35CandidateImporter.ImportStatus.CREATED);
        assertThat(second.status()).isEqualTo(
                PracticeTopik35CandidateImporter.ImportStatus.REUSED);
        assertThat(second.candidateDraftId())
                .isEqualTo(first.candidateDraftId());
        assertThat(second.identityDigest()).isEqualTo(first.identityDigest());
        assertThat(first.readingQuestionCount()).isEqualTo(50);
        assertThat(first.listeningQuestionCount()).isEqualTo(50);
        assertThat(first.writingQuestionCount()).isEqualTo(4);
        assertThat(first.providerCallCount()).isZero();
        Map<String, Long> expected = new LinkedHashMap<>(before);
        expected.compute("practice_drafts", (ignored, count) -> count + 1);
        assertThat(after).isEqualTo(expected);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM practice_drafts "
                        + "WHERE id = ? AND status = 'DRAFT' "
                        + "AND published_set_id IS NULL "
                        + "AND creation_method = 'CANONICAL_SEED'",
                Integer.class, first.candidateDraftId())).isEqualTo(1);
        String draftJson = jdbc.queryForObject(
                "SELECT draft_json FROM practice_drafts WHERE id = ?",
                String.class,
                first.candidateDraftId());
        assertThat(draftJson)
                .contains("\"listeningTimingState\":\"PENDING_OPTIONAL_POST_TEST_QA\"")
                .contains("\"listeningTimingRequiredForCandidate\":false")
                .contains("\"listeningTimingRequiredForPublication\":false")
                .contains("\"timestampAutoNavigation\":false")
                .contains("\"seekAllowed\":false")
                .contains("\"replayAllowed\":false")
                .contains("\"schemaVersion\":\"answer-spec-v2\"")
                .contains("\"evaluationMode\":\"MANUAL_OR_EXPERIMENTAL_UNSCORED\"")
                .doesNotContain("/Users/", "file://", "r2://");
    }

    private Map<String, Long> dataPlaneCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : new String[] {
                "practice_drafts",
                "practice_authoring_candidates",
                "practice_sets",
                "practice_tests",
                "practice_sections",
                "practice_question_groups",
                "practice_questions",
                "practice_published_versions",
                "practice_set_versions",
                "practice_test_versions",
                "practice_section_versions",
                "practice_question_group_versions",
                "practice_question_versions",
                "practice_ai_execution_audits",
                "practice_ai_capability_test_runs"
        }) {
            counts.put(table, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table, Long.class));
        }
        return counts;
    }
}
