package com.ksh.features.practice.manage.authoringcandidate;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeAuthoringCandidateMigrationTest {

    private static final Path MIGRATIONS = Path.of(
            "src/main/resources/db/migration");
    private static final Pattern VERSION = Pattern.compile("V(\\d+)__.*\\.sql");

    @Test
    void reconciledChainIsUniqueContinuousAndContainsV83() throws Exception {
        Map<Integer, Integer> counts = new HashMap<>();
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            files.map(path -> path.getFileName().toString())
                    .map(VERSION::matcher)
                    .filter(Matcher::matches)
                    .map(matcher -> Integer.parseInt(matcher.group(1)))
                    .forEach(version -> counts.merge(version, 1, Integer::sum));
        }

        int current = counts.keySet().stream().mapToInt(Integer::intValue)
                .max().orElseThrow();
        assertThat(current).isGreaterThanOrEqualTo(83);
        assertThat(counts).hasSize(current);
        assertThat(counts.keySet()).containsExactlyInAnyOrderElementsOf(
                java.util.stream.IntStream.rangeClosed(1, current).boxed().toList());
        assertThat(counts.values()).containsOnly(1);
    }

    @Test
    void formerMainMigrationBytesAreUnchangedAfterApprovedOffset() throws Exception {
        Map<String, String> expected = Map.of(
                "V76__practice_evaluation_contract_identity_capacity.sql",
                "f07c0ea1a78f2dc467e5eb82e03c2de698c3e6a4be3450181fcd5a4e6e153922",
                "V77__discovery_ai_editorial.sql",
                "eef20a33ba0a5a04e51a3c038066bbfb04c402dd89cdc39ca32aa652f5af98b8",
                "V78__news_run_traceability.sql",
                "5fb8c6efa3e32e5f4a1bfae0874e01b98ac63194b0741005d348917d30524618",
                "V79__backfill_news_ai_run_trace.sql",
                "6439f7ea24f882fb7da7125017cc929e25db96016511bbdf0aec2cf13c892ee6",
                "V80__class_approval_lifecycle.sql",
                "6640bec6daebb1afd226b49bb9ae87509a5f97418c8c7c04a684e8581abf35fa",
                "V81__seed_ai_flashcard_generator_prompt.sql",
                "7da3da706dda38b1c2b16ce25e086644e3db1dfb374f4541bc5f24b7fbedd435",
                "V82__refine_ai_generation_prompts.sql",
                "5b81c392ce68cfe603bad71b1f9cd07d2170e9d155f7f39af3a50729753431a1");

        for (Map.Entry<String, String> entry : expected.entrySet()) {
            byte[] bytes = Files.readAllBytes(MIGRATIONS.resolve(entry.getKey()));
            assertThat(java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)))
                    .isEqualTo(entry.getValue());
        }
    }

    @Test
    void v83IsAdditiveAndOwnsCandidateAndApplyLedgerOnly() throws Exception {
        String sql = Files.readString(MIGRATIONS.resolve(
                "V83__practice_authoring_candidate_foundation.sql"));

        assertThat(sql).contains(
                "CREATE TABLE practice_authoring_candidates",
                "CREATE TABLE practice_authoring_candidate_apply_events",
                "lock_version BIGINT NOT NULL DEFAULT 0",
                "uk_practice_authoring_candidate_idempotency",
                "uk_practice_authoring_candidate_apply_request",
                "FOREIGN KEY (target_draft_id) REFERENCES practice_drafts(id)",
                "'READY_TO_APPLY', 'APPLIED'",
                "result IN ('DRAFT_APPLIED', 'CONFLICT', 'REJECTED')")
                .doesNotContain(
                        "DROP TABLE", "DROP COLUMN", "TRUNCATE",
                        "DELETE FROM", "UPDATE practice_drafts");
    }
}
