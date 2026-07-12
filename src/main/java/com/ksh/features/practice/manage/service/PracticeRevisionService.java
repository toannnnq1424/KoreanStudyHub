package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticeEditLog;
import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeSet;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.governance.PracticeGovernanceAuditService;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.repository.PracticeEditLogRepository;
import com.ksh.features.practice.repository.PracticePublishedVersionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.service.PracticePublishedVersionService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PracticeRevisionService {

    private final PracticeEditLogRepository editLogRepository;
    private final PracticeDraftRepository draftRepository;
    private final PracticeSetRepository setRepository;
    private final PracticePublishedVersionRepository publishedVersionRepository;
    private final PracticePublishedVersionService publishedVersionService;
    private final PracticePublisherService publisherService;
    private final PracticeAuthorizationService authorizationService;
    private final PracticeGovernanceAuditService auditService;
    private final ObjectMapper objectMapper;

    public PracticeRevisionService(PracticeEditLogRepository editLogRepository,
                                   PracticeDraftRepository draftRepository,
                                   PracticeSetRepository setRepository,
                                   PracticePublishedVersionRepository publishedVersionRepository,
                                   PracticePublishedVersionService publishedVersionService,
                                   PracticePublisherService publisherService,
                                   PracticeAuthorizationService authorizationService,
                                   PracticeGovernanceAuditService auditService,
                                   ObjectMapper objectMapper) {
        this.editLogRepository = editLogRepository;
        this.draftRepository = draftRepository;
        this.setRepository = setRepository;
        this.publishedVersionRepository = publishedVersionRepository;
        this.publishedVersionService = publishedVersionService;
        this.publisherService = publisherService;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * Compatibility entry point for old edit-log links. The snapshot becomes a
     * fresh published version; the selected history row is never rewritten.
     */
    @Transactional
    public Long restoreRevision(Long logId, Long actorId) {
        return restoreRevision(logId, actorId, null);
    }

    @Transactional
    public Long restoreRevision(Long logId, Long actorId, String overrideReason) {
        PracticeEditLog logEntry = editLogRepository.findById(logId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Lịch sử sửa đổi không tồn tại."));
        String snapshot = logEntry.getBeforeSnapshotJson();
        requireSnapshot(snapshot);
        PracticeAuthorizationService.Decision decision = authorizationService.requireSet(
                logEntry.getSetId(), actorId, PracticeAction.RESTORE, overrideReason);
        return applyAsNewVersion(logEntry.getSetId(), snapshot, null, decision,
                actorId, overrideReason, "edit-log:" + logId);
    }

    @Transactional
    public Long restorePublishedVersion(Long setId, Long publishedVersionId,
                                        Long actorId, String overrideReason) {
        PracticeAuthorizationService.Decision decision = authorizationService.requireSet(
                setId, actorId, PracticeAction.RESTORE, overrideReason);
        PracticePublishedVersion source = publishedVersionRepository.findById(publishedVersionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Phiên bản xuất bản không tồn tại."));
        if (!setId.equals(source.getSetId())) {
            throw new IllegalArgumentException("Phiên bản không thuộc học liệu đã chọn.");
        }
        String snapshot = publishedVersionService.draftSnapshotJson(publishedVersionId, setId);
        requireSnapshot(snapshot);
        return applyAsNewVersion(setId, snapshot, publishedVersionId, decision,
                actorId, overrideReason, "published-version:" + source.getVersionNumber());
    }

    @Transactional(readOnly = true)
    public List<PracticePublishedVersion> versions(Long setId, Long actorId) {
        authorizationService.requireSet(setId, actorId, PracticeAction.READ, null);
        return publishedVersionRepository.findBySetIdOrderByVersionNumberDesc(setId);
    }

    private Long applyAsNewVersion(Long setId, String snapshot, Long sourceVersionId,
                                   PracticeAuthorizationService.Decision decision,
                                   Long actorId, String overrideReason,
                                   String sourceLabel) {
        PracticeSet set = setRepository.findById(setId)
                .orElseThrow(() -> new EntityNotFoundException("Học liệu không tồn tại."));
        PracticePublishedVersion current = publishedVersionRepository
                .findFirstBySetIdOrderByVersionNumberDesc(setId).orElse(null);
        String beforeSnapshot = current == null ? null
                : publishedVersionService.draftSnapshotJson(current.getId(), setId);
        JsonNode root = parseSnapshot(snapshot);
        JsonNode document = root.path("document");
        String category = firstNonBlank(
                document.path("detectedCategory").asText(""),
                firstNonBlank(set.getTopikLevel(), set.getAssessmentProgramCode()));
        PracticeDraft temporary = new PracticeDraft(
                firstNonBlank(document.path("title").asText(""), set.getTitle()),
                document.path("description").asText(set.getDescription()),
                category,
                set.getScope(),
                set.getClassId(),
                "DRAFT",
                decision.ownerId(),
                snapshot);
        temporary.setPublishedSetId(setId);
        temporary.setCreationMethod("RESTORE");
        temporary.setAssessmentProgramCode(firstNonBlank(
                document.path("assessmentProgramCode").asText(""),
                set.getAssessmentProgramCode()));
        JsonNode programVersionNode = document.path("assessmentProgramVersionId");
        Long programVersionId = set.getAssessmentProgramVersionId();
        if (programVersionNode.isIntegralNumber() && programVersionNode.canConvertToLong()) {
            programVersionId = Long.valueOf(programVersionNode.longValue());
        }
        temporary.setAssessmentProgramVersionId(programVersionId);
        String examTemplateCode = firstNonBlankOrNull(
                document.path("examTemplateCode").asText(""),
                set.getExamTemplateCode());
        if (examTemplateCode != null) {
            temporary.setExamTemplateCode(examTemplateCode);
        }
        PracticeDraft saved = draftRepository.saveAndFlush(temporary);

        Long restoredSetId = publisherService.publishRestored(
                saved.getId(), actorId, overrideReason);
        PracticePublishedVersion created = publishedVersionRepository
                .findFirstBySetIdOrderByVersionNumberDesc(restoredSetId)
                .orElseThrow(() -> new IllegalStateException(
                        "Restore không tạo được immutable published version."));
        draftRepository.delete(saved);
        draftRepository.flush();
        String afterSnapshot = publishedVersionService.draftSnapshotJson(
                created.getId(), restoredSetId);

        auditService.record("VERSION_RESTORED", "SET", setId, decision.ownerId(),
                actorId, sourceVersionId, decision.overrideUsed(), overrideReason,
                auditEnvelope(sourceLabel, current, beforeSnapshot),
                auditEnvelope(sourceLabel, created, afterSnapshot));
        return created.getId();
    }

    private String auditEnvelope(String sourceLabel,
                                 PracticePublishedVersion version,
                                 String snapshot) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("source", sourceLabel);
            if (version == null) {
                envelope.putNull("publishedVersionId");
                envelope.putNull("versionNumber");
            } else {
                envelope.put("publishedVersionId", version.getId());
                envelope.put("versionNumber", version.getVersionNumber());
            }
            if (snapshot == null || snapshot.isBlank()) {
                envelope.putNull("snapshot");
            } else {
                envelope.set("snapshot", objectMapper.readTree(snapshot));
            }
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể ghi audit khôi phục đầy đủ.", exception);
        }
    }

    private JsonNode parseSnapshot(String snapshot) {
        try {
            JsonNode root = objectMapper.readTree(snapshot);
            if (!root.isObject() || !root.path("sections").isArray()) {
                throw new IllegalArgumentException(
                        "Snapshot lịch sử không có cấu trúc draft hợp lệ.");
            }
            return root;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Dữ liệu snapshot bị lỗi cú pháp.", exception);
        }
    }

    private static void requireSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isBlank() || "{}".equals(snapshot.trim())) {
            throw new IllegalArgumentException(
                    "Không thể khôi phục phiên bản không có snapshot đầy đủ.");
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return "CUSTOM";
    }

    private static String firstNonBlankOrNull(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return null;
    }
}
