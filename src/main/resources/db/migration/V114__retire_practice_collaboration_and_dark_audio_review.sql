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
-- Keep these as separate ALTER statements. MySQL may reorder actions inside a
-- compound ALTER and attempt to remove the supporting index before its FK.
ALTER TABLE practice_sets
    DROP FOREIGN KEY fk_practice_set_locked_by;

-- The composite owner-lock index also happens to support fk_ps_creator on
-- created_by in older schemas. Preserve that live FK with a dedicated index
-- before retiring the lock-specific composite index.
ALTER TABLE practice_sets
    ADD INDEX idx_practice_sets_created_by (created_by);

ALTER TABLE practice_sets
    DROP INDEX idx_practice_set_owner_lock;

ALTER TABLE practice_sets
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
