package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeDraftAssetUsage;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeDraftAssetUsageRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.entities.PracticeAssetLifecycleTask;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.repository.PracticeAssetLifecycleTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class LecturerAssetService {

    private static final Logger log = LoggerFactory.getLogger(LecturerAssetService.class);

    private final LecturerAssetRepository assetRepository;
    private final PracticeDraftAssetUsageRepository usageRepository;
    private final PracticeDraftRepository draftRepository;
    private final AssetStorageService assetStorage;
    private final PracticeAuthorizationService authorizationService;
    private final PracticeMaterialReferenceService materialReferenceService;
    private final PracticeAssetLifecycleTaskRepository lifecycleTaskRepository;
    private final PracticeUploadContentVerifier contentVerifier;

    @org.springframework.beans.factory.annotation.Autowired
    public LecturerAssetService(LecturerAssetRepository assetRepository,
                                PracticeDraftAssetUsageRepository usageRepository,
                                PracticeDraftRepository draftRepository,
                                AssetStorageService assetStorage,
                                PracticeAuthorizationService authorizationService,
                                PracticeMaterialReferenceService materialReferenceService,
                                PracticeAssetLifecycleTaskRepository lifecycleTaskRepository,
                                PracticeUploadContentVerifier contentVerifier) {
        this.assetRepository = assetRepository;
        this.usageRepository = usageRepository;
        this.draftRepository = draftRepository;
        this.assetStorage = assetStorage;
        this.authorizationService = authorizationService;
        this.materialReferenceService = materialReferenceService;
        this.lifecycleTaskRepository = lifecycleTaskRepository;
        this.contentVerifier = contentVerifier;
    }

    public LecturerAssetService(LecturerAssetRepository assetRepository,
                                PracticeDraftAssetUsageRepository usageRepository,
                                PracticeDraftRepository draftRepository,
                                AssetStorageService assetStorage) {
        this(assetRepository, usageRepository, draftRepository, assetStorage,
                null, null, null, null);
    }

    public LecturerAssetService(LecturerAssetRepository assetRepository,
                                PracticeDraftAssetUsageRepository usageRepository,
                                AssetStorageService assetStorage) {
        this(assetRepository, usageRepository, null, assetStorage,
                null, null, null, null);
    }

    @Transactional
    public LecturerAsset createTemporaryAsset(Long ownerId, Long sessionId, Long regionId, InputStream content,
                                              String originalFilename, String mimeType, Integer w, Integer h, Long sizeBytes,
                                              Integer sourcePageNumber, Double cropX, Double cropY, Double cropWidth, Double cropHeight,
                                              String lecturerNote) throws IOException {
        String relativePath = "lecturer-assets/" + ownerId + "/imports/" + sessionId + "/temporary";
        
        // Store physically via AssetStorageService (which computes SHA-256 and saves as SHA-256.ext)
        AssetStorageService.StoredAsset stored = assetStorage.store(content, originalFilename, relativePath);
        registerRollbackCleanup(stored.storageKey(), stored.newlyCreated());

        // Deduplication: Check if lecturer already owns active asset with same SHA-256
        List<LecturerAsset> duplicate = assetRepository.findByOwnerLecturerIdAndSha256AndStatusAndDeletedAtIsNull(ownerId, stored.sha256(), "ACTIVE");
        if (!duplicate.isEmpty()) {
            LecturerAsset existing = duplicate.get(0);
            log.info("[AssetService] Reusing active assetId={} after content deduplication", existing.getId());
            // Delete temp physical file as it's duplicate
            if (stored.newlyCreated()) {
                try {
                    assetStorage.delete(stored.storageKey());
                } catch (IOException e) {
                    enqueueLifecycle(null, PracticeAssetLifecycleTask.ORPHAN_RECONCILE,
                            stored.storageKey(), null);
                }
            }
            return existing;
        }

        // Create new LecturerAsset entity
        LecturerAsset asset = new LecturerAsset();
        asset.setOwnerLecturerId(ownerId);
        asset.setSourceImportSessionId(sessionId);
        asset.setSourceRegionId(regionId);
        asset.setStorageProvider(assetStorage.providerCode());
        asset.setStorageKey(stored.storageKey());
        asset.setOriginalFilename(originalFilename);
        asset.setMimeType(mimeType);
        asset.setContentVerified(true);
        asset.setWidth(w);
        asset.setHeight(h);
        asset.setFileSize(stored.sizeBytes());
        asset.setSha256(stored.sha256());
        asset.setAssetType("IMAGE");
        asset.setTitle(originalFilename != null ? originalFilename : "Imported Crop");
        asset.setSourcePageNumber(sourcePageNumber);
        asset.setCropX(cropX);
        asset.setCropY(cropY);
        asset.setCropWidth(cropWidth);
        asset.setCropHeight(cropHeight);
        asset.setLecturerNote(lecturerNote);
        asset.setStatus("TEMPORARY");
        asset.setVisibility("PRIVATE");
        asset.setCreatedAt(LocalDateTime.now());
        asset.setUpdatedAt(LocalDateTime.now());

        return assetRepository.save(asset);
    }

    @Transactional
    public LecturerAsset createDraftUploadAsset(
            Long draftId, Long actorId,
            org.springframework.web.multipart.MultipartFile file,
            String assetType, long maxBytes) throws IOException {
        return createDraftUploadAsset(
                draftId, actorId, file, assetType, maxBytes, null);
    }

    @Transactional
    public LecturerAsset createDraftUploadAsset(
            Long draftId, Long actorId,
            org.springframework.web.multipart.MultipartFile file,
            String assetType, long maxBytes, String overrideReason) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Tệp tải lên rỗng.");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("Tệp vượt quá dung lượng cho phép.");
        }
        Long ownerId = actorId;
        if (authorizationService != null) {
            ownerId = authorizationService.requireDraft(
                    draftId, actorId, PracticeAction.MATERIAL_MANAGE,
                    overrideReason).ownerId();
        } else {
            requireOwnedDraft(draftId, actorId);
        }
        byte[] bytes = file.getBytes();
        PracticeUploadContentVerifier.VerifiedContent verified = contentVerifier == null
                ? new PracticeUploadContentVerifier().verify(
                        bytes, file.getOriginalFilename(), assetType)
                : contentVerifier.verify(bytes, file.getOriginalFilename(), assetType);
        String relativePath = "lecturer-assets/" + ownerId + "/drafts/" + draftId
                + "/private/" + assetType.toLowerCase(java.util.Locale.ROOT);
        AssetStorageService.StoredAsset stored = assetStorage.store(
                new ByteArrayInputStream(bytes), file.getOriginalFilename(), relativePath);
        registerRollbackCleanup(stored.storageKey(), stored.newlyCreated());

        List<LecturerAsset> duplicates = assetRepository
                .findByOwnerLecturerIdAndSha256AndStatusAndDeletedAtIsNull(
                        ownerId, stored.sha256(), "ACTIVE");
        LecturerAsset asset;
        if (!duplicates.isEmpty()) {
            asset = duplicates.get(0);
            if (stored.newlyCreated() && !stored.storageKey().equals(asset.getStorageKey())) {
                try {
                    assetStorage.delete(stored.storageKey());
                } catch (IOException exception) {
                    enqueueLifecycle(asset.getId(), PracticeAssetLifecycleTask.ORPHAN_RECONCILE,
                            stored.storageKey(), null);
                }
            }
        } else {
            asset = new LecturerAsset();
            asset.setOwnerLecturerId(ownerId);
            asset.setStorageProvider(assetStorage.providerCode());
            asset.setStorageKey(stored.storageKey());
            asset.setOriginalFilename(file.getOriginalFilename());
            asset.setMimeType(verified.mimeType());
            asset.setContentVerified(true);
            asset.setFileSize(stored.sizeBytes());
            asset.setSha256(stored.sha256());
            asset.setAssetType(assetType.toUpperCase(java.util.Locale.ROOT));
            asset.setTitle(file.getOriginalFilename());
            asset.setSourceType("MANUAL_UPLOAD");
            asset.setStatus("ACTIVE");
            asset.setVisibility("PRIVATE");
            asset.setCreatedAt(LocalDateTime.now());
            asset.setUpdatedAt(LocalDateTime.now());
            if ("IMAGE".equalsIgnoreCase(assetType)) {
                AssetStorageService.AssetMetadata metadata = assetStorage.inspect(stored.storageKey());
                asset.setWidth(metadata.width());
                asset.setHeight(metadata.height());
            }
            asset = assetRepository.save(asset);
        }
        if (materialReferenceService != null) {
            materialReferenceService.linkDraft(
                    draftId, asset.getId(), "MANUAL_" + assetType.toUpperCase(java.util.Locale.ROOT));
        }
        return asset;
    }

    @Transactional
    public LecturerAsset promoteToActiveLibrary(Long assetId, Long ownerId) {
        return promoteOwnedAsset(requireOwnedAsset(assetId, ownerId), ownerId);
    }

    @Transactional
    public LecturerAsset promoteSessionRegionAsset(Long sessionId, Long regionId,
                                                    Long assetId, Long ownerId) {
        LecturerAsset asset = requireOwnedAsset(assetId, ownerId);
        if (!sessionId.equals(asset.getSourceImportSessionId())
                || !regionId.equals(asset.getSourceRegionId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Asset không thuộc vùng import đã chọn.");
        }
        return promoteOwnedAsset(asset, ownerId);
    }

    private LecturerAsset promoteOwnedAsset(LecturerAsset asset, Long ownerId) {
        if ("ACTIVE".equalsIgnoreCase(asset.getStatus())) {
            return asset;
        }

        try {
            String oldKey = asset.getStorageKey();
            AssetStorageService.StoredAsset promoted;
            try (InputStream in = assetStorage.load(oldKey).getInputStream()) {
                String relativePath = "lecturer-assets/" + ownerId + "/imports/" + asset.getSourceImportSessionId() + "/library";
                promoted = assetStorage.store(in, asset.getOriginalFilename(), relativePath);
                asset.setStorageKey(promoted.storageKey());
            }
            
            asset.setStatus("ACTIVE");
            asset.setUpdatedAt(LocalDateTime.now());
            log.info("[AssetService] Promoted assetId={} to library status", asset.getId());
            LecturerAsset saved = assetRepository.save(asset);
            registerRollbackCleanup(asset.getStorageKey(), promoted.newlyCreated());
            enqueueLifecycle(asset.getId(), PracticeAssetLifecycleTask.PROMOTE_CLEANUP,
                    oldKey, asset.getStorageKey());
            return saved;
        } catch (IOException e) {
            log.error("[AssetService] Failed to promote assetId={}", asset.getId(), e);
            throw new RuntimeException("Lỗi lưu trữ khi chuyển ảnh vào thư viện.", e);
        }
    }

    private LecturerAsset requireOwnedAsset(Long assetId, Long ownerId) {
        LecturerAsset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy asset."));
        if (!"ACTIVE".equalsIgnoreCase(asset.getStatus())
                && !"TEMPORARY".equalsIgnoreCase(asset.getStatus())) {
            throw new IllegalStateException("Asset không còn ở trạng thái có thể liên kết.");
        }
        if (!ownerId.equals(asset.getOwnerLecturerId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn không có quyền quản lý asset này.");
        }
        return asset;
    }

    public List<LecturerAsset> getLibraryAssets(Long ownerId) {
        return assetRepository.findByOwnerLecturerIdAndStatusAndDeletedAtIsNull(ownerId, "ACTIVE");
    }

    public List<LecturerAsset> getSessionAssets(Long sessionId) {
        return assetRepository.findBySourceImportSessionId(sessionId);
    }

    public List<LecturerAsset> getSessionAssets(Long sessionId, Long ownerId) {
        return assetRepository.findBySourceImportSessionIdAndOwnerLecturerId(sessionId, ownerId);
    }

    @Transactional
    public void deleteAsset(Long assetId, Long ownerId) {
        LecturerAsset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy asset."));
        if (!asset.getOwnerLecturerId().equals(ownerId)) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền xóa asset này.");
        }

        if (materialReferenceService != null
                && materialReferenceService.hasPublishedReference(assetId)) {
            throw new IllegalStateException(
                    "Asset đang thuộc một phiên bản đã xuất bản và không thể xóa vật lý.");
        }

        // Draft references keep the private object intact until explicitly unlinked.
        List<PracticeDraftAssetUsage> usages = usageRepository.findByAssetId(assetId);
        boolean referenced = !usages.isEmpty()
                || (materialReferenceService != null
                    && materialReferenceService.hasAnyReference(assetId));
        if (referenced) {
            // Keep in DB but mark deleted/archived so it won't be seen in general library queries
            asset.setDeletedAt(LocalDateTime.now());
            asset.setStatus("ARCHIVED");
            assetRepository.save(asset);
            log.info("[AssetService] Soft deleted assetId={} due to active references count={}", assetId, usages.size());
        } else {
            asset.setDeletedAt(LocalDateTime.now());
            asset.setStatus("DELETION_PENDING");
            assetRepository.save(asset);
            enqueueLifecycle(assetId, PracticeAssetLifecycleTask.DELETE,
                    asset.getStorageKey(), null);
            log.info("[AssetService] Queued physical delete for unreferenced assetId={}", assetId);
        }
    }

    @Transactional
    public void cleanupTemporaryAssets(Long sessionId, Long ownerId) {
        List<LecturerAsset> temps = assetRepository
                .findBySourceImportSessionIdAndOwnerLecturerId(sessionId, ownerId)
                .stream()
                .filter(asset -> "TEMPORARY".equalsIgnoreCase(asset.getStatus()))
                .toList();
        for (LecturerAsset asset : temps) {
            try {
                if (hasAnyReference(asset.getId())) {
                    asset.setDeletedAt(LocalDateTime.now());
                    asset.setStatus("ARCHIVED");
                    assetRepository.save(asset);
                    log.info("[AssetService] Preserved referenced temporary assetId={} as archived",
                            asset.getId());
                    continue;
                }
                asset.setDeletedAt(LocalDateTime.now());
                asset.setStatus("DELETION_PENDING");
                assetRepository.save(asset);
                enqueueLifecycle(asset.getId(), PracticeAssetLifecycleTask.DELETE,
                        asset.getStorageKey(), null);
            } catch (RuntimeException e) {
                log.warn("[AssetService] Failed to queue temporary assetId={}", asset.getId(), e);
            }
        }
    }

    public Resource loadAssetResource(Long assetId, Long ownerId) throws IOException {
        LecturerAsset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy asset."));
        if (!asset.getOwnerLecturerId().equals(ownerId)) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền truy cập asset này.");
        }
        return assetStorage.load(asset.getStorageKey());
    }

    @Transactional
    public PracticeDraftAssetUsage linkAssetToDraft(Long draftId, Long assetId, Long ownerId,
                                                    String sectionTempId, String groupTempId,
                                                    String questionTempId, String placement, String altText) {
        return linkAssetToDraft(draftId, assetId, ownerId, sectionTempId,
                groupTempId, questionTempId, placement, altText, null);
    }

    @Transactional
    public PracticeDraftAssetUsage linkAssetToDraft(Long draftId, Long assetId, Long ownerId,
                                                    String sectionTempId, String groupTempId,
                                                    String questionTempId, String placement,
                                                    String altText, String overrideReason) {
        Long draftOwnerId = requireManageableDraft(draftId, ownerId, overrideReason);
        LecturerAsset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy asset."));
        if (!"ACTIVE".equalsIgnoreCase(asset.getStatus())
                && !"TEMPORARY".equalsIgnoreCase(asset.getStatus())) {
            throw new IllegalStateException("Asset không còn ở trạng thái có thể liên kết.");
        }
        if (!draftOwnerId.equals(asset.getOwnerLecturerId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn không có quyền sử dụng asset này.");
        }
        PracticeDraftAssetUsage usage = new PracticeDraftAssetUsage();
        usage.setDraftId(draftId);
        usage.setAssetId(assetId);
        usage.setSectionTempId(sectionTempId);
        usage.setGroupTempId(groupTempId);
        usage.setQuestionTempId(questionTempId);
        usage.setPlacement(placement);
        usage.setAltText(altText);
        usage.setCreatedAt(LocalDateTime.now());
        
        PracticeDraftAssetUsage saved = usageRepository.save(usage);
        if (materialReferenceService != null) {
            materialReferenceService.linkDraft(draftId, assetId, placement);
        }
        return saved;
    }

    @Transactional
    public void unlinkAssetFromDraft(Long draftId, Long usageId, Long ownerId) {
        unlinkAssetFromDraft(draftId, usageId, ownerId, null);
    }

    @Transactional
    public void unlinkAssetFromDraft(Long draftId, Long usageId, Long ownerId,
                                     String overrideReason) {
        requireManageableDraft(draftId, ownerId, overrideReason);
        PracticeDraftAssetUsage usage = usageRepository.findById(usageId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy liên kết asset."));
        if (!draftId.equals(usage.getDraftId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn không có quyền xóa liên kết asset này.");
        }
        usageRepository.delete(usage);
        usageRepository.flush();
        if (materialReferenceService != null
                && !usageRepository.existsByDraftIdAndAssetIdAndPlacement(
                        draftId, usage.getAssetId(), usage.getPlacement())) {
            materialReferenceService.unlinkDraft(
                    draftId, usage.getAssetId(), usage.getPlacement());
        }
        queueArchivedAssetIfUnreferenced(usage.getAssetId());
    }

    private void requireOwnedDraft(Long draftId, Long ownerId) {
        if (draftRepository == null || draftRepository.findByIdAndOwnerId(draftId, ownerId).isEmpty()) {
            throw new jakarta.persistence.EntityNotFoundException("Bản nháp không tồn tại.");
        }
    }

    private Long requireManageableDraft(Long draftId, Long actorId,
                                        String overrideReason) {
        if (authorizationService == null) {
            requireOwnedDraft(draftId, actorId);
            return actorId;
        }
        return authorizationService.requireDraft(
                draftId, actorId, PracticeAction.MATERIAL_MANAGE,
                overrideReason).ownerId();
    }

    private boolean hasAnyReference(Long assetId) {
        return !usageRepository.findByAssetId(assetId).isEmpty()
                || (materialReferenceService != null
                    && materialReferenceService.hasAnyReference(assetId));
    }

    private void queueArchivedAssetIfUnreferenced(Long assetId) {
        if (hasAnyReference(assetId)) {
            return;
        }
        LecturerAsset asset = assetRepository.findById(assetId).orElse(null);
        if (asset == null || !"ARCHIVED".equalsIgnoreCase(asset.getStatus())) {
            return;
        }
        asset.setStatus("DELETION_PENDING");
        assetRepository.save(asset);
        enqueueLifecycle(assetId, PracticeAssetLifecycleTask.DELETE,
                asset.getStorageKey(), null);
    }

    private void enqueueLifecycle(Long assetId, String operation, String sourceKey,
                                  String targetKey) {
        if (lifecycleTaskRepository == null) {
            try {
                if (sourceKey != null) assetStorage.delete(sourceKey);
            } catch (IOException exception) {
                log.warn("[AssetService] Legacy cleanup failed for storageKey={}", sourceKey,
                        exception);
            }
            return;
        }
        lifecycleTaskRepository.save(new PracticeAssetLifecycleTask(
                assetId, operation, sourceKey, targetKey));
    }

    private void registerRollbackCleanup(String storageKey, boolean newlyCreated) {
        if (!newlyCreated) {
            return;
        }
        if (!org.springframework.transaction.support.TransactionSynchronizationManager
                .isSynchronizationActive()) {
            return;
        }
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            try {
                                assetStorage.delete(storageKey);
                            } catch (IOException exception) {
                                log.warn("[AssetService] Rollback compensation failed for {}",
                                        storageKey, exception);
                                if (lifecycleTaskRepository != null) {
                                    try {
                                        lifecycleTaskRepository.saveAndFlush(
                                                new PracticeAssetLifecycleTask(
                                                        null,
                                                        PracticeAssetLifecycleTask.ORPHAN_RECONCILE,
                                                        storageKey,
                                                        null));
                                    } catch (RuntimeException queueException) {
                                        log.error("[AssetService] Could not persist rollback cleanup for {}",
                                                storageKey, queueException);
                                    }
                                }
                            }
                        }
                    }
                });
    }
}
