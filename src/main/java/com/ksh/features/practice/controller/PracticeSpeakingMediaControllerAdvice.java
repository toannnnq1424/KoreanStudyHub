package com.ksh.features.practice.controller;

import com.ksh.features.practice.dto.PracticeDtos.SpeakingMediaErrorResponse;
import com.ksh.features.practice.service.audio.SpeakingAudioValidationCategory;
import com.ksh.features.practice.service.audio.SpeakingAudioValidationException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Order(0)
@RestControllerAdvice(assignableTypes = PracticeSpeakingMediaController.class)
public class PracticeSpeakingMediaControllerAdvice {

    private static final Logger log = LoggerFactory.getLogger(PracticeSpeakingMediaControllerAdvice.class);
    private static final CacheControl NO_STORE = CacheControl.maxAge(0, TimeUnit.SECONDS)
            .noStore()
            .mustRevalidate();

    private static final String MESSAGE_INVALID_MULTIPART = "Exactly one learner audio file is required.";
    private static final String MESSAGE_EMPTY = "Audio file is empty.";
    private static final String MESSAGE_TOO_LARGE = "Audio file is too large.";
    private static final String MESSAGE_TOO_LONG = "Audio file is too long.";
    private static final String MESSAGE_UNSUPPORTED = "Audio format is not supported.";
    private static final String MESSAGE_CORRUPT = "Audio file could not be processed.";
    private static final String MESSAGE_TEMPORARY = "Audio service is temporarily unavailable.";
    private static final String MESSAGE_NOT_FOUND = "Speaking media target was not found.";
    private static final String MESSAGE_CONFLICT = "Speaking media cannot be changed in the current state.";
    private static final String MESSAGE_AUTH = "Authentication is required.";
    private static final String MESSAGE_UNEXPECTED = "Speaking media request failed.";

    static InvalidMultipartException invalidMultipart() {
        return new InvalidMultipartException();
    }

    static SpeakingAudioValidationException emptyUpload() {
        return new SpeakingAudioValidationException(SpeakingAudioValidationCategory.EMPTY, "Empty audio file.");
    }

    @ExceptionHandler(SpeakingAudioValidationException.class)
    public ResponseEntity<SpeakingMediaErrorResponse> handleValidation(SpeakingAudioValidationException ex) {
        return error(statusFor(ex.getCategory()), ex.getCategory().name(), messageFor(ex.getCategory()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<SpeakingMediaErrorResponse> handleNotFound(EntityNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", MESSAGE_NOT_FOUND);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<SpeakingMediaErrorResponse> handleConflict(IllegalStateException ex) {
        return error(HttpStatus.CONFLICT, "CONFLICT", MESSAGE_CONFLICT);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<SpeakingMediaErrorResponse> handleAuthentication(AuthenticationException ex) {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", MESSAGE_AUTH);
    }

    @ExceptionHandler({
            InvalidMultipartException.class,
            MissingServletRequestPartException.class,
            MultipartException.class
    })
    public ResponseEntity<SpeakingMediaErrorResponse> handleInvalidMultipart(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_MULTIPART", MESSAGE_INVALID_MULTIPART);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<SpeakingMediaErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "TOO_LARGE", MESSAGE_TOO_LARGE);
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<SpeakingMediaErrorResponse> handleIo(IOException ex) {
        return error(HttpStatus.BAD_REQUEST, "REQUEST_BODY_UNAVAILABLE", "Audio file could not be read.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<SpeakingMediaErrorResponse> handleUnexpected(Exception ex) {
        log.warn("Speaking media request failed");
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "UNEXPECTED_ERROR", MESSAGE_UNEXPECTED);
    }

    private static ResponseEntity<SpeakingMediaErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .cacheControl(NO_STORE)
                .body(new SpeakingMediaErrorResponse(code, message));
    }

    private static HttpStatus statusFor(SpeakingAudioValidationCategory category) {
        return switch (category) {
            case EMPTY, TOO_LONG, CORRUPT_MEDIA, PROBE_OUTPUT_TOO_LARGE -> HttpStatus.BAD_REQUEST;
            case TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case UNSUPPORTED_TYPE, INVALID_CONTAINER, UNSUPPORTED_CODEC,
                 MULTIPLE_AUDIO_STREAMS, NON_AUDIO_STREAM_PRESENT -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case PROBE_UNAVAILABLE, PROBE_TIMEOUT, STORAGE_FAILURE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    private static String messageFor(SpeakingAudioValidationCategory category) {
        return switch (category) {
            case EMPTY -> MESSAGE_EMPTY;
            case TOO_LARGE -> MESSAGE_TOO_LARGE;
            case TOO_LONG -> MESSAGE_TOO_LONG;
            case UNSUPPORTED_TYPE, INVALID_CONTAINER, UNSUPPORTED_CODEC,
                 MULTIPLE_AUDIO_STREAMS, NON_AUDIO_STREAM_PRESENT -> MESSAGE_UNSUPPORTED;
            case CORRUPT_MEDIA, PROBE_OUTPUT_TOO_LARGE -> MESSAGE_CORRUPT;
            case PROBE_UNAVAILABLE, PROBE_TIMEOUT, STORAGE_FAILURE -> MESSAGE_TEMPORARY;
        };
    }

    static final class InvalidMultipartException extends RuntimeException {
    }
}
