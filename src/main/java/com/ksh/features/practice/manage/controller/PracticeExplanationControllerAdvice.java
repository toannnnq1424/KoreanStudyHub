package com.ksh.features.practice.manage.controller;

import com.ksh.features.practice.ai.readinglistening.ExplanationProviderException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Keeps the draft editor API JSON-only when AI generation or revision storage fails. */
@RestControllerAdvice(assignableTypes = PracticeExplanationController.class)
public class PracticeExplanationControllerAdvice {

    @ExceptionHandler(ExplanationProviderException.class)
    ResponseEntity<Map<String, Object>> providerFailure(
            ExplanationProviderException exception) {
        HttpStatus status = exception.retryable()
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(Map.of(
                "code", exception.category(),
                "message", providerMessage(exception.category()),
                "retryable", exception.retryable()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> invalidRequest(
            IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "code", "EXPLANATION_CONTRACT_INVALID",
                "message", safeMessage(exception,
                        "Bản lời giải không khớp dữ liệu câu hỏi hiện tại."),
                "retryable", false));
    }

    @ExceptionHandler({IllegalStateException.class,
            DataIntegrityViolationException.class})
    ResponseEntity<Map<String, Object>> staleOrConflictingRevision(
            RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "EXPLANATION_REVISION_CONFLICT",
                "message", safeMessage(exception,
                        "Bản nháp đã thay đổi; hãy tải lại câu hỏi trước khi tạo lời giải."),
                "retryable", true));
    }

    private static String providerMessage(String category) {
        if ("PROVIDER_TRANSPORT_ERROR".equals(category)) {
            return "Nhà cung cấp AI chưa phản hồi kịp. Bản nháp chưa được lưu; hãy thử lại sau.";
        }
        return "AI chưa tạo được bản lời giải đúng dữ liệu đã kiểm chứng. Bản nháp hiện tại không bị thay đổi.";
    }

    private static String safeMessage(RuntimeException exception,
            String fallback) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }
}
