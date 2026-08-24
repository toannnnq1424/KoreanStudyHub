-- class-announcement-board — Create the `class_announcements` table backing the
-- class Board tab.
--
-- Why this table exists: the Board tab is the default landing surface for every
-- class on both the lecturer and student side, but it had no persistence at all
-- across the first 119 migrations — only a route and a static placeholder
-- template. Lecturers consequently had no way to broadcast a message to a class:
-- notifications are system-generated only and messaging is 1-on-1.
--
-- Why a dedicated table rather than a `type = 'ANNOUNCEMENT'` discriminator on
-- `activity_classes` (design D1): the audit table is append-only, has no
-- `updated_at` and no `is_deleted` column. Announcements need editing and soft
-- deletion, so overloading the audit trail would break its immutability contract
-- and force nullable columns onto every audit row.
--
-- Schema notes:
--   * `content LONGTEXT` — sanitized rich-text HTML produced by Quill, cleaned
--     through HtmlSanitizer before persistence (design D9), so the read path can
--     render it with `th:utext` without re-sanitizing.
--   * `is_deleted TINYINT(1)` — soft delete, matching the project-wide
--     convention. The entity carries @SQLRestriction("is_deleted = 0") so
--     deleted rows disappear from both feeds while the row is retained.
--   * `updated_at ... ON UPDATE CURRENT_TIMESTAMP` — advanced server-side by
--     MySQL on any UPDATE, mirroring `classes` (V1) so JPA never writes it.
--   * `idx_announcement_class (class_id, is_deleted, created_at)` — covers the
--     only listing query shape: newest-first feed for one class (design D10).
--   * No `status` column: publication is immediate, so a draft state would be
--     dead schema (design D5).
--
-- `fk_announcement_class` cascades because an announcement has no meaning
-- without its class. `fk_announcement_creator` deliberately omits ON DELETE so a
-- user row cannot be removed while it still owns authored content.

CREATE TABLE class_announcements (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     class_id BIGINT NOT NULL,
                                     content LONGTEXT NOT NULL,
                                     created_by BIGINT NOT NULL,
                                     created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                                     updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                     is_deleted TINYINT(1) NOT NULL DEFAULT 0,
                                     INDEX idx_announcement_class (class_id, is_deleted, created_at),
                                     CONSTRAINT fk_announcement_class FOREIGN KEY (class_id)
                                         REFERENCES classes(id) ON DELETE CASCADE,
                                     CONSTRAINT fk_announcement_creator FOREIGN KEY (created_by)
                                         REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;