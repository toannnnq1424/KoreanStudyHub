package com.ksh.features.practice.ai.controlplane;

import com.ksh.entities.User;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "app.practice.attempt-evaluation.worker-enabled=false",
        "app.practice.attempt-deadline.worker-enabled=false",
        "app.practice.speaking-media.cleanup-worker-enabled=false",
        "app.practice.speaking-prompt-authoring.worker-enabled=false",
        "app.practice.asset-lifecycle.worker-enabled=false"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_AIM5_AUTH_TESTS", matches = "true")
class PracticeAiControlPlaneAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousCannotReadAdminControlPlane() throws Exception {
        mockMvc.perform(get("/admin/settings/practice-ai"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminWithoutSystemAiPermissionIsForbidden() throws Exception {
        mockMvc.perform(get("/admin/settings/practice-ai"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "PERM_system.ai")
    void permissionWithoutAdminRoleIsForbiddenByAdminBoundary() throws Exception {
        mockMvc.perform(get("/admin/settings/practice-ai"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorizedAdminCanReadAndMissingCapabilityBindingFailsClosed()
            throws Exception {
        KshUserDetails principal = authorizedAdmin();
        mockMvc.perform(get("/admin/settings/practice-ai")
                        .with(user(principal)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/settings/practice-ai/profiles/new")
                        .with(user(principal)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/settings/practice-ai/bindings/"
                        + "PRACTICE_PDF_AUTHORING/edit")
                        .with(user(principal)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/settings/practice-ai/profiles/999999/secret")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.ok").value(false));
        mockMvc.perform(post("/admin/settings/practice-ai/bindings/"
                        + "PRACTICE_SPEAKING_TTS/test")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.errorCode")
                        .value("PROVIDER_PURPOSE_UNAVAILABLE"));
    }

    private static KshUserDetails authorizedAdmin() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getRole()).thenReturn(Role.ADMIN);
        when(user.getEmail()).thenReturn("aim5-admin@ksh.invalid");
        when(user.getPasswordHash()).thenReturn("encoded");
        when(user.getFullName()).thenReturn("AIM-5 Admin");
        when(user.isActive()).thenReturn(true);
        when(user.isLocked()).thenReturn(false);
        return new KshUserDetails(user, java.util.Set.of("system.ai"));
    }
}
