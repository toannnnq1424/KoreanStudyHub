-- Extend the existing Practice AI control plane for separately governed
-- direct-audio evaluation. No provider, model, credential or binding is seeded.

ALTER TABLE practice_ai_purpose_bindings
    ADD COLUMN region_evidence_id VARCHAR(160) NULL AFTER retention_code,
    ADD COLUMN non_training_evidence_id VARCHAR(160) NULL AFTER region_evidence_id,
    ADD COLUMN retention_evidence_id VARCHAR(160) NULL AFTER non_training_evidence_id,
    ADD COLUMN deletion_sla_evidence_id VARCHAR(160) NULL AFTER retention_evidence_id;

UPDATE practice_ai_purpose_bindings
SET capability_json = JSON_SET(capability_json, '$.directAudioInput', FALSE)
WHERE JSON_EXTRACT(capability_json, '$.directAudioInput') IS NULL;

ALTER TABLE practice_ai_purpose_bindings
    DROP CHECK chk_practice_ai_binding_purpose,
    ADD CONSTRAINT chk_practice_ai_binding_purpose CHECK (purpose_code IN (
        'PRACTICE_PDF_AUTHORING',
        'PRACTICE_RL_EXPLANATION',
        'PRACTICE_WRITING_EVALUATION',
        'PRACTICE_SPEAKING_EVALUATION',
        'PRACTICE_SPEAKING_STT',
        'PRACTICE_SPEAKING_TTS',
        'PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION'
    )),
    ADD CONSTRAINT chk_practice_ai_direct_audio_capability CHECK (
        purpose_code <> 'PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION'
        OR JSON_EXTRACT(capability_json, '$.directAudioInput') = TRUE
    ),
    ADD CONSTRAINT chk_practice_ai_direct_audio_policy CHECK (
        purpose_code <> 'PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION'
        OR enabled = FALSE
        OR (
            NULLIF(TRIM(region_evidence_id), '') IS NOT NULL
            AND NULLIF(TRIM(non_training_evidence_id), '') IS NOT NULL
            AND NULLIF(TRIM(retention_evidence_id), '') IS NOT NULL
            AND NULLIF(TRIM(deletion_sla_evidence_id), '') IS NOT NULL
        )
    );

ALTER TABLE practice_ai_execution_audits
    DROP CHECK chk_practice_ai_execution_purpose,
    ADD CONSTRAINT chk_practice_ai_execution_purpose CHECK (purpose_code IN (
        'PRACTICE_PDF_AUTHORING',
        'PRACTICE_RL_EXPLANATION',
        'PRACTICE_WRITING_EVALUATION',
        'PRACTICE_SPEAKING_EVALUATION',
        'PRACTICE_SPEAKING_STT',
        'PRACTICE_SPEAKING_TTS',
        'PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION'
    ));
