package com.ksh.features.practice.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.entities.User;
import com.ksh.features.messaging.service.MessagingService;
import com.ksh.features.notifications.service.NotificationService;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateState;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateView;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import com.ksh.features.practice.manage.controller.PracticePdfImportApiController;
import com.ksh.features.practice.manage.service.LecturerAssetService;
import com.ksh.features.practice.manage.service.PracticeImportSnapshotService;
import com.ksh.features.practice.manage.service.PracticePdfAiGenerationService;
import com.ksh.features.practice.manage.service.PracticePdfAiOrchestrator;
import com.ksh.features.practice.manage.service.PracticePdfAiPayloadBuilder;
import com.ksh.features.practice.manage.service.PracticePdfAuthoringCandidateAssembler;
import com.ksh.features.practice.manage.service.PracticePdfAuthoringRequest;
import com.ksh.features.practice.manage.service.PracticePdfPageExtractionService;
import com.ksh.features.practice.manage.service.PracticePdfPayloadPreviewService;
import com.ksh.features.practice.manage.service.PracticePdfPreviewService;
import com.ksh.features.practice.manage.service.PracticePdfRegionService;
import com.ksh.features.practice.manage.service.PracticePdfImportSessionService;
import com.ksh.features.practice.preferences.PracticeKoreanFontPreferenceService;
import com.ksh.security.AuthenticatedUserIdResolver;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PracticePdfImportApiController.class)
@Import(PracticePdfImportApiControllerTest.MethodSecurityConfiguration.class)
class PracticePdfImportApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private PracticePdfImportSessionService sessionService;
    @MockitoBean private PracticePdfPreviewService previewService;
    @MockitoBean private PracticePdfRegionService regionService;
    @MockitoBean private PracticePdfPageExtractionService pageExtractionService;
    @MockitoBean private LecturerAssetService assetService;
    @MockitoBean private PracticePdfPayloadPreviewService payloadPreviewService;
    @MockitoBean private PracticePdfAiPayloadBuilder payloadBuilder;
    @MockitoBean private PracticePdfAiOrchestrator aiOrchestrator;
    @MockitoBean private PracticePdfAuthoringCandidateAssembler candidateAssembler;
    @MockitoBean private PracticePdfAiGenerationService generationService;
    @MockitoBean private PracticeAuthorizationService authorizationService;
    @MockitoBean private PracticeImportSnapshotService snapshotService;

    // Security slice collaborators.
    @MockitoBean private MessagingService messagingService;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private AuthenticatedUserIdResolver authenticatedUserIdResolver;
    @MockitoBean private PracticeKoreanFontPreferenceService koreanFontPreferenceService;

    private KshUserDetails lecturer;

    @BeforeEach
    void setUp() {
        lecturer = userDetails(1L, Role.LECTURER);
    }

    @Test
    void controllerDeclaresExactLecturerBoundary() {
        PreAuthorize boundary = PracticePdfImportApiController.class
                .getAnnotation(PreAuthorize.class);
        assertEquals(com.ksh.security.Roles.PREAUTH_LECTURER, boundary.value());
    }

    @Test
    void learnerCannotEnterBasicAuthoringBoundary() throws Exception {
        mockMvc.perform(multipart("/practice/manage/pdf-authoring/candidates")
                        .param("sourceType", "TEXT")
                        .param("operation", "EXTRACT")
                        .param("sourceText", "private text")
                        .param("draftId", "91")
                        .param("testNo", "1")
                        .param("skill", "READING")
                        .param("lessonCode", "R1")
                        .with(user(userDetails(2L, Role.STUDENT)))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(payloadBuilder, aiOrchestrator, candidateAssembler);
    }

    @Test
    void getSessionNeverExposesGenerationClaimToken() throws Exception {
        PracticePdfImportSession session = session(1L);
        ReflectionTestUtils.setField(session, "generationClaimToken", "secret-token");
        when(sessionService.getSession(100L, 1L)).thenReturn(session);

        mockMvc.perform(get("/practice/manage/import-sessions/100")
                        .with(user(lecturer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.generationClaimToken").doesNotExist());
    }

    @Test
    void basicTextExtractCreatesCandidateAndReturnsReviewRouteOnly() throws Exception {
        TargetRoute target = new TargetRoute(91L, 1, "READING", "R1");
        PracticePdfAuthoringRequest authoring = request(
                PracticePdfAuthoringRequest.SourceType.TEXT,
                SourceOperation.EXTRACT, target, null);
        PracticePdfAiOrchestrator.GenerationResult generation = generation();
        CandidateView candidate = candidate("candidate-7");
        when(sessionService.resolveStartContext(91L, 1, "R1", 1L))
                .thenReturn(new PracticePdfImportSessionService.PdfImportStartContext(
                        new PracticePdfImportSessionService.TargetSectionOption(
                                1, "R1", "READING", "Reading"), List.of()));
        when(payloadBuilder.buildBasicText(
                "Nguồn câu hỏi", SourceOperation.EXTRACT,
                "Giữ nguyên đáp án", target)).thenReturn(authoring);
        when(aiOrchestrator.generate(authoring)).thenReturn(generation);
        when(candidateAssembler.assemble(authoring, generation, 1L))
                .thenReturn(candidate);

        mockMvc.perform(multipart("/practice/manage/pdf-authoring/candidates")
                        .param("sourceType", "TEXT")
                        .param("operation", "EXTRACT")
                        .param("sourceText", "Nguồn câu hỏi")
                        .param("lecturerRequest", "Giữ nguyên đáp án")
                        .param("draftId", "91")
                        .param("testNo", "1")
                        .param("skill", "READING")
                        .param("lessonCode", "R1")
                        .with(user(lecturer))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateId").value("candidate-7"))
                .andExpect(jsonPath("$.state").value("REVIEWING"))
                .andExpect(jsonPath("$.reviewUrl")
                        .value("/practice/manage/authoring-candidates/candidate-7"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.draftId").doesNotExist());

        verify(candidateAssembler).assemble(authoring, generation, 1L);
    }

    @Test
    void basicPdfGenerateCreatesPrivateSessionThenCandidate() throws Exception {
        TargetRoute target = new TargetRoute(91L, 1, "READING", "R1");
        PracticePdfImportSession uploaded = session(1L);
        uploaded.setLinkedDraftId(91L);
        uploaded.setTargetTestNo(1);
        uploaded.setTargetSkill("READING");
        uploaded.setTargetLessonCode("R1");
        uploaded.setSelectedStartPage(1);
        uploaded.setSelectedEndPage(1);
        PracticePdfAuthoringRequest authoring = request(
                PracticePdfAuthoringRequest.SourceType.PDF,
                SourceOperation.GENERATE, target, 100L);
        PracticePdfAiOrchestrator.GenerationResult generation = generation();
        when(sessionService.resolveStartContext(91L, 1, "R1", 1L))
                .thenReturn(new PracticePdfImportSessionService.PdfImportStartContext(
                        new PracticePdfImportSessionService.TargetSectionOption(
                                1, "R1", "READING", "Reading"), List.of()));
        when(sessionService.createSession(
                eq(1L), any(), eq(null), eq(91L), eq(1),
                eq("READING"), eq("R1"))).thenReturn(uploaded);
        when(sessionService.updatePageRange(100L, 1, 1, 1L))
                .thenReturn(uploaded);
        when(payloadBuilder.buildBasicPdf(
                uploaded, SourceOperation.GENERATE, "Tạo câu mới"))
                .thenReturn(authoring);
        when(aiOrchestrator.generate(authoring)).thenReturn(generation);
        when(candidateAssembler.assemble(authoring, generation, 1L))
                .thenReturn(candidate("candidate-pdf"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "private.pdf", MediaType.APPLICATION_PDF_VALUE,
                "%PDF-fake-bounded-test".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/practice/manage/pdf-authoring/candidates")
                        .file(file)
                        .param("sourceType", "PDF")
                        .param("operation", "GENERATE")
                        .param("lecturerRequest", "Tạo câu mới")
                        .param("draftId", "91")
                        .param("testNo", "1")
                        .param("skill", "READING")
                        .param("lessonCode", "R1")
                        .param("startPage", "1")
                        .param("endPage", "1")
                        .with(user(lecturer)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateId").value("candidate-pdf"))
                .andExpect(jsonPath("$.reviewUrl")
                        .value("/practice/manage/authoring-candidates/candidate-pdf"));

        verify(sessionService).createSession(
                eq(1L), any(), eq(null), eq(91L), eq(1),
                eq("READING"), eq("R1"));
        verify(payloadBuilder).buildBasicPdf(
                uploaded, SourceOperation.GENERATE, "Tạo câu mới");
    }

    @Test
    void mismatchedBasicTargetStopsBeforePdfStorageOrProvider() throws Exception {
        when(sessionService.resolveStartContext(91L, 1, "R1", 1L))
                .thenReturn(new PracticePdfImportSessionService.PdfImportStartContext(
                        new PracticePdfImportSessionService.TargetSectionOption(
                                2, "R2", "READING", "Reading 2"), List.of()));
        MockMultipartFile file = new MockMultipartFile(
                "file", "private.pdf", MediaType.APPLICATION_PDF_VALUE,
                "%PDF-fake-bounded-test".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/practice/manage/pdf-authoring/candidates")
                        .file(file)
                        .param("sourceType", "PDF")
                        .param("operation", "EXTRACT")
                        .param("draftId", "91")
                        .param("testNo", "1")
                        .param("skill", "READING")
                        .param("lessonCode", "R1")
                        .with(user(lecturer)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("PDF_AUTHORING_REQUEST_INVALID"));

        verify(sessionService, org.mockito.Mockito.never()).createSession(
                any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(payloadBuilder, aiOrchestrator, candidateAssembler);
    }

    @Test
    void duplicateAdvancedGenerationDoesNotCallProvider() throws Exception {
        when(generationService.claim(100L, 1L)).thenReturn(
                new PracticePdfAiGenerationService.ClaimResult(
                        PracticePdfAiGenerationService.Outcome.IN_PROGRESS,
                        null, null,
                        LocalDateTime.parse("2026-07-28T08:00:00"), null));

        mockMvc.perform(post("/practice/manage/import-sessions/100/generate")
                        .with(user(lecturer)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        verifyNoInteractions(payloadBuilder, aiOrchestrator, candidateAssembler);
    }

    @Test
    void legacyCompletedAdvancedSessionCannotReturnOrMergeDraft() throws Exception {
        when(generationService.claim(100L, 1L)).thenReturn(
                new PracticePdfAiGenerationService.ClaimResult(
                        PracticePdfAiGenerationService.Outcome.COMPLETED,
                        null, 91L, null, null));

        mockMvc.perform(post("/practice/manage/import-sessions/100/generate")
                        .with(user(lecturer)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("LEGACY_PDF_GENERATION_ALREADY_COMPLETED"));

        verifyNoInteractions(payloadBuilder, aiOrchestrator, candidateAssembler,
                authorizationService);
    }

    @Test
    void claimedAdvancedGenerationUsesFencedSessionAndCreatesCandidate() throws Exception {
        PracticePdfImportSession claimed = session(1L);
        claimed.setLinkedDraftId(91L);
        claimed.setTargetTestNo(1);
        claimed.setTargetSkill("READING");
        claimed.setTargetLessonCode("R1");
        PracticePdfAiPayloadBuilder.PayloadInfo payload =
                new PracticePdfAiPayloadBuilder.PayloadInfo(
                        null, "", List.of(), Map.of(), List.of());
        TargetRoute target = new TargetRoute(91L, 1, "READING", "R1");
        PracticePdfAuthoringRequest authoring = request(
                PracticePdfAuthoringRequest.SourceType.ADVANCED_PDF,
                SourceOperation.GENERATE, target, 100L);
        PracticePdfAiOrchestrator.GenerationResult generation = generation();
        when(generationService.claim(100L, 1L)).thenReturn(
                new PracticePdfAiGenerationService.ClaimResult(
                        PracticePdfAiGenerationService.Outcome.CLAIMED,
                        "claim-token", null,
                        LocalDateTime.parse("2026-07-28T08:00:00"), claimed));
        when(payloadBuilder.buildPayload(claimed)).thenReturn(payload);
        when(payloadBuilder.buildAdvancedAuthoringRequest(
                claimed, payload, SourceOperation.GENERATE, "Tạo biến thể"))
                .thenReturn(authoring);
        when(aiOrchestrator.generate(authoring)).thenReturn(generation);
        when(candidateAssembler.assemble(authoring, generation, 1L))
                .thenReturn(candidate("candidate-advanced"));

        mockMvc.perform(post("/practice/manage/import-sessions/100/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\":\"GENERATE\",\"lecturerRequest\":\"Tạo biến thể\"}")
                        .with(user(lecturer)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateId").value("candidate-advanced"));

        verify(authorizationService).requireDraft(91L, 1L, PracticeAction.EDIT);
        verify(generationService).release(100L, 1L, "claim-token", "REVIEWING");
        verifyNoInteractions(sessionService);
    }

    @Test
    void revokedAdvancedTargetStopsBeforeProviderAndReleasesClaim() throws Exception {
        PracticePdfImportSession claimed = session(1L);
        claimed.setLinkedDraftId(91L);
        when(generationService.claim(100L, 1L)).thenReturn(
                new PracticePdfAiGenerationService.ClaimResult(
                        PracticePdfAiGenerationService.Outcome.CLAIMED,
                        "claim-token", null,
                        LocalDateTime.parse("2026-07-28T08:00:00"), claimed));
        doThrow(new AccessDeniedException("revoked"))
                .when(authorizationService)
                .requireDraft(91L, 1L, PracticeAction.EDIT);

        mockMvc.perform(post("/practice/manage/import-sessions/100/generate")
                        .with(user(lecturer)).with(csrf()))
                .andExpect(status().isForbidden());

        verify(generationService).release(
                100L, 1L, "claim-token", "READY_FOR_AI");
        verifyNoInteractions(payloadBuilder, aiOrchestrator, candidateAssembler);
    }

    private PracticePdfAiOrchestrator.GenerationResult generation() {
        return new PracticePdfAiOrchestrator.GenerationResult(
                objectMapper.createObjectNode(),
                objectMapper.createObjectNode(),
                "authoring-v1", "request-1", "provider-request-1");
    }

    private CandidateView candidate(String id) {
        return new CandidateView(id, CandidateState.REVIEWING, 3L,
                "sha256:" + "b".repeat(64), objectMapper.createObjectNode(), List.of());
    }

    private static PracticePdfAuthoringRequest request(
            PracticePdfAuthoringRequest.SourceType sourceType,
            SourceOperation operation,
            TargetRoute target,
            Long sessionId) {
        String text = "Nguồn câu hỏi";
        return new PracticePdfAuthoringRequest(
                sourceType, operation, "source.txt",
                "sha256:" + "a".repeat(64), target, "",
                List.of(new PracticePdfAuthoringRequest.SourceEvidence(
                        "TEXT_SPAN", "source-1", null, text.length(), text)),
                Map.of("trust", "UNTRUSTED_SOURCE_CONTENT"), List.of(), sessionId);
    }

    private static PracticePdfImportSession session(Long ownerId) {
        PracticePdfImportSession session = new PracticePdfImportSession(
                ownerId, "test.pdf", "path/to/test.pdf", 1, "UPLOADED",
                LocalDateTime.now(), LocalDateTime.now(),
                LocalDateTime.now().plusHours(1));
        session.setId(100L);
        return session;
    }

    private static KshUserDetails userDetails(Long id, Role role) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(role);
        when(user.getEmail()).thenReturn(role.name().toLowerCase() + "@ksh.edu.vn");
        when(user.getPasswordHash()).thenReturn("encodedPassword");
        when(user.getFullName()).thenReturn(role.name());
        when(user.isActive()).thenReturn(true);
        when(user.isLocked()).thenReturn(false);
        return new KshUserDetails(user);
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
