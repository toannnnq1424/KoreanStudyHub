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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class PasswordRecoveryServiceRequestResetTest {

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

    PasswordRecoveryServiceRequestResetTest() {
        when(throttle.allow(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void requestReset_withExistingEmail_savesDigestTokenAndSendsEmail() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(user.getEmail()).thenReturn("student@example.test");
        when(user.getFullName()).thenReturn("Student");
        when(users.findByEmailIgnoreCase("student@example.test"))
                .thenReturn(Optional.of(user));
        when(mail.send(anyString(), anyString(), anyString()))
                .thenReturn(true);

        service.requestReset("student@example.test", "192.0.2.1");

        ArgumentCaptor<PasswordResetToken> tokenCaptor =
                ArgumentCaptor.forClass(PasswordResetToken.class);
        ArgumentCaptor<String> bodyCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(tokens).invalidateUnusedForUser(eq(7L), any());
        verify(tokens).save(tokenCaptor.capture());
        verify(mail).send(
                eq("student@example.test"),
                eq("KSH Password Reset"),
                bodyCaptor.capture()
        );

        String body = bodyCaptor.getValue();
        String rawToken = body.substring(body.indexOf("token=") + 6)
                .split("\\s", 2)[0];

        assertThat(tokenCaptor.getValue().getToken())
                .hasSize(64)
                .isEqualTo(PasswordRecoveryService.digestToken(rawToken))
                .isNotEqualTo(rawToken);
    }

    @Test
    void requestReset_withMissingEmail_hasNoSideEffects() {
        when(users.findByEmailIgnoreCase("missing@example.test"))
                .thenReturn(Optional.empty());

        service.requestReset("missing@example.test", "192.0.2.2");

        verifyNoInteractions(tokens, mail);
    }

    @Test
    void requestReset_whenMailFails_doesNotLogRecipientOrRawToken(
            CapturedOutput output) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(8L);
        when(user.getEmail()).thenReturn("private@example.test");
        when(user.getFullName()).thenReturn("Private User");
        when(users.findByEmailIgnoreCase("private@example.test"))
                .thenReturn(Optional.of(user));
        when(mail.send(anyString(), anyString(), anyString()))
                .thenReturn(false);

        service.requestReset("private@example.test", "192.0.2.3");

        ArgumentCaptor<String> bodyCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(mail).send(anyString(), anyString(), bodyCaptor.capture());

        String body = bodyCaptor.getValue();
        String rawToken = body.substring(body.indexOf("token=") + 6)
                .split("\\s", 2)[0];

        assertThat(output.getAll())
                .contains("Password-reset email was not sent")
                .doesNotContain("private@example.test", rawToken);
    }

    @Test
    void requestReset_whenRequestIsThrottled_hasNoRepositoryOrMailSideEffects() {
        when(throttle.allow("student@example.test", "192.0.2.4"))
                .thenReturn(false);

        service.requestReset("student@example.test", "192.0.2.4");

        verify(users, never()).findByEmailIgnoreCase(anyString());
        verifyNoInteractions(tokens, mail);
    }
}