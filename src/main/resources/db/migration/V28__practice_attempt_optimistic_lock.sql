ALTER TABLE practice_attempts
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;
