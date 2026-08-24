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

import java.time.LocalDateTime;

/**
 * JPA entity mapped to the {@code activity_classes} table.
 * Represents an immutable audit-log entry for every mutation performed on a
 * {@link ClassEntity}: CREATED, UPDATED, STARTED, COMPLETED, CANCELLED, DELETED, etc.
 *
 * <p><b>Important:</b> {@code classId} is stored as a plain {@code Long} rather
 * than a {@code @ManyToOne} association to {@link ClassEntity}. This is intentional:
 * {@code ClassEntity} carries an {@code @SQLRestriction("is_deleted = 0")} filter,
 * which means a JPA join would silently exclude audit rows whose target class has
 * been soft-deleted. Keeping the foreign key as a bare {@code Long} ensures the
 * audit log remains intact and independent of the class lifecycle.
 */
@Entity
@Table(name = "activity_classes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassActivity {

    public static final String TYPE_CREATED = "CREATED";
    public static final String TYPE_UPDATED = "UPDATED";
    public static final String TYPE_STARTED = "STARTED";
    public static final String TYPE_COMPLETED = "COMPLETED";
    public static final String TYPE_CANCELLED = "CANCELLED";
    public static final String TYPE_DELETED = "DELETED";
    /** A student membership became active after an owner-approved request. */
    public static final String TYPE_MEMBER_JOINED = "MEMBER_JOINED";
    /** A student left the class via {@code /my/classes/{id}/leave}. */
    public static final String TYPE_MEMBER_LEFT = "MEMBER_LEFT";
    /** A board announcement was published to the class. */
    public static final String TYPE_ANNOUNCEMENT_CREATED = "TYPE_ANNOUNCEMENT_CREATED";
    /** An existing board announcement had its content edited. */
    public static final String TYPE_ANNOUNCEMENT_UPDATED = "TYPE_ANNOUNCEMENT_UPDATED";
    /** A board announcement was soft-deleted. */
    public static final String TYPE_ANNOUNCEMENT_DELETED = "TYPE_ANNOUNCEMENT_DELETED";
    /**
     * A member commented on a board announcement. The value repeats the constant
     * name, matching the three TYPE_ANNOUNCEMENT_* constants above — unusual, but
     * the established convention in this file, and both fit the
     * {@code activity_classes.type VARCHAR(50)} column.
     */
    public static final String TYPE_ANNOUNCEMENT_COMMENT_CREATED =
            "TYPE_ANNOUNCEMENT_COMMENT_CREATED";
    /** A board announcement comment was soft-deleted. */
    public static final String TYPE_ANNOUNCEMENT_COMMENT_DELETED =
            "TYPE_ANNOUNCEMENT_COMMENT_DELETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "JSON")
    private String metadata;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public ClassActivity(Long classId, String type, String description,
                         String metadata, Long createdBy) {
        this.classId = classId;
        this.type = type;
        this.description = description;
        this.metadata = metadata;
        this.createdBy = createdBy;
    }
}
