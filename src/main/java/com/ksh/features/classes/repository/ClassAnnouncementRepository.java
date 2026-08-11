package com.ksh.features.classes.repository;

import com.ksh.entities.ClassAnnouncement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link ClassAnnouncement} rows backing the class
 * Board tab feed.
 */
public interface ClassAnnouncementRepository extends JpaRepository<ClassAnnouncement, Long> {

    /**
     * Returns one page of a class's announcements, newest first.
     *
     * <p>The {@code id DESC} tie-breaker keeps the order stable when two
     * announcements share a {@code created_at} value — a timestamp-only sort can
     * duplicate or drop rows across page boundaries (design D10).
     *
     * <p>Soft-deleted rows are excluded by the entity's
     * {@code @SQLRestriction("is_deleted = 0")}, which is why the method name
     * carries no {@code AndDeletedFalse} clause. The resulting
     * {@code (class_id, is_deleted, created_at)} predicate is covered by
     * {@code idx_announcement_class} from migration V120.
     *
     * @param classId  id of the class whose feed is being read
     * @param pageable page request carrying the zero-based index and page size
     * @return a page of announcements ordered newest-first
     */
    Page<ClassAnnouncement> findByClassIdOrderByCreatedAtDescIdDesc(Long classId, Pageable pageable);
}