package com.ksh.features.auth.service;

import com.ksh.entities.User;
import com.ksh.features.auth.repository.PasswordResetTokenRepository;
import com.ksh.features.auth.repository.AccountActivationTokenRepository;
import com.ksh.features.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CredentialRotationServiceTest {

    private UserRepository users;
    private PasswordResetTokenRepository tokens;
    private PasswordEncoder encoder;
    private AccountActivationTokenRepository activationTokens;
    private CredentialRotationService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        tokens = mock(PasswordResetTokenRepository.class);
        encoder = mock(PasswordEncoder.class);
        activationTokens = mock(AccountActivationTokenRepository.class);
        service = new CredentialRotationService(users, tokens, activationTokens, encoder);
    }

    @Test
    void ownPasswordChangeLocksUserAndInvalidatesEveryOutstandingResetLink() {
        User user = mock(User.class);
        when(users.findByIdForUpdate(17L)).thenReturn(Optional.of(user));
        when(user.getPasswordHash()).thenReturn("old-hash");
        when(encoder.matches("old-password", "old-hash")).thenReturn(true);
        when(encoder.encode("new-password")).thenReturn("new-hash");
        when(users.save(user)).thenReturn(user);
        when(user.getId()).thenReturn(17L);
        when(user.getEmail()).thenReturn("student@example.test");

        var result = service.changeOwnPassword(17L, "old-password", "new-password");

        assertThat(result).contains(new CredentialRotationService.ChangedCredential(
                17L, "student@example.test"));
        var order = inOrder(users, encoder, user, tokens, activationTokens);
        order.verify(users).findByIdForUpdate(17L);
        order.verify(encoder).matches("old-password", "old-hash");
        order.verify(encoder).encode("new-password");
        order.verify(user).setPasswordHash("new-hash");
        order.verify(users).save(user);
        order.verify(tokens).invalidateUnusedForUser(org.mockito.ArgumentMatchers.eq(17L), any());
        order.verify(activationTokens).invalidateUnusedForUser(
                org.mockito.ArgumentMatchers.eq(17L), any());
    }

    @Test
    void wrongCurrentPasswordLeavesPasswordAndRecoveryLinksUntouched() {
        User user = mock(User.class);
        when(users.findByIdForUpdate(17L)).thenReturn(Optional.of(user));
        when(user.getPasswordHash()).thenReturn("old-hash");
        when(encoder.matches("wrong-password", "old-hash")).thenReturn(false);

        assertThat(service.changeOwnPassword(17L, "wrong-password", "new-password"))
                .isEmpty();

        verify(encoder, never()).encode("new-password");
        verify(users, never()).save(any());
        verifyNoInteractions(tokens);
        verifyNoInteractions(activationTokens);
    }

    @Test
    void callerOwnedPasswordReplacementAlsoInvalidatesSiblingResetLinks() {
        User user = mock(User.class);
        when(encoder.encode("admin-selected-password")).thenReturn("admin-hash");
        when(users.save(user)).thenReturn(user);
        when(user.getId()).thenReturn(29L);

        assertThat(service.replacePassword(user, "admin-selected-password")).isSameAs(user);

        verify(user).setPasswordHash("admin-hash");
        verify(tokens).invalidateUnusedForUser(org.mockito.ArgumentMatchers.eq(29L), any());
        verify(activationTokens).invalidateUnusedForUser(
                org.mockito.ArgumentMatchers.eq(29L), any());
    }

    @Test
    void emailChangeInvalidationConsumesAllUnusedLinks() {
        when(tokens.invalidateUnusedForUser(org.mockito.ArgumentMatchers.eq(31L), any()))
                .thenReturn(3);

        assertThat(service.invalidateRecoveryTokens(31L)).isEqualTo(3);
    }
}
