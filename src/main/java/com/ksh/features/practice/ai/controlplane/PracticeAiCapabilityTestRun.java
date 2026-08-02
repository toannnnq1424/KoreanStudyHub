package com.ksh.features.practice.ai.controlplane;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "practice_ai_capability_test_runs")
public class PracticeAiCapabilityTestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purpose_code", nullable = false, length = 64)
    private String purposeCode;

    @Column(name = "binding_revision", nullable = false)
    private long bindingRevision;

    @Column(name = "required_capability", nullable = false, length = 255)
    private String requiredCapability;

    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "bounded_error_code", length = 64)
    private String boundedErrorCode;

    @Column(name = "tested_by", nullable = false)
    private Long testedBy;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected PracticeAiCapabilityTestRun() {
    }

    public PracticeAiCapabilityTestRun(
            PracticeAiExecutionSnapshot snapshot,
            Long testedBy,
            LocalDateTime startedAt) {
        this.purposeCode = snapshot.purpose().name();
        this.bindingRevision = snapshot.bindingRevision();
        this.requiredCapability = snapshot.purpose().requiredCapabilities().stream()
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
        this.testedBy = testedBy;
        this.startedAt = startedAt;
    }

    public void complete(
            String status,
            long durationMs,
            String boundedErrorCode,
            LocalDateTime completedAt) {
        this.status = status;
        this.durationMs = Math.max(0, durationMs);
        this.boundedErrorCode = boundedErrorCode;
        this.completedAt = completedAt;
    }

    public Long getId() { return id; }
    public String getPurposeCode() { return purposeCode; }
    public long getBindingRevision() { return bindingRevision; }
    public String getRequiredCapability() { return requiredCapability; }
    public String getStatus() { return status; }
    public Long getDurationMs() { return durationMs; }
    public String getBoundedErrorCode() { return boundedErrorCode; }
    public Long getTestedBy() { return testedBy; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
