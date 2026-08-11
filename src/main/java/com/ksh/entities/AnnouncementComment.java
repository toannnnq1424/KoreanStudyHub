package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * JPA entity mapped to the {@code announcement_comments} table.
 * Represents a member-authored plain-text comment on a {@link ClassAnnouncement},
 * rendered under the announcement on both the lecturer and student board feeds.
 *
 * <p><b>Parent foreign keys are plain {@code Long}s, not {@code @ManyToOne}
 * associations.</b> Both {@link ClassEntity} and {@link ClassAnnouncement} carry
 * {@code @SQLRestriction("is_deleted = 0")}, so a JPA association would make any
 * join silently drop comments whose parent has been soft-deleted. That is the
 * hazard documented on {@link ClassActivity} (lines 20-25), and this entity
 * follows the same established convention.
 *
 * <p>{@code classId} is carried denormalized alongside {@code announcementId} so
 * the authorization and class-scope checks need no join through
 * {@code class_announcements} on every comment read and write.
 *
 * <p><b>{@code content} is plain text, never HTML.</b> It is stored verbatim
 * without passing through {@code HtmlSanitizer} and is rendered with
 * {@code th:text}, never {@code th:utext} (design D2), so markup can never reach
 * the DOM as markup. Storing pre-escaped text would double-escape at render.
 *
 * <p>{@link SQLRestriction} on this entity ensures soft-deleted comments
 * disappear from every default query, which removes them from both feeds and
 * from the per-announcement count while retaining the row.
 */
@Entity
@Table(name = "announcement_comments")
@SQLRestriction("is_deleted = 0")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnnouncementComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "announcement_id", nullable = false)
    private Long announcementId;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    /** Populated by the MySQL column default; never written by JPA. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Advanced server-side by {@code ON UPDATE CURRENT_TIMESTAMP}, mirroring
     * {@link ClassAnnouncement}. {@code updatable = false} only keeps JPA from
     * writing the value; it does not freeze it.
     */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted")
    private boolean deleted = false;

    /**
     * Creates a new comment for the create flow.
     *
     * @param announcementId id of the announcement being commented on
     * @param classId        id of the owning class, carried denormalized
     * @param content        trimmed plain-text body
     * @param createdBy      id of the authoring user
     */
    public AnnouncementComment(Long announcementId, Long classId, String content, Long createdBy) {
        this.announcementId = announcementId;
        this.classId = classId;
        this.content = content;
        this.createdBy = createdBy;
    }

    // ── Business helpers ───────────────────────────────────────────

    /**
     * Marks this comment as soft-deleted. After this call the
     * {@link SQLRestriction} on the entity excludes it from all default queries,
     * so it vanishes from both feeds and from the count while the row is kept.
     */
    public void softDelete() {
        this.deleted = true;
    }
}