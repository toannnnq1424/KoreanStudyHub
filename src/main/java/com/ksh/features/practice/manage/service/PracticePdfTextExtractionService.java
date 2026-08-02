package com.ksh.features.practice.manage.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.springframework.stereotype.Service;

import java.awt.geom.Rectangle2D;
import java.io.IOException;

@Service
public class PracticePdfTextExtractionService {

    private final com.ksh.features.practice.pdf.PracticePdfStorageService storageService;

    public PracticePdfTextExtractionService(
            com.ksh.features.practice.pdf.PracticePdfStorageService storageService) {
        this.storageService = storageService;
    }

    public String extractPageRangeText(String pdfPath, int startPage, int endPage) throws IOException {
        try (PDDocument doc = Loader.loadPDF(storageService.readBytes(null, pdfPath))) {
            int total = doc.getNumberOfPages();
            int start = Math.max(1, Math.min(startPage, total));
            int end = Math.max(start, Math.min(endPage, total));

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(start);
            stripper.setEndPage(end);
            return stripper.getText(doc);
        }
    }

    public String extractRegionText(String pdfPath, int pageNumber, double xRatio, double yRatio,
                                    double wRatio, double hRatio) throws IOException {
        try (PDDocument doc = Loader.loadPDF(storageService.readBytes(null, pdfPath))) {
            if (pageNumber < 1 || pageNumber > doc.getNumberOfPages()) {
                throw new IllegalArgumentException("Số trang " + pageNumber + " vượt quá phạm vi PDF.");
            }

            PDPage page = doc.getPage(pageNumber - 1);
            PDRectangle mediaBox = page.getMediaBox();
            double widthPoints = mediaBox.getWidth();
            double heightPoints = mediaBox.getHeight();

            // Handle rotation if page is landscape
            int rotation = page.getRotation();
            if (rotation == 90 || rotation == 270) {
                double temp = widthPoints;
                widthPoints = heightPoints;
                heightPoints = temp;
            }

            double x = xRatio * widthPoints;
            double y = yRatio * heightPoints;
            double w = wRatio * widthPoints;
            double h = hRatio * heightPoints;

            Rectangle2D rect = new Rectangle2D.Double(x, y, w, h);

            PDFTextStripperByArea stripper = new PDFTextStripperByArea();
            stripper.addRegion("region", rect);
            stripper.extractRegions(page);

            String text = stripper.getTextForRegion("region");
            return text != null ? text.trim() : "";
        }
    }
}
