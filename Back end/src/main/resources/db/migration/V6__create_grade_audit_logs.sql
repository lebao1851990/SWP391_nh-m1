CREATE TABLE grade_audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    submission_id VARCHAR(36) NOT NULL,
    actor_id VARCHAR(36) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    pre_rubric_score DECIMAL(5,2),
    post_rubric_score DECIMAL(5,2),
    pre_override_score DECIMAL(5,2),
    post_override_score DECIMAL(5,2),
    pre_effective_score DECIMAL(5,2),
    post_effective_score DECIMAL(5,2),
    rationale TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_grade_audit_submission FOREIGN KEY (submission_id) REFERENCES submissions(id),
    CONSTRAINT fk_grade_audit_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    INDEX idx_grade_audit_submission (submission_id),
    INDEX idx_grade_audit_created_at (created_at)
);
