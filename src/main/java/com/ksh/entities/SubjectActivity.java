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

/** Immutable audit-log entry for administrative mutations on a subject. */
@Entity
@Table(name = "subject_activities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubjectActivity {

    public static final String TYPE_CREATED = "CREATED";
    public static final String TYPE_UPDATED = "UPDATED";
    public static final String TYPE_LEADER_ASSIGNED = "LEADER_ASSIGNED";
    public static final String TYPE_LEADER_CLEARED = "LEADER_CLEARED";
    public static final String TYPE_ACTIVATED = "ACTIVATED";
    public static final String TYPE_DEACTIVATED = "DEACTIVATED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "performed_by")
    private Long performedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public SubjectActivity(Long subjectId, String type, String message,
                           String metadata, Long performedBy) {
        this.subjectId = subjectId;
        this.type = type;
        this.message = message;
        this.metadata = metadata;
        this.performedBy = performedBy;
    }
}
