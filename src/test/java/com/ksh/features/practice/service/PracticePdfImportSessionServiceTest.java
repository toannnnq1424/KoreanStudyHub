package com.ksh.features.practice.service;

import com.ksh.entities.PracticePdfImportSession;
import com.ksh.features.practice.manage.service.PracticePdfImportSessionService;
import com.ksh.features.practice.pdf.PracticePdfStorageService;
import com.ksh.features.practice.repository.PracticePdfImportSessionRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Optional;

import com.ksh.features.practice.repository.PracticePdfRegionAnnotationRepository;
import com.ksh.features.practice.repository.PracticePdfPageExtractionRepository;
import com.ksh.features.practice.repository.PracticeAiRequestAuditRepository;
import com.ksh.features.practice.manage.service.LecturerAssetService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PracticePdfImportSessionServiceTest {

    private PracticePdfImportSessionRepository sessionRepository;
    private PracticePdfRegionAnnotationRepository annotationRepository;
    private PracticePdfPageExtractionRepository pageExtractionRepository;
    private PracticeAiRequestAuditRepository aiRequestAuditRepository;
    private PracticeDraftRepository draftRepository;
    private PracticePdfStorageService storageService;
    private LecturerAssetService assetService;
    private PracticePdfImportSessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(PracticePdfImportSessionRepository.class);
        annotationRepository = mock(PracticePdfRegionAnnotationRepository.class);
        pageExtractionRepository = mock(PracticePdfPageExtractionRepository.class);
        aiRequestAuditRepository = mock(PracticeAiRequestAuditRepository.class);
        draftRepository = mock(PracticeDraftRepository.class);
        storageService = mock(PracticePdfStorageService.class);
        assetService = mock(LecturerAssetService.class);
        
        sessionService = new PracticePdfImportSessionService(
                sessionRepository,
                annotationRepository,
                pageExtractionRepository,
                aiRequestAuditRepository,
                draftRepository,
                storageService,
                assetService
        );
    }

    @Test
    void testGetSession_Success() {
        PracticePdfImportSession session = new PracticePdfImportSession(
                1L, "test.pdf", "path/to/test.pdf", 10, "UPLOADED",
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now().plusHours(24)
        );
        session.setId(100L);

        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));

        PracticePdfImportSession result = sessionService.getSession(100L, 1L);
        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals(1L, result.getUploaderId());
    }

    @Test
    void testGetSession_AccessDenied() {
        PracticePdfImportSession session = new PracticePdfImportSession(
                1L, "test.pdf", "path/to/test.pdf", 10, "UPLOADED",
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now().plusHours(24)
        );
        session.setId(100L);

        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> {
            sessionService.getSession(100L, 999L); // different user
        });
    }

    @Test
    void testUpdatePageRange_Success() {
        PracticePdfImportSession session = new PracticePdfImportSession(
                1L, "test.pdf", "path/to/test.pdf", 10, "UPLOADED",
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now().plusHours(24)
        );
        session.setId(100L);

        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(PracticePdfImportSession.class))).thenAnswer(i -> i.getArgument(0));

        PracticePdfImportSession updated = sessionService.updatePageRange(100L, 2, 8, 1L);
        assertEquals(2, updated.getSelectedStartPage());
        assertEquals(8, updated.getSelectedEndPage());
        assertEquals("ANNOTATING", updated.getStatus());
    }

    @Test
    void testUpdatePageRange_InvalidRange() {
        PracticePdfImportSession session = new PracticePdfImportSession(
                1L, "test.pdf", "path/to/test.pdf", 10, "UPLOADED",
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now().plusHours(24)
        );
        session.setId(100L);

        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));

        assertThrows(IllegalArgumentException.class, () -> {
            sessionService.updatePageRange(100L, 8, 2, 1L); // start > end
        });
    }

    @Test
    void createSessionRejectsForeignLinkedDraftBeforeStoringFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(draftRepository.findByIdAndOwnerId(55L, 1L)).thenReturn(Optional.empty());

        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> sessionService.createSession(1L, file, "TOPIK_II", "Import", 55L));

        verifyNoInteractions(storageService);
        verify(sessionRepository, never()).save(any());
    }
}
