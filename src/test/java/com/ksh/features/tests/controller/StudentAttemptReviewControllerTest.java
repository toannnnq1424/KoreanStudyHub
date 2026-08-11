package com.ksh.features.tests.controller;

import com.ksh.features.tests.service.TestAttemptService;
import com.ksh.features.tests.service.TestAttemptUnavailableException;
import com.ksh.features.tests.service.TestCatalogService;
import com.ksh.security.KshUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentAttemptReviewControllerTest {

    private static final Long CLASS_ID = 7L;
    private static final Long TEST_ID = 21L;
    private static final Long ATTEMPT_ID = 101L;
    private static final Long USER_ID = 42L;
    private static final String UNAVAILABLE_MESSAGE =
            "Chưa thể xem kết quả trước khi bài làm được nộp hoặc hết giờ.";

    @Mock private TestCatalogService catalogService;
    @Mock private TestAttemptService attemptService;
    @Mock private KshUserDetails user;

    private StudentTestController studentController;
    private StudentClassTestsController classController;

    @BeforeEach
    void setUp() {
        studentController = new StudentTestController(catalogService, attemptService);
        classController = new StudentClassTestsController(catalogService, attemptService);
        when(user.getId()).thenReturn(USER_ID);
    }

    @Test
    void global_result_and_review_routes_return_an_open_attempt_to_the_take_screen() {
        rejectStudentResultAndReview();

        ExtendedModelMap resultModel = new ExtendedModelMap();
        RedirectAttributesModelMap resultRedirect = new RedirectAttributesModelMap();
        String resultView = studentController.result(
                TEST_ID, ATTEMPT_ID, user, resultModel, resultRedirect);

        ExtendedModelMap reviewModel = new ExtendedModelMap();
        RedirectAttributesModelMap reviewRedirect = new RedirectAttributesModelMap();
        String reviewView = studentController.review(
                TEST_ID, ATTEMPT_ID, user, reviewModel, reviewRedirect);

        assertRejectedRoute(resultView, "/my/tests/21/take", resultModel, resultRedirect);
        assertRejectedRoute(reviewView, "/my/tests/21/take", reviewModel, reviewRedirect);
        verifyNoInteractions(catalogService);
    }

    @Test
    void class_scoped_result_and_review_routes_return_an_open_attempt_to_the_take_screen() {
        rejectStudentResultAndReview();

        ExtendedModelMap resultModel = new ExtendedModelMap();
        RedirectAttributesModelMap resultRedirect = new RedirectAttributesModelMap();
        String resultView = classController.result(
                CLASS_ID, TEST_ID, ATTEMPT_ID, user, resultModel, resultRedirect);

        ExtendedModelMap reviewModel = new ExtendedModelMap();
        RedirectAttributesModelMap reviewRedirect = new RedirectAttributesModelMap();
        String reviewView = classController.review(
                CLASS_ID, TEST_ID, ATTEMPT_ID, user, reviewModel, reviewRedirect);

        String takeUrl = "/my/classes/7/tests/21/take";
        assertRejectedRoute(resultView, takeUrl, resultModel, resultRedirect);
        assertRejectedRoute(reviewView, takeUrl, reviewModel, reviewRedirect);
        verifyNoInteractions(catalogService);
    }

    private void rejectStudentResultAndReview() {
        when(attemptService.result(TEST_ID, ATTEMPT_ID, USER_ID))
                .thenThrow(new TestAttemptUnavailableException(UNAVAILABLE_MESSAGE));
        when(attemptService.review(TEST_ID, ATTEMPT_ID, USER_ID))
                .thenThrow(new TestAttemptUnavailableException(UNAVAILABLE_MESSAGE));
    }

    private static void assertRejectedRoute(String actualView, String takeUrl,
                                            ExtendedModelMap model,
                                            RedirectAttributesModelMap redirect) {
        assertThat(actualView).isEqualTo("redirect:" + takeUrl);
        assertThat(model).doesNotContainKeys("result", "review");
        assertThat(redirect.getFlashAttributes().get("flashError"))
                .isEqualTo(UNAVAILABLE_MESSAGE);
    }
}
