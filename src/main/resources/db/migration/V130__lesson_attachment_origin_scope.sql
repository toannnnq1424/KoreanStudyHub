-- Distinguish class-private supplementary shares from canonical Library
-- snapshot material without introducing another table.
--
-- Fail-safe backfill:
--   * exact V129 lesson provenance + library-backed attachment => canonical;
--   * direct/legacy/ambiguous rows remain CLASS_PRIVATE.

ALTER TABLE lesson_attachments
    ADD COLUMN origin_scope VARCHAR(24) NOT NULL
        DEFAULT 'CLASS_PRIVATE' AFTER library_asset_id,
    ADD INDEX idx_la_lesson_origin (lesson_id, origin_scope, id),
    ADD INDEX idx_la_lesson_asset_origin
        (lesson_id, library_asset_id, origin_scope),
    ADD CONSTRAINT chk_la_origin_scope
        CHECK (origin_scope IN ('CLASS_PRIVATE', 'CANONICAL_TEMPLATE')),
    ADD CONSTRAINT chk_la_canonical_library
        CHECK (
            origin_scope <> 'CANONICAL_TEMPLATE'
            OR library_asset_id IS NOT NULL
        );

UPDATE lesson_attachments attachment
JOIN lessons lesson ON lesson.id = attachment.lesson_id
SET attachment.origin_scope = 'CANONICAL_TEMPLATE'
WHERE lesson.source_lesson_template_id IS NOT NULL
  AND attachment.library_asset_id IS NOT NULL;
