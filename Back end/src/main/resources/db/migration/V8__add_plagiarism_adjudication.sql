ALTER TABLE similarity_matrix
    ADD COLUMN adjudication_status VARCHAR(30) DEFAULT 'PENDING' NOT NULL,
    ADD COLUMN adjudication_notes TEXT NULL,
    ADD COLUMN adjudicated_by_id VARCHAR(36) NULL,
    ADD COLUMN adjudicated_at DATETIME NULL,
    ADD CONSTRAINT fk_similarity_adjudicated_by FOREIGN KEY (adjudicated_by_id) REFERENCES users(id),
    ADD INDEX idx_similarity_adjudication_status (adjudication_status);
