package com.ksh.features.practice.ai.speaking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(
        prefix = "app.practice.speaking-direct-audio.authorization",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class DirectAudioAuthorizationCoordinator {

    private final DirectAudioAuthorizationLifecycleService lifecycle;
    private final String disclosureVersion;

    public DirectAudioAuthorizationCoordinator(
            DirectAudioAuthorizationJdbcStore store,
            JdbcTemplate jdbc,
            @Value("${app.practice.speaking-direct-audio.authorization.disclosure-version:}")
            String disclosureVersion,
            @Value("${app.practice.speaking-direct-audio.authorization.max-reviewer-grant:P7D}")
            Duration maximumReviewerGrant,
            @Value("${app.practice.speaking-direct-audio.authorization.grant-manager-user-ids:}")
            String grantManagerUserIds) {
        this(store, jdbc, disclosureVersion, maximumReviewerGrant,
                grantManagerUserIds, Clock.systemUTC());
    }

    DirectAudioAuthorizationCoordinator(
            DirectAudioAuthorizationJdbcStore store,
            JdbcTemplate jdbc,
            String disclosureVersion,
            Duration maximumReviewerGrant,
            String grantManagerUserIds,
            Clock clock) {
        if (disclosureVersion == null || disclosureVersion.isBlank()) {
            throw new IllegalStateException(
                    "Direct-audio disclosure version must be configured before enablement.");
        }
        Set<Long> managers = parseManagers(grantManagerUserIds);
        if (managers.isEmpty()) {
            throw new IllegalStateException(
                    "At least one explicit reviewer grant manager is required.");
        }
        this.disclosureVersion = disclosureVersion.trim();
        this.lifecycle = new DirectAudioAuthorizationLifecycleService(
                store,
                (learnerId, attemptId) -> exists(jdbc, """
                        SELECT COUNT(*) FROM practice_attempts
                        WHERE id = ? AND user_id = ? AND skill = 'SPEAKING'
                        """, attemptId, learnerId),
                (actorId, reviewerId, attemptId) -> managers.contains(actorId)
                        && !actorId.equals(reviewerId)
                        && exists(jdbc, """
                                SELECT COUNT(*) FROM practice_attempts
                                WHERE id = ? AND skill = 'SPEAKING'
                                  AND user_id <> ?
                                """, attemptId, reviewerId),
                clock,
                maximumReviewerGrant);
    }

    @Transactional
    public DirectAudioAuthorizationLifecycleService.ConsentEvent grantConsent(
            Long learnerId, Long attemptId, String eventKey,
            String consentChainKey, String evidenceId) {
        return lifecycle.grantConsent(learnerId, attemptId, eventKey,
                consentChainKey, disclosureVersion, evidenceId);
    }

    @Transactional
    public DirectAudioAuthorizationLifecycleService.ConsentEvent withdrawConsent(
            Long learnerId, Long attemptId, String eventKey, String evidenceId) {
        return lifecycle.withdrawConsent(learnerId, attemptId, eventKey, evidenceId);
    }

    @Transactional
    public DirectAudioAuthorizationLifecycleService.ReviewerGrant grantReviewer(
            Long managerId, Long reviewerId, Long attemptId, String grantKey,
            String evidenceId, Instant expiresAt) {
        return lifecycle.grantReviewer(managerId, reviewerId, attemptId,
                grantKey, evidenceId, expiresAt);
    }

    @Transactional
    public DirectAudioAuthorizationLifecycleService.ReviewerGrant revokeReviewer(
            Long managerId, String grantKey, String evidenceId) {
        return lifecycle.revokeReviewer(managerId, grantKey, evidenceId);
    }

    @Transactional(readOnly = true)
    public boolean reviewerAccessActive(Long reviewerId, Long attemptId) {
        return lifecycle.reviewerAccessActive(reviewerId, attemptId);
    }

    private static boolean exists(JdbcTemplate jdbc, String sql, Object... args) {
        Long count = jdbc.queryForObject(sql, Long.class, args);
        return count != null && count == 1L;
    }

    private static Set<Long> parseManagers(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        try {
            return Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(Long::valueOf)
                    .filter(value -> value > 0)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Reviewer grant manager allowlist is invalid.", exception);
        }
    }
}
