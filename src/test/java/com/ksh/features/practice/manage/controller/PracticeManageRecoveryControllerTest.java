package com.ksh.features.practice.manage.controller;

import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationRecoveryQueryService;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationRetryService;
import com.ksh.features.practice.governance.PracticeCollaborationService;
import com.ksh.features.practice.governance.PracticeLifecycleService;
import com.ksh.features.practice.manage.service.PracticeDraftService;
import com.ksh.features.practice.manage.service.PracticeRevisionService;
import com.ksh.features.practice.repository.PracticeAuthoringCollaborationRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.repository.PracticePublishedVersionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.security.KshUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PracticeManageRecoveryControllerTest {

    private QuestionExplanationRetryService retryService;
    private KshUserDetails lecturer;
    private PracticeManageController controller;

    @BeforeEach
    void setUp() {
        retryService = mock(QuestionExplanationRetryService.class);
        lecturer = mock(KshUserDetails.class);
        when(lecturer.getId()).thenReturn(7L);
        controller = new PracticeManageController(
                mock(PracticeSetRepository.class),
                mock(PracticeDraftRepository.class),
                mock(UserRepository.class),
                mock(PracticeDraftService.class),
                mock(PracticeRevisionService.class),
                mock(PracticePublishedVersionRepository.class),
                mock(PracticeAuthoringCollaborationRepository.class),
                mock(PracticeLifecycleService.class),
                mock(PracticeCollaborationService.class),
                mock(QuestionExplanationRecoveryQueryService.class),
                retryService);
    }

    @Test
    void retryUsesStableQuestionIdentityAndRedirectsToExactSelectedSet() {
        when(retryService.retryQuestionVersion(90L, 70L, 7L))
                .thenReturn(new QuestionExplanationRetryService.RetryResult(
                        "PENDING", true, 0,
                        "Đã xếp lịch tạo lại giải thích."));
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

        String redirect = controller.retryQuestionExplanation(
                90L, 70L, lecturer, flash);

        assertThat(redirect)
                .isEqualTo("redirect:/practice/manage/revisions?setId=90");
        assertThat(flash.getFlashAttributes().get("success"))
                .isEqualTo("Đã xếp lịch tạo lại giải thích.");
    }

    @Test
    void cooldownAndRuntimeFailureUseSafeLocalizedFlashWithoutRawProviderMessage() {
        when(retryService.retryQuestionVersion(90L, 70L, 7L))
                .thenReturn(new QuestionExplanationRetryService.RetryResult(
                        "RATE_LIMITED", false, 42,
                        "Yêu cầu thử lại đang trong thời gian chờ."))
                .thenThrow(new IllegalStateException(
                        "PROVIDER_HTTP_500 raw provider response"));

        RedirectAttributesModelMap cooldownFlash =
                new RedirectAttributesModelMap();
        String cooldownRedirect = controller.retryQuestionExplanation(
                90L, 70L, lecturer, cooldownFlash);
        RedirectAttributesModelMap errorFlash =
                new RedirectAttributesModelMap();
        String errorRedirect = controller.retryQuestionExplanation(
                90L, 70L, lecturer, errorFlash);

        assertThat(cooldownRedirect)
                .isEqualTo("redirect:/practice/manage/revisions?setId=90");
        assertThat(cooldownFlash.getFlashAttributes().get("error").toString())
                .contains("42 giây")
                .doesNotContain("provider", "PROVIDER_", "raw");
        assertThat(errorRedirect)
                .isEqualTo("redirect:/practice/manage/revisions?setId=90");
        assertThat(errorFlash.getFlashAttributes().get("error").toString())
                .contains("Không thể xử lý yêu cầu thử lại")
                .doesNotContain("provider", "PROVIDER_", "raw", "500");
    }

    @Test
    void authorizationFailureIsNotConvertedToAFlashOrRetry() {
        when(retryService.retryQuestionVersion(90L, 70L, 7L))
                .thenThrow(new AccessDeniedException("denied"));

        assertThatThrownBy(() -> controller.retryQuestionExplanation(
                90L, 70L, lecturer, new RedirectAttributesModelMap()))
                .isInstanceOf(AccessDeniedException.class);
    }
}
