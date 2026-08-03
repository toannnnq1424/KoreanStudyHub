package com.ksh.features.practice.ai.speaking;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DirectAudioGrantManagerMigrationStaticContractTest {

    @Test
    void v89IsAppendOnlyAuthorityMetadataWithoutSeededIdentity() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V89__practice_speaking_audio_grant_manager_authorities.sql"));
        String lower = sql.toLowerCase();

        assertThat(sql)
                .contains("practice_speaking_audio_grant_manager_events")
                .contains("ACADEMIC_LEADER", "PRIVACY_RELEASE_OWNER")
                .contains("ASSIGNED", "REVOKED")
                .doesNotContain("INSERT INTO");
        assertThat(lower).doesNotContain(
                "alter table", "drop table", "truncate ", "delete from",
                "flyway_schema_history", "audio_bytes", "storage_key",
                "provider_request_id", "credential_secret");
    }
}
