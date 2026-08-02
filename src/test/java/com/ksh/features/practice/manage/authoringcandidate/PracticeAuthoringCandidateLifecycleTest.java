package com.ksh.features.practice.manage.authoringcandidate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateState;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceKind;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeAuthoringCandidateLifecycleTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void followsFrozenLifecycleAndBecomesImmutableAfterApply() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 0, 0);
        String json = PracticeAuthoringCandidateTestFixtures
                .candidateEnvelope(objectMapper, true).toString();
        PracticeAuthoringCandidate candidate = candidate(now, json);

        assertThat(candidate.getState()).isEqualTo(CandidateState.PARSED);
        candidate.markNormalized(json, "b".repeat(64), now);
        candidate.markValidated(json, "c".repeat(64), now);
        candidate.beginReview(json, now);
        candidate.replaceReview(json, "d".repeat(64), 101L, false, now);
        candidate.markReady(json, 101L, false, now);
        candidate.markApplied(json, 4, now.plusMinutes(1));

        assertThat(candidate.getState()).isEqualTo(CandidateState.APPLIED);
        assertThat(candidate.getAppliedDraftVersion()).isEqualTo(4);
        assertThatThrownBy(() -> candidate.replaceReview(
                json, "e".repeat(64), 101L, false, now.plusMinutes(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void warningsRequireSameOwnerAcknowledgementBeforeReady() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 0, 0);
        String json = PracticeAuthoringCandidateTestFixtures
                .candidateEnvelope(objectMapper, true).toString();
        PracticeAuthoringCandidate candidate = candidate(now, json);
        candidate.markNormalized(json, "b".repeat(64), now);
        candidate.markValidated(json, "c".repeat(64), now);
        candidate.beginReview(json, now);

        assertThatThrownBy(() -> candidate.markReady(json, 101L, true, now))
                .isInstanceOf(PracticeAuthoringCandidateException.class)
                .extracting("code")
                .isEqualTo("CANDIDATE_WARNING_ACKNOWLEDGEMENT_REQUIRED");

        candidate.replaceReview(json, "d".repeat(64), 101L, true, now);
        candidate.markReady(json, 101L, true, now);
        assertThat(candidate.getState()).isEqualTo(CandidateState.READY_TO_APPLY);
    }

    @Test
    void expiresAtBoundaryAndCannotReturnToReview() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 0, 0);
        String json = PracticeAuthoringCandidateTestFixtures
                .candidateEnvelope(objectMapper, true).toString();
        PracticeAuthoringCandidate candidate = candidate(now, json);
        candidate.markNormalized(json, "b".repeat(64), now);
        candidate.markValidated(json, "c".repeat(64), now);
        candidate.beginReview(json, now);

        assertThat(candidate.expireIfDue(json, now.plusDays(7))).isTrue();
        assertThat(candidate.getState()).isEqualTo(CandidateState.EXPIRED);
        assertThatThrownBy(() -> candidate.replaceReview(
                json, "d".repeat(64), 101L, false, now.plusDays(7)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesRetentionShorterThanSevenDays() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 0, 0);
        String json = PracticeAuthoringCandidateTestFixtures
                .candidateEnvelope(objectMapper, true).toString();

        assertThatThrownBy(() -> new PracticeAuthoringCandidate(
                PracticeAuthoringCandidateTestFixtures.CANDIDATE_ID,
                101L, SourceKind.QUICK_EXCEL,
                "practice-quick-excel-v1",
                PracticeAuthoringCandidateTestFixtures.SOURCE_DIGEST,
                "upload-1", "reading.xlsx", SourceOperation.NONE,
                5001L, 1, "READING", "R1", 0,
                json, "a".repeat(64), now, now.plusDays(6)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PracticeAuthoringCandidate candidate(
            LocalDateTime now, String json) {
        return new PracticeAuthoringCandidate(
                PracticeAuthoringCandidateTestFixtures.CANDIDATE_ID,
                101L, SourceKind.QUICK_EXCEL,
                "practice-quick-excel-v1",
                PracticeAuthoringCandidateTestFixtures.SOURCE_DIGEST,
                "upload-1", "reading.xlsx", SourceOperation.NONE,
                5001L, 1, "READING", "R1", 0,
                json, "a".repeat(64), now, now.plusDays(7));
    }
}
