package com.ksh.features.classes.service;

import com.ksh.entities.AnnouncementComment;
import com.ksh.entities.ClassActivity;
import com.ksh.entities.ClassAnnouncement;
import com.ksh.entities.ClassEntity;
import com.ksh.entities.Enrollment;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.dto.AnnouncementDtos.AnnouncementRow;
import com.ksh.features.classes.dto.AnnouncementDtos.CommentRow;
import com.ksh.features.classes.repository.AnnouncementCommentRepository;
import com.ksh.features.classes.repository.ClassAnnouncementRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.lessons.support.ClassAccessPolicy;
import com.ksh.features.notifications.entity.NotificationType;
import com.ksh.features.notifications.service.NotificationService;
import com.ksh.features.upload.AnnouncementImageStorageService;
import com.ksh.security.Role;
import com.ksh.utils.AvatarStyles;
import jakarta.persistence.EntityNotFoundException;
import org.jsoup.Jsoup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.ksh.common.IConstant.DEFAULT_ANNOUNCEMENT_PAGE_SIZE;
import static com.ksh.common.IConstant.DEFAULT_COMMENT_PREVIEW_SIZE;
import static com.ksh.common.IConstant.MAX_ANNOUNCEMENT_LENGTH;
import static com.ksh.common.IConstant.MSG_ANNOUNCEMENT_ACTIVITY_CREATED;
import static com.ksh.common.IConstant.MSG_ANNOUNCEMENT_ACTIVITY_DELETED;
import static com.ksh.common.IConstant.MSG_ANNOUNCEMENT_ACTIVITY_UPDATED;
import static com.ksh.common.IConstant.MSG_ANNOUNCEMENT_EMPTY;
import static com.ksh.common.IConstant.MSG_ANNOUNCEMENT_NOTIFY_TITLE;
import static com.ksh.common.IConstant.MSG_ANNOUNCEMENT_TOO_LONG;

/**
 * Owns the class Board announcement feed and its create / update / soft-delete
 * write flows.
 *
 * <p><b>Authorization is deliberately asymmetric.</b> Reads gate through
 * {@link ClassAccessPolicy#requireViewAccess} — the single no-leak gate that
 * admits both teaching staff and enrolled students, collapsing every rejection
 * to a 404 so class existence is never disclosed (design D3). Writes gate
 * through {@link ClassesService#getEditable}, which throws 403; that discloses
 * nothing a caller who already passed the lecturer role gate could not infer
 * (design D4).
 *
 * <p><b>Sanitization happens here, not in the controller</b>, so every write
 * path through this service is covered. Templates therefore render stored
 * content with {@code th:utext} without re-sanitizing (design D9).
 */
@Service
public class ClassAnnouncementService {

    /** Plain-text excerpt length in the fan-out notification body. */
    private static final int NOTIFY_EXCERPT_LENGTH = 200;

    private static final String MSG_CLASS_NOT_FOUND = "Lớp không tồn tại";
    private static final String MSG_ANNOUNCEMENT_NOT_FOUND = "Thông báo không tồn tại";

    private final ClassAnnouncementRepository announcementRepository;
    private final ClassRepository classRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassAccessPolicy classAccessPolicy;
    private final ClassesService classesService;
    private final ClassActivityWriter activityWriter;
    private final NotificationService notificationService;
    private final AnnouncementCommentRepository commentRepository;
    private final AnnouncementImageStorageService announcementImageStorage;

    public ClassAnnouncementService(ClassAnnouncementRepository announcementRepository,
                                    ClassRepository classRepository,
                                    UserRepository userRepository,
                                    EnrollmentRepository enrollmentRepository,
                                    ClassAccessPolicy classAccessPolicy,
                                    ClassesService classesService,
                                    ClassActivityWriter activityWriter,
                                    NotificationService notificationService,
                                    AnnouncementCommentRepository commentRepository,
                                    AnnouncementImageStorageService announcementImageStorage) {
        this.announcementRepository = announcementRepository;
        this.classRepository = classRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.classAccessPolicy = classAccessPolicy;
        this.classesService = classesService;
        this.activityWriter = activityWriter;
        this.notificationService = notificationService;
        this.commentRepository = commentRepository;
        this.announcementImageStorage = announcementImageStorage;
    }

    // ── Read ───────────────────────────────────────────────────────────

    /**
     * Loads a class after applying the board's no-leak read gate.
     *
     * <p>Exists so a controller rendering the board can obtain the
     * {@link ClassEntity} its sidebar needs under the same rule that guards the
     * feed, instead of falling back to {@code ClassesService.getViewable}, which
     * throws 403 and thereby discloses class existence.
     *
     * @param classId id of the class being opened
     * @param userId  id of the calling user
     * @param role    role of the calling user
     * @return the class, when the caller is admitted
     * @throws EntityNotFoundException when the class is absent or the caller is
     *                                 admitted by no access rule
     */
    @Transactional(readOnly = true)
    public ClassEntity getViewableForBoard(Long classId, Long userId, Role role) {
        ClassEntity clazz = loadClass(classId);
        classAccessPolicy.requireViewAccess(clazz, userId, role);
        return clazz;
    }

    /**
     * Returns one page of a class's announcements, newest first.
     *
     * <p>Returns the {@link Page} directly rather than a wrapper record because
     * {@code fragments/pager.html} calls {@code totalPages} / {@code number} /
     * {@code first} / {@code last} on a real Spring Data page.
     *
     * @param classId id of the class whose board is being read
     * @param userId  id of the calling user
     * @param role    role of the calling user
     * @param page    zero-based page index; negative values clamp to the first page
     * @return a page of announcement rows ordered newest-first
     * @throws EntityNotFoundException when the class is absent or the caller is
     *                                 admitted by no access rule
     */
    @Transactional(readOnly = true)
    public Page<AnnouncementRow> listForClass(Long classId, Long userId, Role role, int page) {
        ClassEntity clazz = loadClass(classId);
        classAccessPolicy.requireViewAccess(clazz, userId, role);

        // Page size is fixed server-side; only the index is client-supplied.
        int safePage = Math.max(0, page);
        Page<ClassAnnouncement> rows = announcementRepository
                .findByClassIdOrderByCreatedAtDescIdDesc(
                        classId, PageRequest.of(safePage, DEFAULT_ANNOUNCEMENT_PAGE_SIZE));

        // isPrivileged is the non-throwing predicate; getEditable would throw.
        boolean canManage = classAccessPolicy.isPrivileged(clazz, userId, role);
        Map<Long, String> authorNames = resolveAuthorNames(rows.getContent());

        List<Long> announcementIds = rows.getContent().stream()
                .map(ClassAnnouncement::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Long> commentCounts = resolveCommentCounts(announcementIds);
        Map<Long, List<CommentRow>> commentPreviews =
                resolveCommentPreviews(announcementIds, userId, canManage);

        return rows.map(a -> toRow(a, authorNames, canManage, commentCounts, commentPreviews));
    }

    // ── Write ──────────────────────────────────────────────────────────

    /**
     * Publishes a new announcement to the class and notifies every actively
     * enrolled student.
     *
     * @param classId id of the target class
     * @param rawHtml raw rich-text body straight from the compose form
     * @param userId  id of the authoring user
     * @param role    role of the authoring user
     * @return the persisted announcement
     * @throws org.springframework.security.access.AccessDeniedException when the
     *                                  caller may not write to this class
     * @throws IllegalArgumentException when the content is empty after
     *                                  sanitization or exceeds the length cap
     */
    @Transactional
    public ClassAnnouncement create(Long classId, String rawHtml, Long userId, Role role) {
        ClassEntity clazz = classesService.getEditable(classId, userId, role);
        String content = claimImagesAndValidate(rawHtml, userId);

        ClassAnnouncement saved = announcementRepository
                .save(new ClassAnnouncement(classId, content, userId));
        activityWriter.write(classId, ClassActivity.TYPE_ANNOUNCEMENT_CREATED,
                MSG_ANNOUNCEMENT_ACTIVITY_CREATED, userId);

        fanOutAnnouncementPublished(clazz, content);
        return saved;
    }

    /**
     * Replaces an existing announcement's content.
     *
     * <p>The gate order is fixed and observable: {@code getEditable} runs first,
     * then the class-ownership check, mirroring
     * {@code LessonsPublishService.publish}. A caller with no scope on the
     * addressed class therefore gets 403 and never reaches the ownership check,
     * which leaks nothing about the announcement's real class.
     *
     * @param classId        id of the class the caller addressed
     * @param announcementId id of the announcement to edit
     * @param rawHtml        raw rich-text body straight from the edit form
     * @param userId         id of the acting user
     * @param role           role of the acting user
     * @throws EntityNotFoundException when the announcement does not belong to
     *                                 the addressed class — surfaced as 404 to
     *                                 block path-variable enumeration
     */
    @Transactional
    public void update(Long classId, Long announcementId, String rawHtml, Long userId, Role role) {
        classesService.getEditable(classId, userId, role);
        ClassAnnouncement announcement = loadWithinClass(announcementId, classId);
        String content = claimImagesAndValidate(rawHtml, userId);

        announcement.updateContent(content);
        announcementRepository.save(announcement);
        activityWriter.write(classId, ClassActivity.TYPE_ANNOUNCEMENT_UPDATED,
                MSG_ANNOUNCEMENT_ACTIVITY_UPDATED, userId);
    }

    /**
     * Soft-deletes an announcement, retaining the row with {@code is_deleted = 1}
     * so it vanishes from both feeds without losing the record.
     *
     * <p>Gate order matches {@link #update}: role scope first, ownership second.
     *
     * @param classId        id of the class the caller addressed
     * @param announcementId id of the announcement to delete
     * @param userId         id of the acting user
     * @param role           role of the acting user
     * @throws EntityNotFoundException when the announcement does not belong to
     *                                 the addressed class
     */
    @Transactional
    public void delete(Long classId, Long announcementId, Long userId, Role role) {
        classesService.getEditable(classId, userId, role);
        ClassAnnouncement announcement = loadWithinClass(announcementId, classId);

        announcement.softDelete();
        announcementRepository.save(announcement);
        activityWriter.write(classId, ClassActivity.TYPE_ANNOUNCEMENT_DELETED,
                MSG_ANNOUNCEMENT_ACTIVITY_DELETED, userId);
    }

    // ── Internal helpers ───────────────────────────────────────────────

    /**
     * Promotes staged images to durable URLs, then enforces the content rules.
     *
     * <p><b>Order is fixed.</b> {@code claimIn} sanitizes internally as its first
     * step, so it receives the <i>raw</i> body and the emptiness and length
     * checks run on its return value — that is the final persisted body.
     * Sanitizing beforehand would sanitize twice.
     *
     * <p><b>The emptiness rule admits images but still rejects markup-only
     * bodies.</b> A body is empty only when it has neither visible text nor an
     * {@code <img>}. The text clause exists specifically so
     * {@code <script>x</script>} — non-empty before sanitization, empty after —
     * is still rejected; loosening this to a bare {@code content.isBlank()} would
     * accept image-only bodies but silently regress that rejection.
     *
     * <p>Must run inside the caller's transaction: {@code beginClaim} throws
     * unless a synchronized transaction is active, because it registers the
     * rollback and commit hooks that prevent orphaned objects.
     */
    private String claimImagesAndValidate(String rawHtml, Long userId) {
        String content = announcementImageStorage.beginClaim(userId).claimIn(rawHtml);
        if (content == null) {
            throw new IllegalArgumentException(MSG_ANNOUNCEMENT_EMPTY);
        }
        // An image carries meaning without text, so an image-only body is valid.
        if (Jsoup.parse(content).text().isBlank()
                && Jsoup.parse(content).select("img").isEmpty()) {
            throw new IllegalArgumentException(MSG_ANNOUNCEMENT_EMPTY);
        }
        if (content.length() > MAX_ANNOUNCEMENT_LENGTH) {
            throw new IllegalArgumentException(MSG_ANNOUNCEMENT_TOO_LONG);
        }
        return content;
    }

    private ClassEntity loadClass(Long classId) {
        return classRepository.findById(classId)
                .orElseThrow(() -> new EntityNotFoundException(MSG_CLASS_NOT_FOUND));
    }

    /**
     * Loads an announcement scoped to the addressed class. The class-scoped
     * lookup blocks path-variable enumeration (POSTing class C's URL with an
     * announcement id belonging to class D), following the precedent in
     * {@code LessonsPublishService.verifySectionBelongsToClass}.
     */
    private ClassAnnouncement loadWithinClass(Long announcementId, Long classId) {
        ClassAnnouncement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new EntityNotFoundException(MSG_ANNOUNCEMENT_NOT_FOUND));
        if (!Objects.equals(announcement.getClassId(), classId)) {
            throw new EntityNotFoundException(MSG_ANNOUNCEMENT_NOT_FOUND);
        }
        return announcement;
    }

    /** Batch-resolves author display names so the feed does not issue one query per row. */
    private Map<Long, String> resolveAuthorNames(List<ClassAnnouncement> rows) {
        List<Long> authorIds = rows.stream()
                .map(ClassAnnouncement::getCreatedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (authorIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(authorIds).stream()
                .filter(u -> u.getFullName() != null)
                .collect(Collectors.toMap(User::getId, User::getFullName,
                        (existing, duplicate) -> existing));
    }

    /**
     * Batch-resolves live comment counts for the page. Announcements with no
     * comments are absent from the grouped query, so callers default them to 0.
     */
    private Map<Long, Long> resolveCommentCounts(List<Long> announcementIds) {
        if (announcementIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : commentRepository.countByAnnouncementIds(announcementIds)) {
            counts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    /**
     * Batch-resolves the inline comment previews for the page in one query,
     * keeping the newest {@code DEFAULT_COMMENT_PREVIEW_SIZE} per announcement.
     *
     * <p>The query returns each group newest-first so truncation keeps the
     * <i>newest</i> comments; each kept group is then reversed because the feed
     * renders the oldest of those three at the top. Selecting oldest-first and
     * truncating would return the three oldest comments instead (design D6).
     */
    private Map<Long, List<CommentRow>> resolveCommentPreviews(
            List<Long> announcementIds, Long viewerId, boolean canManage) {
        if (announcementIds.isEmpty()) {
            return Map.of();
        }
        List<AnnouncementComment> comments =
                commentRepository.findForAnnouncementIds(announcementIds);
        if (comments.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> commenterNames = resolveCommenterNames(comments);
        Map<Long, List<CommentRow>> previews = new HashMap<>();
        for (AnnouncementComment c : comments) {
            List<CommentRow> group = previews.computeIfAbsent(
                    c.getAnnouncementId(), key -> new ArrayList<>());
            if (group.size() >= DEFAULT_COMMENT_PREVIEW_SIZE) {
                continue;
            }
            // Author may always delete their own; staff may delete any.
            boolean canDelete = canManage || Objects.equals(c.getCreatedBy(), viewerId);
            String name = commenterNames.get(c.getCreatedBy());
            group.add(new CommentRow(
                    c.getId(),
                    c.getContent(),
                    name,
                    AvatarStyles.label(name),
                    avatarGradientFor(c.getCreatedBy()),
                    c.getCreatedAt(),
                    canDelete));
        }
        // Flip each group so the oldest of the kept comments renders first.
        previews.values().forEach(Collections::reverse);
        return previews;
    }

    /** Batch-resolves comment author names so previews cost no per-row query. */
    private Map<Long, String> resolveCommenterNames(List<AnnouncementComment> comments) {
        List<Long> authorIds = comments.stream()
                .map(AnnouncementComment::getCreatedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (authorIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(authorIds).stream()
                .filter(u -> u.getFullName() != null)
                .collect(Collectors.toMap(User::getId, User::getFullName,
                        (existing, duplicate) -> existing));
    }

    private AnnouncementRow toRow(ClassAnnouncement a, Map<Long, String> authorNames,
                                  boolean canManage,
                                  Map<Long, Long> commentCounts,
                                  Map<Long, List<CommentRow>> commentPreviews) {
        // A row counts as edited only when updated_at moved past created_at.
        boolean edited = a.getCreatedAt() != null && a.getUpdatedAt() != null
                && a.getUpdatedAt().isAfter(a.getCreatedAt());
        String authorName = authorNames.get(a.getCreatedBy());
        return new AnnouncementRow(
                a.getId(),
                a.getContent(),
                authorName,
                AvatarStyles.label(authorName),
                avatarGradientFor(a.getCreatedBy()),
                a.getCreatedAt(),
                edited,
                canManage,
                commentCounts.getOrDefault(a.getId(), 0L),
                commentPreviews.getOrDefault(a.getId(), List.of()));
    }

    /**
     * Picks the avatar gradient for an author, keyed on the author id so the same
     * lecturer keeps one colour across every post in the feed.
     *
     * <p>Authorless rows (legacy data with a null {@code created_by}) fall back to
     * index 0 rather than throwing, matching the em-dash name fallback.
     */
    private static String avatarGradientFor(Long authorId) {
        // AvatarStyles.gradient floor-mods internally, so any int is in range.
        return AvatarStyles.gradient(authorId == null ? 0 : authorId.intValue());
    }

    /**
     * Creates a CLASS_ANNOUNCEMENT notification for every ACTIVE-enrolled student
     * in the class. Best-effort: failures per student are swallowed so one bad
     * recipient cannot abort the rest of the fan-out, and a fan-out failure can
     * never roll back the publish transaction (design D6).
     *
     * <p>{@code referenceId} is the <b>class</b> id, not the announcement id —
     * the deep-link target {@code /my/classes/{referenceId}/board} interpolates
     * it as a class id (design D8).
     */
    private void fanOutAnnouncementPublished(ClassEntity clazz, String content) {
        String body = clazz.getName() + ": " + excerptOf(content);
        try {
            enrollmentRepository
                    .findAllByClassIdAndStatusOrderByJoinedAtDesc(
                            clazz.getId(), Enrollment.STATUS_ACTIVE)
                    .forEach(e -> {
                        try {
                            notificationService.create(
                                    e.getUser().getId(),
                                    MSG_ANNOUNCEMENT_NOTIFY_TITLE,
                                    body,
                                    NotificationType.CLASS_ANNOUNCEMENT,
                                    NotificationType.REF_CLASS,
                                    clazz.getId()
                            );
                        } catch (Exception ignored) {
                            // Failure for one student must not stop other notifications.
                        }
                    });
        } catch (Exception ignored) {
            // Fan-out failure must not roll back the publish transaction.
        }
    }

    /** Flattens sanitized HTML to plain text, truncated for the notification body. */
    private static String excerptOf(String sanitizedHtml) {
        String text = Jsoup.parse(sanitizedHtml).text();
        return text.length() <= NOTIFY_EXCERPT_LENGTH
                ? text
                : text.substring(0, NOTIFY_EXCERPT_LENGTH) + "…";
    }
}