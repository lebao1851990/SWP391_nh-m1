-- Migration V20: Seed Question 1 for legacy exams and backfill multi-question relations
-- Preserves legacy single-question grades and audit provenance with 100% deterministic mapping.

-- 1. Create a synthetic "Question 1" for every existing exam that has no exam_questions
INSERT INTO exam_questions (id, exam_id, title, description, max_score, display_order, status, created_at, updated_at)
SELECT 
    MD5(CONCAT('q1-', e.id)),
    e.id,
    'Question 1',
    'Default question backfilled from single-question exam',
    10.00,
    0,
    'PUBLISHED',
    e.created_at,
    e.created_at
FROM exams e
WHERE NOT EXISTS (
    SELECT 1 FROM exam_questions eq WHERE eq.exam_id = e.id
);

-- 2. Backfill test_cases: link all existing unlinked test_cases to the exam's Question 1
UPDATE test_cases tc
JOIN exam_questions eq ON tc.exam_id = eq.exam_id
SET tc.question_id = eq.id
WHERE tc.question_id IS NULL;

-- 3. Backfill submissions: link all existing unlinked submissions to the exam's Question 1
UPDATE submissions s
JOIN exam_questions eq ON s.exam_id = eq.exam_id
SET s.question_id = eq.id
WHERE s.question_id IS NULL;

-- 4. Backfill similarity_matrix: link unlinked pairs where both submissions belong to the same question
UPDATE similarity_matrix sm
JOIN submissions sa ON sm.submission_a_id = sa.id
SET sm.question_id = sa.question_id
WHERE sm.question_id IS NULL 
  AND sa.question_id IS NOT NULL;

-- 5. Seed student_question_scores from existing submissions
-- Calculates latest submission, monotonic count, and preserves override provenance
INSERT INTO student_question_scores (
    id, exam_id, question_id, student_id,
    latest_submission_id, latest_submission_seq, latest_status,
    submission_count, effective_score, auto_score, override_score,
    override_reason, overridden_by, overridden_at, last_submitted_at,
    created_at, updated_at
)
SELECT
    MD5(CONCAT('sqs-', latest_sub.id)),
    latest_sub.exam_id,
    latest_sub.question_id,
    latest_sub.student_id,
    latest_sub.id,
    agg.total_subs,
    latest_sub.status,
    agg.total_subs,
    COALESCE(latest_sub.override_score, latest_sub.total_score, latest_sub.auto_score, 0.00),
    latest_sub.auto_score,
    latest_sub.override_score,
    latest_audit.rationale,
    latest_audit.actor_id,
    latest_audit.created_at,
    latest_sub.created_at,
    latest_sub.created_at,
    NOW()
FROM (
    -- Identify the latest submission per (question_id, student_id)
    SELECT s.*,
           ROW_NUMBER() OVER (
               PARTITION BY s.question_id, s.student_id 
               ORDER BY s.created_at DESC, s.id DESC
           ) as rn
    FROM submissions s
    WHERE s.question_id IS NOT NULL
) latest_sub
JOIN (
    -- Aggregate total submissions count per (question_id, student_id)
    SELECT question_id, student_id, COUNT(*) as total_subs
    FROM submissions
    WHERE question_id IS NOT NULL
    GROUP BY question_id, student_id
) agg ON latest_sub.question_id = agg.question_id AND latest_sub.student_id = agg.student_id
LEFT JOIN (
    -- Identify latest override audit log for the submission
    SELECT gal.*,
           ROW_NUMBER() OVER (
               PARTITION BY gal.submission_id 
               ORDER BY gal.created_at DESC, gal.id DESC
           ) as rn
    FROM grade_audit_logs gal
    WHERE gal.action_type IN ('OVERRIDE_SET', 'OVERRIDE_UPDATE', 'OVERRIDE_CLEARED', 'OVERRIDE_RESET_BY_RESUBMISSION')
) latest_audit ON latest_sub.id = latest_audit.submission_id AND latest_audit.rn = 1
WHERE latest_sub.rn = 1
ON DUPLICATE KEY UPDATE
    latest_submission_id = VALUES(latest_submission_id),
    latest_submission_seq = VALUES(latest_submission_seq),
    submission_count = VALUES(submission_count),
    effective_score = VALUES(effective_score),
    updated_at = NOW();

