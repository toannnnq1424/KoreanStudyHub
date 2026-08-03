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
                .contains("c.event_type = 'GRANTED'")
                .contains("deleteForWithdrawal", "o.deleted_by = ?",
                        "o.deletion_evidence_id = ?")
                .doesNotContain("SELECT *", "audioBytes", "accessToken",
                        "providerRequestId");
    }

    @Test
    void v93BindsEachNewObservationToExactQuestionAndPrivateMediaForReviewerPlayback()
            throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V93__practice_speaking_direct_audio_observation_media_binding.sql"));
        String store = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/service/"
                        + "DirectAudioReviewerPlaybackStore.java"));

        assertThat(sql).contains("question_id", "media_id", "fk_psdado_media")
                .doesNotContain("audio_bytes", "storage_key", "playback_url", "access_token");
        assertThat(store).contains("c.event_type = 'GRANTED'", "g.reviewer_id = ?",
                        "g.revoked_at IS NULL AND g.expires_at > ?",
                        "o.question_id = m.question_id", "o.media_id = m.id",
                        "o.deleted_at IS NULL", "o.delete_after > ?")
                .doesNotContain("SELECT *", "playback_url", "access_token");

        String controller = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/controller/"
                        + "DirectAudioReviewerPlaybackController.java"));
        String properties = Files.readString(Path.of("src/main/resources/application.properties"));
        assertThat(controller).contains("DIRECT_AUDIO_REVIEW_MEDIA_CONTENT", "isAuthenticated()",
                        "noStore()", "PracticeByteRange")
                .doesNotContain("openForOwner", "playbackUrl", "scoreRelease");
        assertThat(properties).contains(
                "PRACTICE_SPEAKING_DIRECT_AUDIO_REVIEWER_PLAYBACK_API_ENABLED:false");
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
                            "DirectAudioReviewerPlayback",
                            "DIRECT_AUDIO_REVIEW_MEDIA_CONTENT",
                            "practice_speaking_direct_audio_dark_observations");
        }
    }
}
