package com.ksh.features.practice.manage.service;

import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticePdfAiPayloadBuilderRequestLocalTest {

    private static final TargetRoute TARGET =
            new TargetRoute(91L, 1, "READING", "R1");

    @Test
    void extractsOnlyTheBoundedPageIntoAnImmutableRequestSnapshot() throws Exception {
        PracticePdfAiPayloadBuilder builder =
                new PracticePdfAiPayloadBuilder(new PracticePdfAiLimits(2, 100_000));
        MockMultipartFile file = new MockMultipartFile(
                "file", "folder/private.pdf", MediaType.APPLICATION_PDF_VALUE,
                pdf("PAGE ONE", "PAGE TWO"));

        PracticePdfAuthoringRequest request = builder.buildBasicPdf(
                file, 2, 2, SourceOperation.EXTRACT,
                "Giữ nguyên đáp án", TARGET);

        assertThat(request.sourceType())
                .isEqualTo(PracticePdfAuthoringRequest.SourceType.PDF);
        assertThat(request.sourceName()).isEqualTo("private.pdf");
        assertThat(request.sourceDigest()).matches("sha256:[0-9a-f]{64}");
        assertThat(request.target()).isEqualTo(TARGET);
        assertThat(request.evidence()).hasSize(1);
        assertThat(request.evidence().get(0).pageNumber()).isEqualTo(2);
        assertThat(request.evidence().get(0).untrustedText()).contains("PAGE TWO");
        assertThat(request.evidence().get(0).untrustedText()).doesNotContain("PAGE ONE");
        assertThat(request.sourceContext()).containsEntry("mode", "BASIC_PDF");
        assertThat(request.sourceContext()).containsEntry("totalPages", 2);
        assertThrows(UnsupportedOperationException.class,
                () -> request.sourceContext().put("mutated", true));
        assertThrows(UnsupportedOperationException.class,
                () -> request.evidence().add(request.evidence().get(0)));
    }

    @Test
    void rejectsOversizedPdfBeforeReadingItsStream() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(PracticePdfAiPayloadBuilder.MAX_PDF_BYTES + 1);
        when(file.getContentType()).thenReturn(MediaType.APPLICATION_PDF_VALUE);
        when(file.getOriginalFilename()).thenReturn("large.pdf");
        PracticePdfAiPayloadBuilder builder =
                new PracticePdfAiPayloadBuilder(new PracticePdfAiLimits(50, 100_000));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> builder.buildBasicPdf(
                        file, 1, null, SourceOperation.EXTRACT, "", TARGET));

        assertThat(failure.getMessage()).contains("20 MiB");
        verify(file, never()).getInputStream();
    }

    @Test
    void requiresMimeExtensionAndPdfHeader() {
        PracticePdfAiPayloadBuilder builder =
                new PracticePdfAiPayloadBuilder(new PracticePdfAiLimits(50, 100_000));
        MockMultipartFile wrongType = new MockMultipartFile(
                "file", "source.pdf", MediaType.TEXT_PLAIN_VALUE,
                "%PDF-fake".getBytes(StandardCharsets.US_ASCII));
        MockMultipartFile wrongHeader = new MockMultipartFile(
                "file", "source.pdf", MediaType.APPLICATION_PDF_VALUE,
                "not-a-pdf".getBytes(StandardCharsets.US_ASCII));

        assertThat(assertThrows(IllegalArgumentException.class,
                () -> builder.buildBasicPdf(
                        wrongType, 1, null, SourceOperation.EXTRACT, "", TARGET))
                .getMessage()).contains("type");
        assertThat(assertThrows(IllegalArgumentException.class,
                () -> builder.buildBasicPdf(
                        wrongHeader, 1, null, SourceOperation.EXTRACT, "", TARGET))
                .getMessage()).contains("PDF header");
    }

    @Test
    void rejectsOutOfBoundsAndOverBudgetPageRanges() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "private.pdf", MediaType.APPLICATION_PDF_VALUE,
                pdf("PAGE ONE", "PAGE TWO"));
        PracticePdfAiPayloadBuilder onePageBuilder =
                new PracticePdfAiPayloadBuilder(new PracticePdfAiLimits(1, 100_000));

        assertThat(assertThrows(IllegalArgumentException.class,
                () -> onePageBuilder.buildBasicPdf(
                        file, 1, 2, SourceOperation.EXTRACT, "", TARGET))
                .getMessage()).contains("Phạm vi trang");
        assertThat(assertThrows(IllegalArgumentException.class,
                () -> onePageBuilder.buildBasicPdf(
                        file, 3, 3, SourceOperation.EXTRACT, "", TARGET))
                .getMessage()).contains("Phạm vi trang");
    }

    @Test
    void textSourceCarriesDigestAndNoStorageIdentity() {
        PracticePdfAiPayloadBuilder builder =
                new PracticePdfAiPayloadBuilder(new PracticePdfAiLimits(50, 100_000));

        PracticePdfAuthoringRequest request = builder.buildBasicText(
                "  nguồn text  ", SourceOperation.GENERATE, "Tạo hai câu", TARGET);

        assertThat(request.sourceDigest()).matches("sha256:[0-9a-f]{64}");
        assertThat(request.evidence()).extracting(
                        PracticePdfAuthoringRequest.SourceEvidence::untrustedText)
                .containsExactly("nguồn text");
        assertThat(request.sourceContext().toString())
                .doesNotContain("session", "storage", "objectKey");
    }

    private static byte[] pdf(String... pages) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA);
            for (String value : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content =
                             new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, 12);
                    content.newLineAtOffset(72, 720);
                    content.showText(value);
                    content.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
