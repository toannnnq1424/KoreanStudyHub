package com.ksh.features.practice.controller;

import com.ksh.features.practice.dto.PracticeDtos.SpeakingMediaErrorResponse;
import com.ksh.features.practice.service.PracticeSpeakingMediaPlaybackNotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.concurrent.TimeUnit;

@Order(0)
@RestControllerAdvice(assignableTypes = PracticeSpeakingMediaPlaybackController.class)
public class PracticeSpeakingMediaPlaybackControllerAdvice {
    private static final CacheControl NO_STORE = CacheControl.maxAge(0, TimeUnit.SECONDS)
            .noStore()
            .cachePrivate()
            .mustRevalidate();
    private static final String MESSAGE_NOT_FOUND = "Speaking media target was not found.";
    private static final String MESSAGE_AUTH = "Authentication is required.";
    private static final String MESSAGE_FORBIDDEN = "Speaking media target was not found.";

    @ExceptionHandler(PracticeSpeakingMediaPlaybackNotFoundException.class)
    public ResponseEntity<SpeakingMediaErrorResponse> handleUnavailable(
            PracticeSpeakingMediaPlaybackNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", MESSAGE_NOT_FOUND);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<SpeakingMediaErrorResponse> handleAuthentication(AuthenticationException ex) {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", MESSAGE_AUTH);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<SpeakingMediaErrorResponse> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", MESSAGE_FORBIDDEN);
    }

    private static ResponseEntity<SpeakingMediaErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .cacheControl(NO_STORE)
                .body(new SpeakingMediaErrorResponse(code, message));
    }
}
