-- Migration V18: Add submission ZIP storage path and instructor feedback for ZIP grading support

ALTER TABLE submissions
    ADD COLUMN IF NOT EXISTS zip_storage_path VARCHAR(500) NULL,
    ADD COLUMN IF NOT EXISTS instructor_feedback TEXT NULL;
