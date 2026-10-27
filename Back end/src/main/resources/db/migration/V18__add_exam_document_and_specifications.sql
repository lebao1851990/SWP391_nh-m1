-- =============================================================================
-- Migration V18: Add exam problem specifications, document source, and rubric association
-- Enables full persistence of AI-generated exam problem statements, input/output
-- specifications, constraints, sample I/O, and dedicated exam-level rubrics.
-- =============================================================================

ALTER TABLE exams
    ADD COLUMN problem_statement LONGTEXT NULL,
    ADD COLUMN input_specification LONGTEXT NULL,
    ADD COLUMN output_specification LONGTEXT NULL,
    ADD COLUMN constraints_text LONGTEXT NULL,
    ADD COLUMN sample_input LONGTEXT NULL,
    ADD COLUMN sample_output LONGTEXT NULL,
    ADD COLUMN programming_language VARCHAR(50) NULL,
    ADD COLUMN source_document_name VARCHAR(255) NULL;

-- Create exam-specific rubric criteria link table
CREATE TABLE exam_rubric_criteria (
    id VARCHAR(36) PRIMARY KEY,
    exam_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    max_score DECIMAL(5,2) NOT NULL DEFAULT 2.50,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_exam_rubric_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE,
    INDEX idx_exam_rubric_exam (exam_id)
);
