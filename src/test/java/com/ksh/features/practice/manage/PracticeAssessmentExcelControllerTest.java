package com.ksh.features.practice.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.manage.controller.PracticeAssessmentExcelController;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateState;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateView;
import com.ksh.features.practice.manage.service.PracticeAssessmentExcelService;
import com.ksh.security.KshUserDetails;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PracticeAssessmentExcelControllerTest {

    private final PracticeAssessmentExcelService excelService = mock(PracticeAssessmentExcelService.class);
    private final PracticeAssessmentExcelController controller = new PracticeAssessmentExcelController(excelService);
    private final KshUserDetails user = mock(KshUserDetails.class);
    private final MockMultipartFile file = new MockMultipartFile(
            "file", "questions.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            new byte[]{1});

    @Test
    void previewReturnsJson404WhenLinkedDraftIsMissingOrOwnedByAnotherUser() {
        when(user.getId()).thenReturn(9L);
        when(excelService.requireExcelImportContext(
                7L, 9L, 1, "READING", "R1"))
                .thenThrow(new EntityNotFoundException("Bản nháp liên kết không tồn tại."));

        ResponseEntity<?> response = controller.preview(
                file, 7L, 1, "READING", "R1", user);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Bản nháp liên kết không tồn tại.", error(response));
    }

    @Test
    void importReturnsJson403WhenServiceRejectsAccess() {
        when(user.getId()).thenReturn(9L);
        when(excelService.requireExcelImportContext(
                7L, 9L, 1, "READING", "R1"))
                .thenThrow(new AccessDeniedException("forbidden"));

        ResponseEntity<?> response = controller.createCandidate(
                file, 7L, 1, "READING", "R1", null, user);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Bạn không có quyền nhập dữ liệu vào bản nháp này.", error(response));
    }

    @Test
    void importEndpointReturnsCandidateHandoffWithoutDraftRedirect() {
        when(user.getId()).thenReturn(9L);
        PracticeDraft draft = mock(PracticeDraft.class);
        when(draft.getId()).thenReturn(7L);
        PracticeAssessmentExcelService.ExcelImportContext context =
                new PracticeAssessmentExcelService.ExcelImportContext(
                        draft, 1, "R1", "READING");
        when(excelService.requireExcelImportContext(
                7L, 9L, 1, "READING", "R1"))
                .thenReturn(context);
        when(excelService.createCandidate(file, context, 9L, null))
                .thenReturn(new CandidateView(
                        "11111111-1111-4111-8111-111111111111",
                        CandidateState.REVIEWING,
                        0,
                        "sha256:" + "a".repeat(64),
                        new ObjectMapper().createObjectNode(),
                        List.of()));

        ResponseEntity<?> response = controller.createCandidate(
                file, 7L, 1, "READING", "R1", null, user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("11111111-1111-4111-8111-111111111111",
                body.get("candidateId"));
        assertEquals("REVIEWING", body.get("state"));
        assertEquals(
                "/practice/manage/authoring-candidates/11111111-1111-4111-8111-111111111111",
                body.get("reviewUrl"));
        assertFalse(body.containsKey("draftId"));
        assertFalse(body.containsKey("redirectUrl"));
    }

    private static String error(ResponseEntity<?> response) {
        return String.valueOf(((Map<?, ?>) response.getBody()).get("error"));
    }
}
