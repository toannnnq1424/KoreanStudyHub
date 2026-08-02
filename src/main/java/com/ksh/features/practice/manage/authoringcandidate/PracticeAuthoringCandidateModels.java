package com.ksh.features.practice.manage.authoringcandidate;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

/** Immutable command and result shapes at the AIM-2 backend boundary. */
public final class PracticeAuthoringCandidateModels {

    private PracticeAuthoringCandidateModels() {
    }

    public enum SourceKind {
        QUICK_EXCEL("practice-quick-excel-v1"),
        ADVANCED_EXCEL_V2("practice-excel-v2"),
        LEGACY_EXCEL_V1("practice-excel-v1"),
        PDF_AI("practice-pdf-authoring-output-v1");

        private final String contractVersion;

        SourceKind(String contractVersion) {
            this.contractVersion = contractVersion;
        }

        public String contractVersion() {
            return contractVersion;
        }
    }

    public enum SourceOperation {
        NONE,
        EXTRACT,
        GENERATE
    }

    public enum CandidateState {
        PARSED,
        NORMALIZED,
        VALIDATED,
        REVIEWING,
        READY_TO_APPLY,
        APPLIED,
        FAILED,
        REJECTED,
        EXPIRED
    }

    public enum ApplyResultCode {
        DRAFT_APPLIED,
        CONFLICT,
        REJECTED
    }

    public record SourceSnapshot(
            SourceKind kind,
            String contractVersion,
            String sourceDigest,
            String sourceRevision,
            String sourceName,
            SourceOperation operation,
            JsonNode aiExecution
    ) {
    }

    public record TargetRoute(
            Long draftId,
            int testNo,
            String skill,
            String lessonCode
    ) {
    }

    public record CreateCommand(
            Long actorId,
            SourceSnapshot source,
            TargetRoute target,
            JsonNode groups
    ) {
    }

    public record ReviewUpdateCommand(
            String candidateId,
            Long actorId,
            long expectedVersion,
            JsonNode groups,
            boolean acknowledgeWarnings
    ) {
    }

    public record ApplyCommand(
            String candidateId,
            UUID applyRequestId,
            Long actorId,
            long candidateVersion,
            String candidateDigest
    ) {
    }

    public record ApplyResult(
            ApplyResultCode result,
            String resultCode,
            Long draftId,
            Integer draftVersion,
            boolean replayed
    ) {
    }

    public record CandidateView(
            String candidateId,
            CandidateState state,
            long version,
            String contentDigest,
            JsonNode candidate,
            List<ValidationIssue> issues
    ) {
        public CandidateView {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }

    public record ValidationIssue(
            String severity,
            String code,
            String scope,
            String path,
            JsonNode sourceLocation,
            String messageVi,
            String remediation,
            boolean blocking
    ) {
        public static ValidationIssue error(
                String code,
                String scope,
                String path,
                String messageVi,
                String remediation) {
            return new ValidationIssue(
                    "ERROR", code, scope, path, null,
                    messageVi, remediation, true);
        }

        public static ValidationIssue warning(
                String code,
                String scope,
                String path,
                String messageVi,
                String remediation) {
            return new ValidationIssue(
                    "WARNING", code, scope, path, null,
                    messageVi, remediation, false);
        }

        public ValidationIssue withSourceLocation(JsonNode value) {
            return new ValidationIssue(
                    severity, code, scope, path, value,
                    messageVi, remediation, blocking);
        }
    }
}
