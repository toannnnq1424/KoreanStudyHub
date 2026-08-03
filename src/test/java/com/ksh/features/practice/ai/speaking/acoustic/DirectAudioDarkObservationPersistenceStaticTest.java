package com.ksh.features.practice.ai.speaking.acoustic;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DirectAudioDarkObservationPersistenceStaticTest {

    @Test
    void v92IsForwardOnlyDarkStorageWithBoundedRetentionAndNoRawSecrets()
            throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V92__practice_speaking_direct_audio_dark_observations.sql"));

        assertThat(sql)
                .contains("practice_speaking_direct_audio_dark_observations")
                .contains("ksh-speaking-direct-audio-acoustic-v1")
                .contains("KSH-SPEAKING-DIRECT-AUDIO-DISCLOSURE-V1")
                .contains("INTERVAL 30 DAY")
                .contains("receipt_fingerprint", "provider_cache_fingerprint")
                .contains("deleted_at", "deletion_evidence_id")
                .doesNotContain("audio_bytes", "transcript", "access_token",
                        "credential_secret", "provider_request_id",
                        "holistic_score", "attempt_points");
    }

    @Test
    void jdbcReadEmbedsActiveExactPurposeReviewerGrantAndExpiry() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/ai/speaking/acoustic/"
                        + "DirectAudioDarkObservationJdbcStore.java"));

        assertThat(source)
                .contains("a.skill = 'SPEAKING'")
                .contains("practice_speaking_audio_reviewer_grants")
                .contains("PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION")
                .contains("g.reviewer_id = ?")
                .contains("g.revoked_at IS NULL AND g.expires_at > ?")
                .contains("o.deleted_at IS NULL AND o.delete_after > ?")
                .doesNotContain("SELECT *", "audioBytes", "accessToken",
                        "providerRequestId");
    }

    @Test
    void learnerResultSurfacesDoNotConsumeDarkPersistenceTypes() throws Exception {
        String[] paths = {
                "src/main/java/com/ksh/features/practice/result/SpeakingResultPresenter.java",
                "src/main/java/com/ksh/features/practice/service/PracticeService.java",
                "src/main/java/com/ksh/features/practice/service/PracticeProgressService.java",
                "src/main/java/com/ksh/features/practice/dto/PracticeDtos.java"
        };
        for (String path : paths) {
            assertThat(Files.readString(Path.of(path)))
                    .as(path)
                    .doesNotContain("DirectAudioDarkObservation",
                            "practice_speaking_direct_audio_dark_observations");
        }
    }
}
