-- Stable provenance for class lessons materialized from canonical Library templates.
-- This intentionally adds one nullable column instead of another mapping table.
-- NULL is fail-closed: directly-authored lessons and ambiguous legacy snapshots
-- must never be refreshed merely because their chapter/title happens to match.
ALTER TABLE lessons
    ADD COLUMN source_lesson_template_id BIGINT NULL AFTER created_by,
    ADD INDEX idx_lessons_source_template (source_lesson_template_id),
    ADD CONSTRAINT fk_lessons_source_template
        FOREIGN KEY (source_lesson_template_id)
        REFERENCES lesson_templates(id)
        ON DELETE SET NULL;

-- Deliberately do not backfill legacy snapshots. Their audit text records only
-- a human-readable title, not an immutable template id. Guessing by the current
-- subject/chapter/title could bind a renamed or replacement template and later
-- authorize a destructive refresh of unrelated content. Existing rows remain
-- NULL (fail-closed); newly materialized snapshots persist the exact id in code.
