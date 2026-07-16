package com.ksh.features.practice.manage.service;

import com.ksh.entities.Enrollment;
import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.entities.PracticeSet;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticePublishedVersionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Service
public class PracticeMaterialAccessService {

    private final LecturerAssetRepository assetRepository;
    private final PracticeMaterialReferenceService referenceService;
    private final PracticeSetRepository setRepository;
    private final PracticeAttemptRepository attemptRepository;
    private final PracticePublishedVersionRepository publishedVersionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PracticeAuthorizationService authorizationService;
    private final AssetStorageService storageService;

    public PracticeMaterialAccessService(
            LecturerAssetRepository assetRepository,
            PracticeMaterialReferenceService referenceService,
            PracticeSetRepository setRepository,
            PracticeAttemptRepository attemptRepository,
            PracticePublishedVersionRepository publishedVersionRepository,
            EnrollmentRepository enrollmentRepository,
            PracticeAuthorizationService authorizationService,
            AssetStorageService storageService) {
        this.assetRepository = assetRepository;
        this.referenceService = referenceService;
        this.setRepository = setRepository;
        this.attemptRepository = attemptRepository;
        this.publishedVersionRepository = publishedVersionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.authorizationService = authorizationService;
        this.storageService = storageService;
    }

    @Transactional
    public MaterialContent load(Long assetId, Long actorId) throws IOException {
        if (actorId == null) {
            throw new AccessDeniedException("Bạn phải đăng nhập để truy cập tài nguyên này.");
        }
        LecturerAsset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy tài nguyên."));
        if (!asset.isContentVerified()) {
            throw new AccessDeniedException("Tài nguyên chưa vượt qua kiểm tra nội dung.");
        }
        if (!Set.of("ACTIVE", "TEMPORARY", "ARCHIVED").contains(asset.getStatus())) {
            throw new EntityNotFoundException("Tài nguyên đã được lên lịch xóa.");
        }
        if (actorId.equals(asset.getOwnerLecturerId()) || canReadThroughDraftReference(assetId, actorId)
                || canReadPublished(assetId, actorId)) {
            return content(asset);
        }
        throw new AccessDeniedException("Bạn không có quyền truy cập tài nguyên này.");
    }

    @Transactional(readOnly = true)
    public MaterialContent loadForPublishedVersion(
            Long assetId, Long publishedVersionId) throws IOException {
        if (publishedVersionId == null
                || !referenceService.hasPublishedVersionReference(assetId, publishedVersionId)) {
            throw new AccessDeniedException(
                    "Tài nguyên không thuộc phiên bản đề đã xuất bản này.");
        }
        LecturerAsset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy tài nguyên."));
        if (!asset.isContentVerified()) {
            throw new AccessDeniedException("Tài nguyên chưa vượt qua kiểm tra nội dung.");
        }
        if (!Set.of("ACTIVE", "ARCHIVED").contains(asset.getStatus())) {
            throw new EntityNotFoundException("Tài nguyên không còn khả dụng.");
        }
        return content(asset);
    }

    private boolean canReadThroughDraftReference(Long assetId, Long actorId) {
        for (PracticeMaterialReference reference : referenceService.references(assetId)) {
            if (reference.getDraftId() != null
                    && canReadDraftTarget(reference.getDraftId(), actorId)) return true;
        }
        return false;
    }

    private boolean canReadDraftTarget(Long draftId, Long actorId) {
        try {
            authorizationService.requireDraft(draftId, actorId, PracticeAction.READ);
            return true;
        } catch (EntityNotFoundException | AccessDeniedException exception) {
            return false;
        }
    }

    private boolean canReadPublished(Long assetId, Long actorId) {
        for (PracticeMaterialReference reference : referenceService.references(assetId)) {
            if (reference.getSetId() == null || reference.getPublishedVersionId() == null) {
                continue;
            }
            if (attemptRepository.existsByPublishedVersionIdAndUserId(
                    reference.getPublishedVersionId(), actorId)) {
                return true;
            }
            PracticeSet set = setRepository.findById(reference.getSetId()).orElse(null);
            if (set == null) continue;
            if (canReadSetTarget(reference.getSetId(), actorId)) return true;
            if (!PracticeSet.STATUS_PUBLISHED.equals(set.getStatus())) continue;
            Long currentVersionId = publishedVersionRepository
                    .findFirstBySetIdAndStatusOrderByVersionNumberDesc(
                            reference.getSetId(),
                            com.ksh.entities.PracticePublishedVersion.STATUS_PUBLISHED)
                    .map(com.ksh.entities.PracticePublishedVersion::getId)
                    .orElse(null);
            if (!reference.getPublishedVersionId().equals(currentVersionId)) continue;
            if (PracticeSet.SCOPE_GLOBAL.equals(set.getScope())) return true;
            if (set.getClassId() != null && enrollmentRepository
                    .findByUserIdAndClassId(actorId, set.getClassId())
                    .filter(enrollment -> Enrollment.STATUS_ACTIVE.equals(enrollment.getStatus()))
                    .isPresent()) {
                return true;
            }
        }
        return false;
    }

    private boolean canReadSetTarget(Long setId, Long actorId) {
        try {
            authorizationService.requireSet(setId, actorId, PracticeAction.READ);
            return true;
        } catch (EntityNotFoundException | AccessDeniedException ignored) {
            return false;
        }
    }

    private MaterialContent content(LecturerAsset asset) throws IOException {
        Resource resource = storageService.load(asset.getStorageKey());
        return new MaterialContent(resource,
                asset.getMimeType() == null ? "application/octet-stream" : asset.getMimeType(),
                asset.getOriginalFilename(), asset.getFileSize());
    }

    public record MaterialContent(Resource resource, String mimeType,
                                  String filename, Long sizeBytes) {
    }
}
