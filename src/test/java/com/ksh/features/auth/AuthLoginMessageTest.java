package com.ksh.features.auth;

import com.ksh.features.auth.controller.AuthController;
import com.ksh.features.admin.settings.service.OauthSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthLoginMessageTest {
    private final OauthSettingsService settings = mock(OauthSettingsService.class);
    private final AuthController controller = new AuthController(settings);

    @Test void unknown_error_codes_cannot_inject_text_or_disclose_account_existence() {
        var first = new ExtendedModelMap();
        var second = new ExtendedModelMap();
        controller.loginPage("", null, first);
        controller.loginPage("<script>alert(1)</script>", null, second);
        assertThat(second.get("flashError")).isEqualTo(first.get("flashError"));
        assertThat(second.get("flashError").toString()).doesNotContain("<script>");
    }

    @Test void normal_page_has_no_failure_and_logout_keeps_success() {
        var model = new ExtendedModelMap();
        assertThat(controller.loginPage(null, "", model)).isEqualTo("auth/login");
        assertThat(model).doesNotContainKey("flashError").containsKey("flashSuccess");
    }

    @Test void oauth_outage_does_not_break_form_login_or_hide_error() {
        when(settings.isGoogleEnabled()).thenThrow(new IllegalStateException("unavailable"));
        var model = new ExtendedModelMap();
        assertThat(controller.loginPage("bad", null, model)).isEqualTo("auth/login");
        assertThat(model).containsEntry("googleEnabled", false).containsKey("flashError");
    }
}
