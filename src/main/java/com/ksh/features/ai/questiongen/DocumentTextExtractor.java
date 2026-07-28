package com.ksh.features.ai.questiongen;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts bounded text from uploaded PDF/DOCX material without changing POI's global
 * ZIP policy (which is also used by the independent Practice import pipeline).
 */
@Component
public class DocumentTextExtractor {

    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    static final int MAX_TEXT_CHARS = 30_000;
    static final int MAX_PDF_PAGES = 100;
    static final int MAX_PDF_PAGES_EXTRACTED = 50;
    static final int MAX_ZIP_ENTRIES = 2_000;
    static final long MAX_ZIP_ENTRY_BYTES = 10L * 1024 * 1024;
    static final long MAX_ZIP_TOTAL_BYTES = 25L * 1024 * 1024;

    private static final byte[] MAGIC_PDF = {'%', 'P', 'D', 'F'};
    private static final byte[] MAGIC_ZIP =
            {(byte) 0x50, (byte) 0x4B, (byte) 0x03, (byte) 0x04};

    private static final String MSG_EMPTY =
            "Vui lòng chọn file tài liệu hoặc dán nội dung văn bản";
    private static final String MSG_TOO_LARGE =
            "File vượt quá kích thước tối đa 5 MB";
    private static final String MSG_BAD_FORMAT =
            "Chỉ hỗ trợ file PDF hoặc DOCX";
    private static final String MSG_NO_TEXT =
            "Không trích được nội dung văn bản từ file";
    private static final String MSG_TOO_COMPLEX =
            "Tài liệu quá lớn hoặc quá phức tạp để xử lý an toàn";

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(MSG_EMPTY);
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException(MSG_TOO_LARGE);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException(MSG_NO_TEXT);
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException(MSG_TOO_LARGE);
        }

        String extracted;
        if (startsWith(bytes, MAGIC_PDF)) {
            extracted = extractPdf(bytes);
        } else if (startsWith(bytes, MAGIC_ZIP)) {
            preflightDocx(bytes);
            extracted = extractDocx(bytes);
        } else {
            throw new IllegalArgumentException(MSG_BAD_FORMAT);
        }
        return requireText(extracted, MSG_NO_TEXT);
    }

    public String normalizePastedText(String raw) {
        return requireText(raw, MSG_EMPTY);
    }

    private static String extractPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw new IllegalArgumentException(MSG_TOO_COMPLEX);
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(Math.min(document.getNumberOfPages(), MAX_PDF_PAGES_EXTRACTED));
            return stripper.getText(document);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new IllegalArgumentException(MSG_NO_TEXT);
        }
    }

    /**
     * Bounds decompression before POI opens the package. This is deliberately local and
     * does not call {@code ZipSecureFile.set*}, whose static settings affect Practice.
     */
    private static void preflightDocx(byte[] bytes) {
        int entryCount = 0;
        long totalBytes = 0;
        boolean hasDocumentXml = false;
        byte[] buffer = new byte[8_192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > MAX_ZIP_ENTRIES) {
                    throw new IllegalArgumentException(MSG_TOO_COMPLEX);
                }
                if ("word/document.xml".equals(entry.getName())) {
                    hasDocumentXml = true;
                }
                long entryBytes = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    entryBytes += read;
                    totalBytes += read;
                    if (entryBytes > MAX_ZIP_ENTRY_BYTES || totalBytes > MAX_ZIP_TOTAL_BYTES) {
                        throw new IllegalArgumentException(MSG_TOO_COMPLEX);
                    }
                }
                zip.closeEntry();
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new IllegalArgumentException(MSG_BAD_FORMAT);
        }
        if (!hasDocumentXml) {
            throw new IllegalArgumentException(MSG_BAD_FORMAT);
        }
    }

    private static String extractDocx(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (IOException | RuntimeException ex) {
            throw new IllegalArgumentException(MSG_BAD_FORMAT);
        }
    }

    private static String requireText(String raw, String emptyMessage) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        return value.length() > MAX_TEXT_CHARS ? value.substring(0, MAX_TEXT_CHARS) : value;
    }

    private static boolean startsWith(byte[] bytes, byte[] magic) {
        return bytes.length >= magic.length
                && Arrays.equals(Arrays.copyOf(bytes, magic.length), magic);
    }
}
