ALTER TABLE practice_ai_provider_profiles
    ADD COLUMN credential_mode VARCHAR(32) NOT NULL DEFAULT 'STATIC_BEARER'
        AFTER base_url,
    MODIFY COLUMN credential_secret VARCHAR(4096) NULL,
    ADD CONSTRAINT chk_practice_ai_profile_credential_mode
        CHECK (credential_mode IN ('STATIC_BEARER', 'GOOGLE_CLOUD_ADC')),
    ADD CONSTRAINT chk_practice_ai_profile_credential_material
        CHECK (
            (credential_mode = 'STATIC_BEARER'
                AND NULLIF(TRIM(credential_secret), '') IS NOT NULL)
            OR
            (credential_mode = 'GOOGLE_CLOUD_ADC'
                AND credential_secret IS NULL)
        );
