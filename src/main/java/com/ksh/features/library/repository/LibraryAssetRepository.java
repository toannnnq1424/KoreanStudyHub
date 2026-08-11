package com.ksh.features.library.repository;

import com.ksh.entities.LibraryAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

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

    /**
     * Lists only the authenticated owner's live assets. There is deliberately
     * no unscoped picker query: shared lesson templates and personal file
     * inventory are separate concepts.
     */
    @Query("""
            SELECT a FROM LibraryAsset a
            WHERE a.ownerId = :ownerId
              AND (:kind IS NULL OR a.kind = :kind)
              AND (
                    :q IS NULL
                    OR LOWER(a.title) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(a.originalFilename) LIKE LOWER(CONCAT('%', :q, '%'))
                  )
            ORDER BY a.updatedAt DESC, a.id DESC
            """)
    Page<LibraryAsset> searchOwned(@Param("ownerId") Long ownerId,
                                   @Param("q") String q,
                                   @Param("kind") String kind,
                                   Pageable pageable);

    long countByOwnerId(Long ownerId);

    long countByOwnerIdAndKind(Long ownerId, String kind);

    @Query(value = """
            SELECT COUNT(*) FROM lesson_attachments
            WHERE library_asset_id = :assetId
            """, nativeQuery = true)
    long countLessonAttachmentReferences(@Param("assetId") Long assetId);

    @Query(value = """
            SELECT COUNT(*) FROM lessons
            WHERE video_library_asset_id = :assetId
            """, nativeQuery = true)
    long countLessonVideoReferences(@Param("assetId") Long assetId);

    @Query(value = """
            SELECT COUNT(*) FROM lesson_template_attachments
            WHERE library_asset_id = :assetId
            """, nativeQuery = true)
    long countTemplateAttachmentReferences(@Param("assetId") Long assetId);

    @Query(value = """
            SELECT COUNT(*) FROM lesson_templates
            WHERE pdf_library_asset_id = :assetId
            """, nativeQuery = true)
    long countTemplatePdfReferences(@Param("assetId") Long assetId);

    @Query(value = """
            SELECT COUNT(*) FROM lesson_templates
            WHERE video_library_asset_id = :assetId
            """, nativeQuery = true)
    long countTemplateVideoReferences(@Param("assetId") Long assetId);

    List<LibraryAsset> findByOwnerIdOrderByTitleAsc(Long ownerId);
}
