-- PRE-14: lecturer-selected, immutable Reading/Listening explanation strategy
-- and append-only editorial approval authority.

ALTER TABLE practice_questions
    ADD COLUMN explanation_strategy_registry_version VARCHAR(64) NULL
        AFTER explanation,
    ADD COLUMN explanation_strategy_code VARCHAR(64) NULL
        AFTER explanation_strategy_registry_version,
    ADD COLUMN explanation_strategy_version VARCHAR(32) NULL
        AFTER explanation_strategy_code;

ALTER TABLE practice_question_versions
    ADD COLUMN explanation_strategy_registry_version VARCHAR(64) NULL
        AFTER explanation,
    ADD COLUMN explanation_strategy_code VARCHAR(64) NULL
        AFTER explanation_strategy_registry_version,
    ADD COLUMN explanation_strategy_version VARCHAR(32) NULL
        AFTER explanation_strategy_code;

CREATE TABLE practice_explanation_editorial_revisions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    draft_id BIGINT NOT NULL,
    question_client_id VARCHAR(80) NOT NULL,
    revision_no INT NOT NULL,
    strategy_registry_version VARCHAR(64) NOT NULL,
    strategy_code VARCHAR(64) NOT NULL,
    strategy_version VARCHAR(32) NOT NULL,
    authority_fingerprint CHAR(64) NOT NULL,
    editorial_state VARCHAR(24) NOT NULL,
    explanation_json JSON NOT NULL,
    created_by BIGINT NOT NULL,
    approved_by BIGINT NULL,
    approved_at DATETIME NULL,
    invalidated_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_practice_explanation_editorial_revision
        (draft_id, question_client_id, revision_no),
    KEY idx_practice_explanation_editorial_current
        (draft_id, question_client_id, editorial_state, revision_no),
    KEY idx_practice_explanation_editorial_fingerprint
        (authority_fingerprint),
    CONSTRAINT fk_practice_explanation_editorial_draft
        FOREIGN KEY (draft_id) REFERENCES practice_drafts(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_practice_explanation_editorial_created_by
        FOREIGN KEY (created_by) REFERENCES users(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_practice_explanation_editorial_approved_by
        FOREIGN KEY (approved_by) REFERENCES users(id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_practice_explanation_editorial_state
        CHECK (editorial_state IN
            ('GENERATED_DRAFT', 'APPROVED', 'INVALIDATED')),
    CONSTRAINT ck_practice_explanation_editorial_approval
        CHECK (
            (editorial_state = 'APPROVED'
                AND approved_by IS NOT NULL
                AND approved_at IS NOT NULL
                AND invalidated_at IS NULL)
            OR
            (editorial_state = 'GENERATED_DRAFT'
                AND approved_by IS NULL
                AND approved_at IS NULL
                AND invalidated_at IS NULL)
            OR
            (editorial_state = 'INVALIDATED'
                AND invalidated_at IS NOT NULL)
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
