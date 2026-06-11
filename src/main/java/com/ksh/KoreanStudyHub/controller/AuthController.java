package com.ksh.KoreanStudyHub.controller;

import com.ksh.KoreanStudyHub.dto.request.RegisterRequest;
import com.ksh.KoreanStudyHub.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/login")
    public String showLoginPage() {

        return "auth/login";
    }

    @GetMapping("/register")
    public String showRegisterPage(Model model) {

        model.addAttribute(
                "registerRequest",
                new RegisterRequest()
        );

        return "auth/register";
    }

    @PostMapping("/register")
    public String register(
            @Valid
            @ModelAttribute("registerRequest")
            RegisterRequest request,

            BindingResult result,

            Model model
    ) {

        if (result.hasErrors()) {
            return "auth/register";
        }

        try {

            authService.register(request);

            return "redirect:/login";

        } catch (RuntimeException e) {

            model.addAttribute(
                    "error",
                    e.getMessage()
            );

            return "auth/register";
        }
    }
}