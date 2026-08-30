package com.ksh.features.auth.service;

import com.ksh.entities.AccountActivationToken;
import com.ksh.entities.User;
import com.ksh.entities.UserActivity;
import com.ksh.entities.UserFactory;
import com.ksh.features.admin.users.service.AdminUsersAuditWriter;
import com.ksh.features.auth.repository.AccountActivationTokenRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountActivationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 22, 10, 0);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-22T10:00:00Z"), ZoneOffset.UTC);

    @Mock private UserRepository userRepository;
    @Mock private AccountActivationTokenRepository tokenRepository;
    @Mock private CredentialRotationService credentialRotationService;
    @Mock private AdminUsersAuditWriter auditWriter;

    private AccountActivationService service;

    @BeforeEach
    void setUp() {
        service = new AccountActivationService(userRepository, tokenRepository,
                credentialRotationService, auditWriter, CLOCK);
    }

    @Test
    void issueTokenStoresOnlyDigestAndInvalidatesOlderLinks() {
        User user = pendingUser();
        ArgumentCaptor<AccountActivationToken> token =
                ArgumentCaptor.forClass(AccountActivationToken.class);

        String raw = service.issueToken(user);

        assertThat(raw).hasSize(64);
        verify(tokenRepository).invalidateUnusedForUser(41L, NOW);
        verify(tokenRepository).save(token.capture());
        assertThat(token.getValue().getTokenDigest())
                .hasSize(64)
                .isNotEqualTo(raw)
                .isEqualTo(AccountActivationService.digestToken(raw));
        assertThat(token.getValue().getExpiresAt()).isEqualTo(NOW.plusDays(7));
    }

    @Test
    void issueTokenRejectsAnAlreadyActivatedAccountWithoutPersistence() {
        User active = UserFactory.newAdminCreated("active@example.edu.vn", "hash",
                "Active", Role.STUDENT, true, null, null);
        ReflectionTestUtils.setField(active, "id", 42L);

        assertThatThrownBy(() -> service.issueToken(active))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Tài khoản không ở trạng thái chờ kích hoạt.");
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void activateConsumesCredentialAndMarksAccountActiveAndVerified() {
        String raw = "owner-activation-token";
        String digest = AccountActivationService.digestToken(raw);
        User user = pendingUser();
        AccountActivationToken token = new AccountActivationToken(
                user, digest, NOW.plusHours(1));
        when(tokenRepository.findUserIdByTokenDigest(digest)).thenReturn(Optional.of(41L));
        when(userRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(user));
        when(tokenRepository.findByTokenDigestForUpdate(digest)).thenReturn(Optional.of(token));
        when(credentialRotationService.replacePassword(user, "new-password"))
                .thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);

        assertThat(service.activate(raw, "new-password")).isTrue();

        assertThat(user.isActive()).isTrue();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getActivatedAt()).isEqualTo(NOW);
        verify(userRepository).save(user);
        verify(auditWriter).write(41L, UserActivity.TYPE_SELF_ACTIVATED,
                "Chủ tài khoản hoàn tất kích hoạt qua email", null, 41L);
    }

    @Test
    void expiredTokenFailsClosedWithoutChangingCredential() {
        String raw = "expired-token";
        String digest = AccountActivationService.digestToken(raw);
        User user = pendingUser();
        AccountActivationToken token = new AccountActivationToken(
                user, digest, NOW.minusSeconds(1));
        when(tokenRepository.findUserIdByTokenDigest(digest)).thenReturn(Optional.of(41L));
        when(userRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(user));
        when(tokenRepository.findByTokenDigestForUpdate(digest)).thenReturn(Optional.of(token));

        assertThat(service.activate(raw, "new-password")).isFalse();
        assertThat(user.isPendingActivation()).isTrue();
        verify(credentialRotationService, never()).replacePassword(any(), any());
        verify(auditWriter, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void validateTokenRequiresMatchingOwnerAndActivatableAccount() {
        String raw = "preview-token";
        String digest = AccountActivationService.digestToken(raw);
        User user = pendingUser();
        AccountActivationToken token = new AccountActivationToken(
                user, digest, NOW.plusHours(1));
        when(tokenRepository.findUserIdByTokenDigest(digest)).thenReturn(Optional.of(41L));
        when(tokenRepository.findByTokenDigest(digest)).thenReturn(Optional.of(token));
        when(userRepository.findById(41L)).thenReturn(Optional.of(user));

        assertThat(service.validateToken(raw)).isSameAs(user);
        assertThat(service.validateToken(" ")).isNull();
    }

    private static User pendingUser() {
        User user = UserFactory.newPendingActivation(
                "minji@example.edu.vn", "unknown-hash", "Minji Kim",
                Role.STUDENT, "0901234567", null);
        ReflectionTestUtils.setField(user, "id", 41L);
        return user;
    }
}
