package com.ksh.features.lessons.service;

import com.ksh.entities.LessonAttachment;
import com.ksh.entities.PublicViewToken;
import com.ksh.features.lessons.repository.LessonAttachmentRepository;
import com.ksh.features.lessons.repository.PublicViewTokenRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicViewTokenConcurrencyTest {

    private final PublicViewTokenRepository tokens = mock(PublicViewTokenRepository.class);
    private final LessonAttachmentRepository attachments = mock(LessonAttachmentRepository.class);
    private final PublicViewTokenService service =
            new PublicViewTokenService(tokens, attachments, "https://ksh.test");

    @Test
    void locksAttachmentRevokesLiveTokensAndStoresOnlyFreshDigest() {
        LessonAttachment attachment = mock(LessonAttachment.class);
        PublicViewToken newest = new PublicViewToken(
                9L, "newest", LocalDateTime.now().plusHours(1));
        PublicViewToken duplicate = new PublicViewToken(
                9L, "duplicate", LocalDateTime.now().plusHours(1));
        when(attachments.findByIdForUpdate(9L)).thenReturn(Optional.of(attachment));
        when(tokens.findLiveTokensByAttachmentId(eq(9L), any(LocalDateTime.class)))
                .thenReturn(List.of(newest, duplicate));

        String url = service.createPublicViewUrl(9L);

        assertThat(url).startsWith("https://ksh.test/public/view/");
        verify(attachments).findByIdForUpdate(9L);
        verify(tokens).deleteAll(List.of(newest, duplicate));
        verify(tokens).save(any(PublicViewToken.class));
    }

    @Test
    void createsOnlyAfterAttachmentLockWhenNoTokenIsLive() {
        when(attachments.findByIdForUpdate(9L))
                .thenReturn(Optional.of(mock(LessonAttachment.class)));
        when(tokens.findLiveTokensByAttachmentId(eq(9L), any(LocalDateTime.class)))
                .thenReturn(List.of());

        assertThat(service.createPublicViewUrl(9L)).startsWith("https://ksh.test/public/view/");

        verify(attachments).findByIdForUpdate(9L);
        verify(tokens).save(any(PublicViewToken.class));
    }
}
