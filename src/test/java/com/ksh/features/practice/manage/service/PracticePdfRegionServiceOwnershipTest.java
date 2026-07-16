package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.entities.PracticePdfRegionAnnotation;
import com.ksh.features.practice.repository.PracticePdfImportSessionRepository;
import com.ksh.features.practice.repository.PracticePdfRegionAnnotationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticePdfRegionServiceOwnershipTest {

    private final PracticePdfRegionAnnotationRepository annotationRepository =
            mock(PracticePdfRegionAnnotationRepository.class);
    private final PracticePdfImportSessionRepository sessionRepository =
            mock(PracticePdfImportSessionRepository.class);
    private final PracticePdfCropService cropService = mock(PracticePdfCropService.class);
    private final LecturerAssetService assetService = mock(LecturerAssetService.class);

    private PracticePdfRegionService service;

    @BeforeEach
    void setUp() {
        service = new PracticePdfRegionService(
                annotationRepository, sessionRepository, cropService, assetService);
    }

    @Test
    void annotationFromAnotherSessionCannotBeReadUpdatedOrDeleted() {
        PracticePdfImportSession session = new PracticePdfImportSession(
                7L, "exam.pdf", "stored/exam.pdf", 2, "ANNOTATING",
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        session.setId(100L);
        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(annotationRepository.findByIdAndSessionId(500L, 100L)).thenReturn(Optional.empty());

        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> service.getAnnotation(100L, 500L, 7L));
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> service.updateAnnotation(
                        100L, 500L, new PracticePdfRegionAnnotation(), 7L));
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> service.deleteAnnotation(100L, 500L, 7L));

        verify(annotationRepository, never()).save(any());
        verify(annotationRepository, never()).delete(any());
    }

    @Test
    void updatingCropCoordinatesRetiresStaleTemporaryAssetAndCreatesFreshCrop() throws Exception {
        PracticePdfImportSession session = new PracticePdfImportSession(
                7L, "exam.pdf", "stored/exam.pdf", 2, "ANNOTATING",
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        session.setId(100L);
        PracticePdfRegionAnnotation annotation = annotation(
                500L, 100L, 1, 0.1, 0.2, 0.3, 0.4);
        PracticePdfRegionAnnotation update = annotation(
                500L, 100L, 1, 0.15, 0.25, 0.35, 0.45);
        LecturerAsset stale = cropAsset(
                900L, 500L, 1, 0.1, 0.2, 0.3, 0.4, "TEMPORARY");
        LecturerAsset fresh = cropAsset(
                901L, 500L, 1, 0.15, 0.25, 0.35, 0.45, "TEMPORARY");

        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(annotationRepository.findByIdAndSessionId(500L, 100L))
                .thenReturn(Optional.of(annotation));
        when(annotationRepository.save(annotation)).thenReturn(annotation);
        when(assetService.getSessionAssets(100L, 7L)).thenReturn(List.of(stale));
        when(cropService.cropRegion(
                "stored/exam.pdf", 1, 0.15, 0.25, 0.35, 0.45,
                "WITH_PADDING", 16, 7L, 100L, 500L))
                .thenReturn(fresh);

        service.updateAnnotation(100L, 500L, update, 7L);

        verify(assetService).deleteAsset(900L, 7L);
        verify(cropService).cropRegion(
                "stored/exam.pdf", 1, 0.15, 0.25, 0.35, 0.45,
                "WITH_PADDING", 16, 7L, 100L, 500L);
    }

    @Test
    void nullableAiFlagsAreNormalizedBeforeAnnotationIsSaved() throws Exception {
        PracticePdfImportSession session = new PracticePdfImportSession(
                7L, "exam.pdf", "stored/exam.pdf", 2, "ANNOTATING",
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        session.setId(100L);
        PracticePdfRegionAnnotation annotation = annotation(
                500L, 100L, 1, 0.1, 0.2, 0.3, 0.4);
        PracticePdfRegionAnnotation update = annotation(
                500L, 100L, 1, 0.1, 0.2, 0.3, 0.4);
        update.setIncludeInAi(null);
        update.setIncludeTextInAi(null);
        update.setIncludeImageInAi(null);
        update.setSaveToAssetLibrary(null);
        LecturerAsset fresh = cropAsset(
                901L, 500L, 1, 0.1, 0.2, 0.3, 0.4, "TEMPORARY");

        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(annotationRepository.findByIdAndSessionId(500L, 100L))
                .thenReturn(Optional.of(annotation));
        when(annotationRepository.save(annotation)).thenReturn(annotation);
        when(assetService.getSessionAssets(100L, 7L)).thenReturn(List.of());
        when(cropService.cropRegion(
                "stored/exam.pdf", 1, 0.1, 0.2, 0.3, 0.4,
                "WITH_PADDING", 16, 7L, 100L, 500L))
                .thenReturn(fresh);

        PracticePdfRegionAnnotation saved = service.updateAnnotation(100L, 500L, update, 7L);

        assertTrue(saved.getIncludeInAi());
        assertTrue(saved.getIncludeTextInAi());
        assertTrue(saved.getIncludeImageInAi());
        assertFalse(saved.getSaveToAssetLibrary());
    }

    private static PracticePdfRegionAnnotation annotation(
            Long id,
            Long sessionId,
            int page,
            double x,
            double y,
            double width,
            double height) {
        PracticePdfRegionAnnotation annotation = new PracticePdfRegionAnnotation();
        annotation.setId(id);
        annotation.setSessionId(sessionId);
        annotation.setPageNumber(page);
        annotation.setRegionType("IMAGE_ASSET");
        annotation.setxRatio(x);
        annotation.setyRatio(y);
        annotation.setWidthRatio(width);
        annotation.setHeightRatio(height);
        annotation.setIncludeInAi(true);
        annotation.setIncludeTextInAi(false);
        annotation.setIncludeImageInAi(true);
        annotation.setSaveToAssetLibrary(false);
        return annotation;
    }

    private static LecturerAsset cropAsset(
            Long id,
            Long regionId,
            int page,
            double x,
            double y,
            double width,
            double height,
            String status) {
        LecturerAsset asset = new LecturerAsset();
        asset.setId(id);
        asset.setSourceRegionId(regionId);
        asset.setSourcePageNumber(page);
        asset.setCropX(x);
        asset.setCropY(y);
        asset.setCropWidth(width);
        asset.setCropHeight(height);
        asset.setStatus(status);
        asset.setUpdatedAt(LocalDateTime.now());
        return asset;
    }
}
