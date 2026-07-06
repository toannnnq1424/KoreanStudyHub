package com.ksh.features.practice.controller;

import com.ksh.features.practice.service.PracticeSpeakingMediaPlaybackService;
import com.ksh.security.AuthenticatedUserIdResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/practice")
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

    @GetMapping("/attempts/{attemptId}/questions/{questionId}/speaking-media/{mediaId}/content")
    public ResponseEntity<StreamingResponseBody> content(@PathVariable Long attemptId,
                                                         @PathVariable Long questionId,
                                                         @PathVariable Long mediaId,
                                                         Authentication authentication) {
        Long userId = userIdResolver.resolve(authentication);
        PracticeSpeakingMediaPlaybackService.PlaybackStream playback =
                playbackService.openForOwner(userId, attemptId, questionId, mediaId);
        StreamingResponseBody body = outputStream -> {
            try (InputStream input = playback.inputStream()) {
                copyBounded(input, outputStream, playback.byteSize());
                outputStream.flush();
            }
        };

        return ResponseEntity.ok()
                .cacheControl(NO_STORE)
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header(X_CONTENT_TYPE_OPTIONS, "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .contentType(MediaType.parseMediaType(playback.mimeType()))
                .contentLength(playback.byteSize())
                .body(body);
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
}
