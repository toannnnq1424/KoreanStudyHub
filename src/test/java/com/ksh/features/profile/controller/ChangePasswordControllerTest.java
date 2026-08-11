package com.ksh.features.profile.controller;

import com.ksh.entities.User;
import com.ksh.entities.UserFactory;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.profile.dto.ProfileDtos;
import com.ksh.features.profile.service.SessionRevocationService;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChangePasswordControllerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final SessionRevocationService sessionRevocationService =
            mock(SessionRevocationService.class);

    private final ChangePasswordController controller =
            new ChangePasswordController(
                    userRepository,
                    passwordEncoder,
                    sessionRevocationService
            );

    @Test
    void change_withValidInput_updatesPasswordAndRedirects() {
        User user = user(1L, "change.password@ksh.test");
        KshUserDetails principal = new KshUserDetails(user);
        BindingResult bindingResult = mock(BindingResult.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        Model model = mock(Model.class);
        RedirectAttributes redirectAttributes = mock(RedirectAttributes.class);
        ProfileDtos.ChangePasswordRequest form = new ProfileDtos.ChangePasswordRequest(
                "OldPass@123",
                "NewPass@123",
                "NewPass@123"
        );

        when(bindingResult.hasErrors()).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass@123", "encoded-old-password"))
                .thenReturn(true);
        when(passwordEncoder.encode("NewPass@123")).thenReturn("encoded-new-password");
        when(request.getSession(false)).thenReturn(session);
        when(session.getId()).thenReturn("current-session-id");

        String view = controller.change(
                form,
                bindingResult,
                principal,
                request,
                model,
                redirectAttributes
        );

        assertThat(view).isEqualTo("redirect:/change-password");
        assertThat(user.getPasswordHash()).isEqualTo("encoded-new-password");
        verify(userRepository).save(user);
        verify(sessionRevocationService)
                .revokeOtherSessions("change.password@ksh.test", "current-session-id");
        verify(redirectAttributes).addFlashAttribute("passwordChanged", true);
    }

    @Test
    void change_withBlankCurrentPassword_returnsFormWithoutUpdatingPassword() {
        ProfileDtos.ChangePasswordRequest form = new ProfileDtos.ChangePasswordRequest(
                "",
                "NewPass@123",
                "NewPass@123"
        );
        BindingResult bindingResult = mock(BindingResult.class);

        when(bindingResult.hasErrors()).thenReturn(true);

        String view = controller.change(
                form,
                bindingResult,
                mock(KshUserDetails.class),
                mock(HttpServletRequest.class),
                mock(Model.class),
                mock(RedirectAttributes.class)
        );

        assertThat(view).isEqualTo("change-password");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void change_withWrongCurrentPassword_returnsFormWithWrongCurrentFlag() {
        User user = user(2L, "wrong.current@ksh.test");
        KshUserDetails principal = new KshUserDetails(user);
        BindingResult bindingResult = mock(BindingResult.class);
        Model model = mock(Model.class);
        ProfileDtos.ChangePasswordRequest form = new ProfileDtos.ChangePasswordRequest(
                "WrongPass@123",
                "NewPass@123",
                "NewPass@123"
        );

        when(bindingResult.hasErrors()).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass@123", "encoded-old-password"))
                .thenReturn(false);

        String view = controller.change(
                form,
                bindingResult,
                principal,
                mock(HttpServletRequest.class),
                model,
                mock(RedirectAttributes.class)
        );

        assertThat(view).isEqualTo("change-password");
        verify(model).addAttribute("wrongCurrent", true);
        verify(userRepository, never()).save(user);
    }

    @Test
    void change_withMismatchedConfirmation_returnsFormWithMismatchFlag() {
        User user = user(3L, "mismatch@ksh.test");
        KshUserDetails principal = new KshUserDetails(user);
        BindingResult bindingResult = mock(BindingResult.class);
        Model model = mock(Model.class);
        ProfileDtos.ChangePasswordRequest form = new ProfileDtos.ChangePasswordRequest(
                "OldPass@123",
                "NewPass@123",
                "OtherPass@123"
        );

        when(bindingResult.hasErrors()).thenReturn(false);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass@123", "encoded-old-password"))
                .thenReturn(true);

        String view = controller.change(
                form,
                bindingResult,
                principal,
                mock(HttpServletRequest.class),
                model,
                mock(RedirectAttributes.class)
        );

        assertThat(view).isEqualTo("change-password");
        verify(model).addAttribute("mismatch", true);
        verify(userRepository, never()).save(user);
    }

    @Test
    void change_withTooShortNewPassword_returnsFormWithoutUpdatingPassword() {
        ProfileDtos.ChangePasswordRequest form = new ProfileDtos.ChangePasswordRequest(
                "OldPass@123",
                "123",
                "123"
        );
        BindingResult bindingResult = mock(BindingResult.class);

        when(bindingResult.hasErrors()).thenReturn(true);

        String view = controller.change(
                form,
                bindingResult,
                mock(KshUserDetails.class),
                mock(HttpServletRequest.class),
                mock(Model.class),
                mock(RedirectAttributes.class)
        );

        assertThat(view).isEqualTo("change-password");
        verify(userRepository, never()).save(any(User.class));
    }

    private static User user(Long id, String email) {
        User user = UserFactory.newAdminCreated(
                email,
                "encoded-old-password",
                "Test User",
                Role.STUDENT,
                true,
                null,
                null
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
