package com.ksh.features.classes;

import com.ksh.entities.AnnouncementComment;
import com.ksh.entities.ClassAnnouncement;
import com.ksh.entities.ClassEntity;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.AnnouncementCommentRepository;
import com.ksh.features.classes.repository.ClassAnnouncementRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.service.ClassAnnouncementService;
import com.ksh.security.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec scenario "One page of announcements issues a fixed number of comment
 * queries" — asserted by <b>observing real query behaviour</b>, not by
 * reflecting on repository method signatures.
 *
 * <p>This replaces the reflective check that previously stood in for this
 * scenario. That check inspected only that both repository methods accept a
 * {@code Collection<Long>}; a regression that called the batched method once per
 * announcement inside a loop satisfies it completely, which is the exact defect
 * the scenario exists to prevent. Its own Javadoc conceded the point by claiming
 * the project had no query-counting harness — it does:
 * {@code SystemSettingsCacheTest} has used Hibernate {@code Statistics} since
 * before this change.
 *
 * <p><b>How the assertion discriminates.</b> The board is rendered twice, at two
 * different page sizes, and the SQL prepare counts are compared. A batched
 * implementation costs the same either way; an N+1 costs strictly more for the
 * larger page. Comparing two runs rather than asserting one absolute number
 * makes the test independent of how many unrelated queries the surrounding call
 * happens to issue, so it needs no maintenance when the service gains a lookup.
 *
 * <p>Lives in its own class because {@code generate_statistics=true} is a
 * context-level property; enabling it inside the main comment test would fork
 * that class's Spring context for no benefit.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class AnnouncementCommentQueryCountTest {

    /** Small page, and a larger one. The gap is what an N+1 would show up in. */
    private static final int FEW_ANNOUNCEMENTS = 2;
    private static final int MANY_ANNOUNCEMENTS = 8;
    private static final int COMMENTS_EACH = 2;

    @Autowired private ClassAnnouncementService announcementService;
    @Autowired private ClassRepository classRepository;
    @Autowired private ClassAnnouncementRepository announcementRepository;
    @Autowired private AnnouncementCommentRepository commentRepository;
    @Autowired private UserRepository userRepository;
    @PersistenceContext private EntityManager em;

    private Statistics stats;
    private User lecturer;

    @BeforeEach
    void setUp() {
        stats = em.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        stats.clear();
        lecturer = userRepository.findByEmailIgnoreCase("lecturer@ksh.edu.vn").orElseThrow();
    }

    @Test
    void comment_query_cost_does_not_grow_with_the_number_of_announcements() {
        ClassEntity small = seedBoard("Cmt-QSmall", "CQC01", FEW_ANNOUNCEMENTS);
        ClassEntity large = seedBoard("Cmt-QLarge", "CQC02", MANY_ANNOUNCEMENTS);

        long smallCost = renderCost(small);
        long largeCost = renderCost(large);

        // Sanity: both pages really did render the announcements they were given,
        // so a zero-cost "nothing was queried" run cannot pass silently.
        assertThat(announcementService.listForClass(
                small.getId(), lecturer.getId(), Role.LECTURER, 0).getTotalElements())
                .isEqualTo(FEW_ANNOUNCEMENTS);
        assertThat(announcementService.listForClass(
                large.getId(), lecturer.getId(), Role.LECTURER, 0).getTotalElements())
                .isEqualTo(MANY_ANNOUNCEMENTS);

        // The discriminating assertion. Per-announcement comment queries would
        // add (MANY - FEW) * 2 = 12 prepares to the larger page. A small slack
        // absorbs incidental per-row work that is not comment-related; it is far
        // below the gap an N+1 produces.
        long slack = 2;
        assertThat(largeCost - smallCost)
                .as("comment queries must be page-scoped: rendering %d announcements "
                                + "must not cost meaningfully more than %d",
                        MANY_ANNOUNCEMENTS, FEW_ANNOUNCEMENTS)
                .isLessThanOrEqualTo(slack);
    }

    /** SQL prepares issued by one full board render. */
    private long renderCost(ClassEntity c) {
        // Clear first: leftover first-level cache entries would make whichever
        // board renders second look artificially cheap.
        em.flush();
        em.clear();
        long before = stats.getPrepareStatementCount();
        announcementService.listForClass(c.getId(), lecturer.getId(), Role.LECTURER, 0);
        return stats.getPrepareStatementCount() - before;
    }

    private ClassEntity seedBoard(String name, String code, int announcements) {
        ClassEntity c = saveClass(name, code);
        for (int i = 0; i < announcements; i++) {
            ClassAnnouncement a = announcementRepository.saveAndFlush(
                    new ClassAnnouncement(c.getId(), "<p>Thông báo " + i + "</p>",
                            lecturer.getId()));
            for (int j = 0; j < COMMENTS_EACH; j++) {
                commentRepository.saveAndFlush(new AnnouncementComment(
                        a.getId(), c.getId(), "Bình luận " + i + "-" + j, lecturer.getId()));
            }
        }
        return c;
    }

    private ClassEntity saveClass(String name, String code) {
        ClassEntity e = new ClassEntity(name, lecturer.getId(), lecturer.getId(),
                null, null, null, 100);
        e.setSubjectId(lecturer.getSubjectId());
        e.approve(lecturer.getId(), java.time.LocalDateTime.now());
        e.setCode(code);
        return classRepository.saveAndFlush(e);
    }
}