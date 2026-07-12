package com.ksh.features.practice.controller;

import com.ksh.features.practice.manage.service.PracticeMaterialAccessService;
import com.ksh.security.AuthenticatedUserIdResolver;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<Resource> content(
            @PathVariable Long assetId,
            Authentication authentication) throws Exception {
        PracticeMaterialAccessService.MaterialContent content =
                accessService.load(assetId, userIdResolver.resolve(authentication));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300");
        if (content.sizeBytes() != null && content.sizeBytes() >= 0) {
            response.contentLength(content.sizeBytes());
        }
        return response.body(content.resource());
    }
}
