package com.ksh.security;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserIdResolver {

    public Long resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("Authenticated user id is required.");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof KshUserDetails userDetails && userDetails.getId() != null) {
            return userDetails.getId();
        }
        if (principal instanceof CustomOidcUserPrincipal oidcUser && oidcUser.getId() != null) {
            return oidcUser.getId();
        }
        throw new InsufficientAuthenticationException("Authenticated user id is required.");
    }
}
