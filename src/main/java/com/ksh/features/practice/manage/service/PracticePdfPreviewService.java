package com.ksh.features.practice.manage.service;

import com.ksh.entities.PracticePdfImportSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
public class PracticePdfPreviewService {

    private static final Logger log = LoggerFactory.getLogger(PracticePdfPreviewService.class);

    private final PracticePdfImportSessionService sessionService;

    public PracticePdfPreviewService(PracticePdfImportSessionService sessionService) {
        this.sessionService = sessionService;
    }

    public InputStream getPdfStream(Long sessionId, Long userId) throws IOException {
        PracticePdfImportSession session = sessionService.getSession(sessionId, userId);
        String pdfPath = session.getStoredPdfPath();
        if (pdfPath == null) {
            throw new IllegalArgumentException("Đường dẫn file PDF bị thiếu.");
        }
        File file = new File(pdfPath);
        if (!file.exists()) {
            throw new java.io.FileNotFoundException("File PDF không còn tồn tại trên server.");
        }
        return new FileInputStream(file);
    }
}
