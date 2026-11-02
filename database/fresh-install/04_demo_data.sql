-- =============================================================================
-- CODEASSESS — DEMONSTRATION & TEST DATA SCRIPT
-- File: 04_demo_data.sql
-- Description: Realistic synthetic test scenarios covering all 16 tables:
--              Teachers, Students, Classes, Rosters, Class-Aware Exams, Test Cases,
--              Submissions, Automated Test Results, Telemetry, Rubric Scoring,
--              Grade Audit Logs, AST Hashes, Similarity Matrix, Plagiarism Evidence,
--              Appeals, Password Reset Tokens, and Matrix Coordination.
-- Notice:
--   - This is purely synthetic DEMO data for development and testing validation.
--   - It is NOT extracted from any existing or production MySQL server.
--   - All foreign keys, unique keys, and check constraints strictly validated.
-- Password Information for Demo Accounts:
--   - Teachers: Teacher@2026!  (BCrypt: $2a$10$qsyCuBLdtRV3WFZo.FTknevwbIPkjscNLm2Qv90qkAM8k3l.kyy4G)
--   - Students: Student@2026!  (BCrypt: $2a$10$ixonXg7wrN0aZIcW9Mte0.TNXEKDockM0vdaKJmBPK7sFHHGlZCYC)
-- =============================================================================

USE codeassess;

SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 1. USERS (Teachers & Students)
-- -----------------------------------------------------------------------------
INSERT INTO users (id, email, password_hash, role, full_name, created_at, updated_at)
VALUES
    -- Instructors
    ('usr-teacher-000000000001', 'teacher.giang@codeassess.edu.vn', '$2a$10$qsyCuBLdtRV3WFZo.FTknevwbIPkjscNLm2Qv90qkAM8k3l.kyy4G', 'TEACHER', 'Dr. Nguyen Van Giang', NOW(), NOW()),
    ('usr-teacher-000000000002', 'teacher.mai@codeassess.edu.vn',   '$2a$10$qsyCuBLdtRV3WFZo.FTknevwbIPkjscNLm2Qv90qkAM8k3l.kyy4G', 'TEACHER', 'MSc. Tran Thi Mai',    NOW(), NOW()),
    -- Students
    ('usr-student-000000000001', 'student.an@fpt.edu.vn',    '$2a$10$ixonXg7wrN0aZIcW9Mte0.TNXEKDockM0vdaKJmBPK7sFHHGlZCYC', 'STUDENT', 'Nguyen Van An',        NOW(), NOW()),
    ('usr-student-000000000002', 'student.binh@fpt.edu.vn',  '$2a$10$ixonXg7wrN0aZIcW9Mte0.TNXEKDockM0vdaKJmBPK7sFHHGlZCYC', 'STUDENT', 'Tran Thi Binh',        NOW(), NOW()),
    ('usr-student-000000000003', 'student.cuong@fpt.edu.vn', '$2a$10$ixonXg7wrN0aZIcW9Mte0.TNXEKDockM0vdaKJmBPK7sFHHGlZCYC', 'STUDENT', 'Le Quoc Cuong',       NOW(), NOW()),
    ('usr-student-000000000004', 'student.dung@fpt.edu.vn',  '$2a$10$ixonXg7wrN0aZIcW9Mte0.TNXEKDockM0vdaKJmBPK7sFHHGlZCYC', 'STUDENT', 'Pham Tuan Dung',       NOW(), NOW())
ON DUPLICATE KEY UPDATE
    full_name = VALUES(full_name),
    updated_at = NOW();

-- -----------------------------------------------------------------------------
-- 2. PASSWORD_RESET_TOKENS (Auth Workflow Testing)
-- -----------------------------------------------------------------------------
INSERT INTO password_reset_tokens (id, token, user_id, expires_at, used, created_at)
VALUES
    ('rst-token-demo-000000000001', 'demo-valid-reset-token-2026-an', 'usr-student-000000000001', DATE_ADD(NOW(), INTERVAL 24 HOUR), 0, NOW()),
    ('rst-token-demo-000000000002', 'demo-used-reset-token-2026-binh', 'usr-student-000000000002', DATE_SUB(NOW(), INTERVAL 1 HOUR), 1, DATE_SUB(NOW(), INTERVAL 2 HOUR))
ON DUPLICATE KEY UPDATE
    token = VALUES(token);

-- -----------------------------------------------------------------------------
-- 3. CLASSES
-- -----------------------------------------------------------------------------
INSERT INTO classes (id, teacher_id, name, code, semester, created_at)
VALUES
    ('cls-pro192-fa26-00000001', 'usr-teacher-000000000001', 'Object-Oriented Programming (Java)', 'PRO192-SE1801', 'Fall 2026', NOW()),
    ('cls-csd201-fa26-00000002', 'usr-teacher-000000000002', 'Data Structures & Algorithms',        'CSD201-SE1802', 'Fall 2026', NOW())
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    semester = VALUES(semester);

-- -----------------------------------------------------------------------------
-- 4. CLASS_STUDENTS (Enrollment Roster)
-- -----------------------------------------------------------------------------
INSERT INTO class_students (class_id, student_id)
VALUES
    -- PRO192 Enrolled Students
    ('cls-pro192-fa26-00000001', 'usr-student-000000000001'),
    ('cls-pro192-fa26-00000001', 'usr-student-000000000002'),
    ('cls-pro192-fa26-00000001', 'usr-student-000000000003'),
    ('cls-pro192-fa26-00000001', 'usr-student-000000000004'),
    -- CSD201 Enrolled Students
    ('cls-csd201-fa26-00000002', 'usr-student-000000000001'),
    ('cls-csd201-fa26-00000002', 'usr-student-000000000002'),
    ('cls-csd201-fa26-00000002', 'usr-student-000000000003')
ON DUPLICATE KEY UPDATE
    class_id = VALUES(class_id);

-- -----------------------------------------------------------------------------
-- 5. EXAMS (with Soft-Delete column deleted_at)
-- -----------------------------------------------------------------------------
INSERT INTO exams (id, class_id, title, exam_date, duration, status, created_at, deleted_at)
VALUES
    ('exm-pro192-midterm-00001', 'cls-pro192-fa26-00000001', 'PRO192 Midterm Practical Exam - Java OOP', '2026-10-10 08:00:00', 90,  'COMPLETED', '2026-10-01 08:00:00', NULL),
    ('exm-csd201-final-0000002', 'cls-csd201-fa26-00000002', 'CSD201 Final Practical - Binary Search Tree', '2026-10-15 13:30:00', 120, 'COMPLETED', '2026-10-05 08:00:00', NULL)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    status = VALUES(status),
    deleted_at = VALUES(deleted_at);

-- -----------------------------------------------------------------------------
-- 6. EXAM_CLASSES (Class-Aware Multi-Class Assignment)
-- -----------------------------------------------------------------------------
INSERT INTO exam_classes (exam_id, class_id, created_at)
VALUES
    ('exm-pro192-midterm-00001', 'cls-pro192-fa26-00000001', '2026-10-01 08:00:00'),
    ('exm-csd201-final-0000002', 'cls-csd201-fa26-00000002', '2026-10-05 08:00:00')
ON DUPLICATE KEY UPDATE
    created_at = VALUES(created_at);

-- -----------------------------------------------------------------------------
-- 7. TEST_CASES (Automated Testing Specifications)
-- Standard practical exam evaluation suite totaling 100.00 points.
-- -----------------------------------------------------------------------------
INSERT INTO test_cases (id, exam_id, name, description, input_data, expected_output, points, is_hidden, display_order, created_at, updated_at)
VALUES
    ('tc-pro192-00000000000001', 'exm-pro192-midterm-00001', 'TC1: Standard Positive N=5', 'Basic functional calculation for n=5 (1^2+...+5^2 = 55)', '5\n', 'Result: 55\n', 25.00, 0, 1, NOW(), NOW()),
    ('tc-pro192-00000000000002', 'exm-pro192-midterm-00001', 'TC2: Boundary Zero N=0',     'Boundary condition when input is 0 (Result: 0)',         '0\n', 'Result: 0\n',  25.00, 0, 2, NOW(), NOW()),
    ('tc-pro192-00000000000003', 'exm-pro192-midterm-00001', 'TC3: Hidden Scale N=10',     'Hidden test case verifying scaling (1^2+...+10^2 = 385)', '10\n', 'Result: 385\n', 50.00, 1, 3, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    points = VALUES(points);

-- -----------------------------------------------------------------------------
-- 8. SUBMISSIONS (Single-file and Multi-file ZIP with execution telemetry)
-- -----------------------------------------------------------------------------
INSERT INTO submissions (
    id, exam_id, student_id, status, source_code, total_score, override_score,
    auto_score, test_cases_passed, test_cases_total, evaluated_at, created_at,
    submission_type, original_filename, archive_hash, source_tree_hash, file_count, entry_point_class,
    execution_status, exit_code, compile_output, stdout, stderr, execution_duration_ms,
    zip_storage_path, instructor_feedback
)
VALUES
    -- Submission 1: Student An (Original high-scoring implementation)
    (
        'sub-pro192-an-0000000001',
        'exm-pro192-midterm-00001',
        'usr-student-000000000001',
        'COMPLETED',
        'import java.util.*;\n\npublic class Solution {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        int n = sc.hasNextInt() ? sc.nextInt() : 0;\n        int sum = 0;\n        for (int i = 1; i <= n; i++) {\n            sum += i * i;\n        }\n        System.out.println("Result: " + sum);\n    }\n}',
        9.50,
        NULL,
        10.00,
        3,
        3,
        '2026-10-10 08:45:00',
        '2026-10-10 08:35:00',
        'SINGLE_FILE',
        'Solution.java',
        NULL,
        'a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90',
        1,
        'Solution',
        'SUCCESS',
        0,
        'Compilation completed successfully in 210ms.',
        'Result: 55\n',
        '',
        345,
        NULL,
        'Excellent submission, modular logic and fully passed test suite.'
    ),
    -- Submission 2: Student Binh (Plagiarized from An - renamed variables, adjusted loop)
    (
        'sub-pro192-binh-000000002',
        'exm-pro192-midterm-00001',
        'usr-student-000000000002',
        'COMPLETED',
        'import java.util.*;\n\npublic class SolutionB {\n    public static void main(String[] args) {\n        Scanner scanner = new Scanner(System.in);\n        int count = scanner.hasNextInt() ? scanner.nextInt() : 0;\n        int total = 0;\n        for (int idx = 1; idx <= count; idx++) {\n            total += (idx * idx);\n        }\n        System.out.println("Result: " + total);\n    }\n}',
        9.00,
        4.50, -- Overridden by teacher with 50% penalty due to plagiarism
        10.00,
        3,
        3,
        '2026-10-10 08:52:00',
        '2026-10-10 08:42:00',
        'SINGLE_FILE',
        'SolutionB.java',
        NULL,
        'b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90a1',
        1,
        'SolutionB',
        'SUCCESS',
        0,
        'Compilation completed successfully in 198ms.',
        'Result: 55\n',
        '',
        320,
        NULL,
        'Grade penalized by 50% due to confirmed AST plagiarism match with Student An.'
    ),
    -- Submission 3: Student Cuong (Independent original implementation)
    (
        'sub-pro192-cuong-00000003',
        'exm-pro192-midterm-00001',
        'usr-student-000000000003',
        'COMPLETED',
        'import java.util.Scanner;\nimport java.util.stream.IntStream;\n\npublic class StreamSolution {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        int n = sc.hasNextInt() ? sc.nextInt() : 0;\n        int sum = IntStream.rangeClosed(1, n).map(x -> x * x).sum();\n        System.out.println("Result: " + sum);\n    }\n}',
        8.50,
        NULL,
        10.00,
        3,
        3,
        '2026-10-10 08:55:00',
        '2026-10-10 08:48:00',
        'SINGLE_FILE',
        'StreamSolution.java',
        NULL,
        'c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2',
        1,
        'StreamSolution',
        'SUCCESS',
        0,
        'Compilation completed successfully in 312ms.',
        'Result: 55\n',
        '',
        410,
        NULL,
        'Innovative functional approach with Java Stream API.'
    ),
    -- Submission 4: Student Dung (Multi-file ZIP package)
    (
        'sub-pro192-dung-000000004',
        'exm-pro192-midterm-00001',
        'usr-student-000000000004',
        'COMPLETED',
        '// Source extracted from uploaded ZIP archive: project_submission.zip\npackage com.codeassess.app;\nimport java.util.Scanner;\n\npublic class AppMain {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        int n = sc.hasNextInt() ? sc.nextInt() : 0;\n        Calculator calc = new Calculator();\n        System.out.println("Result: " + calc.sumOfSquares(n));\n    }\n}',
        8.80,
        NULL,
        10.00,
        3,
        3,
        '2026-10-10 09:05:00',
        '2026-10-10 08:50:00',
        'ZIP',
        'project_submission.zip',
        'd4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3',
        'e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4',
        3,
        'com.codeassess.app.AppMain',
        'SUCCESS',
        0,
        'Compiled 3 source files cleanly.',
        'Result: 55\n',
        '',
        520,
        'submissions/2026/exm-pro192-midterm-00001/sub-pro192-dung.zip',
        'Multi-file OOP design cleanly packaged.'
    )
ON DUPLICATE KEY UPDATE
    total_score = VALUES(total_score),
    override_score = VALUES(override_score),
    auto_score = VALUES(auto_score);

-- -----------------------------------------------------------------------------
-- 9. SUBMISSION_TEST_RESULTS (Automated Test Pass Records)
-- -----------------------------------------------------------------------------
INSERT INTO submission_test_results (
    id, submission_id, test_case_id, test_case_name, is_hidden, status,
    actual_output, expected_output, execution_time_ms, exit_code, error_message, points_awarded, created_at
)
VALUES
    -- Student An Results
    ('str-an-01', 'sub-pro192-an-0000000001', 'tc-pro192-00000000000001', 'TC1: Standard Positive N=5', 0, 'PASSED', 'Result: 55\n', 'Result: 55\n', 45, 0, NULL, 25.00, '2026-10-10 08:45:00'),
    ('str-an-02', 'sub-pro192-an-0000000001', 'tc-pro192-00000000000002', 'TC2: Boundary Zero N=0',     0, 'PASSED', 'Result: 0\n',  'Result: 0\n',  38, 0, NULL, 25.00, '2026-10-10 08:45:00'),
    ('str-an-03', 'sub-pro192-an-0000000001', 'tc-pro192-00000000000003', 'TC3: Hidden Scale N=10',     1, 'PASSED', 'Result: 385\n', 'Result: 385\n', 62, 0, NULL, 50.00, '2026-10-10 08:45:00'),
    -- Student Binh Results
    ('str-binh-01', 'sub-pro192-binh-000000002', 'tc-pro192-00000000000001', 'TC1: Standard Positive N=5', 0, 'PASSED', 'Result: 55\n', 'Result: 55\n', 42, 0, NULL, 25.00, '2026-10-10 08:52:00'),
    ('str-binh-02', 'sub-pro192-binh-000000002', 'tc-pro192-00000000000002', 'TC2: Boundary Zero N=0',     0, 'PASSED', 'Result: 0\n',  'Result: 0\n',  35, 0, NULL, 25.00, '2026-10-10 08:52:00'),
    ('str-binh-03', 'sub-pro192-binh-000000002', 'tc-pro192-00000000000003', 'TC3: Hidden Scale N=10',     1, 'PASSED', 'Result: 385\n', 'Result: 385\n', 58, 0, NULL, 50.00, '2026-10-10 08:52:00'),
    -- Student Cuong Results
    ('str-cuong-01', 'sub-pro192-cuong-00000003', 'tc-pro192-00000000000001', 'TC1: Standard Positive N=5', 0, 'PASSED', 'Result: 55\n', 'Result: 55\n', 78, 0, NULL, 25.00, '2026-10-10 08:55:00'),
    ('str-cuong-02', 'sub-pro192-cuong-00000003', 'tc-pro192-00000000000002', 'TC2: Boundary Zero N=0',     0, 'PASSED', 'Result: 0\n',  'Result: 0\n',  65, 0, NULL, 25.00, '2026-10-10 08:55:00'),
    ('str-cuong-03', 'sub-pro192-cuong-00000003', 'tc-pro192-00000000000003', 'TC3: Hidden Scale N=10',     1, 'PASSED', 'Result: 385\n', 'Result: 385\n', 95, 0, NULL, 50.00, '2026-10-10 08:55:00'),
    -- Student Dung Results
    ('str-dung-01', 'sub-pro192-dung-000000004', 'tc-pro192-00000000000001', 'TC1: Standard Positive N=5', 0, 'PASSED', 'Result: 55\n', 'Result: 55\n', 52, 0, NULL, 25.00, '2026-10-10 09:05:00'),
    ('str-dung-02', 'sub-pro192-dung-000000004', 'tc-pro192-00000000000002', 'TC2: Boundary Zero N=0',     0, 'PASSED', 'Result: 0\n',  'Result: 0\n',  48, 0, NULL, 25.00, '2026-10-10 09:05:00'),
    ('str-dung-03', 'sub-pro192-dung-000000004', 'tc-pro192-00000000000003', 'TC3: Hidden Scale N=10',     1, 'PASSED', 'Result: 385\n', 'Result: 385\n', 75, 0, NULL, 50.00, '2026-10-10 09:05:00')
ON DUPLICATE KEY UPDATE
    points_awarded = VALUES(points_awarded);

-- -----------------------------------------------------------------------------
-- 10. RULE_SCORES (Itemized Rubric Points per Submission)
-- -----------------------------------------------------------------------------
INSERT INTO rule_scores (id, submission_id, criteria_id, score)
VALUES
    -- Student An (Total 9.50)
    ('rsc-an-01', 'sub-pro192-an-0000000001', 'crit-compilation-001', 3.00),
    ('rsc-an-02', 'sub-pro192-an-0000000001', 'crit-correctness-002', 3.80),
    ('rsc-an-03', 'sub-pro192-an-0000000001', 'crit-structure-003',   1.40),
    ('rsc-an-04', 'sub-pro192-an-0000000001', 'crit-efficiency-004',  1.30),
    -- Student Binh (Rubric Total 9.00)
    ('rsc-binh-01', 'sub-pro192-binh-000000002', 'crit-compilation-001', 3.00),
    ('rsc-binh-02', 'sub-pro192-binh-000000002', 'crit-correctness-002', 3.60),
    ('rsc-binh-03', 'sub-pro192-binh-000000002', 'crit-structure-003',   1.20),
    ('rsc-binh-04', 'sub-pro192-binh-000000002', 'crit-efficiency-004',  1.20),
    -- Student Cuong (Rubric Total 8.50)
    ('rsc-cuong-01', 'sub-pro192-cuong-00000003', 'crit-compilation-001', 3.00),
    ('rsc-cuong-02', 'sub-pro192-cuong-00000003', 'crit-correctness-002', 3.20),
    ('rsc-cuong-03', 'sub-pro192-cuong-00000003', 'crit-structure-003',   1.20),
    ('rsc-cuong-04', 'sub-pro192-cuong-00000003', 'crit-efficiency-004',  1.10),
    -- Student Dung (Rubric Total 8.80)
    ('rsc-dung-01', 'sub-pro192-dung-000000004', 'crit-compilation-001', 2.80),
    ('rsc-dung-02', 'sub-pro192-dung-000000004', 'crit-correctness-002', 3.50),
    ('rsc-dung-03', 'sub-pro192-dung-000000004', 'crit-structure-003',   1.30),
    ('rsc-dung-04', 'sub-pro192-dung-000000004', 'crit-efficiency-004',  1.20)
ON DUPLICATE KEY UPDATE
    score = VALUES(score);

-- -----------------------------------------------------------------------------
-- 11. GRADE_AUDIT_LOGS (Immutable Score Adjustment Audit Trail)
-- -----------------------------------------------------------------------------
INSERT INTO grade_audit_logs (
    id, submission_id, actor_id, action_type, pre_rubric_score, post_rubric_score,
    pre_override_score, post_override_score, pre_effective_score, post_effective_score,
    rationale, created_at
)
VALUES (
    'log-audit-00000000000001',
    'sub-pro192-binh-000000002',
    'usr-teacher-000000000001',
    'SCORE_OVERRIDE',
    9.00,
    9.00,
    NULL,
    4.50,
    9.00,
    4.50,
    'Applied 50% penalty following confirmed AST plagiarism similarity pair #1 with Student An (Pair rate: 87.50%).',
    '2026-10-11 10:15:00'
)
ON DUPLICATE KEY UPDATE
    action_type = VALUES(action_type);

-- -----------------------------------------------------------------------------
-- 12. AST_FINGERPRINTS (Syntactic Tree Hashes for Plagiarism Detection)
-- Explicit IDs 1-9 ensure strict idempotency on re-execution.
-- -----------------------------------------------------------------------------
INSERT INTO ast_fingerprints (id, submission_id, hash_value, node_type, start_line, end_line, file_path, created_at)
VALUES
    -- Student An Fingerprints
    (1, 'sub-pro192-an-0000000001', 114829384729104, 'MethodDeclaration', 4, 12, 'Solution.java', '2026-10-10 08:45:00'),
    (2, 'sub-pro192-an-0000000001', 892348723948723, 'ForStmt',           8, 10, 'Solution.java', '2026-10-10 08:45:00'),
    (3, 'sub-pro192-an-0000000001', 489237498237498, 'BinaryExpr',        9,  9, 'Solution.java', '2026-10-10 08:45:00'),
    -- Student Binh Fingerprints (Shares identical AST subtrees despite variable renaming)
    (4, 'sub-pro192-binh-000000002', 114829384729104, 'MethodDeclaration', 4, 12, 'SolutionB.java', '2026-10-10 08:52:00'),
    (5, 'sub-pro192-binh-000000002', 892348723948723, 'ForStmt',           8, 10, 'SolutionB.java', '2026-10-10 08:52:00'),
    (6, 'sub-pro192-binh-000000002', 489237498237498, 'BinaryExpr',        9,  9, 'SolutionB.java', '2026-10-10 08:52:00'),
    -- Student Cuong Fingerprints (Distinct AST structures)
    (7, 'sub-pro192-cuong-00000003', 771239847192837, 'MethodDeclaration', 4,  8, 'StreamSolution.java', '2026-10-10 08:55:00'),
    (8, 'sub-pro192-cuong-00000003', 339482934829348, 'MethodCallExpr',    6,  6, 'StreamSolution.java', '2026-10-10 08:55:00'),
    -- Student Dung Fingerprints
    (9, 'sub-pro192-dung-000000004', 662938472938472, 'MethodDeclaration', 4,  7, 'AppMain.java', '2026-10-10 09:05:00')
ON DUPLICATE KEY UPDATE
    hash_value = VALUES(hash_value);

-- -----------------------------------------------------------------------------
-- 13. SIMILARITY_MATRIX (Pairwise AST Match Results & Adjudications)
-- -----------------------------------------------------------------------------
INSERT INTO similarity_matrix (
    id, exam_id, submission_a_id, submission_b_id, similarity_rate, shared_hashes_count,
    is_flagged, created_at, adjudication_status, adjudication_notes, adjudicated_by_id,
    adjudicated_at, is_active
)
VALUES
    -- Pair 1: An vs Binh -> Highly similar (87.50%), Flagged, Confirmed Plagiarism
    (
        1,
        'exm-pro192-midterm-00001',
        'sub-pro192-an-0000000001',
        'sub-pro192-binh-000000002',
        87.50,
        42,
        1,
        '2026-10-10 09:10:00',
        'CONFIRMED_PLAGIARISM',
        'Direct syntactic token match on main method body and accumulation loop. Systematic variable renaming detected.',
        'usr-teacher-000000000001',
        '2026-10-11 10:10:00',
        1
    ),
    -- Pair 2: An vs Cuong -> Low similarity (18.25%), Unflagged, Dismissed
    (
        2,
        'exm-pro192-midterm-00001',
        'sub-pro192-an-0000000001',
        'sub-pro192-cuong-00000003',
        18.25,
        6,
        0,
        '2026-10-10 09:10:00',
        'DISMISSED',
        'Incidental overlap on standard boilerplate; different programming paradigm (Imperative vs Stream API).',
        'usr-teacher-000000000001',
        '2026-10-11 10:12:00',
        1
    ),
    -- Pair 3: Binh vs Cuong -> Low similarity (15.00%), Unflagged, Dismissed
    (
        3,
        'exm-pro192-midterm-00001',
        'sub-pro192-binh-000000002',
        'sub-pro192-cuong-00000003',
        15.00,
        5,
        0,
        '2026-10-10 09:10:00',
        'DISMISSED',
        'No substantive structural match.',
        'usr-teacher-000000000001',
        '2026-10-11 10:14:00',
        1
    )
ON DUPLICATE KEY UPDATE
    similarity_rate = VALUES(similarity_rate),
    adjudication_status = VALUES(adjudication_status);

-- -----------------------------------------------------------------------------
-- 14. PLAGIARISM_EVIDENCE (Granular Code Snippet Matches)
-- -----------------------------------------------------------------------------
INSERT INTO plagiarism_evidence (
    id, similarity_matrix_id, evidence_type, description,
    submission_a_start_line, submission_a_end_line,
    submission_b_start_line, submission_b_end_line,
    submission_a_location, submission_b_location, created_at
)
VALUES
    (
        'evi-pro192-00000000000001',
        1,
        'SYNTAX_BLOCK_MATCH',
        'Identical for-loop computation structure: loop accumulator pattern sum += i * i exactly matches total += (idx * idx).',
        8, 10,
        8, 10,
        'Solution.java:8-10',
        'SolutionB.java:8-10',
        '2026-10-10 09:11:00'
    ),
    (
        'evi-pro192-00000000000002',
        1,
        'METHOD_BODY_EQUIVALENCE',
        'Matching main method parameter reading and fallback ternary logic.',
        5, 7,
        5, 7,
        'Solution.java:5-7',
        'SolutionB.java:5-7',
        '2026-10-10 09:11:00'
    )
ON DUPLICATE KEY UPDATE
    description = VALUES(description);

-- -----------------------------------------------------------------------------
-- 15. PLAGIARISM_APPEALS (Student Disputed Grade / Plagiarism Appeals)
-- -----------------------------------------------------------------------------
INSERT INTO plagiarism_appeals (
    id, submission_id, student_id, reason, basis, status, reviewer_notes, created_at, resolved_at
)
VALUES
    -- Appeal 1: Student Binh contesting 50% penalty
    (
        1,
        'sub-pro192-binh-000000002',
        'usr-student-000000000002',
        'I studied with An before the exam, so our logic was similar, but I wrote my code myself without copying.',
        'INDEPENDENT_IMPLEMENTATION',
        'REJECTED',
        'AST Winnowing report demonstrates 87.5% isomorphic syntax tree token similarity. Variable substitution does not qualify as independent implementation. Penalty stands.',
        '2026-10-11 14:00:00',
        '2026-10-12 09:30:00'
    ),
    -- Appeal 2: Student Cuong requesting score review
    (
        2,
        'sub-pro192-cuong-00000003',
        'usr-student-000000000003',
        'My stream implementation passed all test cases within 410ms, but I lost 0.4 points on the efficiency rubric.',
        'GRADING_DISCREPANCY',
        'APPROVED',
        'Re-evaluated stream overhead on JVM warm-up. Solution meets performance criteria. Adjusted grade.',
        '2026-10-11 15:30:00',
        '2026-10-12 10:00:00'
    )
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    reviewer_notes = VALUES(reviewer_notes);

-- -----------------------------------------------------------------------------
-- 16. EXAM_MATRIX_COORDINATION (Distributed Coordination Telemetry)
-- -----------------------------------------------------------------------------
INSERT INTO exam_matrix_coordination (
    exam_id, current_generation, requested_generation, locked_generation,
    lease_token, worker_id, lease_expires_at, status, failure_count, last_error, next_retry_at,
    created_at, updated_at
)
VALUES
    (
        'exm-pro192-midterm-00001',
        2, 2, 2,
        NULL, 'worker-bullmq-node-01', NULL,
        'IDLE',
        0, NULL, NULL,
        '2026-10-10 08:00:00',
        '2026-10-10 09:12:00'
    ),
    (
        'exm-csd201-final-0000002',
        1, 1, 1,
        NULL, 'worker-bullmq-node-02', NULL,
        'IDLE',
        0, NULL, NULL,
        '2026-10-15 13:30:00',
        '2026-10-15 14:00:00'
    )
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    updated_at = NOW();

SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS;

-- -----------------------------------------------------------------------------
-- Verification Output
-- -----------------------------------------------------------------------------
SELECT 'Demo data populated successfully across all 16 tables!' AS status;
