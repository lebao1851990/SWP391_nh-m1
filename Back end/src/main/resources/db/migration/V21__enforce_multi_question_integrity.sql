-- Migration V21: Contract schema and enforce multi-question integrity constraints
-- Adds composite foreign keys, replacement unique constraints, canonical pair virtual column, and NOT NULL.

-- 1. Enforce NOT NULL on question_id for core tables
ALTER TABLE test_cases
    MODIFY COLUMN question_id VARCHAR(36) NOT NULL;

ALTER TABLE submissions
    MODIFY COLUMN question_id VARCHAR(36) NOT NULL;

ALTER TABLE similarity_matrix
    MODIFY COLUMN question_id VARCHAR(36) NOT NULL;

-- 2. Prerequisite unique constraint on submissions to support composite foreign keys
ALTER TABLE submissions
    ADD CONSTRAINT uk_submissions_id_question UNIQUE (id, question_id);

-- 3. Composite Foreign Keys ensuring questions belong to the same exam
ALTER TABLE exam_questions
    ADD CONSTRAINT uk_exam_questions_exam_id UNIQUE (exam_id, id);

ALTER TABLE test_cases
    ADD CONSTRAINT fk_test_cases_exam_question
    FOREIGN KEY (exam_id, question_id) REFERENCES exam_questions(exam_id, id) ON DELETE CASCADE;

ALTER TABLE submissions
    ADD CONSTRAINT fk_submissions_exam_question
    FOREIGN KEY (exam_id, question_id) REFERENCES exam_questions(exam_id, id) ON DELETE RESTRICT;

ALTER TABLE student_question_scores
    ADD CONSTRAINT fk_sqs_exam_question
    FOREIGN KEY (exam_id, question_id) REFERENCES exam_questions(exam_id, id) ON DELETE RESTRICT;

-- 4. Similarity Matrix: add helper index on submission_b_id before dropping legacy unique pair index
ALTER TABLE similarity_matrix
    ADD INDEX idx_sim_sub_b (submission_b_id);

-- 5. Add canonical pair virtual column and replacement unique constraint (TiDB Cloud compatible)
ALTER TABLE similarity_matrix
    ADD COLUMN canonical_pair_hash VARCHAR(73) AS (
        CASE 
            WHEN submission_a_id < submission_b_id 
            THEN CONCAT(submission_a_id, ':', submission_b_id)
            ELSE CONCAT(submission_b_id, ':', submission_a_id)
        END
    ) VIRTUAL;

ALTER TABLE similarity_matrix
    ADD CONSTRAINT uk_similarity_question_canonical_pair
    UNIQUE (question_id, canonical_pair_hash);

-- 6. Add composite Foreign Keys on similarity_matrix ensuring both submissions belong to question_id
ALTER TABLE similarity_matrix
    ADD CONSTRAINT fk_sim_sub_a_question 
    FOREIGN KEY (submission_a_id, question_id) 
    REFERENCES submissions(id, question_id) 
    ON DELETE CASCADE;

ALTER TABLE similarity_matrix
    ADD CONSTRAINT fk_sim_sub_b_question 
    FOREIGN KEY (submission_b_id, question_id) 
    REFERENCES submissions(id, question_id) 
    ON DELETE CASCADE;

-- 7. Drop legacy single-column FKs on similarity_matrix
ALTER TABLE similarity_matrix
    DROP FOREIGN KEY fk_similarity_submission_a;

ALTER TABLE similarity_matrix
    DROP FOREIGN KEY fk_similarity_submission_b;

-- 8. Safely drop legacy unique constraint
ALTER TABLE similarity_matrix
    DROP INDEX uk_similarity_submission_pair;

