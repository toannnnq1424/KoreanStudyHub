package com.ksh.features.practice.assessment;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeExtendedQuestionTypesMigrationTest {

    @Test
    void v75AddsOnlyCanonicalMultipleAnswerAndMatchingTypes() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V75__practice_objective_extended_question_types.sql"));

        assertThat(migration)
                .contains("ALTER TABLE practice_questions")
                .contains("DROP CHECK chk_pq_type")
                .contains("ALTER TABLE practice_question_versions")
                .contains("DROP CHECK chk_pqv_type")
                .contains("'MULTIPLE_ANSWER'")
                .contains("'MATCHING'")
                .doesNotContain("'ORDERING'")
                .doesNotContain("'MATCHING_INFORMATION'");
    }
}
