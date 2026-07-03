package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeDraftAssetUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PracticeDraftAssetUsageRepository extends JpaRepository<PracticeDraftAssetUsage, Long> {
    List<PracticeDraftAssetUsage> findByDraftId(Long draftId);
    List<PracticeDraftAssetUsage> findByAssetId(Long assetId);
    void deleteByDraftId(Long draftId);
    void deleteByDraftIdAndId(Long draftId, Long usageId);
}
