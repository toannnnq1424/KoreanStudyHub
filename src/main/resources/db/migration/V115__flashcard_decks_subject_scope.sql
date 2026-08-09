ALTER TABLE flashcard_decks
    ADD COLUMN subject_id BIGINT NULL AFTER owner_id,
    ADD INDEX idx_flashcard_decks_subject_id (subject_id),
    ADD CONSTRAINT fk_flashcard_decks_subject
        FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE SET NULL;
