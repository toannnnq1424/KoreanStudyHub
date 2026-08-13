package com.ksh.features.progress.service;

import com.ksh.entities.Enrollment;
import com.ksh.entities.LearningProgress;
import com.ksh.entities.Lesson;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.lessons.repository.LessonAttachmentRepository;
import com.ksh.features.lessons.support.LessonAccessResolver;
import com.ksh.features.progress.repository.LearningProgressRepository;
import com.ksh.features.student.dto.StudentLessonsDtos.LessonEngagementItemView;
import com.ksh.features.student.dto.StudentLessonsDtos.LessonEngagementTab;
import com.ksh.features.student.dto.StudentLessonsDtos.LessonEngagementView;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Write service backing student learning-progress recording (KSH-4.5).
 *
 * <p>Re-applies the same four authz gates as
 * {@code com.ksh.features.student.service.StudentLessonDetailService}
 * (that class is canonical for the gate order); any failure collapses to
 * an {@link EntityNotFoundException} with the message
 * {@code "Class not found or not accessible"} so lesson existence is never
 * leaked. The resolution + PUBLISHED gates are shared via
 * {@link LessonAccessResolver}; the ACTIVE-enrollment policy stays inline here.
 */
@Service
public class LearningProgressService {

    private static final String NF_MSG = "Class not found or not accessible";

    private final EnrollmentRepository enrollmentRepository;
    private final LearningProgressRepository progressRepository;
    private final LessonAccessResolver lessonAccessResolver;
    private final LessonAttachmentRepository attachmentRepository;

    public LearningProgressService(EnrollmentRepository enrollmentRepository,
                                   LearningProgressRepository progressRepository,
                                   LessonAccessResolver lessonAccessResolver,
                                   LessonAttachmentRepository attachmentRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.progressRepository = progressRepository;
        this.lessonAccessResolver = lessonAccessResolver;
        this.attachmentRepository = attachmentRepository;
    }

    /**
     * Idempotent upsert invoked after a student successfully opens a lesson.
     * Creates an {@code IN_PROGRESS} row when none exists; an existing row is
     * paused and reconciled against the lesson's current applicable tabs. A
     * concurrent first-open race on the unique key surfaces
     * as {@link DataIntegrityViolationException}, which is swallowed (the row
     * now exists — the desired end state).
     *
     * @throws EntityNotFoundException when any authz gate fails
     */
    @Transactional
    public LessonEngagementView recordOpened(Long classId, Long lessonId, Long userId) {
        // Opening/refreshing the page mutates the same progress row as a
        // heartbeat (it pauses any stale active timer and reconciles status),
        // so it must share the enrollment-row serialization boundary used by
        // checkpoint/toggle. Otherwise a concurrent refresh can save a stale
        // entity after a heartbeat and erase newly accrued seconds.
        LessonAccessResolver.ResolvedLesson resolved =
                runToggleGates(classId, lessonId, userId);
        Applicability applicability = applicabilityOf(resolved.lesson());
        var existing = progressRepository.findByUserIdAndLessonId(userId, lessonId);
        if (existing.isPresent()) {
            LearningProgress progress = existing.get();
            // A fresh page view must not inherit a timer left active by a
            // crashed/closed tab. Reconcile also handles content that changed
            // applicability after an earlier completion.
            progress.pauseEngagement();
            progress.reconcileChecklistProgress(
                    applicability.content(), applicability.video(),
                    applicability.attachments(), LocalDateTime.now());
            LearningProgress saved = progressRepository.saveAndFlush(progress);
            return toView(saved, applicability);
        }
        try {
            LearningProgress progress = new LearningProgress(userId, lessonId);
            progress.reconcileChecklistProgress(
                    applicability.content(), applicability.video(),
                    applicability.attachments(), LocalDateTime.now());
            LearningProgress saved = progressRepository.saveAndFlush(progress);
            return toView(saved, applicability);
        } catch (DataIntegrityViolationException race) {
            // Concurrent first-open won the unique key — the row exists now.
            // The controller treats this write-path failure as fail-soft and
            // may perform a read-only fallback after this transaction exits.
            throw race;
        }
    }

    /**
     * Legacy completion endpoint compatibility. It now performs an idempotent
     * checklist reconciliation: it can report/retain COMPLETED only when all
     * applicable items have their persisted 60-second evidence. It never
     * fabricates evidence and never toggles an eligible completion back off.
     *
     * @return {@code true} only when the persisted checklist is eligible
     * @throws EntityNotFoundException when any authz gate fails
     */
    @Transactional
    public boolean toggleCompletion(Long classId, Long lessonId, Long userId) {
        LessonAccessResolver.ResolvedLesson resolved =
                runToggleGates(classId, lessonId, userId);
        Applicability applicability = applicabilityOf(resolved.lesson());
        LearningProgress progress = progressRepository
                .findByUserIdAndLessonId(userId, lessonId)
                .orElseGet(() -> new LearningProgress(userId, lessonId));
        progress.pauseEngagement();
        boolean nowCompleted = progress.reconcileChecklistCompletion(
                applicability.content(), applicability.video(),
                applicability.attachments(), LocalDateTime.now());
        progressRepository.saveAndFlush(progress);
        return nowCompleted;
    }

    /**
     * Records one server-timed selected/visible-tab heartbeat.
     * Client-supplied elapsed time is intentionally not accepted.
     */
    @Transactional
    public LessonEngagementView checkpointEngagement(Long classId,
                                                     Long lessonId,
                                                     Long userId,
                                                     LessonEngagementTab tab,
                                                     boolean active) {
        if (tab == null) {
            throw new IllegalArgumentException("Lesson engagement tab is required");
        }
        LessonAccessResolver.ResolvedLesson resolved =
                runToggleGates(classId, lessonId, userId);
        Applicability applicability = applicabilityOf(resolved.lesson());
        LearningProgress progress = progressRepository
                .findByUserIdAndLessonId(userId, lessonId)
                .orElseGet(() -> new LearningProgress(userId, lessonId));
        // Validate the selected tab against the lesson state resolved under
        // the enrollment lock. Non-applicable heartbeats are treated as a
        // pause, while the entity independently rejects a stale previous tab
        // before it can accrue time.
        boolean applicableActive = active && applicability.includes(tab);
        progress.checkpointEngagement(tab.name(), applicableActive,
                applicability.content(), applicability.video(),
                applicability.attachments(), LocalDateTime.now());
        LearningProgress saved = progressRepository.saveAndFlush(progress);
        return toView(saved, applicability);
    }

    /** Returns persisted checklist state for the authenticated student view. */
    @Transactional(readOnly = true)
    public LessonEngagementView getEngagement(Long classId, Long lessonId, Long userId) {
        LessonAccessResolver.ResolvedLesson resolved = runGates(classId, lessonId, userId);
        Applicability applicability = applicabilityOf(resolved.lesson());
        return progressRepository.findByUserIdAndLessonId(userId, lessonId)
                .map(progress -> toView(progress, applicability))
                .orElseGet(() -> emptyView(applicability));
    }

    /**
     * Runs the four authz gates in the canonical order: ACTIVE enrollment
     * (inline), then live class, section-belongs-to-class and PUBLISHED via the
     * shared {@link LessonAccessResolver}. Any failure collapses to 404.
     */
    private LessonAccessResolver.ResolvedLesson runGates(Long classId,
                                                         Long lessonId,
                                                         Long userId) {
        // Gate 1: enrollment must be ACTIVE — REMOVED/COMPLETED → 404.
        enrollmentRepository.findByUserIdAndClassId(userId, classId)
                .filter(e -> Enrollment.STATUS_ACTIVE.equals(e.getStatus()))
                .orElseThrow(() -> new EntityNotFoundException(NF_MSG));

        // Gates 2-4: live class, section-belongs-to-class, lesson PUBLISHED.
        return lessonAccessResolver.resolveInClass(classId, lessonId);
    }

    /**
     * Applies the same gates while locking the stable enrollment row. This
     * serializes every progress mutation before it reads/saves the checklist,
     * preventing refresh, checkpoint and completion requests from overwriting
     * one another.
     */
    private LessonAccessResolver.ResolvedLesson runToggleGates(Long classId,
                                                               Long lessonId,
                                                               Long userId) {
        enrollmentRepository.findByUserIdAndClassIdForUpdate(userId, classId)
                .filter(e -> Enrollment.STATUS_ACTIVE.equals(e.getStatus()))
                .orElseThrow(() -> new EntityNotFoundException(NF_MSG));
        return lessonAccessResolver.resolveInClass(classId, lessonId);
    }

    private Applicability applicabilityOf(Lesson lesson) {
        boolean content = hasText(lesson.getContentRichtext())
                || lesson.getPdfAttachmentId() != null;
        boolean video = hasText(lesson.getVideoUrl())
                || lesson.getVideoLibraryAssetId() != null;
        Long mainPdfId = lesson.getPdfAttachmentId();
        boolean attachments = attachmentRepository
                .findByLessonIdOrderByUploadedAtAsc(lesson.getId()).stream()
                .anyMatch(row -> mainPdfId == null || !mainPdfId.equals(row.getId()));
        return new Applicability(content, video, attachments);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static LessonEngagementView emptyView(Applicability applicability) {
        return new LessonEngagementView(
                item(applicability.content(), 0),
                item(applicability.video(), 0),
                item(applicability.attachments(), 0),
                trueIfNothingApplies(applicability),
                false,
                trueIfNothingApplies(applicability) ? 100 : 0);
    }

    private static LessonEngagementView toView(LearningProgress progress,
                                               Applicability applicability) {
        LessonEngagementItemView content = item(
                applicability.content(), progress.getContentEngagedSeconds());
        LessonEngagementItemView video = item(
                applicability.video(), progress.getVideoEngagedSeconds());
        LessonEngagementItemView attachments = item(
                applicability.attachments(), progress.getAttachmentsEngagedSeconds());
        boolean eligible = content.satisfied() && video.satisfied()
                && attachments.satisfied();
        return new LessonEngagementView(content, video, attachments,
                eligible, progress.isCompleted() && eligible,
                viewPercent(content, video, attachments));
    }

    /** Derives display progress from the lesson's current applicable tabs. */
    private static int viewPercent(LessonEngagementItemView content,
                                   LessonEngagementItemView video,
                                   LessonEngagementItemView attachments) {
        int applicableCount = 0;
        int seconds = 0;
        for (LessonEngagementItemView item : new LessonEngagementItemView[]{
                content, video, attachments}) {
            if (item.applicable()) {
                applicableCount++;
                seconds += item.seconds();
            }
        }
        if (applicableCount == 0) {
            return 100;
        }
        return Math.min(100, seconds * 100
                / (LearningProgress.REQUIRED_SECONDS_PER_TAB * applicableCount));
    }

    private static LessonEngagementItemView item(boolean applicable, int seconds) {
        int capped = Math.min(LearningProgress.REQUIRED_SECONDS_PER_TAB,
                Math.max(0, seconds));
        boolean complete = applicable
                && capped >= LearningProgress.REQUIRED_SECONDS_PER_TAB;
        return new LessonEngagementItemView(applicable, capped,
                LearningProgress.REQUIRED_SECONDS_PER_TAB,
                complete, !applicable || complete);
    }

    private static boolean trueIfNothingApplies(Applicability applicability) {
        return !applicability.content() && !applicability.video()
                && !applicability.attachments();
    }

    private record Applicability(boolean content, boolean video, boolean attachments) {
        boolean includes(LessonEngagementTab tab) {
            return switch (tab) {
                case CONTENT -> content;
                case VIDEO -> video;
                case ATTACHMENTS -> attachments;
            };
        }
    }
}
