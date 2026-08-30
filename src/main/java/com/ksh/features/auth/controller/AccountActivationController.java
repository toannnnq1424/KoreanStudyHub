package com.ksh.features.auth.controller;

import com.ksh.features.auth.dto.AuthDtos;
import com.ksh.features.auth.service.AccountActivationService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Public bearer-token page where an imported user chooses their first password. */
@Controller
public class AccountActivationController {
    private static final String VIEW = "auth/activate";
    private final AccountActivationService service;

    public AccountActivationController(AccountActivationService service) {
        this.service = service;
    }

    @GetMapping("/activate")
    public String form(@RequestParam(name = "token", required = false) String token,
                       Model model,
                       HttpServletResponse response) {
        secure(response);
        if (service.validateToken(token) == null) {
            model.addAttribute("invalid", true);
            return VIEW;
        }
        model.addAttribute("token", token);
        model.addAttribute("request",
                new AuthDtos.ActivateAccountRequest(token, "", ""));
        return VIEW;
    }

    @PostMapping("/activate")
    public String submit(
            @Valid @ModelAttribute("request") AuthDtos.ActivateAccountRequest request,
            BindingResult result,
            Model model,
            RedirectAttributes redirect,
            HttpServletResponse response) {
        secure(response);
        if (!request.newPassword().equals(request.confirmPassword())) {
            result.rejectValue("confirmPassword", "password.mismatch",
                    "Mật khẩu xác nhận không khớp");
        }
        if (result.hasErrors()) {
            model.addAttribute("token", request.token());
            return VIEW;
        }
        if (!service.activate(request.token(), request.newPassword())) {
            model.addAttribute("invalid", true);
            return VIEW;
        }
        redirect.addFlashAttribute("flashSuccess",
                "Kích hoạt tài khoản thành công. Bạn có thể đăng nhập ngay.");
        return "redirect:/login";
    }

    private static void secure(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
}
