package com.ksh.features.practice.manage.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PracticePdfCropServiceBoundsTest {

    @TempDir
    Path tempDir;

    @Test
    void invalidNormalizedBoxIsRejectedBeforePdfParsing() throws Exception {
        Path placeholder = Files.createFile(tempDir.resolve("placeholder.pdf"));
        LecturerAssetService assets = mock(LecturerAssetService.class);
        PracticePdfCropService service =
                new PracticePdfCropService(assets, limits());

        assertThatThrownBy(() -> service.cropRegion(
                placeholder.toString(),
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
    }

    @Test
    void oversizedPageIsRejectedBeforeRasterAllocation() throws Exception {
        Path pdf = tempDir.resolve("oversized-page.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(new PDRectangle(2_000, 2_000)));
            document.save(pdf.toFile());
        }
        LecturerAssetService assets = mock(LecturerAssetService.class);
        PracticePdfCropService service =
                new PracticePdfCropService(assets, limits());

        assertThatThrownBy(() -> service.cropRegion(
                pdf.toString(),
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
