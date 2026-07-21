-- =============================================================================
-- KSH - reset only the known development/demo accounts to password "123456".
--
-- Align the KSH demo-login contract without resetting arbitrary real users.
-- BCrypt cost 10 for "123456":
--   $2a$10$QXT/sSbapXXHEWstiZbtT.oyHllsGWSF.E5C..Xl4SkMwYbfi.t5a
-- =============================================================================

UPDATE users
SET password_hash = '$2a$10$QXT/sSbapXXHEWstiZbtT.oyHllsGWSF.E5C..Xl4SkMwYbfi.t5a'
WHERE is_deleted = 0
  AND email IN (
      'admin@ksh.edu.vn',
      'lecturer@ksh.edu.vn',
      'head@ksh.edu.vn',
      'student@ksh.edu.vn',
      'sv01@ksh.edu.vn',
      'sv02@ksh.edu.vn',
      'sv03@ksh.edu.vn',
      'sv04@ksh.edu.vn',
      'sv05@ksh.edu.vn',
      'sv06@ksh.edu.vn',
      'sv07@ksh.edu.vn',
      'sv08@ksh.edu.vn'
  );
