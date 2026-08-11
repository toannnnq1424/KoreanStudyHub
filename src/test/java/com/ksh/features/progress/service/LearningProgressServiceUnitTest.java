package com.ksh.features.progress.service;

import com.ksh.entities.Enrollment;
import com.ksh.entities.LearningProgress;
import com.ksh.entities.User;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.lessons.support.LessonAccessResolver;
import com.ksh.features.progress.repository.LearningProgressRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningProgressServiceUnitTest {

    private final EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
    private final LearningProgressRepository progressRepository =
            mock(LearningProgressRepository.class);
    private final LessonAccessResolver lessonAccessResolver = mock(LessonAccessResolver.class);

    private final LearningProgressService service = new LearningProgressService(
            enrollmentRepository,
            progressRepository,
            lessonAccessResolver
    );

    @Test
    void recordOpened_withActiveEnrollmentAndNoProgress_createsInProgressRow() {
        when(enrollmentRepository.findByUserIdAndClassId(10L, 3L))
                .thenReturn(Optional.of(activeEnrollment(3L)));
        when(progressRepository.findByUserIdAndLessonId(10L, 5L))
                .thenReturn(Optional.empty());

        service.recordOpened(3L, 5L, 10L);

        verify(lessonAccessResolver).resolveInClass(3L, 5L);
        verify(progressRepository).saveAndFlush(any(LearningProgress.class));
    }

    @Test
    void recordOpened_whenProgressAlreadyExists_doesNotSaveAgain() {
        LearningProgress existing = new LearningProgress(10L, 5L);

        when(enrollmentRepository.findByUserIdAndClassId(10L, 3L))
                .thenReturn(Optional.of(activeEnrollment(3L)));
        when(progressRepository.findByUserIdAndLessonId(10L, 5L))
                .thenReturn(Optional.of(existing));

        service.recordOpened(3L, 5L, 10L);

        verify(progressRepository, never()).saveAndFlush(any(LearningProgress.class));
    }

    @Test
    void recordOpened_withoutActiveEnrollment_throwsEntityNotFoundException() {
        when(enrollmentRepository.findByUserIdAndClassId(10L, 3L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordOpened(3L, 5L, 10L))
                .isInstanceOf(EntityNotFoundException.class);

        verify(lessonAccessResolver, never()).resolveInClass(any(), any());
        verify(progressRepository, never()).saveAndFlush(any(LearningProgress.class));
    }

    @Test
    void toggleCompletion_withNoProgress_createsCompletedProgressAndReturnsTrue() {
        when(enrollmentRepository.findByUserIdAndClassIdForUpdate(10L, 3L))
                .thenReturn(Optional.of(activeEnrollment(3L)));
        when(progressRepository.findByUserIdAndLessonId(10L, 5L))
                .thenReturn(Optional.empty());

        boolean result = service.toggleCompletion(3L, 5L, 10L);

        assertThat(result).isTrue();
        verify(progressRepository).saveAndFlush(any(LearningProgress.class));
    }

    @Test
    void toggleCompletion_withCompletedProgress_revertsToInProgressAndReturnsFalse() {
        LearningProgress progress = new LearningProgress(10L, 5L);
        progress.markCompleted();

        when(enrollmentRepository.findByUserIdAndClassIdForUpdate(10L, 3L))
                .thenReturn(Optional.of(activeEnrollment(3L)));
        when(progressRepository.findByUserIdAndLessonId(10L, 5L))
                .thenReturn(Optional.of(progress));

        boolean result = service.toggleCompletion(3L, 5L, 10L);

        assertThat(result).isFalse();
        assertThat(progress.isCompleted()).isFalse();
        verify(progressRepository).saveAndFlush(progress);
    }

    private static Enrollment activeEnrollment(Long classId) {
        return new Enrollment(mock(User.class), classId, Enrollment.JoinedVia.MANUAL.name(), null);
    }
}
