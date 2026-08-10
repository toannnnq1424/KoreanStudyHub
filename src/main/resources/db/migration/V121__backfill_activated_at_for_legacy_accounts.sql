-- admin-user-import-activation — CRITICAL FIX
--
-- V51 added users.activated_at as nullable with no backfill, on the reasoning
-- that a legacy account "reads as PENDING, which is accurate". That reasoning
-- was wrong once the re-send action shipped: PENDING is what gates
-- ActivationResendService, so every pre-existing account — ADMIN accounts
-- included — could be issued a real activation token, letting whoever holds
-- that mailbox set a new password outside the audited reset flow.
--
-- The property the gate actually means is "this account has no usable
-- password". For accounts that predate the feature those two diverge, so this
-- migration stamps activated_at on the legacy rows to make them agree again.
--
-- WHICH ROWS ARE LEGACY
-- Every account that exists when this migration runs. Accounts created by the
-- roster import cannot exist yet: V51 and V52 apply in the same Flyway run,
-- which completes before Spring Boot serves its first request, and the import
-- is only reachable over HTTP. Ordering alone is therefore sufficient on any
-- existing deployment. The NOT EXISTS clause below costs one indexed lookup
-- and keeps the statement correct even if this file is ever replayed against a
-- database that already ran an import (a rebuilt baseline, a restored dump).
--
-- WHY NOT `is_active = 1` ALONE
-- An account an administrator deliberately deactivated before this feature has
-- a real password and a real login history; it is not awaiting activation
-- either, and stamping only the active rows would leave it stuck reading as
-- PENDING and re-sendable. Soft-deleted rows are stamped for the same reason:
-- restoring one must not resurrect it as PENDING.
--
-- WHAT STAYS NULL
-- Only accounts created by the import path — the ones whose owner genuinely has
-- no password yet. That is the whole point of the column, and the re-send
-- feature keeps working for exactly those rows.
--
-- created_at is used as the stamp rather than NOW(): these owners never
-- performed an activation, so the account's own creation time is the least
-- misleading moment to record. COALESCE guards the rows V1 allowed to have a
-- NULL created_at.

SET NAMES utf8mb4;

UPDATE users u
SET u.activated_at = COALESCE(u.created_at, NOW())
WHERE u.activated_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM user_activities a
    WHERE a.target_user_id = u.id AND a.type = 'IMPORTED'
);