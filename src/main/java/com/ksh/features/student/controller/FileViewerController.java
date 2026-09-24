package com.ksh.features.student.controller;

import com.ksh.features.lessons.service.LessonAttachmentsService;
import com.ksh.features.lessons.service.PublicViewTokenService;
import com.ksh.security.KshUserDetails;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Serves thin viewer pages for PDF (PDF.js) and DOCX (JSZip + docx-preview).
 * The viewer templates fetch the file from the existing download endpoint at
 * runtime — the server never buffers the whole file into the HTML, so large
 * PDFs stream instead of inflating memory.
 */
@Controller
public class FileViewerController {

    private final LessonAttachmentsService attachmentsService;
    private final PublicViewTokenService tokenService;
    @org.springframework.beans.factory.annotation.Autowired
    private com.ksh.features.library.service.PersonalLibraryAssetPreviewService previewService;
    @org.springframework.beans.factory.annotation.Autowired
    private com.ksh.features.library.service.StaticPresentationService presentationService;
    @org.springframework.beans.factory.annotation.Autowired
    private com.ksh.features.classes.service.ClassMaterialsService materialsService;

    @GetMapping("/file-viewer/material")
    @PreAuthorize("isAuthenticated()")
    public String material(@RequestParam Long classId, @RequestParam Long materialId,
            @AuthenticationPrincipal KshUserDetails user, Model model) {
        var handle = materialsService.download(classId, materialId, user.getId(), user.getRole());
        String url = "/api/classes/" + classId + "/materials/" + materialId + "/download";
        if (handle.originalFilename().toLowerCase(java.util.Locale.ROOT).matches(".*\\.(pdf|pptx?)")) {
            model.addAttribute("filename", handle.originalFilename());
            model.addAttribute("downloadUrl", "/file-viewer/material/slides?classId=" + classId + "&materialId=" + materialId);
            model.addAttribute("originalDownloadUrl", url);
            return "student/pdfjs-viewer";
        }
        var detail = new com.ksh.features.library.dto.LibraryDtos.LibraryAssetDetail(
                materialId, handle.originalFilename(), handle.originalFilename(), "DOCUMENT", handle.mimeType(),
                "Tài liệu", "document", handle.sizeBytes(), null, null, null, url, url, java.util.List.of());
        model.addAttribute("assetPreview", previewService.loadAuthorized(detail, handle.storageKey()));
        return "library/asset-preview";
    }

    @GetMapping("/file-viewer/material/slides")
    @PreAuthorize("isAuthenticated()")
    public org.springframework.http.ResponseEntity<byte[]> materialSlides(@RequestParam Long classId, @RequestParam Long materialId,
            @AuthenticationPrincipal KshUserDetails user) throws java.io.IOException {
        var handle = materialsService.download(classId, materialId, user.getId(), user.getRole());
        return org.springframework.http.ResponseEntity.ok().header("Cache-Control", "private, no-store")
                .header("X-Content-Type-Options", "nosniff").contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(presentationService.pdf(handle.storageKey(), handle.originalFilename()));
    }

    @GetMapping("/file-viewer/slides/content")
    @PreAuthorize("isAuthenticated()")
    public org.springframework.http.ResponseEntity<byte[]> slides(@RequestParam Long lessonId, @RequestParam Long attachmentId,
            @AuthenticationPrincipal KshUserDetails user) throws java.io.IOException {
        var handle = attachmentsService.download(lessonId, attachmentId, user.getId(), user.getRole());
        return org.springframework.http.ResponseEntity.ok().header("Cache-Control", "private, no-store")
                .header("X-Content-Type-Options", "nosniff").contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(presentationService.pdf(handle.storageKey(), handle.originalFilename()));
    }

    public FileViewerController(LessonAttachmentsService attachmentsService,
                                PublicViewTokenService tokenService) {
        this.attachmentsService = attachmentsService;
        this.tokenService = tokenService;
    }

    /**
     * Renders the PDF.js or docx-preview page. The template client-side
     * fetches {@code downloadUrl} (fetch() is not a navigation, so download
     * managers like IDM do not intercept it) and renders from the buffer.
     */
    @GetMapping("/file-viewer")
    @PreAuthorize("isAuthenticated()")
    public String view(@RequestParam String type,
                       @RequestParam Long lessonId,
                       @RequestParam Long attachmentId,
                       @RequestParam(required = false) String filename,
                       @AuthenticationPrincipal KshUserDetails user,
                       Model model) {
        try {
            // Reuse the download authz gate; discard the handle.
            attachmentsService.download(lessonId, attachmentId,
                    user.getId(), user.getRole());
        } catch (AccessDeniedException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        } catch (EntityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("downloadUrl",
                "/api/lessons/" + lessonId + "/attachments/" + attachmentId + "/download");
        model.addAttribute("filename",
                filename != null ? filename : "Tài liệu");

        if ("docx".equalsIgnoreCase(type)) {
            return "student/docx-viewer";
        }
        return "student/pdfjs-viewer";
    }

    /** Authenticated local Office preview; files are never exposed to a public viewer. */
    @GetMapping("/file-viewer/office")
    @PreAuthorize("isAuthenticated()")
    public String viewOffice(@RequestParam Long lessonId,
                             @RequestParam Long attachmentId,
                             @AuthenticationPrincipal KshUserDetails user, Model model) {
        try {
            // Reuse the download authz gate; discard the handle.
            attachmentsService.download(lessonId, attachmentId,
                    user.getId(), user.getRole());
        } catch (AccessDeniedException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        } catch (EntityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        var handle = attachmentsService.download(lessonId, attachmentId, user.getId(), user.getRole());
        if (handle.originalFilename().toLowerCase(java.util.Locale.ROOT).matches(".*\\.(pdf|pptx?)")) {
            model.addAttribute("downloadUrl", "/file-viewer/slides/content?lessonId=" + lessonId + "&attachmentId=" + attachmentId);
            model.addAttribute("originalDownloadUrl", "/api/lessons/" + lessonId + "/attachments/" + attachmentId + "/download");
            model.addAttribute("filename", handle.originalFilename());
            return "student/pdfjs-viewer";
        }
        String url = "/api/lessons/" + lessonId + "/attachments/" + attachmentId + "/download";
        var detail = new com.ksh.features.library.dto.LibraryDtos.LibraryAssetDetail(
                attachmentId, handle.originalFilename(), handle.originalFilename(), "DOCUMENT",
                handle.mimeType(), "Tài liệu", "document", handle.sizeBytes(), null, null, null, url, url, java.util.List.of());
        model.addAttribute("assetPreview", previewService.loadAuthorized(detail, handle.storageKey()));
        return "library/asset-preview";
    }
}
