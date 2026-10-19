ALTER TABLE exam_matrix_coordination
    ADD COLUMN failure_count INT NOT NULL DEFAULT 0,
    ADD COLUMN last_error VARCHAR(1000) NULL,
    ADD COLUMN next_retry_at DATETIME NULL;

ALTER TABLE exam_matrix_coordination
    ADD INDEX idx_coord_status_retry (status, next_retry_at);
