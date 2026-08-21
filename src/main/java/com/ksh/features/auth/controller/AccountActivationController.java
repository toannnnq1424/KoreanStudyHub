package com.ksh.features.auth.controller;

import com.ksh.entities.User;
import com.ksh.features.auth.dto.AuthDtos;
import com.ksh.features.auth.service.AccountActivationService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static com.ksh.common.IConstant.*;

/**
 * MVC controller for the self-service account-activation flow.
 *
 * <p>Public by necessity: an account that has not yet activated cannot
 * authenticate, so this page cannot sit behind authentication. Authorization
 * rests entirely on possessing a valid token — the same trust model
 * {@code /reset-password} already uses.
 *
 * <p>Failures are reported identically whether the token is unknown, expired,
 * or already consumed, so the endpoint cannot be used to probe for live tokens
 * or registered addresses.
 */
@Controller
public class AccountActivationController {

    private static final String REDIRECT_LOGIN = "redirect:/login";

    private final AccountActivationService service;

    public AccountActivationController(AccountActivationService service) {
        this.service = service;
    }

    /**
     * Renders the choose-a-password form for a valid token; sets
     * {@code invalid=true} when the token is unknown, expired, or already used.
     */
    @GetMapping(URL_ACTIVATE)
    public String activateForm(@RequestParam("token") String token, Model model) {
        User user = service.validateToken(token);
        if (user == null) {
            model.addAttribute(ATTR_INVALID, true);
            return VIEW_ACTIVATE;
        }
        model.addAttribute(ATTR_TOKEN, token);
        model.addAttribute(ATTR_REQUEST, new AuthDtos.ActivateAccountRequest(token, ""));
        return VIEW_ACTIVATE;
    }

    /**
     * Completes activation. Redirects to {@code /login} on success; re-renders
     * the form on a validation error and the generic failure on a bad token.
     */
    @PostMapping(URL_ACTIVATE)
    public String activateSubmit(@Valid @ModelAttribute("request") AuthDtos.ActivateAccountRequest req,
                                 BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            // Password rejected — redisplay the form so the token stays usable.
            model.addAttribute(ATTR_TOKEN, req.token());
            return VIEW_ACTIVATE;
        }
        boolean ok = service.activate(req.token(), req.newPassword());
        if (!ok) {
            model.addAttribute(ATTR_INVALID, true);
            return VIEW_ACTIVATE;
        }
        ra.addFlashAttribute(ATTR_FLASH_SUCCESS, MSG_ACTIVATION_COMPLETE);
        return REDIRECT_LOGIN;
    }
}