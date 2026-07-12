package com.ksh.features.practice.controller;

import com.ksh.features.practice.manage.service.PracticeMaterialAccessService;
import com.ksh.security.AuthenticatedUserIdResolver;
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

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/practice/materials")
@PreAuthorize("isAuthenticated()")
public class PracticeMaterialController {

    private final PracticeMaterialAccessService accessService;
    private final AuthenticatedUserIdResolver userIdResolver;

    public PracticeMaterialController(PracticeMaterialAccessService accessService,
                                      AuthenticatedUserIdResolver userIdResolver) {
        this.accessService = accessService;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping("/{assetId}/content")
    public ResponseEntity<StreamingResponseBody> content(
            @PathVariable Long assetId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            Authentication authentication) throws Exception {
        PracticeMaterialAccessService.MaterialContent content =
                accessService.load(assetId, userIdResolver.resolve(authentication));
        long total = content.sizeBytes() != null && content.sizeBytes() >= 0L
                ? content.sizeBytes()
                : content.resource().contentLength();
        PracticeByteRange.Selection range = PracticeByteRange.parse(rangeHeader, total);
        if (range.unsatisfiable()) {
            return response(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, content, 0L)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + total)
                    .body(outputStream -> { });
        }

        ResponseEntity.BodyBuilder response = response(
                range.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK,
                content,
                range.length());
        if (range.partial()) {
            response.header(HttpHeaders.CONTENT_RANGE,
                    "bytes " + range.start() + "-" + range.end() + "/" + total);
        }
        return response.body(PracticeByteRange.body(
                content.resource()::getInputStream, range));
    }

    private static ResponseEntity.BodyBuilder response(
            HttpStatus status,
            PracticeMaterialAccessService.MaterialContent content,
            long contentLength) {
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(content.filename() == null ? "material" : content.filename(),
                        StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .contentLength(contentLength)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff");
    }
}
