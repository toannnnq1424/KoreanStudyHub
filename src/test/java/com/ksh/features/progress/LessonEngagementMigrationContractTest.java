package com.ksh.features.progress;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LessonEngagementMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V128__lesson_engagement_checklist.sql");

    @Test
    void migration_extends_existing_progress_row_without_creating_a_table() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains(
                        "ALTER TABLE learning_progress",
                        "content_engaged_seconds INT NOT NULL DEFAULT 0",
                        "video_engaged_seconds INT NOT NULL DEFAULT 0",
                        "attachments_engaged_seconds INT NOT NULL DEFAULT 0",
                        "active_engagement_tab VARCHAR(20) NULL",
                        "active_engagement_checkpoint_at DATETIME(6) NULL",
                        "content_engaged_seconds BETWEEN 0 AND 60",
                        "active_engagement_tab IN ('CONTENT', 'VIDEO', 'ATTACHMENTS')")
                .doesNotContain("CREATE TABLE", "DROP TABLE");
    }

    @Test
    void migration_preserves_historical_completion_with_matching_checklist_evidence()
            throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains(
                "content_engaged_seconds = 60",
                "video_engaged_seconds = 60",
                "attachments_engaged_seconds = 60",
                "WHERE status = 'COMPLETED'");
    }
}
