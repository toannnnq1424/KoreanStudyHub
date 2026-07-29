package com.ksh.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects a throttled form-login attempt before password verification.
 */
public final class LoginThrottleFilter extends OncePerRequestFilter {

    private final LoginAttemptThrottle throttle;

    public LoginThrottleFilter(LoginAttemptThrottle throttle) {
        this.throttle = throttle;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !"/login".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        if (throttle.isBlocked(
                request.getParameter("username"), request.getRemoteAddr())) {
            response.sendRedirect(request.getContextPath() + "/login?error");
            return;
        }
        chain.doFilter(request, response);
    }
}
