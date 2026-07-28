package com.ksh.features.practice.manage.speaking;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(assignableTypes = SpeakingPromptAuthoringController.class)
public class SpeakingPromptAuthoringControllerAdvice {

    @ExceptionHandler({
            SpeakingPromptAuthoringConflictException.class,
            ObjectOptimisticLockingFailureException.class
    })
    ResponseEntity<?> conflict(RuntimeException exception) {
        return failure(
                HttpStatus.CONFLICT,
                "SOURCE_CONFLICT",
                "Nội dung đã thay đổi ở nơi khác. Vui lòng tải lại trạng thái mới nhất.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> forbidden(AccessDeniedException exception) {
        return failure(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "Bạn không có quyền chỉnh sửa đề bài Nói này.");
    }

    @ExceptionHandler(EntityNotFoundException.class)
    ResponseEntity<?> notFound(EntityNotFoundException exception) {
        return failure(
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                "Không tìm thấy nguồn đề bài Nói hiện tại.");
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            ConstraintViolationException.class,
            MethodArgumentNotValidException.class,
            MaxUploadSizeExceededException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    ResponseEntity<?> invalid(Exception exception) {
        return failure(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_INPUT",
                vietnameseInputMessage(exception));
    }

    @ExceptionHandler(SpeakingPromptAiContract.ProviderFailure.class)
    ResponseEntity<?> provider(
            SpeakingPromptAiContract.ProviderFailure failure) {
        return switch (failure.publicCategory()) {
            case RATE_LIMIT -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, "30")
                    .body(new SpeakingPromptAuthoringController.ApiFailure(
                            "RATE_LIMIT",
                            "Dịch vụ AI đang giới hạn yêu cầu. Vui lòng thử lại sau.",
                            30L));
            case CONFIGURATION -> failure(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_UNAVAILABLE",
                    "Tính năng AI hiện chưa sẵn sàng. Vui lòng thử lại sau.");
            case TIMEOUT, TRANSPORT -> failure(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_TEMPORARILY_UNAVAILABLE",
                    "Dịch vụ AI tạm thời chưa xử lý được yêu cầu.");
            case STALE_COMPLETION -> failure(
                    HttpStatus.CONFLICT,
                    "SOURCE_CONFLICT",
                    "Kết quả không còn khớp nguồn đề bài hiện tại.");
            case INVALID_INPUT, PROVIDER_REJECTED, EMPTY_OUTPUT, MALFORMED_OUTPUT ->
                    failure(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "UNPROCESSABLE_AUDIO",
                            "Tệp hoặc nội dung này không thể được xử lý.");
        };
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<?> unavailable(IllegalStateException exception) {
        return failure(
                HttpStatus.SERVICE_UNAVAILABLE,
                "TEMPORARILY_UNAVAILABLE",
                "Tính năng biên soạn đề bài Nói tạm thời chưa sẵn sàng.");
    }

    private static ResponseEntity<?> failure(
            HttpStatus status,
            String code,
            String message) {
        return ResponseEntity.status(status).body(
                new SpeakingPromptAuthoringController.ApiFailure(
                        code, message, null));
    }

    private static String vietnameseInputMessage(Exception exception) {
        if (exception instanceof MaxUploadSizeExceededException) {
            return "Tệp audio vượt quá dung lượng cho phép.";
        }
        return "Tệp hoặc nội dung đề bài Nói không hợp lệ.";
    }
}
