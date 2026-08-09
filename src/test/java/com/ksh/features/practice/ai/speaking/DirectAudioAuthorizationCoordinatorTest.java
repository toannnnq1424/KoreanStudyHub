package com.ksh.features.practice.ai.speaking;

import com.ksh.features.practice.service.DirectAudioWithdrawalMediaService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DirectAudioAuthorizationCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-08-03T08:00:00Z");

    @Test
    void enablementFailsClosedWithoutDisclosureOrNamedManager() {
        assertThatThrownBy(() -> coordinator("", "ACADEMIC_LEADER"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disclosure");
        assertThatThrownBy(() -> coordinator("TEST-DISCLOSURE-V1", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("grant manager");
        assertThatThrownBy(() -> coordinator("TEST-DISCLOSURE-V1", "LEADER"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allowlist");
    }

    @Test
    void consentUsesConfiguredDisclosureAndOwnedSpeakingAttemptQuery() {
        DirectAudioAuthorizationCoordinator coordinator =
                coordinator("TEST-DISCLOSURE-V1", "ACADEMIC_LEADER");

        var event = coordinator.grantConsent(11L, 22L, "event-0001",
                "chain-0001", "TEST-CONSENT");

        assertThat(event.disclosureVersion()).isEqualTo("TEST-DISCLOSURE-V1");
        assertThat(event.eventType())
                .isEqualTo(DirectAudioAuthorizationLifecycleService.EventType.GRANTED);
    }

    @Test
    void onlyNamedManagerCanGrantBoundedReviewerAccess() {
        DirectAudioAuthorizationCoordinator coordinator =
                coordinator("TEST-DISCLOSURE-V1",
                        "ACADEMIC_LEADER,PRIVACY_RELEASE_OWNER");

        assertThatThrownBy(() -> coordinator.grantReviewer(99L, 40L, 22L,
                "grant-0001", "TEST-GRANT", NOW.plus(Duration.ofDays(1))))
                .isInstanceOf(SecurityException.class);
        var grant = coordinator.grantReviewer(30L, 40L, 22L,
                "grant-0002", "TEST-GRANT-2", NOW.plus(Duration.ofDays(1)));
        assertThat(grant.reviewerId()).isEqualTo(40L);
        assertThat(grant.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
    }

    @Test
    void withdrawalQueuesOnlyThatLearnersAudioForCleanup() {
        DirectAudioAuthorizationJdbcStore store = mock(DirectAudioAuthorizationJdbcStore.class);
        DirectAudioWithdrawalMediaService withdrawalMedia =
                mock(DirectAudioWithdrawalMediaService.class);
        when(store.latestConsent(11L, 22L,
                DirectAudioAuthorizationLifecycleService.PURPOSE)).thenReturn(Optional.of(
                new DirectAudioAuthorizationLifecycleService.ConsentEvent(
                        "event-0001", "chain-0001", 11L, 22L,
                        DirectAudioAuthorizationLifecycleService.EventType.GRANTED,
                        "TEST-DISCLOSURE-V1", "TEST-CONSENT", NOW.minusSeconds(1))));
        when(store.appendConsent(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DirectAudioAuthorizationCoordinator coordinator = coordinator(
                store, withdrawalMedia,
                "TEST-DISCLOSURE-V1", "ACADEMIC_LEADER");

        coordinator.withdrawConsent(11L, 22L, "event-0002", "TEST-WITHDRAWAL");

        verify(withdrawalMedia).enqueueForWithdrawal(11L, 22L,
                "TEST-WITHDRAWAL");
    }

    private static DirectAudioAuthorizationCoordinator coordinator(
            String disclosure, String managers) {
        DirectAudioAuthorizationJdbcStore store = mock(DirectAudioAuthorizationJdbcStore.class);
        DirectAudioWithdrawalMediaService withdrawalMedia =
                mock(DirectAudioWithdrawalMediaService.class);
        when(store.appendConsent(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(store.createReviewerGrant(any())).thenAnswer(invocation -> invocation.getArgument(0));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.contains("practice_speaking_audio_grant_manager_events")) {
                        Object actorId = invocation.getArgument(2);
                        return Long.valueOf(30L).equals(actorId) ? 1L : 0L;
                    }
                    return 1L;
                });
        return new DirectAudioAuthorizationCoordinator(
                store, withdrawalMedia, jdbc, disclosure,
                Duration.ofDays(7), managers,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static DirectAudioAuthorizationCoordinator coordinator(
            DirectAudioAuthorizationJdbcStore store,
            DirectAudioWithdrawalMediaService withdrawalMedia,
            String disclosure, String managers) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object[].class)))
                .thenReturn(1L);
        return new DirectAudioAuthorizationCoordinator(
                store, withdrawalMedia, jdbc, disclosure,
                Duration.ofDays(7), managers,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
