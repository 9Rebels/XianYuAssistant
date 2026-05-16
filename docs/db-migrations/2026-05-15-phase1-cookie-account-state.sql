-- Phase 1: Cookie / account state convergence.
-- SQLite gray migration. Run only after backing up dbdata/xianyu_assistant.db.
-- This script is additive and keeps existing status/cookie_status codes compatible.

PRAGMA foreign_keys = OFF;
BEGIN IMMEDIATE;

ALTER TABLE xianyu_account ADD COLUMN state_reason TEXT;
ALTER TABLE xianyu_account ADD COLUMN state_updated_time DATETIME;

ALTER TABLE xianyu_cookie ADD COLUMN state_reason TEXT;
ALTER TABLE xianyu_cookie ADD COLUMN state_updated_time DATETIME;

UPDATE xianyu_account
SET state_updated_time = COALESCE(updated_time, datetime('now', 'localtime'))
WHERE state_updated_time IS NULL;

UPDATE xianyu_cookie
SET state_updated_time = COALESCE(updated_time, datetime('now', 'localtime'))
WHERE state_updated_time IS NULL;

CREATE INDEX IF NOT EXISTS idx_account_status ON xianyu_account(status);
CREATE INDEX IF NOT EXISTS idx_account_state_updated_time ON xianyu_account(state_updated_time);
CREATE INDEX IF NOT EXISTS idx_cookie_account_id ON xianyu_cookie(xianyu_account_id);
CREATE INDEX IF NOT EXISTS idx_cookie_status ON xianyu_cookie(cookie_status);
CREATE INDEX IF NOT EXISTS idx_cookie_state_updated_time ON xianyu_cookie(state_updated_time);

COMMIT;
PRAGMA foreign_keys = ON;

-- Rollback note for SQLite:
-- SQLite can only drop columns on newer versions. Prefer restoring the pre-migration
-- database backup if this migration must be rolled back.
