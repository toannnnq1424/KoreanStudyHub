package com.ksh.features.practice.preferences;

import com.ksh.features.practice.web.PracticeModelAttributes;
import com.ksh.features.practice.web.PracticeRoutes;
import com.ksh.features.practice.web.PracticeViews;
import com.ksh.security.AuthenticatedUserIdResolver;
import com.ksh.security.Roles;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping(PracticeRoutes.BASE)
@PreAuthorize(Roles.PREAUTH_STUDENT_OR_LECTURER)
public class PracticeKoreanFontPreferenceController {

    private static final String ATTR_UPDATED = "practiceKoreanFontUpdated";

    private final AuthenticatedUserIdResolver userIdResolver;
    private final PracticeKoreanFontPreferenceService preferenceService;

    public PracticeKoreanFontPreferenceController(
            AuthenticatedUserIdResolver userIdResolver,
            PracticeKoreanFontPreferenceService preferenceService) {
        this.userIdResolver = userIdResolver;
        this.preferenceService = preferenceService;
    }

    @GetMapping(PracticeRoutes.PREFERENCES)
    public String view(Authentication authentication, Model model) {
        addPreferenceModel(
                preferenceService.read(userIdResolver.resolve(authentication)),
                model);
        model.addAttribute(
                PracticeModelAttributes.KOREAN_FONT_OPTIONS,
                PracticeKoreanFont.ALLOWED);
        model.addAttribute(
                PracticeModelAttributes.KOREAN_FONT_SIZE_OPTIONS,
                PracticeKoreanFontSize.ALLOWED);
        return PracticeViews.PREFERENCES;
    }

    @PostMapping(PracticeRoutes.KOREAN_FONT_PREFERENCE)
    public String update(
            @RequestParam(value = "koreanFont", required = false)
            String rawKoreanFont,
            @RequestParam(value = "koreanFontSize", required = false)
            String rawKoreanFontSize,
            @RequestParam(value = "schemaVersion", required = false)
            String rawSchemaVersion,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        PracticeKoreanFont koreanFont = parseKoreanFont(rawKoreanFont);
        PracticeKoreanFontSize koreanFontSize =
                parseKoreanFontSize(rawKoreanFontSize);
        int schemaVersion = parseSchemaVersion(rawSchemaVersion);
        try {
            preferenceService.update(
                    userIdResolver.resolve(authentication),
                    koreanFont,
                    koreanFontSize,
                    schemaVersion);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage(),
                    exception);
        }
        redirectAttributes.addFlashAttribute(ATTR_UPDATED, true);
        return PracticeRoutes.redirect(
                PracticeRoutes.BASE + PracticeRoutes.PREFERENCES);
    }

    private static PracticeKoreanFont parseKoreanFont(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw badRequest("Korean font is required.", null);
        }
        try {
            return PracticeKoreanFont.valueOf(rawValue);
        } catch (IllegalArgumentException exception) {
            throw badRequest("Unsupported Korean font.", exception);
        }
    }

    private static int parseSchemaVersion(String rawValue) {
        if (rawValue == null || !rawValue.matches("[1-9][0-9]{0,2}")) {
            throw badRequest("Invalid preference schema version.", null);
        }
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException exception) {
            throw badRequest("Invalid preference schema version.", exception);
        }
    }

    private static PracticeKoreanFontSize parseKoreanFontSize(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw badRequest("Korean font size is required.", null);
        }
        try {
            return PracticeKoreanFontSize.valueOf(rawValue);
        } catch (IllegalArgumentException exception) {
            throw badRequest("Unsupported Korean font size.", exception);
        }
    }

    private static ResponseStatusException badRequest(
            String message,
            Exception cause) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message,
                cause);
    }

    static void addPreferenceModel(
            PracticeKoreanFontPreferenceService.Snapshot snapshot,
            Model model) {
        model.addAttribute(
                PracticeModelAttributes.KOREAN_FONT,
                snapshot.koreanFont().name());
        model.addAttribute(
                PracticeModelAttributes.KOREAN_FONT_SIZE,
                snapshot.koreanFontSize().name());
        model.addAttribute(
                PracticeModelAttributes.KOREAN_FONT_SCHEMA_VERSION,
                snapshot.schemaVersion());
        model.addAttribute(
                PracticeModelAttributes.KOREAN_FONT_ACCOUNT_ID,
                snapshot.accountId());
        model.addAttribute(
                PracticeModelAttributes.KOREAN_FONT_CACHE_NAMESPACE,
                PracticeKoreanFontPreferenceService.CACHE_NAMESPACE);
    }
}
