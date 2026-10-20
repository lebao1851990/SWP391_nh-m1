-- Migration V15: Add soft delete support to exams table
-- Preserves existing submissions, scores, appeals, and audit logs on deletion

ALTER TABLE exams
    ADD COLUMN deleted_at TIMESTAMP NULL,
    ADD INDEX idx_exams_deleted_at (deleted_at),
    ADD INDEX idx_exams_status_deleted (status, deleted_at);
