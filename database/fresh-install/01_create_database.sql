-- =============================================================================
-- CODEASSESS — DATABASE INITIALIZATION SCRIPT
-- File: 01_create_database.sql
-- Description: Creates the 'codeassess' database with utf8mb4 encoding.
-- Compatibility: MySQL 8.0, 8.4 LTS, 9.x
-- Safety: Non-destructive, does NOT DROP any existing database.
-- =============================================================================

-- Create database only if it does not already exist
CREATE DATABASE IF NOT EXISTS codeassess
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Switch session context to the newly created database
USE codeassess;

-- Display database confirmation
SELECT
    SCHEMA_NAME AS database_name,
    DEFAULT_CHARACTER_SET_NAME AS charset_name,
    DEFAULT_COLLATION_NAME AS collation_name
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME = 'codeassess';
