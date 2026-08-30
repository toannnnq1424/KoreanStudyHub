package com.ksh.features.lessons;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** V130 must remain an ALTER/backfill-only provenance migration. */
class LessonAttachmentOriginScopeMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V130__lesson_attachment_origin_scope.sql");

    @Test
    void migration_adds_bounded_origin_and_backfills_only_exact_snapshots() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains(
                        "ALTER TABLE lesson_attachments",
                        "origin_scope VARCHAR(24) NOT NULL",
                        "DEFAULT 'CLASS_PRIVATE'",
                        "idx_la_lesson_origin",
                        "idx_la_lesson_asset_origin",
                        "chk_la_origin_scope",
                        "'CLASS_PRIVATE', 'CANONICAL_TEMPLATE'",
                        "chk_la_canonical_library",
                        "JOIN lessons lesson ON lesson.id = attachment.lesson_id",
                        "lesson.source_lesson_template_id IS NOT NULL",
                        "attachment.library_asset_id IS NOT NULL",
                        "SET attachment.origin_scope = 'CANONICAL_TEMPLATE'")
                .doesNotContain(
                        "CREATE TABLE",
                        "DROP TABLE",
                        "UPDATE lessons",
                        "lecturer_assets");
    }
}
