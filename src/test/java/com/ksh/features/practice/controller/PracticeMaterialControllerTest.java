package com.ksh.features.practice.controller;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeSet;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.manage.service.AssetStorageService;
import com.ksh.features.practice.manage.service.PracticeMaterialAccessService;
import com.ksh.features.practice.manage.service.PracticeMaterialReferenceService;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticePublishedVersionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
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
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
        assertNoStore(response);
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
        assertNoStore(response);
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
            assertNoStore(response);
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

    @Test
    void learnerPublishedReadUsesNonThrowingAuthorizationProbes() throws Exception {
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeMaterialReferenceService references =
                mock(PracticeMaterialReferenceService.class);
        PracticeSetRepository sets = mock(PracticeSetRepository.class);
        PracticeAttemptRepository attempts = mock(PracticeAttemptRepository.class);
        PracticePublishedVersionRepository versions =
                mock(PracticePublishedVersionRepository.class);
        EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
        PracticeAuthorizationService authorization =
                mock(PracticeAuthorizationService.class);
        AssetStorageService storage = mock(AssetStorageService.class);
        PracticeMaterialAccessService materialAccess =
                new PracticeMaterialAccessService(
                        assets, references, sets, attempts, versions,
                        enrollments, authorization, storage);

        LecturerAsset asset = new LecturerAsset();
        asset.setId(7L);
        asset.setOwnerLecturerId(11L);
        asset.setStorageProvider("LOCAL");
        asset.setStorageProfileCode("PRACTICE_AUTHORING");
        asset.setStorageKey("private/prompt.mp3");
        asset.setOriginalFilename("prompt.mp3");
        asset.setMimeType("audio/mpeg");
        asset.setFileSize(4L);
        asset.setAssetType("AUDIO");
        asset.setStatus("ACTIVE");
        asset.setContentVerified(true);
        PracticeMaterialReference draftReference =
                PracticeMaterialReference.draft(7L, 20L, "SPEAKING_PROMPT_ORIGINAL");
        PracticeMaterialReference publishedReference =
                PracticeMaterialReference.published(
                        7L, 44L, 55L, "SPEAKING_PROMPT_ORIGINAL");
        PracticeSet publishedSet = new PracticeSet(
                "Speaking", "", "SPEAKING", PracticeSet.SCOPE_GLOBAL,
                null, null, "{}", PracticeSet.STATUS_PUBLISHED, 11L);
        PracticePublishedVersion publishedVersion = mock(PracticePublishedVersion.class);

        when(assets.findById(7L)).thenReturn(Optional.of(asset));
        when(references.references(7L))
                .thenReturn(List.of(draftReference, publishedReference));
        when(authorization.canReadDraft(20L, 99L)).thenReturn(false);
        when(authorization.canReadSet(44L, 99L)).thenReturn(false);
        when(sets.findById(44L)).thenReturn(Optional.of(publishedSet));
        when(versions.findFirstBySetIdAndStatusOrderByVersionNumberDesc(
                44L, PracticePublishedVersion.STATUS_PUBLISHED))
                .thenReturn(Optional.of(publishedVersion));
        when(publishedVersion.getId()).thenReturn(55L);
        when(storage.load("PRACTICE_AUTHORING", "private/prompt.mp3"))
                .thenReturn(new ByteArrayResource(new byte[]{1, 2, 3, 4}));

        PracticeMaterialAccessService.MaterialContent material =
                materialAccess.load(7L, 99L);

        assertThat(material.mimeType()).isEqualTo("audio/mpeg");
        verify(authorization).canReadDraft(20L, 99L);
        verify(authorization).canReadSet(44L, 99L);
        verify(storage).load("PRACTICE_AUTHORING", "private/prompt.mp3");
    }

    @Test
    void streamingBodyTreatsBrowserAbortAsCompletedResponse() {
        StreamingResponseBody body = PracticeByteRange.body(
                () -> new ByteArrayInputStream(new byte[]{1, 2, 3, 4}),
                new PracticeByteRange.Selection(0L, 3L, false, false));

        assertDoesNotThrow(() -> body.writeTo(new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("Broken pipe");
            }

            @Override
            public void write(byte[] buffer, int offset, int length) throws IOException {
                throw new IOException("Broken pipe");
            }
        }));
    }

    @Test
    void streamingBodyTreatsSpringWrappedBrowserAbortAsCompletedResponse() {
        StreamingResponseBody body = PracticeByteRange.body(
                () -> new ByteArrayInputStream(new byte[]{1, 2, 3, 4}),
                new PracticeByteRange.Selection(0L, 3L, false, false));

        assertDoesNotThrow(() -> body.writeTo(new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new AsyncRequestNotUsableException(
                        "ServletOutputStream failed to write: java.io.IOException: Broken pipe");
            }

            @Override
            public void write(byte[] buffer, int offset, int length) throws IOException {
                throw new AsyncRequestNotUsableException(
                        "ServletOutputStream failed to write: java.io.IOException: Broken pipe");
            }
        }));
    }

    @Test
    void streamingBodyStillSurfacesNonAbortWriteFailure() {
        StreamingResponseBody body = PracticeByteRange.body(
                () -> new ByteArrayInputStream(new byte[]{1, 2, 3, 4}),
                new PracticeByteRange.Selection(0L, 3L, false, false));

        IOException exception = assertThrows(IOException.class,
                () -> body.writeTo(new OutputStream() {
                    @Override
                    public void write(int value) throws IOException {
                        throw new IOException("disk unavailable");
                    }

                    @Override
                    public void write(byte[] buffer, int offset, int length) throws IOException {
                        throw new IOException("disk unavailable");
                    }
                }));

        assertThat(exception).hasMessage("disk unavailable");
    }

    private static void assertNoStore(
            ResponseEntity<StreamingResponseBody> response) {
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
                .contains(
                        "private",
                        "no-store",
                        "must-revalidate");
        assertThat(response.getHeaders().getFirst(HttpHeaders.PRAGMA))
                .isEqualTo("no-cache");
        assertThat(response.getHeaders().getFirst(HttpHeaders.EXPIRES))
                .isEqualTo("0");
    }

    private static PracticeMaterialAccessService.MaterialContent content(
            Resource resource, Long size) {
        return new PracticeMaterialAccessService.MaterialContent(
                resource, "audio/mpeg", "bài-nghe.mp3", size);
    }
}
