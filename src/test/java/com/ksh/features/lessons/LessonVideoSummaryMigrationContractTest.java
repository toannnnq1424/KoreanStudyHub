package com.ksh.features.lessons;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LessonVideoSummaryMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V127__lesson_video_summary.sql");

    @Test
    void migration_adds_only_nullable_summary_columns_without_new_tables() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("ALTER TABLE lesson_templates",
                        "ALTER TABLE lessons",
                        "video_summary VARCHAR(1000) NULL")
                .doesNotContain("CREATE TABLE", "DROP TABLE", "practice_");
        assertThat(count(sql, "video_summary VARCHAR(1000) NULL")).isEqualTo(2);
    }

    private static int count(String value, String token) {
        return value.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }
}
