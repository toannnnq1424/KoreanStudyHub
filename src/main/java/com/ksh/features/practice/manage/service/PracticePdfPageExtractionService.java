package com.ksh.features.practice.manage.service;

import com.ksh.entities.PracticePdfImportSession;
import com.ksh.entities.PracticePdfPageExtraction;
import com.ksh.features.practice.repository.PracticePdfPageExtractionRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.awt.geom.Rectangle2D;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PracticePdfPageExtractionService {

    private static final Logger log = LoggerFactory.getLogger(PracticePdfPageExtractionService.class);

    private final PracticePdfPageExtractionRepository pageExtractionRepository;
    private final com.ksh.features.practice.pdf.PracticePdfStorageService storageService;

    public PracticePdfPageExtractionService(
            PracticePdfPageExtractionRepository pageExtractionRepository,
            com.ksh.features.practice.pdf.PracticePdfStorageService storageService) {
        this.pageExtractionRepository = pageExtractionRepository;
        this.storageService = storageService;
    }

    @Transactional
    public PracticePdfPageExtraction extractOrGetPageText(PracticePdfImportSession session, int pageNum) {
        Optional<PracticePdfPageExtraction> existing = pageExtractionRepository
                .findBySessionIdAndPageNumber(session.getId(), pageNum);
        
        if (existing.isPresent() && "COMPLETED".equals(existing.get().getExtractionStatus())) {
            return existing.get();
        }

        try (PDDocument doc = Loader.loadPDF(storageService.readBytes(
                session.getStorageProfileCode(), session.getStoredPdfPath()))) {
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

    public String extractRegionText(
            PracticePdfImportSession session,
            int pageNumber,
            double xRatio,
            double yRatio,
            double widthRatio,
            double heightRatio) throws IOException {
        if (session == null) {
            throw new IllegalArgumentException("STORAGE_IDENTITY_INVALID");
        }
        byte[] pdf = storageService.readBytes(
                session.getStorageProfileCode(), session.getStoredPdfPath());
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            if (pageNumber < 1 || pageNumber > doc.getNumberOfPages()) {
                throw new IllegalArgumentException(
                        "Số trang " + pageNumber + " vượt quá phạm vi PDF.");
            }
            PDPage page = doc.getPage(pageNumber - 1);
            PDRectangle mediaBox = page.getMediaBox();
            double pageWidth = mediaBox.getWidth();
            double pageHeight = mediaBox.getHeight();
            if (page.getRotation() == 90 || page.getRotation() == 270) {
                double rotated = pageWidth;
                pageWidth = pageHeight;
                pageHeight = rotated;
            }
            Rectangle2D region = new Rectangle2D.Double(
                    xRatio * pageWidth,
                    yRatio * pageHeight,
                    widthRatio * pageWidth,
                    heightRatio * pageHeight);
            PDFTextStripperByArea stripper = new PDFTextStripperByArea();
            stripper.addRegion("region", region);
            stripper.extractRegions(page);
            String text = stripper.getTextForRegion("region");
            return text == null ? "" : text.trim();
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
