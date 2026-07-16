package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

@Service
public class PracticePdfCropService {

    private static final Logger log = LoggerFactory.getLogger(PracticePdfCropService.class);
    private static final float DRAFT_RENDER_DPI = 180f; // Fine resolution for AI character recognition

    private final LecturerAssetService assetService;

    public PracticePdfCropService(LecturerAssetService assetService) {
        this.assetService = assetService;
    }

    public LecturerAsset cropRegion(String pdfPath, int pageNumber, double xRatio, double yRatio,
                                    double wRatio, double hRatio, String cropMode, Integer paddingPx,
                                    Long ownerId, Long sessionId, Long regionId) throws IOException {
        File file = new File(pdfPath);
        if (!file.exists()) {
            throw new java.io.FileNotFoundException("File PDF không tồn tại.");
        }

        try (PDDocument doc = Loader.loadPDF(file)) {
            if (pageNumber < 1 || pageNumber > doc.getNumberOfPages()) {
                throw new IllegalArgumentException("Số trang " + pageNumber + " vượt quá phạm vi PDF.");
            }

            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage pageImage = renderer.renderImageWithDPI(pageNumber - 1, DRAFT_RENDER_DPI, ImageType.RGB);

            int imgWidth = pageImage.getWidth();
            int imgHeight = pageImage.getHeight();

            int x = (int) Math.round(xRatio * imgWidth);
            int y = (int) Math.round(yRatio * imgHeight);
            int w = (int) Math.round(wRatio * imgWidth);
            int h = (int) Math.round(hRatio * imgHeight);

            // Apply crop modes
            if ("FULL_WIDTH".equalsIgnoreCase(cropMode)) {
                x = 0;
                w = imgWidth;
            } else if ("INCLUDE_PADDING".equalsIgnoreCase(cropMode) || "WITH_PADDING".equalsIgnoreCase(cropMode) || paddingPx != null) {
                int pad = paddingPx != null ? paddingPx : 16;
                x = Math.max(0, x - pad);
                y = Math.max(0, y - pad);
                w = Math.min(imgWidth - x, w + (pad * 2));
                h = Math.min(imgHeight - y, h + (pad * 2));
            }

            // Boundary validation
            x = Math.max(0, Math.min(x, imgWidth - 1));
            y = Math.max(0, Math.min(y, imgHeight - 1));
            w = Math.max(1, Math.min(w, imgWidth - x));
            h = Math.max(1, Math.min(h, imgHeight - y));

            double areaRatio = (double)(w * h) / (double)(imgWidth * imgHeight);
            if (areaRatio > 0.85) {
                log.warn("[PdfCropService] Region covers {}% of the page.", Math.round(areaRatio * 100));
            }

            BufferedImage cropped = pageImage.getSubimage(x, y, w, h);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(cropped, "png", baos);
            byte[] bytes = baos.toByteArray();

            // Save via LecturerAssetService
            String originalFilename = "crop_p" + pageNumber + "_r" + regionId + ".png";
            try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                return assetService.createTemporaryAsset(
                        ownerId,
                        sessionId,
                        regionId,
                        bais,
                        originalFilename,
                        "image/png",
                        w,
                        h,
                        (long) bytes.length,
                        pageNumber,
                        xRatio,
                        yRatio,
                        wRatio,
                        hRatio,
                        null
                );
            }
        }
    }
}
