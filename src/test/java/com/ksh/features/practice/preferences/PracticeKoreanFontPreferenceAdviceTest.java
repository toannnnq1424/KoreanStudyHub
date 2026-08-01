package com.ksh.features.practice.preferences;

import com.ksh.features.practice.controller.PracticeController;
import com.ksh.features.practice.manage.controller.PracticeDraftController;
import com.ksh.security.AuthenticatedUserIdResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PracticeKoreanFontPreferenceAdviceTest {

    @Test
    void adviceCoversLearnerCatalogueAndLecturerDraftEditor() {
        ControllerAdvice annotation =
                PracticeKoreanFontPreferenceAdvice.class.getAnnotation(
                        ControllerAdvice.class);

        assertThat(annotation.assignableTypes())
                .containsExactlyInAnyOrder(
                        PracticeController.class,
                        PracticeDraftController.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_STUDENT", "ROLE_LECTURER"})
    void allowedAccountRoleReceivesItsOwnPreference(String authority) {
        AuthenticatedUserIdResolver resolver =
                mock(AuthenticatedUserIdResolver.class);
        PracticeKoreanFontPreferenceService service =
                mock(PracticeKoreanFontPreferenceService.class);
        PracticeKoreanFontPreferenceAdvice advice =
                new PracticeKoreanFontPreferenceAdvice(resolver, service);
        Authentication authentication = authentication(authority);
        HttpServletRequest request =
                new MockHttpServletRequest("GET", "/practice/manage/drafts/14501");
        ExtendedModelMap model = new ExtendedModelMap();
        when(resolver.resolve(authentication)).thenReturn(74L);
        when(service.read(74L)).thenReturn(
                new PracticeKoreanFontPreferenceService.Snapshot(
                        74L,
                        PracticeKoreanFont.GOWUN_DODUM,
                        PracticeKoreanFontSize.LARGE,
                        2));

        advice.addAccountPreference(authentication, request, model);

        assertThat(model)
                .containsEntry("practiceKoreanFont", "GOWUN_DODUM")
                .containsEntry("practiceKoreanFontSize", "LARGE")
                .containsEntry("practiceKoreanFontAccountId", 74L);
        verify(service).read(74L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LEADER", "ROLE_ADMIN"})
    void governanceRoleDoesNotReceivePracticePreference(String authority) {
        AuthenticatedUserIdResolver resolver =
                mock(AuthenticatedUserIdResolver.class);
        PracticeKoreanFontPreferenceService service =
                mock(PracticeKoreanFontPreferenceService.class);
        PracticeKoreanFontPreferenceAdvice advice =
                new PracticeKoreanFontPreferenceAdvice(resolver, service);

        advice.addAccountPreference(
                authentication(authority),
                new MockHttpServletRequest(
                        "GET",
                        "/practice/manage/drafts/14501"),
                new ExtendedModelMap());

        verifyNoInteractions(resolver, service);
    }

    @Test
    void nonGetRequestDoesNotReadPreference() {
        AuthenticatedUserIdResolver resolver =
                mock(AuthenticatedUserIdResolver.class);
        PracticeKoreanFontPreferenceService service =
                mock(PracticeKoreanFontPreferenceService.class);
        PracticeKoreanFontPreferenceAdvice advice =
                new PracticeKoreanFontPreferenceAdvice(resolver, service);

        advice.addAccountPreference(
                authentication("ROLE_LECTURER"),
                new MockHttpServletRequest(
                        "POST",
                        "/practice/manage/drafts/14501"),
                new ExtendedModelMap());

        verifyNoInteractions(resolver, service);
    }

    private static Authentication authentication(String authority) {
        return new UsernamePasswordAuthenticationToken(
                "account",
                "n/a",
                List.of(new SimpleGrantedAuthority(authority)));
    }
}
