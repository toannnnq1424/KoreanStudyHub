package com.ksh.features.comments.service;

import com.ksh.entities.Comment;
import com.ksh.entities.CommentModeration;
import com.ksh.features.comments.repository.CommentModerationRepository;
import com.ksh.features.comments.repository.LessonCommentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.ksh.common.IConstant.MSG_COMMENT_NOT_FOUND;

/**
 * Executes one comment moderation transition in an independent transaction.
 * This bean boundary is intentional: bulk callers must not rely on
 * self-invoked {@code @Transactional} methods.
 */
@Service
public class CommentModerationWriter {

    private final LessonCommentRepository commentRepository;
    private final CommentModerationRepository moderationRepository;

    public CommentModerationWriter(LessonCommentRepository commentRepository,
                                   CommentModerationRepository moderationRepository) {
        this.commentRepository = commentRepository;
        this.moderationRepository = moderationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void hide(Long lessonId, Long commentId, Long actorId) {
        Comment comment = loadForUpdate(lessonId, commentId);
        if (Comment.MODERATION_REJECTED.equals(comment.getModerationStatus())) {
            return;
        }
        comment.hide();
        commentRepository.saveAndFlush(comment);
        moderationRepository.save(CommentModeration.record(
                commentId, actorId, CommentModeration.ACTION_REJECTED));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void unhide(Long lessonId, Long commentId, Long actorId) {
        Comment comment = loadForUpdate(lessonId, commentId);
        if (Comment.MODERATION_APPROVED.equals(comment.getModerationStatus())) {
            return;
        }
        comment.unhide();
        commentRepository.saveAndFlush(comment);
        moderationRepository.save(CommentModeration.record(
                commentId, actorId, CommentModeration.ACTION_APPROVED));
    }

    private Comment loadForUpdate(Long lessonId, Long commentId) {
        return commentRepository.findByIdAndLessonIdForUpdate(commentId, lessonId)
                .filter(comment -> !comment.isDeleted())
                .orElseThrow(() -> new EntityNotFoundException(MSG_COMMENT_NOT_FOUND));
    }
}
