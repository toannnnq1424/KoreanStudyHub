package com.ksh.features.practice.service;

import com.ksh.entities.PracticeAttemptEvaluationJob;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeEvaluationContractIdentityMigrationTest {

    private static final Path V62 = Path.of(
            "src/main/resources/db/migration/"
                    + "V62__practice_attempt_integrity_gate.sql");
    private static final Path V67 = Path.of(
            "src/main/resources/db/migration/"
                    + "V67__practice_evaluation_contract_identity_capacity.sql");

    @Test
    void forwardMigrationExpandsIdentityWithoutRewritingAppliedV62()
            throws Exception {
        String v62 = Files.readString(V62);
        String v67 = Files.readString(V67);

        assertThat(v62).contains(
                "evaluation_contract_identity VARCHAR(500) NOT NULL");
        assertThat(v67).contains(
                "ALTER TABLE practice_attempt_evaluation_jobs",
                "evaluation_contract_identity VARCHAR("
                        + PracticeAttemptEvaluationJob
                                .MAX_EVALUATION_CONTRACT_IDENTITY_LENGTH
                        + ") NOT NULL");
        assertThat(v67).doesNotContain(
                "DROP TABLE",
                "DROP COLUMN",
                "DELETE FROM",
                "TRUNCATE");
    }
}
