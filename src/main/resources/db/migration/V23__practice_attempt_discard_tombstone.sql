ALTER TABLE practice_attempts
    ADD COLUMN discarded_at DATETIME(6) NULL AFTER submitted_at,
    DROP CHECK chk_pa_status,
    ADD CONSTRAINT chk_pa_status
        CHECK (status IN ('IN_PROGRESS','SUBMITTED','GRADED','DISCARDED')),
    ADD CONSTRAINT chk_pa_discarded_at
        CHECK ((status = 'DISCARDED' AND discarded_at IS NOT NULL)
            OR (status <> 'DISCARDED' AND discarded_at IS NULL)),
    ADD INDEX idx_pa_user_status_created_id (user_id, status, created_at, id);

ALTER TABLE practice_speaking_media_cleanup_tasks
    DROP CHECK chk_psm_cleanup_reason,
    ADD CONSTRAINT chk_psm_cleanup_reason
        CHECK (cleanup_reason IN (
            'SUPERSEDED_RETENTION',
            'LOGICAL_DELETE',
            'DISCARD_ATTEMPT',
            'ACTIVATION_COMPENSATION'
        ));
