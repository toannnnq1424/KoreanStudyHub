package com.ksh.features.practice.ai.speaking;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DirectAudioAuthorizationJdbcStoreTest {

    @Test
    void reviewerMutationCannotAcquireLockOutsideTransaction() {
        DirectAudioAuthorizationJdbcStore store =
                new DirectAudioAuthorizationJdbcStore(mock(JdbcTemplate.class));

        assertThatThrownBy(() -> store.reviewerGrantForUpdate("grant-0001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction");
    }

    @Test
    void adapterUsesOnlyV88MetadataAndFailClosedQueries() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/ai/speaking/"
                        + "DirectAudioAuthorizationJdbcStore.java"));

        assertThat(source)
                .contains("practice_speaking_audio_consent_events")
                .contains("practice_speaking_audio_reviewer_grants")
                .contains("ORDER BY occurred_at DESC, id DESC")
                .contains("revoked_at IS NULL AND expires_at > ?")
                .contains("FOR UPDATE")
                .doesNotContain("audioBytes", "storageKey", "providerRequestId",
                        "credential", "SELECT *");
    }
}
