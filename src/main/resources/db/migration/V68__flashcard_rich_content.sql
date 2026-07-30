-- V68 — Rich card content without introducing another table.
-- Images are object-storage keys; alternatives are a JSON array of accepted
-- answers/choices. Both remain optional so all existing decks keep working.
ALTER TABLE flashcards
    ADD COLUMN front_image VARCHAR(500) NULL AFTER front_text,
    ADD COLUMN back_image VARCHAR(500) NULL AFTER front_image,
    ADD COLUMN alternatives_json TEXT NULL AFTER back_image;
