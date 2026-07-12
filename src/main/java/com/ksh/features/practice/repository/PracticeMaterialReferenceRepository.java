package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeMaterialReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeMaterialReferenceRepository
        extends JpaRepository<PracticeMaterialReference, Long> {
    List<PracticeMaterialReference> findByAssetId(Long assetId);
    List<PracticeMaterialReference> findByDraftId(Long draftId);
    List<PracticeMaterialReference> findByPublishedVersionId(Long publishedVersionId);
    boolean existsByAssetIdAndDraftIdAndPlacement(Long assetId, Long draftId, String placement);
    boolean existsByAssetIdAndPublishedVersionIdAndPlacement(
            Long assetId, Long publishedVersionId, String placement);
    boolean existsByAssetIdAndReferenceScope(Long assetId, String referenceScope);
    void deleteByAssetIdAndDraftIdAndPlacement(
            Long assetId, Long draftId, String placement);
}
