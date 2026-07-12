package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeDraftAssetUsage;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeDraftAssetUsageRepository;
import com.ksh.features.practice.repository.PracticeMaterialReferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class PracticeMaterialReferenceService {

    private final PracticeMaterialReferenceRepository referenceRepository;
    private final PracticeDraftAssetUsageRepository legacyUsageRepository;
    private final LecturerAssetRepository assetRepository;

    public PracticeMaterialReferenceService(
            PracticeMaterialReferenceRepository referenceRepository,
            PracticeDraftAssetUsageRepository legacyUsageRepository,
            LecturerAssetRepository assetRepository) {
        this.referenceRepository = referenceRepository;
        this.legacyUsageRepository = legacyUsageRepository;
        this.assetRepository = assetRepository;
    }

    @Transactional
    public PracticeMaterialReference linkDraft(Long draftId, Long assetId,
                                               String placement) {
        String normalizedPlacement = normalizePlacement(placement);
        if (referenceRepository.existsByAssetIdAndDraftIdAndPlacement(
                assetId, draftId, normalizedPlacement)) {
            return referenceRepository.findByDraftId(draftId).stream()
                    .filter(reference -> assetId.equals(reference.getAssetId()))
                    .filter(reference -> normalizedPlacement.equals(reference.getPlacement()))
                    .findFirst()
                    .orElseThrow();
        }
        return referenceRepository.save(
                PracticeMaterialReference.draft(assetId, draftId, normalizedPlacement));
    }

    @Transactional
    public void promoteDraftReferences(Long draftId, Long setId,
                                       Long publishedVersionId) {
        Set<AssetPlacement> assets = new LinkedHashSet<>();
        for (PracticeMaterialReference reference : referenceRepository.findByDraftId(draftId)) {
            assets.add(new AssetPlacement(
                    reference.getAssetId(), normalizePlacement(reference.getPlacement())));
        }
        for (PracticeDraftAssetUsage usage : legacyUsageRepository.findByDraftId(draftId)) {
            assets.add(new AssetPlacement(
                    usage.getAssetId(), normalizePlacement(usage.getPlacement())));
        }
        Set<Long> activatedAssets = new HashSet<>();
        for (AssetPlacement entry : assets) {
            if (!referenceRepository.existsByAssetIdAndPublishedVersionIdAndPlacement(
                    entry.assetId(), publishedVersionId, entry.placement())) {
                referenceRepository.save(PracticeMaterialReference.published(
                        entry.assetId(), setId, publishedVersionId, entry.placement()));
            }
            if (!activatedAssets.add(entry.assetId())) {
                continue;
            }
            LecturerAsset asset = assetRepository.findById(entry.assetId()).orElse(null);
            if (asset != null) {
                asset.setVisibility("PUBLISHED");
                asset.setStatus("ACTIVE");
                asset.setDeletedAt(null);
                asset.setRetentionUntil(null);
                assetRepository.save(asset);
            }
        }
    }

    @Transactional
    public void unlinkDraft(Long draftId, Long assetId, String placement) {
        referenceRepository.deleteByAssetIdAndDraftIdAndPlacement(
                assetId, draftId, normalizePlacement(placement));
    }

    @Transactional(readOnly = true)
    public List<PracticeMaterialReference> references(Long assetId) {
        return referenceRepository.findByAssetId(assetId);
    }

    @Transactional(readOnly = true)
    public boolean hasAnyReference(Long assetId) {
        return !referenceRepository.findByAssetId(assetId).isEmpty()
                || !legacyUsageRepository.findByAssetId(assetId).isEmpty();
    }

    @Transactional(readOnly = true)
    public boolean hasPublishedReference(Long assetId) {
        return referenceRepository.existsByAssetIdAndReferenceScope(
                assetId, PracticeMaterialReference.SCOPE_PUBLISHED_VERSION);
    }

    private static String normalizePlacement(String placement) {
        if (placement == null || placement.isBlank()) return "MATERIAL";
        String value = placement.trim().toUpperCase(java.util.Locale.ROOT)
                .replaceAll("[^A-Z0-9_-]", "_");
        return value.length() <= 64 ? value : value.substring(0, 64);
    }

    private record AssetPlacement(Long assetId, String placement) {
    }
}
