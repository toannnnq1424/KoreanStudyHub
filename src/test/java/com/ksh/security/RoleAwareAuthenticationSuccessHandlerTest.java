package com.ksh.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleAwareAuthenticationSuccessHandlerTest {

    @Test
    void studentDropsSavedLecturerUrlAndStartsInStudentWorkspace() throws Exception {
        RequestCache cache = mock(RequestCache.class);
        SavedRequest saved = savedRequest("http://localhost:18091/lecturer/classes?continue");
        MockHttpServletRequest request = loginRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(cache.getRequest(request, response)).thenReturn(saved);

        new RoleAwareAuthenticationSuccessHandler(cache).onAuthenticationSuccess(
                request, response, authentication(Roles.STUDENT));

        assertThat(response.getRedirectedUrl()).isEqualTo("/my/classes");
        verify(cache).removeRequest(request, response);
    }

    @Test
    void lecturerCanResumeSavedLecturerUrl() throws Exception {
        RequestCache cache = mock(RequestCache.class);
        String target = "http://localhost:18091/lecturer/classes/9/lessons?continue";
        SavedRequest saved = savedRequest(target);
        MockHttpServletRequest request = loginRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(cache.getRequest(request, response)).thenReturn(saved);

        new RoleAwareAuthenticationSuccessHandler(cache).onAuthenticationSuccess(
                request, response, authentication(Roles.LECTURER));

        assertThat(response.getRedirectedUrl()).isEqualTo(target);
    }

    @Test
    void retiredInviteDeepLinkFallsBackToStudentHome() throws Exception {
        RequestCache cache = mock(RequestCache.class);
        String target = "http://localhost:18091/j/abc123?continue";
        SavedRequest saved = savedRequest(target);
        MockHttpServletRequest request = loginRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(cache.getRequest(request, response)).thenReturn(saved);

        new RoleAwareAuthenticationSuccessHandler(cache).onAuthenticationSuccess(
                request, response, authentication(Roles.STUDENT));

        assertThat(response.getRedirectedUrl()).isEqualTo("/my/classes");
    }

    @Test
    void externalSavedUrlIsNeverResumed() throws Exception {
        RequestCache cache = mock(RequestCache.class);
        SavedRequest saved = savedRequest("https://example.com/lecturer/classes");
        MockHttpServletRequest request = loginRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(cache.getRequest(request, response)).thenReturn(saved);

        new RoleAwareAuthenticationSuccessHandler(cache).onAuthenticationSuccess(
                request, response, authentication(Roles.LECTURER));

        assertThat(response.getRedirectedUrl()).isEqualTo("/lecturer/classes");
        verify(cache).removeRequest(request, response);
    }

    private static SavedRequest savedRequest(String redirectUrl) {
        SavedRequest saved = mock(SavedRequest.class);
        when(saved.getRedirectUrl()).thenReturn(redirectUrl);
        return saved;
    }

    private static MockHttpServletRequest loginRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(18091);
        return request;
    }

    private static Authentication authentication(String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                "user", "password",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
