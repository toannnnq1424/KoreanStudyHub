package com.ksh.features.practice.repository;

import com.ksh.entities.LecturerAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LecturerAssetRepository extends JpaRepository<LecturerAsset, Long> {
    List<LecturerAsset> findByOwnerLecturerIdAndStatusAndDeletedAtIsNull(Long ownerLecturerId, String status);
    List<LecturerAsset> findBySourceImportSessionId(Long sessionId);
    List<LecturerAsset> findBySourceImportSessionIdAndStatus(Long sessionId, String status);
    Optional<LecturerAsset> findByStorageKey(String storageKey);
    List<LecturerAsset> findByOwnerLecturerIdAndSha256AndStatusAndDeletedAtIsNull(Long ownerLecturerId, String sha256, String status);
    void deleteBySourceImportSessionId(Long sessionId);
}
