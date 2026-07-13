package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "practice_authoring_collaborations")
public class PracticeAuthoringCollaboration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "set_id", nullable = false)
    private Long setId;

    @Column(name = "collaborator_id", nullable = false)
    private Long collaboratorId;

    @Column(name = "granted_at", insertable = false, updatable = false)
    private LocalDateTime grantedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected PracticeAuthoringCollaboration() {
    }

    public PracticeAuthoringCollaboration(Long setId, Long collaboratorId) {
        this.setId = setId;
        this.collaboratorId = collaboratorId;
    }

    public Long getId() { return id; }
    public Long getSetId() { return setId; }
    public Long getCollaboratorId() { return collaboratorId; }
    public LocalDateTime getGrantedAt() { return grantedAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }

    public void reactivate() {
        this.revokedAt = null;
    }

    public void revoke() {
        this.revokedAt = LocalDateTime.now();
    }
}
