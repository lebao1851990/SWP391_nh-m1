CREATE TABLE scoring_criteria (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    max_score DECIMAL(5,2) NOT NULL
);

CREATE TABLE rule_scores (
    id VARCHAR(36) PRIMARY KEY,
    submission_id VARCHAR(36) NOT NULL,
    criteria_id VARCHAR(36) NOT NULL,
    score DECIMAL(5,2) NOT NULL,
    CONSTRAINT fk_rule_scores_submission FOREIGN KEY (submission_id) REFERENCES submissions(id),
    CONSTRAINT fk_rule_scores_criteria FOREIGN KEY (criteria_id) REFERENCES scoring_criteria(id),
    UNIQUE KEY uk_rule_scores_submission_criteria (submission_id, criteria_id)
);

CREATE TABLE plagiarism_evidence (
    id VARCHAR(36) PRIMARY KEY,
    similarity_matrix_id BIGINT(19) NOT NULL,
    evidence_type VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    submission_a_start_line INT,
    submission_a_end_line INT,
    submission_b_start_line INT,
    submission_b_end_line INT,
    submission_a_location VARCHAR(255),
    submission_b_location VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_evidence_similarity FOREIGN KEY (similarity_matrix_id) REFERENCES similarity_matrix(id),
    INDEX idx_evidence_similarity (similarity_matrix_id)
);
