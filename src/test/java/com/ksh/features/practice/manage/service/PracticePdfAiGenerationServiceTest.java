package com.ksh.features.practice.manage.service;

import com.ksh.entities.PracticePdfImportSession;
import com.ksh.features.practice.repository.PracticePdfImportSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PracticePdfAiGenerationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-28T07:00:00Z");

    @Autowired
    private PracticePdfAiGenerationService generationService;

    @Autowired
    private PracticeImportSnapshotService snapshotService;

    @Autowired
    private PracticePdfImportSessionRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("""
                DELETE FROM practice_pdf_import_sessions
                WHERE stored_pdf_path LIKE 'phase13h-generation/%'
                """);
    }

    @Test
    void liveClaimRejectsDoubleSubmitAndExpiredClaimIsTokenFenced() {
        PracticePdfImportSession session = persistSession();

        PracticePdfAiGenerationService.ClaimResult first =
                generationService.claim(session.getId(), 7L);
        PracticePdfAiGenerationService.ClaimResult duplicate =
                generationService.claim(session.getId(), 7L);

        assertThat(first.outcome())
                .isEqualTo(PracticePdfAiGenerationService.Outcome.CLAIMED);
        assertThat(first.claimToken()).isNotBlank();
        assertThat(first.claimedSession()).isNotNull();
        assertThat(first.claimedSession().getId()).isEqualTo(session.getId());
        assertThat(duplicate.outcome())
                .isEqualTo(PracticePdfAiGenerationService.Outcome.IN_PROGRESS);
        assertThat(duplicate.leaseExpiresAt())
                .isEqualTo(LocalDateTime.ofInstant(
                        NOW.plusSeconds(600), ZoneOffset.UTC));

        jdbcTemplate.update("""
                UPDATE practice_pdf_import_sessions
                SET generation_lease_expires_at = DATE_SUB(
                    generation_lease_expires_at, INTERVAL 1 DAY)
                WHERE id = ?
                """, session.getId());

        PracticePdfAiGenerationService.ClaimResult reclaimed =
                generationService.claim(session.getId(), 7L);
        assertThat(reclaimed.outcome())
                .isEqualTo(PracticePdfAiGenerationService.Outcome.CLAIMED);
        assertThat(reclaimed.claimToken()).isNotEqualTo(first.claimToken());

        assertThatThrownBy(() -> generationService.release(
                session.getId(), 7L, first.claimToken(), "AI_FAILED_RETRYABLE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PDF AI generation claim mismatch.");

        generationService.release(
                session.getId(),
                7L,
                reclaimed.claimToken(),
                "AI_FAILED_RETRYABLE");
        PracticePdfImportSession released =
                repository.findById(session.getId()).orElseThrow();
        assertThat(released.getStatus()).isEqualTo("AI_FAILED_RETRYABLE");
        assertThat(released.getGenerationClaimToken()).isNull();
        assertThat(released.getGenerationLeaseExpiresAt()).isNull();
    }

    @Test
    void completionIsDurableAndReturnsSameDraftWithoutNewClaim() {
        PracticePdfImportSession session = persistSession();
        PracticePdfAiGenerationService.ClaimResult claim =
                generationService.claim(session.getId(), 7L);

        new TransactionTemplate(transactionManager).executeWithoutResult(
                ignored -> generationService.complete(
                        session.getId(), 7L, claim.claimToken(), 4242L));

        PracticePdfAiGenerationService.ClaimResult duplicate =
                generationService.claim(session.getId(), 7L);
        assertThat(duplicate.outcome())
                .isEqualTo(PracticePdfAiGenerationService.Outcome.COMPLETED);
        assertThat(duplicate.completedDraftId()).isEqualTo(4242L);

        PracticePdfImportSession completed =
                repository.findById(session.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo("AI_COMPLETED");
        assertThat(completed.getLinkedDraftId()).isEqualTo(4242L);
        assertThat(completed.getGenerationClaimToken()).isNull();
    }

    @Test
    void snapshotRestoreInvalidatesLiveGenerationClaim() {
        PracticePdfImportSession session = persistSession();
        session.setSnapshotJson("""
                {
                  "selectedStartPage": 2,
                  "selectedEndPage": 2,
                  "currentPage": 2,
                  "extractionStrategy": "TEXT_ONLY",
                  "annotations": []
                }
                """);
        repository.saveAndFlush(session);

        PracticePdfAiGenerationService.ClaimResult claim =
                generationService.claim(session.getId(), 7L);

        snapshotService.restoreSnapshot(session.getId(), 7L);

        PracticePdfImportSession restored =
                repository.findById(session.getId()).orElseThrow();
        assertThat(restored.getSelectedStartPage()).isEqualTo(2);
        assertThat(restored.getSelectedEndPage()).isEqualTo(2);
        assertThat(restored.getExtractionStrategy()).isEqualTo("TEXT_ONLY");
        assertThat(restored.getStatus()).isEqualTo("ANNOTATING");
        assertThat(restored.getGenerationClaimToken()).isNull();
        assertThat(restored.getGenerationLeaseExpiresAt()).isNull();

        assertThatThrownBy(() -> generationService.release(
                session.getId(),
                7L,
                claim.claimToken(),
                "AI_FAILED_RETRYABLE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PDF AI generation claim mismatch.");
    }

    private PracticePdfImportSession persistSession() {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        PracticePdfImportSession session = new PracticePdfImportSession(
                7L,
                "phase13h.pdf",
                "phase13h-generation/session.pdf",
                2,
                "READY_FOR_AI",
                now,
                now,
                now.plusHours(1));
        return repository.saveAndFlush(session);
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        Clock phase13hGenerationClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
