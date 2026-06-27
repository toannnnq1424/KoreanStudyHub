package com.ksh.shared.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Global exception handler for controllers.
 *
 * <p>Sprint 2 adds 2 specific handlers:
 * <ul>
 *   <li>{@link EntityNotFoundException} → 404 (class/entity does not exist
 *       or has been soft-deleted).</li>
 *   <li>{@link AccessDeniedException} → 403 (violates ownership check from
 *       service layer; Spring Security only handles instances arising
 *       in the filter chain or via {@code @PreAuthorize}).</li>
 * </ul>
 *
 * <p>Specific handlers must be placed BEFORE the catch-all {@code Exception.class}
 * for Spring to select correctly — Spring's exception resolver matches by order of
 * specificity, but we place them in this order for clarity.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(EntityNotFoundException ex, HttpServletRequest request, Model model) {
        log.info("404 tai [{}]: {}", request.getRequestURI(), ex.getMessage());
        model.addAttribute("message",
                ex.getMessage() != null ? ex.getMessage() : "Không tìm thấy nội dung yêu cầu.");
        return "error";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleForbidden(AccessDeniedException ex, HttpServletRequest request, Model model) {
        log.info("403 tai [{}]: {}", request.getRequestURI(), ex.getMessage());
        model.addAttribute("message", "Bạn không có quyền thực hiện thao tác này.");
        return "error";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleUnexpected(Exception ex, HttpServletRequest request, Model model) {
        log.error("Loi khong xu ly tai [{}]: {}", request.getRequestURI(), ex.getMessage(), ex);
        model.addAttribute("message", "Da co loi xay ra. Vui long thu lai sau.");
        return "error";
    }
}