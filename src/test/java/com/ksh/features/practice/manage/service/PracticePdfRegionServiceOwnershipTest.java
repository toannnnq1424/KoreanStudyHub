package com.ksh.features.practice.manage.service;

import com.ksh.entities.PracticePdfImportSession;
import com.ksh.entities.PracticePdfRegionAnnotation;
import com.ksh.features.practice.repository.PracticePdfImportSessionRepository;
import com.ksh.features.practice.repository.PracticePdfRegionAnnotationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
}
