-- ============================================================
--  PG CRM — Migration V16: Support Super Admin Role & Audit Notes
-- ============================================================

-- Add index on users.role for fast Super Admin and RBAC queries
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

-- Ensure audit_logs has index on timestamp and action for Super Admin forensics
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON audit_logs(timestamp);

