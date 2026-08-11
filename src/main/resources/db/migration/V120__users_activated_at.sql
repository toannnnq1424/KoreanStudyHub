-- admin-user-import-activation
-- Records when an account owner completed activation, independently of
-- is_active (which records whether an administrator permits the account).
-- Neither flag is derived from the other.

SET NAMES utf8mb4;

-- Nullable with no default: the migration neither rewrites existing rows nor
-- needs a backfill. Existing accounts deliberately start NULL and therefore
-- read as PENDING, which is accurate -- an admin set their password directly,
-- so nobody has self-activated. is_active is untouched, so they stay usable.
ALTER TABLE users
    ADD COLUMN activated_at DATETIME NULL AFTER is_active;