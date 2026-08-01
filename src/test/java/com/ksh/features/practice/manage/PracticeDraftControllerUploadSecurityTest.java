package com.ksh.features.practice.manage;

import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.manage.controller.PracticeDraftController;
import com.ksh.features.practice.manage.service.PracticeDraftService;
import com.ksh.features.practice.manage.service.PracticePublisherService;
import com.ksh.features.practice.manage.validator.PracticeDraftValidator;
import com.ksh.security.KshUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PracticeDraftControllerUploadSecurityTest {

    private final PracticeDraftService draftService = mock(PracticeDraftService.class);
    private final KshUserDetails user = mock(KshUserDetails.class);
    private PracticeDraftController controller;

    @BeforeEach
    void setUp() {
        controller = new PracticeDraftController(
                draftService,
                mock(PracticePublisherService.class),
                mock(PracticeDraftValidator.class));
        when(user.getId()).thenReturn(7L);
        when(draftService.getDraft(10L, 7L)).thenReturn(mock(PracticeDraft.class));
    }

    @Test
    void audioUploadRejectsPathLikeExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "track.mp3/../../payload", "audio/mpeg", new byte[]{1});

        ResponseEntity<?> response = controller.uploadAudio(10L, file, user);

        assertEquals(400, response.getStatusCode().value());
        assertFalse(String.valueOf(response.getBody()).contains("../"));
    }

    @Test
    void imageUploadRejectsExecutableOrPathLikeExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png/../../payload.html", "text/html", "<script>".getBytes());

        ResponseEntity<?> response = controller.uploadImage(10L, file, user);

        assertEquals(400, response.getStatusCode().value());
        assertFalse(String.valueOf(response.getBody()).contains("payload.html"));
    }

    @Test
    void publishReturnsExactSafeTypedExplanationPreflightReason() {
        PracticePublisherService publisher =
                mock(PracticePublisherService.class);
        PracticeDraftController publishController =
                new PracticeDraftController(
                        draftService,
                        publisher,
                        mock(PracticeDraftValidator.class));
        when(publisher.publish(10L, 7L)).thenThrow(
                new IllegalStateException(
                        "Câu 1 (Đọc) chưa có lời giải typed đã duyệt."));
        RedirectAttributesModelMap redirect =
                new RedirectAttributesModelMap();

        String destination = publishController.publishDraft(
                10L, user, redirect);

        assertEquals("redirect:/practice/manage/drafts/10", destination);
        assertEquals(
                "Câu 1 (Đọc) chưa có lời giải typed đã duyệt.",
                redirect.getFlashAttributes().get("error"));
        assertFalse(String.valueOf(
                redirect.getFlashAttributes().get("error"))
                .contains("clientId"));
        assertTrue(redirect.getFlashAttributes().containsKey("error"));
    }
}
