CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    role VARCHAR(20) NOT NULL,
    full_name VARCHAR(255) NULL
);

CREATE TABLE exams (
    id VARCHAR(36) PRIMARY KEY,
    class_id VARCHAR(36) NULL
);

CREATE TABLE submissions (
    id VARCHAR(36) PRIMARY KEY,
    exam_id VARCHAR(36) NOT NULL,
    student_id VARCHAR(36) NOT NULL,
    status VARCHAR(50) NOT NULL,
    CONSTRAINT fk_submissions_exam FOREIGN KEY (exam_id) REFERENCES exams(id),
    CONSTRAINT fk_submissions_student FOREIGN KEY (student_id) REFERENCES users(id)
);

CREATE TABLE ast_fingerprints (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id VARCHAR(36) NOT NULL,
    hash_value BIGINT NOT NULL,
    node_type VARCHAR(64) NOT NULL,
    start_line INT NOT NULL,
    end_line INT NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_ast_fingerprints_submission FOREIGN KEY (submission_id) REFERENCES submissions(id),
    INDEX idx_ast_hash (hash_value),
    INDEX idx_ast_submission_hash (submission_id, hash_value)
);

CREATE TABLE similarity_matrix (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_id VARCHAR(36) NOT NULL,
    submission_a_id VARCHAR(36) NOT NULL,
    submission_b_id VARCHAR(36) NOT NULL,
    similarity_rate DECIMAL(5,2) NOT NULL,
    shared_hashes_count INT NOT NULL,
    is_flagged TINYINT NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_similarity_exam FOREIGN KEY (exam_id) REFERENCES exams(id),
    CONSTRAINT fk_similarity_submission_a FOREIGN KEY (submission_a_id) REFERENCES submissions(id),
    CONSTRAINT fk_similarity_submission_b FOREIGN KEY (submission_b_id) REFERENCES submissions(id),
    UNIQUE KEY uk_similarity_submission_pair (submission_a_id, submission_b_id),
    INDEX idx_similarity_exam_flagged (exam_id, is_flagged)
);

CREATE TABLE plagiarism_appeals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id VARCHAR(36) NOT NULL,
    student_id VARCHAR(36) NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    reviewer_notes TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    CONSTRAINT fk_appeals_submission FOREIGN KEY (submission_id) REFERENCES submissions(id),
    CONSTRAINT fk_appeals_student FOREIGN KEY (student_id) REFERENCES users(id),
    INDEX idx_appeals_status (status),
    INDEX idx_appeals_student (student_id),
    INDEX idx_appeals_submission (submission_id)
);
