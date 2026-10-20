-- MariaDB dump 10.19  Distrib 10.4.32-MariaDB, for Win64 (AMD64)
--
-- Host: localhost    Database: plagiarism_system
-- ------------------------------------------------------
-- Server version	10.4.32-MariaDB

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `plagiarism_system`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `plagiarism_system` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */;

USE `plagiarism_system`;

--
-- Table structure for table `ast_fingerprints`
--

DROP TABLE IF EXISTS `ast_fingerprints`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ast_fingerprints` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `submission_id` varchar(36) NOT NULL,
  `hash_value` bigint(20) NOT NULL,
  `node_type` varchar(64) NOT NULL,
  `start_line` int(11) NOT NULL,
  `end_line` int(11) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `file_path` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ast_submission_hash` (`submission_id`,`hash_value`),
  KEY `idx_ast_hash` (`hash_value`),
  CONSTRAINT `fk_ast_fingerprints_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ast_fingerprints`
--

LOCK TABLES `ast_fingerprints` WRITE;
/*!40000 ALTER TABLE `ast_fingerprints` DISABLE KEYS */;
/*!40000 ALTER TABLE `ast_fingerprints` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `class_students`
--

DROP TABLE IF EXISTS `class_students`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `class_students` (
  `class_id` varchar(36) NOT NULL,
  `student_id` varchar(36) NOT NULL,
  PRIMARY KEY (`class_id`,`student_id`),
  KEY `idx_class_students_student` (`student_id`),
  CONSTRAINT `fk_class_students_class` FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_class_students_student` FOREIGN KEY (`student_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `class_students`
--

LOCK TABLES `class_students` WRITE;
/*!40000 ALTER TABLE `class_students` DISABLE KEYS */;
/*!40000 ALTER TABLE `class_students` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `classes`
--

DROP TABLE IF EXISTS `classes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `classes` (
  `id` varchar(36) NOT NULL,
  `teacher_id` varchar(36) NOT NULL,
  `name` varchar(255) NOT NULL,
  `code` varchar(100) NOT NULL,
  `semester` varchar(100) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_classes_code` (`code`),
  KEY `idx_classes_teacher` (`teacher_id`),
  CONSTRAINT `fk_classes_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `users` (`id`) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `classes`
--

LOCK TABLES `classes` WRITE;
/*!40000 ALTER TABLE `classes` DISABLE KEYS */;
INSERT INTO `classes` VALUES ('e8269c97-027d-4fa3-b6e7-c28da1888be5','1fa926e4-83d6-4a92-8292-b8475b765d6c','data','CS102','fall2026','2026-10-19 04:28:53');
/*!40000 ALTER TABLE `classes` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `exam_classes`
--

DROP TABLE IF EXISTS `exam_classes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `exam_classes` (
  `exam_id` varchar(36) NOT NULL,
  `class_id` varchar(36) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`exam_id`,`class_id`),
  KEY `idx_exam_classes_exam` (`exam_id`),
  KEY `idx_exam_classes_class` (`class_id`),
  CONSTRAINT `fk_exam_classes_class` FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`),
  CONSTRAINT `fk_exam_classes_exam` FOREIGN KEY (`exam_id`) REFERENCES `exams` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exam_classes`
--

LOCK TABLES `exam_classes` WRITE;
/*!40000 ALTER TABLE `exam_classes` DISABLE KEYS */;
INSERT INTO `exam_classes` VALUES ('0597e42b-ec80-4273-9b87-590b9e4705c1','e8269c97-027d-4fa3-b6e7-c28da1888be5','2026-10-19 04:29:24');
/*!40000 ALTER TABLE `exam_classes` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `exam_matrix_coordination`
--

DROP TABLE IF EXISTS `exam_matrix_coordination`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `exam_matrix_coordination` (
  `exam_id` varchar(36) NOT NULL,
  `current_generation` bigint(20) NOT NULL DEFAULT 0,
  `requested_generation` bigint(20) NOT NULL DEFAULT 0,
  `locked_generation` bigint(20) NOT NULL DEFAULT 0,
  `lease_token` varchar(36) DEFAULT NULL,
  `worker_id` varchar(100) DEFAULT NULL,
  `lease_expires_at` datetime DEFAULT NULL,
  `status` varchar(30) NOT NULL DEFAULT 'IDLE',
  `failure_count` int(11) NOT NULL DEFAULT 0,
  `last_error` varchar(1000) DEFAULT NULL,
  `next_retry_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`exam_id`),
  KEY `idx_coord_status_lease` (`status`,`lease_expires_at`),
  KEY `idx_coord_status_retry` (`status`,`next_retry_at`),
  CONSTRAINT `fk_coord_exam` FOREIGN KEY (`exam_id`) REFERENCES `exams` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exam_matrix_coordination`
--

LOCK TABLES `exam_matrix_coordination` WRITE;
/*!40000 ALTER TABLE `exam_matrix_coordination` DISABLE KEYS */;
/*!40000 ALTER TABLE `exam_matrix_coordination` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `exams`
--

DROP TABLE IF EXISTS `exams`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `exams` (
  `id` varchar(36) NOT NULL,
  `class_id` varchar(36) NOT NULL,
  `title` varchar(255) NOT NULL,
  `exam_date` datetime DEFAULT NULL,
  `duration` int(11) DEFAULT NULL,
  `status` varchar(50) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_exams_class` (`class_id`),
  CONSTRAINT `fk_exams_class` FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`) ON UPDATE CASCADE,
  CONSTRAINT `chk_exams_duration` CHECK (`duration` is null or `duration` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exams`
--

LOCK TABLES `exams` WRITE;
/*!40000 ALTER TABLE `exams` DISABLE KEYS */;
INSERT INTO `exams` VALUES ('0597e42b-ec80-4273-9b87-590b9e4705c1','e8269c97-027d-4fa3-b6e7-c28da1888be5','CS102 data','2026-10-19 04:29:00',90,'active','2026-10-19 04:29:24');
/*!40000 ALTER TABLE `exams` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int(11) NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int(11) DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT current_timestamp(),
  `execution_time` int(11) NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flyway_schema_history`
--

LOCK TABLES `flyway_schema_history` WRITE;
/*!40000 ALTER TABLE `flyway_schema_history` DISABLE KEYS */;
INSERT INTO `flyway_schema_history` VALUES (1,'12','<< Flyway Baseline >>','BASELINE','<< Flyway Baseline >>',NULL,'root','2026-10-19 03:00:49',0,1),(2,'13','align post baseline schema','SQL','V13__align_post_baseline_schema.sql',1265705626,'root','2026-10-19 03:28:37',213,1),(3,'14','add test cases and submission test results','SQL','V14__add_test_cases_and_submission_test_results.sql',-2017940884,'root','2026-10-19 12:52:34',257,1),(4,'15','fix schema and create missing tables','SQL','V15__fix_schema_and_create_missing_tables.sql',-444492855,'root','2026-10-19 13:16:20',288,1),(5,'16','create password reset tokens','SQL','V16__create_password_reset_tokens.sql',1561250734,'root','2026-10-19 13:17:49',153,1),(6,'17','restore plagiarism evidence locations','SQL','V17__restore_plagiarism_evidence_locations.sql',1531152089,'root','2026-10-19 13:26:15',63,1);
/*!40000 ALTER TABLE `flyway_schema_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `grade_audit_logs`
--

DROP TABLE IF EXISTS `grade_audit_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `grade_audit_logs` (
  `id` varchar(36) NOT NULL,
  `submission_id` varchar(36) NOT NULL,
  `actor_id` varchar(36) NOT NULL,
  `action_type` varchar(50) NOT NULL,
  `pre_rubric_score` decimal(5,2) DEFAULT NULL,
  `post_rubric_score` decimal(5,2) DEFAULT NULL,
  `pre_override_score` decimal(5,2) DEFAULT NULL,
  `post_override_score` decimal(5,2) DEFAULT NULL,
  `pre_effective_score` decimal(5,2) DEFAULT NULL,
  `post_effective_score` decimal(5,2) DEFAULT NULL,
  `rationale` text DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `fk_grade_audit_actor` (`actor_id`),
  KEY `idx_grade_audit_submission` (`submission_id`),
  KEY `idx_grade_audit_created_at` (`created_at`),
  CONSTRAINT `fk_grade_audit_actor` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_grade_audit_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `grade_audit_logs`
--

LOCK TABLES `grade_audit_logs` WRITE;
/*!40000 ALTER TABLE `grade_audit_logs` DISABLE KEYS */;
/*!40000 ALTER TABLE `grade_audit_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `password_reset_tokens`
--

DROP TABLE IF EXISTS `password_reset_tokens`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `password_reset_tokens` (
  `id` varchar(36) NOT NULL,
  `token` varchar(128) NOT NULL,
  `user_id` varchar(36) NOT NULL,
  `expires_at` datetime NOT NULL,
  `used` tinyint(4) NOT NULL DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `token` (`token`),
  KEY `idx_pwd_reset_token` (`token`),
  KEY `idx_pwd_reset_user` (`user_id`),
  CONSTRAINT `fk_password_reset_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `password_reset_tokens`
--

LOCK TABLES `password_reset_tokens` WRITE;
/*!40000 ALTER TABLE `password_reset_tokens` DISABLE KEYS */;
/*!40000 ALTER TABLE `password_reset_tokens` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `plagiarism_appeals`
--

DROP TABLE IF EXISTS `plagiarism_appeals`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `plagiarism_appeals` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `submission_id` varchar(36) NOT NULL,
  `student_id` varchar(36) NOT NULL,
  `reason` text NOT NULL,
  `basis` varchar(100) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `reviewer_notes` text DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `resolved_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_appeals_submission` (`submission_id`),
  KEY `idx_appeals_student` (`student_id`),
  KEY `idx_appeals_status` (`status`),
  CONSTRAINT `fk_appeals_student` FOREIGN KEY (`student_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_appeals_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `chk_appeals_status` CHECK (`status` in ('PENDING','APPROVED','REJECTED','ESCALATED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `plagiarism_appeals`
--

LOCK TABLES `plagiarism_appeals` WRITE;
/*!40000 ALTER TABLE `plagiarism_appeals` DISABLE KEYS */;
/*!40000 ALTER TABLE `plagiarism_appeals` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `plagiarism_evidence`
--

DROP TABLE IF EXISTS `plagiarism_evidence`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `plagiarism_evidence` (
  `id` varchar(36) NOT NULL,
  `similarity_matrix_id` bigint(20) NOT NULL,
  `evidence_type` varchar(100) NOT NULL,
  `description` text NOT NULL,
  `submission_a_start_line` int(11) DEFAULT NULL,
  `submission_a_end_line` int(11) DEFAULT NULL,
  `submission_b_start_line` int(11) DEFAULT NULL,
  `submission_b_end_line` int(11) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `submission_a_location` varchar(255) DEFAULT NULL,
  `submission_b_location` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_evidence_similarity` (`similarity_matrix_id`),
  CONSTRAINT `fk_evidence_similarity` FOREIGN KEY (`similarity_matrix_id`) REFERENCES `similarity_matrix` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_evidence_similarity_matrix` FOREIGN KEY (`similarity_matrix_id`) REFERENCES `similarity_matrix` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `plagiarism_evidence`
--

LOCK TABLES `plagiarism_evidence` WRITE;
/*!40000 ALTER TABLE `plagiarism_evidence` DISABLE KEYS */;
/*!40000 ALTER TABLE `plagiarism_evidence` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rule_scores`
--

DROP TABLE IF EXISTS `rule_scores`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rule_scores` (
  `id` varchar(36) NOT NULL,
  `submission_id` varchar(36) NOT NULL,
  `criteria_id` varchar(36) NOT NULL,
  `score` decimal(5,2) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_scores_submission_criteria` (`submission_id`,`criteria_id`),
  KEY `fk_rule_scores_criteria` (`criteria_id`),
  CONSTRAINT `fk_rule_scores_criteria` FOREIGN KEY (`criteria_id`) REFERENCES `scoring_criteria` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_rule_scores_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `chk_rule_scores_score` CHECK (`score` >= 0 and `score` <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rule_scores`
--

LOCK TABLES `rule_scores` WRITE;
/*!40000 ALTER TABLE `rule_scores` DISABLE KEYS */;
/*!40000 ALTER TABLE `rule_scores` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `scoring_criteria`
--

DROP TABLE IF EXISTS `scoring_criteria`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `scoring_criteria` (
  `id` varchar(36) NOT NULL,
  `name` varchar(255) NOT NULL,
  `description` text DEFAULT NULL,
  `max_score` decimal(5,2) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_scoring_criteria_max_score` CHECK (`max_score` >= 0 and `max_score` <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `scoring_criteria`
--

LOCK TABLES `scoring_criteria` WRITE;
/*!40000 ALTER TABLE `scoring_criteria` DISABLE KEYS */;
/*!40000 ALTER TABLE `scoring_criteria` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `similarity_matrix`
--

DROP TABLE IF EXISTS `similarity_matrix`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `similarity_matrix` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `exam_id` varchar(36) NOT NULL,
  `submission_a_id` varchar(36) NOT NULL,
  `submission_b_id` varchar(36) NOT NULL,
  `similarity_rate` decimal(5,2) NOT NULL,
  `shared_hashes_count` int(11) NOT NULL DEFAULT 0,
  `is_flagged` tinyint(4) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `adjudication_status` varchar(30) NOT NULL DEFAULT 'PENDING',
  `adjudication_notes` text DEFAULT NULL,
  `adjudicated_by_id` varchar(36) DEFAULT NULL,
  `adjudicated_at` datetime DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_similarity_submission_pair` (`submission_a_id`,`submission_b_id`),
  KEY `idx_similarity_exam_flagged` (`exam_id`,`is_flagged`),
  KEY `idx_similarity_submission_a` (`submission_a_id`),
  KEY `idx_similarity_submission_b` (`submission_b_id`),
  KEY `idx_similarity_adjudication_status` (`adjudication_status`),
  KEY `idx_similarity_exam_active` (`exam_id`,`is_active`),
  CONSTRAINT `fk_similarity_exam` FOREIGN KEY (`exam_id`) REFERENCES `exams` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_similarity_submission_a` FOREIGN KEY (`submission_a_id`) REFERENCES `submissions` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_similarity_submission_b` FOREIGN KEY (`submission_b_id`) REFERENCES `submissions` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `chk_similarity_rate` CHECK (`similarity_rate` >= 0 and `similarity_rate` <= 100),
  CONSTRAINT `chk_similarity_flag` CHECK (`is_flagged` in (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `similarity_matrix`
--

LOCK TABLES `similarity_matrix` WRITE;
/*!40000 ALTER TABLE `similarity_matrix` DISABLE KEYS */;
/*!40000 ALTER TABLE `similarity_matrix` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `submission_test_results`
--

DROP TABLE IF EXISTS `submission_test_results`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `submission_test_results` (
  `id` varchar(36) NOT NULL,
  `submission_id` varchar(36) NOT NULL,
  `test_case_id` varchar(36) DEFAULT NULL,
  `test_case_name` varchar(255) DEFAULT NULL,
  `is_hidden` tinyint(4) NOT NULL DEFAULT 0,
  `status` varchar(50) NOT NULL,
  `actual_output` longtext DEFAULT NULL,
  `expected_output` longtext DEFAULT NULL,
  `execution_time_ms` bigint(20) DEFAULT NULL,
  `exit_code` int(11) DEFAULT NULL,
  `error_message` text DEFAULT NULL,
  `points_awarded` decimal(5,2) NOT NULL DEFAULT 0.00,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `fk_sub_test_results_test_case` (`test_case_id`),
  KEY `idx_sub_test_results_sub` (`submission_id`),
  CONSTRAINT `fk_sub_test_results_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_sub_test_results_test_case` FOREIGN KEY (`test_case_id`) REFERENCES `test_cases` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `submission_test_results`
--

LOCK TABLES `submission_test_results` WRITE;
/*!40000 ALTER TABLE `submission_test_results` DISABLE KEYS */;
/*!40000 ALTER TABLE `submission_test_results` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `submissions`
--

DROP TABLE IF EXISTS `submissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `submissions` (
  `id` varchar(36) NOT NULL,
  `exam_id` varchar(36) NOT NULL,
  `student_id` varchar(36) NOT NULL,
  `status` varchar(50) NOT NULL,
  `source_code` longtext DEFAULT NULL,
  `total_score` decimal(5,2) DEFAULT NULL,
  `override_score` decimal(5,2) DEFAULT NULL,
  `evaluated_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `submission_type` varchar(20) NOT NULL DEFAULT 'SINGLE_FILE',
  `original_filename` varchar(255) DEFAULT NULL,
  `archive_hash` varchar(64) DEFAULT NULL,
  `source_tree_hash` varchar(64) DEFAULT NULL,
  `file_count` int(11) DEFAULT NULL,
  `entry_point_class` varchar(255) DEFAULT NULL,
  `execution_status` varchar(50) DEFAULT NULL,
  `exit_code` int(11) DEFAULT NULL,
  `compile_output` text DEFAULT NULL,
  `stdout` text DEFAULT NULL,
  `stderr` text DEFAULT NULL,
  `execution_duration_ms` bigint(20) DEFAULT NULL,
  `claim_token` varchar(64) DEFAULT NULL,
  `lease_expires_at` timestamp NULL DEFAULT NULL,
  `auto_score` decimal(5,2) DEFAULT NULL,
  `test_cases_passed` int(11) DEFAULT NULL,
  `test_cases_total` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_submissions_exam` (`exam_id`),
  KEY `idx_submissions_student` (`student_id`),
  KEY `idx_submissions_exam_student` (`exam_id`,`student_id`),
  KEY `idx_submissions_archive_hash` (`archive_hash`),
  KEY `idx_submissions_source_tree_hash` (`source_tree_hash`),
  KEY `idx_submissions_status_lease` (`status`,`lease_expires_at`),
  CONSTRAINT `fk_submissions_exam` FOREIGN KEY (`exam_id`) REFERENCES `exams` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_submissions_student` FOREIGN KEY (`student_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `chk_submissions_total_score` CHECK (`total_score` is null or `total_score` >= 0 and `total_score` <= 100),
  CONSTRAINT `chk_submissions_override_score` CHECK (`override_score` is null or `override_score` >= 0 and `override_score` <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `submissions`
--

LOCK TABLES `submissions` WRITE;
/*!40000 ALTER TABLE `submissions` DISABLE KEYS */;
/*!40000 ALTER TABLE `submissions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `test_cases`
--

DROP TABLE IF EXISTS `test_cases`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `test_cases` (
  `id` varchar(36) NOT NULL,
  `exam_id` varchar(36) NOT NULL,
  `name` varchar(255) NOT NULL,
  `description` text DEFAULT NULL,
  `input_data` longtext DEFAULT NULL,
  `expected_output` longtext DEFAULT NULL,
  `points` decimal(5,2) NOT NULL DEFAULT 10.00,
  `is_hidden` tinyint(4) NOT NULL DEFAULT 0,
  `display_order` int(11) NOT NULL DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_test_cases_exam_order` (`exam_id`,`display_order`),
  CONSTRAINT `fk_test_cases_exam` FOREIGN KEY (`exam_id`) REFERENCES `exams` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `test_cases`
--

LOCK TABLES `test_cases` WRITE;
/*!40000 ALTER TABLE `test_cases` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_cases` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `users` (
  `id` varchar(36) NOT NULL,
  `email` varchar(255) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `role` varchar(20) NOT NULL,
  `full_name` varchar(255) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`),
  CONSTRAINT `chk_users_role` CHECK (`role` in ('ADMIN','TEACHER','STUDENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES ('1fa926e4-83d6-4a92-8292-b8475b765d6c','kindragon999@gmail.com','$2a$10$4RFpeVM.F9J6okpY1B2e6OcXu1oaOGtmopAYbcXOX8koIYkJooBMq','TEACHER','dat','2026-10-19 04:26:25','2026-10-19 04:26:25'),('343d7d48-506b-4810-be5f-9ed5bc12251b','admin@codeassess.edu','$2a$10$Dg2/HF2sumd56oRiGxZ3.ee2jg6kj11BvPA/TSMoS9dQcewNcSufu','ADMIN','System Administrator','2026-10-19 02:56:29','2026-10-19 02:56:29'),('3f4c5caf-d885-4da9-ae75-8486c24d5eba','kindragon987@gmail.com','$2a$10$EsdG25zCXG2xdf7.dPIP2ezLRUX/VLhz6O3XYkL9rZ/UbItsZdHWK','STUDENT','dat','2026-10-19 04:25:24','2026-10-19 04:25:24'),('4aa5b9ce-09f5-46e7-8236-df2e5d0e7b9a','kindragon989@gmail.com','$2a$10$W2aNcUm1PTVSwCE9XfGn1u0peWkgOqHDSqqAAMUvBg1eezbCd2CeC','STUDENT','dat','2026-10-19 04:25:55','2026-10-19 04:25:55');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-19 21:47:18
