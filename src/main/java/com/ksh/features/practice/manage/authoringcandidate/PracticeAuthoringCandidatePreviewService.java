package com.ksh.features.practice.manage.authoringcandidate;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateState;
import com.ksh.features.practice.manage.service.PracticeDraftContractService;
import com.ksh.features.practice.manage.service.PracticeDraftPreviewService;
import com.ksh.features.practice.manage.validator.PracticeDraftValidator;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Clock;
import java.util.Objects;

/** Builds the exact candidate + full-draft learner projection in memory. */
@Service
public class PracticeAuthoringCandidatePreviewService {

    private final PracticeAuthoringCandidateRepository candidateRepository;
    private final PracticeDraftRepository draftRepository;
    private final PracticeAuthorizationService authorizationService;
    private final PracticeAuthoringCandidateDraftProjector projector;
    private final PracticeDraftContractService draftContractService;
    private final PracticeDraftValidator draftValidator;
    private final PracticeDraftPreviewService draftPreviewService;
    private final PracticeAuthoringCandidateMaterialAuthority materialAuthority;
    private final PracticeAuthoringCandidateJson candidateJson;
    private final Clock clock;

    @Autowired
    public PracticeAuthoringCandidatePreviewService(
            PracticeAuthoringCandidateRepository candidateRepository,
            PracticeDraftRepository draftRepository,
            PracticeAuthorizationService authorizationService,
            PracticeAuthoringCandidateDraftProjector projector,
            PracticeDraftContractService draftContractService,
            PracticeDraftValidator draftValidator,
            PracticeDraftPreviewService draftPreviewService,
            PracticeAuthoringCandidateMaterialAuthority materialAuthority,
            PracticeAuthoringCandidateJson candidateJson) {
        this(candidateRepository, draftRepository, authorizationService,
                projector, draftContractService, draftValidator,
                draftPreviewService, materialAuthority, candidateJson,
                Clock.systemUTC());
    }

    PracticeAuthoringCandidatePreviewService(
            PracticeAuthoringCandidateRepository candidateRepository,
            PracticeDraftRepository draftRepository,
            PracticeAuthorizationService authorizationService,
            PracticeAuthoringCandidateDraftProjector projector,
            PracticeDraftContractService draftContractService,
            PracticeDraftValidator draftValidator,
            PracticeDraftPreviewService draftPreviewService,
            PracticeAuthoringCandidateMaterialAuthority materialAuthority,
            PracticeAuthoringCandidateJson candidateJson,
            Clock clock) {
        this.candidateRepository = candidateRepository;
        this.draftRepository = draftRepository;
        this.authorizationService = authorizationService;
        this.projector = projector;
        this.draftContractService = draftContractService;
        this.draftValidator = draftValidator;
        this.draftPreviewService = draftPreviewService;
        this.materialAuthority = Objects.requireNonNull(
                materialAuthority, "material authority");
        this.candidateJson = candidateJson;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(readOnly = true)
    public PreviewResult preview(
            String candidateId,
            Long actorId,
            long submittedVersion,
            String submittedDigest) {
        PracticeAuthoringCandidate candidate = candidateRepository
                .findByIdAndOwnerIdForRead(candidateId, actorId)
                .orElseThrow(PracticeAuthoringCandidatePreviewService::denied);
        authorizationService.requireDraft(
                candidate.getTargetDraftId(), actorId, PracticeAction.READ);
        requireCurrent(candidate, submittedVersion, submittedDigest);
        if (candidate.isExpiredAt(LocalDateTime.ofInstant(
                clock.instant(), ZoneOffset.UTC))) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_EXPIRED", "Candidate đã hết hạn.");
        }
        if (candidate.getState() == CandidateState.APPLIED
                || candidate.getState() == CandidateState.REJECTED
                || candidate.getState() == CandidateState.FAILED
                || candidate.getState() == CandidateState.EXPIRED) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_NOT_PREVIEWABLE",
                    "Candidate không còn ở trạng thái có thể xem trước.");
        }

        PracticeDraft draft = draftRepository.findByIdForRead(
                        candidate.getTargetDraftId())
                .orElseThrow(() -> new PracticeAuthoringCandidateException(
                        "CANDIDATE_TARGET_NOT_FOUND",
                        "Bản nháp target không tồn tại."));
        int currentDraftVersion = version(draft);
        if (currentDraftVersion != candidate.getBaseDraftVersion()) {
            throw new PracticeAuthoringCandidateException(
                    "TARGET_DRAFT_VERSION_CONFLICT",
                    "Bản nháp đã thay đổi; hãy tạo lại candidate trước khi xem.");
        }

        try {
            ObjectNode envelope = candidateJson.readObject(
                    candidate.getCandidateJson());
            ObjectNode projected = projector.append(
                    draft.getDraftJson(), candidate, envelope);
            String normalized = draftContractService.normalize(
                    projected, candidate.getSourceKind().name()).json();
            PracticeDraftValidator.ValidationResult validation =
                    draftValidator.validate(normalized);
            if (validation.hasBlocking()) {
                throw new PracticeAuthoringCandidateException(
                        "CANDIDATE_PREVIEW_VALIDATION_FAILED",
                        "Bản xem trước đầy đủ còn lỗi chặn theo validator chuẩn.");
            }
            materialAuthority.requireAuthorized(
                    candidate.getTargetDraftId(), normalized);
            return new PreviewResult(
                    currentDraftVersion,
                    draftPreviewService.preview(normalized));
        } catch (PracticeAuthoringCandidateException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_PREVIEW_VALIDATION_FAILED",
                    "Không thể dựng bản xem trước learner an toàn.");
        }
    }

    private static void requireCurrent(
            PracticeAuthoringCandidate candidate,
            long submittedVersion,
            String submittedDigest) {
        String digest = PracticeAuthoringCandidateJson.stripDigestPrefix(
                submittedDigest);
        if (candidate.getLockVersion() != submittedVersion
                || !digest.matches("[0-9a-f]{64}")
                || !Objects.equals(candidate.getContentDigest(), digest)) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_VERSION_CONFLICT",
                    "Candidate hoặc digest đã thay đổi; hãy tải lại.");
        }
    }

    private static int version(PracticeDraft draft) {
        return draft.getVersion() == null ? 0 : draft.getVersion();
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException(
                "Bạn không có quyền xem authoring candidate này.");
    }

    public record PreviewResult(
            int baseDraftVersion,
            PracticeDraftPreviewService.DraftDeliveryPreview delivery) {
    }
}
