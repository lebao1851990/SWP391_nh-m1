-- Migration V19: Expand schema for multi-question architecture
-- Creates exam_questions, student_question_scores, and adds nullable question_id to child tables.

CREATE TABLE IF NOT EXISTS exam_questions (
    id VARCHAR(36) PRIMARY KEY,
    exam_id VARCHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description LONGTEXT NULL,
    max_score DECIMAL(5,2) NOT NULL DEFAULT 10.00,
    display_order INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_exam_questions_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE,
    INDEX idx_exam_questions_exam_order (exam_id, display_order)
);

CREATE TABLE IF NOT EXISTS student_question_scores (
    id VARCHAR(36) PRIMARY KEY,
    exam_id VARCHAR(36) NOT NULL,
    question_id VARCHAR(36) NOT NULL,
    student_id VARCHAR(36) NOT NULL,
    latest_submission_id VARCHAR(36) NULL,
    latest_submission_seq INT NOT NULL DEFAULT 1,
    latest_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submission_count INT NOT NULL DEFAULT 1,
    effective_score DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    auto_score DECIMAL(5,2) NULL,
    override_score DECIMAL(5,2) NULL,
    override_reason TEXT NULL,
    overridden_by VARCHAR(36) NULL,
    overridden_at TIMESTAMP NULL,
    last_submitted_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_sqs_question_student UNIQUE (question_id, student_id),
    CONSTRAINT fk_sqs_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE,
    CONSTRAINT fk_sqs_question FOREIGN KEY (question_id) REFERENCES exam_questions(id) ON DELETE CASCADE,
    CONSTRAINT fk_sqs_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_sqs_exam_student (exam_id, student_id)
);

-- Expand test_cases with nullable question_id
ALTER TABLE test_cases
    ADD COLUMN question_id VARCHAR(36) NULL;

-- Expand submissions with nullable question_id
ALTER TABLE submissions
    ADD COLUMN question_id VARCHAR(36) NULL;

-- Expand similarity_matrix with nullable question_id
ALTER TABLE similarity_matrix
    ADD COLUMN question_id VARCHAR(36) NULL;

