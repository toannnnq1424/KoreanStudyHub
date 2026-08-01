package com.ksh.features.practice.preferences;

import com.ksh.features.practice.controller.PracticeController;
import com.ksh.features.practice.manage.controller.PracticeDraftController;
import com.ksh.security.AuthenticatedUserIdResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Set;

@ControllerAdvice(assignableTypes = {
        PracticeController.class,
        PracticeDraftController.class
})
public class PracticeKoreanFontPreferenceAdvice {

    private static final Set<String> PREFERENCE_AUTHORITIES =
            Set.of("ROLE_STUDENT", "ROLE_LECTURER");

    private final AuthenticatedUserIdResolver userIdResolver;
    private final PracticeKoreanFontPreferenceService preferenceService;

    public PracticeKoreanFontPreferenceAdvice(
            AuthenticatedUserIdResolver userIdResolver,
            PracticeKoreanFontPreferenceService preferenceService) {
        this.userIdResolver = userIdResolver;
        this.preferenceService = preferenceService;
    }

    @ModelAttribute
    public void addAccountPreference(
            Authentication authentication,
            HttpServletRequest request,
            Model model) {
        if (!"GET".equalsIgnoreCase(request.getMethod())
                || authentication == null
                || authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .noneMatch(PREFERENCE_AUTHORITIES::contains)) {
            return;
        }
        PracticeKoreanFontPreferenceController.addPreferenceModel(
                preferenceService.read(userIdResolver.resolve(authentication)),
                model);
    }
}
