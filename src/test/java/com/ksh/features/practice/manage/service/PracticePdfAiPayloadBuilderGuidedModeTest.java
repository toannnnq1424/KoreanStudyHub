package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.entities.PracticePdfPageExtraction;
import com.ksh.entities.PracticePdfRegionAnnotation;
import com.ksh.features.practice.manage.validator.ImportAiPayloadValidator;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticePdfAiPayloadBuilderGuidedModeTest {

    @Test
    void basicTextSupportsExtractAndGenerateWithStableUntrustedEvidence() {
        PracticePdfAiPayloadBuilder builder = builder(
                mock(PracticePdfPageExtractionService.class));
        TargetRoute target = new TargetRoute(91L, 2, "READING", "R2");

        PracticePdfAuthoringRequest extract = builder.buildBasicText(
                "  박물관은 월요일에 쉽니다.  ", SourceOperation.EXTRACT,
                "Giữ nguyên câu hỏi", target);
        PracticePdfAuthoringRequest generate = builder.buildBasicText(
                "박물관은 월요일에 쉽니다.", SourceOperation.GENERATE,
                "Tạo biến thể", target);

        assertThat(extract.sourceType())
                .isEqualTo(PracticePdfAuthoringRequest.SourceType.TEXT);
        assertThat(extract.operation()).isEqualTo(SourceOperation.EXTRACT);
        assertThat(generate.operation()).isEqualTo(SourceOperation.GENERATE);
        assertThat(extract.sourceDigest()).isEqualTo(generate.sourceDigest());
        assertThat(extract.evidence()).singleElement().satisfies(evidence -> {
            assertThat(evidence.kind()).isEqualTo("TEXT_SPAN");
            assertThat(evidence.sourceId()).isEqualTo("text-1");
            assertThat(evidence.untrustedText()).isEqualTo("박물관은 월요일에 쉽니다.");
        });
        assertThat(extract.sourceContext())
                .containsEntry("trust", "UNTRUSTED_SOURCE_CONTENT")
                .containsEntry("mode", "BASIC_TEXT");
    }

    @Test
    void basicPdfSupportsExtractAndGenerateWithPageBoundEvidence() {
        PracticePdfPageExtractionService extraction =
                mock(PracticePdfPageExtractionService.class);
        PracticePdfAiPayloadBuilder builder = builder(extraction);
        PracticePdfImportSession session = candidateSession();
        when(extraction.extractOrGetPageText(session, 1))
                .thenReturn(page(9L, 1, "Trang một có câu hỏi."));
        when(extraction.extractOrGetPageText(session, 2))
                .thenReturn(page(9L, 2, "Trang hai có đáp án."));

        PracticePdfAuthoringRequest extract = builder.buildBasicPdf(
                session, SourceOperation.EXTRACT, "Trích xuất");
        PracticePdfAuthoringRequest generate = builder.buildBasicPdf(
                session, SourceOperation.GENERATE, "Tạo câu mới");

        assertThat(extract.sourceType())
                .isEqualTo(PracticePdfAuthoringRequest.SourceType.PDF);
        assertThat(extract.sessionId()).isEqualTo(9L);
        assertThat(extract.evidence()).extracting(
                        PracticePdfAuthoringRequest.SourceEvidence::sourceId)
                .containsExactly("page-1", "page-1-text", "page-2", "page-2-text");
        assertThat(extract.evidence()).filteredOn(
                        evidence -> "PAGE".equals(evidence.kind()))
                .extracting(PracticePdfAuthoringRequest.SourceEvidence::pageNumber)
                .containsExactly(1, 2);
        assertThat(generate.operation()).isEqualTo(SourceOperation.GENERATE);
        assertThat(generate.sourceDigest()).isEqualTo(extract.sourceDigest());
        assertThat(extract.images()).isEmpty();
    }

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

        session.setLinkedDraftId(91L);
        PracticePdfAuthoringRequest advanced =
                builder.buildAdvancedAuthoringRequest(
                        session, payload, SourceOperation.EXTRACT, "");
        assertThat(advanced.sourceType())
                .isEqualTo(PracticePdfAuthoringRequest.SourceType.ADVANCED_PDF);
        assertThat(advanced.evidence()).extracting(
                        PracticePdfAuthoringRequest.SourceEvidence::sourceId)
                .containsExactly("page-2", "page-3");
        assertThat(advanced.sourceContext())
                .containsEntry("mode", "ADVANCED_PDF")
                .containsEntry("trust", "UNTRUSTED_SOURCE_CONTENT");
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

    private static PracticePdfImportSession candidateSession() {
        PracticePdfImportSession session = new PracticePdfImportSession(
                7L, "basic.pdf", "/private/basic.pdf", 2, "UPLOADED",
                LocalDateTime.now(), LocalDateTime.now(),
                LocalDateTime.now().plusHours(1));
        session.setId(9L);
        session.setSelectedStartPage(1);
        session.setSelectedEndPage(2);
        session.setLinkedDraftId(91L);
        session.setTargetTestNo(2);
        session.setTargetSkill("READING");
        session.setTargetLessonCode("R2");
        return session;
    }

    private static PracticePdfAiPayloadBuilder builder(
            PracticePdfPageExtractionService extraction) {
        return new PracticePdfAiPayloadBuilder(
                mock(PracticePdfRegionAnnotationRepository.class),
                mock(PracticePdfImportSectionDraftRepository.class),
                mock(PracticePdfImportGroupDraftRepository.class),
                extraction,
                mock(PracticePdfCropService.class),
                mock(LecturerAssetRepository.class),
                mock(AssetStorageService.class),
                new ImportAiPayloadValidator());
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
