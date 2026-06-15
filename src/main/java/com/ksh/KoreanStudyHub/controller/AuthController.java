package com.ksh.KoreanStudyHub.controller;

import com.ksh.KoreanStudyHub.dto.request.*;
import com.ksh.KoreanStudyHub.entity.User;
import com.ksh.KoreanStudyHub.repository.UserRepository;
import com.ksh.KoreanStudyHub.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @GetMapping("/login")
    public String showLogin() { return "Auth/login"; }

    @GetMapping("/register")
    public String showRegister(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest());
        return "Auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerRequest") RegisterRequest request,
                           BindingResult result, Model model) {
        if (result.hasErrors()) return "Auth/register";
        try {
            authService.register(request);
            return "redirect:/login?registered";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "Auth/register";
        }
    }

    @GetMapping("/forgot-password")
    public String showForgotPassword(Model model) {
        model.addAttribute("forgotRequest", new ForgotPasswordRequest());
        return "Auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(
            @Valid @ModelAttribute("forgotRequest")
            ForgotPasswordRequest request,
            RedirectAttributes ra
    ) {

        try {

            authService.forgotPassword(
                    request.getEmail()
            );

            ra.addFlashAttribute(
                    "success",
                    "Mật khẩu mới đã được gửi qua email của bạn."
            );

        } catch (RuntimeException e) {

            ra.addFlashAttribute(
                    "error",
                    e.getMessage()
            );
        }

        return "redirect:/forgot-password";
    }

    @GetMapping("/profile")
    public String showProfile(Authentication auth, Model model) {
        User user = userRepository.findByEmail(auth.getName()).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("updateRequest", new UpdateProfileRequest());
        model.addAttribute("changePasswordRequest", new ChangePasswordRequest());
        return "Profile/profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(@Valid @ModelAttribute UpdateProfileRequest request,
                                Authentication auth, RedirectAttributes ra) {
        try {
            authService.updateProfile(auth.getName(), request);
            ra.addFlashAttribute("success", "Cập nhật thông tin thành công!");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/profile/change-password")
    public String changePassword(@Valid @ModelAttribute ChangePasswordRequest request,
                                 Authentication auth, RedirectAttributes ra) {
        try {
            authService.changePassword(auth.getName(), request);
            ra.addFlashAttribute("success", "Đổi mật khẩu thành công!");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/profile";
    }
}
