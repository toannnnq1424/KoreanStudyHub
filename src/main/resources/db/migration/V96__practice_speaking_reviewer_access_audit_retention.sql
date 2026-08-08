-- Forward-only retention identity/deadline for V95 reviewer access events.
-- Existing preproduction rows remain visibly unclassified (NULL) and are not
-- guessed into an approved retention policy. Every current writer requires both.

ALTER TABLE practice_speaking_audio_reviewer_access_events
    ADD COLUMN retention_policy_id VARCHAR(80) NULL AFTER outcome_code,
    ADD COLUMN delete_after DATETIME NULL AFTER occurred_at,
    ADD INDEX idx_psarce_retention (delete_after, id);
