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

    public static final String TARGET_SET = "SET";
    public static final String TARGET_DRAFT = "DRAFT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "collaborator_id", nullable = false)
    private Long collaboratorId;

    @Column(name = "can_edit", nullable = false)
    private boolean canEdit;

    @Column(name = "can_publish", nullable = false)
    private boolean canPublish;

    @Column(name = "can_restore", nullable = false)
    private boolean canRestore;

    @Column(name = "can_manage_material", nullable = false)
    private boolean canManageMaterial;

    @Column(name = "granted_by", nullable = false)
    private Long grantedBy;

    @Column(name = "granted_at", insertable = false, updatable = false)
    private LocalDateTime grantedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected PracticeAuthoringCollaboration() {
    }

    public PracticeAuthoringCollaboration(String targetType, Long targetId, Long ownerId,
                                          Long collaboratorId, boolean canEdit,
                                          boolean canPublish, boolean canRestore,
                                          boolean canManageMaterial, Long grantedBy) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.ownerId = ownerId;
        this.collaboratorId = collaboratorId;
        this.canEdit = canEdit;
        this.canPublish = canPublish;
        this.canRestore = canRestore;
        this.canManageMaterial = canManageMaterial;
        this.grantedBy = grantedBy;
    }

    public Long getId() { return id; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public Long getOwnerId() { return ownerId; }
    public Long getCollaboratorId() { return collaboratorId; }
    public boolean isCanEdit() { return canEdit; }
    public boolean isCanPublish() { return canPublish; }
    public boolean isCanRestore() { return canRestore; }
    public boolean isCanManageMaterial() { return canManageMaterial; }
    public Long getGrantedBy() { return grantedBy; }
    public LocalDateTime getGrantedAt() { return grantedAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }

    public void updateGrants(boolean edit, boolean publish, boolean restore, boolean material,
                             Long grantorId) {
        this.canEdit = edit;
        this.canPublish = publish;
        this.canRestore = restore;
        this.canManageMaterial = material;
        this.grantedBy = grantorId;
        this.revokedAt = null;
    }

    public void revoke() {
        this.revokedAt = LocalDateTime.now();
    }
}
