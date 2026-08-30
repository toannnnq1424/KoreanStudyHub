package com.ksh.security;

import com.ksh.entities.User;
import com.ksh.features.admin.permissions.service.PermissionResolver;
import com.ksh.features.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

/** Verifies the login fail-safe through Spring's real transaction proxies. */
@SpringBootTest
@Import(LoginPermissionResolverTransactionIntegrationTest.ProbeConfig.class)
class LoginPermissionResolverTransactionIntegrationTest {

    private static final String SEED_EMAIL = "student@ksh.edu.vn";

    @Autowired private UserRepository userRepository;
    @Autowired private TransactionalLoginProbe loginProbe;

    @MockitoSpyBean private PermissionResolver permissionResolver;

    @Test
    void resolverRollback_doesNotPoisonTheAuthenticationCallerTransaction() {
        User user = userRepository.findByEmailIgnoreCase(SEED_EMAIL).orElseThrow();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            throw new IllegalStateException("simulated RBAC storage failure");
        }).when(permissionResolver).resolvePermissions(user.getId());

        UserDetails principal = loginProbe.load(SEED_EMAIL);

        assertThat(authorityNames(principal)).containsExactly(Role.STUDENT.authority());
    }

    private static List<String> authorityNames(UserDetails details) {
        return details.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    @TestConfiguration
    static class ProbeConfig {
        @Bean
        TransactionalLoginProbe transactionalLoginProbe(
                CustomUserDetailsService userDetailsService) {
            return new TransactionalLoginProbe(userDetailsService);
        }
    }

    static class TransactionalLoginProbe {
        private final CustomUserDetailsService userDetailsService;

        TransactionalLoginProbe(CustomUserDetailsService userDetailsService) {
            this.userDetailsService = userDetailsService;
        }

        /** The commit at method return exposes any leaked rollback-only marker. */
        @Transactional
        public UserDetails load(String email) {
            return userDetailsService.loadUserByUsername(email);
        }
    }
}
