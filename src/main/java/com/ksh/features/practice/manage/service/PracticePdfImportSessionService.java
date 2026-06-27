package com.ksh.features.practice.manage.service;

import com.ksh.entities.PracticePdfImportSession;
import com.ksh.features.practice.pdf.PracticePdfStorageService;
import com.ksh.features.practice.repository.PracticeAiRequestAuditRepository;
import com.ksh.features.practice.repository.PracticePdfImportSessionRepository;
import com.ksh.features.practice.repository.PracticePdfPageExtractionRepository;
import com.ksh.features.practice.repository.PracticePdfRegionAnnotationRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PracticePdfImportSessionService {

    private static final Logger log = LoggerFactory.getLogger(PracticePdfImportSessionService.class);

    private final PracticePdfImportSessionRepository sessionRepository;
    private final PracticePdfRegionAnnotationRepository annotationRepository;
    private final PracticePdfPageExtractionRepository pageExtractionRepository;
    private final PracticeAiRequestAuditRepository aiRequestAuditRepository;
    private final PracticePdfStorageService storageService;
    private final LecturerAssetService assetService;

    public PracticePdfImportSessionService(PracticePdfImportSessionRepository sessionRepository,
                                           PracticePdfRegionAnnotationRepository annotationRepository,
                                           PracticePdfPageExtractionRepository pageExtractionRepository,
                                           PracticeAiRequestAuditRepository aiRequestAuditRepository,
                                           PracticePdfStorageService storageService,
                                           LecturerAssetService assetService) {
        this.sessionRepository = sessionRepository;
        this.annotationRepository = annotationRepository;
        this.pageExtractionRepository = pageExtractionRepository;
        this.aiRequestAuditRepository = aiRequestAuditRepository;
        this.storageService = storageService;
        this.assetService = assetService;
    }

    @Transactional
    public PracticePdfImportSession createSession(Long uploaderId, MultipartFile file, String examCategory, String title, Long linkedDraftId) throws IOException {
        PracticePdfStorageService.StoredPdf storedPdf = storageService.store(file, uploaderId);

        int totalPages = 1;
        try (PDDocument doc = Loader.loadPDF(storedPdf.absolutePath().toFile())) {
            totalPages = doc.getNumberOfPages();
        } catch (Exception e) {
            log.error("[PdfImportSessionService] Failed to read total pages for file={}", storedPdf.absolutePath().toString(), e);
        }

        String finalTitle = (title != null && !title.isBlank()) ? title : file.getOriginalFilename().replaceFirst("(?i)\\.pdf$", "");

        PracticePdfImportSession session = new PracticePdfImportSession(
                uploaderId,
                file.getOriginalFilename(),
                storedPdf.absolutePath().toString(),
                totalPages,
                "UPLOADED",
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(24)
        );
        session.setCreatedBy(uploaderId);
        session.setTitle(finalTitle);
        session.setExamCategory(examCategory != null ? examCategory : "TOPIK_II");
        session.setLinkedDraftId(linkedDraftId);
        session.setExtractionStrategy("HYBRID");

        return sessionRepository.save(session);
    }



    public PracticePdfImportSession getSession(Long id, Long userId) {
        PracticePdfImportSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Session không tồn tại."));
        if (!session.getUploaderId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền truy cập session này.");
        }
        return session;
    }

    @Transactional
    public PracticePdfImportSession updatePageRange(Long id, Integer startPage, Integer endPage, Long userId) {
        PracticePdfImportSession session = getSession(id, userId);
        if (startPage == null || startPage <= 0) startPage = 1;
        if (endPage == null || endPage > session.getTotalPages()) endPage = session.getTotalPages();
        if (startPage > endPage) {
            throw new IllegalArgumentException("Trang bắt đầu không thể lớn hơn trang kết thúc.");
        }
        session.setSelectedStartPage(startPage);
        session.setSelectedEndPage(endPage);
        session.setStatus("ANNOTATING");
        session.setUpdatedAt(LocalDateTime.now());
        return sessionRepository.save(session);
    }

    @Transactional
    public PracticePdfImportSession saveState(Long id, Integer currentPage, Integer startPage, Integer endPage, String strategy, Long userId) {
        PracticePdfImportSession session = getSession(id, userId);
        if (currentPage != null && currentPage > 0 && currentPage <= session.getTotalPages()) {
            session.setCurrentPage(currentPage);
        }
        if (startPage != null && endPage != null && startPage <= endPage) {
            session.setSelectedStartPage(startPage);
            session.setSelectedEndPage(endPage);
        }
        if (strategy != null) {
            session.setExtractionStrategy(strategy);
        }
        session.setUpdatedAt(LocalDateTime.now());
        return sessionRepository.save(session);
    }

    @Transactional
    public void updateStatus(Long id, String status) {
        PracticePdfImportSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Session không tồn tại."));
        session.setStatus(status);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    @Transactional
    public void updateDraftId(Long id, Long draftId) {
        PracticePdfImportSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Session không tồn tại."));
        session.setLinkedDraftId(draftId);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    @Transactional
    public void deleteSession(Long sessionId, Long userId) {
        PracticePdfImportSession session = getSession(sessionId, userId);
        
        // 1. Delete actual PDF file
        if (session.getStoredPdfPath() != null) {
            File file = new File(session.getStoredPdfPath());
            if (file.exists() && file.delete()) {
                log.info("[PdfImportSessionService] Deleted PDF file={}", session.getStoredPdfPath());
            }
        }

        // 2. Cleanup temporary and active assets
        assetService.cleanupTemporaryAssets(sessionId);
        
        // 3. Delete cascades in db (managed by hibernate or query)
        annotationRepository.deleteBySessionId(sessionId);
        pageExtractionRepository.deleteBySessionId(sessionId);
        aiRequestAuditRepository.deleteBySessionId(sessionId);
        sessionRepository.delete(session);
        log.info("[PdfImportSessionService] Deleted import session id={}", sessionId);
    }

    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public void cleanupExpiredSessions() {
        log.info("[PdfImportSessionService] Running cleanup of expired import sessions");
        List<PracticePdfImportSession> expired = sessionRepository.findByExpiresAtBefore(LocalDateTime.now());
        for (PracticePdfImportSession session : expired) {
            try {
                deleteSession(session.getId(), session.getUploaderId());
            } catch (Exception e) {
                log.error("[PdfImportSessionService] Failed to clean up expired session id={}", session.getId(), e);
            }
        }
    }
}
