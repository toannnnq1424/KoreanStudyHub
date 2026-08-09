package com.ksh.features.practice.ai.speaking;

import com.ksh.features.practice.service.DirectAudioWithdrawalMediaService;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final DirectAudioWithdrawalMediaService withdrawalMedia;
    private final String disclosureVersion;

    @Autowired
    public DirectAudioAuthorizationCoordinator(
            DirectAudioAuthorizationJdbcStore store,
            DirectAudioWithdrawalMediaService withdrawalMedia,
            JdbcTemplate jdbc,
            @Value("${app.practice.speaking-direct-audio.authorization.disclosure-version:}")
            String disclosureVersion,
            @Value("${app.practice.speaking-direct-audio.authorization.max-reviewer-grant:P7D}")
            Duration maximumReviewerGrant,
            @Value("${app.practice.speaking-direct-audio.authorization.grant-manager-authorities:}")
            String grantManagerAuthorities) {
        this(store, withdrawalMedia, jdbc, disclosureVersion, maximumReviewerGrant,
                grantManagerAuthorities, Clock.systemUTC());
    }

    DirectAudioAuthorizationCoordinator(
            DirectAudioAuthorizationJdbcStore store,
            DirectAudioWithdrawalMediaService withdrawalMedia,
            JdbcTemplate jdbc,
            String disclosureVersion,
            Duration maximumReviewerGrant,
            String grantManagerAuthorities,
            Clock clock) {
        if (disclosureVersion == null || disclosureVersion.isBlank()) {
            throw new IllegalStateException(
                    "Direct-audio disclosure version must be configured before enablement.");
        }
        Set<String> managers = parseAuthorities(grantManagerAuthorities);
        if (managers.isEmpty()) {
            throw new IllegalStateException(
                    "At least one explicit reviewer grant manager is required.");
        }
        this.disclosureVersion = disclosureVersion.trim();
        this.withdrawalMedia = java.util.Objects.requireNonNull(withdrawalMedia);
        this.lifecycle = new DirectAudioAuthorizationLifecycleService(
                store,
                (learnerId, attemptId) -> exists(jdbc, """
                        SELECT COUNT(*) FROM practice_attempts
                        WHERE id = ? AND user_id = ? AND skill = 'SPEAKING'
                        """, attemptId, learnerId),
                (actorId, reviewerId, attemptId) -> !actorId.equals(reviewerId)
                        && exists(jdbc, """
                                SELECT COUNT(*) FROM practice_attempts
                                WHERE id = ? AND skill = 'SPEAKING'
                                  AND user_id <> ?
                                """, attemptId, reviewerId)
                        && hasGrantManagerAuthority(jdbc, actorId, managers),
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
        DirectAudioAuthorizationLifecycleService.ConsentEvent event = lifecycle.withdrawConsent(
                learnerId, attemptId, eventKey, evidenceId);
        withdrawalMedia.enqueueForWithdrawal(learnerId, attemptId, evidenceId);
        return event;
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

    private static Set<String> parseAuthorities(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        Set<String> values = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        Set<String> supported = Set.of("ACADEMIC_LEADER", "PRIVACY_RELEASE_OWNER");
        if (!supported.containsAll(values)) {
            throw new IllegalStateException(
                    "Reviewer grant manager authority allowlist is invalid.");
        }
        return values;
    }

    private static boolean hasGrantManagerAuthority(
            JdbcTemplate jdbc, Long actorId, Set<String> authorities) {
        String placeholders = authorities.stream().map(value -> "?")
                .collect(Collectors.joining(","));
        String sql = """
                SELECT COUNT(*)
                FROM practice_speaking_audio_grant_manager_events e
                WHERE e.subject_user_id = ?
                  AND e.authority_code IN (%s)
                  AND e.event_type = 'ASSIGNED'
                  AND e.id = (
                    SELECT e2.id
                    FROM practice_speaking_audio_grant_manager_events e2
                    WHERE e2.subject_user_id = e.subject_user_id
                      AND e2.authority_code = e.authority_code
                    ORDER BY e2.occurred_at DESC, e2.id DESC
                    LIMIT 1)
                """.formatted(placeholders);
        Object[] args = new Object[authorities.size() + 1];
        args[0] = actorId;
        int index = 1;
        for (String authority : authorities) args[index++] = authority;
        Long count = jdbc.queryForObject(sql, Long.class, args);
        return count != null && count > 0;
    }
}
