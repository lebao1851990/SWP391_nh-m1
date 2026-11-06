-- =============================================================================
-- CODEASSESS — E2E LIVE & DEMO SEED DATA SCRIPT
-- File: 06_e2e_live_seed_data.sql
-- Phase: P0-3 Demo Seed Data
-- Description: Essential synthetic seed data strictly required for the 45
--              Playwright tests in project 'live' and 'hybrid'.
-- Governance & Safety Rules:
--   - Target Database: codeassess (Development / Test / Demo environment ONLY).
--   - Top-down topological FK order: scoring_criteria -> users -> classes ->
--     class_students -> exams -> exam_classes -> submissions -> grade_audit_logs.
--   - All foreign keys are strictly satisfied without disabling FOREIGN_KEY_CHECKS.
--   - Idempotent: Safe ON DUPLICATE KEY UPDATE clauses that only update non-identifying
--     attributes and do NOT overwrite data outside the seed scope.
--   - Conflict Protection: Pre-flight check procedure signals fatal SQLSTATE '45000'
--     if any seed email, class code, or submission ID collides with non-seed data.
--   - Explicit Rollback: Two-tiered cleanup specification targeting exact deterministic IDs.
--   - Zero plaintext secrets or passwords.
-- =============================================================================

USE codeassess;

-- -----------------------------------------------------------------------------
-- TRANSACTIONAL RUNNER & PRE-CHECK WITH ENGINE-LEVEL FAIL-FAST SEMANTICS
-- Encapsulates conflict detection and all 8 INSERT statements into an atomic
-- stored procedure protected by an SQLEXCEPTION EXIT HANDLER with automatic ROLLBACK.
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS seed_e2e_live_data;
DELIMITER $$
CREATE PROCEDURE seed_e2e_live_data()
BEGIN
    DECLARE user_conflicts INT DEFAULT 0;
    DECLARE class_conflicts INT DEFAULT 0;
    DECLARE sub_conflicts INT DEFAULT 0;

    -- Exit handler: Any SQL exception triggers instant engine-level rollback and re-raises error
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    -- Step 1: Pre-check for unique key collisions with non-seed records
    SELECT COUNT(*) INTO user_conflicts FROM users 
    WHERE email IN (
        'teachera@fpt.edu.vn',
        'teacher.test@codeassess.edu.vn',
        'studenta@fpt.edu.vn',
        'studentb@fpt.edu.vn',
        'student.test@codeassess.edu.vn',
        'admin@codeassess.edu.vn'
    )
    AND id NOT IN (
        'usr-e2e-teacher-000000000001',
        'usr-e2e-teacher-000000000002',
        'usr-e2e-student-000000000001',
        'usr-e2e-student-000000000002',
        'usr-e2e-student-000000000003',
        'usr-e2e-admin-0000000000001'
    );

    SELECT COUNT(*) INTO class_conflicts FROM classes
    WHERE code IN ('PRO192-E2E-FA26', 'CSD201-E2E-FA26')
      AND id NOT IN ('cls-e2e-pro192-00000001', 'cls-e2e-csd201-00000002');

    SELECT COUNT(*) INTO sub_conflicts FROM submissions
    WHERE (id = '040b61a8-3c7a-4ea2-aace-18a0ba2d9d32' AND (student_id != 'usr-e2e-student-000000000001' OR exam_id != 'exm-e2e-pro192-midterm1'))
       OR (id = '007cf9ae-4385-46dc-b492-ba891af6a535' AND (student_id != 'usr-e2e-student-000000000001' OR exam_id != 'exm-e2e-pro192-midterm1'));

    IF user_conflicts > 0 OR class_conflicts > 0 OR sub_conflicts > 0 THEN
        SIGNAL SQLSTATE '45000' 
        SET MESSAGE_TEXT = 'SEED CONFLICT DETECTED: Database contains existing records with conflicting emails, class codes, or submission IDs. Aborting seed.';
    END IF;

    -- Step 2: Atomic transaction for all 8 seed entity groups
    START TRANSACTION;

    -- -------------------------------------------------------------------------
    -- 1. SCORING CRITERIA (Rubric evaluation definitions)
-- ON DUPLICATE KEY UPDATE: Refreshes criteria descriptive metadata and max points.
-- -----------------------------------------------------------------------------
INSERT INTO scoring_criteria (id, name, description, max_score)
VALUES
    ('crit-e2e-compilation-01', 'Compilation & Execution Integrity', 'Source code compiles without errors, respects CPU/memory limits, and terminates cleanly.', 3.00),
    ('crit-e2e-correctness-02', 'Functional Correctness & Test Pass Rate', 'Program passes automated test cases with expected outputs.', 4.00),
    ('crit-e2e-structure-03', 'Code Quality & Structural Modularity', 'Clean OOP modularity, appropriate naming, standard exception handling.', 1.50),
    ('crit-e2e-efficiency-04', 'Time & Space Efficiency', 'Algorithmic time complexity and memory overhead stay within limits.', 1.50)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    max_score = VALUES(max_score);

-- -----------------------------------------------------------------------------
-- 2. USERS (Instructors, Students, and E2E Admin)
-- Passwords:
--   - Pre-computed standard BCrypt hash (Cost 10) compatible with E2E runner.
-- ON DUPLICATE KEY UPDATE: Refreshes full_name, role, and updated_at.
-- -----------------------------------------------------------------------------
INSERT INTO users (id, email, password_hash, role, full_name, created_at, updated_at)
VALUES
    -- Teachers
    ('usr-e2e-teacher-000000000001', 'teachera@fpt.edu.vn',           '$2a$10$qsyCuBLdtRV3WFZo.FTknevwbIPkjscNLm2Qv90qkAM8k3l.kyy4G', 'TEACHER', 'Teacher A (Authorized Teacher)', NOW(), NOW()),
    ('usr-e2e-teacher-000000000002', 'teacher.test@codeassess.edu.vn', '$2a$10$qsyCuBLdtRV3WFZo.FTknevwbIPkjscNLm2Qv90qkAM8k3l.kyy4G', 'TEACHER', 'GV. Nguyen Van Giang',           NOW(), NOW()),
    -- Students
    ('usr-e2e-student-000000000001', 'studenta@fpt.edu.vn',           '$2a$10$ixonXg7wrN0aZIcW9Mte0.TNXEKDockM0vdaKJmBPK7sFHHGlZCYC', 'STUDENT', 'Student A (Nguyen Van An)',      NOW(), NOW()),
    ('usr-e2e-student-000000000002', 'studentb@fpt.edu.vn',           '$2a$10$ixonXg7wrN0aZIcW9Mte0.TNXEKDockM0vdaKJmBPK7sFHHGlZCYC', 'STUDENT', 'Student B (Tran Thi Binh)',      NOW(), NOW()),
    ('usr-e2e-student-000000000003', 'student.test@codeassess.edu.vn', '$2a$10$ixonXg7wrN0aZIcW9Mte0.TNXEKDockM0vdaKJmBPK7sFHHGlZCYC', 'STUDENT', 'SV. Le Quoc Cuong',              NOW(), NOW()),
    -- Admin
    ('usr-e2e-admin-0000000000001', 'admin@codeassess.edu.vn',        '$2a$10$RufBG7Q/gNL2P0xUpa4UGuZorJDI99iZ1PHmVzDSVN.uje55kD0ZW', 'ADMIN',   'Quản trị viên CodeAssess E2E',    NOW(), NOW())
ON DUPLICATE KEY UPDATE
    full_name = VALUES(full_name),
    role = VALUES(role),
    updated_at = NOW();

-- -----------------------------------------------------------------------------
-- 3. CLASSES (Course cohorts owned by Teacher A)
-- ON DUPLICATE KEY UPDATE: Refreshes class name and semester.
-- -----------------------------------------------------------------------------
INSERT INTO classes (id, teacher_id, name, code, semester, created_at)
VALUES
    ('cls-e2e-pro192-00000001', 'usr-e2e-teacher-000000000001', 'Object-Oriented Programming (Java)', 'PRO192-E2E-FA26', 'Fall 2026', NOW()),
    ('cls-e2e-csd201-00000002', 'usr-e2e-teacher-000000000001', 'Data Structures & Algorithms',        'CSD201-E2E-FA26', 'Fall 2026', NOW())
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    semester = VALUES(semester);

-- -----------------------------------------------------------------------------
-- 4. CLASS_STUDENTS (Enrollment rosters)
-- Composite PK: (class_id, student_id). Idempotent no-op on duplicate.
-- -----------------------------------------------------------------------------
INSERT INTO class_students (class_id, student_id)
VALUES
    -- PRO192: Student A, Student B, and Student Test
    ('cls-e2e-pro192-00000001', 'usr-e2e-student-000000000001'),
    ('cls-e2e-pro192-00000001', 'usr-e2e-student-000000000002'),
    ('cls-e2e-pro192-00000001', 'usr-e2e-student-000000000003'),
    -- CSD201: Student A
    ('cls-e2e-csd201-00000002', 'usr-e2e-student-000000000001')
ON DUPLICATE KEY UPDATE
    class_id = VALUES(class_id);

-- -----------------------------------------------------------------------------
-- 5. EXAMS (Assessments with deleted_at IS NULL for active queries)
-- ON DUPLICATE KEY UPDATE: Ensures title, status, and deleted_at = NULL are preserved.
-- -----------------------------------------------------------------------------
INSERT INTO exams (id, class_id, title, exam_date, duration, status, created_at, deleted_at)
VALUES
    -- Core Exam A (Required for core-flows.spec.ts)
    ('exm-e2e-exam-a-00000001', 'cls-e2e-pro192-00000001', 'Exam A', '2026-10-10 08:00:00', 90, 'ACTIVE', NOW(), NULL),
    -- Class-aware Midterm Exam (Required for class-aware-exams.spec.ts)
    ('exm-e2e-pro192-midterm1', 'cls-e2e-pro192-00000001', 'PRO192 Midterm Practical Assessment', '2026-10-15 08:00:00', 120, 'ACTIVE', NOW(), NULL)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    status = VALUES(status),
    deleted_at = NULL;

-- -----------------------------------------------------------------------------
-- 6. EXAM_CLASSES (Class-Aware Multi-Class Assignment Table)
-- Composite PK: (exam_id, class_id). Idempotent no-op on duplicate.
-- -----------------------------------------------------------------------------
INSERT INTO exam_classes (exam_id, class_id, created_at)
VALUES
    ('exm-e2e-exam-a-00000001', 'cls-e2e-pro192-00000001', NOW()),
    ('exm-e2e-pro192-midterm1', 'cls-e2e-pro192-00000001', NOW()),
    ('exm-e2e-pro192-midterm1', 'cls-e2e-csd201-00000002', NOW())
ON DUPLICATE KEY UPDATE
    created_at = VALUES(created_at);

-- -----------------------------------------------------------------------------
-- 7. SUBMISSIONS (Including the two critical hardcoded UUIDs)
-- ON DUPLICATE KEY UPDATE: Resets total_score, override_score, and status
-- to the exact expected baseline state required for test execution.
-- -----------------------------------------------------------------------------
INSERT INTO submissions (
    id, exam_id, student_id, status, source_code, total_score, override_score, auto_score,
    test_cases_passed, test_cases_total, evaluated_at, created_at, submission_type,
    original_filename, execution_status, exit_code, compile_output, stdout, stderr, execution_duration_ms
)
VALUES
    -- Invariant 1: Submission for grade-audit-history.spec.ts
    (
        '040b61a8-3c7a-4ea2-aace-18a0ba2d9d32',
        'exm-e2e-pro192-midterm1',
        'usr-e2e-student-000000000001',
        'COMPLETED',
        'public class Solution {\n    public static void main(String[] args) {\n        System.out.println("Hello from Student A");\n    }\n}',
        80.00,
        NULL, -- Crucial: Must be NULL so UI renders 'No override' pill
        80.00,
        4,
        5,
        NOW(),
        NOW(),
        'SINGLE_FILE',
        'Solution.java',
        'SUCCESS',
        0,
        'Compilation succeeded',
        'Hello from Student A\n',
        '',
        185
    ),
    -- Invariant 2: Submission for scoring-override.spec.ts
    (
        '007cf9ae-4385-46dc-b492-ba891af6a535',
        'exm-e2e-pro192-midterm1',
        'usr-e2e-student-000000000001',
        'COMPLETED',
        'public class SolutionOverride {\n    public static void main(String[] args) {\n        System.out.println("Pending override test");\n    }\n}',
        0.00, -- Crucial: Initial rubric score must be 0 so badge shows '(Rubric: 0 pts)'
        NULL, -- Initial override is NULL; test 1 sets this to 95 pts
        0.00,
        0,
        5,
        NOW(),
        NOW(),
        'SINGLE_FILE',
        'SolutionOverride.java',
        'SUCCESS',
        0,
        'Compilation succeeded',
        'Pending override test\n',
        '',
        150
    )
ON DUPLICATE KEY UPDATE
    total_score = VALUES(total_score),
    override_score = VALUES(override_score),
    status = VALUES(status);

-- -----------------------------------------------------------------------------
-- 8. GRADE_AUDIT_LOGS (Audit history for submission 040b61a8-...)
-- ON DUPLICATE KEY UPDATE: Resets post_override_score = NULL and action_type.
-- -----------------------------------------------------------------------------
INSERT INTO grade_audit_logs (
    id, submission_id, actor_id, action_type,
    pre_rubric_score, post_rubric_score,
    pre_override_score, post_override_score,
    pre_effective_score, post_effective_score,
    rationale, created_at
)
VALUES
    (
        'log-e2e-grade-audit-00000001',
        '040b61a8-3c7a-4ea2-aace-18a0ba2d9d32',
        'usr-e2e-teacher-000000000001',
        'OVERRIDE_CLEARED',
        80.00,
        80.00,
        NULL,
        NULL, -- Must be NULL to assert 'No override' pill in grade-audit-history.spec.ts
        80.00,
        80.00,
        'Initial evaluation completed by rubric.',
        NOW()
    )
ON DUPLICATE KEY UPDATE
    post_override_score = NULL,
    action_type = VALUES(action_type),
    rationale = VALUES(rationale);

    COMMIT;
END$$
DELIMITER ;

CALL seed_e2e_live_data();
DROP PROCEDURE seed_e2e_live_data;

-- =============================================================================
-- ROLLBACK SPECIFICATIONS & GOVERNANCE
-- =============================================================================

-- LEVEL 1: PURE SEED-ONLY ROLLBACK [STATUS: IMPLEMENTED & DETERMINISTIC]
-- Scope: Targets strictly the exact deterministic records created by this script.
-- Safe to execute when live tests have NOT run or before running test suite.
DROP PROCEDURE IF EXISTS rollback_e2e_live_data;
DELIMITER $$
CREATE PROCEDURE rollback_e2e_live_data()
BEGIN
    -- Counters for Exact Seed Existence (8 Tables)
    DECLARE cnt_crit_in INT DEFAULT 0;
    DECLARE cnt_users_in INT DEFAULT 0;
    DECLARE cnt_classes_in INT DEFAULT 0;
    DECLARE cnt_cs_in INT DEFAULT 0;
    DECLARE cnt_cs_out INT DEFAULT 0;
    DECLARE cnt_exams_in INT DEFAULT 0;
    DECLARE cnt_ec_in INT DEFAULT 0;
    DECLARE cnt_ec_out INT DEFAULT 0;
    DECLARE cnt_subs_in INT DEFAULT 0;
    DECLARE cnt_audit_in INT DEFAULT 0;

    -- Counters for Scoring Drift (5 Mutations)
    DECLARE cnt_drift_sub1 INT DEFAULT 0;
    DECLARE cnt_drift_sub2 INT DEFAULT 0;
    DECLARE cnt_drift_audit INT DEFAULT 0;
    DECLARE cnt_drift_exam1 INT DEFAULT 0;

    -- Counters for 24 Inbound Foreign Keys (Direct Child Queries)
    DECLARE chk_c1_reset_tokens INT DEFAULT 0;
    DECLARE chk_r1_classes_teacher INT DEFAULT 0;
    DECLARE chk_r2_cs_class INT DEFAULT 0;
    DECLARE chk_r3_cs_student INT DEFAULT 0;
    DECLARE chk_r4_exams_class INT DEFAULT 0;
    DECLARE chk_c2_ec_exam INT DEFAULT 0;
    DECLARE chk_r5_ec_class INT DEFAULT 0;
    DECLARE chk_c3_test_cases INT DEFAULT 0;
    DECLARE chk_r6_subs_exam INT DEFAULT 0;
    DECLARE chk_r7_subs_student INT DEFAULT 0;
    DECLARE chk_c4_str INT DEFAULT 0;
    DECLARE chk_r8_ast INT DEFAULT 0;
    DECLARE chk_r9_sim_exam INT DEFAULT 0;
    DECLARE chk_r10_sim_sub_a INT DEFAULT 0;
    DECLARE chk_r11_sim_sub_b INT DEFAULT 0;
    DECLARE chk_r12_sim_adjudicated INT DEFAULT 0;
    DECLARE chk_r13_rule_scores_sub INT DEFAULT 0;
    DECLARE chk_r14_rule_scores_crit INT DEFAULT 0;
    DECLARE chk_r15_audit_extra_sub INT DEFAULT 0;
    DECLARE chk_r16_audit_extra_user INT DEFAULT 0;
    DECLARE chk_r17_appeals_sub INT DEFAULT 0;
    DECLARE chk_r18_appeals_student INT DEFAULT 0;
    DECLARE chk_c5_matrix_coord INT DEFAULT 0;
    DECLARE chk_c6_exam_rubric INT DEFAULT 0;

    -- Exit Handler: Automatic Engine-Level Rollback and Resignal on SQLEXCEPTION
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    -- =========================================================================
    -- PHASE 1: EXACT SEED EXISTENCE & EXACT SET EQUALITY (8 TABLES)
    -- =========================================================================

    -- 1.1. scoring_criteria: Exactly 4 seed records
    SELECT COUNT(*) INTO cnt_crit_in FROM scoring_criteria
    WHERE id IN (
        'crit-e2e-compilation-01',
        'crit-e2e-correctness-02',
        'crit-e2e-structure-03',
        'crit-e2e-efficiency-04'
    );
    IF cnt_crit_in != 4 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: scoring_criteria seed rows missing or altered.';
    END IF;

    -- 1.2. users: Exactly 6 seed records
    SELECT COUNT(*) INTO cnt_users_in FROM users
    WHERE id IN (
        'usr-e2e-teacher-000000000001',
        'usr-e2e-teacher-000000000002',
        'usr-e2e-student-000000000001',
        'usr-e2e-student-000000000002',
        'usr-e2e-student-000000000003',
        'usr-e2e-admin-0000000000001'
    );
    IF cnt_users_in != 6 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: users seed rows missing or altered.';
    END IF;

    -- 1.3. classes: Exactly 2 seed records
    SELECT COUNT(*) INTO cnt_classes_in FROM classes
    WHERE id IN ('cls-e2e-pro192-00000001', 'cls-e2e-csd201-00000002');
    IF cnt_classes_in != 2 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: classes seed rows missing or altered.';
    END IF;

    -- 1.4. class_students: Exact Set Equality (In-set = 4 AND Out-of-set = 0)
    SELECT COUNT(*) INTO cnt_cs_in FROM class_students
    WHERE (class_id, student_id) IN (
        ('cls-e2e-pro192-00000001', 'usr-e2e-student-000000000001'),
        ('cls-e2e-pro192-00000001', 'usr-e2e-student-000000000002'),
        ('cls-e2e-pro192-00000001', 'usr-e2e-student-000000000003'),
        ('cls-e2e-csd201-00000002', 'usr-e2e-student-000000000001')
    );
    SELECT COUNT(*) INTO cnt_cs_out FROM class_students
    WHERE (class_id IN ('cls-e2e-pro192-00000001', 'cls-e2e-csd201-00000002')
        OR student_id IN ('usr-e2e-student-000000000001', 'usr-e2e-student-000000000002', 'usr-e2e-student-000000000003'))
      AND (class_id, student_id) NOT IN (
        ('cls-e2e-pro192-00000001', 'usr-e2e-student-000000000001'),
        ('cls-e2e-pro192-00000001', 'usr-e2e-student-000000000002'),
        ('cls-e2e-pro192-00000001', 'usr-e2e-student-000000000003'),
        ('cls-e2e-csd201-00000002', 'usr-e2e-student-000000000001')
    );
    IF cnt_cs_in != 4 OR cnt_cs_out > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: class_students exact set mismatch or foreign enrollments detected.';
    END IF;

    -- 1.5. exams: Exactly 2 seed records with expected titles, classes, and active status
    SELECT COUNT(*) INTO cnt_exams_in FROM exams
    WHERE (id = 'exm-e2e-exam-a-00000001' AND (class_id <=> 'cls-e2e-pro192-00000001') AND title = 'Exam A' AND status = 'ACTIVE')
       OR (id = 'exm-e2e-pro192-midterm1' AND (class_id <=> 'cls-e2e-pro192-00000001') AND title = 'PRO192 Midterm Practical Assessment' AND status = 'ACTIVE');
    IF cnt_exams_in != 2 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: exams seed rows missing, title altered, or re-parented.';
    END IF;

    -- 1.6. exam_classes: Exact Set Equality (In-set = 3 AND Out-of-set = 0)
    SELECT COUNT(*) INTO cnt_ec_in FROM exam_classes
    WHERE (exam_id, class_id) IN (
        ('exm-e2e-exam-a-00000001', 'cls-e2e-pro192-00000001'),
        ('exm-e2e-pro192-midterm1', 'cls-e2e-pro192-00000001'),
        ('exm-e2e-pro192-midterm1', 'cls-e2e-csd201-00000002')
    );
    SELECT COUNT(*) INTO cnt_ec_out FROM exam_classes
    WHERE (exam_id IN ('exm-e2e-exam-a-00000001', 'exm-e2e-pro192-midterm1')
        OR class_id IN ('cls-e2e-pro192-00000001', 'cls-e2e-csd201-00000002'))
      AND (exam_id, class_id) NOT IN (
        ('exm-e2e-exam-a-00000001', 'cls-e2e-pro192-00000001'),
        ('exm-e2e-pro192-midterm1', 'cls-e2e-pro192-00000001'),
        ('exm-e2e-pro192-midterm1', 'cls-e2e-csd201-00000002')
    );
    IF cnt_ec_in != 3 OR cnt_ec_out > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: exam_classes exact set mismatch or foreign exam assignments detected.';
    END IF;

    -- 1.7. submissions: Exactly 2 seed records matching expected exams and students
    SELECT COUNT(*) INTO cnt_subs_in FROM submissions
    WHERE (id = '040b61a8-3c7a-4ea2-aace-18a0ba2d9d32' AND exam_id = 'exm-e2e-pro192-midterm1' AND student_id = 'usr-e2e-student-000000000001')
       OR (id = '007cf9ae-4385-46dc-b492-ba891af6a535' AND exam_id = 'exm-e2e-pro192-midterm1' AND student_id = 'usr-e2e-student-000000000001');
    IF cnt_subs_in != 2 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: submissions seed rows missing or re-parented.';
    END IF;

    -- 1.8. grade_audit_logs: Exact single seed record matching expected submission and actor
    SELECT COUNT(*) INTO cnt_audit_in FROM grade_audit_logs
    WHERE id = 'log-e2e-grade-audit-00000001'
      AND submission_id = '040b61a8-3c7a-4ea2-aace-18a0ba2d9d32'
      AND actor_id = 'usr-e2e-teacher-000000000001'
      AND action_type = 'OVERRIDE_CLEARED'
      AND post_override_score IS NULL;
    IF cnt_audit_in != 1 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: grade_audit_logs seed record missing, altered, or re-parented.';
    END IF;

    -- =========================================================================
    -- PHASE 2: SCORING & SOFT-DELETE DRIFT PREFLIGHT (5 MUTATIONS)
    -- =========================================================================

    -- 2.1. Submission 1 override_score drift
    SELECT COUNT(*) INTO cnt_drift_sub1 FROM submissions
    WHERE id = '040b61a8-3c7a-4ea2-aace-18a0ba2d9d32'
      AND override_score IS NOT NULL;

    -- 2.2. Submission 2 total_score and override_score drift (NULL-Safe)
    SELECT COUNT(*) INTO cnt_drift_sub2 FROM submissions
    WHERE id = '007cf9ae-4385-46dc-b492-ba891af6a535'
      AND (NOT (total_score <=> 0.00) OR override_score IS NOT NULL);

    -- 2.3. Grade audit log post_override_score drift
    SELECT COUNT(*) INTO cnt_drift_audit FROM grade_audit_logs
    WHERE id = 'log-e2e-grade-audit-00000001'
      AND post_override_score IS NOT NULL;

    -- 2.4. Exam A deleted_at drift (Soft-delete check)
    SELECT COUNT(*) INTO cnt_drift_exam1 FROM exams
    WHERE id = 'exm-e2e-exam-a-00000001'
      AND deleted_at IS NOT NULL;

    IF cnt_drift_sub1 > 0 OR cnt_drift_sub2 > 0 OR cnt_drift_audit > 0 OR cnt_drift_exam1 > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Live scoring or soft-delete drift detected. Environment is not pristine baseline.';
    END IF;

    -- =========================================================================
    -- PHASE 3: ALL 24 INBOUND FK PREFLIGHT ASSERTIONS (DIRECT CHILD QUERIES)
    -- =========================================================================

    -- FK 1: password_reset_tokens.user_id -> users(id) [CASCADE]
    SELECT COUNT(*) INTO chk_c1_reset_tokens FROM password_reset_tokens
    WHERE user_id IN (
        'usr-e2e-teacher-000000000001', 'usr-e2e-teacher-000000000002',
        'usr-e2e-student-000000000001', 'usr-e2e-student-000000000002',
        'usr-e2e-student-000000000003', 'usr-e2e-admin-0000000000001'
    );
    IF chk_c1_reset_tokens > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on password_reset_tokens.user_id (CASCADE guard).';
    END IF;

    -- FK 2: classes.teacher_id -> users(id) [RESTRICT]
    SELECT COUNT(*) INTO chk_r1_classes_teacher FROM classes
    WHERE teacher_id IN ('usr-e2e-teacher-000000000001', 'usr-e2e-teacher-000000000002')
      AND id NOT IN ('cls-e2e-pro192-00000001', 'cls-e2e-csd201-00000002');
    IF chk_r1_classes_teacher > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on classes.teacher_id (RESTRICT guard).';
    END IF;

    -- FK 3: class_students.class_id -> classes(id) [RESTRICT]
    SELECT COUNT(*) INTO chk_r2_cs_class FROM class_students
    WHERE class_id IN ('cls-e2e-pro192-00000001', 'cls-e2e-csd201-00000002')
      AND (class_id, student_id) NOT IN (
        ('cls-e2e-pro192-00000001', 'usr-e2e-student-000000000001'),
        ('cls-e2e-pro192-00000001', 'usr-e2e-student-000000000002'),
        ('cls-e2e-pro192-00000001', 'usr-e2e-student-000000000003'),
        ('cls-e2e-csd201-00000002', 'usr-e2e-student-000000000001')
    );
    IF chk_r2_cs_class > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on class_students.class_id (RESTRICT guard).';
    END IF;

    -- FK 4: class_students.student_id -> users(id) [RESTRICT]
    SELECT COUNT(*) INTO chk_r3_cs_student FROM class_students
    WHERE student_id IN ('usr-e2e-student-000000000001', 'usr-e2e-student-000000000002', 'usr-e2e-student-000000000003')
      AND (class_id, student_id) NOT IN (
        ('cls-e2e-pro192-00000001', 'usr-e2e-student-000000000001'),
        ('cls-e2e-pro192-00000001', 'usr-e2e-student-000000000002'),
        ('cls-e2e-pro192-00000001', 'usr-e2e-student-000000000003'),
        ('cls-e2e-csd201-00000002', 'usr-e2e-student-000000000001')
    );
    IF chk_r3_cs_student > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on class_students.student_id (RESTRICT guard).';
    END IF;

    -- FK 5: exams.class_id -> classes(id) [RESTRICT] (NULL-Safe)
    SELECT COUNT(*) INTO chk_r4_exams_class FROM exams
    WHERE class_id IN ('cls-e2e-pro192-00000001', 'cls-e2e-csd201-00000002')
      AND id NOT IN ('exm-e2e-exam-a-00000001', 'exm-e2e-pro192-midterm1');
    IF chk_r4_exams_class > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on exams.class_id (RESTRICT guard).';
    END IF;

    -- FK 6: exam_classes.exam_id -> exams(id) [CASCADE]
    SELECT COUNT(*) INTO chk_c2_ec_exam FROM exam_classes
    WHERE exam_id IN ('exm-e2e-exam-a-00000001', 'exm-e2e-pro192-midterm1')
      AND (exam_id, class_id) NOT IN (
        ('exm-e2e-exam-a-00000001', 'cls-e2e-pro192-00000001'),
        ('exm-e2e-pro192-midterm1', 'cls-e2e-pro192-00000001'),
        ('exm-e2e-pro192-midterm1', 'cls-e2e-csd201-00000002')
    );
    IF chk_c2_ec_exam > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on exam_classes.exam_id (CASCADE guard).';
    END IF;

    -- FK 7: exam_classes.class_id -> classes(id) [RESTRICT]
    SELECT COUNT(*) INTO chk_r5_ec_class FROM exam_classes
    WHERE class_id IN ('cls-e2e-pro192-00000001', 'cls-e2e-csd201-00000002')
      AND (exam_id, class_id) NOT IN (
        ('exm-e2e-exam-a-00000001', 'cls-e2e-pro192-00000001'),
        ('exm-e2e-pro192-midterm1', 'cls-e2e-pro192-00000001'),
        ('exm-e2e-pro192-midterm1', 'cls-e2e-csd201-00000002')
    );
    IF chk_r5_ec_class > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on exam_classes.class_id (RESTRICT guard).';
    END IF;

    -- FK 8: test_cases.exam_id -> exams(id) [CASCADE]
    SELECT COUNT(*) INTO chk_c3_test_cases FROM test_cases
    WHERE exam_id IN ('exm-e2e-exam-a-00000001', 'exm-e2e-pro192-midterm1');
    IF chk_c3_test_cases > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on test_cases.exam_id (CASCADE guard).';
    END IF;

    -- FK 9: submissions.exam_id -> exams(id) [RESTRICT]
    SELECT COUNT(*) INTO chk_r6_subs_exam FROM submissions
    WHERE exam_id IN ('exm-e2e-exam-a-00000001', 'exm-e2e-pro192-midterm1')
      AND id NOT IN ('040b61a8-3c7a-4ea2-aace-18a0ba2d9d32', '007cf9ae-4385-46dc-b492-ba891af6a535');
    IF chk_r6_subs_exam > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on submissions.exam_id (RESTRICT guard).';
    END IF;

    -- FK 10: submissions.student_id -> users(id) [RESTRICT]
    SELECT COUNT(*) INTO chk_r7_subs_student FROM submissions
    WHERE student_id IN ('usr-e2e-student-000000000001', 'usr-e2e-student-000000000002', 'usr-e2e-student-000000000003')
      AND id NOT IN ('040b61a8-3c7a-4ea2-aace-18a0ba2d9d32', '007cf9ae-4385-46dc-b492-ba891af6a535');
    IF chk_r7_subs_student > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on submissions.student_id (RESTRICT guard).';
    END IF;

    -- FK 11: submission_test_results.submission_id -> submissions(id) [CASCADE]
    SELECT COUNT(*) INTO chk_c4_str FROM submission_test_results
    WHERE submission_id IN ('040b61a8-3c7a-4ea2-aace-18a0ba2d9d32', '007cf9ae-4385-46dc-b492-ba891af6a535');
    IF chk_c4_str > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on submission_test_results.submission_id (CASCADE guard).';
    END IF;

    -- FK 12: ast_fingerprints.submission_id -> submissions(id) [RESTRICT]
    SELECT COUNT(*) INTO chk_r8_ast FROM ast_fingerprints
    WHERE submission_id IN ('040b61a8-3c7a-4ea2-aace-18a0ba2d9d32', '007cf9ae-4385-46dc-b492-ba891af6a535');
    IF chk_r8_ast > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on ast_fingerprints.submission_id (RESTRICT guard).';
    END IF;

    -- FK 13: similarity_matrix.exam_id -> exams(id) [RESTRICT]
    SELECT COUNT(*) INTO chk_r9_sim_exam FROM similarity_matrix
    WHERE exam_id IN ('exm-e2e-exam-a-00000001', 'exm-e2e-pro192-midterm1');
    IF chk_r9_sim_exam > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on similarity_matrix.exam_id (RESTRICT guard).';
    END IF;

    -- FK 14: similarity_matrix.submission_a_id -> submissions(id) [RESTRICT]
    SELECT COUNT(*) INTO chk_r10_sim_sub_a FROM similarity_matrix
    WHERE submission_a_id IN ('040b61a8-3c7a-4ea2-aace-18a0ba2d9d32', '007cf9ae-4385-46dc-b492-ba891af6a535');
    IF chk_r10_sim_sub_a > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on similarity_matrix.submission_a_id (RESTRICT guard).';
    END IF;

    -- FK 15: similarity_matrix.submission_b_id -> submissions(id) [RESTRICT]
    SELECT COUNT(*) INTO chk_r11_sim_sub_b FROM similarity_matrix
    WHERE submission_b_id IN ('040b61a8-3c7a-4ea2-aace-18a0ba2d9d32', '007cf9ae-4385-46dc-b492-ba891af6a535');
    IF chk_r11_sim_sub_b > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on similarity_matrix.submission_b_id (RESTRICT guard).';
    END IF;

    -- FK 16: similarity_matrix.adjudicated_by_id -> users(id) [RESTRICT] (NULL-Safe)
    SELECT COUNT(*) INTO chk_r12_sim_adjudicated FROM similarity_matrix
    WHERE adjudicated_by_id IS NOT NULL
      AND adjudicated_by_id IN ('usr-e2e-teacher-000000000001', 'usr-e2e-teacher-000000000002');
    IF chk_r12_sim_adjudicated > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on similarity_matrix.adjudicated_by_id (RESTRICT guard).';
    END IF;

    -- FK 17: rule_scores.submission_id -> submissions(id) [RESTRICT]
    SELECT COUNT(*) INTO chk_r13_rule_scores_sub FROM rule_scores
    WHERE submission_id IN ('040b61a8-3c7a-4ea2-aace-18a0ba2d9d32', '007cf9ae-4385-46dc-b492-ba891af6a535');
    IF chk_r13_rule_scores_sub > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on rule_scores.submission_id (RESTRICT guard).';
    END IF;

    -- FK 18: rule_scores.criteria_id -> scoring_criteria(id) [RESTRICT]
    SELECT COUNT(*) INTO chk_r14_rule_scores_crit FROM rule_scores
    WHERE criteria_id IN (
        'crit-e2e-compilation-01', 'crit-e2e-correctness-02',
        'crit-e2e-structure-03', 'crit-e2e-efficiency-04'
    );
    IF chk_r14_rule_scores_crit > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on rule_scores.criteria_id (RESTRICT guard).';
    END IF;

    -- FK 19: grade_audit_logs.submission_id -> submissions(id) [RESTRICT] (Extra logs check)
    SELECT COUNT(*) INTO chk_r15_audit_extra_sub FROM grade_audit_logs
    WHERE submission_id IN ('040b61a8-3c7a-4ea2-aace-18a0ba2d9d32', '007cf9ae-4385-46dc-b492-ba891af6a535')
      AND id != 'log-e2e-grade-audit-00000001';
    IF chk_r15_audit_extra_sub > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on grade_audit_logs.submission_id (RESTRICT guard - extra logs).';
    END IF;

    -- FK 20: grade_audit_logs.actor_id -> users(id) [RESTRICT] (Extra logs check)
    SELECT COUNT(*) INTO chk_r16_audit_extra_user FROM grade_audit_logs
    WHERE actor_id IN (
        'usr-e2e-teacher-000000000001', 'usr-e2e-teacher-000000000002',
        'usr-e2e-student-000000000001', 'usr-e2e-student-000000000002',
        'usr-e2e-student-000000000003', 'usr-e2e-admin-0000000000001'
    )
      AND id != 'log-e2e-grade-audit-00000001';
    IF chk_r16_audit_extra_user > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on grade_audit_logs.actor_id (RESTRICT guard - extra logs).';
    END IF;

    -- FK 21: plagiarism_appeals.submission_id -> submissions(id) [RESTRICT]
    SELECT COUNT(*) INTO chk_r17_appeals_sub FROM plagiarism_appeals
    WHERE submission_id IN ('040b61a8-3c7a-4ea2-aace-18a0ba2d9d32', '007cf9ae-4385-46dc-b492-ba891af6a535');
    IF chk_r17_appeals_sub > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on plagiarism_appeals.submission_id (RESTRICT guard).';
    END IF;

    -- FK 22: plagiarism_appeals.student_id -> users(id) [RESTRICT]
    SELECT COUNT(*) INTO chk_r18_appeals_student FROM plagiarism_appeals
    WHERE student_id IN ('usr-e2e-student-000000000001', 'usr-e2e-student-000000000002', 'usr-e2e-student-000000000003');
    IF chk_r18_appeals_student > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on plagiarism_appeals.student_id (RESTRICT guard).';
    END IF;

    -- FK 23: exam_matrix_coordination.exam_id -> exams(id) [CASCADE] (PK exam_id)
    SELECT COUNT(*) INTO chk_c5_matrix_coord FROM exam_matrix_coordination
    WHERE exam_id IN ('exm-e2e-exam-a-00000001', 'exm-e2e-pro192-midterm1');
    IF chk_c5_matrix_coord > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on exam_matrix_coordination.exam_id (CASCADE guard).';
    END IF;

    -- FK 24: exam_rubric_criteria.exam_id -> exams(id) [CASCADE]
    SELECT COUNT(*) INTO chk_c6_exam_rubric FROM exam_rubric_criteria
    WHERE exam_id IN ('exm-e2e-exam-a-00000001', 'exm-e2e-pro192-midterm1');
    IF chk_c6_exam_rubric > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK ABORTED: Inbound FK violation on exam_rubric_criteria.exam_id (CASCADE guard).';
    END IF;

    -- =========================================================================
    -- PHASE 4: ATOMIC DELETION IN REVERSE TOPOLOGICAL DEPENDENCY ORDER
    -- =========================================================================

    -- Step 1: Leaf table grade_audit_logs (Seed log only)
    DELETE FROM grade_audit_logs WHERE id = 'log-e2e-grade-audit-00000001';

    -- Step 2: Parent of test results and audit logs
    DELETE FROM submissions WHERE id IN ('040b61a8-3c7a-4ea2-aace-18a0ba2d9d32', '007cf9ae-4385-46dc-b492-ba891af6a535');

    -- Step 3: Junction table exam_classes (Exact 3 verified seed pairs)
    DELETE FROM exam_classes WHERE (exam_id, class_id) IN (
        ('exm-e2e-exam-a-00000001', 'cls-e2e-pro192-00000001'),
        ('exm-e2e-pro192-midterm1', 'cls-e2e-pro192-00000001'),
        ('exm-e2e-pro192-midterm1', 'cls-e2e-csd201-00000002')
    );

    -- Step 4: Parent of exam_classes, test_cases, coord, rubric
    DELETE FROM exams WHERE id IN ('exm-e2e-exam-a-00000001', 'exm-e2e-pro192-midterm1');

    -- Step 5: Junction table class_students (Exact 4 verified seed pairs)
    DELETE FROM class_students WHERE (class_id, student_id) IN (
        ('cls-e2e-pro192-00000001', 'usr-e2e-student-000000000001'),
        ('cls-e2e-pro192-00000001', 'usr-e2e-student-000000000002'),
        ('cls-e2e-pro192-00000001', 'usr-e2e-student-000000000003'),
        ('cls-e2e-csd201-00000002', 'usr-e2e-student-000000000001')
    );

    -- Step 6: Parent of exams and class_students
    DELETE FROM classes WHERE id IN ('cls-e2e-pro192-00000001', 'cls-e2e-csd201-00000002');

    -- Step 7: Parent of tokens, classes, submissions, appeals
    DELETE FROM users WHERE id IN (
        'usr-e2e-teacher-000000000001', 'usr-e2e-teacher-000000000002',
        'usr-e2e-student-000000000001', 'usr-e2e-student-000000000002',
        'usr-e2e-student-000000000003', 'usr-e2e-admin-0000000000001'
    );

    -- Step 8: Parent of rule_scores
    DELETE FROM scoring_criteria WHERE id IN (
        'crit-e2e-compilation-01', 'crit-e2e-correctness-02',
        'crit-e2e-structure-03', 'crit-e2e-efficiency-04'
    );

    COMMIT;
END$$
DELIMITER ;

-- LEVEL 2: POST-TEST RUNTIME TEARDOWN [STATUS: BLOCKED / UNSAFE FOR SHARED/PROD ENVIRONMENTS]
-- Reason for BLOCKED status:
--   The current database schema lacks run_id and session isolation metadata on dynamic tables
--   (e.g., dynamically generated exams and submissions). Heuristics such as 'creator_id' and
--   'title LIKE "Class-Aware Exam %"' cannot mathematically guarantee isolation from parallel,
--   historical, or user-created records sharing the same pattern.
-- Safe recommendation:
--   Do NOT execute Level 2 runtime teardown in shared environments.
--   For repeatable test runs, use ephemeral/isolated database containers or restore from
--   a clean baseline migration snapshot.
/*
-- Step 2.1: Dynamic plagiarism evidence & similarity records generated for Exam A
DELETE FROM plagiarism_evidence WHERE similarity_matrix_id IN (
    SELECT id FROM similarity_matrix WHERE exam_id = 'exm-e2e-exam-a-00000001'
);
DELETE FROM similarity_matrix WHERE exam_id = 'exm-e2e-exam-a-00000001';

-- Step 2.2: Dynamic test submissions generated by Student A & B in core-flows
DELETE FROM grade_audit_logs WHERE submission_id IN (SELECT id FROM submissions WHERE exam_id = 'exm-e2e-exam-a-00000001');
DELETE FROM plagiarism_appeals WHERE submission_id IN (SELECT id FROM submissions WHERE exam_id = 'exm-e2e-exam-a-00000001');
DELETE FROM rule_scores WHERE submission_id IN (SELECT id FROM submissions WHERE exam_id = 'exm-e2e-exam-a-00000001');
DELETE FROM ast_fingerprints WHERE submission_id IN (SELECT id FROM submissions WHERE exam_id = 'exm-e2e-exam-a-00000001');
DELETE FROM submission_test_results WHERE submission_id IN (SELECT id FROM submissions WHERE exam_id = 'exm-e2e-exam-a-00000001');
DELETE FROM submissions WHERE exam_id = 'exm-e2e-exam-a-00000001';

-- Step 2.3: Dynamic exams generated by class-aware-exams (created by E2E Teacher A)
-- Cleans all dependent children in reverse topological order before deleting exams
DELETE FROM plagiarism_evidence WHERE similarity_matrix_id IN (
    SELECT id FROM similarity_matrix WHERE exam_id IN (
        SELECT id FROM exams WHERE creator_id = 'usr-e2e-teacher-000000000001' AND title LIKE 'Class-Aware Exam %'
    )
);
DELETE FROM similarity_matrix WHERE exam_id IN (
    SELECT id FROM exams WHERE creator_id = 'usr-e2e-teacher-000000000001' AND title LIKE 'Class-Aware Exam %'
);
DELETE FROM grade_audit_logs WHERE submission_id IN (
    SELECT id FROM submissions WHERE exam_id IN (
        SELECT id FROM exams WHERE creator_id = 'usr-e2e-teacher-000000000001' AND title LIKE 'Class-Aware Exam %'
    )
);
DELETE FROM plagiarism_appeals WHERE submission_id IN (
    SELECT id FROM submissions WHERE exam_id IN (
        SELECT id FROM exams WHERE creator_id = 'usr-e2e-teacher-000000000001' AND title LIKE 'Class-Aware Exam %'
    )
);
DELETE FROM rule_scores WHERE submission_id IN (
    SELECT id FROM submissions WHERE exam_id IN (
        SELECT id FROM exams WHERE creator_id = 'usr-e2e-teacher-000000000001' AND title LIKE 'Class-Aware Exam %'
    )
);
DELETE FROM ast_fingerprints WHERE submission_id IN (
    SELECT id FROM submissions WHERE exam_id IN (
        SELECT id FROM exams WHERE creator_id = 'usr-e2e-teacher-000000000001' AND title LIKE 'Class-Aware Exam %'
    )
);
DELETE FROM submission_test_results WHERE submission_id IN (
    SELECT id FROM submissions WHERE exam_id IN (
        SELECT id FROM exams WHERE creator_id = 'usr-e2e-teacher-000000000001' AND title LIKE 'Class-Aware Exam %'
    )
);
DELETE FROM submissions WHERE exam_id IN (
    SELECT id FROM exams WHERE creator_id = 'usr-e2e-teacher-000000000001' AND title LIKE 'Class-Aware Exam %'
);
DELETE FROM test_cases WHERE exam_id IN (
    SELECT id FROM exams WHERE creator_id = 'usr-e2e-teacher-000000000001' AND title LIKE 'Class-Aware Exam %'
);
DELETE FROM exam_rubric_items WHERE exam_id IN (
    SELECT id FROM exams WHERE creator_id = 'usr-e2e-teacher-000000000001' AND title LIKE 'Class-Aware Exam %'
);
DELETE FROM exam_matrix_coordination WHERE exam_id IN (
    SELECT id FROM exams WHERE creator_id = 'usr-e2e-teacher-000000000001' AND title LIKE 'Class-Aware Exam %'
);
DELETE FROM exam_classes WHERE exam_id IN (
    SELECT id FROM exams WHERE creator_id = 'usr-e2e-teacher-000000000001' AND title LIKE 'Class-Aware Exam %'
);
DELETE FROM exams WHERE creator_id = 'usr-e2e-teacher-000000000001' AND title LIKE 'Class-Aware Exam %';

-- Step 2.4: Execute Level 1 Seed Rollback to restore baseline
*/
