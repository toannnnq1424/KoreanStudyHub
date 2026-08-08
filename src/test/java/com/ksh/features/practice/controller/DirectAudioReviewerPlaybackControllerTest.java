package com.ksh.features.practice.controller;

import com.ksh.entities.User;
import com.ksh.features.practice.service.DirectAudioReviewerPlaybackService;
import com.ksh.security.AuthenticatedUserIdResolver;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DirectAudioReviewerPlaybackControllerTest {

    @Test
    void reviewerRouteStreamsOnlyServiceAuthorizedPrivateAudioWithNoStoreRangeHeaders() throws Exception {
        DirectAudioReviewerPlaybackService service = mock(DirectAudioReviewerPlaybackService.class);
        when(service.openForReviewer(77L, 10L, 20L, 30L)).thenReturn(
                new DirectAudioReviewerPlaybackService.PlaybackStream(
                        "audio/webm", 4L, new ByteArrayInputStream(new byte[]{1, 2, 3, 4})));
        DirectAudioReviewerPlaybackController controller = new DirectAudioReviewerPlaybackController(
                service, new AuthenticatedUserIdResolver());

        var response = controller.content(10L, 20L, 30L, "bytes=1-2", authentication(77L));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ((StreamingResponseBody) response.getBody()).writeTo(output);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 1-2/4");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("no-store", "private");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("inline");
        assertThat(output.toByteArray()).containsExactly(2, 3);
        verify(service).openForReviewer(77L, 10L, 20L, 30L);
    }

    private static UsernamePasswordAuthenticationToken authentication(Long userId) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getRole()).thenReturn(Role.STUDENT);
        when(user.getEmail()).thenReturn("reviewer@example.test");
        when(user.getPasswordHash()).thenReturn("encoded");
        when(user.getFullName()).thenReturn("Reviewer");
        when(user.isActive()).thenReturn(true);
        when(user.isLocked()).thenReturn(false);
        KshUserDetails principal = new KshUserDetails(user);
        return new UsernamePasswordAuthenticationToken(principal, "N/A", principal.getAuthorities());
    }
}
