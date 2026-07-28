package com.ksh.features.comments.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Comment;
import com.ksh.features.comments.repository.LessonCommentRepository;
import com.ksh.features.lessons.support.ClassAccessPolicy;
import com.ksh.features.lessons.support.LessonAccessResolver;
import com.ksh.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LessonCommentDeleteAuthorizationTest {

    private static final Long LESSON_ID = 11L;
    private static final Long COMMENT_ID = 22L;
    private static final Long AUTHOR_ID = 33L;
    private static final Long ACTOR_ID = 44L;

    private LessonCommentRepository repository;
    private ClassAccessPolicy accessPolicy;
    private LessonCommentsService service;
    private ClassEntity clazz;
    private Comment comment;

    @BeforeEach
    void setUp() {
        repository = mock(LessonCommentRepository.class);
        accessPolicy = mock(ClassAccessPolicy.class);
        LessonAccessResolver lessonAccessResolver = mock(LessonAccessResolver.class);
        clazz = mock(ClassEntity.class);
        comment = mock(Comment.class);

        when(lessonAccessResolver.resolveByLesson(LESSON_ID))
                .thenReturn(new LessonAccessResolver.ResolvedLesson(clazz, null, null));
        when(repository.findByIdAndLessonIdForUpdate(COMMENT_ID, LESSON_ID))
                .thenReturn(Optional.of(comment));
        when(comment.isDeleted()).thenReturn(false);
        when(comment.getUserId()).thenReturn(AUTHOR_ID);

        service = new LessonCommentsService(
                repository,
                mock(CommentThreadAssembler.class),
                lessonAccessResolver,
                accessPolicy,
                mock(CommentModerationWriter.class));
    }

    @Test
    void departmentLeaderModeratorMayDeleteAnotherUsersComment() {
        when(accessPolicy.isModerator(clazz, ACTOR_ID, Role.LEADER)).thenReturn(true);

        service.delete(LESSON_ID, COMMENT_ID, ACTOR_ID, Role.LEADER);

        verify(accessPolicy).requireModeratorOrEnrolled(clazz, ACTOR_ID, Role.LEADER);
        verify(comment).markDeleted();
        verify(repository).saveAndFlush(comment);
    }

    @Test
    void nonModeratorStillCannotDeleteAnotherUsersComment() {
        when(accessPolicy.isModerator(clazz, ACTOR_ID, Role.STUDENT)).thenReturn(false);

        assertThatThrownBy(() ->
                service.delete(LESSON_ID, COMMENT_ID, ACTOR_ID, Role.STUDENT))
                .isInstanceOf(AccessDeniedException.class);

        verify(comment, never()).markDeleted();
        verify(repository, never()).saveAndFlush(comment);
    }

    @Test
    void authorMayStillDeleteOwnComment() {
        service.delete(LESSON_ID, COMMENT_ID, AUTHOR_ID, Role.STUDENT);

        verify(comment).markDeleted();
        verify(repository).saveAndFlush(comment);
    }
}
