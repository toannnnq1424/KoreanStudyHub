package com.ksh.features.practice.controller;

import com.ksh.features.practice.service.PracticeSpeakingMediaPlaybackService;
import com.ksh.features.practice.web.PracticeMediaRoutes;
import com.ksh.features.practice.web.PracticeRoutes;
import com.ksh.security.AuthenticatedUserIdResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(PracticeRoutes.BASE)
@PreAuthorize("hasRole('STUDENT')")
@ConditionalOnProperty(
        prefix = "app.practice.speaking-media",
        name = "playback-api-enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class PracticeSpeakingMediaPlaybackController {
    private static final CacheControl NO_STORE = CacheControl.maxAge(0, TimeUnit.SECONDS)
            .noStore()
            .cachePrivate()
            .mustRevalidate();
    private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final int BUFFER_SIZE = 8192;

    private final PracticeSpeakingMediaPlaybackService playbackService;
    private final AuthenticatedUserIdResolver userIdResolver;

    public PracticeSpeakingMediaPlaybackController(PracticeSpeakingMediaPlaybackService playbackService,
                                                   AuthenticatedUserIdResolver userIdResolver) {
        this.playbackService = playbackService;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping(PracticeMediaRoutes.SPEAKING_MEDIA_CONTENT)
    public ResponseEntity<StreamingResponseBody> content(@PathVariable Long attemptId,
                                                         @PathVariable Long questionId,
                                                         @PathVariable Long mediaId,
                                                         @RequestHeader(value = HttpHeaders.RANGE, required = false)
                                                         String rangeHeader,
                                                         Authentication authentication) {
        Long userId = userIdResolver.resolve(authentication);
        PracticeSpeakingMediaPlaybackService.PlaybackStream playback =
                playbackService.openForOwner(userId, attemptId, questionId, mediaId);

        ByteRange range = ByteRange.parse(rangeHeader, playback.byteSize());
        if (range.unsatisfiable()) {
            closeQuietly(playback.inputStream());
            return response(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, playback.mimeType(), 0L)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + playback.byteSize())
                    .body(outputStream -> { });
        }

        long contentLength = range.length();
        StreamingResponseBody body = outputStream -> {
            try (InputStream input = playback.inputStream()) {
                skipFully(input, range.start());
                copyBounded(input, outputStream, contentLength);
            }
        };

        ResponseEntity.BodyBuilder builder = response(
                range.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK,
                playback.mimeType(),
                contentLength);
        if (range.partial()) {
            builder.header(HttpHeaders.CONTENT_RANGE, "bytes " + range.start() + "-" + range.end()
                    + "/" + playback.byteSize());
        }
        return builder.body(body);
    }

    private static ResponseEntity.BodyBuilder response(HttpStatus status, String mimeType, long contentLength) {
        return ResponseEntity.status(status)
                .cacheControl(NO_STORE)
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header(X_CONTENT_TYPE_OPTIONS, "nosniff")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .contentType(MediaType.parseMediaType(mimeType))
                .contentLength(contentLength);
    }

    private static void copyBounded(InputStream input, java.io.OutputStream output, long byteSize) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = byteSize;
        while (remaining > 0L) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read == -1) {
                return;
            }
            output.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private static void skipFully(InputStream input, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0L) {
            long skipped = input.skip(remaining);
            if (skipped > 0L) {
                remaining -= skipped;
                continue;
            }
            if (input.read() == -1) {
                throw new EOFException("Audio stream ended before requested range.");
            }
            remaining--;
        }
    }

    private static void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // Nothing useful can be returned to the client for a rejected range.
        }
    }

    private record ByteRange(long start, long end, boolean partial, boolean unsatisfiable) {
        private static ByteRange parse(String header, long total) {
            if (header == null || header.isBlank()) {
                return new ByteRange(0L, total - 1L, false, false);
            }
            String value = header.trim();
            if (!value.startsWith("bytes=")) {
                return unsatisfiable(total);
            }
            String spec = value.substring("bytes=".length()).trim();
            if (spec.isBlank() || spec.contains(",")) {
                return unsatisfiable(total);
            }
            int separator = spec.indexOf('-');
            if (separator < 0 || separator != spec.lastIndexOf('-')) {
                return unsatisfiable(total);
            }

            String startText = spec.substring(0, separator).trim();
            String endText = spec.substring(separator + 1).trim();
            if (startText.isEmpty()) {
                Long suffixLength = parseDigits(endText);
                if (suffixLength == null || suffixLength <= 0L) {
                    return unsatisfiable(total);
                }
                long start = suffixLength >= total ? 0L : total - suffixLength;
                return new ByteRange(start, total - 1L, true, false);
            }

            Long start = parseDigits(startText);
            if (start == null || start >= total) {
                return unsatisfiable(total);
            }
            long end = total - 1L;
            if (!endText.isEmpty()) {
                Long parsedEnd = parseDigits(endText);
                if (parsedEnd == null || start > parsedEnd) {
                    return unsatisfiable(total);
                }
                end = Math.min(parsedEnd, total - 1L);
            }
            return new ByteRange(start, end, true, false);
        }

        private static Long parseDigits(String text) {
            if (text == null || text.isBlank()) {
                return null;
            }
            for (int i = 0; i < text.length(); i++) {
                if (!Character.isDigit(text.charAt(i))) {
                    return null;
                }
            }
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        private static ByteRange unsatisfiable(long total) {
            return new ByteRange(0L, total - 1L, false, true);
        }

        private long length() {
            return unsatisfiable ? 0L : end - start + 1L;
        }
    }
}
