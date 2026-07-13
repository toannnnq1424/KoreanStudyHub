package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.features.practice.repository.LecturerAssetRepository;
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
    private final LecturerAssetRepository assetRepository;

    public PracticeMaterialReferenceService(
            PracticeMaterialReferenceRepository referenceRepository,
            LecturerAssetRepository assetRepository) {
        this.referenceRepository = referenceRepository;
        this.assetRepository = assetRepository;
    }

    @Transactional
    public PracticeMaterialReference linkDraft(Long draftId, Long assetId,
                                               String placement) {
        return linkDraft(draftId, assetId, placement, "", null);
    }

    @Transactional
    public PracticeMaterialReference linkDraft(Long draftId, Long assetId,
                                               String placement, String referenceKey,
                                               String referenceMetadataJson) {
        String normalizedPlacement = normalizePlacement(placement);
        String normalizedKey = referenceKey == null ? "" : referenceKey.trim();
        if (referenceRepository.existsByAssetIdAndDraftIdAndPlacementAndReferenceKey(
                assetId, draftId, normalizedPlacement, normalizedKey)) {
            return referenceRepository.findByDraftId(draftId).stream()
                    .filter(reference -> assetId.equals(reference.getAssetId()))
                    .filter(reference -> normalizedPlacement.equals(reference.getPlacement()))
                    .filter(reference -> normalizedKey.equals(reference.getReferenceKey()))
                    .findFirst()
                    .orElseThrow();
        }
        return referenceRepository.save(
                PracticeMaterialReference.draft(assetId, draftId, normalizedPlacement,
                        normalizedKey, referenceMetadataJson));
    }

    @Transactional
    public void promoteDraftReferences(Long draftId, Long setId,
                                       Long publishedVersionId) {
        Set<AssetPlacement> assets = new LinkedHashSet<>();
        for (PracticeMaterialReference reference : referenceRepository.findByDraftId(draftId)) {
            assets.add(new AssetPlacement(
                    reference.getAssetId(), normalizePlacement(reference.getPlacement())));
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

    @Transactional
    public void unlinkDraftReference(Long draftId, Long referenceId) {
        referenceRepository.deleteByIdAndDraftId(referenceId, draftId);
    }

    @Transactional(readOnly = true)
    public List<PracticeMaterialReference> references(Long assetId) {
        return referenceRepository.findByAssetId(assetId);
    }

    @Transactional(readOnly = true)
    public List<PracticeMaterialReference> referencesForDraft(Long draftId) {
        return referenceRepository.findByDraftId(draftId);
    }

    @Transactional(readOnly = true)
    public boolean hasAnyReference(Long assetId) {
        return !referenceRepository.findByAssetId(assetId).isEmpty();
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
