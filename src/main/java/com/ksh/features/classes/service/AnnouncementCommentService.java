package com.ksh.features.classes.service;

import com.ksh.entities.AnnouncementComment;
import com.ksh.entities.ClassActivity;
import com.ksh.entities.ClassAnnouncement;
import com.ksh.entities.ClassEntity;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.AnnouncementCommentRepository;
import com.ksh.features.classes.repository.ClassAnnouncementRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.lessons.support.ClassAccessPolicy;
import com.ksh.features.notifications.entity.NotificationType;
import com.ksh.features.notifications.service.NotificationService;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static com.ksh.common.IConstant.MAX_COMMENT_LENGTH;
import static com.ksh.common.IConstant.MSG_COMMENT_ACTIVITY_CREATED;
import static com.ksh.common.IConstant.MSG_COMMENT_ACTIVITY_DELETED;
import static com.ksh.common.IConstant.MSG_COMMENT_EMPTY;
import static com.ksh.common.IConstant.MSG_COMMENT_FORBIDDEN;
import static com.ksh.common.IConstant.MSG_COMMENT_NOT_FOUND;
import static com.ksh.common.IConstant.MSG_COMMENT_NOTIFY_ANONYMOUS_ACTOR;
import static com.ksh.common.IConstant.MSG_COMMENT_NOTIFY_BODY_PREFIX;
import static com.ksh.common.IConstant.MSG_COMMENT_NOTIFY_TITLE;
import static com.ksh.common.IConstant.MSG_COMMENT_TOO_LONG;

/**
 * Owns creation and soft-deletion of member comments on class announcements.
 *
 * <p><b>The write gate is deliberately {@link ClassAccessPolicy#requireViewAccess},
 * not {@code ClassesService.getEditable}.</b> Every announcement write uses
 * {@code getEditable}, which admits only the owning lecturer, co-lecturers,
 * department leaders, and admins — it rejects students with 403. Students must be
 * able to comment, so {@code getEditable} is structurally the wrong gate
 * (design D3). Reusing the board's <i>read</i> gate means "who may participate in
 * this class" has exactly one definition rather than two that can drift.
 *
 * <p><b>Status codes are split on purpose:</b>
 * <ul>
 *   <li>Caller admitted by no rule, or announcement missing / deleted / belonging
 *       to another class → 404, matching the no-leak read contract and blocking
 *       path-variable enumeration.</li>
 *   <li>Caller admitted to the class but neither the comment's author nor
 *       privileged → 403. Membership is not in doubt, only ownership failed, so
 *       404 would be misleading.</li>
 * </ul>
 *
 * <p>Comment bodies are stored as <b>plain text</b> with no sanitization,
 * because they render with {@code th:text} and are never treated as markup on
 * any path (design D2).
 */
@Service
public class AnnouncementCommentService {

    private final AnnouncementCommentRepository commentRepository;
    private final ClassAnnouncementRepository announcementRepository;
    private final ClassRepository classRepository;
    private final UserRepository userRepository;
    private final ClassAccessPolicy classAccessPolicy;
    private final ClassActivityWriter activityWriter;
    private final NotificationService notificationService;

    public AnnouncementCommentService(AnnouncementCommentRepository commentRepository,
                                      ClassAnnouncementRepository announcementRepository,
                                      ClassRepository classRepository,
                                      UserRepository userRepository,
                                      ClassAccessPolicy classAccessPolicy,
                                      ClassActivityWriter activityWriter,
                                      NotificationService notificationService) {
        this.commentRepository = commentRepository;
        this.announcementRepository = announcementRepository;
        this.classRepository = classRepository;
        this.userRepository = userRepository;
        this.classAccessPolicy = classAccessPolicy;
        this.activityWriter = activityWriter;
        this.notificationService = notificationService;
    }

    /**
     * Posts a comment on an announcement and notifies its author.
     *
     * <p>Shared by both route families ({@code /lecturer/**} and {@code /my/**}),
     * so the authorization decision lives here once and cannot drift between them
     * (design D5).
     *
     * @param classId        id of the class the caller addressed
     * @param announcementId id of the announcement being commented on
     * @param content        raw plain-text body from the composer
     * @param userId         id of the commenting user
     * @param role           role of the commenting user
     * @return the persisted comment
     * @throws EntityNotFoundException  when the class or announcement is absent,
     *                                  soft-deleted, belongs to another class, or
     *                                  the caller is admitted by no access rule
     * @throws IllegalArgumentException when the content is blank or exceeds
     *                                  {@link com.ksh.common.IConstant#MAX_COMMENT_LENGTH}
     */
    @Transactional
    public AnnouncementComment create(Long classId, Long announcementId, String content,
                                      Long userId, Role role) {
        ClassEntity clazz = loadClass(classId);
        classAccessPolicy.requireViewAccess(clazz, userId, role);
        ClassAnnouncement announcement = loadWithinClass(announcementId, classId);
        String body = validateContent(content);

        AnnouncementComment saved = commentRepository.save(
                new AnnouncementComment(announcementId, classId, body, userId));
        activityWriter.write(classId, ClassActivity.TYPE_ANNOUNCEMENT_COMMENT_CREATED,
                MSG_COMMENT_ACTIVITY_CREATED, userId);

        notifyAuthor(clazz, announcement, userId);
        return saved;
    }

    /**
     * Soft-deletes a comment on behalf of its author or the class's teaching staff.
     *
     * <p>The row is retained with {@code is_deleted = 1} so it disappears from
     * both feeds and from the count without losing the record.
     *
     * @param classId        id of the class the caller addressed
     * @param announcementId id of the announcement owning the comment
     * @param commentId      id of the comment to delete
     * @param userId         id of the acting user
     * @param role           role of the acting user
     * @throws EntityNotFoundException when the class, announcement, or comment is
     *                                 absent or addressed through the wrong parent
     * @throws AccessDeniedException   when the caller is admitted to the class but
     *                                 is neither the comment's author nor privileged
     */
    @Transactional
    public void delete(Long classId, Long announcementId, Long commentId,
                       Long userId, Role role) {
        ClassEntity clazz = loadClass(classId);
        classAccessPolicy.requireViewAccess(clazz, userId, role);
        loadWithinClass(announcementId, classId);

        AnnouncementComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException(MSG_COMMENT_NOT_FOUND));
        // Scope the comment to its addressed parents, blocking enumeration.
        if (!Objects.equals(comment.getAnnouncementId(), announcementId)
                || !Objects.equals(comment.getClassId(), classId)) {
            throw new EntityNotFoundException(MSG_COMMENT_NOT_FOUND);
        }

        boolean isAuthor = Objects.equals(comment.getCreatedBy(), userId);
        // Membership already proven above, so a failure here is ownership-only.
        if (!isAuthor && !classAccessPolicy.isPrivileged(clazz, userId, role)) {
            throw new AccessDeniedException(MSG_COMMENT_FORBIDDEN);
        }

        comment.softDelete();
        commentRepository.save(comment);
        activityWriter.write(classId, ClassActivity.TYPE_ANNOUNCEMENT_COMMENT_DELETED,
                MSG_COMMENT_ACTIVITY_DELETED, userId);
    }

    // ── Internal helpers ───────────────────────────────────────────────

    /**
     * Trims and enforces the comment content rules.
     *
     * <p>Unlike announcements, the emptiness check is a plain blank check: the
     * body is never sanitized, so there is no "non-empty before, empty after"
     * case to guard against (design D2).
     */
    private static String validateContent(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(MSG_COMMENT_EMPTY);
        }
        if (trimmed.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException(MSG_COMMENT_TOO_LONG);
        }
        return trimmed;
    }

    private ClassEntity loadClass(Long classId) {
        return classRepository.findById(classId)
                .orElseThrow(() -> new EntityNotFoundException(MSG_COMMENT_NOT_FOUND));
    }

    /**
     * Loads an announcement scoped to the addressed class, so POSTing class C's
     * URL with an announcement belonging to class D yields 404 rather than
     * confirming the announcement exists.
     */
    private ClassAnnouncement loadWithinClass(Long announcementId, Long classId) {
        ClassAnnouncement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new EntityNotFoundException(MSG_COMMENT_NOT_FOUND));
        if (!Objects.equals(announcement.getClassId(), classId)) {
            throw new EntityNotFoundException(MSG_COMMENT_NOT_FOUND);
        }
        return announcement;
    }

    /**
     * Notifies the announcement's author that someone commented.
     *
     * <p>Best-effort: wrapped so a notification failure can never roll back the
     * committed comment, following
     * {@code LessonsPublishService.fanOutLessonPublished}.
     *
     * <p>{@code referenceId} is the <b>class</b> id, not the announcement id —
     * there is no {@code REF_ANNOUNCEMENT}, and the deep-link target
     * {@code /my/classes/{referenceId}/board} interpolates it as a class id
     * (design D9).
     */
    private void notifyAuthor(ClassEntity clazz, ClassAnnouncement announcement, Long actorId) {
        Long authorId = announcement.getCreatedBy();
        // Notifying an author about their own comment is pure noise (design D10).
        if (authorId == null || Objects.equals(authorId, actorId)) {
            return;
        }
        try {
            String actorName = userRepository.findById(actorId)
                    .map(User::getFullName)
                    .filter(name -> name != null && !name.isBlank())
                    .orElse(MSG_COMMENT_NOTIFY_ANONYMOUS_ACTOR);
            notificationService.create(
                    authorId,
                    MSG_COMMENT_NOTIFY_TITLE,
                    actorName + " " + MSG_COMMENT_NOTIFY_BODY_PREFIX + clazz.getName(),
                    NotificationType.ANNOUNCEMENT_COMMENT,
                    NotificationType.REF_CLASS,
                    clazz.getId());
        } catch (Exception ignored) {
            // Notification failure must not roll back the comment.
        }
    }
}