package com.ksh.features.admin.settings.controller;

import com.ksh.features.admin.settings.dto.StorageProfileDtos.ProfileForm;
import com.ksh.features.admin.settings.service.StorageProfileAdminService;
import com.ksh.features.storage.profile.StorageProfileCode;
import com.ksh.security.KshUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

import static com.ksh.common.IConstant.ATTR_ACTIVE_TAB;
import static com.ksh.common.IConstant.ATTR_FLASH_ERROR;
import static com.ksh.common.IConstant.ATTR_FLASH_SUCCESS;
import static com.ksh.common.IConstant.TAB_SETTINGS;

@Controller
@RequestMapping("/admin/settings/storage-profiles")
@PreAuthorize("hasAuthority('PERM_system.storage')")
public class StorageProfileController {
    private static final String REDIRECT = "redirect:/admin/settings/storage-profiles";
    private final StorageProfileAdminService service;

    public StorageProfileController(StorageProfileAdminService service) {
        this.service = service;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("profiles", service.profiles());
        model.addAttribute("missingCodes", service.missingCodes());
        model.addAttribute(ATTR_ACTIVE_TAB, TAB_SETTINGS);
        return "admin/settings-storage-profiles";
    }

    @GetMapping("/{code}/edit")
    public String edit(@PathVariable StorageProfileCode code,
                       Model model,
                       RedirectAttributes redirect) {
        return service.form(code).map(form -> {
            model.addAttribute("form", form);
            populateForm(model, "edit");
            return "admin/settings-storage-profile-form";
        }).orElseGet(() -> error(redirect, "STORAGE_PROFILE_NOT_FOUND"));
    }

    @GetMapping("/{code}/new")
    public String create(@PathVariable StorageProfileCode code,
                         Model model,
                         RedirectAttributes redirect) {
        if (!service.missingCodes().contains(code)) {
            return error(redirect, "STORAGE_PROFILE_ALREADY_EXISTS");
        }
        model.addAttribute("form", ProfileForm.create(code));
        populateForm(model, "create");
        return "admin/settings-storage-profile-form";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("form") ProfileForm form,
                       BindingResult result,
                       @AuthenticationPrincipal KshUserDetails principal,
                       Model model,
                       RedirectAttributes redirect) {
        if (principal == null) return error(redirect, "ADMIN_SESSION_UNSUPPORTED");
        if (result.hasErrors()) {
            populateForm(model, form.revision() == null ? "create" : "edit");
            return "admin/settings-storage-profile-form";
        }
        try {
            service.save(form, principal.getId());
            redirect.addFlashAttribute(ATTR_FLASH_SUCCESS, "Storage profile saved");
            return REDIRECT;
        } catch (RuntimeException exception) {
            result.reject("storageProfile", safeCode(exception));
            populateForm(model, form.revision() == null ? "create" : "edit");
            return "admin/settings-storage-profile-form";
        }
    }

    @PostMapping("/{code}/toggle")
    public String toggle(@PathVariable StorageProfileCode code,
                         @RequestParam("revision") long revision,
                         @AuthenticationPrincipal KshUserDetails principal,
                         RedirectAttributes redirect) {
        if (principal == null) return error(redirect, "ADMIN_SESSION_UNSUPPORTED");
        try {
            service.toggle(code, revision, principal.getId());
            redirect.addFlashAttribute(ATTR_FLASH_SUCCESS, "Storage profile updated");
            return REDIRECT;
        } catch (RuntimeException exception) {
            return error(redirect, safeCode(exception));
        }
    }

    @PostMapping("/{code}/delete")
    public String delete(@PathVariable StorageProfileCode code,
                         @RequestParam("revision") long revision,
                         @AuthenticationPrincipal KshUserDetails principal,
                         RedirectAttributes redirect) {
        if (principal == null) return error(redirect, "ADMIN_SESSION_UNSUPPORTED");
        try {
            service.delete(code, revision);
            redirect.addFlashAttribute(ATTR_FLASH_SUCCESS, "Storage profile deleted");
            return REDIRECT;
        } catch (RuntimeException exception) {
            return error(redirect, safeCode(exception));
        }
    }

    @GetMapping(value = "/{code}/secret", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> reveal(@PathVariable StorageProfileCode code) {
        Map<String, Object> body = service.revealSecret(code)
                .<Map<String, Object>>map(secret -> Map.of("ok", true, "secret", secret))
                .orElseGet(() -> Map.of("ok", false, "errorCode", "STORAGE_SECRET_UNAVAILABLE"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private static void populateForm(Model model, String mode) {
        model.addAttribute("mode", mode);
        model.addAttribute(ATTR_ACTIVE_TAB, TAB_SETTINGS);
    }

    private static String error(RedirectAttributes redirect, String code) {
        redirect.addFlashAttribute(ATTR_FLASH_ERROR, code);
        return REDIRECT;
    }

    private static String safeCode(RuntimeException exception) {
        String value = exception.getMessage();
        return value == null || !value.matches("[A-Z0-9_]{3,80}")
                ? "STORAGE_PROFILE_OPERATION_FAILED" : value;
    }
}
