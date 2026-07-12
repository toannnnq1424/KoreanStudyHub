package com.ksh.features.practice.manage;

import com.ksh.features.practice.assessment.AssessmentGovernanceService;
import com.ksh.features.practice.assessment.persistence.AssessmentExamTemplateVersion;
import com.ksh.features.practice.assessment.persistence.AssessmentProgramVersion;
import com.ksh.entities.User;
import com.ksh.features.practice.manage.controller.AssessmentGovernanceController;
import com.ksh.features.practice.manage.controller.AssessmentGovernancePageController;
import com.ksh.features.practice.manage.service.PracticeStorageReadinessService;
import com.ksh.security.AuthenticatedUserIdResolver;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = {
        AssessmentGovernanceController.class,
        AssessmentGovernancePageController.class
})
@Import(AssessmentGovernanceControllerTest.MethodSecurityTestConfig.class)
class AssessmentGovernanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AssessmentGovernanceService governanceService;

    @MockBean
    private PracticeStorageReadinessService storageReadinessService;

    @MockBean
    private AuthenticatedUserIdResolver userIdResolver;

    @BeforeEach
    void setUp() {
        when(userIdResolver.resolve(any(Authentication.class))).thenReturn(9L);
        when(governanceService.governanceCatalog(9L)).thenReturn(
                new AssessmentGovernanceService.GovernanceCatalog(List.of(), List.of()));
        when(storageReadinessService.readiness()).thenReturn(
                new PracticeStorageReadinessService.Readiness(
                        "LOCAL", "R2", "LOCAL_ACTIVE_R2_NOT_CONFIGURED",
                        false, false, List.of("bucket"), "Phase 12 local storage"));
    }

    @Test
    void headCanRenderGovernancePage() throws Exception {
        mockMvc.perform(get("/practice/manage/assessment-governance")
                        .with(user(userDetails(9L, Role.HEAD))))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/manage/assessment-governance"))
                .andExpect(model().attributeExists("catalog", "storageReadiness"));
    }

    @Test
    void lecturerCannotEnterGovernancePageOrApi() throws Exception {
        KshUserDetails lecturer = userDetails(9L, Role.LECTURER);
        mockMvc.perform(get("/practice/manage/assessment-governance")
                        .with(user(lecturer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/practice/manage/governance/assessment/catalog")
                        .with(user(lecturer)))
                .andExpect(status().isForbidden());

        verify(governanceService, never()).governanceCatalog(9L);
    }

    @Test
    void templateVersionRouteKeepsLegacyRequestCompatibility() throws Exception {
        AssessmentExamTemplateVersion created = new AssessmentExamTemplateVersion(
                "TOPIK_II", 1, "{\"schemaVersion\":\"assessment-template-v1\"}", 9L);
        when(governanceService.createTemplateVersion(
                eq("TOPIK_II"), any(String.class), eq(9L))).thenReturn(created);

        mockMvc.perform(post("/practice/manage/governance/assessment/templates/TOPIK_II/versions")
                        .with(user(userDetails(9L, Role.ADMIN)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configJson\":\"{\\\"schemaVersion\\\":\\\"assessment-template-v1\\\"}\"}"))
                .andExpect(status().isOk());

        verify(governanceService).createTemplateVersion(
                eq("TOPIK_II"), any(String.class), eq(9L));
    }

    @Test
    void templateVersionRouteUsesExactProgramVersionWhenProvided() throws Exception {
        AssessmentExamTemplateVersion created = new AssessmentExamTemplateVersion(
                "TOPIK_II", 12L, 2,
                "{\"schemaVersion\":\"assessment-template-v1\"}", 9L);
        when(governanceService.createTemplateVersion(
                eq("TOPIK_II"), eq(12L), any(String.class), eq(9L))).thenReturn(created);

        mockMvc.perform(post("/practice/manage/governance/assessment/templates/TOPIK_II/versions")
                        .with(user(userDetails(9L, Role.ADMIN)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programVersionId\":12,\"configJson\":\"{\\\"schemaVersion\\\":\\\"assessment-template-v1\\\"}\"}"))
                .andExpect(status().isOk());

        verify(governanceService).createTemplateVersion(
                eq("TOPIK_II"), eq(12L), any(String.class), eq(9L));
    }

    @Test
    void programActivationRoutePassesRequiredAuditReason() throws Exception {
        AssessmentProgramVersion version = new AssessmentProgramVersion(
                "TOPIK", 2, "TOPIK v2", "INACTIVE", "ko");
        when(governanceService.activateProgramVersion(
                "TOPIK", 12L, 9L, "Duyệt release v2")).thenReturn(version);

        mockMvc.perform(post("/practice/manage/governance/assessment/programs/TOPIK/versions/12/activate")
                        .with(user(userDetails(9L, Role.HEAD)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Duyệt release v2\"}"))
                .andExpect(status().isOk());

        verify(governanceService).activateProgramVersion(
                "TOPIK", 12L, 9L, "Duyệt release v2");
    }

    private static KshUserDetails userDetails(Long id, Role role) {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(role);
        when(user.getEmail()).thenReturn(role.name().toLowerCase() + "@ksh.edu.vn");
        when(user.getPasswordHash()).thenReturn("encoded");
        when(user.getFullName()).thenReturn(role.name());
        when(user.isActive()).thenReturn(true);
        when(user.isLocked()).thenReturn(false);
        return new KshUserDetails(user);
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }
}
