package com.ksh.features.practice.manage;

import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.manage.controller.PracticeAssessmentExcelController;
import com.ksh.features.practice.manage.service.PracticeAssessmentExcelService;
import com.ksh.security.KshUserDetails;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PracticeAssessmentExcelControllerTest {

    private final PracticeAssessmentExcelService excelService = mock(PracticeAssessmentExcelService.class);
    private final PracticeAssessmentExcelController controller = new PracticeAssessmentExcelController(
            excelService, mock(AssessmentAuthoringCatalogService.class));
    private final KshUserDetails user = mock(KshUserDetails.class);
    private final MockMultipartFile file = new MockMultipartFile(
            "file", "questions.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            new byte[]{1});

    @Test
    void previewReturnsJson404WhenLinkedDraftIsMissingOrOwnedByAnotherUser() {
        when(user.getId()).thenReturn(9L);
        when(excelService.requireExcelImportContext(7L, 9L, 1, "R1"))
                .thenThrow(new EntityNotFoundException("Bản nháp liên kết không tồn tại."));

        ResponseEntity<?> response = controller.preview(file, "CUSTOM_FLEXIBLE", 7L, 1, "R1", user);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Bản nháp liên kết không tồn tại.", error(response));
    }

    @Test
    void importReturnsJson403WhenServiceRejectsAccess() {
        when(user.getId()).thenReturn(9L);
        when(excelService.requireExcelImportContext(7L, 9L, 1, "R1"))
                .thenThrow(new AccessDeniedException("forbidden"));

        ResponseEntity<?> response = controller.importDraft(
                file, "CUSTOM_FLEXIBLE", 7L, 1, "R1", null, user);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Bạn không có quyền nhập dữ liệu vào bản nháp này.", error(response));
    }

    private static String error(ResponseEntity<?> response) {
        return String.valueOf(((Map<?, ?>) response.getBody()).get("error"));
    }
}
