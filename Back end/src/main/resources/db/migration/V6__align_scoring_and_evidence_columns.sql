-- V6: non-destructive compatibility bridge between V5 and current JPA mappings.
-- Source-verified mismatches:
--   rule_scores.score_awarded (V5) vs RuleScore.score
--   plagiarism_evidence.submission_*_location (V5) vs start/end line columns
--
-- This migration does not drop legacy columns.
-- It is written for MySQL. Do not use MariaDB-only column existence guards.
-- Flyway runs each version once; repeating ADD COLUMN on an already-migrated DB
-- is expected to fail, which is the correct Flyway behavior.
--
-- Do not rewrite V6 if it is already recorded in flyway_schema_history.
-- Live MySQL metadata and Flyway history were not verified in this workspace,
-- so this file is a source-level compatibility proposal, not a live-applied change.
-- Do not auto-convert submission_a_location / submission_b_location to line numbers:
-- the legacy string format is not proven.

ALTER TABLE rule_scores
    ADD COLUMN score DECIMAL(5,2) NULL AFTER criteria_id;

-- Backfill only when the application column is empty and the legacy value is known.
-- If both columns are present and differ, leave score unchanged: no proven business rule.
UPDATE rule_scores
SET score = score_awarded
WHERE score IS NULL
  AND score_awarded IS NOT NULL;

ALTER TABLE plagiarism_evidence
    ADD COLUMN submission_a_start_line INT NULL,
    ADD COLUMN submission_a_end_line INT NULL,
    ADD COLUMN submission_b_start_line INT NULL,
    ADD COLUMN submission_b_end_line INT NULL;
