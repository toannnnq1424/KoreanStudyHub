package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.entities.PracticeAssetLifecycleTask;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.manage.material.PracticeMaterialPlacements;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class LecturerAssetService {

    private static final Logger log = LoggerFactory.getLogger(LecturerAssetService.class);
    private static final java.time.Duration UNBOUND_UPLOAD_RETENTION =
            java.time.Duration.ofHours(24);

    private final LecturerAssetRepository assetRepository;
    private final PracticeDraftRepository draftRepository;
    private final AssetStorageService assetStorage;
    private final PracticeAuthorizationService authorizationService;
    private final PracticeMaterialReferenceService materialReferenceService;
    private final PracticeAssetLifecycleTaskRepository lifecycleTaskRepository;
    private final PracticeUploadContentVerifier contentVerifier;
    private PracticeAssetReferenceGuard assetReferenceGuard;

    @org.springframework.beans.factory.annotation.Autowired
    public LecturerAssetService(LecturerAssetRepository assetRepository,
                                PracticeDraftRepository draftRepository,
                                AssetStorageService assetStorage,
                                PracticeAuthorizationService authorizationService,
                                PracticeMaterialReferenceService materialReferenceService,
                                PracticeAssetLifecycleTaskRepository lifecycleTaskRepository,
                                PracticeUploadContentVerifier contentVerifier) {
        this.assetRepository = assetRepository;
        this.draftRepository = draftRepository;
        this.assetStorage = assetStorage;
        this.authorizationService = authorizationService;
        this.materialReferenceService = materialReferenceService;
        this.lifecycleTaskRepository = lifecycleTaskRepository;
        this.contentVerifier = contentVerifier;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAssetReferenceGuard(
            PracticeAssetReferenceGuard assetReferenceGuard) {
        this.assetReferenceGuard = assetReferenceGuard;
    }

    public LecturerAssetService(LecturerAssetRepository assetRepository,
                                PracticeDraftRepository draftRepository,
                                AssetStorageService assetStorage) {
        this(assetRepository, draftRepository, assetStorage,
                null, null, null, null);
    }

    public LecturerAssetService(LecturerAssetRepository assetRepository,
                                AssetStorageService assetStorage) {
        this(assetRepository, null, assetStorage,
                null, null, null, null);
    }

    @Transactional
    public LecturerAsset createTemporaryAsset(Long ownerId, Long sessionId, Long regionId, InputStream content,
                                              String originalFilename, String mimeType, Integer w, Integer h, Long sizeBytes,
                                              Integer sourcePageNumber, Double cropX, Double cropY, Double cropWidth, Double cropHeight,
                                              String lecturerNote) throws IOException {
        String relativePath = freshStorageNamespace(
                "lecturer-assets/" + ownerId + "/imports/" + sessionId
                        + "/temporary");
        
        // Store physically via AssetStorageService (which computes SHA-256 and saves as SHA-256.ext)
        AssetStorageService.StoredAsset stored = assetStorage.store(content, originalFilename, relativePath);
        requireFreshStorageResult(relativePath, stored);
        registerRollbackCleanup(stored.storageKey(), stored.newlyCreated());

        // Deduplication: Check if lecturer already owns active asset with same SHA-256
        List<LecturerAsset> duplicate = assetRepository.findByOwnerLecturerIdAndSha256AndStatusAndDeletedAtIsNull(ownerId, stored.sha256(), "ACTIVE");
        if (!duplicate.isEmpty()) {
            LecturerAsset existing = duplicate.get(0);
            log.info("[AssetService] Reusing active assetId={} after content deduplication", existing.getId());
            if (stored.newlyCreated()
                    && !stored.storageKey().equals(existing.getStorageKey())) {
                enqueueLifecycle(
                        null,
                        PracticeAssetLifecycleTask.ORPHAN_RECONCILE,
                        stored.storageKey(),
                        null);
            }
            return existing;
        }

        // Create new LecturerAsset entity
        LecturerAsset asset = new LecturerAsset();
        asset.setOwnerLecturerId(ownerId);
        asset.setSourceImportSessionId(sessionId);
        asset.setSourceRegionId(regionId);
        asset.setStorageProvider(stored.storageProvider());
        asset.setStorageProfileCode(stored.storageProfileCode());
        asset.setStorageKey(stored.storageKey());
        asset.setOriginalFilename(originalFilename);
        asset.setMimeType(mimeType);
        asset.setContentVerified(true);
        asset.setWidth(w);
        asset.setHeight(h);
        asset.setFileSize(stored.sizeBytes());
        asset.setSha256(stored.sha256());
        asset.setAssetType("IMAGE");
        asset.setTitle(validatedAssetTitle(
                originalFilename, "Imported Crop"));
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

        reserveStorageKeyForAsset(stored.storageKey());
        return assetRepository.save(asset);
    }

    @Transactional
    public LecturerAsset createDraftUploadAsset(
            Long draftId, Long actorId,
            org.springframework.web.multipart.MultipartFile file,
            String assetType, long maxBytes) throws IOException {
        return createDraftUploadAsset(
                draftId,
                actorId,
                file,
                assetType,
                maxBytes,
                "MANUAL_" + assetType.toUpperCase(java.util.Locale.ROOT),
                "");
    }

    /**
     * Stores one bounded lecturer upload and binds it to an exact draft
     * placement. Callers retain ownership of their domain-specific content
     * verification; storage and deduplication remain centralized here.
     */
    @Transactional
    public LecturerAsset createDraftUploadAsset(
            Long draftId, Long actorId,
            org.springframework.web.multipart.MultipartFile file,
            String assetType, long maxBytes,
            String placement, String referenceKey) throws IOException {
        return createDraftUploadAssetInternal(
                draftId,
                actorId,
                file,
                assetType,
                maxBytes,
                placement,
                referenceKey,
                true);
    }

    /**
     * Stores and registers one private draft upload without creating a draft
     * material reference. A domain service may bind it later inside its own
     * locked transaction after rechecking the exact mutation authority.
     */
    @Transactional
    public LecturerAsset createUnboundDraftUploadAsset(
            Long draftId,
            Long actorId,
            org.springframework.web.multipart.MultipartFile file,
            String assetType,
            long maxBytes) throws IOException {
        return createDraftUploadAssetInternal(
                draftId,
                actorId,
                file,
                assetType,
                maxBytes,
                null,
                null,
                false);
    }

    private LecturerAsset createDraftUploadAssetInternal(
            Long draftId,
            Long actorId,
            org.springframework.web.multipart.MultipartFile file,
            String assetType,
            long maxBytes,
            String placement,
            String referenceKey,
            boolean bindDraftReference) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Tệp tải lên rỗng.");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("Tệp vượt quá dung lượng cho phép.");
        }
        Long ownerId = actorId;
        if (authorizationService != null) {
            ownerId = authorizationService.requireDraft(
                    draftId, actorId, PracticeAction.MATERIAL_MANAGE).ownerId();
        } else {
            requireOwnedDraft(draftId, actorId);
        }
        byte[] bytes = file.getBytes();
        PracticeUploadContentVerifier.VerifiedContent verified = contentVerifier == null
                ? new PracticeUploadContentVerifier().verify(
                        bytes, file.getOriginalFilename(), assetType)
                : contentVerifier.verify(bytes, file.getOriginalFilename(), assetType);
        String relativePath = freshStorageNamespace(
                "lecturer-assets/" + ownerId + "/drafts/" + draftId
                        + "/private/"
                        + assetType.toLowerCase(java.util.Locale.ROOT));
        AssetStorageService.StoredAsset stored = assetStorage.store(
                new ByteArrayInputStream(bytes), file.getOriginalFilename(), relativePath);
        requireFreshStorageResult(relativePath, stored);
        registerRollbackCleanup(stored.storageKey(), stored.newlyCreated());

        List<LecturerAsset> duplicates = assetRepository
                .findByOwnerLecturerIdAndSha256AndStatusAndDeletedAtIsNull(
                        ownerId, stored.sha256(), "ACTIVE")
                .stream()
                .filter(candidate -> assetType.equalsIgnoreCase(
                        candidate.getAssetType()))
                .filter(candidate -> "MANUAL_UPLOAD".equalsIgnoreCase(
                        candidate.getSourceType()))
                .toList();
        ManualUploadDeduplication deduplication =
                resolveManualUploadDeduplication(
                        duplicates, ownerId, assetType, stored);
        LecturerAsset asset;
        if (deduplication.reusableLogicalAsset() != null) {
            asset = deduplication.reusableLogicalAsset();
            queueUnusedFreshUpload(stored, asset.getStorageKey());
        } else {
            LecturerAsset storageSource = deduplication.storageSource();
            String registeredStorageKey = storageSource == null
                    ? stored.storageKey()
                    : storageSource.getStorageKey();
            String registeredStorageProvider = storageSource == null
                    ? stored.storageProvider()
                    : storageSource.getStorageProvider();
            String registeredStorageProfile = storageSource == null
                    ? stored.storageProfileCode()
                    : storageSource.getStorageProfileCode();
            asset = new LecturerAsset();
            asset.setOwnerLecturerId(ownerId);
            asset.setStorageProvider(registeredStorageProvider);
            asset.setStorageProfileCode(registeredStorageProfile);
            asset.setStorageKey(registeredStorageKey);
            asset.setOriginalFilename(file.getOriginalFilename());
            asset.setMimeType(verified.mimeType());
            asset.setContentVerified(true);
            asset.setFileSize(stored.sizeBytes());
            asset.setSha256(stored.sha256());
            asset.setAssetType(assetType.toUpperCase(java.util.Locale.ROOT));
            asset.setTitle(validatedAssetTitle(
                    file.getOriginalFilename(), "Tài nguyên đã tải lên"));
            asset.setSourceType("MANUAL_UPLOAD");
            asset.setStatus(bindDraftReference ? "ACTIVE" : "TEMPORARY");
            asset.setVisibility("PRIVATE");
            asset.setRetentionUntil(bindDraftReference
                    ? null
                    : LocalDateTime.now().plus(UNBOUND_UPLOAD_RETENTION));
            asset.setCreatedAt(LocalDateTime.now());
            asset.setUpdatedAt(LocalDateTime.now());
            if ("IMAGE".equalsIgnoreCase(assetType)) {
                AssetStorageService.AssetMetadata metadata =
                        registeredStorageProfile == null
                                ? assetStorage.inspect(registeredStorageKey)
                                : assetStorage.inspect(registeredStorageProfile, registeredStorageKey);
                asset.setWidth(metadata.width());
                asset.setHeight(metadata.height());
            }
            if (storageSource == null) {
                reserveStorageKeyForAsset(registeredStorageKey);
            }
            asset = assetRepository.save(asset);
            queueUnusedFreshUpload(stored, registeredStorageKey);
        }
        if (bindDraftReference && materialReferenceService != null) {
            materialReferenceService.linkDraft(
                    draftId, asset.getId(), placement, referenceKey, null);
        }
        return asset;
    }

    /**
     * Reuses a logical upload only while it is still private and unbound. An
     * immutable published row (or any other retained private row) may still
     * supply its physical bytes to a new private logical identity.
     */
    private ManualUploadDeduplication resolveManualUploadDeduplication(
            List<LecturerAsset> candidates,
            Long ownerId,
            String assetType,
            AssetStorageService.StoredAsset stored) {
        LecturerAsset storageSource = null;
        List<LecturerAsset> ordered = candidates.stream()
                .filter(candidate -> candidate.getStorageKey() != null
                        && !candidate.getStorageKey().isBlank())
                .sorted(Comparator
                        .comparing(LecturerAsset::getStorageKey)
                        .thenComparing(
                                LecturerAsset::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        for (LecturerAsset candidate : ordered) {
            String storageKey = candidate.getStorageKey();
            /*
             * Cleanup uses task-key then all-asset-row locking. Registration
             * takes the same order before sharing an existing physical key.
             */
            reserveStorageKeyForAsset(candidate.getStorageProfileCode(), storageKey);
            List<LecturerAsset> lockedRows = candidate.getStorageProfileCode() == null
                    ? assetRepository.findByStorageKeyForUpdate(storageKey)
                    : assetRepository.findByStorageProfileCodeAndStorageKeyForUpdate(
                            candidate.getStorageProfileCode(), storageKey);
            LecturerAsset locked = lockedRows
                    .stream()
                    .filter(value -> Objects.equals(
                            candidate.getId(), value.getId()))
                    .filter(value -> isMatchingManualUploadStorage(
                            value, ownerId, assetType, stored))
                    .findFirst()
                    .orElse(null);
            if (locked == null) {
                continue;
            }
            if (storageSource == null
                    && "PUBLISHED".equalsIgnoreCase(
                            locked.getVisibility())) {
                storageSource = locked;
            }
            if ("PRIVATE".equalsIgnoreCase(locked.getVisibility())
                    && !hasAnyReference(locked.getId())) {
                return new ManualUploadDeduplication(locked, locked);
            }
        }
        return new ManualUploadDeduplication(null, storageSource);
    }

    private boolean isMatchingManualUploadStorage(
            LecturerAsset candidate,
            Long ownerId,
            String assetType,
            AssetStorageService.StoredAsset stored) {
        return Objects.equals(ownerId, candidate.getOwnerLecturerId())
                && candidate.getDeletedAt() == null
                && candidate.isContentVerified()
                && "ACTIVE".equalsIgnoreCase(candidate.getStatus())
                && ("PRIVATE".equalsIgnoreCase(candidate.getVisibility())
                    || "PUBLISHED".equalsIgnoreCase(candidate.getVisibility()))
                && assetType.equalsIgnoreCase(candidate.getAssetType())
                && "MANUAL_UPLOAD".equalsIgnoreCase(candidate.getSourceType())
                && candidate.getSha256() != null
                && candidate.getSha256().equalsIgnoreCase(stored.sha256())
                && Objects.equals(candidate.getFileSize(), stored.sizeBytes())
                && candidate.getStorageProvider() != null
                && candidate.getStorageProvider().equalsIgnoreCase(
                        stored.storageProvider())
                && Objects.equals(candidate.getStorageProfileCode(),
                        stored.storageProfileCode());
    }

    private void queueUnusedFreshUpload(
            AssetStorageService.StoredAsset stored,
            String registeredStorageKey) {
        if (stored.newlyCreated()
                && !Objects.equals(stored.storageKey(), registeredStorageKey)) {
            enqueueLifecycle(
                    null,
                    PracticeAssetLifecycleTask.ORPHAN_RECONCILE,
                    stored.storageKey(),
                    null);
        }
    }

    private record ManualUploadDeduplication(
            LecturerAsset reusableLogicalAsset,
            LecturerAsset storageSource) {
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
            String oldProfileCode = asset.getStorageProfileCode();
            AssetStorageService.StoredAsset promoted;
            try (InputStream in = (oldProfileCode == null
                    ? assetStorage.load(oldKey)
                    : assetStorage.load(oldProfileCode, oldKey)).getInputStream()) {
                String relativePath = freshStorageNamespace(
                        "lecturer-assets/" + ownerId + "/imports/"
                                + asset.getSourceImportSessionId()
                                + "/library");
                promoted = assetStorage.store(in, asset.getOriginalFilename(), relativePath);
                requireFreshStorageResult(relativePath, promoted);
                registerRollbackCleanup(
                        promoted.storageKey(), promoted.newlyCreated());
                reserveStorageKeyForAsset(promoted.storageKey());
                asset.setStorageKey(promoted.storageKey());
                asset.setStorageProvider(promoted.storageProvider());
                asset.setStorageProfileCode(promoted.storageProfileCode());
            }
            
            asset.setStatus("ACTIVE");
            asset.setUpdatedAt(LocalDateTime.now());
            log.info("[AssetService] Promoted assetId={} to library status", asset.getId());
            LecturerAsset saved = assetRepository.save(asset);
            enqueueLifecycle(asset.getId(), oldProfileCode,
                    PracticeAssetLifecycleTask.PROMOTE_CLEANUP,
                    oldKey, asset.getStorageKey());
            return saved;
        } catch (IOException e) {
            log.error("[AssetService] Failed to promote assetId={}", asset.getId(), e);
            throw new RuntimeException("Lỗi lưu trữ khi chuyển ảnh vào thư viện.", e);
        }
    }

    private LecturerAsset requireOwnedAsset(Long assetId, Long ownerId) {
        LecturerAsset asset = assetRepository.findByIdForUpdate(assetId)
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

    /**
     * Applies display-only lecturer metadata while holding the exact asset row
     * lock. Asset type and lifecycle status are semantic authorization inputs,
     * so this generic PATCH route may confirm their current value but may not
     * transition either one. Retained draft, task, artifact or immutable
     * publication evidence is never mutable through an ID-based request.
     */
    @Transactional
    public LecturerAsset updateAssetMetadata(
            Long assetId,
            Long ownerId,
            String title,
            String tagsJson,
            String assetType,
            String lecturerNote,
            String status) {
        LecturerAsset asset = assetRepository.findByIdForUpdate(assetId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Không tìm thấy asset."));
        if (ownerId == null || !ownerId.equals(asset.getOwnerLecturerId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn không có quyền chỉnh sửa asset này.");
        }
        if (asset.getDeletedAt() != null
                || (!"ACTIVE".equalsIgnoreCase(asset.getStatus())
                    && !"TEMPORARY".equalsIgnoreCase(asset.getStatus()))) {
            throw new IllegalStateException(
                    "Asset không còn ở trạng thái có thể chỉnh sửa.");
        }

        /*
         * Reference creators and cleanup paths take this same row lock. The
         * central guard therefore answers against one serialized asset state,
         * before even display metadata or updatedAt can be changed.
         */
        if (hasAnyReference(assetId)) {
            throw new IllegalStateException(
                    "Asset đang được bản nháp, tác vụ hoặc phiên bản đã xuất bản sử dụng. "
                            + "Hãy gỡ đúng liên kết trước khi chỉnh sửa.");
        }

        requireUnchangedPatchValue(
                "loại tài nguyên",
                asset.getAssetType(),
                assetType,
                "Loại tài nguyên được xác định từ nội dung đã xác minh và không thể đổi "
                        + "qua endpoint chỉnh sửa.");
        requireUnchangedPatchValue(
                "trạng thái",
                asset.getStatus(),
                status,
                "Trạng thái asset chỉ được thay đổi qua thao tác promote hoặc xóa chuyên biệt.");

        if (title != null) {
            asset.setTitle(validatedAssetTitle(title, null));
        }
        if (tagsJson != null) asset.setTagsJson(tagsJson);
        if (lecturerNote != null) asset.setLecturerNote(lecturerNote);
        asset.setUpdatedAt(LocalDateTime.now());
        return assetRepository.save(asset);
    }

    @Transactional
    public void deleteAsset(Long assetId, Long ownerId) {
        LecturerAsset asset = assetRepository.findByIdForUpdate(assetId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy asset."));
        if (!asset.getOwnerLecturerId().equals(ownerId)) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền xóa asset này.");
        }

        /*
         * A user delete is not authority to invalidate retained evidence.
         * Every reference creator takes this same asset lock before binding,
         * so this all-reference recheck is the logical-delete decision point.
         */
        if (hasAnyReference(assetId)) {
            throw new IllegalStateException(
                    "Asset đang được bản nháp, tác vụ hoặc phiên bản đã xuất bản sử dụng. "
                            + "Hãy gỡ đúng liên kết trước khi xóa.");
        }

        if ("DELETED".equalsIgnoreCase(asset.getStatus())
                || "DELETION_PENDING".equalsIgnoreCase(asset.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        asset.setDeletedAt(now);
        asset.setUpdatedAt(now);
        asset.setStatus("DELETION_PENDING");
        assetRepository.save(asset);
        enqueueLifecycle(assetId, asset.getStorageProfileCode(), PracticeAssetLifecycleTask.DELETE,
                asset.getStorageKey(), null);
        log.info("[AssetService] Queued physical delete for unreferenced assetId={}", assetId);
    }

    @Transactional
    public void cleanupTemporaryAssets(Long sessionId, Long ownerId) {
        List<Long> candidateIds = assetRepository
                .findIdsBySourceImportSessionIdAndOwnerLecturerId(
                        sessionId, ownerId);
        for (Long assetId : candidateIds) {
            try {
                LecturerAsset asset = assetRepository
                        .findByIdForUpdate(assetId)
                        .orElse(null);
                if (asset == null
                        || !ownerId.equals(asset.getOwnerLecturerId())
                        || !sessionId.equals(asset.getSourceImportSessionId())
                        || !"TEMPORARY".equalsIgnoreCase(asset.getStatus())) {
                    continue;
                }
                if (hasAnyReference(assetId)) {
                    log.info("[AssetService] Retained referenced temporary assetId={} unchanged",
                            assetId);
                    continue;
                }
                LocalDateTime now = LocalDateTime.now();
                asset.setDeletedAt(now);
                asset.setUpdatedAt(now);
                asset.setStatus("DELETION_PENDING");
                assetRepository.save(asset);
                enqueueLifecycle(assetId, asset.getStorageProfileCode(), PracticeAssetLifecycleTask.DELETE,
                        asset.getStorageKey(), null);
            } catch (RuntimeException e) {
                log.warn("[AssetService] Failed to queue temporary assetId={}", assetId, e);
            }
        }
    }

    public Resource loadAssetResource(Long assetId, Long ownerId) throws IOException {
        LecturerAsset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy asset."));
        if (!asset.getOwnerLecturerId().equals(ownerId)) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền truy cập asset này.");
        }
        return asset.getStorageProfileCode() == null
                ? assetStorage.load(asset.getStorageKey())
                : assetStorage.load(asset.getStorageProfileCode(), asset.getStorageKey());
    }

    /**
     * Loads exact bytes for a previously authorized lecturer asset while
     * enforcing the caller's provider-input bound. This does not grant access:
     * callers must still enforce their draft/reference domain boundary.
     */
    public byte[] loadOwnedAssetBytes(
            Long assetId,
            Long ownerId,
            long maximumBytes) throws IOException {
        if (maximumBytes <= 0L || maximumBytes >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Asset byte bound is outside the supported range.");
        }
        LecturerAsset asset = assetRepository
                .findByIdAndOwnerLecturerId(assetId, ownerId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Không tìm thấy asset."));
        if (asset.getFileSize() == null
                || asset.getFileSize() <= 0L
                || asset.getFileSize() > maximumBytes) {
            throw new IllegalArgumentException(
                    "Kích thước asset không hợp lệ.");
        }
        try (InputStream input = (asset.getStorageProfileCode() == null
                ? assetStorage.load(asset.getStorageKey())
                : assetStorage.load(asset.getStorageProfileCode(), asset.getStorageKey()))
                .getInputStream()) {
            byte[] bytes = input.readNBytes(
                    Math.toIntExact(maximumBytes + 1L));
            if (bytes.length == 0 || bytes.length > maximumBytes) {
                throw new IllegalArgumentException(
                        "Kích thước asset không hợp lệ.");
            }
            return bytes;
        }
    }

    /**
     * Stores a verified generated-audio candidate outside the durable task
     * completion transaction. The physical object is immediately represented
     * by an unbound private TEMPORARY row, so restart before completion leaves
     * only a retention-bounded orphan that the existing reconciler can claim.
     * Registration and exact draft linkage still happen separately.
     */
    @Transactional
    public GeneratedAudioCandidate storeGeneratedDraftAudio(
            Long ownerId,
            Long draftId,
            byte[] verifiedBytes,
            String filename,
            String mimeType,
            String expectedSha256,
            String sourceType) throws IOException {
        if (ownerId == null || draftId == null
                || verifiedBytes == null || verifiedBytes.length == 0
                || filename == null || filename.isBlank()
                || mimeType == null || mimeType.isBlank()
                || expectedSha256 == null
                || !expectedSha256.matches("[0-9a-fA-F]{64}")
                || sourceType == null
                || !"AI_TTS".equalsIgnoreCase(sourceType)) {
            throw new IllegalArgumentException(
                    "Generated audio candidate is invalid.");
        }
        Long draftOwnerId = requireManageableDraft(draftId, ownerId);
        if (!ownerId.equals(draftOwnerId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bản nháp không thuộc chủ sở hữu audio được tạo.");
        }
        String relativePath = freshStorageNamespace(
                "lecturer-assets/" + ownerId
                        + "/drafts/" + draftId
                        + "/private/generated-audio");
        AssetStorageService.StoredAsset stored = assetStorage.store(
                new ByteArrayInputStream(verifiedBytes),
                filename,
                relativePath);
        requireFreshStorageResult(relativePath, stored);
        if (stored.sizeBytes() != verifiedBytes.length
                || !stored.sha256().equalsIgnoreCase(expectedSha256)) {
            registerCompletionCleanup(stored.storageKey());
            throw new IllegalStateException(
                    "Stored generated audio does not match verified bytes.");
        }
        registerRollbackCleanup(stored.storageKey(), stored.newlyCreated());
        reserveStorageKeyForAsset(stored.storageKey());
        LocalDateTime now = LocalDateTime.now();
        LecturerAsset staged = new LecturerAsset();
        staged.setOwnerLecturerId(ownerId);
        staged.setStorageProvider(stored.storageProvider());
        staged.setStorageProfileCode(stored.storageProfileCode());
        staged.setStorageKey(stored.storageKey());
        staged.setOriginalFilename(filename);
        staged.setMimeType(mimeType.toLowerCase(Locale.ROOT));
        staged.setContentVerified(true);
        staged.setFileSize(stored.sizeBytes());
        staged.setSha256(stored.sha256().toLowerCase(Locale.ROOT));
        staged.setAssetType("AUDIO");
        staged.setTitle(validatedAssetTitle(
                filename, "Tài nguyên tạm"));
        staged.setSourceType(sourceType.toUpperCase(Locale.ROOT));
        staged.setStatus("TEMPORARY");
        staged.setVisibility("PRIVATE");
        staged.setRetentionUntil(now.plus(UNBOUND_UPLOAD_RETENTION));
        staged.setCreatedAt(now);
        staged.setUpdatedAt(now);
        staged = assetRepository.save(staged);
        if (staged.getId() == null) {
            throw new IllegalStateException(
                    "Không thể đăng ký audio đang chờ xử lý.");
        }
        return new GeneratedAudioCandidate(
                staged.getId(),
                ownerId,
                draftId,
                stored.storageKey(),
                stored.sizeBytes(),
                stored.sha256().toLowerCase(Locale.ROOT),
                stored.newlyCreated(),
                filename,
                mimeType.toLowerCase(Locale.ROOT),
                sourceType.toUpperCase(Locale.ROOT));
    }

    @Transactional
    public LecturerAsset registerGeneratedDraftAudio(
            GeneratedAudioCandidate candidate,
            String title,
            String placement,
            String referenceKey) {
        if (candidate == null) {
            throw new IllegalArgumentException(
                    "Generated audio candidate is required.");
        }
        synchronized (candidate) {
            candidate.beginRegistration();
            try {
                Long draftOwnerId = requireManageableDraft(
                        candidate.draftId,
                        candidate.ownerId);
                if (!candidate.ownerId.equals(draftOwnerId)) {
                    throw new org.springframework.security.access.AccessDeniedException(
                            "Bản nháp không thuộc chủ sở hữu audio được tạo.");
                }
                LecturerAsset staged = assetRepository
                        .findByIdForUpdate(candidate.stagedAssetId)
                        .orElseThrow(() ->
                                new jakarta.persistence.EntityNotFoundException(
                                        "Không tìm thấy audio đang chờ đăng ký."));
                requireExactGeneratedStaging(candidate, staged);
                if (hasAnyReference(staged.getId())) {
                    throw new IllegalStateException(
                            "Audio đang chờ đăng ký đã có liên kết ngoài dự kiến.");
                }
                LecturerAsset asset = assetRepository
                        .findByOwnerLecturerIdAndSha256AndStatusAndDeletedAtIsNull(
                                candidate.ownerId,
                                candidate.sha256,
                                "ACTIVE")
                        .stream()
                        .filter(value -> candidate.sourceType.equalsIgnoreCase(
                                value.getSourceType()))
                        .filter(value -> "AUDIO".equalsIgnoreCase(
                                value.getAssetType()))
                        .findFirst()
                        .orElse(null);
                LocalDateTime now = LocalDateTime.now();
                if (asset == null) {
                    staged.setTitle(validatedAssetTitle(
                            title, "Tài nguyên"));
                    staged.setStatus("ACTIVE");
                    staged.setRetentionUntil(null);
                    staged.setUpdatedAt(now);
                    asset = assetRepository.save(staged);
                } else {
                    staged.setStatus("DELETION_PENDING");
                    staged.setDeletedAt(now);
                    staged.setUpdatedAt(now);
                    assetRepository.save(staged);
                    enqueueLifecycle(
                            staged.getId(),
                            staged.getStorageProfileCode(),
                            PracticeAssetLifecycleTask.DELETE,
                            staged.getStorageKey(),
                            null);
                }
                if (materialReferenceService == null) {
                    throw new IllegalStateException(
                            "Material reference service chưa được cấu hình.");
                }
                materialReferenceService.linkDraft(
                        candidate.draftId,
                        asset.getId(),
                        placement,
                        referenceKey,
                        null);
                candidate.registrationSucceeded();
                return asset;
            } catch (RuntimeException exception) {
                candidate.registrationFailed();
                throw exception;
            }
        }
    }

    @Transactional
    public void linkExistingGeneratedDraftAudio(
            Long draftId,
            Long ownerId,
            Long assetId,
            String sourceType,
            String placement,
            String referenceKey) {
        Long draftOwnerId = requireManageableDraft(draftId, ownerId);
        LecturerAsset asset = assetRepository
                .findByIdAndOwnerLecturerId(assetId, draftOwnerId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Không tìm thấy audio đã tạo."));
        if (!asset.isContentVerified()
                || asset.getDeletedAt() != null
                || !"ACTIVE".equalsIgnoreCase(asset.getStatus())
                || !"AUDIO".equalsIgnoreCase(asset.getAssetType())
                || !sourceType.equalsIgnoreCase(asset.getSourceType())) {
            throw new IllegalArgumentException(
                    "Audio đã tạo không còn ở trạng thái có thể liên kết.");
        }
        if (materialReferenceService == null) {
            throw new IllegalStateException(
                    "Material reference service chưa được cấu hình.");
        }
        materialReferenceService.linkDraft(
                draftId,
                assetId,
                placement,
                referenceKey,
                null);
    }

    @Transactional
    public void discardGeneratedDraftAudio(
            GeneratedAudioCandidate candidate) {
        if (candidate == null) {
            return;
        }
        synchronized (candidate) {
            if (!candidate.chooseCleanup()) {
                return;
            }
            LecturerAsset staged = assetRepository
                    .findByIdForUpdate(candidate.stagedAssetId)
                    .orElse(null);
            if (staged == null) {
                registerCompletionCleanup(candidate.storageKey);
                return;
            }
            if (!matchesGeneratedStaging(candidate, staged)
                    || hasAnyReference(staged.getId())) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            staged.setStatus("DELETION_PENDING");
            staged.setDeletedAt(now);
            staged.setUpdatedAt(now);
            assetRepository.save(staged);
            enqueueLifecycle(
                    staged.getId(),
                    staged.getStorageProfileCode(),
                    PracticeAssetLifecycleTask.DELETE,
                    staged.getStorageKey(),
                    null);
        }
    }

    @Transactional
    public PracticeMaterialReference linkAssetToDraft(Long draftId, Long assetId, Long ownerId,
                                                    String sectionTempId, String groupTempId,
                                                    String questionTempId, String placement, String altText) {
        Long draftOwnerId = requireManageableDraft(draftId, ownerId);
        LecturerAsset asset = assetRepository.findByIdForUpdate(assetId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy asset."));
        if (!"ACTIVE".equalsIgnoreCase(asset.getStatus())
                && !"TEMPORARY".equalsIgnoreCase(asset.getStatus())) {
            throw new IllegalStateException("Asset không còn ở trạng thái có thể liên kết.");
        }
        if (!draftOwnerId.equals(asset.getOwnerLecturerId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn không có quyền sử dụng asset này.");
        }
        if (materialReferenceService == null) {
            throw new IllegalStateException("Material reference service chưa được cấu hình.");
        }
        String referenceKey = referenceKey(sectionTempId, groupTempId,
                questionTempId, placement);
        String metadataJson = referenceMetadata(sectionTempId, groupTempId,
                questionTempId, altText);
        return materialReferenceService.linkDraft(draftId, assetId, placement,
                referenceKey, metadataJson);
    }

    /**
     * Internal prompt-retention handoff. It is not an ID-authorized public
     * delete route; exact repository references are rechecked under the asset
     * row lock before a physical lifecycle task is made eligible.
     */
    @Transactional
    public void queuePrivatePromptAssetIfUnreferenced(Long assetId) {
        if (assetId == null) return;
        LecturerAsset asset = assetRepository.findByIdForUpdate(assetId)
                .orElse(null);
        if (asset == null
                || asset.getDeletedAt() != null
                || !"PRIVATE".equalsIgnoreCase(asset.getVisibility())
                || (!"MANUAL_UPLOAD".equalsIgnoreCase(asset.getSourceType())
                    && !"AI_TTS".equalsIgnoreCase(asset.getSourceType()))
                || hasAnyReference(assetId)) {
            return;
        }
        asset.setStatus("DELETION_PENDING");
        asset.setDeletedAt(LocalDateTime.now());
        asset.setUpdatedAt(LocalDateTime.now());
        assetRepository.save(asset);
        enqueueLifecycle(
                assetId,
                asset.getStorageProfileCode(),
                PracticeAssetLifecycleTask.DELETE,
                asset.getStorageKey(),
                null);
    }

    @Transactional
    public void unlinkAssetFromDraft(Long draftId, Long referenceId, Long ownerId) {
        requireManageableDraft(draftId, ownerId);
        PracticeMaterialReference reference = materialReferenceService.referencesForDraft(draftId).stream()
                .filter(value -> referenceId.equals(value.getId()))
                .findFirst()
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy liên kết asset."));
        materialReferenceService.unlinkDraftReference(draftId, referenceId);
        queueArchivedAssetIfUnreferenced(reference.getAssetId());
    }

    private void requireOwnedDraft(Long draftId, Long ownerId) {
        if (draftRepository == null || draftRepository.findByIdAndOwnerId(draftId, ownerId).isEmpty()) {
            throw new jakarta.persistence.EntityNotFoundException("Bản nháp không tồn tại.");
        }
    }

    private Long requireManageableDraft(Long draftId, Long actorId) {
        if (authorizationService == null) {
            requireOwnedDraft(draftId, actorId);
            return actorId;
        }
        return authorizationService.requireDraft(
                draftId, actorId, PracticeAction.MATERIAL_MANAGE).ownerId();
    }

    private boolean hasAnyReference(Long assetId) {
        if (assetReferenceGuard == null) {
            /*
             * Compatibility constructors exist only for bounded tests/legacy
             * callers. Missing the all-reference guard is never authority to
             * archive or delete a production asset.
             */
            return true;
        }
        return assetReferenceGuard.isRetained(assetId);
    }

    private static void requireUnchangedPatchValue(
            String field,
            String currentValue,
            String requestedValue,
            String message) {
        if (requestedValue == null) {
            return;
        }
        String normalizedRequested = requestedValue.trim();
        if (normalizedRequested.isEmpty()
                || currentValue == null
                || !currentValue.equalsIgnoreCase(normalizedRequested)) {
            throw new IllegalArgumentException(field + " không hợp lệ. " + message);
        }
    }

    private static String referenceKey(String sectionRef, String groupRef,
                                       String questionRef, String placement) {
        String value = String.join("|",
                sectionRef == null ? "" : sectionRef,
                groupRef == null ? "" : groupRef,
                questionRef == null ? "" : questionRef,
                placement == null ? "" : placement);
        return value.length() <= 255 ? value : value.substring(0, 255);
    }

    private static String referenceMetadata(String sectionRef, String groupRef,
                                            String questionRef, String altText) {
        com.fasterxml.jackson.databind.node.ObjectNode metadata =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        if (sectionRef != null) metadata.put("sectionRef", sectionRef);
        if (groupRef != null) metadata.put("groupRef", groupRef);
        if (questionRef != null) metadata.put("questionRef", questionRef);
        if (altText != null) metadata.put("altText", altText);
        return metadata.isEmpty() ? null : metadata.toString();
    }

    private void queueArchivedAssetIfUnreferenced(Long assetId) {
        LecturerAsset asset = assetRepository.findByIdForUpdate(assetId)
                .orElse(null);
        if (asset == null
                || !"ARCHIVED".equalsIgnoreCase(asset.getStatus())
                || hasAnyReference(assetId)) {
            return;
        }
        asset.setStatus("DELETION_PENDING");
        asset.setUpdatedAt(LocalDateTime.now());
        assetRepository.save(asset);
        enqueueLifecycle(assetId, asset.getStorageProfileCode(), PracticeAssetLifecycleTask.DELETE,
                asset.getStorageKey(), null);
    }

    private void enqueueLifecycle(Long assetId, String operation, String sourceKey,
                                  String targetKey) {
        enqueueLifecycle(assetId, assetStorage.profileCode(), operation, sourceKey, targetKey);
    }

    private void enqueueLifecycle(Long assetId, String storageProfileCode,
                                  String operation, String sourceKey,
                                  String targetKey) {
        if (lifecycleTaskRepository == null) {
            if (sourceKey != null && !sourceKey.isBlank()) {
                registerCompletionCleanup(sourceKey);
            }
            return;
        }
        lifecycleTaskRepository.save(new PracticeAssetLifecycleTask(
                assetId, storageProfileCode, operation, sourceKey, targetKey));
    }

    /**
     * Serializes a new LecturerAsset registration with physical cleanup for
     * the same key. Fresh-key callers need only this task lock; callers sharing
     * an existing key take this lock before locking every LecturerAsset row for
     * that key. This matches cleanup's lock order: pending cleanup becomes
     * obsolete, while every running cleanup fails registration closed
     * regardless of lease age.
     */
    private void reserveStorageKeyForAsset(String storageKey) {
        reserveStorageKeyForAsset(assetStorage.profileCode(), storageKey);
    }

    private void reserveStorageKeyForAsset(String storageProfileCode, String storageKey) {
        if (lifecycleTaskRepository == null
                || storageKey == null
                || storageKey.isBlank()) {
            return;
        }
        List<PracticeAssetLifecycleTask> active =
                storageProfileCode == null
                        ? lifecycleTaskRepository
                            .findActiveBySourceStorageKeyForUpdate(storageKey)
                        : lifecycleTaskRepository
                            .findActiveByStorageProfileCodeAndSourceStorageKeyForUpdate(
                                    storageProfileCode, storageKey);
        if (active.stream().anyMatch(task ->
                "RUNNING".equals(task.getStatus()))) {
            throw new IllegalStateException(
                    "Tài nguyên đang được đối chiếu lưu trữ. Vui lòng thử lại.");
        }
        active.forEach(PracticeAssetLifecycleTask::markCompleted);
        if (!active.isEmpty()) {
            lifecycleTaskRepository.saveAllAndFlush(active);
        }
    }

    /**
     * A fresh physical namespace makes the first store non-reusing by
     * construction. It removes the unsafe interval in which an older cleanup
     * worker could have confirmed deletion before this transaction knew the
     * content-addressed filename.
     */
    private static String freshStorageNamespace(String basePath) {
        return basePath + "/objects/"
                + java.util.UUID.randomUUID().toString().toLowerCase(
                        Locale.ROOT);
    }

    private void requireFreshStorageResult(
            String freshNamespace,
            AssetStorageService.StoredAsset stored) {
        String expectedPrefix = freshNamespace + "/";
        if (stored == null
                || !stored.newlyCreated()
                || stored.storageKey() == null
                || !stored.storageKey().startsWith(expectedPrefix)
                || stored.storageKey().length() <= expectedPrefix.length()) {
            if (stored != null
                    && stored.newlyCreated()
                    && stored.storageKey() != null
                    && !stored.storageKey().isBlank()) {
                registerCompletionCleanup(stored.storageKey());
            }
            throw new IllegalStateException(
                    "Không thể thiết lập vùng lưu trữ riêng an toàn.");
        }
    }

    private static void requireExactGeneratedStaging(
            GeneratedAudioCandidate candidate,
            LecturerAsset staged) {
        if (!matchesGeneratedStaging(candidate, staged)) {
            throw new IllegalStateException(
                    "Audio đang chờ đăng ký không còn đúng định danh ban đầu.");
        }
    }

    private static boolean matchesGeneratedStaging(
            GeneratedAudioCandidate candidate,
            LecturerAsset staged) {
        return java.util.Objects.equals(
                        candidate.stagedAssetId, staged.getId())
                && java.util.Objects.equals(
                        candidate.ownerId, staged.getOwnerLecturerId())
                && java.util.Objects.equals(
                        candidate.storageKey, staged.getStorageKey())
                && java.util.Objects.equals(
                        candidate.sizeBytes, staged.getFileSize())
                && java.util.Objects.equals(
                        candidate.filename, staged.getOriginalFilename())
                && candidate.sha256.equalsIgnoreCase(staged.getSha256())
                && candidate.mimeType.equalsIgnoreCase(staged.getMimeType())
                && staged.isContentVerified()
                && staged.getDeletedAt() == null
                && "AUDIO".equalsIgnoreCase(staged.getAssetType())
                && "PRIVATE".equalsIgnoreCase(staged.getVisibility())
                && candidate.sourceType.equalsIgnoreCase(
                        staged.getSourceType())
                && "TEMPORARY".equalsIgnoreCase(staged.getStatus())
                && staged.getRetentionUntil() != null;
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
                            discardStoredCandidate(storageKey, true);
                        }
                    }
                });
    }

    private void discardStoredCandidate(
            String storageKey,
            boolean newlyCreated) {
        if (!newlyCreated) {
            return;
        }
        if (lifecycleTaskRepository != null) {
            try {
                lifecycleTaskRepository.saveAndFlush(
                        new PracticeAssetLifecycleTask(
                                null,
                                assetStorage.profileCode(),
                                PracticeAssetLifecycleTask.ORPHAN_RECONCILE,
                                storageKey,
                                null));
            } catch (RuntimeException exception) {
                log.error(
                        "[AssetService] Could not persist candidate cleanup exception={}",
                        exception.getClass().getSimpleName());
            }
            return;
        }
        try {
            if (assetStorage.profileCode() == null) {
                assetStorage.delete(storageKey);
            } else {
                assetStorage.delete(assetStorage.profileCode(), storageKey);
            }
        } catch (IOException exception) {
            enqueueLifecycle(
                    null,
                    PracticeAssetLifecycleTask.ORPHAN_RECONCILE,
                    storageKey,
                    null);
        }
    }

    private void registerCompletionCleanup(String storageKey) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager
                .isSynchronizationActive()) {
            discardStoredCandidate(storageKey, true);
            return;
        }
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                                discardStoredCandidate(storageKey, true);
                            }
                        });
    }

    static String validatedAssetTitle(
            String value, String fallback) {
        String candidate = value;
        if (candidate == null || candidate.isBlank()) {
            candidate = fallback;
        }
        if (candidate == null || candidate.isBlank()) {
            throw new IllegalArgumentException(
                    "Tiêu đề tài nguyên không được để trống.");
        }
        if (candidate.codePointCount(0, candidate.length()) > 255) {
            throw new IllegalArgumentException(
                    "Tiêu đề tài nguyên không được vượt quá 255 ký tự.");
        }
        if (candidate.codePoints().anyMatch(
                Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "Tiêu đề tài nguyên chứa ký tự điều khiển không hợp lệ.");
        }
        return candidate;
    }

    /**
     * Opaque handle deliberately exposing no storage key, digest or asset id.
     */
    public static final class GeneratedAudioCandidate {
        private enum Disposition {
            STORED,
            REGISTERING,
            REGISTERED,
            CLEANUP
        }

        private final Long stagedAssetId;
        private final Long ownerId;
        private final Long draftId;
        private final String storageKey;
        private final long sizeBytes;
        private final String sha256;
        private final boolean newlyCreated;
        private final String filename;
        private final String mimeType;
        private final String sourceType;
        private Disposition disposition = Disposition.STORED;

        private GeneratedAudioCandidate(
                Long stagedAssetId,
                Long ownerId,
                Long draftId,
                String storageKey,
                long sizeBytes,
                String sha256,
                boolean newlyCreated,
                String filename,
                String mimeType,
                String sourceType) {
            this.stagedAssetId = stagedAssetId;
            this.ownerId = ownerId;
            this.draftId = draftId;
            this.storageKey = storageKey;
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256;
            this.newlyCreated = newlyCreated;
            this.filename = filename;
            this.mimeType = mimeType;
            this.sourceType = sourceType;
        }

        private synchronized void beginRegistration() {
            if (disposition != Disposition.STORED) {
                throw new IllegalStateException(
                        "Generated audio candidate is no longer available.");
            }
            disposition = Disposition.REGISTERING;
        }

        private synchronized void registrationSucceeded() {
            if (disposition != Disposition.REGISTERING) {
                throw new IllegalStateException(
                        "Generated audio candidate registration is invalid.");
            }
            disposition = Disposition.REGISTERED;
        }

        private synchronized void registrationFailed() {
            if (disposition != Disposition.REGISTERING) {
                return;
            }
            disposition = Disposition.STORED;
        }

        private synchronized boolean chooseCleanup() {
            if (disposition != Disposition.STORED) {
                return false;
            }
            disposition = Disposition.CLEANUP;
            return true;
        }

        @Override
        public String toString() {
            return "GeneratedAudioCandidate{"
                    + "sizeBytes=" + sizeBytes
                    + ", mimeType='" + mimeType + '\''
                    + ", newlyCreated=" + newlyCreated
                    + '}';
        }
    }
}
