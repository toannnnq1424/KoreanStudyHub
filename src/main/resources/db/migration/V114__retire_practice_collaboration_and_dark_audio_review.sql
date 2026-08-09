-- Retire two explicitly discontinued Practice capabilities:
--   1. cross-lecturer co-authoring of Practice sets;
--   2. the reviewer-only direct-audio dark-observation experiment.
--
-- The learner/lecturer GLOBAL catalog, owner authoring, immutable versions,
-- Speaking media, consent, STT and AI evaluation remain in service.

-- The reviewer access audit has a foreign key to the dark observation key, so
-- it must be removed before the experimental observation table.
DROP TABLE IF EXISTS practice_speaking_audio_reviewer_access_events;
DROP TABLE IF EXISTS practice_speaking_direct_audio_dark_observations;

DROP TABLE IF EXISTS practice_authoring_collaborations;

-- Set locking existed to let an owner temporarily block collaborator edits.
-- With owner-only mutation it becomes a self-lock with no authorization value.
ALTER TABLE practice_sets
    DROP FOREIGN KEY fk_practice_set_locked_by,
    DROP INDEX idx_practice_set_owner_lock,
    DROP COLUMN locked_at,
    DROP COLUMN locked_by,
    DROP COLUMN owner_locked;

-- Retire the now-unreachable RBAC capability after removing its role grants.
DELETE rp
FROM role_permissions rp
JOIN permissions p ON p.id = rp.permission_id
WHERE p.feature_key = 'practice.lock';

DELETE FROM permissions
WHERE feature_key = 'practice.lock';
