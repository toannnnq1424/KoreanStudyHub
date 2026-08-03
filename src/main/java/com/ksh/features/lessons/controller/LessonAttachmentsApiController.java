package com.ksh.features.lessons.controller;

import com.ksh.features.lessons.service.LessonAttachmentsService;
import com.ksh.features.lessons.service.LessonAttachmentsService.DownloadHandle;
import com.ksh.features.storage.ObjectStorage;
import com.ksh.features.storage.StoredObject;
import com.ksh.features.storage.StoredObjectResource;
import com.ksh.security.KshUserDetails;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;


/**
 * Read-only streaming endpoint for materials on distributed lessons.
 * Authoring uploads are owned by Library; class-scoped attachment mutation
 * routes intentionally do not exist.
 */
@RestController
public class LessonAttachmentsApiController {

    private static final Logger log = LoggerFactory.getLogger(LessonAttachmentsApiController.class);

    private final LessonAttachmentsService attachmentsService;
    private final ObjectStorage objectStorage;

    public LessonAttachmentsApiController(LessonAttachmentsService attachmentsService,
                                          ObjectStorage objectStorage) {
        this.attachmentsService = attachmentsService;
        this.objectStorage = objectStorage;
    }

    @GetMapping("/api/lessons/{lessonId}/attachments/{attachmentId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> download(@PathVariable Long lessonId,
                                             @PathVariable Long attachmentId,
                                             @AuthenticationPrincipal KshUserDetails user) {
        DownloadHandle handle;
        try {
            handle = attachmentsService.download(lessonId, attachmentId,
                    user.getId(), user.getRole());
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        StoredObject obj;
        try {
            if (!objectStorage.exists(handle.storageKey())) {
                log.warn("Attachment row {} points at missing object {}", attachmentId, handle.storageKey());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            obj = objectStorage.open(handle.storageKey());
        } catch (IOException ex) {
            log.error("Failed to read attachment object {}", handle.storageKey(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(safeFilename(handle.originalFilename()), StandardCharsets.UTF_8)
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(disposition);
        headers.setContentType(parseMime(handle.mimeType()));
        long length = obj.contentLength() >= 0 ? obj.contentLength() : handle.sizeBytes();
        if (length >= 0) {
            headers.setContentLength(length);
        }
        return new ResponseEntity<>(new StoredObjectResource(obj, handle.storageKey()),
                headers, HttpStatus.OK);
    }

    private static String safeFilename(String name) {
        return name == null || name.isBlank() ? "attachment" : name;
    }

    private static MediaType parseMime(String value) {
        try {
            return MediaType.parseMediaType(value);
        } catch (RuntimeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
