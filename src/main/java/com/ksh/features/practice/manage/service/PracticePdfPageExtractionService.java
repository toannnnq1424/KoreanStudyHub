package com.ksh.features.practice.manage.service;

import com.ksh.entities.PracticePdfImportSession;
import com.ksh.entities.PracticePdfPageExtraction;
import com.ksh.features.practice.repository.PracticePdfPageExtractionRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PracticePdfPageExtractionService {

    private static final Logger log = LoggerFactory.getLogger(PracticePdfPageExtractionService.class);

    private final PracticePdfPageExtractionRepository pageExtractionRepository;

    public PracticePdfPageExtractionService(PracticePdfPageExtractionRepository pageExtractionRepository) {
        this.pageExtractionRepository = pageExtractionRepository;
    }

    @Transactional
    public PracticePdfPageExtraction extractOrGetPageText(PracticePdfImportSession session, int pageNum) {
        Optional<PracticePdfPageExtraction> existing = pageExtractionRepository
                .findBySessionIdAndPageNumber(session.getId(), pageNum);
        
        if (existing.isPresent() && "COMPLETED".equals(existing.get().getExtractionStatus())) {
            return existing.get();
        }

        // Perform text extraction via PDFBox
        File file = new File(session.getStoredPdfPath());
        if (!file.exists()) {
            throw new IllegalArgumentException("Không tìm thấy tệp PDF để bóc tách chữ.");
        }

        try (PDDocument doc = Loader.loadPDF(file)) {
            if (pageNum < 1 || pageNum > doc.getNumberOfPages()) {
                throw new IllegalArgumentException("Số trang vượt quá phạm vi PDF.");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageNum);
            stripper.setEndPage(pageNum);
            stripper.setSortByPosition(true);
            
            String text = stripper.getText(doc);
            if (text == null) text = "";
            text = text.trim();

            PracticePdfPageExtraction extraction = existing.orElseGet(() -> {
                PracticePdfPageExtraction ext = new PracticePdfPageExtraction();
                ext.setSessionId(session.getId());
                ext.setPageNumber(pageNum);
                ext.setCreatedAt(LocalDateTime.now());
                return ext;
            });

            extraction.setRawText(text);
            extraction.setNormalizedText(normalizeText(text));
            extraction.setRawCharCount(text.length());
            extraction.setExtractionStatus("COMPLETED");

            return pageExtractionRepository.save(extraction);
        } catch (IOException e) {
            log.error("[PageExtraction] Failed to extract pageNum={} for sessionId={}", pageNum, session.getId(), e);
            
            PracticePdfPageExtraction failedExt = existing.orElseGet(() -> {
                PracticePdfPageExtraction ext = new PracticePdfPageExtraction();
                ext.setSessionId(session.getId());
                ext.setPageNumber(pageNum);
                ext.setCreatedAt(LocalDateTime.now());
                return ext;
            });
            failedExt.setExtractionStatus("FAILED");
            return pageExtractionRepository.save(failedExt);
        }
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        // Basic normalization: unify whitespaces, strip header/footers placeholder indicators
        return text.replaceAll("\\r?\\n", "\n")
                .replaceAll("[\\t ]+", " ")
                .trim();
    }
}
