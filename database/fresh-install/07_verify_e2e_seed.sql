-- =============================================================================
-- CODEASSESS — E2E LIVE SEED DATA VERIFICATION SCRIPT (READ-ONLY)
-- File: 07_verify_e2e_seed.sql
-- Phase: P0-3 Demo Seed Data
-- Description: Strictly READ-ONLY SQL queries to verify that all required
--              E2E entities, invariants, relationships, and foreign keys are valid.
-- Governance:
--   - READ-ONLY ONLY: Zero mutating statements (NO INSERT, UPDATE, DELETE).
-- =============================================================================

USE codeassess;

-- -----------------------------------------------------------------------------
-- 1. ENTITY COUNT AUDIT
-- -----------------------------------------------------------------------------
SELECT 'users' AS entity, COUNT(*) AS actual_count, 6 AS expected_min,
       CASE WHEN COUNT(*) >= 6 THEN 'PASS' ELSE 'FAIL: Insufficient seed users' END AS status
FROM users
WHERE email IN (
    'teachera@fpt.edu.vn',
    'teacher.test@codeassess.edu.vn',
    'studenta@fpt.edu.vn',
    'studentb@fpt.edu.vn',
    'student.test@codeassess.edu.vn',
    'admin@codeassess.edu.vn'
)
UNION ALL
SELECT 'classes' AS entity, COUNT(*) AS actual_count, 2 AS expected_min,
       CASE WHEN COUNT(*) >= 2 THEN 'PASS' ELSE 'FAIL: Insufficient seed classes' END AS status
FROM classes
WHERE id IN ('cls-e2e-pro192-00000001', 'cls-e2e-csd201-00000002')
UNION ALL
SELECT 'class_students' AS entity, COUNT(*) AS actual_count, 4 AS expected_min,
       CASE WHEN COUNT(*) >= 4 THEN 'PASS' ELSE 'FAIL: Insufficient class enrollments' END AS status
FROM class_students
WHERE class_id IN ('cls-e2e-pro192-00000001', 'cls-e2e-csd201-00000002')
UNION ALL
SELECT 'exams' AS entity, COUNT(*) AS actual_count, 2 AS expected_min,
       CASE WHEN COUNT(*) >= 2 THEN 'PASS' ELSE 'FAIL: Insufficient active exams' END AS status
FROM exams
WHERE id IN ('exm-e2e-exam-a-00000001', 'exm-e2e-pro192-midterm1')
  AND deleted_at IS NULL
UNION ALL
SELECT 'exam_classes' AS entity, COUNT(*) AS actual_count, 3 AS expected_min,
       CASE WHEN COUNT(*) >= 3 THEN 'PASS' ELSE 'FAIL: Insufficient exam-class links' END AS status
FROM exam_classes
WHERE exam_id IN ('exm-e2e-exam-a-00000001', 'exm-e2e-pro192-midterm1')
UNION ALL
SELECT 'submissions' AS entity, COUNT(*) AS actual_count, 2 AS expected_min,
       CASE WHEN COUNT(*) >= 2 THEN 'PASS' ELSE 'FAIL: Insufficient hardcoded submissions' END AS status
FROM submissions
WHERE id IN (
    '040b61a8-3c7a-4ea2-aace-18a0ba2d9d32',
    '007cf9ae-4385-46dc-b492-ba891af6a535'
)
UNION ALL
SELECT 'grade_audit_logs' AS entity, COUNT(*) AS actual_count, 1 AS expected_min,
       CASE WHEN COUNT(*) >= 1 THEN 'PASS' ELSE 'FAIL: Missing initial grade audit log' END AS status
FROM grade_audit_logs
WHERE submission_id = '040b61a8-3c7a-4ea2-aace-18a0ba2d9d32';

-- -----------------------------------------------------------------------------
-- 2. CRITICAL INVARIANT 1: SUBMISSION 040b61a8-... & AUDIT LOG CHECK
-- Required for grade-audit-history.spec.ts:
--   - post_override_score MUST be NULL to trigger "No override" pill
--   - actor_id MUST resolve to an authorized teacher user
--   - student_id MUST resolve to Student A (studenta@fpt.edu.vn)
-- -----------------------------------------------------------------------------
SELECT 
    s.id AS submission_id,
    s.status AS submission_status,
    s.total_score,
    s.override_score,
    l.id AS audit_log_id,
    l.action_type,
    l.post_override_score,
    u_actor.email AS actor_email,
    u_actor.full_name AS actor_name,
    u_student.email AS student_email,
    CASE 
        WHEN l.post_override_score IS NULL 
         AND u_actor.role = 'TEACHER' 
         AND u_student.email = 'studenta@fpt.edu.vn'
         AND s.status = 'COMPLETED' 
        THEN 'PASS: Renders "No override" pill & valid actor & student ownership verified' 
        ELSE 'FAIL: Invariant mismatch for grade audit log or student ownership' 
    END AS invariant_check
FROM submissions s
JOIN grade_audit_logs l ON s.id = l.submission_id
JOIN users u_actor ON l.actor_id = u_actor.id
JOIN users u_student ON s.student_id = u_student.id
WHERE s.id = '040b61a8-3c7a-4ea2-aace-18a0ba2d9d32';

-- -----------------------------------------------------------------------------
-- 3. CRITICAL INVARIANT 2: SUBMISSION 007cf9ae-... & INITIAL SCORE CHECK
-- Required for scoring-override.spec.ts:
--   - total_score MUST be 0.00 so badge displays "(Rubric: 0 pts)"
--   - override_score MUST be NULL initially
--   - Student MUST be studenta@fpt.edu.vn
-- -----------------------------------------------------------------------------
SELECT 
    s.id AS submission_id,
    u.email AS student_email,
    s.status,
    s.total_score,
    s.override_score,
    CASE 
        WHEN s.total_score = 0.00 
         AND s.override_score IS NULL 
         AND u.email = 'studenta@fpt.edu.vn' 
         AND s.status = 'COMPLETED'
        THEN 'PASS: Ready for override test' 
        ELSE 'FAIL: Expected total_score=0, override_score=NULL, student=studenta' 
    END AS invariant_check
FROM submissions s
JOIN users u ON s.student_id = u.id
WHERE s.id = '007cf9ae-4385-46dc-b492-ba891af6a535';

-- -----------------------------------------------------------------------------
-- 4. CRITICAL INVARIANT 3: "Exam A" ACCESSIBILITY & COHORT CHECK
-- Required for core-flows.spec.ts:
--   - Title must be 'Exam A'
--   - Status must be ACTIVE
--   - deleted_at must be NULL
--   - Enrolled: studenta and studentb
-- -----------------------------------------------------------------------------
SELECT 
    e.id AS exam_id,
    e.title,
    e.status,
    e.deleted_at,
    COUNT(cs.student_id) AS enrolled_students_count,
    CASE 
        WHEN e.title = 'Exam A' 
         AND e.status = 'ACTIVE' 
         AND e.deleted_at IS NULL 
         AND COUNT(cs.student_id) >= 2 
        THEN 'PASS: Core pipeline ready' 
        ELSE 'FAIL: Exam A configuration mismatch' 
    END AS invariant_check
FROM exams e
JOIN exam_classes ec ON e.id = ec.exam_id
JOIN class_students cs ON ec.class_id = cs.class_id
WHERE e.id = 'exm-e2e-exam-a-00000001'
GROUP BY e.id, e.title, e.status, e.deleted_at;

-- -----------------------------------------------------------------------------
-- 5. CRITICAL INVARIANT 4: CONFLICT AUDIT (ZERO ORPHAN / ACCIDENTAL OVERWRITES)
-- Ensures no duplicate key collisions exist on email or class code
-- with non-seed entities.
-- -----------------------------------------------------------------------------
SELECT 
    'Zero Key Conflicts' AS verification_check,
    (SELECT COUNT(*) FROM users WHERE email IN (
        'teachera@fpt.edu.vn', 'teacher.test@codeassess.edu.vn',
        'studenta@fpt.edu.vn', 'studentb@fpt.edu.vn',
        'student.test@codeassess.edu.vn', 'admin@codeassess.edu.vn'
    ) AND id NOT IN (
        'usr-e2e-teacher-000000000001', 'usr-e2e-teacher-000000000002',
        'usr-e2e-student-000000000001', 'usr-e2e-student-000000000002',
        'usr-e2e-student-000000000003', 'usr-e2e-admin-0000000000001'
    )) AS email_conflict_count,
    (SELECT COUNT(*) FROM classes WHERE code IN ('PRO192-E2E-FA26', 'CSD201-E2E-FA26')
      AND id NOT IN ('cls-e2e-pro192-00000001', 'cls-e2e-csd201-00000002')
    ) AS class_code_conflict_count,
    CASE 
        WHEN (SELECT COUNT(*) FROM users WHERE email IN (
            'teachera@fpt.edu.vn', 'teacher.test@codeassess.edu.vn',
            'studenta@fpt.edu.vn', 'studentb@fpt.edu.vn',
            'student.test@codeassess.edu.vn', 'admin@codeassess.edu.vn'
        ) AND id NOT IN (
            'usr-e2e-teacher-000000000001', 'usr-e2e-teacher-000000000002',
            'usr-e2e-student-000000000001', 'usr-e2e-student-000000000002',
            'usr-e2e-student-000000000003', 'usr-e2e-admin-0000000000001'
        )) = 0
         AND (SELECT COUNT(*) FROM classes WHERE code IN ('PRO192-E2E-FA26', 'CSD201-E2E-FA26')
              AND id NOT IN ('cls-e2e-pro192-00000001', 'cls-e2e-csd201-00000002')) = 0
        THEN 'PASS: Zero Key Conflicts'
        ELSE 'FAIL: Key Conflict Detected with existing non-seed rows'
    END AS status;
