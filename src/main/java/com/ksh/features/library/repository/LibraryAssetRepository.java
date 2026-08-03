package com.ksh.features.library.repository;

import com.ksh.entities.LibraryAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;

/**
 * Spring Data repository for {@link LibraryAsset}. Soft-deleted rows are
 * excluded by the entity {@code @SQLRestriction}.
 */
public interface LibraryAssetRepository extends JpaRepository<LibraryAsset, Long> {

    /** Owner-scoped lookup; returns empty for other owners or soft-deleted rows. */
    Optional<LibraryAsset> findByIdAndOwnerId(Long id, Long ownerId);

    /** Serializes reference creation against owner-scoped asset deletion. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a FROM LibraryAsset a
            WHERE a.id = :id AND a.ownerId = :ownerId
            """)
    Optional<LibraryAsset> findByIdAndOwnerIdForUpdate(@Param("id") Long id,
                                                       @Param("ownerId") Long ownerId);

    List<LibraryAsset> findByOwnerIdOrderByTitleAsc(Long ownerId);
}
