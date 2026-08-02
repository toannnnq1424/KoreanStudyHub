package com.ksh.features.admin.settings.controller;

import com.ksh.entities.User;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "app.practice.attempt-evaluation.worker-enabled=false",
        "app.practice.attempt-deadline.worker-enabled=false",
        "app.practice.speaking-media.cleanup-worker-enabled=false",
        "app.practice.speaking-prompt-authoring.worker-enabled=false",
        "app.practice.asset-lifecycle.worker-enabled=false"
})
@AutoConfigureMockMvc
class StorageProfileAuthorizationTest {
    @Autowired MockMvc mockMvc;

    @Test
    void anonymousCannotReadStorageProfiles() throws Exception {
        mockMvc.perform(get("/admin/settings/storage-profiles"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminWithoutStoragePermissionIsForbidden() throws Exception {
        mockMvc.perform(get("/admin/settings/storage-profiles"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "PERM_system.storage")
    void storagePermissionWithoutAdminRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/admin/settings/storage-profiles"))
                .andExpect(status().isForbidden());
    }

    @Test
    void exactAuthorizedAdminCanReadAndRevealRemainsNoStore() throws Exception {
        KshUserDetails principal = authorizedAdmin();
        mockMvc.perform(get("/admin/settings/storage-profiles").with(user(principal)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/settings/storage-profiles/PRACTICE_AUTHORING/edit")
                        .with(user(principal)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/settings/storage-profiles/PRACTICE_SPEAKING/secret")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.ok").value(false));
    }

    private static KshUserDetails authorizedAdmin() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getRole()).thenReturn(Role.ADMIN);
        when(user.getEmail()).thenReturn("aim6-admin@ksh.invalid");
        when(user.getPasswordHash()).thenReturn("encoded");
        when(user.getFullName()).thenReturn("AIM-6 Admin");
        when(user.isActive()).thenReturn(true);
        when(user.isLocked()).thenReturn(false);
        return new KshUserDetails(user, java.util.Set.of("system.storage"));
    }
}
