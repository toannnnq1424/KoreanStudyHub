package com.ksh.features.practice.manage;

import com.ksh.features.practice.manage.controller.PracticeImportController;
import com.ksh.features.practice.manage.service.PracticeImportTargetService;
import com.ksh.security.KshUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeImportControllerTest {

    @Test
    void opensOnlyAnOwnedTargetAwareBasicImport() {
        PracticeImportTargetService targetService =
                mock(PracticeImportTargetService.class);
        KshUserDetails user = mock(KshUserDetails.class);
        when(user.getId()).thenReturn(42L);
        PracticeImportTargetService.TargetSectionOption selected =
                new PracticeImportTargetService.TargetSectionOption(
                        2, "W2", "WRITING", "Phần Viết");
        PracticeImportTargetService.ImportStartContext context =
                new PracticeImportTargetService.ImportStartContext(
                        selected, List.of(selected));
        when(targetService.resolveStartContext(15L, 2, "WRITING", "W2", 42L))
                .thenReturn(context);

        ExtendedModelMap model = new ExtendedModelMap();
        String view = new PracticeImportController(targetService)
                .showImportStartPage(15L, 2, "WRITING", "W2", user, model);

        assertEquals("practice/manage/import-wizard", view);
        assertEquals(15L, model.get("draftId"));
        assertEquals(context, model.get("pdfImportContext"));
        assertFalse(model.containsAttribute("recentSessions"));
        verify(targetService).resolveStartContext(
                15L, 2, "WRITING", "W2", 42L);
    }

    @Test
    void staleTargetIsAStableBadRequestInsteadOfAWorkspaceErrorPage() {
        PracticeImportTargetService targetService =
                mock(PracticeImportTargetService.class);
        KshUserDetails user = mock(KshUserDetails.class);
        when(user.getId()).thenReturn(42L);
        when(targetService.resolveStartContext(
                15L, 9, "READING", "R9", 42L))
                .thenThrow(new IllegalArgumentException("Target không khớp"));

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> new PracticeImportController(targetService)
                        .showImportStartPage(
                                15L, 9, "READING", "R9", user,
                                new ExtendedModelMap()));

        assertEquals(400, failure.getStatusCode().value());
    }
}
