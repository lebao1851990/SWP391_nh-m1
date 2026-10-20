-- Migration V12: Add dedicated claim token and lease expiration columns to submissions table
-- Separates worker concurrency lease and fencing metadata from business execution telemetry

ALTER TABLE submissions
    ADD COLUMN claim_token VARCHAR(64) NULL,
    ADD COLUMN lease_expires_at TIMESTAMP NULL,
    ADD INDEX idx_submissions_status_lease (status, lease_expires_at);
