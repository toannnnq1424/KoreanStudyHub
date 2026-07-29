package com.ksh.features.auth.service;

import com.ksh.entities.PasswordResetToken;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.PasswordResetTokenRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.mail.MailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class PasswordRecoveryServiceSecurityTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final MailService mail = mock(MailService.class);
    private final PasswordResetRequestThrottle throttle = mock(PasswordResetRequestThrottle.class);
    private final PasswordRecoveryService service =
            new PasswordRecoveryService(users, tokens, encoder, mail, throttle, "https://ksh.test");

    PasswordRecoveryServiceSecurityTest() {
        when(throttle.allow(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void requestResetPersistsDigestInsteadOfBearerToken() {
        User user = mock(User.class);
        when(user.getEmail()).thenReturn("student@example.test");
        when(user.getFullName()).thenReturn("Student");
        when(users.findByEmailIgnoreCase("student@example.test")).thenReturn(Optional.of(user));
        when(mail.send(anyString(), anyString(), anyString())).thenReturn(true);

        when(user.getId()).thenReturn(7L);
        service.requestReset("student@example.test", "192.0.2.1");

        ArgumentCaptor<PasswordResetToken> entity = ArgumentCaptor.forClass(PasswordResetToken.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(tokens).save(entity.capture());
        verify(tokens).invalidateUnusedForUser(eq(7L), any());
        verify(mail).send(eq("student@example.test"), eq("KSH Password Reset"), body.capture());

        String marker = "token=";
        String raw = body.getValue().substring(body.getValue().indexOf(marker) + marker.length())
                .split("\\s", 2)[0];
        assertThat(entity.getValue().getToken())
                .hasSize(64)
                .isEqualTo(PasswordRecoveryService.digestToken(raw))
                .isNotEqualTo(raw);
    }

    @Test
    void resetConsumesTheDigestedTokenThroughPessimisticLookup() {
        String raw = "raw-reset-token";
        PasswordResetToken token = mock(PasswordResetToken.class);
        User user = mock(User.class);
        when(tokens.findByTokenForUpdate(PasswordRecoveryService.digestToken(raw)))
                .thenReturn(Optional.of(token));
        when(token.isValid()).thenReturn(true);
        when(token.getUser()).thenReturn(user);
        when(encoder.encode("new-password")).thenReturn("encoded");

        assertThat(service.resetPassword(raw, "new-password")).isTrue();

        verify(tokens).findByTokenForUpdate(PasswordRecoveryService.digestToken(raw));
        verify(tokens, never()).findByTokenForUpdate(raw);
        verify(user).setPasswordHash("encoded");
        verify(token).markUsed();
        verify(tokens).save(token);
    }

    @Test
    void unknownEmailIsEnumerationNeutralAndCreatesNoSideEffects() {
        when(users.findByEmailIgnoreCase("missing@example.test")).thenReturn(Optional.empty());

        service.requestReset("missing@example.test", "192.0.2.2");

        verifyNoInteractions(tokens, mail);
    }

    @Test
    void failedDeliveryDoesNotLogRecipientOrBearerToken(CapturedOutput output) {
        User user = mock(User.class);
        when(user.getEmail()).thenReturn("private@example.test");
        when(user.getFullName()).thenReturn("Private");
        when(users.findByEmailIgnoreCase("private@example.test")).thenReturn(Optional.of(user));
        when(mail.send(anyString(), anyString(), anyString())).thenReturn(false);

        service.requestReset("private@example.test", "192.0.2.3");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mail).send(anyString(), anyString(), body.capture());
        String raw = body.getValue().substring(body.getValue().indexOf("token=") + 6)
                .split("\\s", 2)[0];
        assertThat(output.getAll()).doesNotContain("private@example.test", raw);
    }

    @Test
    void throttledRequestHasNoRepositoryOrMailSideEffects() {
        when(throttle.allow("student@example.test", "192.0.2.4")).thenReturn(false);

        service.requestReset("student@example.test", "192.0.2.4");

        verifyNoInteractions(users, tokens, mail);
    }
}
