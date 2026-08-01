package com.ksh.features.practice.ai.readinglistening;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeObjectiveExplanationEditorialMigrationTest {

    private static final Path V73 = Path.of(
            "src/main/resources/db/migration/"
                    + "V73__practice_objective_explanation_editorial_authority.sql");

    @Test
    void forwardMigrationAddsTypedStrategyAndAppendOnlyEditorialAuthority()
            throws Exception {
        String migration = Files.readString(V73);

        assertThat(migration)
                .contains(
                        "ALTER TABLE practice_questions",
                        "ALTER TABLE practice_question_versions",
                        "explanation_strategy_registry_version VARCHAR(64)",
                        "explanation_strategy_code VARCHAR(64)",
                        "explanation_strategy_version VARCHAR(32)",
                        "CREATE TABLE practice_explanation_editorial_revisions",
                        "UNIQUE KEY uk_practice_explanation_editorial_revision",
                        "FOREIGN KEY (draft_id) REFERENCES practice_drafts(id)",
                        "FOREIGN KEY (created_by) REFERENCES users(id)",
                        "FOREIGN KEY (approved_by) REFERENCES users(id)",
                        "'GENERATED_DRAFT', 'APPROVED', 'INVALIDATED'")
                .doesNotContain(
                        "DROP TABLE",
                        "DROP COLUMN",
                        "TRUNCATE",
                        "DELETE FROM");
    }
}
