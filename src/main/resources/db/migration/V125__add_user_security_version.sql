ALTER TABLE users
    ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0
        COMMENT 'Monotonic version invalidating authenticated principals after credential/access changes';
