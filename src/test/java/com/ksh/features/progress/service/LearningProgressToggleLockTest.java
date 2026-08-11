package com.ksh.features.progress.service;

import com.ksh.entities.Enrollment;
import com.ksh.entities.LearningProgress;
import com.ksh.entities.User;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.lessons.support.LessonAccessResolver;
import com.ksh.features.lessons.repository.LessonAttachmentRepository;
import com.ksh.features.progress.repository.LearningProgressRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningProgressToggleLockTest {

    @Test
    void toggleLocksStableEnrollmentBeforeReadingProgress() {
        EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
        LearningProgressRepository progress = mock(LearningProgressRepository.class);
        LessonAccessResolver access = mock(LessonAccessResolver.class);
        LessonAttachmentRepository attachments = mock(LessonAttachmentRepository.class);
        Enrollment active = new Enrollment(mock(User.class), 3L,
                Enrollment.JoinedVia.MANUAL.name(), null);
        when(enrollments.findByUserIdAndClassIdForUpdate(7L, 3L))
                .thenReturn(Optional.of(active));
        com.ksh.entities.Lesson lesson = mock(com.ksh.entities.Lesson.class);
        when(lesson.getId()).thenReturn(5L);
        when(lesson.getContentRichtext()).thenReturn("<p>Body</p>");
        when(access.resolveInClass(3L, 5L)).thenReturn(
                new LessonAccessResolver.ResolvedLesson(null, null, lesson));
        when(attachments.findByLessonIdOrderByUploadedAtAsc(5L))
                .thenReturn(java.util.List.of());
        when(progress.findByUserIdAndLessonId(7L, 5L)).thenReturn(Optional.empty());
        LearningProgressService service = new LearningProgressService(
                enrollments, progress, access, attachments);

        assertThat(service.toggleCompletion(3L, 5L, 7L)).isFalse();

        verify(enrollments).findByUserIdAndClassIdForUpdate(7L, 3L);
        verify(access).resolveInClass(3L, 5L);
        verify(progress).saveAndFlush(any(LearningProgress.class));
    }
}
