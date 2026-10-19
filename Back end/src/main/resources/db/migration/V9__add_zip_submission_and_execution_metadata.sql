-- Migration V9: Add ZIP submission metadata, execution telemetry, and AST file path tracking

ALTER TABLE submissions
    ADD COLUMN submission_type VARCHAR(20) NOT NULL DEFAULT 'SINGLE_FILE',
    ADD COLUMN original_filename VARCHAR(255) NULL,
    ADD COLUMN archive_hash VARCHAR(64) NULL,
    ADD COLUMN source_tree_hash VARCHAR(64) NULL,
    ADD COLUMN file_count INT NULL,
    ADD COLUMN entry_point_class VARCHAR(255) NULL,
    ADD COLUMN execution_status VARCHAR(50) NULL,
    ADD COLUMN exit_code INT NULL,
    ADD COLUMN compile_output TEXT NULL,
    ADD COLUMN stdout TEXT NULL,
    ADD COLUMN stderr TEXT NULL,
    ADD COLUMN execution_duration_ms BIGINT NULL,
    ADD INDEX idx_submissions_archive_hash (archive_hash),
    ADD INDEX idx_submissions_source_tree_hash (source_tree_hash);

ALTER TABLE ast_fingerprints
    ADD COLUMN file_path VARCHAR(255) NULL;
