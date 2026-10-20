-- V17: Restore submission_a_location and submission_b_location columns in plagiarism_evidence
-- Required by com.codeassess.plagiarism.entity.PlagiarismEvidence entity mapping
ALTER TABLE plagiarism_evidence
    ADD COLUMN IF NOT EXISTS submission_a_location VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS submission_b_location VARCHAR(255) NULL;
