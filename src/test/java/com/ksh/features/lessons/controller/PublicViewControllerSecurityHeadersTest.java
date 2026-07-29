package com.ksh.features.lessons.controller;

import com.ksh.features.lessons.service.PublicViewTokenService;
import com.ksh.features.lessons.service.PublicViewTokenService.AttachmentHandle;
import com.ksh.features.storage.ObjectStorage;
import com.ksh.features.storage.StoredObject;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicViewControllerSecurityHeadersTest {

    @Test
    void successfulPublicAttachmentIsPrivateNoStoreAndNoReferrer() throws Exception {
        PublicViewTokenService tokens = mock(PublicViewTokenService.class);
        ObjectStorage storage = mock(ObjectStorage.class);
        when(tokens.resolve("valid")).thenReturn(
                new AttachmentHandle("lessons/1/file.pdf", "file.pdf", "application/pdf", 3));
        when(storage.exists("lessons/1/file.pdf")).thenReturn(true);
        StoredObject object = new StoredObject(
                new ByteArrayInputStream(new byte[]{1, 2, 3}), 3L, "application/pdf");
        when(storage.open("lessons/1/file.pdf")).thenReturn(object);

        ResponseEntity<?> response = new PublicViewController(tokens, storage).view("valid");

        assertProtected(response);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void invalidBearerUrlIsAlsoPrivateNoStoreAndNoReferrer() {
        PublicViewTokenService tokens = mock(PublicViewTokenService.class);
        ObjectStorage storage = mock(ObjectStorage.class);
        when(tokens.resolve("invalid")).thenThrow(new EntityNotFoundException("Invalid token"));

        ResponseEntity<?> response = new PublicViewController(tokens, storage).view("invalid");

        assertProtected(response);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    private static void assertProtected(ResponseEntity<?> response) {
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("private, no-store");
        assertThat(response.getHeaders().getFirst("Referrer-Policy")).isEqualTo("no-referrer");
    }
}
