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

        String inspection = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/controller/"
                        + "DirectAudioReviewerInspectionController.java"));
        assertThat(inspection).contains("DIRECT_AUDIO_REVIEW_LATEST_OBSERVATION",
                        "isAuthenticated()", "noStore()", "scoreReleaseEligible")
                .doesNotContain("payloadJson()", "providerObservationTotal()",
                        "providerConfidence()", "holisticScore()", "attemptPoints()",
                        "storageKey", "playbackUrl");
        assertThat(properties).contains(
                "PRACTICE_SPEAKING_DIRECT_AUDIO_REVIEWER_INSPECTION_API_ENABLED:false");
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

    @Test
    void v95AuditIsAuthorizedMetadataOnlyAndBothReviewerReadsFailClosedOnAudit()
            throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V95__practice_speaking_reviewer_access_audit.sql"));
        String audit = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/service/"
                        + "DirectAudioReviewerAccessAudit.java"));
        String playback = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/service/"
                        + "DirectAudioReviewerPlaybackService.java"));
        String inspection = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/ai/speaking/acoustic/"
                        + "DirectAudioDarkObservationCoordinator.java"));

        assertThat(sql).contains("outcome_code = 'AUTHORIZED'",
                        "INSPECTION_METADATA", "PLAYBACK_OPEN", "observation_key")
                .doesNotContain("audio_bytes", "storage_key", "playback_url",
                        "access_token", "provider_payload", "score_value",
                        "ip_address", "user_agent");
        assertThat(audit).contains("DIRECT_AUDIO_REVIEWER_ACCESS_AUDIT_FAILED",
                        "rows != 1", "DirectAudioSpeakingEvaluationService.PURPOSE")
                .doesNotContain("storageKey", "audioBytes", "accessToken",
                        "providerObservationTotal", "providerConfidence");
        assertThat(playback).contains("Action.PLAYBACK_OPEN")
                .containsSubsequence("descriptor.validate", "audit.recordAuthorized",
                        "storage.open");
        assertThat(inspection).contains("Action.INSPECTION_METADATA",
                "audit.recordAuthorized");
    }

    @Test
    void v96RequiresExplicitRetentionIdentityAndKeepsPurgeDefaultOff()
            throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V96__practice_speaking_reviewer_access_audit_retention.sql"));
        String audit = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/service/"
                        + "DirectAudioReviewerAccessAudit.java"));
        String purge = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/service/"
                        + "DirectAudioReviewerAccessAuditRetention.java"));
        String properties = Files.readString(Path.of(
                "src/main/resources/application.properties"));

        assertThat(sql).contains("retention_policy_id", "delete_after")
                .doesNotContain("audio_bytes", "storage_key", "provider_payload",
                        "access_token", "score_value");
        assertThat(audit).contains("retentionDeadline", "retentionPolicyId",
                "DIRECT_AUDIO_REVIEWER_ACCESS_AUDIT_RETENTION_NOT_READY");
        assertThat(purge).contains("delete_after <= ?", "LIMIT ?", "MAX_BATCH_SIZE")
                .doesNotContain("TRUNCATE", "DROP TABLE");
        assertThat(properties).contains(
                "PRACTICE_SPEAKING_DIRECT_AUDIO_REVIEWER_ACCESS_AUDIT_RETENTION_POLICY_ID:",
                "PRACTICE_SPEAKING_DIRECT_AUDIO_REVIEWER_ACCESS_AUDIT_RETENTION:PT0S",
                "PRACTICE_SPEAKING_DIRECT_AUDIO_REVIEWER_ACCESS_AUDIT_RETENTION_WORKER_ENABLED:false");
    }
}
