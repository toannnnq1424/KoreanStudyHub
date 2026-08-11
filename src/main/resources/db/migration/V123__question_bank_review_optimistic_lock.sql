-- Curator decisions are state-machine transitions. A later concurrent review
-- must fail its stale write instead of silently overwriting the first decision.
ALTER TABLE question_bank_items
    ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0 AFTER id;
