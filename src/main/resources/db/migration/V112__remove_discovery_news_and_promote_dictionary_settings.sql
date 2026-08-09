-- Korea Discovery News has been retired from the product.
-- Preserve an administrator's existing KRDICT settings before removing the
-- Discovery-only configuration and data model. The shared Dictionary feature
-- continues to use the DICTIONARY settings group and /api/korean-dictionary.

INSERT INTO system_settings (
    setting_key,
    setting_value,
    setting_group,
    description,
    is_encrypted,
    updated_by
)
SELECT
    'dictionary.krdict.api-key',
    legacy.setting_value,
    'DICTIONARY',
    'Korean Basic Dictionary Open API key',
    legacy.is_encrypted,
    legacy.updated_by
FROM system_settings legacy
WHERE legacy.setting_key = 'app.news.dictionary.api-key'
  AND TRIM(legacy.setting_value) <> ''
ON DUPLICATE KEY UPDATE
    setting_group = 'DICTIONARY',
    description = 'Korean Basic Dictionary Open API key',
    is_encrypted = CASE
        WHEN TRIM(system_settings.setting_value) = '' THEN VALUES(is_encrypted)
        ELSE system_settings.is_encrypted
    END,
    updated_by = CASE
        WHEN TRIM(system_settings.setting_value) = '' THEN VALUES(updated_by)
        ELSE system_settings.updated_by
    END,
    setting_value = CASE
        WHEN TRIM(system_settings.setting_value) = '' THEN VALUES(setting_value)
        ELSE system_settings.setting_value
    END;

INSERT INTO system_settings (
    setting_key,
    setting_value,
    setting_group,
    description,
    is_encrypted,
    updated_by
)
SELECT
    'dictionary.krdict.base-url',
    legacy.setting_value,
    'DICTIONARY',
    'Korean Basic Dictionary Open API endpoint',
    legacy.is_encrypted,
    legacy.updated_by
FROM system_settings legacy
WHERE legacy.setting_key = 'app.news.dictionary.base-url'
  AND TRIM(legacy.setting_value) <> ''
ON DUPLICATE KEY UPDATE
    setting_group = 'DICTIONARY',
    description = 'Korean Basic Dictionary Open API endpoint',
    is_encrypted = CASE
        WHEN TRIM(system_settings.setting_value) = '' THEN VALUES(is_encrypted)
        ELSE system_settings.is_encrypted
    END,
    updated_by = CASE
        WHEN TRIM(system_settings.setting_value) = '' THEN VALUES(updated_by)
        ELSE system_settings.updated_by
    END,
    setting_value = CASE
        WHEN TRIM(system_settings.setting_value) = '' THEN VALUES(setting_value)
        ELSE system_settings.setting_value
    END;

DELETE FROM system_settings
WHERE setting_key IN ('app.news.dictionary.api-key', 'app.news.dictionary.base-url');

DELETE FROM system_settings
WHERE setting_group = 'DISCOVERY';

DELETE FROM ai_system_prompts
WHERE name = 'DISCOVERY_NEWS_EDITOR';

DROP TABLE news_vocabularies;
DROP TABLE news_articles;
DROP TABLE news_ingestion_runs;
DROP TABLE news_sources;
