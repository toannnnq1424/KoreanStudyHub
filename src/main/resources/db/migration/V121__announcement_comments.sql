-- announcement-images-and-comments — Create the `announcement_comments` table
-- backing member-authored comments on class board announcements.
--
-- Why this table exists: the board shipped in V120 as a one-way feed. A student
-- who does not understand a post has nowhere to ask — notifications are
-- system-generated and messaging is 1-on-1 with the lecturer, so a question and
-- its answer never reach the rest of the class. Comments close that gap and are
-- the first write path a ROLE_STUDENT caller may execute on class-owned content.
--
-- Why a dedicated table rather than a discriminator on `class_announcements`
-- (design D1): a comment differs from an announcement in author population
-- (students may write comments, not announcements), content model (plain text vs
-- sanitized rich text), and lifecycle. Overloading one table would force
-- nullable columns onto every announcement row and turn "is this row a post or a
-- reply" into a runtime concern rather than a schema one. A self-referencing
-- `parent_announcement_id` was also rejected: it invites accidental recursion
-- and would silently include comments in the feed query unless every read added
-- a `parent IS NULL` filter.
--
-- Why `class_id` is duplicated here even though it is reachable through
-- `announcement_id -> class_announcements.class_id`: authorization and the
-- class-scope check that blocks path-variable enumeration (POSTing class C's URL
-- with an announcement belonging to class D) both need the owning class on every
-- comment read and write. Carrying it denormalized keeps those checks a single
-- indexed lookup with no join, and the FK below guarantees it stays valid.
--
-- Schema notes:
--   * `content TEXT` — plain text stored verbatim, NOT passed through
--     HtmlSanitizer. Comments render with `th:text`, never `th:utext`, so markup
--     can never reach the DOM as markup (design D2). Storing pre-escaped text
--     would double-escape at render.
--   * `is_deleted TINYINT(1)` — soft delete, matching the project-wide
--     convention. The entity carries @SQLRestriction("is_deleted = 0") so
--     deleted comments disappear from both feeds and from the count while the
--     row is retained.
--   * `updated_at ... ON UPDATE CURRENT_TIMESTAMP` — advanced server-side by
--     MySQL, mirroring `class_announcements` (V120) so JPA never writes it.
--   * `idx_anncomment_announcement (announcement_id, is_deleted, created_at, id)`
--     — covers both page-wide queries: the grouped count and the newest-first
--     preview including its `id DESC` tiebreaker (design D6).
--
-- `fk_anncomment_announcement` and `fk_anncomment_class` cascade because a
-- comment has no meaning without its announcement or its class.
-- `fk_anncomment_author` deliberately omits ON DELETE so a user row cannot be
-- removed while it still owns authored content, matching V120.

CREATE TABLE announcement_comments (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       announcement_id BIGINT NOT NULL,
                                       class_id BIGINT NOT NULL,
                                       content TEXT NOT NULL,
                                       created_by BIGINT NOT NULL,
                                       created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                       is_deleted TINYINT(1) NOT NULL DEFAULT 0,
                                       INDEX idx_anncomment_announcement (announcement_id, is_deleted, created_at, id),
                                       CONSTRAINT fk_anncomment_announcement FOREIGN KEY (announcement_id)
                                           REFERENCES class_announcements(id) ON DELETE CASCADE,
                                       CONSTRAINT fk_anncomment_class FOREIGN KEY (class_id)
                                           REFERENCES classes(id) ON DELETE CASCADE,
                                       CONSTRAINT fk_anncomment_author FOREIGN KEY (created_by)
                                           REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;