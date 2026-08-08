package com.ksh.features.practice.service;

import com.ksh.entities.PracticeSpeakingMediaCleanupReason;
import com.ksh.entities.PracticeSpeakingMediaCleanupTask;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectAudioWithdrawalCleanupStaticTest {

    @Test
    void v109AddsMetadataOnlyExactWithdrawalEvidenceAndKeepsHistoricalReasons() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V109__practice_speaking_consent_withdrawal_cleanup.sql"));
        String repository = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/repository/"
                        + "PracticeSpeakingMediaCleanupTaskRepository.java"));

        assertThat(migration).contains("CONSENT_WITHDRAWAL",
                        "authorization_evidence_id", "chk_psm_cleanup_authorization_evidence")
                .doesNotContain("audio_bytes", "playback_url", "provider_request_id",
                        "access_token", "credential", "DROP TABLE", "DROP COLUMN");
        assertThat(repository).contains("VALUES(cleanup_reason) = 'CONSENT_WITHDRAWAL'",
                        "VALUES(authorization_evidence_id)")
                .doesNotContain("SELECT *", "audio_bytes", "access_token");
    }

    @Test
    void taskRequiresBoundedEvidenceOnlyForWithdrawalReason() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 3, 10, 0);
        PracticeSpeakingMediaCleanupTask task = PracticeSpeakingMediaCleanupTask.pendingExact(
                PracticeSpeakingMediaCleanupReason.CONSENT_WITHDRAWAL,
                31L, PracticeSpeakingStorageProvider.LOCAL, "PRACTICE_SPEAKING",
                "learner-speaking/ready/a", now, now, "WITHDRAWAL-EVIDENCE-1");

        assertThat(task.getCleanupReason()).isEqualTo(
                PracticeSpeakingMediaCleanupReason.CONSENT_WITHDRAWAL);
        assertThat(task.getAuthorizationEvidenceId()).isEqualTo("WITHDRAWAL-EVIDENCE-1");
        assertThatThrownBy(() -> PracticeSpeakingMediaCleanupTask.pendingExact(
                PracticeSpeakingMediaCleanupReason.CONSENT_WITHDRAWAL,
                31L, PracticeSpeakingStorageProvider.LOCAL, "PRACTICE_SPEAKING",
                "learner-speaking/ready/b", now, now, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PracticeSpeakingMediaCleanupTask.pendingExact(
                PracticeSpeakingMediaCleanupReason.LOGICAL_DELETE,
                31L, PracticeSpeakingStorageProvider.LOCAL, "PRACTICE_SPEAKING",
                "learner-speaking/ready/c", now, now, "NOT-ALLOWED"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
