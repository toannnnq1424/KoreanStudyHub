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

        PracticeByteRange.Selection range = PracticeByteRange.parse(
                rangeHeader, playback.byteSize());
        if (range.unsatisfiable()) {
            PracticeByteRange.closeQuietly(playback.inputStream());
            return response(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, playback.mimeType(), 0L)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + playback.byteSize())
                    .body(outputStream -> { });
        }

        long contentLength = range.length();
        StreamingResponseBody body = PracticeByteRange.body(
                playback::inputStream, range);

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

}
