-- =============================================================================
-- CODEASSESS — REFERENCE DATA SEED SCRIPT
-- File: 03_seed_reference_data.sql
-- Description: Essential reference data required for CodeAssess operation.
--              Includes standard rubric scoring criteria and a secure bootstrap administrator.
-- Security:
--   - Passwords are strictly BCrypt hashed (Cost factor 10, Spring Security standard).
--   - Plaintext passwords are NEVER stored in the database.
--   - Bootstrap credentials must be rotated upon first login.
-- =============================================================================

USE codeassess;

-- Disable FK checks during seed execution for clean transactional loading
SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 1. SCORING CRITERIA (Standard Assessment Rubrics)
-- Standard evaluation criteria configured for automated and rubric-based grading.
-- Total max_score sum = 10.00 points.
-- -----------------------------------------------------------------------------
INSERT INTO scoring_criteria (id, name, description, max_score)
VALUES
    ('crit-compilation-001', 'Compilation & Execution Integrity', 'Source code successfully compiles without errors, passes memory and sandbox limits, and terminates cleanly with exit code 0.', 3.00),
    ('crit-correctness-002', 'Functional Correctness & Test Pass Rate', 'Program passes standard and edge test cases, producing expected stdout outputs without unhandled runtime exceptions.', 4.00),
    ('crit-structure-003', 'Code Quality & Structural Modularity', 'Code exhibits clean OOP structure, standard naming conventions, proper exception handling, and modular methods.', 1.50),
    ('crit-efficiency-004', 'Time & Space Efficiency', 'Execution duration stays well within allotted wall-clock and CPU quota limits; memory utilization remains within bounds.', 1.50)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    max_score = VALUES(max_score);

-- -----------------------------------------------------------------------------
-- 2. BOOTSTRAP SYSTEM ADMINISTRATOR ACCOUNT
-- Role: ADMIN
-- Email: admin.bootstrap@codeassess.edu.vn
-- BCrypt Hash: $2a$10$RufBG7Q/gNL2P0xUpa4UGuZorJDI99iZ1PHmVzDSVN.uje55kD0ZW
-- Default Temp Password: Admin@CodeAssess2026!
-- IMPORTANT:
--   Immediately log in and update this password, or override the hash below with
--   your own BCrypt hash prior to executing this script.
-- -----------------------------------------------------------------------------
INSERT INTO users (id, email, password_hash, role, full_name, created_at, updated_at)
VALUES (
    'usr-admin-bootstrap-000000000001',
    'admin.bootstrap@codeassess.edu.vn',
    '$2a$10$RufBG7Q/gNL2P0xUpa4UGuZorJDI99iZ1PHmVzDSVN.uje55kD0ZW',
    'ADMIN',
    'CodeAssess System Administrator',
    NOW(),
    NOW()
)
ON DUPLICATE KEY UPDATE
    full_name = VALUES(full_name),
    updated_at = NOW();

-- Restore FK checks
SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS;

-- -----------------------------------------------------------------------------
-- Verification Output
-- -----------------------------------------------------------------------------
SELECT 'Reference data inserted successfully:' AS status;
SELECT id, name, max_score FROM scoring_criteria;
SELECT id, email, role, full_name FROM users WHERE role = 'ADMIN';
