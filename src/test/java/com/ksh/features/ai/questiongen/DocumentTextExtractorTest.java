package com.ksh.features.ai.questiongen;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor();

    @Test
    void normalizes_and_caps_pasted_text() {
        String raw = "  " + "x".repeat(DocumentTextExtractor.MAX_TEXT_CHARS + 20) + "  ";
        assertThat(extractor.normalizePastedText(raw))
                .hasSize(DocumentTextExtractor.MAX_TEXT_CHARS);
    }

    @Test
    void rejects_blank_pasted_text() {
        assertThatThrownBy(() -> extractor.normalizePastedText(" \n "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_oversized_upload_before_reading_it() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(DocumentTextExtractor.MAX_FILE_BYTES + 1);

        assertThatThrownBy(() -> extractor.extract(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5 MB");
    }

    @Test
    void extracts_pdf_text_by_magic_bytes() throws Exception {
        byte[] bytes;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.showText("Korean lesson");
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            bytes = out.toByteArray();
        }

        MockMultipartFile file =
                new MockMultipartFile("file", "renamed.bin", "application/octet-stream", bytes);
        assertThat(extractor.extract(file)).contains("Korean lesson");
    }

    @Test
    void rejects_pdf_with_excessive_page_count() throws Exception {
        byte[] bytes;
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i <= DocumentTextExtractor.MAX_PDF_PAGES; i++) {
                document.addPage(new PDPage());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            bytes = out.toByteArray();
        }

        MockMultipartFile file =
                new MockMultipartFile("file", "many.pdf", "application/pdf", bytes);
        assertThatThrownBy(() -> extractor.extract(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("phức tạp");
    }

    @Test
    void extracts_docx_text_after_local_zip_preflight() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("Bài học tiếng Hàn");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            bytes = out.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                bytes);
        assertThat(extractor.extract(file)).contains("Bài học tiếng Hàn");
    }

    @Test
    void rejects_docx_zip_bomb_before_poi_parses_it() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(new byte[(int) DocumentTextExtractor.MAX_ZIP_ENTRY_BYTES + 1]);
            zip.closeEntry();
        }
        MockMultipartFile file =
                new MockMultipartFile("file", "bomb.docx", "application/zip", out.toByteArray());

        assertThatThrownBy(() -> extractor.extract(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("phức tạp");
    }

    @Test
    void rejects_non_pdf_non_docx_magic() {
        MockMultipartFile file =
                new MockMultipartFile("file", "notes.pdf", "application/pdf", "not pdf".getBytes());
        assertThatThrownBy(() -> extractor.extract(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF");
    }
}
