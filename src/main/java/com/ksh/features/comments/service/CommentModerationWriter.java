package com.ksh.features.comments.service;

import com.ksh.entities.Comment;
import com.ksh.entities.CommentModeration;
import com.ksh.features.comments.repository.CommentModerationRepository;
import com.ksh.features.comments.repository.LessonCommentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.ksh.common.IConstant.MSG_COMMENT_NOT_FOUND;

/**
 * Executes one comment moderation transition at a real proxy transaction
 * boundary. Direct single-item calls join the service transaction, while the
 * non-transactional bulk loop opens and commits one transaction per item.
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

    @Transactional
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

    @Transactional
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
