package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "practice_governance_audit_events")
public class PracticeGovernanceAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action_code", nullable = false, length = 80)
    private String actionCode;

    @Column(name = "target_type", nullable = false, length = 40)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "source_version_id")
    private Long sourceVersionId;

    @Column(name = "override_used", nullable = false)
    private boolean overrideUsed;

    @Column(length = 500)
    private String reason;

    @Column(name = "before_json", columnDefinition = "LONGTEXT")
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "LONGTEXT")
    private String afterJson;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PracticeGovernanceAuditEvent() {
    }

    public PracticeGovernanceAuditEvent(String actionCode, String targetType, Long targetId,
                                        Long ownerId, Long actorId, Long sourceVersionId,
                                        boolean overrideUsed, String reason,
                                        String beforeJson, String afterJson) {
        this.actionCode = actionCode;
        this.targetType = targetType;
        this.targetId = targetId;
        this.ownerId = ownerId;
        this.actorId = actorId;
        this.sourceVersionId = sourceVersionId;
        this.overrideUsed = overrideUsed;
        this.reason = reason;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
    }

    public Long getId() { return id; }
    public String getActionCode() { return actionCode; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public Long getOwnerId() { return ownerId; }
    public Long getActorId() { return actorId; }
    public Long getSourceVersionId() { return sourceVersionId; }
    public boolean isOverrideUsed() { return overrideUsed; }
    public String getReason() { return reason; }
    public String getBeforeJson() { return beforeJson; }
    public String getAfterJson() { return afterJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
