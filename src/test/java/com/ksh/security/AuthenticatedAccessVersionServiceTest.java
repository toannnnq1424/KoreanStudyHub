package com.ksh.security;

import com.ksh.features.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticatedAccessVersionServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final AuthenticatedAccessVersionService service =
            new AuthenticatedAccessVersionService(users);

    @Test
    void exactLoginCapableVersionIsCurrent() {
        when(users.findLoginCapableSecurityVersion(9L)).thenReturn(Optional.of(4L));

        assertThat(service.isCurrent(9L, 4L)).isTrue();
    }

    @Test
    void missingOrChangedVersionIsStale() {
        when(users.findLoginCapableSecurityVersion(9L)).thenReturn(Optional.of(5L));
        when(users.findLoginCapableSecurityVersion(10L)).thenReturn(Optional.empty());

        assertThat(service.isCurrent(9L, 4L)).isFalse();
        assertThat(service.isCurrent(10L, 0L)).isFalse();
        assertThat(service.isCurrent(null, 0L)).isFalse();
    }
}
