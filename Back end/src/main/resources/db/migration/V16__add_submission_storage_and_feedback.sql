-- Migration V16: Add submission ZIP storage path and instructor feedback

ALTER TABLE submissions
    ADD COLUMN zip_storage_path VARCHAR(500) NULL,
    ADD COLUMN instructor_feedback TEXT NULL;
