package com.ksh.features.auth.service;

import com.ksh.entities.User;
import com.ksh.features.auth.repository.PasswordResetTokenRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.mail.MailService;
import com.ksh.features.profile.service.SessionRevocationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class PasswordResetMailDispatcherTest {

    @Test
    void requestThreadDoesNotWaitForSlowMailService() throws Exception {
        MailService mail = mock(MailService.class);
        CountDownLatch mailStarted = new CountDownLatch(1);
        CountDownLatch releaseMail = new CountDownLatch(1);
        doAnswer(invocation -> {
            mailStarted.countDown();
            releaseMail.await(5, TimeUnit.SECONDS);
            return true;
        }).when(mail).send(anyString(), anyString(), anyString());

        PasswordResetMailDispatcher dispatcher = new PasswordResetMailDispatcher(
                mail, 1, 8, Duration.ofSeconds(1));
        UserRepository users = mock(UserRepository.class);
        PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
        PasswordResetRequestThrottle throttle = mock(PasswordResetRequestThrottle.class);
        User user = mock(User.class);
        when(throttle.allow(anyString(), anyString())).thenReturn(true);
        when(users.findByEmailIgnoreCaseForUpdate("student@example.test"))
                .thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(7L);
        when(user.getEmail()).thenReturn("student@example.test");
        when(user.getFullName()).thenReturn("Student");
        PasswordRecoveryService service = new PasswordRecoveryService(
                users,
                tokens,
                mock(CredentialRotationService.class),
                dispatcher,
                throttle,
                mock(SessionRevocationService.class),
                "https://ksh.test");
        ExecutorService requestThread = Executors.newSingleThreadExecutor();

        try {
            Future<?> response = requestThread.submit(() ->
                    service.requestReset("student@example.test", "192.0.2.20"));

            assertThat(mailStarted.await(1, TimeUnit.SECONDS)).isTrue();
            response.get(250, TimeUnit.MILLISECONDS);
            assertThat(releaseMail.getCount()).isEqualTo(1L);
        } finally {
            releaseMail.countDown();
            requestThread.shutdownNow();
            requestThread.awaitTermination(1, TimeUnit.SECONDS);
            dispatcher.destroy();
        }
    }

    @Test
    void providerFailureDoesNotLogRecipientBearerTokenOrExceptionDetail(
            CapturedOutput output) throws Exception {
        String recipient = "private@example.test";
        String bearer = "raw-reset-bearer";
        String body = "https://ksh.test/reset-password?token=" + bearer;
        MailService mail = mock(MailService.class);
        CountDownLatch attempted = new CountDownLatch(1);
        doAnswer(invocation -> {
            attempted.countDown();
            throw new IllegalStateException(
                    "provider rejected " + recipient + " " + bearer);
        }).when(mail).send(anyString(), anyString(), anyString());
        PasswordResetMailDispatcher dispatcher = new PasswordResetMailDispatcher(
                mail, 1, 8, Duration.ofSeconds(1));

        try {
            dispatcher.dispatch(recipient, body);
            assertThat(attempted.await(1, TimeUnit.SECONDS)).isTrue();
            verify(mail).send(recipient, PasswordResetMailDispatcher.SUBJECT, body);
        } finally {
            dispatcher.destroy();
        }

        assertThat(output.getAll())
                .doesNotContain(recipient, bearer, "provider rejected");
    }
}
