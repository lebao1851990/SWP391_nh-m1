-- =============================================================================
-- CODEASSESS — DATABASE VERIFICATION SCRIPT (READ-ONLY)
-- File: 05_verify_database.sql
-- Description: Comprehensive read-only audit verifying table presence across all 16 tables,
--              record counts, referential integrity, foreign keys, and business logic state.
-- Safety: Pure SELECT queries only. Absolutely NO INSERT, UPDATE, or DELETE statements.
-- =============================================================================

USE codeassess;

-- -----------------------------------------------------------------------------
-- 1. DATABASE METADATA & ENCODING CHECK
-- -----------------------------------------------------------------------------
SELECT
    SCHEMA_NAME AS 'Database',
    DEFAULT_CHARACTER_SET_NAME AS 'Charset (Expected: utf8mb4)',
    DEFAULT_COLLATION_NAME AS 'Collation (Expected: utf8mb4_unicode_ci)'
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME = 'codeassess';

-- -----------------------------------------------------------------------------
-- 2. TABLE PRESENCE AUDIT (Expected: 17 physical tables: 16 entities + exam_classes join table)
-- -----------------------------------------------------------------------------
SELECT
    TABLE_NAME AS 'Table Name',
    ENGINE AS 'Engine',
    TABLE_ROWS AS 'Estimated Rows',
    CREATE_TIME AS 'Created Time'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'codeassess'
ORDER BY TABLE_NAME ASC;

SELECT COUNT(*) AS 'Total Tables in Database (Expected: >= 17)'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'codeassess';

-- Explicit Checklist for each of the 17 required physical tables
SELECT
    req.expected_table AS 'Required Physical Table',
    CASE WHEN ist.TABLE_NAME IS NOT NULL THEN 'PRESENT' ELSE 'MISSING' END AS 'Status',
    COALESCE(ist.ENGINE, 'N/A') AS 'Engine'
FROM (
    SELECT 'users' AS expected_table UNION ALL
    SELECT 'password_reset_tokens' UNION ALL
    SELECT 'classes' UNION ALL
    SELECT 'class_students' UNION ALL
    SELECT 'exams' UNION ALL
    SELECT 'exam_classes' UNION ALL
    SELECT 'test_cases' UNION ALL
    SELECT 'submissions' UNION ALL
    SELECT 'submission_test_results' UNION ALL
    SELECT 'ast_fingerprints' UNION ALL
    SELECT 'similarity_matrix' UNION ALL
    SELECT 'plagiarism_evidence' UNION ALL
    SELECT 'scoring_criteria' UNION ALL
    SELECT 'rule_scores' UNION ALL
    SELECT 'grade_audit_logs' UNION ALL
    SELECT 'plagiarism_appeals' UNION ALL
    SELECT 'exam_matrix_coordination'
) req
LEFT JOIN information_schema.TABLES ist
    ON ist.TABLE_SCHEMA = 'codeassess' AND ist.TABLE_NAME = req.expected_table
ORDER BY req.expected_table ASC;

-- -----------------------------------------------------------------------------
-- 3. EXACT ROW COUNTS PER TABLE (ALL 17 DATABASE TABLES)
-- -----------------------------------------------------------------------------
SELECT 'users' AS table_name, COUNT(*) AS row_count FROM users
UNION ALL
SELECT 'password_reset_tokens', COUNT(*) FROM password_reset_tokens
UNION ALL
SELECT 'classes', COUNT(*) FROM classes
UNION ALL
SELECT 'class_students', COUNT(*) FROM class_students
UNION ALL
SELECT 'exams', COUNT(*) FROM exams
UNION ALL
SELECT 'exam_classes', COUNT(*) FROM exam_classes
UNION ALL
SELECT 'test_cases', COUNT(*) FROM test_cases
UNION ALL
SELECT 'submissions', COUNT(*) FROM submissions
UNION ALL
SELECT 'submission_test_results', COUNT(*) FROM submission_test_results
UNION ALL
SELECT 'ast_fingerprints', COUNT(*) FROM ast_fingerprints
UNION ALL
SELECT 'similarity_matrix', COUNT(*) FROM similarity_matrix
UNION ALL
SELECT 'plagiarism_evidence', COUNT(*) FROM plagiarism_evidence
UNION ALL
SELECT 'scoring_criteria', COUNT(*) FROM scoring_criteria
UNION ALL
SELECT 'rule_scores', COUNT(*) FROM rule_scores
UNION ALL
SELECT 'grade_audit_logs', COUNT(*) FROM grade_audit_logs
UNION ALL
SELECT 'plagiarism_appeals', COUNT(*) FROM plagiarism_appeals
UNION ALL
SELECT 'exam_matrix_coordination', COUNT(*) FROM exam_matrix_coordination;

-- -----------------------------------------------------------------------------
-- 4. USER IDENTITY & ROLE AUDIT
-- -----------------------------------------------------------------------------
SELECT
    role AS 'Role',
    COUNT(*) AS 'Count',
    GROUP_CONCAT(email ORDER BY email SEPARATOR ', ') AS 'Accounts'
FROM users
GROUP BY role;

-- -----------------------------------------------------------------------------
-- 5. REFERENTIAL INTEGRITY & FOREIGN KEY AUDIT (Should return 0 for all checks)
-- -----------------------------------------------------------------------------
SELECT 'Orphaned classes (invalid teacher_id)' AS integrity_check, COUNT(*) AS violations
FROM classes c LEFT JOIN users u ON c.teacher_id = u.id WHERE u.id IS NULL
UNION ALL
SELECT 'Orphaned class_students (invalid class_id)', COUNT(*)
FROM class_students cs LEFT JOIN classes c ON cs.class_id = c.id WHERE c.id IS NULL
UNION ALL
SELECT 'Orphaned class_students (invalid student_id)', COUNT(*)
FROM class_students cs LEFT JOIN users u ON cs.student_id = u.id WHERE u.id IS NULL
UNION ALL
SELECT 'Orphaned exams (invalid class_id)', COUNT(*)
FROM exams e LEFT JOIN classes c ON e.class_id = c.id WHERE e.class_id IS NOT NULL AND c.id IS NULL
UNION ALL
SELECT 'Orphaned exam_classes (invalid exam_id)', COUNT(*)
FROM exam_classes ec LEFT JOIN exams e ON ec.exam_id = e.id WHERE e.id IS NULL
UNION ALL
SELECT 'Orphaned exam_classes (invalid class_id)', COUNT(*)
FROM exam_classes ec LEFT JOIN classes c ON ec.class_id = c.id WHERE c.id IS NULL
UNION ALL
SELECT 'Orphaned test_cases (invalid exam_id)', COUNT(*)
FROM test_cases tc LEFT JOIN exams e ON tc.exam_id = e.id WHERE e.id IS NULL
UNION ALL
SELECT 'Orphaned submissions (invalid exam_id)', COUNT(*)
FROM submissions s LEFT JOIN exams e ON s.exam_id = e.id WHERE e.id IS NULL
UNION ALL
SELECT 'Orphaned submissions (invalid student_id)', COUNT(*)
FROM submissions s LEFT JOIN users u ON s.student_id = u.id WHERE u.id IS NULL
UNION ALL
SELECT 'Orphaned submission_test_results (invalid submission_id)', COUNT(*)
FROM submission_test_results str LEFT JOIN submissions s ON str.submission_id = s.id WHERE s.id IS NULL
UNION ALL
SELECT 'Orphaned submission_test_results (invalid test_case_id)', COUNT(*)
FROM submission_test_results str LEFT JOIN test_cases tc ON str.test_case_id = tc.id WHERE str.test_case_id IS NOT NULL AND tc.id IS NULL
UNION ALL
SELECT 'Orphaned password_reset_tokens (invalid user_id)', COUNT(*)
FROM password_reset_tokens prt LEFT JOIN users u ON prt.user_id = u.id WHERE u.id IS NULL
UNION ALL
SELECT 'Orphaned ast_fingerprints (invalid submission_id)', COUNT(*)
FROM ast_fingerprints af LEFT JOIN submissions s ON af.submission_id = s.id WHERE s.id IS NULL
UNION ALL
SELECT 'Orphaned similarity_matrix (invalid submission_a_id)', COUNT(*)
FROM similarity_matrix sm LEFT JOIN submissions s ON sm.submission_a_id = s.id WHERE s.id IS NULL
UNION ALL
SELECT 'Orphaned similarity_matrix (invalid submission_b_id)', COUNT(*)
FROM similarity_matrix sm LEFT JOIN submissions s ON sm.submission_b_id = s.id WHERE s.id IS NULL
UNION ALL
SELECT 'Orphaned similarity_matrix (invalid exam_id)', COUNT(*)
FROM similarity_matrix sm LEFT JOIN exams e ON sm.exam_id = e.id WHERE e.id IS NULL
UNION ALL
SELECT 'Orphaned plagiarism_evidence (invalid similarity_matrix_id)', COUNT(*)
FROM plagiarism_evidence pe LEFT JOIN similarity_matrix sm ON pe.similarity_matrix_id = sm.id WHERE sm.id IS NULL
UNION ALL
SELECT 'Orphaned rule_scores (invalid submission_id)', COUNT(*)
FROM rule_scores rs LEFT JOIN submissions s ON rs.submission_id = s.id WHERE s.id IS NULL
UNION ALL
SELECT 'Orphaned rule_scores (invalid criteria_id)', COUNT(*)
FROM rule_scores rs LEFT JOIN scoring_criteria sc ON rs.criteria_id = sc.id WHERE sc.id IS NULL
UNION ALL
SELECT 'Orphaned grade_audit_logs (invalid submission_id)', COUNT(*)
FROM grade_audit_logs gal LEFT JOIN submissions s ON gal.submission_id = s.id WHERE s.id IS NULL
UNION ALL
SELECT 'Orphaned grade_audit_logs (invalid actor_id)', COUNT(*)
FROM grade_audit_logs gal LEFT JOIN users u ON gal.actor_id = u.id WHERE u.id IS NULL
UNION ALL
SELECT 'Orphaned plagiarism_appeals (invalid submission_id)', COUNT(*)
FROM plagiarism_appeals pa LEFT JOIN submissions s ON pa.submission_id = s.id WHERE s.id IS NULL
UNION ALL
SELECT 'Orphaned plagiarism_appeals (invalid student_id)', COUNT(*)
FROM plagiarism_appeals pa LEFT JOIN users u ON pa.student_id = u.id WHERE u.id IS NULL
UNION ALL
SELECT 'Orphaned exam_matrix_coordination (invalid exam_id)', COUNT(*)
FROM exam_matrix_coordination emc LEFT JOIN exams e ON emc.exam_id = e.id WHERE e.id IS NULL;

-- -----------------------------------------------------------------------------
-- 6. SCHEMA COLUMNS INTEGRITY VERIFICATION (V13-V16 COLUMNS)
-- -----------------------------------------------------------------------------
SELECT
    TABLE_NAME,
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'codeassess'
  AND (
      (TABLE_NAME = 'exams' AND COLUMN_NAME = 'deleted_at') OR
      (TABLE_NAME = 'submissions' AND COLUMN_NAME IN ('auto_score', 'test_cases_passed', 'test_cases_total', 'zip_storage_path', 'instructor_feedback')) OR
      (TABLE_NAME = 'users' AND COLUMN_NAME = 'password_changed_at')
  )
ORDER BY TABLE_NAME, COLUMN_NAME;

-- Explicit verification that deprecated phantom column 'password_changed_at' is absent
SELECT
    'users.password_changed_at' AS 'Deprecated Column Check',
    CASE
        WHEN COUNT(*) = 0 THEN 'PASSED (Column correctly absent from schema)'
        ELSE 'VIOLATION (Deprecated column exists)'
    END AS 'Status'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'codeassess'
  AND TABLE_NAME = 'users'
  AND COLUMN_NAME = 'password_changed_at';

-- -----------------------------------------------------------------------------
-- 7. TEST CASES & AUTOMATED TESTING OVERVIEW
-- -----------------------------------------------------------------------------
SELECT
    tc.id AS 'Test Case ID',
    e.title AS 'Exam',
    tc.name AS 'Case Name',
    tc.points AS 'Points',
    tc.is_hidden AS 'Hidden Flag',
    tc.display_order AS 'Order'
FROM test_cases tc
JOIN exams e ON tc.exam_id = e.id
ORDER BY tc.display_order ASC;

SELECT
    str.submission_id AS 'Submission ID',
    u.full_name AS 'Student',
    str.test_case_name AS 'Test Case',
    str.status AS 'Result',
    str.points_awarded AS 'Points',
    str.execution_time_ms AS 'Time (ms)'
FROM submission_test_results str
JOIN submissions s ON str.submission_id = s.id
JOIN users u ON s.student_id = u.id
ORDER BY str.submission_id, str.id;

-- -----------------------------------------------------------------------------
-- 8. SUBMISSION & GRADING AUDIT (RUBRIC + AUTO SCORE + OVERRIDE)
-- -----------------------------------------------------------------------------
SELECT
    s.id AS 'Submission ID',
    u.full_name AS 'Student',
    s.submission_type AS 'Type',
    s.status AS 'Status',
    s.execution_status AS 'Execution',
    s.auto_score AS 'Auto Score',
    CONCAT(COALESCE(s.test_cases_passed, 0), '/', COALESCE(s.test_cases_total, 0)) AS 'Tests Passed',
    s.total_score AS 'Rubric Score',
    s.override_score AS 'Override Score',
    COALESCE(s.override_score, s.total_score) AS 'Effective Score',
    s.execution_duration_ms AS 'Duration (ms)'
FROM submissions s
JOIN users u ON s.student_id = u.id
ORDER BY s.created_at ASC;

-- -----------------------------------------------------------------------------
-- 9. PLAGIARISM DETECTION & SIMILARITY MATRIX
-- -----------------------------------------------------------------------------
SELECT
    sm.id AS 'Pair ID',
    e.title AS 'Exam',
    u1.full_name AS 'Student A',
    u2.full_name AS 'Student B',
    sm.similarity_rate AS 'Similarity Rate (%)',
    sm.shared_hashes_count AS 'Shared Hashes',
    sm.is_flagged AS 'Flagged',
    sm.adjudication_status AS 'Adjudication Decision',
    adj.full_name AS 'Adjudicated By'
FROM similarity_matrix sm
JOIN exams e ON sm.exam_id = e.id
JOIN submissions s1 ON sm.submission_a_id = s1.id
JOIN users u1 ON s1.student_id = u1.id
JOIN submissions s2 ON sm.submission_b_id = s2.id
JOIN users u2 ON s2.student_id = u2.id
LEFT JOIN users adj ON sm.adjudicated_by_id = adj.id
ORDER BY sm.similarity_rate DESC;

-- -----------------------------------------------------------------------------
-- 10. PLAGIARISM EVIDENCE MATCHES
-- -----------------------------------------------------------------------------
SELECT
    pe.id AS 'Evidence ID',
    pe.similarity_matrix_id AS 'Pair ID',
    pe.evidence_type AS 'Type',
    pe.submission_a_location AS 'A Location',
    pe.submission_b_location AS 'B Location',
    pe.description AS 'Match Description'
FROM plagiarism_evidence pe
ORDER BY pe.similarity_matrix_id ASC, pe.id ASC;

-- -----------------------------------------------------------------------------
-- 11. PLAGIARISM APPEALS STATUS
-- -----------------------------------------------------------------------------
SELECT
    pa.id AS 'Appeal ID',
    u.full_name AS 'Appellant Student',
    pa.basis AS 'Appeal Basis',
    pa.status AS 'Status',
    pa.reason AS 'Student Reason',
    pa.reviewer_notes AS 'Reviewer Resolution Notes'
FROM plagiarism_appeals pa
JOIN users u ON pa.student_id = u.id
ORDER BY pa.id ASC;

-- -----------------------------------------------------------------------------
-- 12. MATRIX COORDINATION & WORKER TELEMETRY
-- -----------------------------------------------------------------------------
SELECT
    emc.exam_id AS 'Exam ID',
    e.title AS 'Exam Title',
    emc.status AS 'Coordination Status',
    emc.current_generation AS 'Current Gen',
    emc.requested_generation AS 'Requested Gen',
    emc.worker_id AS 'Last Worker ID',
    emc.failure_count AS 'Failures'
FROM exam_matrix_coordination emc
JOIN exams e ON emc.exam_id = e.id;
