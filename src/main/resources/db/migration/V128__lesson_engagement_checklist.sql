-- Persist the three one-minute lesson engagement checkpoints in the existing
-- learning_progress row. No additional table is required: the existing unique
-- (user_id, lesson_id) key already provides the correct ownership boundary.
ALTER TABLE learning_progress
    ADD COLUMN content_engaged_seconds INT NOT NULL DEFAULT 0 AFTER progress_percent,
    ADD COLUMN video_engaged_seconds INT NOT NULL DEFAULT 0 AFTER content_engaged_seconds,
    ADD COLUMN attachments_engaged_seconds INT NOT NULL DEFAULT 0 AFTER video_engaged_seconds,
    ADD COLUMN active_engagement_tab VARCHAR(20) NULL AFTER attachments_engaged_seconds,
    ADD COLUMN active_engagement_checkpoint_at DATETIME(6) NULL AFTER active_engagement_tab;

-- Preserve legitimate historical completion instead of silently revoking it.
-- New completions can only be produced by the server-timed checklist flow.
UPDATE learning_progress
SET content_engaged_seconds = 60,
    video_engaged_seconds = 60,
    attachments_engaged_seconds = 60
WHERE status = 'COMPLETED';

ALTER TABLE learning_progress
    ADD CONSTRAINT chk_learning_progress_engagement_seconds CHECK (
        content_engaged_seconds BETWEEN 0 AND 60
        AND video_engaged_seconds BETWEEN 0 AND 60
        AND attachments_engaged_seconds BETWEEN 0 AND 60
    ),
    ADD CONSTRAINT chk_learning_progress_active_tab CHECK (
        active_engagement_tab IS NULL
        OR active_engagement_tab IN ('CONTENT', 'VIDEO', 'ATTACHMENTS')
    ),
    ADD CONSTRAINT chk_learning_progress_active_checkpoint CHECK (
        (active_engagement_tab IS NULL AND active_engagement_checkpoint_at IS NULL)
        OR (active_engagement_tab IS NOT NULL
            AND active_engagement_checkpoint_at IS NOT NULL)
    );
