package com.ksh.features.profile.controller;

import com.ksh.features.auth.service.CredentialRotationService;
import com.ksh.features.profile.dto.ProfileDtos;
import com.ksh.features.profile.service.SessionRevocationService;
import com.ksh.security.KshUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChangePasswordControllerSecurityTest {

    private CredentialRotationService credentials;
    private SessionRevocationService sessions;
    private ChangePasswordController controller;
    private BindingResult binding;
    private KshUserDetails principal;
    private HttpServletRequest request;
    private Model model;
    private RedirectAttributes redirect;

    @BeforeEach
    void setUp() {
        credentials = mock(CredentialRotationService.class);
        sessions = mock(SessionRevocationService.class);
        controller = new ChangePasswordController(credentials, sessions);
        binding = mock(BindingResult.class);
        principal = mock(KshUserDetails.class);
        request = mock(HttpServletRequest.class);
        model = mock(Model.class);
        redirect = mock(RedirectAttributes.class);
        when(principal.getId()).thenReturn(17L);
    }

    @Test
    void successfulChangeUsesAtomicCredentialRotationAndKeepsCurrentSession() {
        var form = new ProfileDtos.ChangePasswordRequest(
                "old-password", "new-password", "new-password");
        var changed = new CredentialRotationService.ChangedCredential(
                17L, "student@example.test");
        HttpSession currentSession = mock(HttpSession.class);
        when(currentSession.getId()).thenReturn("session-to-keep");
        when(request.getSession(false)).thenReturn(currentSession);
        when(credentials.changeOwnPassword(17L, "old-password", "new-password"))
                .thenReturn(Optional.of(changed));

        String view = controller.change(
                form, binding, principal, request, model, redirect);

        assertThat(view).isEqualTo("redirect:/change-password");
        verify(sessions).revokeOtherSessions(
                "student@example.test", "session-to-keep");
        verify(redirect).addFlashAttribute("passwordChanged", true);
    }

    @Test
    void wrongCurrentPasswordDoesNotRevokeSessions() {
        var form = new ProfileDtos.ChangePasswordRequest(
                "wrong-password", "new-password", "new-password");
        when(credentials.changeOwnPassword(17L, "wrong-password", "new-password"))
                .thenReturn(Optional.empty());

        String view = controller.change(
                form, binding, principal, request, model, redirect);

        assertThat(view).isEqualTo("change-password");
        verify(model).addAttribute("wrongCurrent", true);
        verifyNoInteractions(sessions);
        verify(redirect, never()).addFlashAttribute("passwordChanged", true);
    }

    @Test
    void mismatchedConfirmationNeverTouchesCredentials() {
        var form = new ProfileDtos.ChangePasswordRequest(
                "old-password", "new-password", "different-password");

        String view = controller.change(
                form, binding, principal, request, model, redirect);

        assertThat(view).isEqualTo("change-password");
        verify(model).addAttribute("mismatch", true);
        verifyNoInteractions(credentials, sessions);
    }
}
