-- Preserve the publication/token ordering used to permanently revoke an
-- anonymous Office-viewer URL after a lesson is unpublished and republished.
-- Comparing persisted values avoids JVM/DB timestamp rounding differences.

ALTER TABLE lessons
    MODIFY published_at DATETIME(6) NULL;

ALTER TABLE public_view_tokens
    MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
