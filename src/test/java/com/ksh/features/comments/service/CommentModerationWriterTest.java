package com.ksh.features.comments.service;

import com.ksh.entities.Comment;
import com.ksh.entities.CommentModeration;
import com.ksh.features.comments.repository.CommentModerationRepository;
import com.ksh.features.comments.repository.LessonCommentRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentModerationWriterTest {

    private final LessonCommentRepository comments = mock(LessonCommentRepository.class);
    private final CommentModerationRepository audits = mock(CommentModerationRepository.class);
    private final CommentModerationWriter writer =
            new CommentModerationWriter(comments, audits);

    @Test
    void hideLocksBeforeWritingOneStateAndAuditTransition() {
        Comment comment = new Comment(7L, 9L, null, "body");
        when(comments.findByIdAndLessonIdForUpdate(11L, 7L))
                .thenReturn(Optional.of(comment));

        writer.hide(7L, 11L, 23L);

        assertThat(comment.getModerationStatus()).isEqualTo(Comment.MODERATION_REJECTED);
        verify(comments).saveAndFlush(comment);
        verify(audits).save(argThat(audit ->
                audit.getCommentId().equals(11L)
                        && audit.getModeratedBy().equals(23L)
                        && CommentModeration.ACTION_REJECTED.equals(audit.getAction())));
    }

    @Test
    void repeatedHideIsIdempotentUnderLockedState() {
        Comment comment = new Comment(7L, 9L, null, "body");
        comment.hide();
        when(comments.findByIdAndLessonIdForUpdate(11L, 7L))
                .thenReturn(Optional.of(comment));

        writer.hide(7L, 11L, 23L);

        verify(comments, never()).saveAndFlush(comment);
        verify(audits, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
