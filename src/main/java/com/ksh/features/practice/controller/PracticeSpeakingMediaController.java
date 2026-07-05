package com.ksh.features.practice.controller;

import com.ksh.features.practice.dto.PracticeDtos.SpeakingMediaDeleteResponse;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingMediaUploadResponse;
import com.ksh.features.practice.service.SpeakingAudioUploadService;
import com.ksh.security.AuthenticatedUserIdResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/practice")
@PreAuthorize("isAuthenticated()")
@ConditionalOnProperty(
        prefix = "app.practice.speaking-media",
        name = "upload-api-enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class PracticeSpeakingMediaController {

    private static final CacheControl NO_STORE = CacheControl.maxAge(0, TimeUnit.SECONDS)
            .noStore()
            .mustRevalidate();

    private final SpeakingAudioUploadService uploadService;
    private final AuthenticatedUserIdResolver userIdResolver;

    public PracticeSpeakingMediaController(SpeakingAudioUploadService uploadService,
                                           AuthenticatedUserIdResolver userIdResolver) {
        this.uploadService = uploadService;
        this.userIdResolver = userIdResolver;
    }

    @PostMapping(
            path = "/attempts/{attemptId}/questions/{questionId}/speaking-media",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<SpeakingMediaUploadResponse> uploadOrReplace(
            @PathVariable Long attemptId,
            @PathVariable Long questionId,
            @RequestPart(name = "file", required = false) List<MultipartFile> files,
            MultipartHttpServletRequest request,
            Authentication authentication) throws IOException {
        Long userId = userIdResolver.resolve(authentication);
        MultipartFile file = requireSingleLearnerAudio(files, request);
        if (file.isEmpty()) {
            throw PracticeSpeakingMediaControllerAdvice.emptyUpload();
        }

        SpeakingAudioUploadService.SpeakingAudioUploadResult result;
        try (InputStream input = file.getInputStream()) {
            result = uploadService.uploadOrReplaceForOwner(
                    userId,
                    attemptId,
                    questionId,
                    input,
                    file.getSize(),
                    file.getContentType());
        }

        return ResponseEntity.ok()
                .cacheControl(NO_STORE)
                .body(toResponse(result));
    }

    @DeleteMapping(
            path = "/attempts/{attemptId}/questions/{questionId}/speaking-media/{mediaId}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<SpeakingMediaDeleteResponse> delete(
            @PathVariable Long attemptId,
            @PathVariable Long questionId,
            @PathVariable Long mediaId,
            Authentication authentication) {
        Long userId = userIdResolver.resolve(authentication);
        SpeakingAudioUploadService.SpeakingAudioDeletionResult result =
                uploadService.deleteForOwner(userId, attemptId, questionId, mediaId);

        return ResponseEntity.ok()
                .cacheControl(NO_STORE)
                .body(toResponse(result));
    }

    private static MultipartFile requireSingleLearnerAudio(List<MultipartFile> files,
                                                           MultipartHttpServletRequest request) {
        int totalFileParts = request.getMultiFileMap().values().stream()
                .mapToInt(List::size)
                .sum();
        int namedFileParts = files == null ? 0 : files.size();
        if (totalFileParts != 1 || namedFileParts != 1) {
            throw PracticeSpeakingMediaControllerAdvice.invalidMultipart();
        }
        return files.get(0);
    }

    private static SpeakingMediaUploadResponse toResponse(
            SpeakingAudioUploadService.SpeakingAudioUploadResult result) {
        return new SpeakingMediaUploadResponse(
                result.mediaId(),
                result.questionId(),
                result.status().name(),
                result.byteSize(),
                result.durationMs(),
                result.mimeType(),
                result.lockVersion());
    }

    private static SpeakingMediaDeleteResponse toResponse(
            SpeakingAudioUploadService.SpeakingAudioDeletionResult result) {
        return new SpeakingMediaDeleteResponse(
                result.mediaId(),
                result.status().name());
    }
}
