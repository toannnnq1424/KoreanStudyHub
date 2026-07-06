package com.ksh.features.practice.controller;

import com.ksh.entities.User;
import com.ksh.features.practice.service.PracticeSpeakingMediaPlaybackNotFoundException;
import com.ksh.features.practice.service.PracticeSpeakingMediaPlaybackService;
import com.ksh.security.AuthenticatedUserIdResolver;
import com.ksh.security.CustomOidcUserPrincipal;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.practice.speaking-media.playback-api-enabled=true")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class PracticeSpeakingMediaPlaybackControllerTest {
    private static final String ROUTE = "/practice/attempts/10/questions/20/speaking-media/30/content";
    private static final String SECRET_KEY = "LEARNER_PLAYBACK_STORAGE_KEY_SECRET_B3B1";
    private static final String SECRET_HASH = "LEARNER_PLAYBACK_HASH_SECRET_B3B1";
    private static final String SECRET_PATH = "C:\\secret\\learner-playback-b3b1.webm";
    private static final String SECRET_FILENAME = "original-secret-b3b1.webm";
    private static final String SECRET_PROVIDER = "SECRET_BUCKET_PROVIDER_B3B1";
    private static final String SECRET_USER = "user-771";
    private static final String SECRET_ATTEMPT = "attempt-10";
    private static final String SECRET_QUESTION = "question-20";
    private static final String SECRET_MEDIA = "media-30";
    private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PracticeSpeakingMediaPlaybackService playbackService;

    @Test
    void formLoginStudentOwnerReceivesFullStreamWithSafeHeaders() throws Exception {
        TrackingInputStream stream = stream(new byte[]{1, 2, 3, 4});
        when(playbackService.openForOwner(77L, 10L, 20L, 30L))
                .thenReturn(new PracticeSpeakingMediaPlaybackService.PlaybackStream("audio/webm", 4L, stream));

        MvcResult result = mockMvc.perform(get(ROUTE)
                        .with(authentication(formAuthentication(77L, Role.STUDENT))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{1, 2, 3, 4}))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("audio/webm")))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 4L))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline"))
                .andExpect(header().string(X_CONTENT_TYPE_OPTIONS, "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("private")))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(HttpHeaders.EXPIRES, "0"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCEPT_RANGES))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(content().string(not(containsString(SECRET_KEY))))
                .andExpect(content().string(not(containsString(SECRET_HASH))))
                .andExpect(content().string(not(containsString(SECRET_PATH))))
                .andExpect(content().string(not(containsString(SECRET_FILENAME))))
                .andExpect(content().string(not(containsString(SECRET_PROVIDER))));

        assertThat(stream.closed).isTrue();
        verify(playbackService).openForOwner(77L, 10L, 20L, 30L);
    }

    @Test
    void oidcStudentOwnerUsesLocalUserId() throws Exception {
        when(playbackService.openForOwner(88L, 10L, 20L, 30L))
                .thenReturn(new PracticeSpeakingMediaPlaybackService.PlaybackStream(
                        "audio/mp4", 2L, stream(new byte[]{8, 9})));

        MvcResult result = mockMvc.perform(get(ROUTE)
                        .with(authentication(oidcAuthentication(88L, Role.STUDENT))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{8, 9}))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("audio/mp4")));

        verify(playbackService).openForOwner(88L, 10L, 20L, 30L);
    }

    @Test
    void rangeHeaderStillReturnsFullResponseWithoutRangeHeaders() throws Exception {
        TransactionObservingInputStream stream = new TransactionObservingInputStream(new byte[]{1, 2, 3, 4});
        when(playbackService.openForOwner(77L, 10L, 20L, 30L))
                .thenReturn(new PracticeSpeakingMediaPlaybackService.PlaybackStream(
                        "audio/webm", 4L, stream));

        MvcResult result = mockMvc.perform(get(ROUTE)
                        .header(HttpHeaders.RANGE, "bytes=1-2")
                        .with(authentication(formAuthentication(77L, Role.STUDENT))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{1, 2, 3, 4}))
                .andExpect(header().doesNotExist(HttpHeaders.ACCEPT_RANGES))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_RANGE));

        assertThat(stream.transactionActiveDuringRead).containsOnly(false);
    }

    @Test
    void streamCopyDoesNotExceedPersistedByteSize() throws Exception {
        TrackingInputStream stream = stream(new byte[]{1, 2, 3, 4, 5});
        when(playbackService.openForOwner(77L, 10L, 20L, 30L))
                .thenReturn(new PracticeSpeakingMediaPlaybackService.PlaybackStream(
                        "audio/webm", 3L, stream));

        MvcResult result = mockMvc.perform(get(ROUTE)
                        .with(authentication(formAuthentication(77L, Role.STUDENT))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{1, 2, 3}))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 3L));

        assertThat(stream.closed).isTrue();
    }

    @Test
    void objectShorterThanPersistedByteSizeTruncatesSafelyAndCloses() throws Exception {
        TrackingInputStream stream = stream(new byte[]{1, 2});
        when(playbackService.openForOwner(77L, 10L, 20L, 30L))
                .thenReturn(new PracticeSpeakingMediaPlaybackService.PlaybackStream(
                        "audio/webm", 4L, stream));

        MvcResult result = mockMvc.perform(get(ROUTE)
                        .with(authentication(formAuthentication(77L, Role.STUDENT))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{1, 2}))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 4L));

        assertThat(stream.closed).isTrue();
    }

    @Test
    void unsupportedPrincipalIsDeniedWithBoundedJson() throws Exception {
        mockMvc.perform(get(ROUTE)
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                "student@ksh.edu.vn",
                                "n/a",
                                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Authentication is required."));

        verifyNoInteractions(playbackService);
    }

    @Test
    void anonymousIsRedirectedBeforeServiceCall() throws Exception {
        mockMvc.perform(get(ROUTE))
                .andExpect(status().is3xxRedirection());

        verifyNoInteractions(playbackService);
    }

    @Test
    void getDoesNotRequireCsrf() throws Exception {
        when(playbackService.openForOwner(77L, 10L, 20L, 30L))
                .thenReturn(new PracticeSpeakingMediaPlaybackService.PlaybackStream(
                        "audio/webm", 1L, stream(new byte[]{1})));

        MvcResult result = mockMvc.perform(get(ROUTE)
                        .with(authentication(formAuthentication(77L, Role.STUDENT))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk());
    }

    @Test
    void postToContentRouteIsNotSupported() throws Exception {
        mockMvc.perform(post(ROUTE)
                        .with(authentication(formAuthentication(77L, Role.STUDENT)))
                        .with(csrf()))
                .andExpect(status().is5xxServerError());

        verify(playbackService, never()).openForOwner(any(), any(), any(), any());
    }

    @Test
    void lecturerHeadAndAdminAreDeniedBeforeServiceCall() throws Exception {
        for (Role role : List.of(Role.LECTURER, Role.HEAD, Role.ADMIN)) {
            mockMvc.perform(get(ROUTE)
                            .with(authentication(formAuthentication(700L + role.ordinal(), role))))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(playbackService);
    }

    @Test
    void missingWrongOwnerMismatchAndUnavailableCollapseToBoundedNotFound(CapturedOutput output) throws Exception {
        when(playbackService.openForOwner(77L, 10L, 20L, 30L))
                .thenThrow(new PracticeSpeakingMediaPlaybackNotFoundException());

        mockMvc.perform(get(ROUTE)
                        .with(authentication(formAuthentication(77L, Role.STUDENT))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Speaking media target was not found."))
                .andExpect(content().string(not(containsString(SECRET_KEY))))
                .andExpect(content().string(not(containsString(SECRET_HASH))))
                .andExpect(content().string(not(containsString(SECRET_PATH))))
                .andExpect(content().string(not(containsString(SECRET_FILENAME))))
                .andExpect(content().string(not(containsString(SECRET_PROVIDER))))
                .andExpect(content().string(not(containsString(SECRET_USER))))
                .andExpect(content().string(not(containsString(SECRET_ATTEMPT))))
                .andExpect(content().string(not(containsString(SECRET_QUESTION))))
                .andExpect(content().string(not(containsString(SECRET_MEDIA))));

        assertThat(output.getOut()).doesNotContain(SECRET_KEY, SECRET_PATH, SECRET_HASH, SECRET_PROVIDER);
        assertThat(output.getErr()).doesNotContain(SECRET_KEY, SECRET_PATH, SECRET_HASH, SECRET_PROVIDER);
    }

    @Test
    void inCallbackReadFailureClosesStreamAndDoesNotLogSecrets(CapturedOutput output) throws Exception {
        FailingReadInputStream stream = new FailingReadInputStream();
        PracticeSpeakingMediaPlaybackService localService = mock(PracticeSpeakingMediaPlaybackService.class);
        when(localService.openForOwner(77L, 10L, 20L, 30L))
                .thenReturn(new PracticeSpeakingMediaPlaybackService.PlaybackStream("audio/webm", 4L, stream));
        PracticeSpeakingMediaPlaybackController controller =
                new PracticeSpeakingMediaPlaybackController(localService, new AuthenticatedUserIdResolver());

        ResponseEntity<StreamingResponseBody> response = controller.content(
                10L, 20L, 30L, formAuthentication(77L, Role.STUDENT));

        assertThat(response.getBody()).isNotNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        response.getBody().writeTo(new OutputStream() {
                            @Override
                            public void write(int b) {
                            }
                        }))
                .isInstanceOf(IOException.class)
                .hasMessageContaining(SECRET_PATH);

        assertThat(stream.closed).isTrue();
        assertThat(output.getOut()).doesNotContain(SECRET_KEY, SECRET_PATH);
        assertThat(output.getErr()).doesNotContain(SECRET_KEY, SECRET_PATH);
    }

    @Test
    void streamClosesWhenOutputCopyFails() throws Exception {
        TrackingInputStream stream = stream(new byte[]{1, 2, 3, 4});
        PracticeSpeakingMediaPlaybackService localService = mock(PracticeSpeakingMediaPlaybackService.class);
        when(localService.openForOwner(77L, 10L, 20L, 30L))
                .thenReturn(new PracticeSpeakingMediaPlaybackService.PlaybackStream("audio/webm", 4L, stream));
        PracticeSpeakingMediaPlaybackController controller =
                new PracticeSpeakingMediaPlaybackController(localService, new AuthenticatedUserIdResolver());

        ResponseEntity<StreamingResponseBody> response = controller.content(
                10L, 20L, 30L, formAuthentication(77L, Role.STUDENT));

        assertThat(response.getBody()).isNotNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        response.getBody().writeTo(new ThrowingOutputStream()))
                .isInstanceOf(IOException.class);
        assertThat(stream.closed).isTrue();
    }

    private static TrackingInputStream stream(byte[] content) {
        return new TrackingInputStream(content);
    }

    private static UsernamePasswordAuthenticationToken formAuthentication(Long userId, Role role) {
        KshUserDetails principal = new KshUserDetails(user(userId, role));
        return new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities());
    }

    private static UsernamePasswordAuthenticationToken oidcAuthentication(Long userId, Role role) {
        CustomOidcUserPrincipal principal = new CustomOidcUserPrincipal(mock(OidcUser.class), user(userId, role));
        return new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities());
    }

    private static User user(Long id, Role role) {
        User user = mock(User.class);
        org.mockito.Mockito.when(user.getId()).thenReturn(id);
        org.mockito.Mockito.when(user.getRole()).thenReturn(role);
        org.mockito.Mockito.when(user.getEmail()).thenReturn(role.name().toLowerCase() + "-" + id + "@ksh.edu.vn");
        org.mockito.Mockito.when(user.getPasswordHash()).thenReturn("encoded");
        org.mockito.Mockito.when(user.getFullName()).thenReturn(role.name() + " " + id);
        org.mockito.Mockito.when(user.isActive()).thenReturn(true);
        org.mockito.Mockito.when(user.isLocked()).thenReturn(false);
        return user;
    }

    private static class TrackingInputStream extends ByteArrayInputStream {
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

    private static final class TransactionObservingInputStream extends TrackingInputStream {
        private final java.util.List<Boolean> transactionActiveDuringRead = new java.util.ArrayList<>();

        private TransactionObservingInputStream(byte[] buf) {
            super(buf);
        }

        @Override
        public int read(byte[] b, int off, int len) {
            transactionActiveDuringRead.add(TransactionSynchronizationManager.isActualTransactionActive());
            return super.read(b, off, len);
        }
    }

    private static final class FailingReadInputStream extends InputStream {
        private boolean closed;

        @Override
        public int read() throws IOException {
            throw new IOException(SECRET_PATH);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            throw new IOException(SECRET_PATH);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class ThrowingOutputStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {
            throw new IOException(SECRET_PATH);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            throw new IOException(SECRET_PATH);
        }
    }
}
