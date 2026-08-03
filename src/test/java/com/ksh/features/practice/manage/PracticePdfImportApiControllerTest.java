package com.ksh.features.practice.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateState;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateView;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import com.ksh.features.practice.manage.controller.PracticePdfImportApiController;
import com.ksh.features.practice.manage.service.LecturerAssetService;
import com.ksh.features.practice.manage.service.PracticeImportTargetService;
import com.ksh.features.practice.manage.service.PracticePdfAiOrchestrator;
import com.ksh.features.practice.manage.service.PracticePdfAiPayloadBuilder;
import com.ksh.features.practice.manage.service.PracticePdfAuthoringCandidateAssembler;
import com.ksh.features.practice.manage.service.PracticePdfAuthoringRequest;
import com.ksh.features.practice.ai.transport.PracticeAiContractException;
import com.ksh.security.KshUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PracticePdfImportApiControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LecturerAssetService assetService = mock(LecturerAssetService.class);
    private final PracticeImportTargetService targetService =
            mock(PracticeImportTargetService.class);
    private final PracticePdfAiPayloadBuilder payloadBuilder =
            mock(PracticePdfAiPayloadBuilder.class);
    private final PracticePdfAiOrchestrator aiOrchestrator =
            mock(PracticePdfAiOrchestrator.class);
    private final PracticePdfAuthoringCandidateAssembler candidateAssembler =
            mock(PracticePdfAuthoringCandidateAssembler.class);
    private PracticePdfImportApiController controller;
    private KshUserDetails lecturer;

    @BeforeEach
    void setUp() {
        controller = new PracticePdfImportApiController(
                assetService, targetService, payloadBuilder,
                aiOrchestrator, candidateAssembler);
        lecturer = mock(KshUserDetails.class);
        when(lecturer.getId()).thenReturn(1L);
    }

    @Test
    void controllerDeclaresExactLecturerBoundary() {
        PreAuthorize boundary = PracticePdfImportApiController.class
                .getAnnotation(PreAuthorize.class);
        assertEquals(com.ksh.security.Roles.PREAUTH_LECTURER, boundary.value());
    }

    @Test
    void basicTextCreatesCandidateAndReturnsReviewRouteOnly() {
        TargetRoute target = new TargetRoute(91L, 1, "READING", "R1");
        PracticePdfAuthoringRequest authoring = request(
                PracticePdfAuthoringRequest.SourceType.TEXT,
                SourceOperation.EXTRACT, target);
        PracticePdfAiOrchestrator.GenerationResult generation = generation();
        when(targetService.requireExactTarget(
                91L, 1, "READING", "R1", 1L)).thenReturn(target);
        when(payloadBuilder.buildBasicText(
                "Nguồn câu hỏi", SourceOperation.EXTRACT,
                "Giữ nguyên đáp án", target)).thenReturn(authoring);
        when(aiOrchestrator.generate(authoring)).thenReturn(generation);
        when(candidateAssembler.assemble(authoring, generation, 1L))
                .thenReturn(candidate("candidate-7"));

        ResponseEntity<?> response = controller.createBasicCandidate(
                "TEXT", "EXTRACT", "Nguồn câu hỏi", null,
                "Giữ nguyên đáp án", 91L, 1, "READING", "R1",
                null, null, lecturer);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("candidateId", "candidate-7")
                .containsEntry("state", "REVIEWING")
                .containsEntry("reviewUrl",
                        "/practice/manage/authoring-candidates/candidate-7")
                .doesNotContainKeys("draftId", "sessionId", "publishedId");
    }

    @Test
    void basicPdfIsPassedDirectlyToRequestLocalBuilder() {
        TargetRoute target = new TargetRoute(91L, 1, "READING", "R1");
        PracticePdfAuthoringRequest authoring = request(
                PracticePdfAuthoringRequest.SourceType.PDF,
                SourceOperation.GENERATE, target);
        PracticePdfAiOrchestrator.GenerationResult generation = generation();
        MockMultipartFile file = new MockMultipartFile(
                "file", "private.pdf", MediaType.APPLICATION_PDF_VALUE,
                "%PDF-fake".getBytes(StandardCharsets.US_ASCII));
        when(targetService.requireExactTarget(
                91L, 1, "READING", "R1", 1L)).thenReturn(target);
        when(payloadBuilder.buildBasicPdf(
                file, 2, 3, SourceOperation.GENERATE,
                "Tạo câu mới", target)).thenReturn(authoring);
        when(aiOrchestrator.generate(authoring)).thenReturn(generation);
        when(candidateAssembler.assemble(authoring, generation, 1L))
                .thenReturn(candidate("candidate-pdf"));

        ResponseEntity<?> response = controller.createBasicCandidate(
                "PDF", "GENERATE", null, file, "Tạo câu mới",
                91L, 1, "READING", "R1", 2, 3, lecturer);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(payloadBuilder).buildBasicPdf(
                file, 2, 3, SourceOperation.GENERATE, "Tạo câu mới", target);
    }

    @Test
    void unavailablePurposeReturnsStableActionableCodeWithoutCallingItForbidden() {
        TargetRoute target = new TargetRoute(91L, 1, "READING", "R1");
        PracticePdfAuthoringRequest authoring = request(
                PracticePdfAuthoringRequest.SourceType.TEXT,
                SourceOperation.EXTRACT, target);
        when(targetService.requireExactTarget(
                91L, 1, "READING", "R1", 1L)).thenReturn(target);
        when(payloadBuilder.buildBasicText(
                "Nguồn câu hỏi", SourceOperation.EXTRACT, "", target))
                .thenReturn(authoring);
        when(aiOrchestrator.generate(authoring)).thenThrow(
                new PracticeAiContractException("PROVIDER_PURPOSE_UNAVAILABLE", false));

        ResponseEntity<?> response = controller.createBasicCandidate(
                "TEXT", "EXTRACT", "Nguồn câu hỏi", null, "",
                91L, 1, "READING", "R1", null, null, lecturer);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body)
                .containsEntry("code", "PRACTICE_PDF_AUTHORING_UNAVAILABLE")
                .containsEntry("causeCode", "PROVIDER_PURPOSE_UNAVAILABLE");
        assertThat(body.get("error").toString()).contains("PDF không bị chặn")
                .doesNotContain("bị cấm");
        verifyNoInteractions(candidateAssembler);
    }

    @Test
    void revokedTargetStopsBeforeSourceOrProviderProcessing() {
        doThrow(new AccessDeniedException("revoked"))
                .when(targetService)
                .requireExactTarget(91L, 1, "READING", "R1", 1L);

        assertThrows(AccessDeniedException.class,
                () -> controller.createBasicCandidate(
                        "TEXT", "EXTRACT", "private text", null, "",
                        91L, 1, "READING", "R1", null, null, lecturer));

        verifyNoInteractions(payloadBuilder, aiOrchestrator, candidateAssembler);
    }

    @Test
    void exposedRoutesContainNoPdfSessionWorkspaceSurface() {
        List<String> routes = Arrays.stream(
                        PracticePdfImportApiController.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(routeValues(method)))
                .toList();

        assertThat(routes).containsExactlyInAnyOrder(
                "/pdf-authoring/candidates",
                "/assets",
                "/assets/{assetId}",
                "/drafts/{draftId}/assets");
        assertThat(routes).noneMatch(route -> route.contains("import-sessions"));
    }

    private static String[] routeValues(Method method) {
        PostMapping post = method.getAnnotation(PostMapping.class);
        if (post != null) return post.value();
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null) return get.value();
        DeleteMapping delete = method.getAnnotation(DeleteMapping.class);
        return delete == null ? new String[0] : delete.value();
    }

    private PracticePdfAiOrchestrator.GenerationResult generation() {
        return new PracticePdfAiOrchestrator.GenerationResult(
                objectMapper.createObjectNode(), objectMapper.createObjectNode(),
                "authoring-v1", "request-1", "provider-request-1");
    }

    private CandidateView candidate(String id) {
        return new CandidateView(id, CandidateState.REVIEWING, 3L,
                "sha256:" + "b".repeat(64), objectMapper.createObjectNode(), List.of());
    }

    private static PracticePdfAuthoringRequest request(
            PracticePdfAuthoringRequest.SourceType sourceType,
            SourceOperation operation,
            TargetRoute target) {
        String text = "Nguồn câu hỏi";
        return new PracticePdfAuthoringRequest(
                sourceType, operation, "source.txt",
                "sha256:" + "a".repeat(64), target, "",
                List.of(new PracticePdfAuthoringRequest.SourceEvidence(
                        "TEXT_SPAN", "source-1", null, text.length(), text)),
                Map.of("trust", "UNTRUSTED_SOURCE_CONTENT"), List.of());
    }
}
