-- =============================================================================
-- V6: Fix schema mismatches and create missing tables
--
-- Issues fixed:
--   1. plagiarism_evidence: replace wrong location columns with correct line-range
--      columns that match PlagiarismEvidence.java entity.
--   2. similarity_matrix: table missing from all previous migrations.
--   3. ast_fingerprints:  table missing from all previous migrations.
--   4. rule_scores: column was named 'score_awarded' in V5 but entity uses 'score'.
--   5. scoring_criteria: V5 added exam_id FK that has no matching field in entity.
--
-- Safety: every DDL uses IF EXISTS / IF NOT EXISTS guards so the migration is
-- idempotent and safe to run on databases that were created manually or via
-- hibernate ddl-auto before this migration was introduced.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. FIX plagiarism_evidence
--    V5 created columns: submission_a_location VARCHAR(100),
--                        submission_b_location VARCHAR(100)
--    Entity PlagiarismEvidence.java expects:
--                        submission_a_start_line INT,
--                        submission_a_end_line   INT,
--                        submission_b_start_line INT,
--                        submission_b_end_line   INT
-- ---------------------------------------------------------------------------

-- Add correct columns (ignore error if they already exist)
ALTER TABLE plagiarism_evidence
    ADD COLUMN IF NOT EXISTS submission_a_start_line INT NULL,
    ADD COLUMN IF NOT EXISTS submission_a_end_line   INT NULL,
    ADD COLUMN IF NOT EXISTS submission_b_start_line INT NULL,
    ADD COLUMN IF NOT EXISTS submission_b_end_line   INT NULL;

-- Drop the wrong columns created by V5 (ignore error if already removed)
ALTER TABLE plagiarism_evidence
    DROP COLUMN IF EXISTS submission_a_location,
    DROP COLUMN IF EXISTS submission_b_location;

-- ---------------------------------------------------------------------------
-- 2. FIX scoring_criteria
--    V5 added exam_id + FK but ScoringCriteria entity has no exam field.
--    Drop the FK constraint first, then the column.
-- ---------------------------------------------------------------------------

-- Drop the FK constraint if it exists (constraint name matches V5 definition)
ALTER TABLE scoring_criteria
    DROP FOREIGN KEY IF EXISTS fk_scoring_criteria_exam;

-- Drop the orphaned exam_id column
ALTER TABLE scoring_criteria
    DROP COLUMN IF EXISTS exam_id;

-- ---------------------------------------------------------------------------
-- 3. FIX rule_scores
--    V5 named the column 'score_awarded'; entity RuleScore.java maps to 'score'.
-- ---------------------------------------------------------------------------

-- Add the correctly-named column if it does not exist yet
ALTER TABLE rule_scores
    ADD COLUMN IF NOT EXISTS score DECIMAL(5,2) NOT NULL DEFAULT 0.00;

-- Migrate existing data from score_awarded → score (if old column still present)
UPDATE rule_scores SET score = score_awarded WHERE score = 0.00 AND score_awarded IS NOT NULL;

-- Drop the wrongly-named column
ALTER TABLE rule_scores
    DROP COLUMN IF EXISTS score_awarded;

-- Also rename FK column from 'criteria_id' if V5 used 'criteria_id' already
-- (Entity uses @JoinColumn(name="criteria_id") so this is already correct — no change needed)

-- ---------------------------------------------------------------------------
-- 4. CREATE similarity_matrix (if not exists)
--    Entity: SimilarityPair.java  →  @Table(name = "similarity_matrix")
--    PK:     BIGINT AUTO_INCREMENT
--    FKs:    exam_id → exams(id), submission_a_id → submissions(id),
--            submission_b_id → submissions(id)
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS similarity_matrix (
    id                 BIGINT          NOT NULL AUTO_INCREMENT,
    exam_id            VARCHAR(36)     NOT NULL,
    submission_a_id    VARCHAR(36)     NOT NULL,
    submission_b_id    VARCHAR(36)     NOT NULL,
    similarity_rate    DECIMAL(5,2)    NOT NULL,
    shared_hashes_count INT            NULL,
    is_flagged         TINYINT(1)      NULL,
    created_at         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_similarity_pair (submission_a_id, submission_b_id),
    INDEX idx_sm_exam (exam_id),
    INDEX idx_sm_sub_a (submission_a_id),
    INDEX idx_sm_sub_b (submission_b_id),

    CONSTRAINT fk_sm_exam
        FOREIGN KEY (exam_id)         REFERENCES exams(id)        ON DELETE CASCADE,
    CONSTRAINT fk_sm_sub_a
        FOREIGN KEY (submission_a_id) REFERENCES submissions(id)  ON DELETE CASCADE,
    CONSTRAINT fk_sm_sub_b
        FOREIGN KEY (submission_b_id) REFERENCES submissions(id)  ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 5. CREATE ast_fingerprints (if not exists)
--    Entity: AstFingerprint.java  →  @Table(name = "ast_fingerprints")
--    PK:     BIGINT AUTO_INCREMENT
--    FK:     submission_id → submissions(id)
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ast_fingerprints (
    id             BIGINT          NOT NULL AUTO_INCREMENT,
    submission_id  VARCHAR(36)     NOT NULL,
    hash_value     BIGINT          NOT NULL,
    node_type      VARCHAR(64)     NOT NULL,
    start_line     INT             NOT NULL,
    end_line       INT             NOT NULL,
    created_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_afp_submission (submission_id),
    INDEX idx_afp_hash (hash_value),

    CONSTRAINT fk_afp_submission
        FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 6. Fix plagiarism_evidence FK reference
--    V5 created the FK pointing to similarity_matrix(id) which did not exist
--    at that time. Re-verify the FK now that similarity_matrix exists.
--    (MySQL silently deferred FK checks; this ensures the constraint is live.)
-- ---------------------------------------------------------------------------

-- Drop and recreate fk_evidence_similarity if it was not actually enforced
ALTER TABLE plagiarism_evidence
    DROP FOREIGN KEY IF EXISTS fk_evidence_similarity;

ALTER TABLE plagiarism_evidence
    ADD CONSTRAINT fk_evidence_similarity
        FOREIGN KEY (similarity_matrix_id) REFERENCES similarity_matrix(id)
        ON DELETE CASCADE;
