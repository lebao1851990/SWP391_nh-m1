ALTER TABLE similarity_matrix
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD INDEX idx_similarity_exam_active (exam_id, is_active);

CREATE TABLE exam_matrix_coordination (
    exam_id VARCHAR(36) NOT NULL PRIMARY KEY,
    current_generation BIGINT NOT NULL DEFAULT 0,
    requested_generation BIGINT NOT NULL DEFAULT 0,
    locked_generation BIGINT NOT NULL DEFAULT 0,
    lease_token VARCHAR(36) NULL,
    worker_id VARCHAR(100) NULL,
    lease_expires_at DATETIME NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'IDLE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_coord_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE,
    INDEX idx_coord_status_lease (status, lease_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO exam_matrix_coordination (exam_id, current_generation, requested_generation, locked_generation, status)
SELECT id, 0, 0, 0, 'IDLE' FROM exams
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;
