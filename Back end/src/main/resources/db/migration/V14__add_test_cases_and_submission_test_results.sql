-- Migration V14: Add test cases and automated test results tables

CREATE TABLE test_cases (
    id VARCHAR(36) PRIMARY KEY,
    exam_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    input_data LONGTEXT NULL,
    expected_output LONGTEXT NULL,
    points DECIMAL(5,2) NOT NULL DEFAULT 10.00,
    is_hidden TINYINT NOT NULL DEFAULT 0,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_test_cases_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE,
    INDEX idx_test_cases_exam_order (exam_id, display_order)
);

CREATE TABLE submission_test_results (
    id VARCHAR(36) PRIMARY KEY,
    submission_id VARCHAR(36) NOT NULL,
    test_case_id VARCHAR(36) NULL,
    test_case_name VARCHAR(255) NULL,
    is_hidden TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL,
    actual_output LONGTEXT NULL,
    expected_output LONGTEXT NULL,
    execution_time_ms BIGINT NULL,
    exit_code INT NULL,
    error_message TEXT NULL,
    points_awarded DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sub_test_results_submission FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE CASCADE,
    CONSTRAINT fk_sub_test_results_test_case FOREIGN KEY (test_case_id) REFERENCES test_cases(id) ON DELETE SET NULL,
    INDEX idx_sub_test_results_sub (submission_id)
);

ALTER TABLE submissions
    ADD COLUMN auto_score DECIMAL(5,2) NULL,
    ADD COLUMN test_cases_passed INT NULL,
    ADD COLUMN test_cases_total INT NULL;
