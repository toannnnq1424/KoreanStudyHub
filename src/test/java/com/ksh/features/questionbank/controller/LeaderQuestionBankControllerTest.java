package com.ksh.features.questionbank.controller;

import com.ksh.features.leader.service.LeaderDepartmentResolver;
import com.ksh.features.questionbank.controller.LeaderQuestionBankController.ReviewFilters;
import com.ksh.features.questionbank.service.QuestionBankItemService;
import com.ksh.features.questionbank.service.QuestionBankReviewService;
import com.ksh.features.questionbank.service.QuestionBankValidationException;
import com.ksh.security.KshUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LeaderQuestionBankControllerTest {

    private final QuestionBankReviewService reviewService = mock(QuestionBankReviewService.class);
    private final LeaderQuestionBankController controller = new LeaderQuestionBankController(
            mock(QuestionBankItemService.class), reviewService,
            mock(LeaderDepartmentResolver.class));
    private final KshUserDetails user = mock(KshUserDetails.class);
    private final ReviewFilters filters = new ReviewFilters(5L, "REVIEW", 20L, "xin chào");

    @BeforeEach
    void setUp() {
        when(user.getId()).thenReturn(30L);
    }

    @Test
    void stale_state_is_redirected_back_with_existing_filters_and_error_flash() {
        doThrow(new QuestionBankValidationException("Trạng thái đã thay đổi"))
                .when(reviewService).approve(30L, 10L);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.approve(10L, filters, user, redirect);

        assertThat(view).isEqualTo("redirect:/leader/question-bank");
        assertThat(redirect.getFlashAttributes().get("flashError"))
                .isEqualTo("Trạng thái đã thay đổi");
        assertThat(redirect.getFlashAttributes()).doesNotContainKey("flashSuccess");
        assertThat(redirect.getAttribute("subjectId")).isEqualTo("5");
        assertThat(redirect.getAttribute("status")).isEqualTo("REVIEW");
        assertThat(redirect.getAttribute("contributorId")).isEqualTo("20");
        assertThat(redirect.getAttribute("q")).isEqualTo("xin chào");
    }

    @Test
    void overlapping_review_is_redirected_with_a_conflict_flash() {
        doThrow(new OptimisticLockingFailureException("row version changed"))
                .when(reviewService).reject(30L, 10L, "note");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.reject(10L, "note", filters, user, redirect);

        assertThat(view).isEqualTo("redirect:/leader/question-bank");
        assertThat(redirect.getFlashAttributes().get("flashError").toString())
                .contains("vừa được người khác cập nhật");
        assertThat(redirect.getFlashAttributes()).doesNotContainKey("flashSuccess");
    }

    @Test
    void unexpected_failure_is_not_converted_to_a_user_conflict() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(reviewService).archive(30L, 10L, null);

        assertThatThrownBy(() -> controller.archive(
                10L, null, filters, user, new RedirectAttributesModelMap()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
    }
}
