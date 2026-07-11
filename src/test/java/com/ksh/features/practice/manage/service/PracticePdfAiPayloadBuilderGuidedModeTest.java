package com.ksh.features.practice.manage.service;

import com.ksh.entities.PracticePdfImportSession;
import com.ksh.entities.PracticePdfPageExtraction;
import com.ksh.features.practice.manage.validator.ImportAiPayloadValidator;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticePdfImportGroupDraftRepository;
import com.ksh.features.practice.repository.PracticePdfImportSectionDraftRepository;
import com.ksh.features.practice.repository.PracticePdfRegionAnnotationRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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
        assertFalse(payload.validationErrors().stream()
                .anyMatch(error -> "ERROR".equals(error.severity())));
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
        session.setExamCategory("CUSTOM_FLEXIBLE");
        session.setExtractionStrategy("FULL_SELECTED_PAGES");
        return session;
    }
}
