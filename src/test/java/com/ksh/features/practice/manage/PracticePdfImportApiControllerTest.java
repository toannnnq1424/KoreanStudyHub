package com.ksh.features.practice.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.entities.PracticePdfRegionAnnotation;
import com.ksh.entities.User;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
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
    private PracticePdfCropService cropService;

    @MockBean
    private LecturerAssetService assetService;

    @MockBean
    private com.ksh.features.practice.repository.LecturerAssetRepository assetRepository;

    @MockBean
    private com.ksh.features.practice.repository.PracticeDraftAssetUsageRepository usageRepository;

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
}
