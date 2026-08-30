package com.ksh.features.lessons.repository;

import com.ksh.entities.Section;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Section}.
 *
 * <p>{@link Section} carries a {@code @SQLRestriction("is_deleted = 0")} so
 * every method below transparently filters out soft-deleted rows.
 */
public interface SectionRepository extends JpaRepository<Section, Long> {

    /** Returns the live sections of a class ordered by {@code display_order}. */
    List<Section> findByClassIdOrderByDisplayOrderAsc(Long classId);

    /**
     * Batch counterpart used by class-target trees. Keeping the parent id in
     * the ordering lets callers group the flat result without issuing one
     * query for every class.
     */
    List<Section> findByClassIdInOrderByClassIdAscDisplayOrderAsc(
            Collection<Long> classIds);

    /** Loads a section scoped by class to harden the URL hierarchy. */
    Optional<Section> findByIdAndClassId(Long id, Long classId);

    /** Locks a section as the stable parent for a lesson reorder operation. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Section s where s.id = :id and s.classId = :classId")
    Optional<Section> findByIdAndClassIdForUpdate(@Param("id") Long id,
                                                  @Param("classId") Long classId);

    /**
     * Returns the highest {@code display_order} currently used for the given
     * class, or {@code -1} when the class has no sections yet. The native
     * query bypasses {@code @SQLRestriction} so soft-deleted rows are not
     * considered — that is intentional: once a position is freed it can be
     * re-used without a numbering conflict.
     */
    @Query(value = "SELECT COALESCE(MAX(display_order), -1) FROM sections "
            + "WHERE class_id = :classId AND is_deleted = 0",
           nativeQuery = true)
    short findMaxDisplayOrder(@Param("classId") Long classId);
}
