package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.entities.PracticePdfRegionAnnotation;
import com.ksh.features.practice.repository.PracticePdfImportSessionRepository;
import com.ksh.features.practice.repository.PracticePdfRegionAnnotationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PracticePdfRegionService {

    private static final Logger log = LoggerFactory.getLogger(PracticePdfRegionService.class);

    private final PracticePdfRegionAnnotationRepository annotationRepository;
    private final PracticePdfImportSessionRepository sessionRepository;
    private final PracticePdfCropService cropService;
    private final LecturerAssetService assetService;

    public PracticePdfRegionService(PracticePdfRegionAnnotationRepository annotationRepository,
                                     PracticePdfImportSessionRepository sessionRepository,
                                     PracticePdfCropService cropService,
                                     LecturerAssetService assetService) {
        this.annotationRepository = annotationRepository;
        this.sessionRepository = sessionRepository;
        this.cropService = cropService;
        this.assetService = assetService;
    }

    public List<PracticePdfRegionAnnotation> getAnnotations(Long sessionId, Long userId) {
        PracticePdfImportSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Session không tồn tại."));
        if (!session.getUploaderId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền truy cập session này.");
        }
        return annotationRepository.findBySessionIdOrderByPageNumberAscDisplayOrderAsc(sessionId);
    }

    @Transactional
    public PracticePdfRegionAnnotation createAnnotation(Long sessionId, PracticePdfRegionAnnotation annotation, Long userId) {
        PracticePdfImportSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Session không tồn tại."));
        if (!session.getUploaderId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền chỉnh sửa session này.");
        }

        annotation.setSessionId(sessionId);
        annotation.setCreatedAt(LocalDateTime.now());
        annotation.setUpdatedAt(LocalDateTime.now());

        PracticePdfRegionAnnotation saved = annotationRepository.save(annotation);

        // Auto crop if image enabled
        triggerAutoCropIfNecessary(session, saved, userId);

        return saved;
    }

    @Transactional
    public PracticePdfRegionAnnotation updateAnnotation(Long sessionId, Long annotationId, PracticePdfRegionAnnotation update, Long userId) {
        PracticePdfImportSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Session không tồn tại."));
        if (!session.getUploaderId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền chỉnh sửa session này.");
        }

        PracticePdfRegionAnnotation annotation = annotationRepository.findById(annotationId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy annotation."));

        annotation.setRegionType(update.getRegionType());
        annotation.setxRatio(update.getxRatio());
        annotation.setyRatio(update.getyRatio());
        annotation.setWidthRatio(update.getWidthRatio());
        annotation.setHeightRatio(update.getHeightRatio());
        annotation.setDisplayOrder(update.getDisplayOrder());
        annotation.setSectionTempId(update.getSectionTempId());
        annotation.setGroupTempId(update.getGroupTempId());
        annotation.setExpectedQuestionType(update.getExpectedQuestionType());
        annotation.setExpectedQuestionFrom(update.getExpectedQuestionFrom());
        annotation.setExpectedQuestionTo(update.getExpectedQuestionTo());
        annotation.setTargetQuestionNo(update.getTargetQuestionNo());
        annotation.setOptionIndex(update.getOptionIndex());
        annotation.setAssetPlacement(update.getAssetPlacement());
        annotation.setIncludeInAi(update.getIncludeInAi());
        annotation.setIncludeTextInAi(update.getIncludeTextInAi());
        annotation.setIncludeImageInAi(update.getIncludeImageInAi());
        annotation.setSaveToAssetLibrary(update.getSaveToAssetLibrary());
        annotation.setLecturerNote(update.getLecturerNote());
        annotation.setUpdatedAt(LocalDateTime.now());

        PracticePdfRegionAnnotation saved = annotationRepository.save(annotation);

        // Auto crop if image enabled
        triggerAutoCropIfNecessary(session, saved, userId);

        return saved;
    }

    @Transactional
    public void deleteAnnotation(Long sessionId, Long annotationId, Long userId) {
        PracticePdfImportSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Session không tồn tại."));
        if (!session.getUploaderId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền chỉnh sửa session này.");
        }

        PracticePdfRegionAnnotation annotation = annotationRepository.findById(annotationId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy annotation."));

        // Delete associated asset if it is TEMPORARY
        List<LecturerAsset> sessionAssets = assetService.getSessionAssets(sessionId);
        sessionAssets.stream()
                .filter(a -> annotationId.equals(a.getSourceRegionId()) && "TEMPORARY".equalsIgnoreCase(a.getStatus()))
                .forEach(a -> {
                    try {
                        assetService.deleteAsset(a.getId(), userId);
                    } catch (Exception e) {
                        log.warn("[PdfRegionService] Failed to delete asset={}", a.getId(), e);
                    }
                });

        annotationRepository.delete(annotation);
    }

    private void triggerAutoCropIfNecessary(PracticePdfImportSession session, PracticePdfRegionAnnotation ann, Long userId) {
        boolean includeImage = ann.getIncludeImageInAi() != false;
        if (includeImage && !"IGNORE".equalsIgnoreCase(ann.getRegionType())) {
            try {
                // Check if crop already exists
                List<LecturerAsset> existing = assetService.getSessionAssets(session.getId());
                boolean hasAsset = existing.stream().anyMatch(a -> ann.getId().equals(a.getSourceRegionId()));

                if (!hasAsset) {
                    LecturerAsset asset = cropService.cropRegion(
                            session.getStoredPdfPath(),
                            ann.getPageNumber(),
                            ann.getxRatio(),
                            ann.getyRatio(),
                            ann.getWidthRatio(),
                            ann.getHeightRatio(),
                            "WITH_PADDING",
                            16,
                            userId,
                            session.getId(),
                            ann.getId()
                    );

                    // If marked for library save, promote immediately
                    if (Boolean.TRUE.equals(ann.getSaveToAssetLibrary())) {
                        assetService.promoteToActiveLibrary(asset.getId(), userId);
                    }
                } else if (Boolean.TRUE.equals(ann.getSaveToAssetLibrary())) {
                    // If it has crop but saveToAssetLibrary just turned true
                    existing.stream()
                            .filter(a -> ann.getId().equals(a.getSourceRegionId()) && "TEMPORARY".equalsIgnoreCase(a.getStatus()))
                            .findFirst()
                            .ifPresent(a -> assetService.promoteToActiveLibrary(a.getId(), userId));
                }
            } catch (Exception e) {
                log.error("[PdfRegionService] Failed to auto crop regionId={}", ann.getId(), e);
            }
        }
    }
}
