package com.ksh.features.practice.ai.speaking;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DirectAudioAuthorizationMigrationStaticContractTest {

    @Test
    void v88IsForwardOnlyMetadataOnlyAndPinsDirectAudioPurpose() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V88__practice_speaking_direct_audio_authorization.sql"));
        String lower = sql.toLowerCase();

        assertThat(sql)
                .contains("practice_speaking_audio_consent_events")
                .contains("practice_speaking_audio_reviewer_grants")
                .contains(DirectAudioSpeakingEvaluationService.PURPOSE)
                .contains("GRANTED", "WITHDRAWN", "expires_at", "revoked_at")
                .doesNotContain("PRACTICE_SPEAKING_EVALUATION'");
        assertThat(lower)
                .doesNotContain("alter table", "drop table", "drop column",
                        "truncate ", "delete from", "flyway_schema_history",
                        "audio_bytes", "storage_key", "provider_request_id",
                        "credential_secret");
    }
}
