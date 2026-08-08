-- Durable private-audio cleanup after direct-audio consent withdrawal.
-- The withdrawal evidence is metadata only; no audio, URL, storage key copy,
-- provider identifier, secret material or score is added by this migration.

ALTER TABLE practice_speaking_media_cleanup_tasks
    ADD COLUMN authorization_evidence_id VARCHAR(160) NULL AFTER cleanup_reason,
    DROP CHECK chk_psm_cleanup_reason,
    ADD CONSTRAINT chk_psm_cleanup_reason CHECK (cleanup_reason IN (
        'TEMPORARY_EXPIRY', 'SUPERSEDED_RETENTION', 'LOGICAL_DELETE',
        'DISCARD_ATTEMPT', 'ACTIVATION_COMPENSATION', 'MIGRATION_SOURCE_DELETE',
        'CONSENT_WITHDRAWAL'
    )),
    ADD CONSTRAINT chk_psm_cleanup_authorization_evidence CHECK (
        (cleanup_reason = 'CONSENT_WITHDRAWAL'
            AND authorization_evidence_id IS NOT NULL)
        OR
        (cleanup_reason <> 'CONSENT_WITHDRAWAL'
            AND authorization_evidence_id IS NULL)
    );
