package com.ksh.features.auth.service;

import com.ksh.entities.PasswordResetToken;
import com.ksh.entities.User;
import com.ksh.entities.UserFactory;
import com.ksh.features.auth.repository.PasswordResetTokenRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.mail.MailService;
import com.ksh.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** MySQL contract for serialized reset-link issuance per account. */
@SpringBootTest
class PasswordRecoveryConcurrencyIntegrationTest {

    @Autowired private PasswordRecoveryService recoveryService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private PasswordResetRequestThrottle requestThrottle;
    @MockitoBean private MailService mailService;

    @BeforeEach
    void allowRequestsAndMail() {
        when(requestThrottle.allow(anyString(), anyString())).thenReturn(true);
        when(mailService.send(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    @Timeout(30)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRequestsLeaveAtMostOneUsableLink() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        String email = "reset-race-" + UUID.randomUUID() + "@ksh.test";
        Long userId = transactions.execute(status -> userRepository.saveAndFlush(
                UserFactory.newAdminCreated(
                        email,
                        "existing-test-hash",
                        "Reset Race Student",
                        Role.STUDENT,
                        true,
                        null,
                        null)).getId());
        assertThat(userId).isNotNull();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> requestAfterLatch(
                    email, "192.0.2.10", ready, start));
            Future<?> second = executor.submit(() -> requestAfterLatch(
                    email, "192.0.2.11", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);

            List<PasswordResetToken> issued = transactions.execute(status ->
                    tokenRepository.findAll().stream()
                            .filter(token -> userId.equals(token.getUser().getId()))
                            .toList());
            assertThat(issued).isNotNull();
            assertThat(issued).hasSize(2);
            assertThat(issued.stream().filter(token -> !token.isUsed())).hasSize(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            transactions.executeWithoutResult(status -> {
                List<PasswordResetToken> issued = tokenRepository.findAll().stream()
                        .filter(token -> userId.equals(token.getUser().getId()))
                        .toList();
                tokenRepository.deleteAll(issued);
                tokenRepository.flush();
                userRepository.deleteById(userId);
                userRepository.flush();
            });
        }
    }

    @Test
    @Timeout(30)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void successfulResetInvalidatesEverySiblingLinkInTheSameTransaction() {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        String email = "reset-siblings-" + UUID.randomUUID() + "@ksh.test";
        String presentedRaw = "presented-" + UUID.randomUUID();
        String siblingRaw = "sibling-" + UUID.randomUUID();
        ResetFixture fixture = transactions.execute(status -> {
            User user = userRepository.saveAndFlush(UserFactory.newAdminCreated(
                    email,
                    passwordEncoder.encode("old-password"),
                    "Reset Sibling Student",
                    Role.STUDENT,
                    true,
                    null,
                    null));
            List<PasswordResetToken> issued = tokenRepository.saveAllAndFlush(List.of(
                    new PasswordResetToken(
                            user,
                            PasswordRecoveryService.digestToken(presentedRaw),
                            java.time.LocalDateTime.now().plusHours(1)),
                    new PasswordResetToken(
                            user,
                            PasswordRecoveryService.digestToken(siblingRaw),
                            java.time.LocalDateTime.now().plusHours(1))));
            return new ResetFixture(
                    user.getId(), issued.get(0).getId(), issued.get(1).getId());
        });
        assertThat(fixture).isNotNull();

        try {
            assertThat(recoveryService.resetPassword(presentedRaw, "new-password"))
                    .isTrue();

            transactions.executeWithoutResult(status -> {
                User changed = userRepository.findById(fixture.userId()).orElseThrow();
                PasswordResetToken presented = tokenRepository
                        .findById(fixture.presentedTokenId()).orElseThrow();
                PasswordResetToken sibling = tokenRepository
                        .findById(fixture.siblingTokenId()).orElseThrow();
                assertThat(passwordEncoder.matches(
                        "new-password", changed.getPasswordHash())).isTrue();
                assertThat(presented.isUsed()).isTrue();
                assertThat(sibling.isUsed()).isTrue();
            });
        } finally {
            transactions.executeWithoutResult(status -> {
                tokenRepository.deleteById(fixture.presentedTokenId());
                tokenRepository.deleteById(fixture.siblingTokenId());
                tokenRepository.flush();
                userRepository.deleteById(fixture.userId());
                userRepository.flush();
            });
        }
    }

    private void requestAfterLatch(String email,
                                   String ip,
                                   CountDownLatch ready,
                                   CountDownLatch start) {
        ready.countDown();
        await(start);
        recoveryService.requestReset(email, ip);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Reset issuance latch timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reset issuance interrupted", interrupted);
        }
    }

    private record ResetFixture(Long userId,
                                Long presentedTokenId,
                                Long siblingTokenId) {
    }
}
