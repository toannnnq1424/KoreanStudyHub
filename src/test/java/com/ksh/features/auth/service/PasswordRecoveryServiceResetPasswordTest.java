package com.ksh.features.auth.service;

import com.ksh.entities.PasswordResetToken;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.PasswordResetTokenRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.mail.MailService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordRecoveryServiceResetPasswordTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final MailService mail = mock(MailService.class);
    private final PasswordResetRequestThrottle throttle = mock(PasswordResetRequestThrottle.class);

    private final PasswordRecoveryService service =
            new PasswordRecoveryService(
                    users,
                    tokens,
                    encoder,
                    mail,
                    throttle,
                    "https://ksh.test"
            );

    @Test
    void resetPassword_withValidToken_updatesPasswordAndMarksTokenUsed() {
        User user = mock(User.class);
        PasswordResetToken token = validToken(user, "valid-reset-token");

        when(tokens.findByTokenForUpdate(
                PasswordRecoveryService.digestToken("valid-reset-token")))
                .thenReturn(Optional.of(token));
        when(encoder.encode("NewPass@123"))
                .thenReturn("encoded-new-password");

        boolean result = service.resetPassword("valid-reset-token", "NewPass@123");

        assertThat(result).isTrue();
        verify(user).setPasswordHash("encoded-new-password");
        verify(users).save(user);
        verify(tokens).save(token);
        assertThat(token.isUsed()).isTrue();
    }

    @Test
    void resetPassword_withInvalidToken_returnsFalseAndDoesNotUpdatePassword() {
        when(tokens.findByTokenForUpdate(
                PasswordRecoveryService.digestToken("invalid-reset-token")))
                .thenReturn(Optional.empty());
        when(tokens.findByTokenForUpdate("invalid-reset-token"))
                .thenReturn(Optional.empty());

        boolean result = service.resetPassword("invalid-reset-token", "NewPass@123");

        assertThat(result).isFalse();
        verify(encoder, never()).encode(anyString());
        verify(users, never()).save(any(User.class));
        verify(tokens, never()).save(any(PasswordResetToken.class));
    }

    @Test
    void resetPassword_withExpiredToken_returnsFalseAndDoesNotUpdatePassword() {
        User user = mock(User.class);
        PasswordResetToken token = expiredToken(user, "expired-reset-token");

        when(tokens.findByTokenForUpdate(
                PasswordRecoveryService.digestToken("expired-reset-token")))
                .thenReturn(Optional.of(token));

        boolean result = service.resetPassword("expired-reset-token", "NewPass@123");

        assertThat(result).isFalse();
        verify(encoder, never()).encode(anyString());
        verify(users, never()).save(any(User.class));
        verify(tokens, never()).save(token);
    }

    @Test
    void resetPassword_withUsedToken_returnsFalseAndDoesNotUpdatePassword() {
        User user = mock(User.class);
        PasswordResetToken token = validToken(user, "used-reset-token");
        token.markUsed();

        when(tokens.findByTokenForUpdate(
                PasswordRecoveryService.digestToken("used-reset-token")))
                .thenReturn(Optional.of(token));

        boolean result = service.resetPassword("used-reset-token", "NewPass@123");

        assertThat(result).isFalse();
        verify(encoder, never()).encode(anyString());
        verify(users, never()).save(any(User.class));
        verify(tokens, never()).save(token);
    }

    @Test
    void resetPassword_withBlankToken_returnsFalseWithoutRepositoryLookup() {
        boolean result = service.resetPassword(" ", "NewPass@123");

        assertThat(result).isFalse();
        verify(tokens, never()).findByTokenForUpdate(anyString());
        verify(encoder, never()).encode(anyString());
        verify(users, never()).save(any(User.class));
    }

    private static PasswordResetToken validToken(User user, String rawToken) {
        return new PasswordResetToken(
                user,
                PasswordRecoveryService.digestToken(rawToken),
                LocalDateTime.now().plusHours(1)
        );
    }

    private static PasswordResetToken expiredToken(User user, String rawToken) {
        return new PasswordResetToken(
                user,
                PasswordRecoveryService.digestToken(rawToken),
                LocalDateTime.now().minusMinutes(1)
        );
    }
}