package com.ksh.features.classes.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** View-model DTOs for the class detail page — Board tab. */
public class AnnouncementDtos {

    /** Shared display format for announcement timestamps. */
    private static final DateTimeFormatter STAMP_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    /**
     * A single comment shown under an announcement in the board feed.
     * Rendered by {@code templates/classes/detail-board.html} and
     * {@code templates/student/class-board.html}.
     *
     * <p>{@code content} is <b>plain text, not HTML</b>. It is stored verbatim
     * and MUST be rendered with {@code th:text}, never {@code th:utext} — that
     * is the only thing standing between a student-authored comment and stored
     * XSS (design D2).
     *
     * <p><b>{@code canDelete} is deliberately not named {@code canManage}</b>
     * like the sibling {@link AnnouncementRow}. They are different predicates:
     * {@code canManage} means edit-or-delete and is staff-only, whereas
     * {@code canDelete} is delete-only and is additionally true for the comment's
     * own author, who cannot edit. Reusing the name would imply an editing
     * affordance that does not exist — comment editing is out of scope.
     *
     * @param id           comment id, used to build the delete form action
     * @param content      plain-text comment body
     * @param authorName   display name of the comment's author
     * @param avatarLabel  author initials for the comment avatar
     * @param avatarGradient CSS gradient backing the comment avatar
     * @param createdAt    creation timestamp
     * @param canDelete    whether the current viewer may delete this comment
     */
    public record CommentRow(
            Long id,
            String content,
            String authorName,
            String avatarLabel,
            String avatarGradient,
            LocalDateTime createdAt,
            boolean canDelete
    ) {
        /**
         * Returns the creation timestamp formatted for display, substituting an
         * em-dash when absent.
         *
         * @return formatted timestamp, or {@code "—"} if {@code createdAt} is null
         */
        public String displayCreatedAt() {
            return createdAt == null ? "—" : STAMP_FORMAT.format(createdAt);
        }

        /**
         * Returns the author's display name, substituting an em-dash when the
         * user row carries no name.
         *
         * @return the author name, or {@code "—"} if absent
         */
        public String displayAuthor() {
            return authorName == null || authorName.isBlank() ? "—" : authorName;
        }
    }

    /**
     * Carries a rejected comment submission back to the re-rendered board.
     *
     * <p>A validation failure must not surface as a toast — it renders inline
     * beside the composer that produced it (`.claude/rules/flash-toast-drain.md`).
     * Several composers exist on one page, so {@code announcementId} tells the
     * template which one to re-open and mark.
     *
     * @param announcementId  announcement whose composer was submitted
     * @param rejectedContent the text the member typed, preserved so it is not lost
     * @param errorMessage    the validation message to show beside the composer
     */
    public record CommentFormError(
            Long announcementId,
            String rejectedContent,
            String errorMessage
    ) {
    }

    /**
     * A single announcement in the board feed.
     * Rendered by {@code templates/classes/detail-board.html} and
     * {@code templates/student/class-board.html}.
     *
     * <p>{@code content} is already sanitized — the service cleans it through
     * {@code HtmlSanitizer} before persistence — so templates render it with
     * {@code th:utext} without re-sanitizing.
     *
     * @param id             announcement id, used to build edit and delete form actions
     * @param content        sanitized rich-text HTML body
     * @param authorName     display name of the author
     * @param avatarLabel    author initials for the feed avatar, from {@code AvatarStyles}
     * @param avatarGradient CSS gradient backing the feed avatar, from {@code AvatarStyles}
     * @param createdAt      creation timestamp
     * @param edited         whether the announcement has been edited since creation
     * @param canManage      whether the current viewer may edit or delete this row
     * @param commentCount   total live comments on this announcement
     * @param comments       the newest few comments, ordered oldest-of-those first
     */
    public record AnnouncementRow(
            Long id,
            String content,
            String authorName,
            String avatarLabel,
            String avatarGradient,
            LocalDateTime createdAt,
            boolean edited,
            boolean canManage,
            long commentCount,
            List<CommentRow> comments
    ) {
        /**
         * Returns the creation timestamp formatted for display, substituting an
         * em-dash when absent.
         *
         * @return formatted timestamp, or {@code "—"} if {@code createdAt} is null
         */
        public String displayCreatedAt() {
            return createdAt == null ? "—" : STAMP_FORMAT.format(createdAt);
        }

        /**
         * Returns the author's display name, substituting an em-dash when the
         * user row carries no name.
         *
         * @return the author name, or {@code "—"} if absent
         */
        public String displayAuthor() {
            return authorName == null || authorName.isBlank() ? "—" : authorName;
        }

        /**
         * Returns the comment count label shown under the announcement body.
         *
         * @return e.g. {@code "0 bình luận"}, {@code "7 bình luận"}
         */
        public String displayCommentCount() {
            return commentCount + " bình luận";
        }
    }
}