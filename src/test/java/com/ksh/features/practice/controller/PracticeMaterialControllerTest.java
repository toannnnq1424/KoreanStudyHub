package com.ksh.features.practice.controller;

import com.ksh.features.practice.manage.service.PracticeMaterialAccessService;
import com.ksh.security.AuthenticatedUserIdResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeMaterialControllerTest {

    private final PracticeMaterialAccessService accessService =
            mock(PracticeMaterialAccessService.class);
    private final AuthenticatedUserIdResolver userIdResolver =
            mock(AuthenticatedUserIdResolver.class);
    private final Authentication authentication = mock(Authentication.class);

    private PracticeMaterialController controller;

    @BeforeEach
    void setUp() {
        controller = new PracticeMaterialController(accessService, userIdResolver);
        when(userIdResolver.resolve(authentication)).thenReturn(99L);
    }

    @Test
    void requestWithoutRangeStreamsFullAuthorizedMaterial() throws Exception {
        when(accessService.load(7L, 99L)).thenReturn(content(
                new ByteArrayResource(new byte[]{1, 2, 3, 4}), 4L));

        ResponseEntity<StreamingResponseBody> response =
                controller.content(7L, null, authentication);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(4L);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES))
                .isEqualTo("bytes");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isNull();
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
                .contains("private");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("inline");
        assertThat(output.toByteArray()).containsExactly(1, 2, 3, 4);
    }

    @Test
    void validSingleRangeReturnsPartialContent() throws Exception {
        when(accessService.load(7L, 99L)).thenReturn(content(
                new ByteArrayResource(new byte[]{1, 2, 3, 4}), 4L));

        ResponseEntity<StreamingResponseBody> response =
                controller.content(7L, "bytes=1-2", authentication);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE))
                .isEqualTo("bytes 1-2/4");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(2L);
        assertThat(output.toByteArray()).containsExactly(2, 3);
    }

    @Test
    void invalidOrMultipleRangeReturns416WithoutOpeningResourceStream()
            throws Exception {
        Resource resource = mock(Resource.class);
        when(accessService.load(7L, 99L)).thenReturn(content(resource, 4L));

        for (String range : java.util.List.of(
                "bytes=4-", "bytes=3-1", "bytes=0-1,2-3", "items=0-1")) {
            ResponseEntity<StreamingResponseBody> response =
                    controller.content(7L, range, authentication);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            response.getBody().writeTo(output);

            assertThat(response.getStatusCode())
                    .isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
            assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE))
                    .isEqualTo("bytes */4");
            assertThat(response.getHeaders().getContentLength()).isZero();
            assertThat(output.toByteArray()).isEmpty();
        }

        verify(resource, never()).getInputStream();
    }

    @Test
    void authorizationFailureOccursBeforeAnyResourceCanBeOpened() throws Exception {
        when(accessService.load(7L, 99L))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> controller.content(7L, "bytes=0-0", authentication));

        verify(accessService).load(7L, 99L);
    }

    private static PracticeMaterialAccessService.MaterialContent content(
            Resource resource, Long size) {
        return new PracticeMaterialAccessService.MaterialContent(
                resource, "audio/mpeg", "bài-nghe.mp3", size);
    }
}
