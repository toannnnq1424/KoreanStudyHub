-- Add owner-controlled anonymous, read-only links to existing flashcard decks.
-- No new table is introduced: link state lives on the deck row.
ALTER TABLE flashcard_decks
    ADD COLUMN share_token VARCHAR(40) NULL,
    ADD COLUMN is_public TINYINT(1) NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX idx_flashcard_decks_share_token
    ON flashcard_decks (share_token);
