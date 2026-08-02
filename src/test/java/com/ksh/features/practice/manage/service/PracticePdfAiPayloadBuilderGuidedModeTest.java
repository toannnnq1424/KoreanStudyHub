package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.entities.PracticePdfPageExtraction;
import com.ksh.entities.PracticePdfRegionAnnotation;
import com.ksh.features.practice.manage.validator.ImportAiPayloadValidator;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticePdfImportGroupDraftRepository;
import com.ksh.features.practice.repository.PracticePdfImportSectionDraftRepository;
import com.ksh.features.practice.repository.PracticePdfRegionAnnotationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticePdfAiPayloadBuilderGuidedModeTest {

    @Test
    void fullSelectedPagesCreatesTraceableSyntheticRegionsWithoutManualCrops() {
        PracticePdfRegionAnnotationRepository annotations = mock(PracticePdfRegionAnnotationRepository.class);
        PracticePdfImportSectionDraftRepository sections = mock(PracticePdfImportSectionDraftRepository.class);
        PracticePdfImportGroupDraftRepository groups = mock(PracticePdfImportGroupDraftRepository.class);
        PracticePdfPageExtractionService extraction = mock(PracticePdfPageExtractionService.class);
        when(annotations.findBySessionIdOrderByPageNumberAscDisplayOrderAsc(9L)).thenReturn(List.of());
        when(sections.findBySessionIdOrderByDisplayOrderAsc(9L)).thenReturn(List.of());
        when(groups.findBySessionIdOrderByDisplayOrderAsc(9L)).thenReturn(List.of());
        when(extraction.extractOrGetPageText(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(2)))
                .thenReturn(page(9L, 2, "Trang hai co cau hoi."));
        when(extraction.extractOrGetPageText(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(3)))
                .thenReturn(page(9L, 3, "Trang ba co dap an."));

        PracticePdfAiPayloadBuilder builder = new PracticePdfAiPayloadBuilder(
                annotations,
                sections,
                groups,
                extraction,
                mock(PracticePdfCropService.class),
                mock(LecturerAssetRepository.class),
                mock(AssetStorageService.class),
                new ImportAiPayloadValidator()
        );
        PracticePdfImportSession session = session();

        PracticePdfAiPayloadBuilder.PayloadInfo payload = builder.buildPayload(session);

        assertEquals(List.of("page-2", "page-3"), payload.requestDto().getRegions().stream()
                .map(region -> region.getRegionId()).toList());
        assertTrue(payload.requestDto().getPageContexts().stream()
                .allMatch(context -> !Boolean.TRUE.equals(context.getAllowEntityCreation())
                        && context.getRawText().isEmpty()));
        assertTrue(payload.requestDto().getRegions().get(0).getOcrText().contains("Trang hai"));
        assertTrue(payload.basePageRangeText().isEmpty());
        assertEquals(2, payload.statsSummary().get("activeRegionsCount"));
        assertEquals(2, payload.requestDto().getDocument().getTargetTestNo());
        assertEquals("R2", payload.requestDto().getDocument().getTargetLessonCode());
        assertEquals("R2", payload.requestDto().getSections().get(0).getLessonCode());
        assertFalse(payload.validationErrors().stream()
                .anyMatch(error -> "ERROR".equals(error.severity())));
    }

    @Test
    void selectedPageBudgetIsRejectedBeforeExtractionOrCropWork() {
        PracticePdfPageExtractionService extraction =
                mock(PracticePdfPageExtractionService.class);
        PracticePdfCropService crop = mock(PracticePdfCropService.class);
        PracticePdfAiPayloadBuilder builder = new PracticePdfAiPayloadBuilder(
                mock(PracticePdfRegionAnnotationRepository.class),
                mock(PracticePdfImportSectionDraftRepository.class),
                mock(PracticePdfImportGroupDraftRepository.class),
                extraction,
                crop,
                mock(LecturerAssetRepository.class),
                mock(AssetStorageService.class),
                new ImportAiPayloadValidator(),
                new PracticePdfAiLimits(
                        1,
                        100,
                        1_000_000,
                        5_242_880L,
                        20_971_520L,
                        40_000_000L,
                        Duration.ofMinutes(2)));

        assertThatThrownBy(() -> builder.buildPayload(session()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Phạm vi trang PDF vượt ngân sách xử lý an toàn.");
        org.mockito.Mockito.verifyNoInteractions(extraction, crop);
    }

    @Test
    void imageBudgetUsesLoadedBytesInsteadOfTrustingStoredMetadata() throws Exception {
        PracticePdfRegionAnnotationRepository annotations =
                mock(PracticePdfRegionAnnotationRepository.class);
        PracticePdfImportSectionDraftRepository sections =
                mock(PracticePdfImportSectionDraftRepository.class);
        PracticePdfImportGroupDraftRepository groups =
                mock(PracticePdfImportGroupDraftRepository.class);
        PracticePdfPageExtractionService extraction =
                mock(PracticePdfPageExtractionService.class);
        PracticePdfCropService crop = mock(PracticePdfCropService.class);
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        AssetStorageService storage = mock(AssetStorageService.class);
        PracticePdfRegionAnnotation region = imageRegion();
        LecturerAsset misleadingMetadata = croppedAsset();
        PracticePdfImportSession session = singlePageRegionSession();

        when(annotations.findBySessionIdOrderByPageNumberAscDisplayOrderAsc(9L))
                .thenReturn(List.of(region));
        when(sections.findBySessionIdOrderByDisplayOrderAsc(9L))
                .thenReturn(List.of());
        when(groups.findBySessionIdOrderByDisplayOrderAsc(9L))
                .thenReturn(List.of());
        when(extraction.extractOrGetPageText(session, 1))
                .thenReturn(page(9L, 1, ""));
        when(assets.findBySourceImportSessionId(9L))
                .thenReturn(List.of());
        when(crop.cropRegion(
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
                .thenReturn(misleadingMetadata);
        when(storage.load("phase13h/crop.png"))
                .thenReturn(new ByteArrayResource(new byte[65_537]));

        PracticePdfAiPayloadBuilder builder = new PracticePdfAiPayloadBuilder(
                annotations,
                sections,
                groups,
                extraction,
                crop,
                assets,
                storage,
                new ImportAiPayloadValidator(),
                new PracticePdfAiLimits(
                        1,
                        1,
                        10_000,
                        65_536L,
                        65_536L,
                        1_000_000L,
                        Duration.ofMinutes(1)));

        assertThatThrownBy(() -> builder.buildPayload(session))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ảnh crop vượt ngân sách kích thước an toàn.");
        verify(storage).load("phase13h/crop.png");
    }

    private static PracticePdfPageExtraction page(Long sessionId, int page, String text) {
        return new PracticePdfPageExtraction(
                sessionId, page, text, text, text.length(), "COMPLETED", LocalDateTime.now());
    }

    private static PracticePdfImportSession session() {
        PracticePdfImportSession session = new PracticePdfImportSession(
                7L, "custom.pdf", "/tmp/custom.pdf", 3, "UPLOADED",
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        session.setId(9L);
        session.setSelectedStartPage(2);
        session.setSelectedEndPage(3);
        session.setTargetTestNo(2);
        session.setTargetSkill("READING");
        session.setTargetLessonCode("R2");
        session.setExtractionStrategy("FULL_SELECTED_PAGES");
        return session;
    }

    private static PracticePdfImportSession singlePageRegionSession() {
        PracticePdfImportSession session = new PracticePdfImportSession(
                7L, "custom.pdf", "/tmp/custom.pdf", 1, "UPLOADED",
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        session.setId(9L);
        session.setSelectedStartPage(1);
        session.setSelectedEndPage(1);
        session.setExtractionStrategy("REGION_ONLY");
        return session;
    }

    private static PracticePdfRegionAnnotation imageRegion() {
        PracticePdfRegionAnnotation region = new PracticePdfRegionAnnotation();
        region.setId(11L);
        region.setSessionId(9L);
        region.setPageNumber(1);
        region.setDisplayOrder(1);
        region.setRegionType("IMAGE_ASSET");
        region.setxRatio(0.1d);
        region.setyRatio(0.1d);
        region.setWidthRatio(0.2d);
        region.setHeightRatio(0.2d);
        region.setIncludeInAi(true);
        region.setIncludeTextInAi(false);
        region.setIncludeImageInAi(true);
        return region;
    }

    private static LecturerAsset croppedAsset() {
        LecturerAsset asset = new LecturerAsset();
        asset.setId(21L);
        asset.setFileSize(1L);
        asset.setStorageKey("phase13h/crop.png");
        asset.setMimeType("image/png");
        return asset;
    }
}
