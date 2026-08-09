package com.ksh.features.admin.settings.controller;

import com.ksh.features.dictionary.KoreanDictionarySettingsDtos.Form;
import com.ksh.features.dictionary.KoreanDictionarySettingsService;
import com.ksh.security.KshUserDetails;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/settings/dictionary")
@PreAuthorize("hasAuthority('PERM_system.settings')")
public class KoreanDictionarySettingsController {
    private static final String VIEW = "admin/settings-dictionary";
    private static final String REDIRECT = "redirect:/admin/settings/dictionary";
    private final KoreanDictionarySettingsService service;
    private final String environmentApiKey;
    private final String environmentBaseUrl;

    public KoreanDictionarySettingsController(
            KoreanDictionarySettingsService service,
            @Value("${app.dictionary.api-key:}") String environmentApiKey,
            @Value("${app.dictionary.base-url:https://krdict.korean.go.kr/api/search}") String environmentBaseUrl) {
        this.service = service;
        this.environmentApiKey = environmentApiKey;
        this.environmentBaseUrl = environmentBaseUrl;
    }

    @GetMapping
    public String view(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new Form(
                    service.maskedApiKey(environmentApiKey),
                    service.baseUrl(environmentBaseUrl)));
        }
        model.addAttribute("configured", !service.apiKey(environmentApiKey).isBlank());
        model.addAttribute("activeTab", "settings");
        return VIEW;
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("form") Form form,
                       BindingResult result,
                       @AuthenticationPrincipal KshUserDetails user,
                       Model model,
                       RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("configured", service.hasStoredApiKey());
            model.addAttribute("activeTab", "settings");
            return VIEW;
        }
        try {
            service.save(form.apiKey(), form.baseUrl(), user == null ? null : user.getId());
            redirect.addFlashAttribute("flashSuccess", "Đã lưu cấu hình Korean Basic Dictionary dùng chung.");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("flashError", exception.getMessage());
            redirect.addFlashAttribute("form", form);
        }
        return REDIRECT;
    }
}
