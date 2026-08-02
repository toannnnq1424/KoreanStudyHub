package com.ksh.features.practice.manage.service;

import com.ksh.entities.PracticePdfImportSession;
import com.ksh.features.practice.pdf.PracticePdfStorageService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PracticePdfCropServiceBoundsTest {

    @Test
    void invalidNormalizedBoxIsRejectedBeforePdfParsing() throws Exception {
        LecturerAssetService assets = mock(LecturerAssetService.class);
        PracticePdfStorageService storage = mock(PracticePdfStorageService.class);
        PracticePdfCropService service =
                new PracticePdfCropService(assets, limits(), storage);

        assertThatThrownBy(() -> service.cropRegion(
                session("placeholder.pdf"),
                1,
                0.9d,
                0.1d,
                0.2d,
                0.2d,
                "WITH_PADDING",
                16,
                7L,
                9L,
                11L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tọa độ crop PDF không hợp lệ.");

        verifyNoInteractions(assets);
        verifyNoInteractions(storage);
    }

    @Test
    void oversizedPageIsRejectedBeforeRasterAllocation() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(new PDRectangle(2_000, 2_000)));
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            document.save(output);
            pdf = output.toByteArray();
        }
        LecturerAssetService assets = mock(LecturerAssetService.class);
        PracticePdfStorageService storage = mock(PracticePdfStorageService.class);
        PracticePdfImportSession session = session("oversized-page.pdf");
        when(storage.readBytes(null, "oversized-page.pdf")).thenReturn(pdf);
        PracticePdfCropService service =
                new PracticePdfCropService(assets, limits(), storage);

        assertThatThrownBy(() -> service.cropRegion(
                session,
                1,
                0.1d,
                0.1d,
                0.2d,
                0.2d,
                "WITH_PADDING",
                16,
                7L,
                9L,
                11L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Trang PDF vượt ngân sách render an toàn.");

        verifyNoInteractions(assets);
    }

    private static PracticePdfImportSession session(String path) {
        return new PracticePdfImportSession(
                7L, "exam.pdf", path, 1, "UPLOADED",
                LocalDateTime.now(), LocalDateTime.now(),
                LocalDateTime.now().plusHours(1));
    }

    private static PracticePdfAiLimits limits() {
        return new PracticePdfAiLimits(
                1,
                1,
                10_000,
                65_536L,
                65_536L,
                1_000_000L,
                Duration.ofMinutes(1));
    }
}
