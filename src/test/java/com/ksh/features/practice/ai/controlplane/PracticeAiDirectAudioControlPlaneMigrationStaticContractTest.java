package com.ksh.features.practice.ai.controlplane;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeAiDirectAudioControlPlaneMigrationStaticContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V90__practice_speaking_direct_audio_control_plane.sql");

    @Test
    void v105KeepsOptionalLegacyColumnsAndRequiresOnlyMinimalProviderPolicy()
            throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION")
                .contains("directAudioInput")
                .contains("region_evidence_id", "non_training_evidence_id",
                        "retention_evidence_id", "deletion_sla_evidence_id")
                .contains("chk_practice_ai_direct_audio_capability",
                        "chk_practice_ai_direct_audio_policy")
                .contains("NULLIF(TRIM(non_training_evidence_id), '') IS NOT NULL")
                .contains("NULLIF(TRIM(retention_evidence_id), '') IS NOT NULL")
                .doesNotContain(
                        "NULLIF(TRIM(region_evidence_id), '') IS NOT NULL",
                        "NULLIF(TRIM(deletion_sla_evidence_id), '') IS NOT NULL")
                .doesNotContain("INSERT INTO", "credential_secret", "/models",
                        "api.openai.com", "generativelanguage.googleapis.com");
    }
}
