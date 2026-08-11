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
 * JPA entity mapped to the {@code class_announcements} table.
 * Represents a lecturer-authored rich-text announcement broadcast to a single
 * {@link ClassEntity}, rendered on the class Board tab for teaching staff and
 * enrolled students alike.
 *
 * <p><b>Parent foreign key is a plain {@code Long}, not a {@code @ManyToOne}.</b>
 * {@code ClassEntity} carries {@code @SQLRestriction("is_deleted = 0")}, so a JPA
 * association would make any join silently drop announcements whose class has
 * been soft-deleted. That is exactly the hazard documented on
 * {@link ClassActivity} (lines 20-25), and this entity follows the same
 * established convention so it stays readable when its class is archived.
 *
 * <p>{@link SQLRestriction} on this entity ensures soft-deleted announcements
 * disappear from every default query, which is what removes them from both
 * feeds while retaining the row.
 *
 * <p>{@code content} holds HTML already cleaned by
 * {@code com.ksh.common.HtmlSanitizer} — sanitization happens on write, so the
 * read path renders it with {@code th:utext} without re-sanitizing.
 */
@Entity
@Table(name = "class_announcements")
@SQLRestriction("is_deleted = 0")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassAnnouncement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    /**
     * Populated by the MySQL column default; never written by JPA.
     */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Advanced server-side by {@code ON UPDATE CURRENT_TIMESTAMP} on every UPDATE
     * that changes a column, so {@code updatable = false} does not freeze it —
     * it only keeps JPA from writing the value. Mirrors
     * {@link ClassEntity} lines 76-80. The in-memory value is stale until the row
     * is re-read, which is harmless because the read path always issues a fresh
     * query and the write path redirects rather than rendering the mutated
     * instance.
     */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted")
    private boolean deleted = false;

    /**
     * Creates a new announcement for the publish flow.
     *
     * @param classId   id of the class this announcement belongs to
     * @param content   sanitized rich-text HTML body
     * @param createdBy id of the authoring user
     */
    public ClassAnnouncement(Long classId, String content, Long createdBy) {
        this.classId = classId;
        this.content = content;
        this.createdBy = createdBy;
    }

    // ── Business helpers ───────────────────────────────────────────

    /**
     * Replaces the announcement body. The caller is responsible for sanitizing
     * {@code content} first — the service layer owns that step so every write
     * path through it is covered.
     *
     * @param content sanitized rich-text HTML body
     */
    public void updateContent(String content) {
        this.content = content;
    }

    /**
     * Marks this announcement as soft-deleted. After this call the
     * {@link SQLRestriction} on the entity excludes it from all default queries,
     * so it vanishes from both board feeds while the row is retained.
     */
    public void softDelete() {
        this.deleted = true;
    }
}