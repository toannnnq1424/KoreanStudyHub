package com.ksh.features.practice.manage.controller;

import com.ksh.features.practice.ai.readinglistening.QuestionExplanationRetryService;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PracticeExplanationControllerTest {

    private QuestionExplanationRetryService retryService;
    private KshUserDetails lecturer;
    private PracticeExplanationController controller;

    @BeforeEach
    void setUp() {
        retryService = mock(QuestionExplanationRetryService.class);
        lecturer = mock(KshUserDetails.class);
        when(lecturer.getId()).thenReturn(7L);
        controller = new PracticeExplanationController(retryService);
    }

    @Test
    void restBoundaryRequiresExactLecturerRole() {
        PreAuthorize boundary =
                PracticeExplanationController.class.getAnnotation(PreAuthorize.class);

        assertThat(boundary).isNotNull();
        assertThat(boundary.value()).isEqualTo(Roles.PREAUTH_LECTURER);
        assertThat(boundary.value())
                .doesNotContain("HEAD", "ADMIN", "LECTURER_OR_ABOVE");
    }

    @Test
    void readyAndPendingNoOpsReturnOkWithoutInventingAnotherRequest() {
        when(retryService.retry(50L, 7L))
                .thenReturn(new QuestionExplanationRetryService.RetryResult(
                        "READY", false, 0,
                        "Giải thích đã sẵn sàng; không cần tạo lại."))
                .thenReturn(new QuestionExplanationRetryService.RetryResult(
                        "PENDING", false, 0,
                        "Giải thích đang được xử lý; hệ thống không tạo yêu cầu trùng lặp."));

        ResponseEntity<Map<String, Object>> ready =
                controller.retry(50L, lecturer);
        ResponseEntity<Map<String, Object>> pending =
                controller.retry(50L, lecturer);

        assertThat(ready.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pending.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ready.getBody()).containsEntry("queued", false);
        assertThat(pending.getBody()).containsEntry("queued", false);
    }

    @Test
    void queuedRetryReturnsAcceptedAndNonRetryableReturnsConflict() {
        when(retryService.retry(50L, 7L))
                .thenReturn(new QuestionExplanationRetryService.RetryResult(
                        "PENDING", true, 0,
                        "Đã xếp lịch tạo lại giải thích."))
                .thenReturn(new QuestionExplanationRetryService.RetryResult(
                        "NOT_RETRYABLE", false, 0,
                        "Không thể thử lại. Hãy sửa nội dung hoặc bằng chứng rồi xuất bản phiên bản mới."));

        ResponseEntity<Map<String, Object>> queued =
                controller.retry(50L, lecturer);
        ResponseEntity<Map<String, Object>> nonRetryable =
                controller.retry(50L, lecturer);

        assertThat(queued.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(queued.getBody()).containsEntry("queued", true);
        assertThat(nonRetryable.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(nonRetryable.getBody()).containsEntry("queued", false);
    }

    @Test
    void cooldownReturnsTooManyRequestsWithServerRetryAfterAndSafeLocalizedBody() {
        when(retryService.retry(50L, 7L))
                .thenReturn(new QuestionExplanationRetryService.RetryResult(
                        "RATE_LIMITED", false, 37,
                        "Yêu cầu thử lại đang trong thời gian chờ."));

        ResponseEntity<Map<String, Object>> response =
                controller.retry(50L, lecturer);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))
                .isEqualTo("37");
        assertThat(response.getBody().get("message").toString())
                .contains("thời gian chờ")
                .doesNotContain("provider", "PROVIDER_", "exception", "raw");
    }
}
