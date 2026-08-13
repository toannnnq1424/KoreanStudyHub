package com.ksh.security;

import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads user authentication data from the database for Spring Security.
 *
 * <p>In KSH, users authenticate with their <b>email address</b>, so the
 * {@code username} parameter passed by Spring Security is treated as an email.
 * Returns a {@link KshUserDetails} principal that exposes {@code fullName},
 * enabling Thymeleaf templates to use a shared accessor across both
 * form-login and OAuth flows (see {@link CustomOidcUserPrincipal}).
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final LoginPermissionResolver loginPermissionResolver;

    /**
     * Constructs the service with the required collaborators.
     *
     * @param userRepository     JPA repository used to look up {@link User} records by email
     * @param loginPermissionResolver resolves permissions without making them a login precondition
     */
    public CustomUserDetailsService(UserRepository userRepository,
                                    LoginPermissionResolver loginPermissionResolver) {
        this.userRepository = userRepository;
        this.loginPermissionResolver = loginPermissionResolver;
    }

    /**
     * Locates a {@link User} by email address and wraps it in a {@link KshUserDetails} principal.
     *
     * <p>This method is called by Spring Security during form-login authentication.
     * The {@code username} parameter is treated as an email, which is the unique
     * login identifier in KSH.
     *
     * @param email the email address submitted on the login form
     * @return a fully-populated {@link KshUserDetails} for the matching account
     * @throws UsernameNotFoundException if no account with the given email exists
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account found for email: " + email));

        // KshUserDetails maps roles to ROLE_<name> and exposes isEnabled()/isAccountNonLocked()
        // so that Spring Security throws DisabledException / LockedException respectively.
        LoginPermissionResolver.PermissionSnapshot permissions =
                loginPermissionResolver.resolveSnapshotSafely(user.getId());
        return new KshUserDetails(
                user, permissions.featureKeys(), permissions.validUntil());
    }

}
