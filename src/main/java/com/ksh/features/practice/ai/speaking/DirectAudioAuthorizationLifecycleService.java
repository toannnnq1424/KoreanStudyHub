package com.ksh.features.practice.ai.speaking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Consent and named-reviewer authorization contract for Speaking branch B.
 * Persistence adapters must serialize writes per attempt; this class never
 * handles audio bytes, storage keys, provider identifiers or secrets.
 */
public final class DirectAudioAuthorizationLifecycleService {

    public static final String PURPOSE = DirectAudioSpeakingEvaluationService.PURPOSE;

    private final AuthorizationStore store;
    private final AttemptAuthority attempts;
    private final ReviewerGrantAuthority reviewerAuthority;
    private final Clock clock;
    private final Duration maximumReviewerGrant;

    public DirectAudioAuthorizationLifecycleService(
            AuthorizationStore store,
            AttemptAuthority attempts,
            ReviewerGrantAuthority reviewerAuthority,
            Clock clock,
            Duration maximumReviewerGrant) {
        this.store = Objects.requireNonNull(store);
        this.attempts = Objects.requireNonNull(attempts);
        this.reviewerAuthority = Objects.requireNonNull(reviewerAuthority);
        this.clock = Objects.requireNonNull(clock);
        this.maximumReviewerGrant = Objects.requireNonNull(maximumReviewerGrant);
        if (maximumReviewerGrant.isZero() || maximumReviewerGrant.isNegative()) {
            throw new IllegalArgumentException("maximumReviewerGrant must be positive.");
        }
    }

    public ConsentEvent grantConsent(Long learnerId, Long attemptId, String eventKey,
                                     String consentChainKey, String disclosureVersion,
                                     String evidenceId) {
        requireOwnedSpeakingAttempt(learnerId, attemptId);
        requireToken(eventKey, "eventKey");
        requireToken(consentChainKey, "consentChainKey");
        requireToken(disclosureVersion, "disclosureVersion");
        requireToken(evidenceId, "evidenceId");
        Instant now = clock.instant();
        ConsentEvent event = new ConsentEvent(eventKey, consentChainKey, learnerId,
                attemptId, EventType.GRANTED, disclosureVersion, evidenceId, now);
        return store.appendConsent(event);
    }

    public ConsentEvent withdrawConsent(Long learnerId, Long attemptId, String eventKey,
                                        String evidenceId) {
        requireOwnedSpeakingAttempt(learnerId, attemptId);
        requireToken(eventKey, "eventKey");
        requireToken(evidenceId, "evidenceId");
        ConsentEvent active = activeConsent(learnerId, attemptId)
                .orElseThrow(() -> new IllegalStateException("No active direct-audio consent."));
        ConsentEvent event = new ConsentEvent(eventKey, active.consentChainKey(), learnerId,
                attemptId, EventType.WITHDRAWN, active.disclosureVersion(), evidenceId,
                clock.instant());
        return store.appendConsent(event);
    }

    public Optional<ConsentEvent> activeConsent(Long learnerId, Long attemptId) {
        if (learnerId == null || attemptId == null) return Optional.empty();
        return store.latestConsent(learnerId, attemptId, PURPOSE)
                .filter(event -> event.eventType() == EventType.GRANTED);
    }

    public ReviewerGrant grantReviewer(Long grantorId, Long reviewerId, Long attemptId,
                                       String grantKey, String evidenceId, Instant expiresAt) {
        requireIdentity(grantorId, "grantorId");
        requireIdentity(reviewerId, "reviewerId");
        requireIdentity(attemptId, "attemptId");
        requireToken(grantKey, "grantKey");
        requireToken(evidenceId, "evidenceId");
        if (grantorId.equals(reviewerId)) {
            throw new IllegalArgumentException("Self-granted reviewer audio access is forbidden.");
        }
        if (!reviewerAuthority.mayManage(grantorId, reviewerId, attemptId)) {
            throw new SecurityException("Reviewer grant authority denied.");
        }
        Instant now = clock.instant();
        if (expiresAt == null || !expiresAt.isAfter(now)
                || expiresAt.isAfter(now.plus(maximumReviewerGrant))) {
            throw new IllegalArgumentException("Reviewer grant expiry is outside policy.");
        }
        return store.createReviewerGrant(new ReviewerGrant(grantKey, attemptId,
                reviewerId, grantorId, evidenceId, now, expiresAt, null, null, null));
    }

    public ReviewerGrant revokeReviewer(Long actorId, String grantKey, String evidenceId) {
        requireIdentity(actorId, "actorId");
        requireToken(grantKey, "grantKey");
        requireToken(evidenceId, "evidenceId");
        ReviewerGrant grant = store.reviewerGrantForUpdate(grantKey)
                .orElseThrow(() -> new IllegalStateException("Reviewer grant not found."));
        if (!reviewerAuthority.mayManage(actorId, grant.reviewerId(), grant.attemptId())) {
            throw new SecurityException("Reviewer revocation authority denied.");
        }
        if (grant.revokedAt() != null) return grant;
        return store.revokeReviewerGrant(grantKey, actorId, evidenceId, clock.instant());
    }

    public boolean reviewerAccessActive(Long reviewerId, Long attemptId) {
        if (reviewerId == null || attemptId == null) return false;
        return store.activeReviewerGrant(reviewerId, attemptId, PURPOSE, clock.instant())
                .filter(grant -> grant.revokedAt() == null)
                .filter(grant -> grant.expiresAt().isAfter(clock.instant()))
                .isPresent();
    }

    private void requireOwnedSpeakingAttempt(Long learnerId, Long attemptId) {
        requireIdentity(learnerId, "learnerId");
        requireIdentity(attemptId, "attemptId");
        if (!attempts.isOwnedSpeakingAttempt(learnerId, attemptId)) {
            throw new SecurityException("Owned Speaking attempt authority denied.");
        }
    }

    private static void requireIdentity(Long value, String name) {
        if (value == null || value <= 0) throw new IllegalArgumentException(name + " is invalid.");
    }

    private static void requireToken(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required.");
    }

    public enum EventType { GRANTED, WITHDRAWN }

    public record ConsentEvent(String eventKey, String consentChainKey, Long learnerId,
                               Long attemptId, EventType eventType,
                               String disclosureVersion, String evidenceId,
                               Instant occurredAt) { }

    public record ReviewerGrant(String grantKey, Long attemptId, Long reviewerId,
                                Long grantedBy, String evidenceId, Instant grantedAt,
                                Instant expiresAt, Instant revokedAt, Long revokedBy,
                                String revokeEvidenceId) { }

    public interface AttemptAuthority {
        boolean isOwnedSpeakingAttempt(Long learnerId, Long attemptId);
    }

    public interface ReviewerGrantAuthority {
        boolean mayManage(Long actorId, Long reviewerId, Long attemptId);
    }

    public interface AuthorizationStore {
        ConsentEvent appendConsent(ConsentEvent event);
        Optional<ConsentEvent> latestConsent(Long learnerId, Long attemptId, String purpose);
        ReviewerGrant createReviewerGrant(ReviewerGrant grant);
        Optional<ReviewerGrant> reviewerGrantForUpdate(String grantKey);
        ReviewerGrant revokeReviewerGrant(String grantKey, Long actorId,
                                          String evidenceId, Instant revokedAt);
        Optional<ReviewerGrant> activeReviewerGrant(Long reviewerId, Long attemptId,
                                                    String purpose, Instant now);
    }
}
