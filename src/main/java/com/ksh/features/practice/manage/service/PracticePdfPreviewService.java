package com.ksh.features.practice.manage.service;

import com.ksh.entities.PracticePdfImportSession;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
public class PracticePdfPreviewService {

    private final PracticePdfImportSessionService sessionService;
    private final com.ksh.features.practice.pdf.PracticePdfStorageService storageService;

    public PracticePdfPreviewService(
            PracticePdfImportSessionService sessionService,
            com.ksh.features.practice.pdf.PracticePdfStorageService storageService) {
        this.sessionService = sessionService;
        this.storageService = storageService;
    }

    public InputStream getPdfStream(Long sessionId, Long userId) throws IOException {
        PracticePdfImportSession session = sessionService.getSession(sessionId, userId);
        String pdfPath = session.getStoredPdfPath();
        if (pdfPath == null) {
            throw new IllegalArgumentException("Đường dẫn file PDF bị thiếu.");
        }
        return storageService.open(session.getStorageProfileCode(), pdfPath);
    }
}
