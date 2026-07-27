package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeMaterialReferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ksh.features.practice.manage.speaking.SpeakingPromptPublicationService;

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
        LecturerAsset asset = assetRepository.findByIdForUpdate(assetId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Không tìm thấy asset."));
        if (asset.getDeletedAt() != null
                || (!"ACTIVE".equalsIgnoreCase(asset.getStatus())
                    && !"TEMPORARY".equalsIgnoreCase(asset.getStatus()))) {
            throw new IllegalStateException(
                    "Asset không còn ở trạng thái có thể liên kết.");
        }
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
        promoteDraftReferences(
                draftId, setId, publishedVersionId, Set.of());
    }

    @Transactional
    public void promoteDraftReferences(
            Long draftId,
            Long setId,
            Long publishedVersionId,
            Set<SpeakingPromptPublicationService.ActiveAssetBinding>
                    activeSpeakingBindings) {
        Set<SpeakingPromptPublicationService.ActiveAssetBinding> exactSpeaking =
                activeSpeakingBindings == null ? Set.of() : Set.copyOf(activeSpeakingBindings);
        Set<AssetPlacement> assets = new LinkedHashSet<>();
        for (PracticeMaterialReference reference : referenceRepository.findByDraftId(draftId)) {
            if (isSpeakingPromptPlacement(reference.getPlacement())
                    && exactSpeaking.stream().noneMatch(binding ->
                    binding.assetId().equals(reference.getAssetId())
                            && binding.placement().equals(reference.getPlacement())
                            && binding.questionClientId().equals(
                            reference.getReferenceKey()))) {
                continue;
            }
            assets.add(new AssetPlacement(
                    reference.getAssetId(), normalizePlacement(reference.getPlacement())));
        }
        Map<Long, LecturerAsset> lockedAssets = new LinkedHashMap<>();
        assets.stream()
                .map(AssetPlacement::assetId)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .forEach(assetId -> {
                    LecturerAsset asset = assetRepository
                            .findByIdForUpdate(assetId)
                            .orElseThrow(() ->
                                    new jakarta.persistence.EntityNotFoundException(
                                            "Không tìm thấy asset để xuất bản."));
                    if (asset.getDeletedAt() != null
                            || (!"ACTIVE".equalsIgnoreCase(asset.getStatus())
                                && !"TEMPORARY".equalsIgnoreCase(
                                        asset.getStatus()))) {
                        throw new IllegalStateException(
                                "Asset không còn ở trạng thái có thể xuất bản.");
                    }
                    lockedAssets.put(assetId, asset);
                });

        /*
         * Every parent asset is now locked and known linkable. Only after that
         * fail-closed decision may immutable published references be inserted.
         */
        for (AssetPlacement entry : assets) {
            if (!referenceRepository.existsByAssetIdAndPublishedVersionIdAndPlacement(
                    entry.assetId(), publishedVersionId, entry.placement())) {
                referenceRepository.save(PracticeMaterialReference.published(
                        entry.assetId(), setId, publishedVersionId, entry.placement()));
            }
        }
        for (LecturerAsset asset : lockedAssets.values()) {
            asset.setVisibility("PUBLISHED");
            asset.setStatus("ACTIVE");
            asset.setRetentionUntil(null);
            assetRepository.save(asset);
        }
    }

    private static boolean isSpeakingPromptPlacement(String placement) {
        return "SPEAKING_PROMPT_ORIGINAL".equals(placement)
                || "SPEAKING_PROMPT_TTS".equals(placement)
                || PracticeAssessmentExcelService.EXCEL_SPEAKING_STAGING
                    .equals(placement);
    }

    @Transactional
    public void unlinkDraft(Long draftId, Long assetId, String placement) {
        referenceRepository.deleteByAssetIdAndDraftIdAndPlacement(
                assetId, draftId, normalizePlacement(placement));
    }

    @Transactional
    public void unlinkDraft(Long draftId, Long assetId, String placement,
                            String referenceKey) {
        referenceRepository
                .deleteByAssetIdAndDraftIdAndPlacementAndReferenceKey(
                        assetId,
                        draftId,
                        normalizePlacement(placement),
                        referenceKey == null ? "" : referenceKey.trim());
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
    public boolean hasDraftReference(
            Long draftId,
            Long assetId,
            String placement,
            String referenceKey) {
        return referenceRepository
                .existsByAssetIdAndDraftIdAndPlacementAndReferenceKey(
                        assetId,
                        draftId,
                        normalizePlacement(placement),
                        referenceKey == null ? "" : referenceKey.trim());
    }

    @Transactional(readOnly = true)
    public boolean hasPublishedReference(Long assetId) {
        return referenceRepository.existsByAssetIdAndReferenceScope(
                assetId, PracticeMaterialReference.SCOPE_PUBLISHED_VERSION);
    }

    @Transactional(readOnly = true)
    public boolean hasPublishedVersionReference(Long assetId, Long publishedVersionId) {
        return referenceRepository.existsByAssetIdAndPublishedVersionId(
                assetId, publishedVersionId);
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
