-- Phase 13H adds durable ownership fences for out-of-transaction work.
-- Published migrations V1-V56 remain immutable.

ALTER TABLE practice_speaking_media_cleanup_tasks
    ADD COLUMN claim_token VARCHAR(64) NULL AFTER status,
    ADD COLUMN lease_expires_at DATETIME NULL AFTER claim_token,
    DROP CHECK chk_psm_cleanup_status,
    ADD CONSTRAINT chk_psm_cleanup_status
        CHECK (status IN
            ('PENDING','PROCESSING','RETRY','COMPLETED','TERMINAL')),
    ADD INDEX idx_psm_cleanup_status_lease
        (status, lease_expires_at, next_attempt_at, due_at);

ALTER TABLE practice_pdf_import_sessions
    ADD COLUMN generation_claim_token VARCHAR(64) NULL AFTER status,
    ADD COLUMN generation_lease_expires_at DATETIME NULL AFTER generation_claim_token,
    ADD INDEX idx_pdf_session_generation_lease
        (status, generation_lease_expires_at);
