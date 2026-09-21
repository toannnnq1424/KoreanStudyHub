-- Anchors are organizational hints, never ownership/cascade parents.
ALTER TABLE lesson_attachments
    ADD COLUMN anchor_section_id BIGINT NULL,
    ADD COLUMN anchor_lesson_id BIGINT NULL;
