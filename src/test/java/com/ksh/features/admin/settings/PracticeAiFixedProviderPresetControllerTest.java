package com.ksh.features.admin.settings;

import com.ksh.features.admin.settings.controller.PracticeAiControlPlaneController;
import com.ksh.features.admin.settings.service.PracticeAiControlPlaneAdminService;
import com.ksh.features.practice.ai.controlplane.PracticeAiCapabilityTestService;
import com.ksh.security.KshUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PracticeAiFixedProviderPresetControllerTest {

    @Test
    void csrfOwnedPostDelegatesOnlyAllowlistedPresetCreationAndRedirectsToEdit() {
        PracticeAiControlPlaneAdminService adminService =
                mock(PracticeAiControlPlaneAdminService.class);
        PracticeAiCapabilityTestService capabilityTests =
                mock(PracticeAiCapabilityTestService.class);
        KshUserDetails principal = mock(KshUserDetails.class);
        when(principal.getId()).thenReturn(17L);
        when(adminService.createFixedProviderPreset("XAI_GROK", 17L))
                .thenReturn(42L);
        var controller = new PracticeAiControlPlaneController(
                adminService, capabilityTests);
        var redirect = new RedirectAttributesModelMap();

        String view = controller.createFixedProviderPreset(
                "XAI_GROK", principal, redirect);

        assertThat(view).isEqualTo(
                "redirect:/admin/settings/practice-ai/profiles/42/edit");
        assertThat(redirect.getFlashAttributes())
                .containsKey("flashSuccess");
        verify(adminService).createFixedProviderPreset("XAI_GROK", 17L);
        verifyNoInteractions(capabilityTests);
    }

    @Test
    void missingPrincipalFailsClosedBeforeService() {
        PracticeAiControlPlaneAdminService adminService =
                mock(PracticeAiControlPlaneAdminService.class);
        PracticeAiCapabilityTestService capabilityTests =
                mock(PracticeAiCapabilityTestService.class);
        var controller = new PracticeAiControlPlaneController(
                adminService, capabilityTests);

        String view = controller.createFixedProviderPreset(
                "GROQ", null, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/admin/settings/practice-ai");
        verifyNoInteractions(adminService, capabilityTests);
    }
}
