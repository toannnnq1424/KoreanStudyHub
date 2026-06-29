-- =============================================================================
-- ksh — V13__increase_invite_code_column_size.sql
-- Increase code column size in class_invite_codes to accommodate 32-char LINK tokens
-- =============================================================================
-- Problem: The code column was VARCHAR(20) but LINK tokens are 32 characters (base64url)
-- Solution: Increase to VARCHAR(40) to safely accommodate both CODE (6 chars) and LINK (32 chars)
-- =============================================================================

ALTER TABLE class_invite_codes
    MODIFY COLUMN code VARCHAR(40) NOT NULL;
