package com.ksh.security;

import com.ksh.entities.User;
import com.ksh.entities.UserFactory;
import com.ksh.features.admin.permissions.service.PermissionResolver;
import com.ksh.features.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class CustomUserDetailsServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PermissionResolver permissionResolver = mock(PermissionResolver.class);
    private final CustomUserDetailsService service =
            new CustomUserDetailsService(userRepository, permissionResolver);

    @Test
    void loadUserByUsername_withActiveUser_returnsUserDetails() {
        User user = activeUser(1L, "active.user@ksh.test");

        when(userRepository.findByEmailIgnoreCase("active.user@ksh.test"))
                .thenReturn(Optional.of(user));
        when(permissionResolver.resolvePermissions(1L))
                .thenReturn(Set.of("LESSON_VIEW"));

        UserDetails result = service.loadUserByUsername("active.user@ksh.test");

        assertThat(result.getUsername()).isEqualTo("active.user@ksh.test");
        assertThat(result.getPassword()).isEqualTo("encoded-password");
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.isAccountNonLocked()).isTrue();
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_STUDENT", "PERM_LESSON_VIEW");
    }

    @Test
    void loadUserByUsername_withUnknownEmail_throwsUsernameNotFoundException() {
        when(userRepository.findByEmailIgnoreCase("notfound@ksh.test"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("notfound@ksh.test"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("No account found for email: notfound@ksh.test");
    }

    @Test
    void loadUserByUsername_withLockedUser_returnsLockedUserDetails() {
        User user = activeUser(2L, "locked.user@ksh.test");
        user.lock("Violation of policy");

        when(userRepository.findByEmailIgnoreCase("locked.user@ksh.test"))
                .thenReturn(Optional.of(user));
        when(permissionResolver.resolvePermissions(2L))
                .thenReturn(Set.of());

        UserDetails result = service.loadUserByUsername("locked.user@ksh.test");

        assertThat(result.getUsername()).isEqualTo("locked.user@ksh.test");
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.isAccountNonLocked()).isFalse();
    }

    @Test
    void loadUserByUsername_whenPermissionResolverFails_returnsRoleOnlyAuthorities(
            CapturedOutput output) {
        User user = activeUser(3L, "permission.error@ksh.test");

        when(userRepository.findByEmailIgnoreCase("permission.error@ksh.test"))
                .thenReturn(Optional.of(user));
        when(permissionResolver.resolvePermissions(3L))
                .thenThrow(new RuntimeException("permission error"));

        UserDetails result = service.loadUserByUsername("permission.error@ksh.test");

        assertThat(result.getUsername()).isEqualTo("permission.error@ksh.test");
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.isAccountNonLocked()).isTrue();
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_STUDENT");
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .doesNotContain("PERM_LESSON_VIEW");

        assertThat(output.getOut())
                .contains("Permission resolution failed for user 3");
    }

    private static User activeUser(Long id, String email) {
        User user = UserFactory.newAdminCreated(
                email,
                "encoded-password",
                "Test User",
                Role.STUDENT,
                true,
                null,
                null
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}