package com.ksh.features.practice.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PracticeSpeakingReviewerAccessAuditSchemaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void freshFlywayChainCreatesBoundedMetadataOnlyAuthorizedAuditSchema() {
        Integer migrationCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                Integer.class);
        String version = jdbc.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history "
                        + "WHERE success = 1",
                String.class);
        List<String> columns = jdbc.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'practice_speaking_audio_reviewer_access_events'
                ORDER BY ordinal_position
                """, String.class);
        List<String> checks = jdbc.queryForList("""
                SELECT check_clause
                FROM information_schema.check_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_name LIKE 'chk_psarce_%'
                ORDER BY constraint_name
                """, String.class);

        assertThat(migrationCount).isEqualTo(96);
        assertThat(version).isEqualTo("96");
        assertThat(columns).contains("event_key", "reviewer_id", "attempt_id",
                        "question_id", "media_id", "observation_key", "purpose_code",
                        "action_code", "outcome_code", "retention_policy_id",
                        "occurred_at", "delete_after", "recorded_at")
                .doesNotContain("audio_bytes", "storage_key", "playback_url",
                        "access_token", "provider_payload", "score_value",
                        "ip_address", "user_agent");
        assertThat(String.join(" ", checks))
                .contains("AUTHORIZED", "INSPECTION_METADATA", "PLAYBACK_OPEN",
                        "PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION");
    }
}
