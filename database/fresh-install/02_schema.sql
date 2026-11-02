-- =============================================================================
-- CODEASSESS — COMPLETE DDL SCHEMA DEFINITION
-- File: 02_schema.sql
-- Description: Complete schema for fresh installation of CodeAssess.
-- Architecture: 17 Physical Tables (16 JPA Entities + 1 Join Table exam_classes) + Optional Flyway History Table.
-- Compatibility: MySQL 8.0, 8.4 LTS, 9.x (InnoDB engine, utf8mb4)
-- Synchronized with: Flyway Migrations V1 through V16 & 16 JPA Entity Classes
-- Safety: Non-destructive. Uses CREATE TABLE IF NOT EXISTS.
-- Table Dependency Order:
--   1. users
--   2. password_reset_tokens (FK -> users)
--   3. classes (FK -> users)
--   4. class_students (FK -> classes, users)
--   5. exams (FK -> classes)
--   6. exam_classes (FK -> exams, classes)
--   7. test_cases (FK -> exams)
--   8. submissions (FK -> exams, users)
--   9. submission_test_results (FK -> submissions, test_cases)
--   10. ast_fingerprints (FK -> submissions)
--   11. similarity_matrix (FK -> exams, submissions, users)
--   12. plagiarism_evidence (FK -> similarity_matrix)
--   13. scoring_criteria
--   14. rule_scores (FK -> submissions, scoring_criteria)
--   15. grade_audit_logs (FK -> submissions, users)
--   16. plagiarism_appeals (FK -> submissions, users)
--   17. exam_matrix_coordination (FK -> exams)
-- =============================================================================

USE codeassess;

-- Disable foreign key checks during batch schema creation to prevent ordering race conditions
SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 1. TABLE: users
-- Purpose: User identity, roles (ADMIN, TEACHER, STUDENT), authentication hashes.
-- Mapped Entity: com.codeassess.user.entity.User
-- Sources: V1__initial_schema.sql, V1.1__extend_users_schema.sql
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    full_name VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'TEACHER', 'STUDENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 2. TABLE: password_reset_tokens
-- Purpose: Secure password reset tokens and expiration tracking.
-- Mapped Entity: com.codeassess.auth.entity.PasswordResetToken
-- Sources: V13__create_password_reset_tokens.sql
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id VARCHAR(36) NOT NULL,
    token VARCHAR(128) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    expires_at DATETIME NOT NULL,
    used TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uk_pwd_reset_token UNIQUE (token),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_pwd_reset_token (token),
    INDEX idx_pwd_reset_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 3. TABLE: classes
-- Purpose: Course sections / academic classes managed by teachers.
-- Mapped Entity: com.codeassess.classes.entity.CourseClass
-- Sources: V2__create_classes_and_extend_exams.sql
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS classes (
    id VARCHAR(36) NOT NULL,
    teacher_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(100) NOT NULL,
    semester VARCHAR(100) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_classes PRIMARY KEY (id),
    CONSTRAINT uk_classes_code UNIQUE (code),
    CONSTRAINT fk_classes_teacher FOREIGN KEY (teacher_id) REFERENCES users (id),
    INDEX idx_classes_teacher (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 4. TABLE: class_students
-- Purpose: Many-to-Many enrollment roster between classes and students.
-- Mapped Entity: com.codeassess.classes.entity.ClassStudent
-- Sources: V2__create_classes_and_extend_exams.sql
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS class_students (
    class_id VARCHAR(36) NOT NULL,
    student_id VARCHAR(36) NOT NULL,
    CONSTRAINT pk_class_students PRIMARY KEY (class_id, student_id),
    CONSTRAINT fk_class_students_class FOREIGN KEY (class_id) REFERENCES classes (id),
    CONSTRAINT fk_class_students_student FOREIGN KEY (student_id) REFERENCES users (id),
    INDEX idx_class_students_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 5. TABLE: exams
-- Purpose: Programming assessments, quizzes, assignments, and exams.
-- Mapped Entity: com.codeassess.exam.entity.Exam
-- Sources: V1, V2, V15__add_exam_soft_delete_and_time_tracking.sql
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exams (
    id VARCHAR(36) NOT NULL,
    class_id VARCHAR(36) NULL,
    title VARCHAR(255) NULL,
    exam_date DATETIME NULL,
    duration INT NULL,
    status VARCHAR(50) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT pk_exams PRIMARY KEY (id),
    CONSTRAINT fk_exams_class FOREIGN KEY (class_id) REFERENCES classes (id),
    INDEX idx_exams_class (class_id),
    INDEX idx_exams_deleted_at (deleted_at),
    INDEX idx_exams_status_deleted (status, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 6. TABLE: exam_classes
-- Purpose: Multi-class exam assignment (class-aware exams spanning multiple classes).
-- Mapped Entity: com.codeassess.exam.entity.Exam (classes @JoinTable)
-- Sources: V7__create_exam_classes.sql
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exam_classes (
    exam_id VARCHAR(36) NOT NULL,
    class_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_exam_classes PRIMARY KEY (exam_id, class_id),
    CONSTRAINT fk_exam_classes_exam FOREIGN KEY (exam_id) REFERENCES exams (id) ON DELETE CASCADE,
    CONSTRAINT fk_exam_classes_class FOREIGN KEY (class_id) REFERENCES classes (id) ON DELETE RESTRICT,
    INDEX idx_exam_classes_exam (exam_id),
    INDEX idx_exam_classes_class (class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 7. TABLE: test_cases
-- Purpose: Automated evaluation test suites, expected I/O, points, visibility flags.
-- Mapped Entity: com.codeassess.testcase.entity.TestCase
-- Sources: V14__add_test_cases_and_submission_test_results.sql
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS test_cases (
    id VARCHAR(36) NOT NULL,
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
    CONSTRAINT pk_test_cases PRIMARY KEY (id),
    CONSTRAINT fk_test_cases_exam FOREIGN KEY (exam_id) REFERENCES exams (id) ON DELETE CASCADE,
    INDEX idx_test_cases_exam_order (exam_id, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 8. TABLE: submissions
-- Purpose: Student code submissions, grading telemetry, sandboxed run metadata,
--          scores, claim tokens, and worker concurrency leases.
-- Mapped Entity: com.codeassess.submission.entity.Submission
-- Sources: V1, V3, V9, V12, V14, V16
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS submissions (
    id VARCHAR(36) NOT NULL,
    exam_id VARCHAR(36) NOT NULL,
    student_id VARCHAR(36) NOT NULL,
    status VARCHAR(50) NOT NULL,
    source_code LONGTEXT NULL,
    total_score DECIMAL(5,2) NULL,
    override_score DECIMAL(5,2) NULL,
    auto_score DECIMAL(5,2) NULL,
    test_cases_passed INT NULL,
    test_cases_total INT NULL,
    evaluated_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    submission_type VARCHAR(20) NOT NULL DEFAULT 'SINGLE_FILE',
    original_filename VARCHAR(255) NULL,
    archive_hash VARCHAR(64) NULL,
    source_tree_hash VARCHAR(64) NULL,
    file_count INT NULL,
    entry_point_class VARCHAR(255) NULL,
    execution_status VARCHAR(50) NULL,
    exit_code INT NULL,
    compile_output TEXT NULL,
    stdout TEXT NULL,
    stderr TEXT NULL,
    execution_duration_ms BIGINT NULL,
    claim_token VARCHAR(64) NULL,
    lease_expires_at TIMESTAMP NULL,
    zip_storage_path VARCHAR(500) NULL,
    instructor_feedback TEXT NULL,
    CONSTRAINT pk_submissions PRIMARY KEY (id),
    CONSTRAINT fk_submissions_exam FOREIGN KEY (exam_id) REFERENCES exams (id),
    CONSTRAINT fk_submissions_student FOREIGN KEY (student_id) REFERENCES users (id),
    INDEX idx_submissions_exam (exam_id),
    INDEX idx_submissions_student (student_id),
    INDEX idx_submissions_archive_hash (archive_hash),
    INDEX idx_submissions_source_tree_hash (source_tree_hash),
    INDEX idx_submissions_status_lease (status, lease_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 9. TABLE: submission_test_results
-- Purpose: Granular outcomes per test case execution inside the sandbox.
-- Mapped Entity: com.codeassess.testcase.entity.SubmissionTestResult
-- Sources: V14__add_test_cases_and_submission_test_results.sql
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS submission_test_results (
    id VARCHAR(36) NOT NULL,
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
    CONSTRAINT pk_submission_test_results PRIMARY KEY (id),
    CONSTRAINT fk_sub_test_results_submission FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE,
    CONSTRAINT fk_sub_test_results_test_case FOREIGN KEY (test_case_id) REFERENCES test_cases (id) ON DELETE SET NULL,
    INDEX idx_sub_test_results_sub (submission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 10. TABLE: ast_fingerprints
-- Purpose: Abstract Syntax Tree token hashes, node types, code line coordinates,
--          and multi-file relative paths for winnowing plagiarism detection.
-- Mapped Entity: com.codeassess.plagiarism.entity.AstFingerprint
-- Sources: V1__initial_schema.sql, V9__add_zip_submission_and_execution_metadata.sql
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ast_fingerprints (
    id BIGINT AUTO_INCREMENT NOT NULL,
    submission_id VARCHAR(36) NOT NULL,
    hash_value BIGINT NOT NULL,
    node_type VARCHAR(64) NOT NULL,
    start_line INT NOT NULL,
    end_line INT NOT NULL,
    file_path VARCHAR(255) NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT pk_ast_fingerprints PRIMARY KEY (id),
    CONSTRAINT fk_ast_fingerprints_submission FOREIGN KEY (submission_id) REFERENCES submissions (id),
    INDEX idx_ast_hash (hash_value),
    INDEX idx_ast_submission_hash (submission_id, hash_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 11. TABLE: similarity_matrix
-- Purpose: Pairwise similarity rates, shared AST hash counts, flagged status,
--          instructor adjudication decisions, and active state flags.
-- Mapped Entity: com.codeassess.plagiarism.entity.SimilarityPair
-- Sources: V1, V8, V10
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS similarity_matrix (
    id BIGINT AUTO_INCREMENT NOT NULL,
    exam_id VARCHAR(36) NOT NULL,
    submission_a_id VARCHAR(36) NOT NULL,
    submission_b_id VARCHAR(36) NOT NULL,
    similarity_rate DECIMAL(5,2) NOT NULL,
    shared_hashes_count INT NOT NULL,
    is_flagged TINYINT NOT NULL,
    created_at DATETIME NOT NULL,
    adjudication_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    adjudication_notes TEXT NULL,
    adjudicated_by_id VARCHAR(36) NULL,
    adjudicated_at DATETIME NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_similarity_matrix PRIMARY KEY (id),
    CONSTRAINT fk_similarity_exam FOREIGN KEY (exam_id) REFERENCES exams (id),
    CONSTRAINT fk_similarity_submission_a FOREIGN KEY (submission_a_id) REFERENCES submissions (id),
    CONSTRAINT fk_similarity_submission_b FOREIGN KEY (submission_b_id) REFERENCES submissions (id),
    CONSTRAINT fk_similarity_adjudicated_by FOREIGN KEY (adjudicated_by_id) REFERENCES users (id),
    CONSTRAINT uk_similarity_submission_pair UNIQUE (submission_a_id, submission_b_id),
    INDEX idx_similarity_exam_flagged (exam_id, is_flagged),
    INDEX idx_similarity_adjudication_status (adjudication_status),
    INDEX idx_similarity_exam_active (exam_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 12. TABLE: plagiarism_evidence
-- Purpose: Granular matching snippets, side-by-side AST match spans, and line ranges.
-- Mapped Entity: com.codeassess.plagiarism.entity.PlagiarismEvidence
-- Sources: V5__create_scoring_and_plagiarism_evidence.sql
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plagiarism_evidence (
    id VARCHAR(36) NOT NULL,
    similarity_matrix_id BIGINT NOT NULL,
    evidence_type VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    submission_a_start_line INT NULL,
    submission_a_end_line INT NULL,
    submission_b_start_line INT NULL,
    submission_b_end_line INT NULL,
    submission_a_location VARCHAR(255) NULL,
    submission_b_location VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_plagiarism_evidence PRIMARY KEY (id),
    CONSTRAINT fk_evidence_similarity FOREIGN KEY (similarity_matrix_id) REFERENCES similarity_matrix (id),
    INDEX idx_evidence_similarity (similarity_matrix_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 13. TABLE: scoring_criteria
-- Purpose: Rubric criteria definitions and maximum allocated points for scoring.
-- Mapped Entity: com.codeassess.scoring.entity.ScoringCriteria
-- Sources: V5__create_scoring_and_plagiarism_evidence.sql
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS scoring_criteria (
    id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    max_score DECIMAL(5,2) NOT NULL,
    CONSTRAINT pk_scoring_criteria PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 14. TABLE: rule_scores
-- Purpose: Itemized points awarded per rubric criterion for a submission.
-- Mapped Entity: com.codeassess.scoring.entity.RuleScore
-- Sources: V5__create_scoring_and_plagiarism_evidence.sql
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rule_scores (
    id VARCHAR(36) NOT NULL,
    submission_id VARCHAR(36) NOT NULL,
    criteria_id VARCHAR(36) NOT NULL,
    score DECIMAL(5,2) NOT NULL,
    CONSTRAINT pk_rule_scores PRIMARY KEY (id),
    CONSTRAINT fk_rule_scores_submission FOREIGN KEY (submission_id) REFERENCES submissions (id),
    CONSTRAINT fk_rule_scores_criteria FOREIGN KEY (criteria_id) REFERENCES scoring_criteria (id),
    CONSTRAINT uk_rule_scores_submission_criteria UNIQUE (submission_id, criteria_id),
    INDEX idx_rule_scores_submission (submission_id),
    INDEX idx_rule_scores_criteria (criteria_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 15. TABLE: grade_audit_logs
-- Purpose: Immutable audit log tracking score adjustments, instructor overrides,
--          pre/post score states, and change rationale.
-- Mapped Entity: com.codeassess.scoring.entity.GradeAuditLog
-- Sources: V6__create_grade_audit_logs.sql
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS grade_audit_logs (
    id VARCHAR(36) NOT NULL,
    submission_id VARCHAR(36) NOT NULL,
    actor_id VARCHAR(36) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    pre_rubric_score DECIMAL(5,2) NULL,
    post_rubric_score DECIMAL(5,2) NULL,
    pre_override_score DECIMAL(5,2) NULL,
    post_override_score DECIMAL(5,2) NULL,
    pre_effective_score DECIMAL(5,2) NULL,
    post_effective_score DECIMAL(5,2) NULL,
    rationale TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_grade_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_grade_audit_submission FOREIGN KEY (submission_id) REFERENCES submissions (id),
    CONSTRAINT fk_grade_audit_actor FOREIGN KEY (actor_id) REFERENCES users (id),
    INDEX idx_grade_audit_submission (submission_id),
    INDEX idx_grade_audit_actor (actor_id),
    INDEX idx_grade_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 16. TABLE: plagiarism_appeals
-- Purpose: Student appeals challenging plagiarism flags or disputed grades.
-- Mapped Entity: com.codeassess.plagiarism.entity.Appeal
-- Sources: V1__initial_schema.sql, V4__extend_appeals_schema.sql
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plagiarism_appeals (
    id BIGINT AUTO_INCREMENT NOT NULL,
    submission_id VARCHAR(36) NOT NULL,
    student_id VARCHAR(36) NOT NULL,
    reason TEXT NOT NULL,
    basis VARCHAR(100) NULL,
    status VARCHAR(20) NOT NULL,
    reviewer_notes TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    CONSTRAINT pk_plagiarism_appeals PRIMARY KEY (id),
    CONSTRAINT fk_appeals_submission FOREIGN KEY (submission_id) REFERENCES submissions (id),
    CONSTRAINT fk_appeals_student FOREIGN KEY (student_id) REFERENCES users (id),
    INDEX idx_appeals_status (status),
    INDEX idx_appeals_student (student_id),
    INDEX idx_appeals_submission (submission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 17. TABLE: exam_matrix_coordination
-- Purpose: Distributed worker coordination, generation locking, worker leasing,
--          and failure retry telemetry for plagiarism matrix computation.
-- Mapped Entity: com.codeassess.plagiarism.entity.ExamMatrixCoordination
-- Sources: V10__add_matrix_coordination_and_active_flag.sql, V11__add_matrix_coordination_failure_and_retry_columns.sql
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exam_matrix_coordination (
    exam_id VARCHAR(36) NOT NULL,
    current_generation BIGINT NOT NULL DEFAULT 0,
    requested_generation BIGINT NOT NULL DEFAULT 0,
    locked_generation BIGINT NOT NULL DEFAULT 0,
    lease_token VARCHAR(36) NULL,
    worker_id VARCHAR(100) NULL,
    lease_expires_at DATETIME NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'IDLE',
    failure_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000) NULL,
    next_retry_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_exam_matrix_coordination PRIMARY KEY (exam_id),
    CONSTRAINT fk_coord_exam FOREIGN KEY (exam_id) REFERENCES exams (id) ON DELETE CASCADE,
    INDEX idx_coord_status_lease (status, lease_expires_at),
    INDEX idx_coord_status_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Restore foreign key checks
SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS;
