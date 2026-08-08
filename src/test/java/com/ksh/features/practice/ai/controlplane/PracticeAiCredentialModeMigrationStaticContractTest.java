package com.ksh.features.practice.ai.controlplane;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeAiCredentialModeMigrationStaticContractTest {

    @Test
    void v91SeparatesStaticBearerFromSecretlessAdcWithoutSeedingProfiles()
            throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V106__practice_ai_profile_credential_modes.sql"));

        assertThat(sql)
                .contains("credential_mode")
                .contains("STATIC_BEARER")
                .contains("GOOGLE_CLOUD_ADC")
                .contains("credential_secret IS NULL")
                .contains("NULLIF(TRIM(credential_secret), '') IS NOT NULL")
                .doesNotContain("INSERT INTO", "UPDATE ", "DELETE FROM", "DROP TABLE");
    }
}
