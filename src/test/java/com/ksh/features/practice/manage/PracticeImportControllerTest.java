package com.ksh.features.practice.manage;

import com.ksh.entities.PracticePdfImportSession;
import com.ksh.features.practice.manage.controller.PracticeImportController;
import com.ksh.features.practice.manage.service.PracticePdfImportSessionService;
import com.ksh.features.practice.repository.PracticePdfImportSessionRepository;
import com.ksh.security.KshUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PracticeImportControllerTest {

    @Test
    void emptyLinkedDraftFallsBackToStandaloneImportWithActionableWarning() {
        PracticePdfImportSessionService service = mock(PracticePdfImportSessionService.class);
        PracticePdfImportSessionRepository repository =
                mock(PracticePdfImportSessionRepository.class);
        KshUserDetails user = mock(KshUserDetails.class);
        when(user.getId()).thenReturn(42L);
        when(service.resolveStartContext(15L, null, null, 42L))
                .thenThrow(new IllegalArgumentException(
                        "Bản nháp chưa có phần kỹ năng để nhập PDF."));
        when(repository.findByUploaderIdOrderByCreatedAtDesc(42L))
                .thenReturn(List.<PracticePdfImportSession>of());

        ExtendedModelMap model = new ExtendedModelMap();
        String view = new PracticeImportController(service, repository)
                .showImportStartPage(15L, null, null, user, model);

        assertEquals("practice/manage/import-wizard", view);
        assertNull(model.get("draftId"));
        assertNull(model.get("pdfImportContext"));
        assertTrue(String.valueOf(model.get("pdfImportError"))
                .contains("Hãy thêm phần Đọc, Nghe, Viết hoặc Nói"));
    }
}
