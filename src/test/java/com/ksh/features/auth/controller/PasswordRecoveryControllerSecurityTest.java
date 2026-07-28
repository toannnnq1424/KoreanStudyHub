package com.ksh.features.auth.controller;

import com.ksh.features.auth.service.PasswordRecoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PasswordRecoveryControllerSecurityTest {

    @Test
    void resetPageDisablesCachingAndReferrerDisclosureForValidToken() {
        PasswordRecoveryService service = mock(PasswordRecoveryService.class);
        when(service.validateToken("secret")).thenReturn(mock(com.ksh.entities.User.class));
        PasswordRecoveryController controller = new PasswordRecoveryController(service);
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.resetForm("secret", new ExtendedModelMap(), response);

        assertThat(view).isEqualTo("auth/reset-password");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    }

    @Test
    void resetPageDisablesCachingAndReferrerDisclosureForInvalidToken() {
        PasswordRecoveryService service = mock(PasswordRecoveryService.class);
        PasswordRecoveryController controller = new PasswordRecoveryController(service);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.resetForm("invalid", new ExtendedModelMap(), response);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    }
}
