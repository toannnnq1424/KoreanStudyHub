package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeDraftAssetUsage;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeDraftAssetUsageRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
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

    @org.springframework.beans.factory.annotation.Autowired
    public LecturerAssetService(LecturerAssetRepository assetRepository,
                                PracticeDraftAssetUsageRepository usageRepository,
                                PracticeDraftRepository draftRepository,
                                AssetStorageService assetStorage) {
        this.assetRepository = assetRepository;
        this.usageRepository = usageRepository;
        this.draftRepository = draftRepository;
        this.assetStorage = assetStorage;
    }

    public LecturerAssetService(LecturerAssetRepository assetRepository,
                                PracticeDraftAssetUsageRepository usageRepository,
                                AssetStorageService assetStorage) {
        this(assetRepository, usageRepository, null, assetStorage);
    }

    @Transactional
    public LecturerAsset createTemporaryAsset(Long ownerId, Long sessionId, Long regionId, InputStream content,
                                              String originalFilename, String mimeType, Integer w, Integer h, Long sizeBytes,
                                              Integer sourcePageNumber, Double cropX, Double cropY, Double cropWidth, Double cropHeight,
                                              String lecturerNote) throws IOException {
        String relativePath = "lecturer-assets/" + ownerId + "/imports/" + sessionId + "/temporary";
        
        // Store physically via AssetStorageService (which computes SHA-256 and saves as SHA-256.ext)
        AssetStorageService.StoredAsset stored = assetStorage.store(content, originalFilename, relativePath);

        // Deduplication: Check if lecturer already owns active asset with same SHA-256
        List<LecturerAsset> duplicate = assetRepository.findByOwnerLecturerIdAndSha256AndStatusAndDeletedAtIsNull(ownerId, stored.sha256(), "ACTIVE");
        if (!duplicate.isEmpty()) {
            LecturerAsset existing = duplicate.get(0);
            log.info("[AssetService] Reusing active assetId={} after content deduplication", existing.getId());
            // Delete temp physical file as it's duplicate
            try {
                assetStorage.delete(stored.storageKey());
            } catch (IOException e) {
                log.warn("[AssetService] Failed to delete redundant duplicate physical file", e);
            }
            return existing;
        }

        // Create new LecturerAsset entity
        LecturerAsset asset = new LecturerAsset();
        asset.setOwnerLecturerId(ownerId);
        asset.setSourceImportSessionId(sessionId);
        asset.setSourceRegionId(regionId);
        asset.setStorageProvider("LOCAL");
        asset.setStorageKey(stored.storageKey());
        asset.setOriginalFilename(originalFilename);
        asset.setMimeType(mimeType);
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
        asset.setCreatedAt(LocalDateTime.now());
        asset.setUpdatedAt(LocalDateTime.now());

        return assetRepository.save(asset);
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
            try (InputStream in = assetStorage.load(oldKey).getInputStream()) {
                String relativePath = "lecturer-assets/" + ownerId + "/imports/" + asset.getSourceImportSessionId() + "/library";
                AssetStorageService.StoredAsset stored = assetStorage.store(in, asset.getOriginalFilename(), relativePath);
                asset.setStorageKey(stored.storageKey());
            }
            
            // Delete old file
            assetStorage.delete(oldKey);
            
            asset.setStatus("ACTIVE");
            asset.setUpdatedAt(LocalDateTime.now());
            log.info("[AssetService] Promoted assetId={} to library status", asset.getId());
            return assetRepository.save(asset);
        } catch (IOException e) {
            log.error("[AssetService] Failed to promote assetId={}", asset.getId(), e);
            throw new RuntimeException("Lỗi lưu trữ khi chuyển ảnh vào thư viện.", e);
        }
    }

    private LecturerAsset requireOwnedAsset(Long assetId, Long ownerId) {
        LecturerAsset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy asset."));
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

        // Soft delete: Check usages first
        List<PracticeDraftAssetUsage> usages = usageRepository.findByAssetId(assetId);
        if (!usages.isEmpty()) {
            // Keep in DB but mark deleted/archived so it won't be seen in general library queries
            asset.setDeletedAt(LocalDateTime.now());
            asset.setStatus("ARCHIVED");
            assetRepository.save(asset);
            log.info("[AssetService] Soft deleted assetId={} due to active references count={}", assetId, usages.size());
        } else {
            // Safe to delete physically if no usages
            try {
                assetStorage.delete(asset.getStorageKey());
            } catch (IOException e) {
                log.warn("[AssetService] Failed to delete file for assetId={}", assetId, e);
            }
            assetRepository.delete(asset);
            log.info("[AssetService] Hard deleted assetId={} and its physical file", assetId);
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
                assetStorage.delete(asset.getStorageKey());
                assetRepository.delete(asset);
            } catch (IOException e) {
                log.warn("[AssetService] Failed to cleanup temporary assetId={}", asset.getId(), e);
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
        requireOwnedDraft(draftId, ownerId);
        LecturerAsset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy asset."));
        if (!ownerId.equals(asset.getOwnerLecturerId())) {
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
        
        return usageRepository.save(usage);
    }

    @Transactional
    public void unlinkAssetFromDraft(Long draftId, Long usageId, Long ownerId) {
        requireOwnedDraft(draftId, ownerId);
        PracticeDraftAssetUsage usage = usageRepository.findById(usageId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy liên kết asset."));
        if (!draftId.equals(usage.getDraftId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn không có quyền xóa liên kết asset này.");
        }
        usageRepository.delete(usage);
    }

    private void requireOwnedDraft(Long draftId, Long ownerId) {
        if (draftRepository == null || draftRepository.findByIdAndOwnerId(draftId, ownerId).isEmpty()) {
            throw new jakarta.persistence.EntityNotFoundException("Bản nháp không tồn tại.");
        }
    }
}
