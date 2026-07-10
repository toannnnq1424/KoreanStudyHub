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
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        ReflectionTestUtils.setField(controller, "rawUploadDir", "target/test-practice-uploads");
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
}
