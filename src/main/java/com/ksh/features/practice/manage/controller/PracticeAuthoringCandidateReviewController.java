package com.ksh.features.practice.manage.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ksh.features.practice.assessment.ObjectiveExplanationStrategyRegistry;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateApplyService;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateException;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ApplyCommand;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ApplyResult;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ApplyResultCode;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateView;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ReviewUpdateCommand;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidatePreviewService;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateService;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/practice/manage/authoring-candidates")
@PreAuthorize(Roles.PREAUTH_LECTURER)
public class PracticeAuthoringCandidateReviewController {

    private final PracticeAuthoringCandidateService candidateService;
    private final PracticeAuthoringCandidatePreviewService previewService;
    private final PracticeAuthoringCandidateApplyService applyService;

    public PracticeAuthoringCandidateReviewController(
            PracticeAuthoringCandidateService candidateService,
            PracticeAuthoringCandidatePreviewService previewService,
            PracticeAuthoringCandidateApplyService applyService) {
        this.candidateService = candidateService;
        this.previewService = previewService;
        this.applyService = applyService;
    }

    @GetMapping("/{candidateId}")
    public String page(
            @PathVariable("candidateId") String candidateId,
            @AuthenticationPrincipal KshUserDetails user,
            Model model) {
        CandidateView candidate = candidateService.get(candidateId, user.getId());
        model.addAttribute("candidateId", candidate.candidateId());
        model.addAttribute(
                "objectiveExplanationStrategyCatalog",
                ObjectiveExplanationStrategyRegistry.catalog());
        return "practice/manage/candidate-review";
    }

    @GetMapping("/{candidateId}/data")
    @ResponseBody
    public ResponseEntity<?> data(
            @PathVariable("candidateId") String candidateId,
            @AuthenticationPrincipal KshUserDetails user) {
        try {
            return ResponseEntity.ok(candidateService.get(
                    candidateId, user.getId()));
        } catch (AccessDeniedException exception) {
            return forbidden();
        }
    }

    @PostMapping("/{candidateId}/review")
    @ResponseBody
    public ResponseEntity<?> updateReview(
            @PathVariable("candidateId") String candidateId,
            @RequestBody ReviewPayload payload,
            @AuthenticationPrincipal KshUserDetails user) {
        try {
            return ResponseEntity.ok(candidateService.updateReview(
                    new ReviewUpdateCommand(
                            candidateId,
                            user.getId(),
                            submittedVersion(payload == null
                                    ? null : payload.candidateVersion()),
                            submittedDigest(payload == null
                                    ? null : payload.candidateDigest()),
                            payload.groups(),
                            payload.acknowledgeWarnings())));
        } catch (PracticeAuthoringCandidateException exception) {
            return candidateFailure(exception);
        } catch (ObjectOptimisticLockingFailureException exception) {
            return optimisticConflict();
        } catch (AccessDeniedException exception) {
            return forbidden();
        } catch (IllegalArgumentException exception) {
            return badRequest("CANDIDATE_REVIEW_INVALID", exception.getMessage());
        }
    }

    @PostMapping("/{candidateId}/ready")
    @ResponseBody
    public ResponseEntity<?> markReady(
            @PathVariable("candidateId") String candidateId,
            @RequestBody VersionDigestPayload payload,
            @AuthenticationPrincipal KshUserDetails user) {
        try {
            return ResponseEntity.ok(candidateService.markReady(
                    candidateId, user.getId(),
                    submittedVersion(payload == null
                            ? null : payload.candidateVersion()),
                    submittedDigest(payload == null
                            ? null : payload.candidateDigest())));
        } catch (PracticeAuthoringCandidateException exception) {
            return candidateFailure(exception);
        } catch (ObjectOptimisticLockingFailureException exception) {
            return optimisticConflict();
        } catch (AccessDeniedException exception) {
            return forbidden();
        } catch (IllegalArgumentException exception) {
            return badRequest("CANDIDATE_IDENTITY_INVALID",
                    exception.getMessage());
        }
    }

    @PostMapping("/{candidateId}/reject")
    @ResponseBody
    public ResponseEntity<?> reject(
            @PathVariable("candidateId") String candidateId,
            @RequestBody VersionDigestPayload payload,
            @AuthenticationPrincipal KshUserDetails user) {
        try {
            return ResponseEntity.ok(candidateService.reject(
                    candidateId, user.getId(),
                    submittedVersion(payload == null
                            ? null : payload.candidateVersion()),
                    submittedDigest(payload == null
                            ? null : payload.candidateDigest())));
        } catch (PracticeAuthoringCandidateException exception) {
            return candidateFailure(exception);
        } catch (ObjectOptimisticLockingFailureException exception) {
            return optimisticConflict();
        } catch (AccessDeniedException exception) {
            return forbidden();
        } catch (IllegalArgumentException exception) {
            return badRequest("CANDIDATE_IDENTITY_INVALID",
                    exception.getMessage());
        }
    }

    @PostMapping("/{candidateId}/learner-preview")
    @ResponseBody
    public ResponseEntity<?> learnerPreview(
            @PathVariable("candidateId") String candidateId,
            @RequestBody VersionDigestPayload payload,
            @AuthenticationPrincipal KshUserDetails user) {
        try {
            return ResponseEntity.ok(previewService.preview(
                    candidateId, user.getId(),
                    submittedVersion(payload == null
                            ? null : payload.candidateVersion()),
                    submittedDigest(payload == null
                            ? null : payload.candidateDigest())));
        } catch (PracticeAuthoringCandidateException exception) {
            return candidateFailure(exception);
        } catch (AccessDeniedException exception) {
            return forbidden();
        } catch (IllegalArgumentException exception) {
            return badRequest("CANDIDATE_IDENTITY_INVALID",
                    exception.getMessage());
        }
    }

    @PostMapping("/{candidateId}/apply")
    @ResponseBody
    public ResponseEntity<?> apply(
            @PathVariable("candidateId") String candidateId,
            @RequestBody ApplyPayload payload,
            @AuthenticationPrincipal KshUserDetails user) {
        try {
            UUID requestId = submittedApplyRequestId(payload == null
                    ? null : payload.applyRequestId());
            ApplyResult result = applyService.apply(new ApplyCommand(
                    candidateId, requestId, user.getId(),
                    submittedVersion(payload == null
                            ? null : payload.candidateVersion()),
                    submittedDigest(payload == null
                            ? null : payload.candidateDigest())));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("result", result.result().name());
            body.put("resultCode", result.resultCode());
            body.put("draftId", result.draftId());
            body.put("draftVersion", result.draftVersion());
            body.put("replayed", result.replayed());
            if (result.result() == ApplyResultCode.DRAFT_APPLIED) {
                body.put("editorUrl",
                        "/practice/manage/drafts/" + result.draftId());
                return ResponseEntity.ok(body);
            }
            HttpStatus status = result.result() == ApplyResultCode.CONFLICT
                    || result.resultCode().contains("CONFLICT")
                    ? HttpStatus.CONFLICT
                    : HttpStatus.UNPROCESSABLE_ENTITY;
            return ResponseEntity.status(status).body(body);
        } catch (PracticeAuthoringCandidateException exception) {
            return candidateFailure(exception);
        } catch (ObjectOptimisticLockingFailureException exception) {
            return optimisticConflict();
        } catch (IllegalArgumentException exception) {
            return badRequest(
                    "APPLY_REQUEST_INVALID",
                    exception.getMessage());
        } catch (AccessDeniedException exception) {
            return forbidden();
        }
    }

    private static ResponseEntity<Map<String, String>> candidateFailure(
            PracticeAuthoringCandidateException exception) {
        HttpStatus status = exception.code().contains("CONFLICT")
                ? HttpStatus.CONFLICT
                : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(Map.of(
                "code", exception.code(),
                "error", exception.getMessage()));
    }

    private static ResponseEntity<Map<String, String>> optimisticConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "CANDIDATE_VERSION_CONFLICT",
                "error", "Candidate đã thay đổi; hãy tải lại trước khi tiếp tục."));
    }

    private static ResponseEntity<Map<String, String>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "code", "CANDIDATE_NOT_AUTHORIZED",
                "error", "Bạn không có quyền truy cập authoring candidate này."));
    }

    private static ResponseEntity<Map<String, String>> badRequest(
            String code, String message) {
        return ResponseEntity.badRequest().body(Map.of(
                "code", code,
                "error", message == null || message.isBlank()
                        ? "Yêu cầu candidate không hợp lệ." : message));
    }

    private static long submittedVersion(Long value) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(
                    "candidateVersion phải được gửi rõ ràng và không âm.");
        }
        return value;
    }

    private static String submittedDigest(String value) {
        String normalized = value == null ? ""
                : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("(?:sha256:)?[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "candidateDigest phải là SHA-256 hợp lệ.");
        }
        return normalized;
    }

    private static UUID submittedApplyRequestId(String value) {
        try {
            return UUID.fromString(value == null ? "" : value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "applyRequestId phải là UUID hợp lệ.");
        }
    }

    public record ReviewPayload(
            Long candidateVersion,
            String candidateDigest,
            JsonNode groups,
            boolean acknowledgeWarnings) {
    }

    public record VersionDigestPayload(
            Long candidateVersion,
            String candidateDigest) {
    }

    public record ApplyPayload(
            String applyRequestId,
            Long candidateVersion,
            String candidateDigest) {
    }
}
