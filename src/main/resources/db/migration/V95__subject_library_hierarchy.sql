-- Reuse lesson_templates as the canonical subject -> chapter -> lesson model.
-- Materials continue to use lesson_template_attachments -> library_assets.
ALTER TABLE lesson_templates
    ADD COLUMN subject_id BIGINT NULL AFTER owner_id,
    ADD COLUMN chapter_title VARCHAR(200) NOT NULL DEFAULT 'Chương 1' AFTER title,
    ADD COLUMN chapter_order INT NOT NULL DEFAULT 1 AFTER chapter_title,
    ADD COLUMN display_order INT NOT NULL DEFAULT 0 AFTER chapter_title,
    ADD INDEX idx_lesson_templates_subject_chapter (subject_id, chapter_order, display_order),
    ADD CONSTRAINT fk_lesson_templates_subject FOREIGN KEY (subject_id)
        REFERENCES subjects(id);

UPDATE lesson_templates template_row
JOIN users owner_row ON owner_row.id = template_row.owner_id
SET template_row.subject_id = owner_row.subject_id
WHERE template_row.subject_id IS NULL;
