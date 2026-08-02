package com.ksh.features.practice.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateApplyService;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ApplyResult;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ApplyResultCode;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateState;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateView;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ReviewUpdateCommand;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidatePreviewService;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateService;
import com.ksh.features.practice.manage.controller.PracticeAuthoringCandidateReviewController;
import com.ksh.security.KshUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeAuthoringCandidateReviewControllerTest {

    private static final String CANDIDATE_ID =
            "11111111-1111-4111-8111-111111111111";

    private PracticeAuthoringCandidateService candidateService;
    private PracticeAuthoringCandidatePreviewService previewService;
    private PracticeAuthoringCandidateApplyService applyService;
    private PracticeAuthoringCandidateReviewController controller;
    private KshUserDetails user;

    @BeforeEach
    void setUp() {
        candidateService = mock(PracticeAuthoringCandidateService.class);
        previewService = mock(PracticeAuthoringCandidatePreviewService.class);
        applyService = mock(PracticeAuthoringCandidateApplyService.class);
        controller = new PracticeAuthoringCandidateReviewController(
                candidateService, previewService, applyService);
        user = mock(KshUserDetails.class);
        when(user.getId()).thenReturn(101L);
    }

    @Test
    void reviewSurfaceIsExactLecturerOnlyAndPageAuthorizesOwner() {
        PreAuthorize guard = PracticeAuthoringCandidateReviewController.class
                .getAnnotation(PreAuthorize.class);
        when(candidateService.get(CANDIDATE_ID, 101L)).thenReturn(view());
        ExtendedModelMap model = new ExtendedModelMap();

        String template = controller.page(CANDIDATE_ID, user, model);

        assertThat(guard.value()).isEqualTo("hasRole('LECTURER')");
        assertThat(template).isEqualTo("practice/manage/candidate-review");
        assertThat(model.get("candidateId")).isEqualTo(CANDIDATE_ID);
        verify(candidateService).get(CANDIDATE_ID, 101L);
    }

    @Test
    void anotherLecturerGetsForbiddenEnvelopeWithoutCandidateData() {
        when(candidateService.get(CANDIDATE_ID, 101L))
                .thenThrow(new AccessDeniedException("denied"));

        var response = controller.data(CANDIDATE_ID, user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("code")).isEqualTo("CANDIDATE_NOT_AUTHORIZED");
        assertThat(body.containsKey("candidateId")).isFalse();
    }

    @Test
    void reviewPassesBothSubmittedVersionAndDigest() {
        ObjectNode groups = new ObjectMapper().createObjectNode();
        var payload = new PracticeAuthoringCandidateReviewController.ReviewPayload(
                4L, "sha256:" + "a".repeat(64), groups, true);
        when(candidateService.updateReview(any())).thenReturn(view());

        var response = controller.updateReview(
                CANDIDATE_ID, payload, user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(candidateService).updateReview(new ReviewUpdateCommand(
                CANDIDATE_ID, 101L, 4L,
                "sha256:" + "a".repeat(64), groups, true));
    }

    @Test
    void successfulExplicitApplyReturnsExistingEditorRoute() {
        UUID requestId = UUID.fromString(
                "22222222-2222-4222-8222-222222222222");
        var payload = new PracticeAuthoringCandidateReviewController.ApplyPayload(
                requestId.toString(), 4L, "sha256:" + "a".repeat(64));
        when(applyService.apply(any())).thenReturn(new ApplyResult(
                ApplyResultCode.DRAFT_APPLIED, "DRAFT_APPLIED",
                5001L, 7, false));

        var response = controller.apply(CANDIDATE_ID, payload, user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("editorUrl"))
                .isEqualTo("/practice/manage/drafts/5001");
        assertThat(body.get("result")).isEqualTo("DRAFT_APPLIED");
        assertThat(body.get("replayed")).isEqualTo(false);
    }

    @Test
    void missingSubmittedVersionFailsBeforeReviewService() {
        ObjectNode groups = new ObjectMapper().createObjectNode();
        var payload = new PracticeAuthoringCandidateReviewController.ReviewPayload(
                null, "sha256:" + "a".repeat(64), groups, false);

        var response = controller.updateReview(CANDIDATE_ID, payload, user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("code")).isEqualTo("CANDIDATE_REVIEW_INVALID");
        verify(candidateService, never()).updateReview(any());
    }

    @Test
    void staleApplyResultUsesConflictStatusSoClientReloads() {
        UUID requestId = UUID.fromString(
                "22222222-2222-4222-8222-222222222222");
        var payload = new PracticeAuthoringCandidateReviewController.ApplyPayload(
                requestId.toString(), 4L, "sha256:" + "a".repeat(64));
        when(applyService.apply(any())).thenReturn(new ApplyResult(
                ApplyResultCode.REJECTED, "CANDIDATE_VERSION_CONFLICT",
                5001L, null, false));

        var response = controller.apply(CANDIDATE_ID, payload, user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("resultCode"))
                .isEqualTo("CANDIDATE_VERSION_CONFLICT");
    }

    private static CandidateView view() {
        ObjectNode candidate = new ObjectMapper().createObjectNode();
        ObjectNode target = candidate.putObject("target");
        target.put("draftId", 5001L);
        target.put("baseDraftVersion", 0);
        target.put("testNo", 1);
        target.put("skill", "READING");
        target.put("lessonCode", "R1");
        candidate.putArray("groups");
        return new CandidateView(
                CANDIDATE_ID, CandidateState.REVIEWING, 4L,
                "sha256:" + "a".repeat(64), candidate, List.of());
    }
}
