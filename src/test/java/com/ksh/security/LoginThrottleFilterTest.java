package com.ksh.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginThrottleFilterTest {

    @Test
    void blockedLoginUsesSameNeutralFailureRedirectWithoutCallingAuthentication() throws Exception {
        LoginAttemptThrottle throttle = mock(LoginAttemptThrottle.class);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setServletPath("/login");
        request.setParameter("username", "student@example.test");
        request.setRemoteAddr("192.0.2.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(throttle.isBlocked("student@example.test", "192.0.2.5"))
                .thenReturn(true);

        new LoginThrottleFilter(throttle).doFilter(request, response, chain);

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void unblockedLoginContinuesToAuthentication() throws Exception {
        LoginAttemptThrottle throttle = mock(LoginAttemptThrottle.class);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setServletPath("/login");
        request.setParameter("username", "student@example.test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new LoginThrottleFilter(throttle).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
