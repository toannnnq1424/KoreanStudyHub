package com.ksh.features.practice.manage.seed;

import com.ksh.features.practice.manage.service.LecturerAssetService;
import com.ksh.features.practice.manage.service.PracticeMaterialAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @Autowired PracticeTopik35CheckAudioBinder checkAudioBinder;
    @Autowired PracticeMaterialAccessService materialAccess;
    @Autowired LecturerAssetService assetService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void currentCanonicalPackagesCreateThenReuseOneDisabledCandidate()
            throws Exception {
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
        Map<String, Long> afterCandidate = dataPlaneCounts();

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
        assertThat(afterCandidate).isEqualTo(expected);
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

        assertThatThrownBy(() -> checkAudioBinder.bind(
                first.candidateDraftId(), 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TOPIK35_CHECK_AUDIO_OWNER_MISMATCH");

        PracticeTopik35CheckAudioBinder.BindResult audioCreated =
                checkAudioBinder.bind(first.candidateDraftId(), 1L);
        PracticeTopik35CheckAudioBinder.BindResult audioReplay =
                checkAudioBinder.bind(first.candidateDraftId(), 1L);

        assertThat(audioCreated.status()).isEqualTo(
                PracticeTopik35CheckAudioBinder.BindStatus.CREATED);
        assertThat(audioReplay.status()).isEqualTo(
                PracticeTopik35CheckAudioBinder.BindStatus.REUSED);
        assertThat(audioReplay.assetId()).isEqualTo(audioCreated.assetId());
        assertThat(audioCreated.sha256()).isEqualTo(
                PracticeTopik35CheckAudioBinder.FULL_SHA256);
        assertThat(audioCreated.logicalKey()).isEqualTo(
                "practice-seed/topik35-v1/review/artifact/"
                        + PracticeTopik35CheckAudioBinder.FULL_SHA256
                        + ".wav");
        assertThat(audioCreated.materialReference()).isEqualTo(
                "/practice/materials/" + audioCreated.assetId()
                        + "/content");
        assertThat(dataPlaneCounts()).isEqualTo(withDeltas(
                before, Map.of(
                        "practice_drafts", 1L,
                        "lecturer_assets", 1L,
                        "practice_material_references", 1L)));

        PracticeMaterialAccessService.MaterialContent ownerContent =
                materialAccess.load(audioCreated.assetId(), 1L);
        byte[] delivered = ownerContent.resource().getInputStream()
                .readAllBytes();
        assertThat(delivered).hasSize(
                (int) PracticeTopik35CheckAudioBinder.FILE_SIZE);
        assertThat(sha256(delivered)).isEqualTo(
                PracticeTopik35CheckAudioBinder.FULL_SHA256);
        assertThatThrownBy(() -> materialAccess.load(
                audioCreated.assetId(), 999_999L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> assetService.deleteAsset(
                audioCreated.assetId(), 2L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> assetService.deleteAsset(
                audioCreated.assetId(), 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đang được");

        PracticeTopik35CheckAudioBinder.WithdrawResult withdrawn =
                checkAudioBinder.withdraw(first.candidateDraftId(), 1L);
        assertThat(withdrawn.assetId()).isEqualTo(audioCreated.assetId());
        assertThat(withdrawn.candidateCheckAudioState())
                .isEqualTo("WITHDRAWN_MATERIAL_REQUIRED");
        assetService.deleteAsset(audioCreated.assetId(), 1L);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM lecturer_assets WHERE id = ?",
                String.class, audioCreated.assetId()))
                .isEqualTo("DELETION_PENDING");
        assertThatThrownBy(() -> materialAccess.load(
                audioCreated.assetId(), 1L))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);

        PracticeTopik35CheckAudioBinder.BindResult rebound =
                checkAudioBinder.bind(first.candidateDraftId(), 1L);
        PracticeTopik35CheckAudioBinder.BindResult reboundReplay =
                checkAudioBinder.bind(first.candidateDraftId(), 1L);
        assertThat(rebound.status()).isEqualTo(
                PracticeTopik35CheckAudioBinder.BindStatus.CREATED);
        assertThat(rebound.assetId()).isNotEqualTo(audioCreated.assetId());
        assertThat(reboundReplay.status()).isEqualTo(
                PracticeTopik35CheckAudioBinder.BindStatus.REUSED);
        assertThat(reboundReplay.assetId()).isEqualTo(rebound.assetId());

        String reboundDraft = jdbc.queryForObject(
                "SELECT draft_json FROM practice_drafts WHERE id = ?",
                String.class, first.candidateDraftId());
        assertThat(reboundDraft)
                .contains("\"candidateCheckAudioState\":\"BOUND_OWNER_SCOPED\"")
                .contains("\"checkAudioReference\":\"/practice/materials/"
                        + rebound.assetId() + "/content\"")
                .contains("\"listeningTimingRequiredForPublication\":false")
                .contains("\"evaluationMode\":\"MANUAL_OR_EXPERIMENTAL_UNSCORED\"")
                .doesNotContain("/Users/", "file://", "r2://");
        assertThat(dataPlaneCounts()).isEqualTo(withDeltas(
                before, Map.of(
                        "practice_drafts", 1L,
                        "lecturer_assets", 2L,
                        "practice_material_references", 1L,
                        "practice_asset_lifecycle_tasks", 1L)));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM lecturer_assets "
                        + "WHERE id = ? AND owner_lecturer_id = 1 "
                        + "AND source_type = 'MANUAL_UPLOAD' "
                        + "AND asset_type = 'AUDIO' "
                        + "AND mime_type = 'audio/wav' "
                        + "AND content_verified = 1 "
                        + "AND status = 'ACTIVE' AND visibility = 'PRIVATE' "
                        + "AND sha256 = ? AND storage_provider = 'LOCAL' "
                        + "AND storage_profile_code = 'PRACTICE_AUTHORING'",
                Integer.class, rebound.assetId(),
                PracticeTopik35CheckAudioBinder.FULL_SHA256)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM storage_profiles "
                        + "WHERE profile_code = 'PRACTICE_AUTHORING' "
                        + "AND backend = 'LOCAL' AND enabled = 1",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM practice_sets "
                        + "WHERE CONCAT_WS(' ', title, description) LIKE '%TOPIK 35%'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM practice_ai_execution_audits",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM practice_ai_capability_test_runs",
                Integer.class)).isZero();
    }

    private Map<String, Long> dataPlaneCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : new String[] {
                "practice_drafts",
                "lecturer_assets",
                "practice_material_references",
                "practice_asset_lifecycle_tasks",
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

    private static Map<String, Long> withDeltas(
            Map<String, Long> baseline,
            Map<String, Long> deltas) {
        Map<String, Long> result = new LinkedHashMap<>(baseline);
        deltas.forEach((table, delta) ->
                result.compute(table, (ignored, count) -> count + delta));
        return result;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
