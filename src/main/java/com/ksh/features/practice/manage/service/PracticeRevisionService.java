package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticeEditLog;
import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeSet;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
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
    private final ObjectMapper objectMapper;

    public PracticeRevisionService(PracticeEditLogRepository editLogRepository,
                                   PracticeDraftRepository draftRepository,
                                   PracticeSetRepository setRepository,
                                   PracticePublishedVersionRepository publishedVersionRepository,
                                   PracticePublishedVersionService publishedVersionService,
                                   PracticePublisherService publisherService,
                                   PracticeAuthorizationService authorizationService,
                                   ObjectMapper objectMapper) {
        this.editLogRepository = editLogRepository;
        this.draftRepository = draftRepository;
        this.setRepository = setRepository;
        this.publishedVersionRepository = publishedVersionRepository;
        this.publishedVersionService = publishedVersionService;
        this.publisherService = publisherService;
        this.authorizationService = authorizationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Compatibility entry point for old edit-log links. The snapshot becomes a
     * fresh published version; the selected history row is never rewritten.
     */
    @Transactional
    public Long restoreRevision(Long logId, Long actorId) {
        PracticeEditLog logEntry = editLogRepository.findById(logId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Lịch sử sửa đổi không tồn tại."));
        String snapshot = logEntry.getBeforeSnapshotJson();
        requireSnapshot(snapshot);
        PracticeAuthorizationService.Decision decision = authorizationService.requireSet(
                logEntry.getSetId(), actorId, PracticeAction.RESTORE);
        return applyAsNewVersion(logEntry.getSetId(), snapshot, decision, actorId);
    }

    @Transactional
    public Long restorePublishedVersion(Long setId, Long publishedVersionId,
                                        Long actorId) {
        PracticeAuthorizationService.Decision decision = authorizationService.requireSet(
                setId, actorId, PracticeAction.RESTORE);
        PracticePublishedVersion source = publishedVersionRepository.findById(publishedVersionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Phiên bản xuất bản không tồn tại."));
        if (!setId.equals(source.getSetId())) {
            throw new IllegalArgumentException("Phiên bản không thuộc học liệu đã chọn.");
        }
        String snapshot = publishedVersionService.draftSnapshotJson(publishedVersionId, setId);
        requireSnapshot(snapshot);
        return applyAsNewVersion(setId, snapshot, decision, actorId);
    }

    @Transactional(readOnly = true)
    public List<PracticePublishedVersion> versions(Long setId, Long actorId) {
        authorizationService.requireSet(setId, actorId, PracticeAction.READ);
        return publishedVersionRepository.findBySetIdOrderByVersionNumberDesc(setId);
    }

    private Long applyAsNewVersion(Long setId, String snapshot,
                                   PracticeAuthorizationService.Decision decision,
                                   Long actorId) {
        PracticeSet set = setRepository.findById(setId)
                .orElseThrow(() -> new EntityNotFoundException("Học liệu không tồn tại."));
        JsonNode root = parseSnapshot(snapshot);
        JsonNode document = root.path("document");
        PracticeDraft temporary = new PracticeDraft(
                firstNonBlank(document.path("title").asText(""), set.getTitle()),
                document.path("description").asText(set.getDescription()),
                set.getScope(),
                set.getClassId(),
                "DRAFT",
                decision.ownerId(),
                snapshot);
        temporary.setPublishedSetId(setId);
        temporary.setCreationMethod("RESTORE");
        PracticeDraft saved = draftRepository.saveAndFlush(temporary);

        Long restoredSetId = publisherService.publishRestored(
                saved.getId(), actorId);
        PracticePublishedVersion created = publishedVersionRepository
                .findFirstBySetIdOrderByVersionNumberDesc(restoredSetId)
                .orElseThrow(() -> new IllegalStateException(
                        "Restore không tạo được immutable published version."));
        draftRepository.delete(saved);
        draftRepository.flush();
        return created.getId();
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
