-- Migration V22: Grade Approval, Publication Control, and Appeal Lifecycle Extension
-- Implements decoupled review status, publication control, score versioning, and appeal tracking.

-- 1. Extend submissions with review, publication, and score version columns
ALTER TABLE submissions
    ADD COLUMN review_status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',
    ADD COLUMN publication_status VARCHAR(20) NOT NULL DEFAULT 'UNPUBLISHED',
    ADD COLUMN reviewed_by VARCHAR(36) NULL,
    ADD COLUMN reviewed_at TIMESTAMP NULL,
    ADD COLUMN review_notes TEXT NULL,
    ADD COLUMN approved_score DECIMAL(5,2) NULL,
    ADD COLUMN published_by VARCHAR(36) NULL,
    ADD COLUMN published_at TIMESTAMP NULL,
    ADD COLUMN published_score DECIMAL(5,2) NULL,
    ADD COLUMN score_version INT NOT NULL DEFAULT 1;

ALTER TABLE submissions
    ADD CONSTRAINT fk_submissions_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users(id),
    ADD CONSTRAINT fk_submissions_published_by FOREIGN KEY (published_by) REFERENCES users(id),
    ADD INDEX idx_submissions_review_pub (review_status, publication_status);

-- 2. Extend student_question_scores with publication tracking
ALTER TABLE student_question_scores
    ADD COLUMN publication_status VARCHAR(20) NOT NULL DEFAULT 'UNPUBLISHED',
    ADD COLUMN published_score DECIMAL(5,2) NULL,
    ADD COLUMN published_at TIMESTAMP NULL;

-- 3. Extend plagiarism_appeals to support UNDER_REVIEW and resolution tracking
ALTER TABLE plagiarism_appeals
    ADD COLUMN admin_id VARCHAR(36) NULL,
    ADD COLUMN resolution_action VARCHAR(50) NULL;

ALTER TABLE plagiarism_appeals
    ADD CONSTRAINT fk_appeals_admin FOREIGN KEY (admin_id) REFERENCES users(id);

-- 4. Backfill existing evaluated submissions to PUBLISHED to preserve existing exam records
-- Uses LEGACY_AUTO review_status to truthfully indicate automated evaluation under V21 without false teacher approval attribution.
UPDATE submissions
SET review_status = 'LEGACY_AUTO',
    publication_status = 'PUBLISHED',
    approved_score = COALESCE(override_score, total_score, auto_score),
    published_score = COALESCE(override_score, total_score, auto_score),
    published_at = COALESCE(evaluated_at, created_at),
    reviewed_by = NULL,
    published_by = NULL,
    review_notes = 'Legacy auto-backfill from V21 pre-approval system',
    score_version = 1
WHERE evaluated_at IS NOT NULL OR status IN ('COMPLETED', 'GRADED', 'EVALUATED');

-- Special preservation for submissions with historical appeal resolutions
UPDATE submissions s
JOIN plagiarism_appeals a ON s.id = a.submission_id
SET s.review_notes = CONCAT('Legacy appeal resolution #', a.id, ' by admin')
WHERE a.status IN ('APPROVED', 'REJECTED')
  AND (s.evaluated_at IS NOT NULL OR s.status IN ('COMPLETED', 'GRADED', 'EVALUATED'));

UPDATE student_question_scores
SET publication_status = 'PUBLISHED',
    published_score = effective_score,
    published_at = updated_at
WHERE latest_status IN ('COMPLETED', 'GRADED', 'EVALUATED');

