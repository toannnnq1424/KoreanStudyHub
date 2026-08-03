package com.ksh.features.practice.ai.speaking;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DirectAudioAuthorizationCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-08-03T08:00:00Z");

    @Test
    void enablementFailsClosedWithoutDisclosureOrNamedManager() {
        assertThatThrownBy(() -> coordinator("", "30"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disclosure");
        assertThatThrownBy(() -> coordinator("TEST-DISCLOSURE-V1", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("grant manager");
        assertThatThrownBy(() -> coordinator("TEST-DISCLOSURE-V1", "role:LECTURER"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allowlist");
    }

    @Test
    void consentUsesConfiguredDisclosureAndOwnedSpeakingAttemptQuery() {
        DirectAudioAuthorizationCoordinator coordinator =
                coordinator("TEST-DISCLOSURE-V1", "30");

        var event = coordinator.grantConsent(11L, 22L, "event-0001",
                "chain-0001", "TEST-CONSENT");

        assertThat(event.disclosureVersion()).isEqualTo("TEST-DISCLOSURE-V1");
        assertThat(event.eventType())
                .isEqualTo(DirectAudioAuthorizationLifecycleService.EventType.GRANTED);
    }

    @Test
    void onlyNamedManagerCanGrantBoundedReviewerAccess() {
        DirectAudioAuthorizationCoordinator coordinator =
                coordinator("TEST-DISCLOSURE-V1", "30,31");

        assertThatThrownBy(() -> coordinator.grantReviewer(99L, 40L, 22L,
                "grant-0001", "TEST-GRANT", NOW.plus(Duration.ofDays(1))))
                .isInstanceOf(SecurityException.class);
        var grant = coordinator.grantReviewer(30L, 40L, 22L,
                "grant-0002", "TEST-GRANT-2", NOW.plus(Duration.ofDays(1)));
        assertThat(grant.reviewerId()).isEqualTo(40L);
        assertThat(grant.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
    }

    private static DirectAudioAuthorizationCoordinator coordinator(
            String disclosure, String managers) {
        DirectAudioAuthorizationJdbcStore store = mock(DirectAudioAuthorizationJdbcStore.class);
        when(store.appendConsent(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(store.createReviewerGrant(any())).thenAnswer(invocation -> invocation.getArgument(0));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object[].class)))
                .thenReturn(1L);
        return new DirectAudioAuthorizationCoordinator(
                store, jdbc, disclosure, Duration.ofDays(7), managers,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
