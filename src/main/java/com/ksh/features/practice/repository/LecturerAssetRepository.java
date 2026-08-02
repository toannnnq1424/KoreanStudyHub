package com.ksh.features.practice.repository;

import com.ksh.entities.LecturerAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface LecturerAssetRepository extends JpaRepository<LecturerAsset, Long> {
    List<LecturerAsset> findByOwnerLecturerIdAndStatusAndDeletedAtIsNull(Long ownerLecturerId, String status);
    List<LecturerAsset> findByOwnerLecturerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
            Long ownerLecturerId, Pageable pageable);
    Optional<LecturerAsset> findByIdAndOwnerLecturerId(Long id, Long ownerLecturerId);
    List<LecturerAsset> findByOwnerLecturerIdAndSha256AndStatusAndDeletedAtIsNull(Long ownerLecturerId, String sha256, String status);
    @Query("""
            select a.id from LecturerAsset a
            where a.status = 'TEMPORARY'
              and a.visibility = 'PRIVATE'
              and a.sourceType in ('MANUAL_UPLOAD', 'AI_TTS')
              and a.deletedAt is null
              and a.retentionUntil is not null
              and a.retentionUntil <= :now
            order by a.id asc
            """)
    List<Long> findExpiredUnboundAssetIds(
            @Param("now") LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LecturerAsset a where a.id = :id")
    Optional<LecturerAsset> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from LecturerAsset a
            where a.storageProfileCode is null
              and a.storageKey = :storageKey
            order by a.id asc
            """)
    List<LecturerAsset> findByStorageKeyForUpdate(
            @Param("storageKey") String storageKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from LecturerAsset a
            where a.storageProfileCode = :profileCode
              and a.storageKey = :storageKey
            order by a.id asc
            """)
    List<LecturerAsset> findByStorageProfileCodeAndStorageKeyForUpdate(
            @Param("profileCode") String profileCode,
            @Param("storageKey") String storageKey);
}
