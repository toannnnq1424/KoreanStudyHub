package com.ksh.features.practice.ai.speaking;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectAudioAuthorizationLifecycleServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-03T08:00:00Z");

    @Test
    void consentIsAttemptScopedAppendOnlyAndWithdrawalImmediatelyBlocksAuthority() {
        MemoryStore store = new MemoryStore();
        DirectAudioAuthorizationLifecycleService service = service(store, true, true);

        var granted = service.grantConsent(11L, 22L, "event-0001", "chain-0001",
                "TEST-DISCLOSURE-V1", "TEST-CONSENT-GRANT");

        assertThat(service.activeConsent(11L, 22L)).contains(granted);
        var withdrawn = service.withdrawConsent(11L, 22L, "event-0002",
                "TEST-CONSENT-WITHDRAW");
        assertThat(withdrawn.eventType())
                .isEqualTo(DirectAudioAuthorizationLifecycleService.EventType.WITHDRAWN);
        assertThat(withdrawn.consentChainKey()).isEqualTo(granted.consentChainKey());
        assertThat(service.activeConsent(11L, 22L)).isEmpty();
        assertThat(store.consentEvents).hasSize(2);
    }

    @Test
    void consentCannotBeCreatedForAnUnownedOrNonSpeakingAttempt() {
        DirectAudioAuthorizationLifecycleService service =
                service(new MemoryStore(), false, true);

        assertThatThrownBy(() -> service.grantConsent(11L, 22L,
                "event-0001", "chain-0001", "TEST-DISCLOSURE", "TEST-EVIDENCE"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void reviewerGrantRequiresSeparateAuthorityAndBoundedExpiry() {
        MemoryStore store = new MemoryStore();
        DirectAudioAuthorizationLifecycleService denied = service(store, true, false);
        assertThatThrownBy(() -> denied.grantReviewer(30L, 40L, 22L,
                "grant-0001", "TEST-GRANT", NOW.plus(Duration.ofHours(1))))
                .isInstanceOf(SecurityException.class);

        DirectAudioAuthorizationLifecycleService service = service(store, true, true);
        assertThatThrownBy(() -> service.grantReviewer(40L, 40L, 22L,
                "grant-0001", "TEST-GRANT", NOW.plus(Duration.ofHours(1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.grantReviewer(30L, 40L, 22L,
                "grant-0001", "TEST-GRANT", NOW.plus(Duration.ofDays(8))))
                .isInstanceOf(IllegalArgumentException.class);

        var grant = service.grantReviewer(30L, 40L, 22L,
                "grant-0001", "TEST-GRANT", NOW.plus(Duration.ofDays(2)));
        assertThat(service.reviewerAccessActive(40L, 22L)).isTrue();
        assertThat(grant.toString()).doesNotContain("audio");
    }

    @Test
    void revocationIsImmediateIdempotentAndRequiresGrantAuthority() {
        MemoryStore store = new MemoryStore();
        DirectAudioAuthorizationLifecycleService service = service(store, true, true);
        service.grantReviewer(30L, 40L, 22L, "grant-0001", "TEST-GRANT",
                NOW.plus(Duration.ofDays(2)));

        var revoked = service.revokeReviewer(30L, "grant-0001", "TEST-REVOKE");
        assertThat(revoked.revokedAt()).isEqualTo(NOW);
        assertThat(service.reviewerAccessActive(40L, 22L)).isFalse();
        assertThat(service.revokeReviewer(30L, "grant-0001", "TEST-REVOKE-RETRY"))
                .isEqualTo(revoked);

        DirectAudioAuthorizationLifecycleService denied = service(store, true, false);
        assertThatThrownBy(() -> denied.revokeReviewer(
                31L, "grant-0001", "TEST-REVOKE-DENIED"))
                .isInstanceOf(SecurityException.class);
    }

    private static DirectAudioAuthorizationLifecycleService service(
            MemoryStore store, boolean ownedSpeakingAttempt, boolean reviewerAuthority) {
        return new DirectAudioAuthorizationLifecycleService(
                store,
                (learnerId, attemptId) -> ownedSpeakingAttempt,
                (actorId, reviewerId, attemptId) -> reviewerAuthority,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofDays(7));
    }

    private static final class MemoryStore
            implements DirectAudioAuthorizationLifecycleService.AuthorizationStore {
        private final List<DirectAudioAuthorizationLifecycleService.ConsentEvent>
                consentEvents = new ArrayList<>();
        private final Map<String, DirectAudioAuthorizationLifecycleService.ReviewerGrant>
                grants = new LinkedHashMap<>();

        @Override
        public DirectAudioAuthorizationLifecycleService.ConsentEvent appendConsent(
                DirectAudioAuthorizationLifecycleService.ConsentEvent event) {
            consentEvents.add(event);
            return event;
        }

        @Override
        public Optional<DirectAudioAuthorizationLifecycleService.ConsentEvent> latestConsent(
                Long learnerId, Long attemptId, String purpose) {
            return consentEvents.stream()
                    .filter(event -> event.learnerId().equals(learnerId)
                            && event.attemptId().equals(attemptId))
                    .reduce((first, second) -> second);
        }

        @Override
        public DirectAudioAuthorizationLifecycleService.ReviewerGrant createReviewerGrant(
                DirectAudioAuthorizationLifecycleService.ReviewerGrant grant) {
            grants.put(grant.grantKey(), grant);
            return grant;
        }

        @Override
        public Optional<DirectAudioAuthorizationLifecycleService.ReviewerGrant>
        reviewerGrantForUpdate(String grantKey) {
            return Optional.ofNullable(grants.get(grantKey));
        }

        @Override
        public DirectAudioAuthorizationLifecycleService.ReviewerGrant revokeReviewerGrant(
                String grantKey, Long actorId, String evidenceId, Instant revokedAt) {
            var current = grants.get(grantKey);
            var revoked = new DirectAudioAuthorizationLifecycleService.ReviewerGrant(
                    current.grantKey(), current.attemptId(), current.reviewerId(),
                    current.grantedBy(), current.evidenceId(), current.grantedAt(),
                    current.expiresAt(), revokedAt, actorId, evidenceId);
            grants.put(grantKey, revoked);
            return revoked;
        }

        @Override
        public Optional<DirectAudioAuthorizationLifecycleService.ReviewerGrant>
        activeReviewerGrant(Long reviewerId, Long attemptId, String purpose, Instant now) {
            return grants.values().stream()
                    .filter(grant -> grant.reviewerId().equals(reviewerId)
                            && grant.attemptId().equals(attemptId)
                            && grant.revokedAt() == null
                            && grant.expiresAt().isAfter(now))
                    .findFirst();
        }
    }
}
