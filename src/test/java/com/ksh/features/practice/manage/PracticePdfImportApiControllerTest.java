package com.ksh.features.practice.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.entities.PracticePdfRegionAnnotation;
import com.ksh.entities.LecturerAsset;
import com.ksh.entities.User;
import com.ksh.features.messaging.service.MessagingService;
import com.ksh.features.notifications.service.NotificationService;
import com.ksh.features.practice.manage.controller.PracticePdfImportApiController;
import com.ksh.features.practice.manage.service.*;
import com.ksh.security.KshUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;

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

    @MockBean
    private PracticePdfImportSessionService sessionService;

    @MockBean
    private PracticePdfPreviewService previewService;

    @MockBean
    private PracticePdfRegionService regionService;

    @MockBean
    private PracticePdfPageExtractionService pageExtractionService;

    @MockBean
    private LecturerAssetService assetService;

    @MockBean
    private com.ksh.features.practice.repository.LecturerAssetRepository assetRepository;

    @MockBean
    private PracticePdfPayloadPreviewService payloadPreviewService;

    @MockBean
    private PracticePdfAiPayloadBuilder payloadBuilder;

    @MockBean
    private PracticePdfAiOrchestrator aiOrchestrator;

    @MockBean
    private PracticePdfDraftAssembler draftAssembler;

    @MockBean
    private PracticeImportDraftService importDraftService;

    @MockBean
    private PracticeImportSnapshotService snapshotService;

    @MockBean
    private PracticePublisherService publisherService;

    @MockBean
    private MessagingService messagingService;

    @MockBean
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

        when(sessionService.getSession(eq(100L), any())).thenReturn(session);

        mockMvc.perform(get("/practice/manage/import-sessions/100")
                        .with(user(lecturerUser))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.originalFilename").value("test.pdf"))
                .andExpect(jsonPath("$.status").value("UPLOADED"));
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
