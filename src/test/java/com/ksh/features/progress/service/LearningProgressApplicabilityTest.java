package com.ksh.features.progress.service;

import com.ksh.entities.Enrollment;
import com.ksh.entities.Lesson;
import com.ksh.entities.LessonAttachment;
import com.ksh.entities.LearningProgress;
import com.ksh.entities.User;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.lessons.repository.LessonAttachmentRepository;
import com.ksh.features.lessons.support.LessonAccessResolver;
import com.ksh.features.progress.repository.LearningProgressRepository;
import com.ksh.features.student.dto.StudentLessonsDtos.LessonEngagementTab;
import com.ksh.features.student.dto.StudentLessonsDtos.LessonEngagementView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningProgressApplicabilityTest {

    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final LearningProgressRepository progress = mock(LearningProgressRepository.class);
    private final LessonAccessResolver access = mock(LessonAccessResolver.class);
    private final LessonAttachmentRepository attachments = mock(LessonAttachmentRepository.class);
    private final Lesson lesson = mock(Lesson.class);
    private LearningProgressService service;

    @BeforeEach
    void setUp() {
        Enrollment active = new Enrollment(mock(User.class), 3L,
                Enrollment.JoinedVia.MANUAL.name(), null);
        when(enrollments.findByUserIdAndClassId(7L, 3L))
                .thenReturn(Optional.of(active));
        when(enrollments.findByUserIdAndClassIdForUpdate(7L, 3L))
                .thenReturn(Optional.of(active));
        when(lesson.getId()).thenReturn(5L);
        // Mockito returns numeric zero for an unstubbed boxed Long. These are
        // nullable foreign keys in production, so model their absent state
        // explicitly and override them only in the applicable test cases.
        when(lesson.getPdfAttachmentId()).thenReturn(null);
        when(lesson.getVideoLibraryAssetId()).thenReturn(null);
        when(access.resolveInClass(3L, 5L)).thenReturn(
                new LessonAccessResolver.ResolvedLesson(null, null, lesson));
        when(progress.findByUserIdAndLessonId(7L, 5L)).thenReturn(Optional.empty());
        service = new LearningProgressService(enrollments, progress, access, attachments);
    }

    @Test
    void mainPdfAttachmentRowIsContentButNotAccessoryAttachment() {
        LessonAttachment mainPdf = mock(LessonAttachment.class);
        when(lesson.getPdfAttachmentId()).thenReturn(900L);
        when(mainPdf.getId()).thenReturn(900L);
        when(mainPdf.getLibraryAssetId()).thenReturn(42L);
        when(attachments.findByLessonIdOrderByUploadedAtAsc(5L))
                .thenReturn(List.of(mainPdf));

        LessonEngagementView view = service.getEngagement(3L, 5L, 7L);

        assertThat(view.content().applicable()).isTrue();
        assertThat(view.attachments().applicable()).isFalse();
        assertThat(view.attachments().satisfied()).isTrue();
    }

    @Test
    void accessoryBeyondMainPdfMakesAttachmentsApplicable() {
        LessonAttachment mainPdf = mock(LessonAttachment.class);
        LessonAttachment handout = mock(LessonAttachment.class);
        when(lesson.getPdfAttachmentId()).thenReturn(900L);
        when(mainPdf.getId()).thenReturn(900L);
        when(mainPdf.getLibraryAssetId()).thenReturn(42L);
        when(handout.getId()).thenReturn(901L);
        when(handout.getLibraryAssetId()).thenReturn(43L);
        when(attachments.findByLessonIdOrderByUploadedAtAsc(5L))
                .thenReturn(List.of(mainPdf, handout));

        LessonEngagementView view = service.getEngagement(3L, 5L, 7L);

        assertThat(view.attachments().applicable()).isTrue();
        assertThat(view.attachments().satisfied()).isFalse();
    }

    @Test
    void libraryVideoReferenceIsApplicableEvenBeforeResolvedStreamUrl() {
        when(lesson.getVideoLibraryAssetId()).thenReturn(88L);
        when(attachments.findByLessonIdOrderByUploadedAtAsc(5L))
                .thenReturn(List.of());

        LessonEngagementView view = service.getEngagement(3L, 5L, 7L);

        assertThat(view.video().applicable()).isTrue();
        assertThat(view.video().satisfied()).isFalse();
    }

    @Test
    void recordOpenedReturnsReconciledViewWithoutSecondReadPass() {
        when(lesson.getContentRichtext()).thenReturn("<p>Nội dung</p>");
        when(attachments.findByLessonIdOrderByUploadedAtAsc(5L))
                .thenReturn(List.of());
        when(progress.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LessonEngagementView view = service.recordOpened(3L, 5L, 7L);

        assertThat(view.content().applicable()).isTrue();
        assertThat(view.content().seconds()).isZero();
        assertThat(view.video().satisfied()).isTrue();
        assertThat(view.attachments().satisfied()).isTrue();
        assertThat(view.overallCompleted()).isFalse();
        verify(enrollments, times(1))
                .findByUserIdAndClassIdForUpdate(7L, 3L);
        verify(access, times(1)).resolveInClass(3L, 5L);
        verify(attachments, times(1)).findByLessonIdOrderByUploadedAtAsc(5L);
        verify(progress, times(1)).findByUserIdAndLessonId(7L, 5L);
    }

    @Test
    void videoAddedLaterCannotInheritHeartbeatsSentWhileItWasAbsent() {
        com.ksh.entities.LearningProgress stored =
                new com.ksh.entities.LearningProgress(7L, 5L);
        when(progress.findByUserIdAndLessonId(7L, 5L))
                .thenReturn(Optional.of(stored));
        when(progress.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(attachments.findByLessonIdOrderByUploadedAtAsc(5L))
                .thenReturn(List.of());

        LessonEngagementView absent = service.checkpointEngagement(
                3L, 5L, 7L, LessonEngagementTab.VIDEO, true);
        assertThat(absent.video().applicable()).isFalse();
        assertThat(stored.getVideoEngagedSeconds()).isZero();
        assertThat(stored.getActiveEngagementTab()).isNull();

        when(lesson.getVideoUrl()).thenReturn("https://video.example/lesson");
        LessonEngagementView added = service.checkpointEngagement(
                3L, 5L, 7L, LessonEngagementTab.VIDEO, true);

        assertThat(added.video().applicable()).isTrue();
        assertThat(added.video().seconds()).isZero();
        assertThat(stored.getVideoEngagedSeconds()).isZero();
        assertThat(stored.getActiveEngagementTab())
                .isEqualTo(com.ksh.entities.LearningProgress.TAB_VIDEO);
    }

    @Test
    void readViewNeverReportsCompletedForAChangedIncompleteLessonShape() {
        when(lesson.getContentRichtext()).thenReturn("<p>Nội dung</p>");
        when(lesson.getVideoUrl()).thenReturn("https://video.example/lesson");
        when(attachments.findByLessonIdOrderByUploadedAtAsc(5L))
                .thenReturn(List.of());
        LearningProgress stored = new LearningProgress(7L, 5L);
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 12, 0);
        stored.checkpointEngagement(LearningProgress.TAB_CONTENT, true,
                true, false, false, now);
        for (int i = 0; i < 4; i++) {
            now = now.plusSeconds(15);
            stored.checkpointEngagement(LearningProgress.TAB_CONTENT, true,
                    true, false, false, now);
        }
        stored.markCompleted();
        when(progress.findByUserIdAndLessonId(7L, 5L))
                .thenReturn(Optional.of(stored));

        LessonEngagementView view = service.getEngagement(3L, 5L, 7L);

        assertThat(view.eligible()).isFalse();
        assertThat(view.overallCompleted()).isFalse();
        assertThat(view.overallPercent()).isEqualTo(50);
    }
}
