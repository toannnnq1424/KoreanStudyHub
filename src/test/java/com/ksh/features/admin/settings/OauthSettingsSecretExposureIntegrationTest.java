package com.ksh.features.admin.settings;

import com.ksh.entities.User;
import com.ksh.entities.UserFactory;
import com.ksh.features.admin.settings.controller.OauthSettingsController;
import com.ksh.features.admin.settings.dto.OauthSettingsDtos.OauthSettingsForm;
import com.ksh.features.admin.settings.service.OauthSettingsService;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindingResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class OauthSettingsSecretExposureIntegrationTest {

    private static final long ADMIN_ID = 9107L;
    private static final String STORED_SECRET =
            "stored-oauth-secret-must-never-enter-html";
    private static final String SUBMITTED_SECRET =
            "submitted-oauth-secret-must-never-be-echoed";

    private OauthSettingsService service;
    private OauthSettingsController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(OauthSettingsService.class);
        controller = new OauthSettingsController(service);

        User user = UserFactory.newAdminCreated(
                "oauth-security-admin@ksh.test", "unused", "OAuth Security Admin",
                Role.ADMIN, true, null, null);
        ReflectionTestUtils.setField(user, "id", ADMIN_ID);
        KshUserDetails admin = new KshUserDetails(user, Set.of("system.oauth"));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                admin, null, admin.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        when(service.hasStoredGoogleSecret()).thenReturn(true);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getModelAndTemplateCannotCarryTheStoredSecret() throws Exception {
        when(service.load()).thenReturn(new OauthSettingsForm(
                "client-id", STORED_SECRET, "openid,profile,email"));

        var model = new org.springframework.ui.ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(controller.view(model, response)).isEqualTo("admin/settings-oauth");
        assertThat(((OauthSettingsForm) model.get("form")).googleClientSecret()).isEmpty();
        assertThat(model.toString()).doesNotContain(STORED_SECRET);
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");

        String template = Files.readString(Path.of(
                "src/main/resources/templates/admin/settings-oauth.html"));
        assertThat(template)
                .contains("type=\"password\"", "autocomplete=\"new-password\"")
                .doesNotContain("th:value=\"*{googleClientSecret}\"")
                .doesNotContain("th:field=\"*{googleClientSecret}\"");
    }

    @Test
    void validationRerenderRedactsFormBindingResultAndResponse() throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/settings/oauth")
                        .param("googleClientId", "x".repeat(256))
                        .param("googleClientSecret", SUBMITTED_SECRET)
                        .param("googleScope", "openid,profile,email"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/settings-oauth"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        org.hamcrest.Matchers.containsString("no-store")))
                .andReturn();

        assertRedactedModel(result, SUBMITTED_SECRET);
        verify(service, never()).save(
                org.mockito.ArgumentMatchers.any(OauthSettingsForm.class), anyLong());
    }

    @Test
    void maskedSentinelCannotEnableOAuthWithoutAStoredSecret() throws Exception {
        when(service.hasStoredGoogleSecret()).thenReturn(false);
        MvcResult result = mockMvc.perform(post("/admin/settings/oauth")
                        .param("googleClientId", "client-id")
                        .param("googleClientSecret", "********")
                        .param("googleScope", "openid,profile,email"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/settings-oauth"))
                .andReturn();

        assertRedactedModel(result, "********");
        verify(service, never()).save(
                org.mockito.ArgumentMatchers.any(OauthSettingsForm.class), anyLong());
    }

    private static void assertRedactedModel(MvcResult result, String forbiddenSecret) {
        var model = result.getModelAndView().getModel();
        assertThat(model.toString()).doesNotContain(forbiddenSecret);
        assertThat(((OauthSettingsForm) model.get("form")).googleClientSecret()).isEmpty();
        Object candidate = model.get(BindingResult.MODEL_KEY_PREFIX + "form");
        assertThat(candidate).isInstanceOf(BindingResult.class);
        BindingResult binding = (BindingResult) candidate;
        assertThat(((OauthSettingsForm) binding.getTarget()).googleClientSecret()).isEmpty();
        assertThat(binding.getAllErrors()).isNotEmpty();
        assertThat(binding.getAllErrors().toString()).doesNotContain(forbiddenSecret);
    }
}
