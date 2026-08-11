package com.ksh.features.classes.repository;

import com.ksh.entities.AnnouncementComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Spring Data repository for {@link AnnouncementComment} rows shown under each
 * announcement in the class Board feed.
 *
 * <p><b>Both query methods take a collection of announcement ids, never a single
 * id.</b> That signature is what structurally prevents an N+1: one board page
 * costs exactly two comment queries regardless of how many announcements it
 * holds (design D6). A single-id overload would make the per-announcement call
 * pattern available again, so none is offered.
 *
 * <p>Soft-deleted comments are excluded from both queries by the entity's
 * {@code @SQLRestriction("is_deleted = 0")}, which is why neither method carries
 * a {@code deleted} predicate.
 */
public interface AnnouncementCommentRepository extends JpaRepository<AnnouncementComment, Long> {

    /**
     * Counts live comments per announcement for a whole board page.
     *
     * <p>Returns one row per announcement that has at least one comment;
     * announcements with none are simply absent, so the caller defaults them to
     * zero rather than this query returning a zero row.
     *
     * @param announcementIds ids of every announcement on the page
     * @return rows of {@code [announcementId, count]}
     */
    @Query("""
            SELECT c.announcementId, COUNT(c)
            FROM AnnouncementComment c
            WHERE c.announcementId IN :announcementIds
            GROUP BY c.announcementId
            """)
    List<Object[]> countByAnnouncementIds(
            @Param("announcementIds") Collection<Long> announcementIds);

    /**
     * Loads comments for a whole board page, ordered so each announcement's
     * group arrives <b>newest-first</b>.
     *
     * <p>Selection order and render order are deliberately opposite. The feed
     * shows the newest {@code DEFAULT_COMMENT_PREVIEW_SIZE} comments with the
     * oldest of those at the top, so the service truncates each group here and
     * then reverses it. Ordering oldest-first and truncating would return the
     * three OLDEST comments — the wrong set entirely.
     *
     * <p>The {@code id DESC} tiebreaker keeps the order stable when two comments
     * share a {@code created_at} value, and is covered by the trailing {@code id}
     * column of {@code idx_anncomment_announcement}.
     *
     * @param announcementIds ids of every announcement on the page
     * @return all live comments for those announcements, grouped by announcement
     *         and newest-first within each group
     */
    @Query("""
            SELECT c
            FROM AnnouncementComment c
            WHERE c.announcementId IN :announcementIds
            ORDER BY c.announcementId ASC, c.createdAt DESC, c.id DESC
            """)
    List<AnnouncementComment> findForAnnouncementIds(
            @Param("announcementIds") Collection<Long> announcementIds);
}