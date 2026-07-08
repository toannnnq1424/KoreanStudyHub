package com.ksh.features.practice.controller;

import com.ksh.entities.PracticeSpeakingMediaStatus;
import com.ksh.entities.User;
import com.ksh.features.practice.service.SpeakingAudioUploadService;
import com.ksh.features.practice.service.audio.SpeakingAudioValidationCategory;
import com.ksh.features.practice.service.audio.SpeakingAudioValidationException;
import com.ksh.security.CustomOidcUserPrincipal;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.practice.speaking-media.upload-api-enabled=true")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class PracticeSpeakingMediaControllerTest {

    private static final String ROUTE = "/practice/attempts/10/questions/20/speaking-media";
    private static final String DELETE_ROUTE = "/practice/attempts/10/questions/20/speaking-media/30";
    private static final String SECRET_KEY = "LEARNER_AUDIO_STORAGE_KEY_SECRET_B2B";
    private static final String SECRET_HASH = "LEARNER_AUDIO_HASH_SECRET_B2B";
    private static final String SECRET_PATH = "LEARNER_AUDIO_PATH_SECRET_B2B";
    private static final String SECRET_FILENAME = "LEARNER_AUDIO_FILENAME_SECRET_B2B";
    private static final String SECRET_PARSER = "LEARNER_AUDIO_PARSER_SECRET_B2B";
    private static final String SECRET_USER = "LEARNER_AUDIO_USER_SECRET_B2B";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SpeakingAudioUploadService uploadService;

    @Test
    void postWithFormPrincipalUploadsSingleFileAndReturnsSafeDtoWithNoStore() throws Exception {
        when(uploadService.uploadOrReplaceForOwner(eq(77L), eq(10L), eq(20L), any(InputStream.class),
                eq(4L), eq("audio/webm"))).thenReturn(uploadResult());

        TrackingMockMultipartFile file = trackingFile("file", "audio/webm", new byte[]{1, 2, 3, 4});

        mockMvc.perform(multipart(ROUTE)
                        .file(file)
                        .with(authentication(formAuthentication(77L)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.*", hasSize(10)))
                .andExpect(jsonPath("$.mediaId").value(30))
                .andExpect(jsonPath("$.attemptId").value(10))
                .andExpect(jsonPath("$.questionId").value(20))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.byteSize").value(4))
                .andExpect(jsonPath("$.durationMs").value(1234))
                .andExpect(jsonPath("$.mimeType").value("audio/webm"))
                .andExpect(jsonPath("$.playbackPath").value("/practice/attempts/10/questions/20/speaking-media/30/content"))
                .andExpect(jsonPath("$.lockVersion").value(5))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(jsonPath("$.contentHash").doesNotExist())
                .andExpect(jsonPath("$.path").doesNotExist())
                .andExpect(jsonPath("$.filename").doesNotExist())
                .andExpect(content().string(not(containsString(SECRET_KEY))))
                .andExpect(content().string(not(containsString(SECRET_HASH))))
                .andExpect(content().string(not(containsString(SECRET_PATH))))
                .andExpect(content().string(not(containsString(SECRET_FILENAME))));

        assertThat(file.wasClosed()).isTrue();
        verify(uploadService).uploadOrReplaceForOwner(eq(77L), eq(10L), eq(20L), any(InputStream.class),
                eq(4L), eq("audio/webm"));
    }

    @Test
    void postWithOidcPrincipalUsesLocalUserId() throws Exception {
        when(uploadService.uploadOrReplaceForOwner(eq(88L), eq(10L), eq(20L), any(InputStream.class),
                eq(2L), eq("audio/webm"))).thenReturn(uploadResult());

        mockMvc.perform(multipart(ROUTE)
                        .file(file("file", "audio/webm", new byte[]{1, 2}))
                        .with(authentication(oidcAuthentication(88L)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaId").value(30));

        verify(uploadService).uploadOrReplaceForOwner(eq(88L), eq(10L), eq(20L), any(InputStream.class),
                eq(2L), eq("audio/webm"));
    }

    @Test
    void postWithoutCsrfIsForbiddenBeforeServiceCall() throws Exception {
        mockMvc.perform(multipart(ROUTE)
                        .file(file("file", "audio/webm", new byte[]{1}))
                        .with(authentication(formAuthentication(77L))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(uploadService);
    }

    @Test
    void postAnonymousRedirectsToLoginBeforeServiceCall() throws Exception {
        mockMvc.perform(multipart(ROUTE)
                        .file(file("file", "audio/webm", new byte[]{1}))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        verifyNoInteractions(uploadService);
    }

    @Test
    void postRejectsUnsupportedPrincipalWithBoundedJson() throws Exception {
        mockMvc.perform(multipart(ROUTE)
                        .file(file("file", "audio/webm", new byte[]{1}))
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                "student@ksh.edu.vn",
                                "n/a",
                                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")))))
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Authentication is required."));

        verifyNoInteractions(uploadService);
    }

    @Test
    void postRejectsMissingFileWithBoundedJson() throws Exception {
        mockMvc.perform(multipart(ROUTE)
                        .with(authentication(formAuthentication(77L)))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MULTIPART"))
                .andExpect(jsonPath("$.message").value("Exactly one learner audio file is required."));

        verifyNoInteractions(uploadService);
    }

    @Test
    void postRejectsDuplicateFilePartsWithoutSelectingFirst() throws Exception {
        mockMvc.perform(multipart(ROUTE)
                        .file(file("file", "audio/webm", new byte[]{1}))
                        .file(file("file", "audio/webm", new byte[]{2}))
                        .with(authentication(formAuthentication(77L)))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MULTIPART"));

        verifyNoInteractions(uploadService);
    }

    @Test
    void postRejectsAdditionalFilePartWithDifferentName() throws Exception {
        mockMvc.perform(multipart(ROUTE)
                        .file(file("file", "audio/webm", new byte[]{1}))
                        .file(file("other", "audio/webm", new byte[]{2}))
                        .with(authentication(formAuthentication(77L)))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MULTIPART"));

        verifyNoInteractions(uploadService);
    }

    @Test
    void postRejectsOnlyDifferentFileFieldName() throws Exception {
        mockMvc.perform(multipart(ROUTE)
                        .file(file("otherAudio", "audio/webm", new byte[]{1}))
                        .with(authentication(formAuthentication(77L)))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MULTIPART"));

        verifyNoInteractions(uploadService);
    }

    @Test
    void postAllowsUnrelatedNonFileField() throws Exception {
        when(uploadService.uploadOrReplaceForOwner(eq(77L), eq(10L), eq(20L), any(InputStream.class),
                eq(1L), eq("audio/webm"))).thenReturn(uploadResult());

        mockMvc.perform(multipart(ROUTE)
                        .file(file("file", "audio/webm", new byte[]{1}))
                        .param("note", "ignored")
                        .with(authentication(formAuthentication(77L)))
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(uploadService).uploadOrReplaceForOwner(eq(77L), eq(10L), eq(20L), any(InputStream.class),
                eq(1L), eq("audio/webm"));
    }

    @Test
    void postRejectsEmptyFileAsValidationError() throws Exception {
        mockMvc.perform(multipart(ROUTE)
                        .file(file("file", "audio/webm", new byte[]{}))
                        .with(authentication(formAuthentication(77L)))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY"))
                .andExpect(jsonPath("$.message").value("Audio file is empty."));

        verifyNoInteractions(uploadService);
    }

    @Test
    void postMapsBusinessTooLargeTo413() throws Exception {
        when(uploadService.uploadOrReplaceForOwner(eq(77L), eq(10L), eq(20L), any(InputStream.class),
                eq(1L), eq("audio/webm"))).thenThrow(validation(SpeakingAudioValidationCategory.TOO_LARGE));

        mockMvc.perform(validPost())
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("Audio file is too large."));
    }

    @ParameterizedTest
    @EnumSource(SpeakingAudioValidationCategory.class)
    void mapsEveryValidationCategoryExplicitly(SpeakingAudioValidationCategory category) throws Exception {
        when(uploadService.uploadOrReplaceForOwner(eq(77L), eq(10L), eq(20L), any(InputStream.class),
                eq(1L), eq("audio/webm"))).thenThrow(validation(category));

        mockMvc.perform(validPost())
                .andExpect(status().is(expectedStatus(category)))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(jsonPath("$.code").value(category.name()))
                .andExpect(jsonPath("$.message").value(expectedMessage(category)))
                .andExpect(content().string(not(containsString(SECRET_PARSER))));
    }

    @Test
    void postMapsUnsupportedFormatTo415() throws Exception {
        when(uploadService.uploadOrReplaceForOwner(eq(77L), eq(10L), eq(20L), any(InputStream.class),
                eq(1L), eq("audio/webm"))).thenThrow(validation(SpeakingAudioValidationCategory.UNSUPPORTED_CODEC));

        mockMvc.perform(validPost())
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CODEC"))
                .andExpect(jsonPath("$.message").value("Audio format is not supported."));
    }

    @Test
    void postMapsCorruptMediaTo400() throws Exception {
        when(uploadService.uploadOrReplaceForOwner(eq(77L), eq(10L), eq(20L), any(InputStream.class),
                eq(1L), eq("audio/webm"))).thenThrow(validation(SpeakingAudioValidationCategory.CORRUPT_MEDIA));

        mockMvc.perform(validPost())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CORRUPT_MEDIA"))
                .andExpect(jsonPath("$.message").value("Audio file could not be processed."));
    }

    @Test
    void postMapsProbeUnavailableTo503() throws Exception {
        when(uploadService.uploadOrReplaceForOwner(eq(77L), eq(10L), eq(20L), any(InputStream.class),
                eq(1L), eq("audio/webm"))).thenThrow(validation(SpeakingAudioValidationCategory.PROBE_UNAVAILABLE));

        mockMvc.perform(validPost())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PROBE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Audio service is temporarily unavailable."));
    }

    @Test
    void postMapsStorageFailureTo503WithoutLeakingExceptionMessage() throws Exception {
        when(uploadService.uploadOrReplaceForOwner(eq(77L), eq(10L), eq(20L), any(InputStream.class),
                eq(1L), eq("audio/webm"))).thenThrow(new SpeakingAudioValidationException(
                SpeakingAudioValidationCategory.STORAGE_FAILURE,
                SECRET_KEY + " " + SECRET_PATH));

        mockMvc.perform(validPost())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STORAGE_FAILURE"))
                .andExpect(content().string(not(containsString(SECRET_KEY))))
                .andExpect(content().string(not(containsString(SECRET_PATH))));
    }

    @Test
    void postMapsNotFoundTo404WithoutOwnershipDetail() throws Exception {
        when(uploadService.uploadOrReplaceForOwner(eq(77L), eq(10L), eq(20L), any(InputStream.class),
                eq(1L), eq("audio/webm"))).thenThrow(new EntityNotFoundException("wrong owner " + SECRET_HASH));

        mockMvc.perform(validPost())
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Speaking media target was not found."))
                .andExpect(content().string(not(containsString(SECRET_HASH))))
                .andExpect(content().string(not(containsString("<html"))));
    }

    @Test
    void postMapsConflictTo409WithoutStateDetail() throws Exception {
        when(uploadService.uploadOrReplaceForOwner(eq(77L), eq(10L), eq(20L), any(InputStream.class),
                eq(1L), eq("audio/webm"))).thenThrow(new IllegalStateException("COMPLETED " + SECRET_PARSER));

        mockMvc.perform(validPost())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Speaking media cannot be changed in the current state."))
                .andExpect(content().string(not(containsString(SECRET_PARSER))));
    }

    @Test
    void postMapsMaxUploadSizeExceededTo413() throws Exception {
        when(uploadService.uploadOrReplaceForOwner(eq(77L), eq(10L), eq(20L), any(InputStream.class),
                eq(1L), eq("audio/webm"))).thenThrow(new MaxUploadSizeExceededException(100L));

        mockMvc.perform(validPost())
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("Audio file is too large."));
    }

    @Test
    void postMapsMultipartExceptionTo400() throws Exception {
        when(uploadService.uploadOrReplaceForOwner(eq(77L), eq(10L), eq(20L), any(InputStream.class),
                eq(1L), eq("audio/webm"))).thenThrow(new MultipartException(SECRET_PARSER));

        mockMvc.perform(validPost())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MULTIPART"))
                .andExpect(content().string(not(containsString(SECRET_PARSER))));
    }

    @Test
    void postMapsInputStreamOpenFailureToBounded400() throws Exception {
        mockMvc.perform(multipart(ROUTE)
                        .file(new ThrowingInputStreamMockMultipartFile())
                        .with(authentication(formAuthentication(77L)))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_UNAVAILABLE"))
                .andExpect(content().string(not(containsString(SECRET_PATH))));

        verifyNoInteractions(uploadService);
    }

    @Test
    void postClosesStreamWhenServiceThrowsValidationException() throws Exception {
        when(uploadService.uploadOrReplaceForOwner(eq(77L), eq(10L), eq(20L), any(InputStream.class),
                eq(1L), eq("audio/webm"))).thenThrow(validation(SpeakingAudioValidationCategory.CORRUPT_MEDIA));
        TrackingMockMultipartFile file = trackingFile("file", "audio/webm", new byte[]{1});

        mockMvc.perform(multipart(ROUTE)
                        .file(file)
                        .with(authentication(formAuthentication(77L)))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        assertThat(file.wasClosed()).isTrue();
    }

    @Test
    void postClosesStreamWhenServiceThrowsRuntimeException() throws Exception {
        when(uploadService.uploadOrReplaceForOwner(eq(77L), eq(10L), eq(20L), any(InputStream.class),
                eq(1L), eq("audio/webm"))).thenThrow(new RuntimeException(SECRET_KEY));
        TrackingMockMultipartFile file = trackingFile("file", "audio/webm", new byte[]{1});

        mockMvc.perform(multipart(ROUTE)
                        .file(file)
                        .with(authentication(formAuthentication(77L)))
                        .with(csrf()))
                .andExpect(status().isInternalServerError());

        assertThat(file.wasClosed()).isTrue();
    }

    @Test
    void postMapsUnexpectedFailureToBounded500WithoutGlobalHtmlOrSecretLog(CapturedOutput output) throws Exception {
        when(uploadService.uploadOrReplaceForOwner(eq(77L), eq(10L), eq(20L), any(InputStream.class),
                eq(1L), eq("audio/webm"))).thenThrow(new RuntimeException(SECRET_KEY + SECRET_USER));

        mockMvc.perform(validPost())
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(jsonPath("$.code").value("UNEXPECTED_ERROR"))
                .andExpect(jsonPath("$.message").value("Speaking media request failed."))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string(not(containsString(SECRET_KEY))))
                .andExpect(content().string(not(containsString(SECRET_USER))))
                .andExpect(content().string(not(containsString("<html"))));

        assertThat(output.getOut()).doesNotContain(SECRET_KEY);
        assertThat(output.getOut()).doesNotContain(SECRET_USER);
        assertThat(output.getErr()).doesNotContain(SECRET_KEY);
        assertThat(output.getErr()).doesNotContain(SECRET_USER);
    }

    @Test
    void malformedMultipartBodyReturnsBoundedJsonWhenDispatcherReachesControllerAdvice() throws Exception {
        mockMvc.perform(post(ROUTE)
                        .contentType("multipart/form-data; boundary=bad")
                        .content("--not-a-complete-part")
                        .with(authentication(formAuthentication(77L)))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_MULTIPART"))
                .andExpect(content().string(not(containsString(SECRET_PARSER))))
                .andExpect(content().string(not(containsString("<html"))));

        verifyNoInteractions(uploadService);
    }

    @Test
    void deleteWithFormPrincipalReturnsSafeDtoAndNoStore() throws Exception {
        when(uploadService.deleteForOwner(77L, 10L, 20L, 30L)).thenReturn(deleteResult(PracticeSpeakingMediaStatus.DELETED));

        mockMvc.perform(delete(DELETE_ROUTE)
                        .with(authentication(formAuthentication(77L)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.*", hasSize(6)))
                .andExpect(jsonPath("$.mediaId").value(30))
                .andExpect(jsonPath("$.attemptId").value(10))
                .andExpect(jsonPath("$.questionId").value(20))
                .andExpect(jsonPath("$.status").value("DELETED"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.pendingCleanup").value(true))
                .andExpect(jsonPath("$.storageKey").doesNotExist());
    }

    @Test
    void deleteAlreadyDeletedSafeResultStillReturns200() throws Exception {
        when(uploadService.deleteForOwner(77L, 10L, 20L, 30L)).thenReturn(deleteResult(PracticeSpeakingMediaStatus.DELETED));

        mockMvc.perform(delete(DELETE_ROUTE)
                        .with(authentication(formAuthentication(77L)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELETED"));
    }

    @Test
    void deleteWithoutCsrfIsForbiddenBeforeServiceCall() throws Exception {
        mockMvc.perform(delete(DELETE_ROUTE)
                        .with(authentication(formAuthentication(77L))))
                .andExpect(status().isForbidden());

        verify(uploadService, never()).deleteForOwner(any(), any(), any(), any());
    }

    @Test
    void deleteAnonymousRedirectsToLoginBeforeServiceCall() throws Exception {
        mockMvc.perform(delete(DELETE_ROUTE)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(uploadService, never()).deleteForOwner(any(), any(), any(), any());
    }

    @Test
    void deleteMapsNotFoundTo404ForCrossUserOrScopeMismatch() throws Exception {
        when(uploadService.deleteForOwner(77L, 10L, 20L, 30L)).thenThrow(new EntityNotFoundException("secret owner"));

        mockMvc.perform(delete(DELETE_ROUTE)
                        .with(authentication(formAuthentication(77L)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Speaking media target was not found."));
    }

    @Test
    void deleteMapsConflictTo409() throws Exception {
        when(uploadService.deleteForOwner(77L, 10L, 20L, 30L)).thenThrow(new IllegalStateException("completed"));

        mockMvc.perform(delete(DELETE_ROUTE)
                        .with(authentication(formAuthentication(77L)))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    private RequestBuilder validPost() {
        return multipart(ROUTE)
                .file(file("file", "audio/webm", new byte[]{1}))
                .with(authentication(formAuthentication(77L)))
                .with(csrf());
    }

    private static MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile(name, SECRET_FILENAME, contentType, content);
    }

    private static SpeakingAudioValidationException validation(SpeakingAudioValidationCategory category) {
        return new SpeakingAudioValidationException(category, SECRET_PARSER);
    }

    private static int expectedStatus(SpeakingAudioValidationCategory category) {
        return switch (category) {
            case EMPTY, TOO_LONG, CORRUPT_MEDIA, PROBE_OUTPUT_TOO_LARGE -> 400;
            case TOO_LARGE -> 413;
            case UNSUPPORTED_TYPE, INVALID_CONTAINER, UNSUPPORTED_CODEC,
                 MULTIPLE_AUDIO_STREAMS, NON_AUDIO_STREAM_PRESENT -> 415;
            case PROBE_UNAVAILABLE, PROBE_TIMEOUT, STORAGE_FAILURE -> 503;
        };
    }

    private static String expectedMessage(SpeakingAudioValidationCategory category) {
        return switch (category) {
            case EMPTY -> "Audio file is empty.";
            case TOO_LARGE -> "Audio file is too large.";
            case TOO_LONG -> "Audio file is too long.";
            case UNSUPPORTED_TYPE, INVALID_CONTAINER, UNSUPPORTED_CODEC,
                 MULTIPLE_AUDIO_STREAMS, NON_AUDIO_STREAM_PRESENT -> "Audio format is not supported.";
            case CORRUPT_MEDIA, PROBE_OUTPUT_TOO_LARGE -> "Audio file could not be processed.";
            case PROBE_UNAVAILABLE, PROBE_TIMEOUT, STORAGE_FAILURE -> "Audio service is temporarily unavailable.";
        };
    }

    private static SpeakingAudioUploadService.SpeakingAudioUploadResult uploadResult() {
        return new SpeakingAudioUploadService.SpeakingAudioUploadResult(
                30L,
                10L,
                20L,
                PracticeSpeakingMediaStatus.READY,
                4L,
                1234L,
                "audio/webm",
                5L);
    }

    private static SpeakingAudioUploadService.SpeakingAudioDeletionResult deleteResult(
            PracticeSpeakingMediaStatus status) {
        return new SpeakingAudioUploadService.SpeakingAudioDeletionResult(30L, 10L, 20L, status, true);
    }

    private static UsernamePasswordAuthenticationToken formAuthentication(Long userId) {
        KshUserDetails principal = new KshUserDetails(user(userId));
        return new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities());
    }

    private static UsernamePasswordAuthenticationToken oidcAuthentication(Long userId) {
        CustomOidcUserPrincipal principal = new CustomOidcUserPrincipal(mock(OidcUser.class), user(userId));
        return new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities());
    }

    private static User user(Long id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(Role.STUDENT);
        when(user.getEmail()).thenReturn("student-" + id + "@ksh.edu.vn");
        when(user.getPasswordHash()).thenReturn("encoded");
        when(user.getFullName()).thenReturn("Student " + id);
        when(user.isActive()).thenReturn(true);
        when(user.isLocked()).thenReturn(false);
        return user;
    }

    private static TrackingMockMultipartFile trackingFile(String name, String contentType, byte[] content) {
        return new TrackingMockMultipartFile(name, SECRET_FILENAME, contentType, content);
    }

    private static final class TrackingMockMultipartFile extends MockMultipartFile {
        private final byte[] content;
        private TrackingInputStream stream;

        private TrackingMockMultipartFile(String name, String suppliedName, String contentType, byte[] content) {
            super(name, suppliedName, contentType, content);
            this.content = content.clone();
        }

        @Override
        public InputStream getInputStream() {
            stream = new TrackingInputStream(content);
            return stream;
        }

        boolean wasClosed() {
            return stream != null && stream.closed;
        }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInputStream(byte[] buf) {
            super(buf);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class ThrowingInputStreamMockMultipartFile extends MockMultipartFile {
        private ThrowingInputStreamMockMultipartFile() {
            super("file", SECRET_FILENAME, "audio/webm", new byte[]{1});
        }

        @Override
        public InputStream getInputStream() throws IOException {
            throw new IOException(SECRET_PATH);
        }
    }
}
