package com.ksh.features.lessons;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LessonTemplateSnapshotProvenanceMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V129__lesson_template_snapshot_provenance.sql");

    @Test
    void migration_adds_nullable_exact_provenance_without_a_new_table() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains(
                        "source_lesson_template_id BIGINT NULL",
                        "idx_lessons_source_template",
                        "fk_lessons_source_template",
                        "REFERENCES lesson_templates(id)",
                        "Deliberately do not backfill legacy snapshots")
                .doesNotContain(
                        "CREATE TABLE",
                        "DROP TABLE",
                        "UPDATE lessons",
                        "JOIN lesson_templates",
                        "activity_lessons",
                        "HAVING COUNT(*)");
    }
}
