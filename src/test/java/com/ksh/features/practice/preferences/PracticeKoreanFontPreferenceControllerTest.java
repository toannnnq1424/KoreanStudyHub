package com.ksh.features.practice.preferences;

import com.ksh.entities.User;
import com.ksh.features.messaging.service.MessagingService;
import com.ksh.features.notifications.service.NotificationService;
import com.ksh.security.AuthenticatedUserIdResolver;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class PracticeKoreanFontPreferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticatedUserIdResolver userIdResolver;

    @MockitoBean
    private PracticeKoreanFontPreferenceService preferenceService;

    @MockitoBean
    private MessagingService messagingService;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void studentViewReceivesCanonicalServerPreferenceAndExactAllowlist()
            throws Exception {
        when(userIdResolver.resolve(any())).thenReturn(73L);
        when(preferenceService.read(73L)).thenReturn(
                new PracticeKoreanFontPreferenceService.Snapshot(
                        73L,
                        PracticeKoreanFont.DIPHYLLEIA,
                        PracticeKoreanFontSize.LARGE,
                        2));

        mockMvc.perform(get("/practice/preferences")
                        .with(user(principal(73L, Role.STUDENT))))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/preferences"))
                .andExpect(model().attribute(
                        "practiceKoreanFont",
                        "DIPHYLLEIA"))
                .andExpect(model().attribute(
                        "practiceKoreanFontSize",
                        "LARGE"))
                .andExpect(model().attribute(
                        "practiceKoreanFontOptions",
                        PracticeKoreanFont.ALLOWED))
                .andExpect(model().attribute(
                        "practiceKoreanFontSizeOptions",
                        PracticeKoreanFontSize.ALLOWED))
                .andExpect(model().attribute(
                        "practiceKoreanFontAccountId",
                        73L));
    }

    @Test
    void postUsesPrincipalIdentityAndNeverAcceptsArbitraryUserId()
            throws Exception {
        when(userIdResolver.resolve(any())).thenReturn(73L);

        mockMvc.perform(post("/practice/preferences/korean-font")
                        .with(user(principal(73L, Role.STUDENT)))
                        .with(csrf())
                        .param("koreanFont", "GOTHIC_A1")
                        .param("koreanFontSize", "EXTRA_LARGE")
                        .param("schemaVersion", "2")
                        .param("userId", "999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/preferences"));

        verify(preferenceService).update(
                73L,
                PracticeKoreanFont.GOTHIC_A1,
                PracticeKoreanFontSize.EXTRA_LARGE,
                2);
    }

    @Test
    void postRequiresAuthenticationStudentRoleAndCsrf() throws Exception {
        mockMvc.perform(post("/practice/preferences/korean-font")
                        .with(csrf())
                        .param("koreanFont", "NANUM_GOTHIC")
                        .param("koreanFontSize", "DEFAULT")
                        .param("schemaVersion", "2"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/practice/preferences/korean-font")
                        .with(user(principal(73L, Role.STUDENT)))
                        .param("koreanFont", "NANUM_GOTHIC")
                        .param("koreanFontSize", "DEFAULT")
                        .param("schemaVersion", "2"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/practice/preferences/korean-font")
                        .with(user(principal(74L, Role.LECTURER)))
                        .with(csrf())
                        .param("koreanFont", "NANUM_GOTHIC")
                        .param("koreanFontSize", "DEFAULT")
                        .param("schemaVersion", "2"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(preferenceService);
    }

    @Test
    void invalidEnumAndSchemaFailClosedAsBadRequest() throws Exception {
        when(userIdResolver.resolve(any())).thenReturn(73L);
        doThrow(new IllegalArgumentException("Unsupported preference schema version."))
                .when(preferenceService)
                .update(
                        73L,
                        PracticeKoreanFont.NANUM_GOTHIC,
                        PracticeKoreanFontSize.DEFAULT,
                        1);

        mockMvc.perform(post("/practice/preferences/korean-font")
                        .with(user(principal(73L, Role.STUDENT)))
                        .with(csrf())
                        .param("koreanFont", "COMIC_SANS")
                        .param("koreanFontSize", "DEFAULT")
                        .param("schemaVersion", "2"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/practice/preferences/korean-font")
                        .with(user(principal(73L, Role.STUDENT)))
                        .with(csrf())
                        .param("koreanFont", "NANUM_GOTHIC")
                        .param("koreanFontSize", "DEFAULT")
                        .param("schemaVersion", "1"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/practice/preferences/korean-font")
                        .with(user(principal(73L, Role.STUDENT)))
                        .with(csrf())
                        .param("koreanFont", "NANUM_GOTHIC")
                        .param("koreanFontSize", "DEFAULT")
                        .param("schemaVersion", "not-a-version"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/practice/preferences/korean-font")
                        .with(user(principal(73L, Role.STUDENT)))
                        .with(csrf())
                        .param("koreanFont", "NANUM_GOTHIC")
                        .param("koreanFontSize", "HUGE")
                        .param("schemaVersion", "2"))
                .andExpect(status().isBadRequest());
    }

    private static KshUserDetails principal(Long id, Role role) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(role);
        when(user.getEmail()).thenReturn(
                role.name().toLowerCase() + "-" + id + "@ksh.edu.vn");
        when(user.getPasswordHash()).thenReturn("encoded");
        when(user.getFullName()).thenReturn(role.name() + " " + id);
        when(user.isActive()).thenReturn(true);
        when(user.isLocked()).thenReturn(false);
        return new KshUserDetails(user);
    }
}
