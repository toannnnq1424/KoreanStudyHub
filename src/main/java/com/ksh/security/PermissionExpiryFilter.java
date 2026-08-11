package com.ksh.security;

import com.ksh.features.admin.permissions.service.PermissionResolver;
import com.ksh.features.profile.service.AuthenticatedWebSocketSessionRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

/** Retires a principal when its permission deadline or durable access version is stale. */
public final class PermissionExpiryFilter extends OncePerRequestFilter {

    private final PermissionResolver permissionResolver;
    private final AuthenticatedWebSocketSessionRegistry webSocketSessions;
    private final AuthenticatedAccessVersionService accessVersions;

    public PermissionExpiryFilter(PermissionResolver permissionResolver,
                                  AuthenticatedWebSocketSessionRegistry webSocketSessions,
                                  AuthenticatedAccessVersionService accessVersions) {
        this.permissionResolver = permissionResolver;
        this.webSocketSessions = webSocketSessions;
        this.accessVersions = accessVersions;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.startsWith("/fonts/")
                || path.startsWith("/webjars/")
                || path.startsWith("/uploads/avatars/")
                || path.startsWith("/uploads/exams/")
                || path.startsWith("/uploads/flashcards/")
                || "/favicon.ico".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof KshUserDetails principal
                && (hasExpired(principal, LocalDateTime.now())
                || !accessVersions.isCurrent(
                        principal.getId(), principal.getSecurityVersion()))) {
            // Remove any set cached before the override deadline. The next login
            // resolves a fresh role + permission snapshot from the database.
            permissionResolver.evictUser(principal.getId());
            webSocketSessions.closeAll(principal.getId());
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }

    static boolean hasExpired(KshUserDetails principal, LocalDateTime now) {
        LocalDateTime validUntil = principal.getPermissionAuthoritiesValidUntil();
        return validUntil != null && !validUntil.isAfter(now);
    }
}
