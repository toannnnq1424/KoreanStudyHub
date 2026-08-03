package com.ksh.security;

import org.springframework.security.core.Authentication;

/**
 * Central role-to-workspace navigation rules.
 *
 * <p>This class deliberately describes navigation compatibility only. It does
 * not replace controller or service authorization. Its job is to prevent a
 * saved browser URL from one account (for example {@code /lecturer/classes})
 * being replayed after another role signs in on the same browser.</p>
 */
public final class RoleNavigation {

    private static final String ROLE_PREFIX = "ROLE_";

    private RoleNavigation() {
        // utility class
    }

    /** Returns the first useful workspace for the authenticated role. */
    public static String homeUrl(Authentication authentication) {
        if (hasRole(authentication, Roles.ADMIN)) {
            return "/admin/dashboard";
        }
        if (hasRole(authentication, Roles.LEADER)) {
            return "/leader";
        }
        if (hasRole(authentication, Roles.LECTURER)) {
            return "/lecturer/classes";
        }
        if (hasRole(authentication, Roles.STUDENT)) {
            return "/my/classes";
        }
        return "/";
    }

    /**
     * Returns whether a saved path belongs to a workspace the new role can use.
     * Unknown shared routes remain resumable and are still protected by the
     * normal Spring Security/controller authorization chain.
     */
    public static boolean canResume(Authentication authentication, String path) {
        if (path == null || path.isBlank() || !path.startsWith("/")
                || path.startsWith("//") || path.indexOf('\\') >= 0) {
            return false;
        }

        // Header polling/API endpoints are not destinations. This is a
        // second guard for requests saved before the request-cache matcher
        // was introduced.
        if (path.endsWith("/unread-count") || path.endsWith("/recent")) {
            return false;
        }

        if (matchesArea(path, "/admin")) {
            return hasRole(authentication, Roles.ADMIN);
        }
        if (matchesArea(path, "/leader")) {
            return hasRole(authentication, Roles.LEADER);
        }
        if (matchesArea(path, "/lecturer")) {
            return hasAnyRole(authentication, Roles.LECTURER, Roles.LEADER, Roles.ADMIN);
        }

        // Practice navigation only: business authorization remains in the
        // existing Practice controllers/services.
        if (matchesArea(path, "/practice/manage")) {
            return hasRole(authentication, Roles.LECTURER);
        }
        if (matchesArea(path, "/practice/preferences")
                || matchesArea(path, "/practice/progress")
                || matchesArea(path, "/practice/profile")
                || matchesArea(path, "/practice/attempts")
                || matchesArea(path, "/practice/sets")) {
            return hasRole(authentication, Roles.STUDENT);
        }
        if (matchesArea(path, "/practice")) {
            return hasAnyRole(authentication, Roles.STUDENT, Roles.LECTURER);
        }

        if (matchesArea(path, "/my/classes")
                || matchesArea(path, "/my/tests")
                || matchesArea(path, "/classes")) {
            return hasRole(authentication, Roles.STUDENT);
        }
        if (matchesArea(path, "/j")) {
            return false;
        }

        return !matchesArea(path, "/login") && !matchesArea(path, "/logout");
    }

    private static boolean matchesArea(String path, String area) {
        return path.equals(area) || path.startsWith(area + "/");
    }

    private static boolean hasAnyRole(Authentication authentication, String... roles) {
        for (String role : roles) {
            if (hasRole(authentication, role)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRole(Authentication authentication, String role) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        String authority = ROLE_PREFIX + role;
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
