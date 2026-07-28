package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeMaterialReference;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PracticeMaterialReferenceRepository
        extends JpaRepository<PracticeMaterialReference, Long> {
    List<PracticeMaterialReference> findByAssetId(Long assetId);
    List<PracticeMaterialReference> findByDraftId(Long draftId);
    List<PracticeMaterialReference> findByDraftIdAndPlacementAndReferenceKey(
            Long draftId, String placement, String referenceKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from PracticeMaterialReference r
            where r.draftId = :draftId
              and r.placement = :placement
              and r.referenceKey = :referenceKey
            order by r.id asc
            """)
    List<PracticeMaterialReference>
            findDraftPlacementAndReferenceKeyForUpdate(
                    @Param("draftId") Long draftId,
                    @Param("placement") String placement,
                    @Param("referenceKey") String referenceKey);
    List<PracticeMaterialReference> findBySetId(Long setId);
    List<PracticeMaterialReference> findByPublishedVersionId(Long publishedVersionId);
    boolean existsByAssetIdAndDraftIdAndPlacement(Long assetId, Long draftId, String placement);
    boolean existsByAssetIdAndDraftIdAndPlacementAndReferenceKey(
            Long assetId, Long draftId, String placement, String referenceKey);
    boolean existsByAssetIdAndPublishedVersionIdAndPlacement(
            Long assetId, Long publishedVersionId, String placement);
    boolean existsByAssetIdAndPublishedVersionId(Long assetId, Long publishedVersionId);
    boolean existsByAssetIdAndReferenceScope(Long assetId, String referenceScope);
    void deleteByAssetIdAndDraftIdAndPlacement(
            Long assetId, Long draftId, String placement);
    void deleteByAssetIdAndDraftIdAndPlacementAndReferenceKey(
            Long assetId, Long draftId, String placement, String referenceKey);
    void deleteByIdAndDraftId(Long id, Long draftId);
}
