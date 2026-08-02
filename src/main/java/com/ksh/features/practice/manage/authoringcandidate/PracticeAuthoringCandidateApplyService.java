package com.ksh.features.practice.manage.authoringcandidate;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ApplyCommand;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ApplyResult;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ApplyResultCode;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateState;
import com.ksh.features.practice.manage.service.PracticeDraftContractService;
import com.ksh.features.practice.manage.validator.PracticeDraftValidator;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Service
public class PracticeAuthoringCandidateApplyService {

    private final PracticeAuthoringCandidateRepository candidateRepository;
    private final PracticeAuthoringCandidateApplyEventRepository eventRepository;
    private final PracticeDraftRepository draftRepository;
    private final PracticeAuthorizationService authorizationService;
    private final PracticeAuthoringCandidateDraftProjector projector;
    private final PracticeDraftContractService draftContractService;
    private final PracticeDraftValidator draftValidator;
    private final PracticeAuthoringCandidateJson candidateJson;
    private final Clock clock;

    @Autowired
    public PracticeAuthoringCandidateApplyService(
            PracticeAuthoringCandidateRepository candidateRepository,
            PracticeAuthoringCandidateApplyEventRepository eventRepository,
            PracticeDraftRepository draftRepository,
            PracticeAuthorizationService authorizationService,
            PracticeAuthoringCandidateDraftProjector projector,
            PracticeDraftContractService draftContractService,
            PracticeDraftValidator draftValidator,
            PracticeAuthoringCandidateJson candidateJson) {
        this(candidateRepository, eventRepository, draftRepository,
                authorizationService, projector, draftContractService,
                draftValidator, candidateJson, Clock.systemUTC());
    }

    PracticeAuthoringCandidateApplyService(
            PracticeAuthoringCandidateRepository candidateRepository,
            PracticeAuthoringCandidateApplyEventRepository eventRepository,
            PracticeDraftRepository draftRepository,
            PracticeAuthorizationService authorizationService,
            PracticeAuthoringCandidateDraftProjector projector,
            PracticeDraftContractService draftContractService,
            PracticeDraftValidator draftValidator,
            PracticeAuthoringCandidateJson candidateJson,
            Clock clock) {
        this.candidateRepository = candidateRepository;
        this.eventRepository = eventRepository;
        this.draftRepository = draftRepository;
        this.authorizationService = authorizationService;
        this.projector = projector;
        this.draftContractService = draftContractService;
        this.draftValidator = draftValidator;
        this.candidateJson = candidateJson;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public ApplyResult apply(ApplyCommand command) {
        requireCommand(command);
        PracticeAuthoringCandidate visible = candidateRepository
                .findByIdAndOwnerId(command.candidateId(), command.actorId())
                .orElseThrow(PracticeAuthoringCandidateApplyService::denied);
        authorizationService.requireDraft(
                visible.getTargetDraftId(), command.actorId(),
                PracticeAction.EDIT);

        // Stable lock order: candidate first, exact draft second.
        PracticeAuthoringCandidate candidate = candidateRepository
                .findByIdForUpdate(command.candidateId())
                .filter(value -> Objects.equals(
                        value.getOwnerId(), command.actorId()))
                .orElseThrow(PracticeAuthoringCandidateApplyService::denied);
        PracticeDraft draft = draftRepository
                .findByIdForUpdate(candidate.getTargetDraftId())
                .orElseThrow(() -> new PracticeAuthoringCandidateException(
                        "CANDIDATE_TARGET_NOT_FOUND",
                        "Bản nháp target không tồn tại."));

        PracticeAuthoringCandidateApplyEvent replay = eventRepository
                .findByCandidateIdAndApplyRequestId(
                        candidate.getId(), command.applyRequestId().toString())
                .orElse(null);
        if (replay != null) {
            if (!replay.matches(
                    command.candidateVersion(), command.candidateDigest(),
                    candidate.getBaseDraftVersion(), command.actorId())) {
                return new ApplyResult(
                        ApplyResultCode.REJECTED,
                        "APPLY_REQUEST_MISMATCH",
                        candidate.getTargetDraftId(), null, true);
            }
            return result(candidate, replay, true);
        }

        LocalDateTime now = now();
        if (!validDigest(command.candidateDigest())) {
            return new ApplyResult(
                    ApplyResultCode.REJECTED,
                    "CANDIDATE_VERSION_CONFLICT",
                    candidate.getTargetDraftId(), null, false);
        }
        if (!candidate.isTerminal() && candidate.isExpiredAt(now)) {
            ObjectNode envelope = candidateJson.readObject(
                    candidate.getCandidateJson());
            PracticeAuthoringCandidateService.setState(
                    envelope, CandidateState.EXPIRED);
            candidate.expireIfDue(candidateJson.write(envelope), now);
            return record(
                    candidate, command, ApplyResultCode.REJECTED,
                    "CANDIDATE_EXPIRED", null, now);
        }
        if (candidate.getState() != CandidateState.READY_TO_APPLY) {
            return record(
                    candidate, command, ApplyResultCode.REJECTED,
                    "CANDIDATE_NOT_READY", null, now);
        }
        if (candidate.getLockVersion() != command.candidateVersion()
                || !candidate.getContentDigest().equals(
                PracticeAuthoringCandidateJson.stripDigestPrefix(
                        command.candidateDigest()))) {
            return record(
                    candidate, command, ApplyResultCode.REJECTED,
                    "CANDIDATE_VERSION_CONFLICT", null, now);
        }
        if (version(draft) != candidate.getBaseDraftVersion()) {
            return record(
                    candidate, command, ApplyResultCode.CONFLICT,
                    "TARGET_DRAFT_VERSION_CONFLICT", null, now);
        }

        ObjectNode envelope = candidateJson.readObject(
                candidate.getCandidateJson());
        String normalizedDraft;
        try {
            ObjectNode projected = projector.append(
                    draft.getDraftJson(), candidate, envelope);
            normalizedDraft = draftContractService.normalize(
                    projected, candidate.getSourceKind().name()).json();
        } catch (PracticeAuthoringCandidateException exception) {
            return record(
                    candidate, command, ApplyResultCode.REJECTED,
                    exception.code(), null, now);
        } catch (IllegalArgumentException exception) {
            return record(
                    candidate, command, ApplyResultCode.REJECTED,
                    "CANDIDATE_DRAFT_NORMALIZATION_FAILED", null, now);
        }
        PracticeDraftValidator.ValidationResult validation =
                draftValidator.validate(normalizedDraft);
        if (validation.hasBlocking()) {
            return record(
                    candidate, command, ApplyResultCode.REJECTED,
                    "CANDIDATE_DRAFT_VALIDATION_FAILED", null, now);
        }

        draft.setDraftJson(normalizedDraft);
        draft.setDraftSchemaVersion(PracticeDraftContractService.SCHEMA_VERSION);
        PracticeDraft savedDraft = draftRepository.saveAndFlush(draft);
        int resultVersion = version(savedDraft);

        PracticeAuthoringCandidateApplyEvent event = newEvent(
                candidate, command, ApplyResultCode.DRAFT_APPLIED,
                "DRAFT_APPLIED", resultVersion, now);
        eventRepository.save(event);
        ObjectNode applied = envelope.putObject("applied");
        applied.put("applyRequestId", command.applyRequestId().toString());
        applied.put("draftVersion", resultVersion);
        applied.put("appliedAt", now.atOffset(ZoneOffset.UTC).toString());
        PracticeAuthoringCandidateService.setState(
                envelope, CandidateState.APPLIED);
        candidate.markApplied(
                candidateJson.write(envelope), resultVersion, now);
        candidateRepository.save(candidate);
        return new ApplyResult(
                ApplyResultCode.DRAFT_APPLIED,
                "DRAFT_APPLIED",
                candidate.getTargetDraftId(), resultVersion, false);
    }

    private ApplyResult record(
            PracticeAuthoringCandidate candidate,
            ApplyCommand command,
            ApplyResultCode result,
            String code,
            Integer draftVersion,
            LocalDateTime now) {
        PracticeAuthoringCandidateApplyEvent event = newEvent(
                candidate, command, result, code, draftVersion, now);
        eventRepository.save(event);
        if (candidate.getState() == CandidateState.EXPIRED) {
            candidateRepository.save(candidate);
        }
        return new ApplyResult(
                result, code, candidate.getTargetDraftId(),
                draftVersion, false);
    }

    private PracticeAuthoringCandidateApplyEvent newEvent(
            PracticeAuthoringCandidate candidate,
            ApplyCommand command,
            ApplyResultCode result,
            String code,
            Integer resultDraftVersion,
            LocalDateTime now) {
        return new PracticeAuthoringCandidateApplyEvent(
                candidate.getId(), command.applyRequestId(),
                command.candidateVersion(),
                PracticeAuthoringCandidateJson.stripDigestPrefix(
                        command.candidateDigest()),
                candidate.getBaseDraftVersion(), result, code,
                resultDraftVersion, command.actorId(), now);
    }

    private static ApplyResult result(
            PracticeAuthoringCandidate candidate,
            PracticeAuthoringCandidateApplyEvent event,
            boolean replayed) {
        return new ApplyResult(
                event.getResult(), event.getResultCode(),
                candidate.getTargetDraftId(),
                event.getResultDraftVersion(), replayed);
    }

    private static void requireCommand(ApplyCommand command) {
        if (command == null || command.candidateId() == null
                || command.applyRequestId() == null
                || command.actorId() == null
                || command.candidateVersion() < 0) {
            throw new IllegalArgumentException("Apply command is invalid");
        }
    }

    private static boolean validDigest(String value) {
        return PracticeAuthoringCandidateJson.stripDigestPrefix(value)
                .matches("[0-9a-f]{64}");
    }

    private static int version(PracticeDraft draft) {
        return draft.getVersion() == null ? 0 : draft.getVersion();
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException(
                "Bạn không có quyền áp dụng authoring candidate này.");
    }
}
