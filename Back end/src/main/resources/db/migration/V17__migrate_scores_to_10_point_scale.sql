-- =============================================================================
-- V17: Migrate scoring from 100-point scale to 10-point scale
-- Ensures idempotency: only scores strictly greater than 10.00 are scaled down.
-- Plagiarism similarity rates, weights, and counts remain untouched.
-- =============================================================================

-- 1. Scale existing submission scores
UPDATE submissions
SET auto_score = ROUND(auto_score / 10.0, 2)
WHERE auto_score IS NOT NULL AND auto_score > 10.00;

UPDATE submissions
SET total_score = ROUND(total_score / 10.0, 2)
WHERE total_score IS NOT NULL AND total_score > 10.00;

UPDATE submissions
SET override_score = ROUND(override_score / 10.0, 2)
WHERE override_score IS NOT NULL AND override_score > 10.00;

-- 2. Scale existing grade audit logs scores
UPDATE grade_audit_logs
SET pre_rubric_score = ROUND(pre_rubric_score / 10.0, 2)
WHERE pre_rubric_score IS NOT NULL AND pre_rubric_score > 10.00;

UPDATE grade_audit_logs
SET post_rubric_score = ROUND(post_rubric_score / 10.0, 2)
WHERE post_rubric_score IS NOT NULL AND post_rubric_score > 10.00;

UPDATE grade_audit_logs
SET pre_override_score = ROUND(pre_override_score / 10.0, 2)
WHERE pre_override_score IS NOT NULL AND pre_override_score > 10.00;

UPDATE grade_audit_logs
SET post_override_score = ROUND(post_override_score / 10.0, 2)
WHERE post_override_score IS NOT NULL AND post_override_score > 10.00;

UPDATE grade_audit_logs
SET pre_effective_score = ROUND(pre_effective_score / 10.0, 2)
WHERE pre_effective_score IS NOT NULL AND pre_effective_score > 10.00;

UPDATE grade_audit_logs
SET post_effective_score = ROUND(post_effective_score / 10.0, 2)
WHERE post_effective_score IS NOT NULL AND post_effective_score > 10.00;
