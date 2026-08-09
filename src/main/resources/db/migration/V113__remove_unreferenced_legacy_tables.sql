-- Retire four legacy tables that have no production entity, repository,
-- service, controller, template or runtime query in the current application.
-- Their former dependent activity table was already removed by V96.

-- The permission represented a login-history screen that no longer exists.
-- Both role grants and user overrides are removed by FK ON DELETE CASCADE.
DELETE FROM permissions
WHERE feature_key = 'system.login_history';

DROP TABLE content_versions;
DROP TABLE lesson_contents;
DROP TABLE login_history;
DROP TABLE user_verification_tokens;
