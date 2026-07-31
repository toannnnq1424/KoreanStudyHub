package com.ksh.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Resumes a saved request only when it belongs to the newly authenticated
 * role; otherwise starts that role in its own workspace.
 */
public final class RoleAwareAuthenticationSuccessHandler
        extends SimpleUrlAuthenticationSuccessHandler {

    private final RequestCache requestCache;

    public RoleAwareAuthenticationSuccessHandler(RequestCache requestCache) {
        this.requestCache = requestCache;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null
                && isSameOrigin(request, savedRequest.getRedirectUrl())
                && RoleNavigation.canResume(authentication, pathOf(savedRequest.getRedirectUrl()))) {
            clearAuthenticationAttributes(request);
            getRedirectStrategy().sendRedirect(
                    request, response, savedRequest.getRedirectUrl());
            return;
        }

        // A stale request from the previous account must not survive into the
        // newly authenticated session.
        requestCache.removeRequest(request, response);
        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(
                request, response, RoleNavigation.homeUrl(authentication));
    }

    private static String pathOf(String redirectUrl) {
        try {
            return new URI(redirectUrl).getPath();
        } catch (URISyntaxException | IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean isSameOrigin(HttpServletRequest request, String redirectUrl) {
        try {
            URI target = new URI(redirectUrl);
            if (!target.isAbsolute()) {
                return target.getRawAuthority() == null;
            }
            int targetPort = normalizedPort(target.getScheme(), target.getPort());
            int requestPort = normalizedPort(request.getScheme(), request.getServerPort());
            return target.getScheme() != null
                    && target.getScheme().equalsIgnoreCase(request.getScheme())
                    && target.getHost() != null
                    && target.getHost().equalsIgnoreCase(request.getServerName())
                    && targetPort == requestPort;
        } catch (URISyntaxException | IllegalArgumentException ex) {
            return false;
        }
    }

    private static int normalizedPort(String scheme, int port) {
        if (port >= 0) {
            return port;
        }
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
