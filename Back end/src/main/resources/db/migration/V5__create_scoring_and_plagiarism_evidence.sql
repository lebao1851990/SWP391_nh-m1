CREATE TABLE scoring_criteria (
    id VARCHAR(36) PRIMARY KEY,
    exam_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    max_score DECIMAL(5,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scoring_criteria_exam FOREIGN KEY (exam_id) REFERENCES exams(id)
);

CREATE TABLE rule_scores (
    id VARCHAR(36) PRIMARY KEY,
    submission_id VARCHAR(36) NOT NULL,
    criteria_id VARCHAR(36) NOT NULL,
    score_awarded DECIMAL(5,2) NOT NULL,
    feedback TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rule_scores_submission FOREIGN KEY (submission_id) REFERENCES submissions(id),
    CONSTRAINT fk_rule_scores_criteria FOREIGN KEY (criteria_id) REFERENCES scoring_criteria(id)
);

CREATE TABLE plagiarism_evidence (
    id VARCHAR(36) PRIMARY KEY,
    similarity_matrix_id BIGINT(19) NOT NULL,
    evidence_type VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    submission_a_location VARCHAR(100),
    submission_b_location VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_evidence_similarity FOREIGN KEY (similarity_matrix_id) REFERENCES similarity_matrix(id)
);
