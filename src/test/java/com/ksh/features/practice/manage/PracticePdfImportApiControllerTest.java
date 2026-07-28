package com.ksh.features.practice.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.entities.PracticePdfRegionAnnotation;
import com.ksh.entities.LecturerAsset;
import com.ksh.entities.User;
import com.ksh.features.messaging.service.MessagingService;
import com.ksh.features.notifications.service.NotificationService;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.manage.controller.PracticePdfImportApiController;
import com.ksh.features.practice.manage.service.*;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.security.KshUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PracticePdfImportApiController.class)
class PracticePdfImportApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PracticePdfImportSessionService sessionService;

    @MockitoBean
    private PracticePdfPreviewService previewService;

    @MockitoBean
    private PracticePdfRegionService regionService;

    @MockitoBean
    private PracticePdfPageExtractionService pageExtractionService;

    @MockitoBean
    private LecturerAssetService assetService;

    @MockitoBean
    private com.ksh.features.practice.repository.LecturerAssetRepository assetRepository;

    @MockitoBean
    private PracticePdfPayloadPreviewService payloadPreviewService;

    @MockitoBean
    private PracticePdfAiPayloadBuilder payloadBuilder;

    @MockitoBean
    private PracticePdfAiOrchestrator aiOrchestrator;

    @MockitoBean
    private PracticePdfDraftAssembler draftAssembler;

    @MockitoBean
    private PracticePdfAiGenerationService generationService;

    @MockitoBean
    private PracticeDraftRepository draftRepository;

    @MockitoBean
    private PracticeAuthorizationService authorizationService;

    @MockitoBean
    private PracticeImportDraftService importDraftService;

    @MockitoBean
    private PracticeImportSnapshotService snapshotService;

    @MockitoBean
    private PracticePublisherService publisherService;

    @MockitoBean
    private MessagingService messagingService;

    @MockitoBean
    private NotificationService notificationService;

    private KshUserDetails lecturerUser;

    @BeforeEach
    void setUp() {
        // Create mock lecturer user
        User mockUser = org.mockito.Mockito.mock(User.class);
        when(mockUser.getId()).thenReturn(1L);
        when(mockUser.getRole()).thenReturn(com.ksh.security.Role.LECTURER);
        when(mockUser.getEmail()).thenReturn("lecturer@ksh.edu.vn");
        when(mockUser.getPasswordHash()).thenReturn("encodedPassword");
        when(mockUser.getFullName()).thenReturn("Nguyễn Giảng Viên");
        when(mockUser.isActive()).thenReturn(true);
        when(mockUser.isLocked()).thenReturn(false);

        lecturerUser = new KshUserDetails(mockUser);
    }

    @Test
    @WithMockUser(roles = "LECTURER")
    void testGetSession_Success() throws Exception {
        PracticePdfImportSession session = new PracticePdfImportSession(
                1L, "test.pdf", "path/to/test.pdf", 10, "UPLOADED",
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now().plusHours(24)
        );
        session.setId(100L);
        ReflectionTestUtils.setField(
                session, "generationClaimToken", "server-secret-token");

        when(sessionService.getSession(eq(100L), any())).thenReturn(session);

        mockMvc.perform(get("/practice/manage/import-sessions/100")
                        .with(user(lecturerUser))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.originalFilename").value("test.pdf"))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.generationClaimToken").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "LECTURER")
    void testUpdatePageRange_Success() throws Exception {
        PracticePdfImportSession session = new PracticePdfImportSession(
                1L, "test.pdf", "path/to/test.pdf", 10, "ANNOTATING",
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now().plusHours(24)
        );
        session.setId(100L);
        session.setSelectedStartPage(2);
        session.setSelectedEndPage(8);

        when(sessionService.updatePageRange(eq(100L), eq(2), eq(8), any())).thenReturn(session);

        mockMvc.perform(put("/practice/manage/import-sessions/100/page-range")
                        .with(user(lecturerUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startPage\":2,\"endPage\":8,\"extractionMode\":\"HYBRID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedStartPage").value(2))
                .andExpect(jsonPath("$.selectedEndPage").value(8))
                .andExpect(jsonPath("$.status").value("ANNOTATING"));
    }

    @Test
    @WithMockUser(roles = "LECTURER")
    void testGetAnnotations_Success() throws Exception {
        PracticePdfRegionAnnotation annotation = new PracticePdfRegionAnnotation();
        annotation.setId(500L);
        annotation.setSessionId(100L);
        annotation.setPageNumber(2);
        annotation.setRegionType("INSTRUCTION");

        when(regionService.getAnnotations(eq(100L), any())).thenReturn(List.of(annotation));

        mockMvc.perform(get("/practice/manage/import-sessions/100/annotations")
                        .with(user(lecturerUser))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(500))
                .andExpect(jsonPath("$[0].regionType").value("INSTRUCTION"));
    }

    @Test
    @WithMockUser(roles = "LECTURER")
    void promoteAssetBindsSessionRegionAndOwnerFromRoute() throws Exception {
        PracticePdfRegionAnnotation annotation = new PracticePdfRegionAnnotation();
        annotation.setId(500L);
        annotation.setSessionId(100L);
        LecturerAsset asset = new LecturerAsset();
        asset.setId(700L);
        asset.setOwnerLecturerId(1L);
        asset.setStatus("ACTIVE");
        when(regionService.getAnnotation(100L, 500L, 1L)).thenReturn(annotation);
        when(assetService.promoteSessionRegionAsset(100L, 500L, 700L, 1L)).thenReturn(asset);

        mockMvc.perform(post("/practice/manage/import-sessions/100/regions/500/promote-asset")
                        .param("assetId", "700")
                        .with(user(lecturerUser))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(700))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.contentUrl")
                        .value("/practice/materials/700/content"))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(jsonPath("$.storageProvider").doesNotExist())
                .andExpect(jsonPath("$.sha256").doesNotExist());

        verify(regionService).getAnnotation(100L, 500L, 1L);
        verify(assetService).promoteSessionRegionAsset(100L, 500L, 700L, 1L);
    }

    @Test
    @WithMockUser(roles = "LECTURER")
    void assetListUsesSafeViewWithoutPrivateStorageMetadata() throws Exception {
        LecturerAsset asset = new LecturerAsset();
        asset.setId(700L);
        asset.setOwnerLecturerId(1L);
        asset.setStorageProvider("LOCAL");
        asset.setStorageKey("lecturer-assets/1/private/secret.png");
        asset.setSha256("secret-hash");
        asset.setTitle("Ảnh câu hỏi");
        asset.setAssetType("IMAGE");
        asset.setStatus("ACTIVE");
        asset.setVisibility("PRIVATE");
        asset.setFileSize(120L);
        when(assetService.getLibraryAssets(1L)).thenReturn(List.of(asset));

        mockMvc.perform(get("/practice/manage/assets")
                        .with(user(lecturerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(700))
                .andExpect(jsonPath("$[0].title").value("Ảnh câu hỏi"))
                .andExpect(jsonPath("$[0].contentUrl")
                        .value("/practice/materials/700/content"))
                .andExpect(jsonPath("$[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$[0].storageProvider").doesNotExist())
                .andExpect(jsonPath("$[0].sha256").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "LECTURER")
    void lecturerPayloadPreviewIsRedactedByController() throws Exception {
        PracticePdfImportSession session = session(1L);
        when(sessionService.getSession(100L, 1L)).thenReturn(session);
        when(payloadPreviewService.getPreview(session)).thenReturn(payloadPreview());

        mockMvc.perform(get("/practice/manage/import-sessions/100/payload-preview")
                        .with(user(lecturerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privilegedDetails").value(false))
                .andExpect(jsonPath("$.systemPrompt").value(nullValue()))
                .andExpect(jsonPath("$.requestJsonPreview").value(nullValue()))
                .andExpect(jsonPath("$.model").value(nullValue()));
    }

    @Test
    @WithMockUser(roles = "LECTURER")
    void duplicateGenerateWhileClaimIsLiveDoesNotCallProvider() throws Exception {
        PracticePdfImportSession session = session(1L);
        when(sessionService.getSession(100L, 1L)).thenReturn(session);
        when(generationService.claim(100L, 1L)).thenReturn(
                new PracticePdfAiGenerationService.ClaimResult(
                        PracticePdfAiGenerationService.Outcome.IN_PROGRESS,
                        null,
                        null,
                        LocalDateTime.parse("2026-07-28T08:00:00"),
                        null));

        mockMvc.perform(post("/practice/manage/import-sessions/100/generate")
                        .with(user(lecturerUser))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.leaseExpiresAt")
                        .value("2026-07-28T08:00:00"));

        verifyNoInteractions(payloadBuilder, aiOrchestrator, draftAssembler);
    }

    @Test
    @WithMockUser(roles = "LECTURER")
    void completedGenerateReturnsOwnedDraftWithoutCallingProvider() throws Exception {
        PracticePdfImportSession session = session(1L);
        PracticeDraft completed = new PracticeDraft(
                "Import", "", "GLOBAL", null, "DRAFT", 1L, "{}");
        ReflectionTestUtils.setField(completed, "id", 91L);
        when(sessionService.getSession(100L, 1L)).thenReturn(session);
        when(generationService.claim(100L, 1L)).thenReturn(
                new PracticePdfAiGenerationService.ClaimResult(
                        PracticePdfAiGenerationService.Outcome.COMPLETED,
                        null,
                        91L,
                        null,
                        null));
        when(draftRepository.findById(91L))
                .thenReturn(Optional.of(completed));

        mockMvc.perform(post("/practice/manage/import-sessions/100/generate")
                        .with(user(lecturerUser))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(91));

        verify(authorizationService)
                .requireDraft(91L, 1L, PracticeAction.EDIT);
        verifyNoInteractions(payloadBuilder, aiOrchestrator, draftAssembler);
    }

    @Test
    @WithMockUser(roles = "LECTURER")
    void claimedGenerationUsesSessionFencedByClaim() throws Exception {
        PracticePdfImportSession claimedSession = session(1L);
        claimedSession.setSelectedStartPage(1);
        claimedSession.setSelectedEndPage(1);
        PracticePdfAiPayloadBuilder.PayloadInfo payloadInfo =
                new PracticePdfAiPayloadBuilder.PayloadInfo(
                        null, "", List.of(), Map.of(), List.of());
        PracticeDraft generated = new PracticeDraft(
                "Import", "", "GLOBAL", null, "DRAFT", 1L, "{}");
        ReflectionTestUtils.setField(generated, "id", 92L);

        when(generationService.claim(100L, 1L)).thenReturn(
                new PracticePdfAiGenerationService.ClaimResult(
                        PracticePdfAiGenerationService.Outcome.CLAIMED,
                        "claim-token",
                        null,
                        LocalDateTime.parse("2026-07-28T08:00:00"),
                        claimedSession));
        when(payloadBuilder.buildPayload(claimedSession))
                .thenReturn(payloadInfo);
        when(aiOrchestrator.callAi(
                payloadInfo, 100L, claimedSession.getExtractionStrategy()))
                .thenReturn("{}");
        when(draftAssembler.assembleAndSaveDraft(
                claimedSession, "{}", 1L, "claim-token"))
                .thenReturn(generated);

        mockMvc.perform(post("/practice/manage/import-sessions/100/generate")
                        .with(user(lecturerUser))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(92));

        verify(authorizationService)
                .requireGlobal(1L, PracticeAction.CREATE);
        verify(payloadBuilder).buildPayload(claimedSession);
        verifyNoInteractions(sessionService);
    }

    @Test
    @WithMockUser(roles = "LECTURER")
    void revokedDraftPermissionStopsBeforeProviderAndReleasesClaim()
            throws Exception {
        PracticePdfImportSession claimedSession = session(1L);
        claimedSession.setLinkedDraftId(91L);
        when(generationService.claim(100L, 1L)).thenReturn(
                new PracticePdfAiGenerationService.ClaimResult(
                        PracticePdfAiGenerationService.Outcome.CLAIMED,
                        "claim-token",
                        null,
                        LocalDateTime.parse("2026-07-28T08:00:00"),
                        claimedSession));
        doThrow(new AccessDeniedException("revoked"))
                .when(authorizationService)
                .requireDraft(91L, 1L, PracticeAction.EDIT);

        mockMvc.perform(post("/practice/manage/import-sessions/100/generate")
                        .with(user(lecturerUser))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(generationService).release(
                100L, 1L, "claim-token", "READY_FOR_AI");
        verifyNoInteractions(payloadBuilder, aiOrchestrator, draftAssembler);
    }

    @Test
    void controllerDeclaresExactLecturerBoundary() {
        PreAuthorize boundary = PracticePdfImportApiController.class
                .getAnnotation(PreAuthorize.class);

        assertEquals(com.ksh.security.Roles.PREAUTH_LECTURER, boundary.value());
    }

    private static PracticePdfPayloadPreviewService.PayloadPreviewDto payloadPreview() {
        return new PracticePdfPayloadPreviewService.PayloadPreviewDto(
                true,
                "SECRET_SYSTEM_PROMPT",
                "safe-model",
                "HYBRID",
                Map.of("selectedPagesCount", 1),
                List.of(),
                List.of(),
                "SECRET_REQUEST"
        );
    }

    private static PracticePdfImportSession session(Long ownerId) {
        PracticePdfImportSession session = new PracticePdfImportSession(
                ownerId, "test.pdf", "path/to/test.pdf", 1, "UPLOADED",
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        session.setId(100L);
        return session;
    }

    private static KshUserDetails userDetails(Long id, com.ksh.security.Role role) {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(role);
        when(user.getEmail()).thenReturn(role.name().toLowerCase() + "@ksh.edu.vn");
        when(user.getPasswordHash()).thenReturn("encodedPassword");
        when(user.getFullName()).thenReturn(role.name());
        when(user.isActive()).thenReturn(true);
        when(user.isLocked()).thenReturn(false);
        return new KshUserDetails(user);
    }
}
