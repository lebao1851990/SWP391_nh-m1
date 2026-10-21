-- Migration V13: Add password_changed_at column to users table for session invalidation tracking
-- Note: Created for forward migration; NOT executed directly in audit mode.
ALTER TABLE users
    ADD COLUMN password_changed_at TIMESTAMP NULL;
