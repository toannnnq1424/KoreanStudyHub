-- MySQL dump 10.13  Distrib 9.2.0, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: ksh_dev_20260809
-- ------------------------------------------------------
-- Server version	9.2.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `activity_classes`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `activity_classes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `class_id` bigint NOT NULL,
  `type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `metadata` json DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_acl_class` (`class_id`,`created_at`),
  KEY `idx_acl_type` (`type`),
  KEY `fk_acl_creator` (`created_by`),
  CONSTRAINT `fk_acl_class` FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_acl_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `activity_lessons`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `activity_lessons` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lesson_id` bigint NOT NULL,
  `type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `metadata` json DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_al_lesson` (`lesson_id`,`created_at`),
  KEY `idx_al_type` (`type`),
  KEY `fk_al_creator` (`created_by`),
  CONSTRAINT `fk_al_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_al_lesson` FOREIGN KEY (`lesson_id`) REFERENCES `lessons` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `activity_sections`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `activity_sections` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `section_id` bigint NOT NULL,
  `type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `metadata` json DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_asec_section` (`section_id`,`created_at`),
  KEY `fk_asec_creator` (`created_by`),
  CONSTRAINT `fk_asec_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_asec_section` FOREIGN KEY (`section_id`) REFERENCES `sections` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `activity_tests`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `activity_tests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `test_id` bigint NOT NULL,
  `type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `metadata` json DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_atest_test` (`test_id`,`created_at`),
  KEY `idx_atest_type` (`type`),
  KEY `fk_atest_creator` (`created_by`),
  CONSTRAINT `fk_atest_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_atest_test` FOREIGN KEY (`test_id`) REFERENCES `tests` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ai_providers`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_providers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `base_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `model` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `api_key` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_enabled` tinyint(1) NOT NULL DEFAULT '1',
  `display_order` smallint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_ai_providers_name` (`name`),
  UNIQUE KEY `idx_ai_providers_display_order` (`display_order`),
  KEY `idx_ai_providers_enabled_order` (`is_enabled`,`display_order`),
  KEY `fk_ai_providers_updater` (`updated_by`),
  CONSTRAINT `fk_ai_providers_updater` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ai_question_draft_sessions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_question_draft_sessions` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_id` bigint NOT NULL,
  `test_id` bigint NOT NULL,
  `questions_json` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `expires_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `consumed_at` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_ai_qdraft_actor_test` (`actor_id`,`test_id`),
  KEY `idx_ai_qdraft_expiry` (`expires_at`),
  CONSTRAINT `chk_ai_qdraft_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'CONSUMED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ai_request_logs`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_request_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `provider_id` bigint DEFAULT NULL,
  `provider_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `model` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `prompt_tokens` int DEFAULT NULL,
  `completion_tokens` int DEFAULT NULL,
  `total_tokens` int DEFAULT NULL,
  `duration_ms` int DEFAULT NULL,
  `error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_logs_created` (`created_at`),
  KEY `idx_ai_logs_provider` (`provider_id`),
  KEY `idx_ai_logs_status` (`status`),
  KEY `fk_ai_logs_user` (`created_by`),
  CONSTRAINT `fk_ai_logs_user` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ai_request_logs_chk_1` CHECK ((`status` in (_utf8mb4'SUCCESS',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ai_system_prompts`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_system_prompts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_enabled` tinyint(1) NOT NULL DEFAULT '1',
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_ai_system_prompts_name` (`name`),
  KEY `idx_ai_system_prompts_enabled` (`is_enabled`),
  KEY `fk_ai_system_prompts_updater` (`updated_by`),
  CONSTRAINT `fk_ai_system_prompts_updater` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `assignment_feedback`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assignment_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `submission_id` bigint NOT NULL,
  `graded_by` bigint NOT NULL,
  `score` decimal(5,2) NOT NULL,
  `feedback` text COLLATE utf8mb4_unicode_ci,
  `rubric_scores` json DEFAULT NULL,
  `is_ai_generated` tinyint(1) DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `submission_id` (`submission_id`),
  KEY `fk_af_grader` (`graded_by`),
  CONSTRAINT `fk_af_grader` FOREIGN KEY (`graded_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_af_submission` FOREIGN KEY (`submission_id`) REFERENCES `assignment_submissions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `assignment_submissions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assignment_submissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `assignment_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `attachment_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'SUBMITTED',
  `is_late` tinyint(1) DEFAULT '0',
  `submitted_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_sub_asg_user` (`assignment_id`,`user_id`),
  KEY `idx_sub_asg` (`assignment_id`),
  KEY `fk_sub_user` (`user_id`),
  CONSTRAINT `fk_sub_asg` FOREIGN KEY (`assignment_id`) REFERENCES `assignments` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_sub_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `assignment_submissions_chk_1` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'SUBMITTED',_utf8mb4'GRADED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `assignments`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assignments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `class_id` bigint NOT NULL,
  `title` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `rubric` json DEFAULT NULL,
  `max_score` decimal(5,2) DEFAULT '100.00',
  `due_date` datetime DEFAULT NULL,
  `allow_late_submission` tinyint(1) DEFAULT '0',
  `attachment_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'DRAFT',
  `created_by` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_asg_class` (`class_id`),
  KEY `idx_asg_due` (`due_date`),
  KEY `fk_asg_creator` (`created_by`),
  CONSTRAINT `fk_asg_class` FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`),
  CONSTRAINT `fk_asg_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `assignments_chk_1` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PUBLISHED',_utf8mb4'CLOSED')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `class_co_lecturers`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `class_co_lecturers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `class_id` bigint NOT NULL,
  `lecturer_id` bigint NOT NULL,
  `assigned_by` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_co_lecturer` (`class_id`,`lecturer_id`),
  KEY `idx_co_lecturer_user_class` (`lecturer_id`,`class_id`),
  KEY `fk_co_lecturer_assigner` (`assigned_by`),
  CONSTRAINT `fk_co_lecturer_assigner` FOREIGN KEY (`assigned_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_co_lecturer_class` FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_co_lecturer_user` FOREIGN KEY (`lecturer_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `classes`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `classes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `lecturer_id` bigint NOT NULL,
  `subject_id` bigint DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `max_students` int DEFAULT '100',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'UPCOMING',
  `description` text COLLATE utf8mb4_unicode_ci,
  `created_by` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) DEFAULT '0',
  `approved_by` bigint DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `rejection_note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_class_lecturer` (`lecturer_id`),
  KEY `idx_class_status` (`status`),
  KEY `fk_class_creator` (`created_by`),
  KEY `idx_class_subject` (`subject_id`),
  KEY `fk_class_approver` (`approved_by`),
  KEY `idx_classes_subject_status_created` (`subject_id`,`status`,`created_at`),
  CONSTRAINT `fk_class_approver` FOREIGN KEY (`approved_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_class_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_class_lecturer` FOREIGN KEY (`lecturer_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_class_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_classes_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'ACTIVE',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB AUTO_INCREMENT=35 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `conversations`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `conversations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_lo_id` bigint NOT NULL,
  `user_hi_id` bigint NOT NULL,
  `last_message_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_conversation_pair` (`user_lo_id`,`user_hi_id`),
  KEY `fk_conversation_hi` (`user_hi_id`),
  CONSTRAINT `fk_conversation_hi` FOREIGN KEY (`user_hi_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_conversation_lo` FOREIGN KEY (`user_lo_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_conversation_pair_order` CHECK ((`user_lo_id` < `user_hi_id`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `enrollments`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `enrollments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `class_id` bigint NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'ACTIVE',
  `joined_via` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `joined_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `completed_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_enroll_user_class` (`user_id`,`class_id`),
  KEY `idx_enroll_class` (`class_id`),
  KEY `idx_enroll_status` (`status`),
  CONSTRAINT `fk_enroll_class` FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`),
  CONSTRAINT `fk_enroll_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_enrollments_joined_via` CHECK ((`joined_via` in (_utf8mb4'IMPORT',_utf8mb4'MANUAL',_utf8mb4'REQUEST'))),
  CONSTRAINT `chk_enrollments_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'REMOVED',_utf8mb4'COMPLETED',_utf8mb4'PENDING',_utf8mb4'REJECTED')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `flashcard_decks`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flashcard_decks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `class_id` bigint DEFAULT NULL,
  `subject_id` bigint DEFAULT NULL,
  `owner_id` bigint NOT NULL,
  `visibility` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'PRIVATE',
  `is_official` tinyint(1) DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) DEFAULT '0',
  `share_token` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_public` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_flashcard_decks_share_token` (`share_token`),
  KEY `idx_fd_owner` (`owner_id`),
  KEY `idx_fd_class` (`class_id`),
  KEY `idx_flashcard_decks_subject_id` (`subject_id`),
  CONSTRAINT `fk_fd_class` FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_fd_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_flashcard_decks_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`id`) ON DELETE SET NULL,
  CONSTRAINT `flashcard_decks_chk_1` CHECK ((`visibility` in (_utf8mb4'PRIVATE',_utf8mb4'SHARED',_utf8mb4'OFFICIAL')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `flashcard_reviews`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flashcard_reviews` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `flashcard_id` bigint NOT NULL,
  `quality` tinyint NOT NULL,
  `easiness_factor` decimal(5,2) DEFAULT '2.50',
  `interval_days` int DEFAULT '1',
  `repetitions` int DEFAULT '0',
  `next_review_at` datetime NOT NULL,
  `reviewed_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_fr_user_card` (`user_id`,`flashcard_id`),
  KEY `idx_fr_user_card` (`user_id`,`flashcard_id`),
  KEY `idx_fr_next` (`user_id`,`next_review_at`),
  KEY `fk_fr_card` (`flashcard_id`),
  CONSTRAINT `fk_fr_card` FOREIGN KEY (`flashcard_id`) REFERENCES `flashcards` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_fr_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `flashcard_reviews_chk_1` CHECK ((`quality` between 0 and 5))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `flashcards`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flashcards` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `deck_id` bigint NOT NULL,
  `front_text` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `front_image` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `back_image` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `alternatives_json` text COLLATE utf8mb4_unicode_ci,
  `back_text` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_fc_deck` (`deck_id`,`sort_order`),
  CONSTRAINT `fk_fc_deck` FOREIGN KEY (`deck_id`) REFERENCES `flashcard_decks` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `learning_progress`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `learning_progress` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `lesson_id` bigint NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'NOT_STARTED',
  `progress_percent` decimal(5,2) DEFAULT '0.00',
  `started_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_lp_user_lesson` (`user_id`,`lesson_id`),
  KEY `idx_lp_user` (`user_id`),
  KEY `idx_lp_lesson` (`lesson_id`),
  CONSTRAINT `fk_lp_lesson` FOREIGN KEY (`lesson_id`) REFERENCES `lessons` (`id`),
  CONSTRAINT `fk_lp_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `learning_progress_chk_1` CHECK ((`status` in (_utf8mb4'NOT_STARTED',_utf8mb4'IN_PROGRESS',_utf8mb4'COMPLETED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lecturer_assets`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lecturer_assets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `owner_lecturer_id` bigint NOT NULL,
  `sha256` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MANUAL_UPLOAD',
  `storage_provider` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'LOCAL',
  `storage_profile_code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `storage_key` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `original_filename` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `mime_type` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content_verified` tinyint(1) NOT NULL DEFAULT '0',
  `width` int DEFAULT NULL,
  `height` int DEFAULT NULL,
  `size_bytes` bigint NOT NULL DEFAULT '0',
  `asset_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `alt_text` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `visibility` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PRIVATE',
  `status` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TEMPORARY',
  `retention_until` datetime DEFAULT NULL,
  `lecturer_note` text COLLATE utf8mb4_unicode_ci,
  `tags_json` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lecturer_asset_owner_identity` (`id`,`owner_lecturer_id`),
  KEY `idx_lecturer_assets_owner` (`owner_lecturer_id`),
  KEY `idx_lecturer_assets_status` (`status`),
  KEY `idx_lecturer_assets_sha256` (`sha256`),
  KEY `idx_lecturer_assets_visibility_status` (`visibility`,`status`,`deleted_at`),
  KEY `idx_lecturer_assets_storage_key` (`storage_key`,`id`),
  KEY `idx_lecturer_asset_profile_key` (`storage_profile_code`,`storage_key`),
  CONSTRAINT `fk_lecturer_asset_storage_profile` FOREIGN KEY (`storage_profile_code`) REFERENCES `storage_profiles` (`profile_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lesson_attachments`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lesson_attachments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lesson_id` bigint NOT NULL,
  `original_filename` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `stored_path` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `mime_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `size_bytes` bigint NOT NULL,
  `uploaded_by` bigint NOT NULL,
  `uploaded_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `library_asset_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_la_lesson` (`lesson_id`),
  KEY `fk_la_uploader` (`uploaded_by`),
  KEY `idx_la_library_asset` (`library_asset_id`),
  CONSTRAINT `fk_la_lesson` FOREIGN KEY (`lesson_id`) REFERENCES `lessons` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_la_library_asset` FOREIGN KEY (`library_asset_id`) REFERENCES `library_assets` (`id`),
  CONSTRAINT `fk_la_uploader` FOREIGN KEY (`uploaded_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lesson_template_attachments`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lesson_template_attachments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_id` bigint NOT NULL,
  `library_asset_id` bigint NOT NULL,
  `original_filename` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `mime_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `size_bytes` bigint NOT NULL,
  `display_order` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_lta_template` (`template_id`),
  KEY `idx_lta_library_asset` (`library_asset_id`),
  CONSTRAINT `fk_lta_library_asset` FOREIGN KEY (`library_asset_id`) REFERENCES `library_assets` (`id`),
  CONSTRAINT `fk_lta_template` FOREIGN KEY (`template_id`) REFERENCES `lesson_templates` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_lta_size` CHECK ((`size_bytes` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lesson_templates`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lesson_templates` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `owner_id` bigint NOT NULL,
  `subject_id` bigint DEFAULT NULL,
  `title` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `chapter_title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Chương 1',
  `display_order` int NOT NULL DEFAULT '0',
  `chapter_order` int NOT NULL DEFAULT '1',
  `content_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content_richtext` longtext COLLATE utf8mb4_unicode_ci,
  `pdf_library_asset_id` bigint DEFAULT NULL,
  `video_provider` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `video_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `video_library_asset_id` bigint DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_lesson_templates_owner` (`owner_id`),
  KEY `idx_lesson_templates_owner_deleted` (`owner_id`,`is_deleted`),
  KEY `idx_lesson_templates_pdf_asset` (`pdf_library_asset_id`),
  KEY `idx_lesson_templates_video_asset` (`video_library_asset_id`),
  KEY `idx_lesson_templates_subject_chapter` (`subject_id`,`chapter_order`,`display_order`),
  CONSTRAINT `fk_lesson_templates_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_lesson_templates_pdf_asset` FOREIGN KEY (`pdf_library_asset_id`) REFERENCES `library_assets` (`id`),
  CONSTRAINT `fk_lesson_templates_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`id`),
  CONSTRAINT `fk_lesson_templates_video_asset` FOREIGN KEY (`video_library_asset_id`) REFERENCES `library_assets` (`id`),
  CONSTRAINT `chk_lesson_templates_content_shape` CHECK ((((`content_type` = _utf8mb4'RICHTEXT') and (`content_richtext` is not null)) or ((`content_type` = _utf8mb4'PDF') and (`pdf_library_asset_id` is not null)) or ((`content_type` = _utf8mb4'VIDEO') and (`video_provider` is not null) and ((`video_library_asset_id` is not null) or ((`video_url` is not null) and (`video_url` <> _utf8mb4'')))))),
  CONSTRAINT `chk_lesson_templates_content_type` CHECK ((`content_type` in (_utf8mb4'RICHTEXT',_utf8mb4'PDF',_utf8mb4'VIDEO')))
) ENGINE=InnoDB AUTO_INCREMENT=64 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lessons`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lessons` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `section_id` bigint NOT NULL,
  `title` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_order` smallint DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `content_richtext` longtext COLLATE utf8mb4_unicode_ci,
  `created_by` bigint NOT NULL,
  `published_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `content_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RICHTEXT',
  `pdf_attachment_id` bigint DEFAULT NULL,
  `video_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `video_provider` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `video_library_asset_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lesson_section_order` (`section_id`,`display_order`),
  KEY `idx_lesson_section_id` (`section_id`,`is_deleted`),
  KEY `idx_lesson_status` (`status`),
  KEY `fk_lesson_creator` (`created_by`),
  KEY `idx_lessons_pdf_attachment` (`pdf_attachment_id`),
  KEY `idx_lessons_video_library_asset` (`video_library_asset_id`),
  CONSTRAINT `fk_lesson_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_lesson_pdf_attachment` FOREIGN KEY (`pdf_attachment_id`) REFERENCES `lesson_attachments` (`id`),
  CONSTRAINT `fk_lesson_section` FOREIGN KEY (`section_id`) REFERENCES `sections` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_lesson_video_library_asset` FOREIGN KEY (`video_library_asset_id`) REFERENCES `library_assets` (`id`),
  CONSTRAINT `chk_lesson_content_shape` CHECK ((((`content_type` = _utf8mb4'RICHTEXT') and (`content_richtext` is not null)) or ((`content_type` = _utf8mb4'PDF') and (`pdf_attachment_id` is not null)) or ((`content_type` = _utf8mb4'VIDEO') and (`video_url` is not null) and (`video_provider` is not null)))),
  CONSTRAINT `chk_lesson_content_type` CHECK ((`content_type` in (_utf8mb4'RICHTEXT',_utf8mb4'PDF',_utf8mb4'VIDEO'))),
  CONSTRAINT `chk_lesson_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PUBLISHED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `library_assets`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `library_assets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `owner_id` bigint NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `original_filename` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `stored_path` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `mime_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `size_bytes` bigint NOT NULL,
  `kind` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_library_assets_owner` (`owner_id`),
  KEY `idx_library_assets_owner_kind` (`owner_id`,`kind`),
  KEY `idx_library_assets_owner_deleted` (`owner_id`,`is_deleted`),
  CONSTRAINT `fk_library_assets_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_library_assets_kind` CHECK ((`kind` in (_utf8mb4'DOCUMENT',_utf8mb4'VIDEO'))),
  CONSTRAINT `chk_library_assets_size` CHECK ((`size_bytes` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mail_outbox_jobs`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mail_outbox_jobs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `notification_id` bigint DEFAULT NULL,
  `recipient_email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `subject` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `body` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `source` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `attempt_count` int NOT NULL DEFAULT '0',
  `max_attempts` int NOT NULL DEFAULT '8',
  `available_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `lease_owner` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_expires_at` datetime(6) DEFAULT NULL,
  `last_error_code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_mail_outbox_notification` (`notification_id`),
  KEY `idx_mail_outbox_due` (`status`,`available_at`,`id`),
  KEY `idx_mail_outbox_expired_lease` (`status`,`lease_expires_at`,`id`),
  CONSTRAINT `fk_mail_outbox_notification` FOREIGN KEY (`notification_id`) REFERENCES `notifications` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_mail_outbox_attempts` CHECK (((`attempt_count` >= 0) and (`max_attempts` > 0))),
  CONSTRAINT `chk_mail_outbox_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'PROCESSING',_utf8mb4'RETRY',_utf8mb4'SENT',_utf8mb4'FAILED')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `messages`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint NOT NULL,
  `sender_id` bigint NOT NULL,
  `body` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `read_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_message_sender` (`sender_id`),
  KEY `idx_messages_conversation_created` (`conversation_id`,`created_at`),
  CONSTRAINT `fk_message_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `conversations` (`id`),
  CONSTRAINT `fk_message_sender` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notifications`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `title` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reference_type` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reference_id` bigint DEFAULT NULL,
  `is_read` tinyint(1) DEFAULT '0',
  `read_at` datetime DEFAULT NULL,
  `is_email_sent` tinyint(1) DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_noti_user_read` (`user_id`,`is_read`,`created_at`),
  CONSTRAINT `fk_noti_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `password_reset_tokens`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `password_reset_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `token` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` datetime NOT NULL,
  `used_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_preset_token` (`token`),
  KEY `idx_preset_user` (`user_id`),
  CONSTRAINT `fk_preset_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `permission_activities`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `permission_activities` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role_code` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `feature_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `target_user_id` bigint DEFAULT NULL,
  `message` text COLLATE utf8mb4_unicode_ci,
  `metadata` text COLLATE utf8mb4_unicode_ci,
  `performed_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_pact_type` (`type`),
  KEY `idx_pact_role` (`role_code`),
  KEY `idx_pact_target` (`target_user_id`),
  KEY `idx_pact_created` (`created_at`),
  KEY `fk_pact_actor` (`performed_by`),
  CONSTRAINT `fk_pact_actor` FOREIGN KEY (`performed_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_pact_target` FOREIGN KEY (`target_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `permissions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `feature_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `permission_group` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Nhóm quyền: AUTH, COURSE, LESSON, ...',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_perm_key` (`feature_key`),
  KEY `idx_perm_group` (`permission_group`)
) ENGINE=InnoDB AUTO_INCREMENT=144 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_ai_capability_test_runs`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_ai_capability_test_runs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `purpose_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `binding_revision` bigint NOT NULL,
  `required_capability` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `bounded_error_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tested_by` bigint NOT NULL,
  `started_at` datetime NOT NULL,
  `completed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_practice_ai_capability_test_purpose` (`purpose_code`,`started_at`),
  KEY `fk_practice_ai_capability_test_user` (`tested_by`),
  CONSTRAINT `fk_practice_ai_capability_test_binding` FOREIGN KEY (`purpose_code`) REFERENCES `practice_ai_purpose_bindings` (`purpose_code`),
  CONSTRAINT `fk_practice_ai_capability_test_user` FOREIGN KEY (`tested_by`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_practice_ai_capability_test_duration` CHECK (((`duration_ms` is null) or (`duration_ms` >= 0))),
  CONSTRAINT `chk_practice_ai_capability_test_status` CHECK (((`status` is null) or (`status` in (_utf8mb4'PASS',_utf8mb4'FAIL',_utf8mb4'CANCELLED'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_ai_execution_audits`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_ai_execution_audits` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `purpose_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `binding_revision` bigint NOT NULL,
  `provider_profile_revision` bigint NOT NULL,
  `provider_family` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_profile_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `model` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `transport_dialect` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `capability_digest` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `limits_digest` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `retention_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operation_code` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contract_identity_digest` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `data_class` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `bounded_error_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `started_at` datetime NOT NULL,
  `completed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_practice_ai_execution_purpose` (`purpose_code`,`started_at`),
  KEY `idx_practice_ai_execution_binding` (`purpose_code`,`binding_revision`),
  CONSTRAINT `chk_practice_ai_execution_capability_digest` CHECK (regexp_like(`capability_digest`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `chk_practice_ai_execution_contract_digest` CHECK (regexp_like(`contract_identity_digest`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `chk_practice_ai_execution_limits_digest` CHECK (regexp_like(`limits_digest`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `chk_practice_ai_execution_purpose` CHECK ((`purpose_code` in (_utf8mb4'PRACTICE_PDF_AUTHORING',_utf8mb4'PRACTICE_RL_EXPLANATION',_utf8mb4'PRACTICE_WRITING_EVALUATION',_utf8mb4'PRACTICE_SPEAKING_EVALUATION',_utf8mb4'PRACTICE_SPEAKING_STT',_utf8mb4'PRACTICE_SPEAKING_TTS',_utf8mb4'PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION'))),
  CONSTRAINT `chk_practice_ai_execution_revisions` CHECK (((`binding_revision` >= 0) and (`provider_profile_revision` >= 0))),
  CONSTRAINT `chk_practice_ai_execution_status` CHECK ((`status` in (_utf8mb4'RESOLVED',_utf8mb4'SUCCESS',_utf8mb4'FAILED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_ai_provider_profiles`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_ai_provider_profiles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `profile_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_family` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `base_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `credential_mode` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STATIC_BEARER',
  `credential_secret` varchar(4096) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '0',
  `revision` bigint NOT NULL DEFAULT '0',
  `updated_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_practice_ai_profile_code` (`profile_code`),
  KEY `fk_practice_ai_profile_updated_by` (`updated_by`),
  CONSTRAINT `fk_practice_ai_profile_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_practice_ai_profile_code` CHECK (regexp_like(`profile_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$')),
  CONSTRAINT `chk_practice_ai_profile_credential_material` CHECK ((((`credential_mode` = _utf8mb4'STATIC_BEARER') and (nullif(trim(`credential_secret`),_utf8mb4'') is not null)) or ((`credential_mode` = _utf8mb4'GOOGLE_CLOUD_ADC') and (`credential_secret` is null)))),
  CONSTRAINT `chk_practice_ai_profile_credential_mode` CHECK ((`credential_mode` in (_utf8mb4'STATIC_BEARER',_utf8mb4'GOOGLE_CLOUD_ADC'))),
  CONSTRAINT `chk_practice_ai_profile_revision` CHECK ((`revision` >= 0)),
  CONSTRAINT `chk_practice_ai_provider_family` CHECK ((`provider_family` = _utf8mb4'OPENAI_COMPATIBLE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_ai_purpose_bindings`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_ai_purpose_bindings` (
  `purpose_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_profile_id` bigint NOT NULL,
  `model` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `transport_dialect` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `capability_json` json NOT NULL,
  `limits_json` json NOT NULL,
  `retention_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `region_evidence_id` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `non_training_evidence_id` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `retention_evidence_id` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deletion_sla_evidence_id` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '0',
  `revision` bigint NOT NULL DEFAULT '0',
  `updated_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`purpose_code`),
  KEY `idx_practice_ai_binding_profile` (`provider_profile_id`),
  KEY `fk_practice_ai_binding_updated_by` (`updated_by`),
  CONSTRAINT `fk_practice_ai_binding_profile` FOREIGN KEY (`provider_profile_id`) REFERENCES `practice_ai_provider_profiles` (`id`),
  CONSTRAINT `fk_practice_ai_binding_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_practice_ai_binding_purpose` CHECK ((`purpose_code` in (_utf8mb4'PRACTICE_PDF_AUTHORING',_utf8mb4'PRACTICE_RL_EXPLANATION',_utf8mb4'PRACTICE_WRITING_EVALUATION',_utf8mb4'PRACTICE_SPEAKING_EVALUATION',_utf8mb4'PRACTICE_SPEAKING_STT',_utf8mb4'PRACTICE_SPEAKING_TTS',_utf8mb4'PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION'))),
  CONSTRAINT `chk_practice_ai_binding_revision` CHECK ((`revision` >= 0)),
  CONSTRAINT `chk_practice_ai_direct_audio_capability` CHECK (((`purpose_code` <> _utf8mb4'PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION') or (json_extract(`capability_json`,_utf8mb4'$.directAudioInput') = true))),
  CONSTRAINT `chk_practice_ai_direct_audio_policy` CHECK (((`purpose_code` <> _utf8mb4'PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION') or (`enabled` = false) or ((nullif(trim(`non_training_evidence_id`),_utf8mb4'') is not null) and (nullif(trim(`retention_evidence_id`),_utf8mb4'') is not null)))),
  CONSTRAINT `chk_practice_ai_retention_code` CHECK (regexp_like(`retention_code`,_utf8mb4'^[A-Z][A-Z0-9_]{1,63}$')),
  CONSTRAINT `chk_practice_ai_transport_dialect` CHECK ((`transport_dialect` = _utf8mb4'OPENAI_COMPATIBLE_V1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_asset_lifecycle_tasks`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_asset_lifecycle_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `asset_id` bigint DEFAULT NULL,
  `storage_profile_code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operation` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_storage_key` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `target_storage_key` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `attempt_count` int NOT NULL DEFAULT '0',
  `next_attempt_at` datetime DEFAULT NULL,
  `claim_token` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_error` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_practice_asset_task_asset` (`asset_id`),
  KEY `idx_practice_asset_task_due` (`status`,`next_attempt_at`,`id`),
  KEY `idx_practice_asset_task_source_active` (`source_storage_key`,`status`,`id`),
  KEY `idx_practice_asset_task_profile_source` (`storage_profile_code`,`source_storage_key`,`status`,`id`),
  CONSTRAINT `fk_practice_asset_task_asset` FOREIGN KEY (`asset_id`) REFERENCES `lecturer_assets` (`id`),
  CONSTRAINT `fk_practice_asset_task_storage_profile` FOREIGN KEY (`storage_profile_code`) REFERENCES `storage_profiles` (`profile_code`),
  CONSTRAINT `chk_practice_asset_task_operation` CHECK ((`operation` in (_utf8mb4'DELETE',_utf8mb4'PROMOTE_CLEANUP',_utf8mb4'ORPHAN_RECONCILE'))),
  CONSTRAINT `chk_practice_asset_task_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'RUNNING',_utf8mb4'COMPLETED',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_attempt_evaluation_jobs`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_attempt_evaluation_jobs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attempt_id` bigint NOT NULL,
  `operation` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_question_id` bigint DEFAULT NULL,
  `input_fingerprint` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `evaluation_contract_identity` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL,
  `job_status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `attempt_count` int NOT NULL DEFAULT '0',
  `max_attempts` int NOT NULL DEFAULT '3',
  `next_attempt_at` datetime DEFAULT NULL,
  `lease_owner` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_expires_at` datetime DEFAULT NULL,
  `expires_at` datetime NOT NULL,
  `retryable` tinyint(1) NOT NULL DEFAULT '0',
  `error_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `requested_by` bigint NOT NULL,
  `manual_retry_count` int NOT NULL DEFAULT '0',
  `last_retry_requested_at` datetime DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_practice_attempt_evaluation_job_attempt` (`attempt_id`),
  KEY `idx_practice_attempt_evaluation_job_due` (`job_status`,`next_attempt_at`,`lease_expires_at`,`id`),
  KEY `fk_practice_attempt_evaluation_job_requester` (`requested_by`),
  CONSTRAINT `fk_practice_attempt_evaluation_job_attempt` FOREIGN KEY (`attempt_id`) REFERENCES `practice_attempts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_practice_attempt_evaluation_job_requester` FOREIGN KEY (`requested_by`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_practice_attempt_evaluation_job_attempts` CHECK (((`attempt_count` >= 0) and (`max_attempts` between 1 and 10))),
  CONSTRAINT `chk_practice_attempt_evaluation_job_manual_retries` CHECK ((`manual_retry_count` between 0 and 2)),
  CONSTRAINT `chk_practice_attempt_evaluation_job_operation` CHECK ((`operation` in (_utf8mb4'SUBMIT',_utf8mb4'FULL_REEVALUATE',_utf8mb4'QUESTION_REEVALUATE'))),
  CONSTRAINT `chk_practice_attempt_evaluation_job_status` CHECK ((`job_status` in (_utf8mb4'QUEUED',_utf8mb4'PROCESSING',_utf8mb4'RETRY_WAIT',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'UNAVAILABLE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_attempts`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_attempts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `set_id` bigint NOT NULL,
  `test_id` bigint NOT NULL,
  `skill` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `section_id` bigint DEFAULT NULL,
  `status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'IN_PROGRESS',
  `analysis_status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NOT_REQUESTED',
  `score` decimal(6,2) DEFAULT NULL,
  `total_points` decimal(6,2) DEFAULT NULL,
  `score_unit` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `earned_points` decimal(8,2) DEFAULT NULL,
  `score_percentage` decimal(6,2) DEFAULT NULL,
  `answers_json` json DEFAULT NULL,
  `ai_feedback_json` json DEFAULT NULL,
  `analysis_requested_at` datetime DEFAULT NULL,
  `analysis_completed_at` datetime DEFAULT NULL,
  `analysis_engine` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `analysis_error_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `started_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deadline_at` datetime NOT NULL,
  `last_saved_at` datetime DEFAULT NULL,
  `deadline_reconcile_attempts` int NOT NULL DEFAULT '0',
  `deadline_reconcile_next_at` datetime DEFAULT NULL,
  `deadline_reconcile_error_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deadline_reconcile_quarantined_at` datetime DEFAULT NULL,
  `submitted_at` datetime DEFAULT NULL,
  `discarded_at` datetime(6) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `lock_version` bigint NOT NULL DEFAULT '0',
  `published_version_id` bigint DEFAULT NULL,
  `set_version_id` bigint DEFAULT NULL,
  `test_version_id` bigint DEFAULT NULL,
  `section_version_id` bigint DEFAULT NULL,
  `version_compatibility_status` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `version_compatibility_note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `activity_at` datetime GENERATED ALWAYS AS (coalesce(`submitted_at`,`updated_at`,`created_at`)) STORED,
  PRIMARY KEY (`id`),
  KEY `idx_pa_user_test_skill` (`user_id`,`test_id`,`skill`),
  KEY `idx_pa_user_set` (`user_id`,`set_id`),
  KEY `idx_pa_test` (`test_id`),
  KEY `fk_pa_set` (`set_id`),
  KEY `idx_pa_user_status_created_id` (`user_id`,`status`,`created_at`,`id`),
  KEY `idx_pa_version_lock` (`published_version_id`,`set_version_id`,`test_version_id`,`section_version_id`),
  KEY `fk_pa_set_version` (`set_version_id`),
  KEY `fk_pa_test_version` (`test_version_id`),
  KEY `fk_pa_section_version` (`section_version_id`),
  KEY `idx_practice_attempts_user_writing_activity` (`user_id`,`skill`,`activity_at` DESC,`id` DESC,`status`),
  KEY `idx_practice_attempts_user_set_activity` (`user_id`,`set_id`,`activity_at` DESC,`id` DESC,`status`),
  KEY `idx_practice_attempts_user_section_status` (`user_id`,`section_id`,`status`),
  KEY `idx_practice_attempts_user_resume_deadline` (`user_id`,`status`,`deadline_at`,`activity_at` DESC,`id` DESC),
  KEY `idx_practice_attempts_deadline_reconcile` (`status`,`deadline_reconcile_quarantined_at`,`deadline_reconcile_next_at`,`deadline_at`,`id`),
  CONSTRAINT `fk_pa_published_version` FOREIGN KEY (`published_version_id`) REFERENCES `practice_published_versions` (`id`),
  CONSTRAINT `fk_pa_section_version` FOREIGN KEY (`section_version_id`) REFERENCES `practice_section_versions` (`id`),
  CONSTRAINT `fk_pa_set` FOREIGN KEY (`set_id`) REFERENCES `practice_sets` (`id`),
  CONSTRAINT `fk_pa_set_version` FOREIGN KEY (`set_version_id`) REFERENCES `practice_set_versions` (`id`),
  CONSTRAINT `fk_pa_test_version` FOREIGN KEY (`test_version_id`) REFERENCES `practice_test_versions` (`id`),
  CONSTRAINT `fk_pa_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_pa_analysis` CHECK ((`analysis_status` in (_utf8mb4'NOT_REQUESTED',_utf8mb4'QUEUED',_utf8mb4'PROCESSING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'UNAVAILABLE'))),
  CONSTRAINT `chk_pa_deadline_reconcile_attempts` CHECK ((`deadline_reconcile_attempts` between 0 and 5)),
  CONSTRAINT `chk_pa_deadline_reconcile_quarantine` CHECK (((`deadline_reconcile_quarantined_at` is null) or ((`deadline_reconcile_attempts` = 5) and (`deadline_reconcile_next_at` is null)))),
  CONSTRAINT `chk_pa_discarded_at` CHECK ((((`status` = _utf8mb4'DISCARDED') and (`discarded_at` is not null)) or ((`status` <> _utf8mb4'DISCARDED') and (`discarded_at` is null)))),
  CONSTRAINT `chk_pa_skill` CHECK ((`skill` in (_utf8mb4'READING',_utf8mb4'LISTENING',_utf8mb4'WRITING',_utf8mb4'SPEAKING'))),
  CONSTRAINT `chk_pa_status` CHECK ((`status` in (_utf8mb4'IN_PROGRESS',_utf8mb4'SUBMITTED',_utf8mb4'GRADED',_utf8mb4'DISCARDED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_authoring_candidate_apply_events`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_authoring_candidate_apply_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `candidate_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `apply_request_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `candidate_version` bigint NOT NULL,
  `candidate_digest` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `base_draft_version` int NOT NULL,
  `result` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `result_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `result_draft_version` int DEFAULT NULL,
  `actor_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_practice_authoring_candidate_apply_request` (`candidate_id`,`apply_request_id`),
  KEY `idx_practice_authoring_candidate_apply_actor` (`actor_id`,`created_at`),
  CONSTRAINT `fk_practice_authoring_candidate_apply_actor` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_practice_authoring_candidate_apply_candidate` FOREIGN KEY (`candidate_id`) REFERENCES `practice_authoring_candidates` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_practice_authoring_candidate_apply_digest` CHECK (regexp_like(`candidate_digest`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `chk_practice_authoring_candidate_apply_draft` CHECK ((((`result` = _utf8mb4'DRAFT_APPLIED') and (`result_draft_version` is not null)) or ((`result` <> _utf8mb4'DRAFT_APPLIED') and (`result_draft_version` is null)))),
  CONSTRAINT `chk_practice_authoring_candidate_apply_result` CHECK ((`result` in (_utf8mb4'DRAFT_APPLIED',_utf8mb4'CONFLICT',_utf8mb4'REJECTED'))),
  CONSTRAINT `chk_practice_authoring_candidate_apply_version` CHECK (((`candidate_version` >= 0) and (`base_draft_version` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_authoring_candidates`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_authoring_candidates` (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_id` bigint NOT NULL,
  `source_kind` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_contract_version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_digest` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_revision` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_operation` varchar(12) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE',
  `target_draft_id` bigint NOT NULL,
  `target_test_no` int NOT NULL,
  `target_skill` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_lesson_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `base_draft_version` int NOT NULL,
  `state` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `normalizer_version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `validator_version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `candidate_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `content_digest` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `warning_acknowledged_at` datetime(6) DEFAULT NULL,
  `warning_acknowledged_by` bigint DEFAULT NULL,
  `expires_at` datetime(6) NOT NULL,
  `applied_at` datetime(6) DEFAULT NULL,
  `applied_draft_version` int DEFAULT NULL,
  `lock_version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_practice_authoring_candidate_idempotency` (`owner_id`,`source_kind`,`source_contract_version`,`source_digest`,`source_revision`,`source_operation`,`target_draft_id`,`target_test_no`,`target_skill`,`target_lesson_code`,`base_draft_version`,`normalizer_version`),
  KEY `idx_practice_authoring_candidate_owner_state_expiry` (`owner_id`,`state`,`expires_at`),
  KEY `idx_practice_authoring_candidate_target` (`target_draft_id`,`base_draft_version`),
  KEY `fk_practice_authoring_candidate_warning_actor` (`warning_acknowledged_by`),
  CONSTRAINT `fk_practice_authoring_candidate_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_practice_authoring_candidate_target` FOREIGN KEY (`target_draft_id`) REFERENCES `practice_drafts` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_practice_authoring_candidate_warning_actor` FOREIGN KEY (`warning_acknowledged_by`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_practice_authoring_candidate_applied` CHECK ((((`state` = _utf8mb4'APPLIED') and (`applied_at` is not null) and (`applied_draft_version` is not null)) or ((`state` <> _utf8mb4'APPLIED') and (`applied_at` is null) and (`applied_draft_version` is null)))),
  CONSTRAINT `chk_practice_authoring_candidate_digest` CHECK ((regexp_like(`source_digest`,_utf8mb4'^[0-9a-f]{64}$') and regexp_like(`content_digest`,_utf8mb4'^[0-9a-f]{64}$'))),
  CONSTRAINT `chk_practice_authoring_candidate_expiry` CHECK ((`expires_at` >= (`created_at` + interval 7 day))),
  CONSTRAINT `chk_practice_authoring_candidate_operation` CHECK ((`source_operation` in (_utf8mb4'NONE',_utf8mb4'EXTRACT',_utf8mb4'GENERATE'))),
  CONSTRAINT `chk_practice_authoring_candidate_skill` CHECK ((`target_skill` in (_utf8mb4'READING',_utf8mb4'LISTENING',_utf8mb4'WRITING',_utf8mb4'SPEAKING'))),
  CONSTRAINT `chk_practice_authoring_candidate_source_kind` CHECK ((`source_kind` in (_utf8mb4'QUICK_EXCEL',_utf8mb4'ADVANCED_EXCEL_V2',_utf8mb4'LEGACY_EXCEL_V1',_utf8mb4'PDF_AI'))),
  CONSTRAINT `chk_practice_authoring_candidate_state` CHECK ((`state` in (_utf8mb4'PARSED',_utf8mb4'NORMALIZED',_utf8mb4'VALIDATED',_utf8mb4'REVIEWING',_utf8mb4'READY_TO_APPLY',_utf8mb4'APPLIED',_utf8mb4'FAILED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED'))),
  CONSTRAINT `chk_practice_authoring_candidate_target` CHECK (((`target_test_no` > 0) and (`base_draft_version` >= 0) and regexp_like(`target_lesson_code`,_utf8mb4'^[RLSW][1-9][0-9]*$')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_drafts`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_drafts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `scope` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `class_id` bigint DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_id` bigint NOT NULL,
  `draft_json` longtext COLLATE utf8mb4_unicode_ci,
  `version` int DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `published_set_id` bigint DEFAULT NULL,
  `creation_method` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `draft_schema_version` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_practice_draft_owner_identity` (`id`,`owner_id`),
  KEY `fk_pd_published_set` (`published_set_id`),
  CONSTRAINT `fk_pd_published_set` FOREIGN KEY (`published_set_id`) REFERENCES `practice_sets` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_edit_logs`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_edit_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `set_id` bigint NOT NULL,
  `edited_by` bigint NOT NULL,
  `change_summary` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `change_details_json` longtext COLLATE utf8mb4_unicode_ci,
  `before_snapshot_json` longtext COLLATE utf8mb4_unicode_ci,
  `after_snapshot_json` longtext COLLATE utf8mb4_unicode_ci,
  `edit_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `edited_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_pel_set` (`set_id`),
  CONSTRAINT `fk_pel_set` FOREIGN KEY (`set_id`) REFERENCES `practice_sets` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_explanation_editorial_revisions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_explanation_editorial_revisions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `draft_id` bigint NOT NULL,
  `question_client_id` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `revision_no` int NOT NULL,
  `strategy_registry_version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `strategy_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `strategy_version` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `authority_fingerprint` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `editorial_state` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `explanation_json` json NOT NULL,
  `created_by` bigint NOT NULL,
  `approved_by` bigint DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `invalidated_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_practice_explanation_editorial_revision` (`draft_id`,`question_client_id`,`revision_no`),
  KEY `idx_practice_explanation_editorial_current` (`draft_id`,`question_client_id`,`editorial_state`,`revision_no`),
  KEY `idx_practice_explanation_editorial_fingerprint` (`authority_fingerprint`),
  KEY `fk_practice_explanation_editorial_created_by` (`created_by`),
  KEY `fk_practice_explanation_editorial_approved_by` (`approved_by`),
  CONSTRAINT `fk_practice_explanation_editorial_approved_by` FOREIGN KEY (`approved_by`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_practice_explanation_editorial_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_practice_explanation_editorial_draft` FOREIGN KEY (`draft_id`) REFERENCES `practice_drafts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_practice_explanation_editorial_approval` CHECK ((((`editorial_state` = _utf8mb4'APPROVED') and (`approved_by` is not null) and (`approved_at` is not null) and (`invalidated_at` is null)) or ((`editorial_state` = _utf8mb4'GENERATED_DRAFT') and (`approved_by` is null) and (`approved_at` is null) and (`invalidated_at` is null)) or ((`editorial_state` = _utf8mb4'INVALIDATED') and (`invalidated_at` is not null)))),
  CONSTRAINT `ck_practice_explanation_editorial_state` CHECK ((`editorial_state` in (_utf8mb4'GENERATED_DRAFT',_utf8mb4'APPROVED',_utf8mb4'INVALIDATED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_material_references`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_material_references` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `asset_id` bigint NOT NULL,
  `draft_id` bigint DEFAULT NULL,
  `set_id` bigint DEFAULT NULL,
  `published_version_id` bigint DEFAULT NULL,
  `reference_scope` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `placement` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MATERIAL',
  `reference_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `reference_metadata_json` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_practice_material_draft_ref` (`asset_id`,`reference_scope`,`draft_id`,`placement`,`reference_key`),
  UNIQUE KEY `uk_practice_material_version_ref` (`asset_id`,`reference_scope`,`published_version_id`,`placement`),
  KEY `fk_practice_material_set` (`set_id`),
  KEY `idx_practice_material_draft` (`draft_id`,`asset_id`),
  KEY `idx_practice_material_version` (`published_version_id`,`asset_id`),
  KEY `idx_practice_material_asset` (`asset_id`,`reference_scope`),
  CONSTRAINT `fk_practice_material_asset` FOREIGN KEY (`asset_id`) REFERENCES `lecturer_assets` (`id`),
  CONSTRAINT `fk_practice_material_draft` FOREIGN KEY (`draft_id`) REFERENCES `practice_drafts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_practice_material_set` FOREIGN KEY (`set_id`) REFERENCES `practice_sets` (`id`),
  CONSTRAINT `fk_practice_material_version` FOREIGN KEY (`published_version_id`) REFERENCES `practice_published_versions` (`id`),
  CONSTRAINT `chk_practice_material_scope` CHECK ((((`reference_scope` = _utf8mb4'DRAFT') and (`draft_id` is not null) and (`set_id` is null) and (`published_version_id` is null)) or ((`reference_scope` = _utf8mb4'PUBLISHED_VERSION') and (`draft_id` is null) and (`set_id` is not null) and (`published_version_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_published_versions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_published_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `set_id` bigint NOT NULL,
  `version_number` int NOT NULL,
  `status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content_hash` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `published_by` bigint DEFAULT NULL,
  `published_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ppv_set_version` (`set_id`,`version_number`),
  KEY `idx_ppv_set_status_version` (`set_id`,`status`,`version_number`),
  CONSTRAINT `fk_ppv_set` FOREIGN KEY (`set_id`) REFERENCES `practice_sets` (`id`),
  CONSTRAINT `chk_ppv_status` CHECK ((`status` in (_utf8mb4'PUBLISHED',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_question_group_versions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_question_group_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `published_version_id` bigint NOT NULL,
  `section_version_id` bigint NOT NULL,
  `group_id` bigint NOT NULL,
  `group_label` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `question_from` int NOT NULL,
  `question_to` int NOT NULL,
  `instruction` text COLLATE utf8mb4_unicode_ci,
  `stimulus_type` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `instruction_language_tag` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'vi',
  `stimulus_language_tag` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ko',
  `passage_text` longtext COLLATE utf8mb4_unicode_ci,
  `transcript_text` longtext COLLATE utf8mb4_unicode_ci,
  `image_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stimulus_provenance_json` json DEFAULT NULL,
  `audio_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `example_json` json DEFAULT NULL,
  `display_order` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pqgv_section_version` (`section_version_id`,`display_order`),
  KEY `idx_pqgv_group` (`group_id`),
  KEY `fk_pqgv_published` (`published_version_id`),
  CONSTRAINT `fk_pqgv_published` FOREIGN KEY (`published_version_id`) REFERENCES `practice_published_versions` (`id`),
  CONSTRAINT `fk_pqgv_section_version` FOREIGN KEY (`section_version_id`) REFERENCES `practice_section_versions` (`id`),
  CONSTRAINT `chk_practice_question_group_versions_instruction_language` CHECK ((`instruction_language_tag` in (_utf8mb4'ko',_utf8mb4'vi'))),
  CONSTRAINT `chk_practice_question_group_versions_stimulus_language` CHECK ((`stimulus_language_tag` in (_utf8mb4'ko',_utf8mb4'vi')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_question_groups`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_question_groups` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `set_id` bigint NOT NULL,
  `section_id` bigint DEFAULT NULL,
  `group_label` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `question_from` int NOT NULL,
  `question_to` int NOT NULL,
  `instruction` text COLLATE utf8mb4_unicode_ci,
  `stimulus_type` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `instruction_language_tag` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'vi',
  `stimulus_language_tag` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ko',
  `passage_text` longtext COLLATE utf8mb4_unicode_ci,
  `transcript_text` longtext COLLATE utf8mb4_unicode_ci,
  `image_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stimulus_provenance_json` json DEFAULT NULL,
  `audio_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `example_json` json DEFAULT NULL,
  `display_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `fk_pqg_set` (`set_id`),
  KEY `fk_pqg_section` (`section_id`),
  CONSTRAINT `fk_pqg_section` FOREIGN KEY (`section_id`) REFERENCES `practice_sections` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_pqg_set` FOREIGN KEY (`set_id`) REFERENCES `practice_sets` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_practice_question_groups_instruction_language` CHECK ((`instruction_language_tag` in (_utf8mb4'ko',_utf8mb4'vi'))),
  CONSTRAINT `chk_practice_question_groups_stimulus_language` CHECK ((`stimulus_language_tag` in (_utf8mb4'ko',_utf8mb4'vi')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_question_versions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_question_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `published_version_id` bigint NOT NULL,
  `section_version_id` bigint NOT NULL,
  `group_version_id` bigint DEFAULT NULL,
  `question_id` bigint NOT NULL,
  `question_no` int NOT NULL,
  `question_type` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `prompt` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `options_json` json DEFAULT NULL,
  `question_content_json` json DEFAULT NULL,
  `answer_key` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `answer_spec_json` json DEFAULT NULL,
  `explanation` text COLLATE utf8mb4_unicode_ci,
  `explanation_strategy_registry_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `explanation_strategy_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `explanation_strategy_version` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `points` decimal(5,2) NOT NULL,
  `display_order` int NOT NULL,
  `writing_task_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pqv_section_version` (`section_version_id`,`display_order`,`question_no`),
  KEY `idx_pqv_question` (`question_id`),
  KEY `fk_pqv_published` (`published_version_id`),
  KEY `fk_pqv_group_version` (`group_version_id`),
  CONSTRAINT `fk_pqv_group_version` FOREIGN KEY (`group_version_id`) REFERENCES `practice_question_group_versions` (`id`),
  CONSTRAINT `fk_pqv_published` FOREIGN KEY (`published_version_id`) REFERENCES `practice_published_versions` (`id`),
  CONSTRAINT `fk_pqv_section_version` FOREIGN KEY (`section_version_id`) REFERENCES `practice_section_versions` (`id`),
  CONSTRAINT `chk_pqv_type` CHECK ((`question_type` in (_utf8mb4'SINGLE_CHOICE',_utf8mb4'MULTIPLE_ANSWER',_utf8mb4'MATCHING',_utf8mb4'FILL_BLANK',_utf8mb4'TRUE_FALSE_NOT_GIVEN',_utf8mb4'ESSAY',_utf8mb4'SPEAKING'))),
  CONSTRAINT `chk_pqv_writing_task` CHECK ((((`question_type` <> _utf8mb4'ESSAY') and (`writing_task_type` is null)) or ((`question_type` = _utf8mb4'ESSAY') and (`question_no` in (51,52,53,54)) and (`writing_task_type` is not null) and (`writing_task_type` in (_utf8mb4'Q51',_utf8mb4'Q52',_utf8mb4'Q53',_utf8mb4'Q54')) and (`question_no` = cast(substr(`writing_task_type`,2) as unsigned)))))
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_questions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_questions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `set_id` bigint NOT NULL,
  `group_id` bigint DEFAULT NULL,
  `question_no` int NOT NULL,
  `question_type` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `prompt` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `options_json` json DEFAULT NULL,
  `question_content_json` json DEFAULT NULL,
  `answer_key` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `answer_spec_json` json DEFAULT NULL,
  `explanation` text COLLATE utf8mb4_unicode_ci,
  `explanation_strategy_registry_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `explanation_strategy_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `explanation_strategy_version` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `points` decimal(5,2) NOT NULL DEFAULT '1.00',
  `display_order` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `writing_task_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pq_set_order` (`set_id`,`display_order`),
  KEY `fk_pq_group` (`group_id`),
  KEY `idx_practice_questions_set_writing_task` (`set_id`,`writing_task_type`),
  CONSTRAINT `fk_pq_group` FOREIGN KEY (`group_id`) REFERENCES `practice_question_groups` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_pq_set` FOREIGN KEY (`set_id`) REFERENCES `practice_sets` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_pq_type` CHECK ((`question_type` in (_utf8mb4'SINGLE_CHOICE',_utf8mb4'MULTIPLE_ANSWER',_utf8mb4'MATCHING',_utf8mb4'FILL_BLANK',_utf8mb4'TRUE_FALSE_NOT_GIVEN',_utf8mb4'ESSAY',_utf8mb4'SPEAKING'))),
  CONSTRAINT `chk_pq_writing_task` CHECK ((((`question_type` <> _utf8mb4'ESSAY') and (`writing_task_type` is null)) or ((`question_type` = _utf8mb4'ESSAY') and (`question_no` in (51,52,53,54)) and (`writing_task_type` is not null) and (`writing_task_type` in (_utf8mb4'Q51',_utf8mb4'Q52',_utf8mb4'Q53',_utf8mb4'Q54')) and (`question_no` = cast(substr(`writing_task_type`,2) as unsigned)))))
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_section_versions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_section_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `published_version_id` bigint NOT NULL,
  `test_version_id` bigint NOT NULL,
  `section_id` bigint NOT NULL,
  `title` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `skill` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `section_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `instructions` text COLLATE utf8mb4_unicode_ci,
  `delivery_json` json DEFAULT NULL,
  `duration_minutes` int DEFAULT NULL,
  `total_points` decimal(6,2) DEFAULT NULL,
  `display_order` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pscv_published_section` (`published_version_id`,`section_id`),
  KEY `idx_pscv_test_version` (`test_version_id`,`display_order`),
  KEY `fk_pscv_section` (`section_id`),
  CONSTRAINT `fk_pscv_published` FOREIGN KEY (`published_version_id`) REFERENCES `practice_published_versions` (`id`),
  CONSTRAINT `fk_pscv_test_version` FOREIGN KEY (`test_version_id`) REFERENCES `practice_test_versions` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_sections`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_sections` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `set_id` bigint NOT NULL,
  `test_id` bigint DEFAULT NULL,
  `title` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `skill` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `section_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `instructions` text COLLATE utf8mb4_unicode_ci,
  `delivery_json` json DEFAULT NULL,
  `duration_minutes` int DEFAULT NULL,
  `total_points` decimal(6,2) DEFAULT NULL,
  `display_order` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_psec_test` (`test_id`),
  KEY `idx_practice_sections_set_skill` (`set_id`,`skill`),
  CONSTRAINT `fk_ps_set` FOREIGN KEY (`set_id`) REFERENCES `practice_sets` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_psec_test` FOREIGN KEY (`test_id`) REFERENCES `practice_tests` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_set_versions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_set_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `published_version_id` bigint NOT NULL,
  `set_id` bigint NOT NULL,
  `title` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `skill` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `class_id` bigint DEFAULT NULL,
  `metadata_json` json DEFAULT NULL,
  `creation_method` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cover_image_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_psv_published` (`published_version_id`),
  KEY `idx_psv_set` (`set_id`),
  CONSTRAINT `fk_psv_published` FOREIGN KEY (`published_version_id`) REFERENCES `practice_published_versions` (`id`),
  CONSTRAINT `fk_psv_set` FOREIGN KEY (`set_id`) REFERENCES `practice_sets` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_sets`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_sets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `skill` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'GLOBAL',
  `class_id` bigint DEFAULT NULL,
  `source_pdf_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `audio_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `metadata_json` json DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `archived_at` datetime DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `creation_method` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MANUAL',
  `cover_image_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ps_skill_status` (`skill`,`status`,`is_deleted`),
  KEY `idx_ps_scope_class` (`scope`,`class_id`),
  KEY `fk_ps_class` (`class_id`),
  KEY `idx_practice_sets_catalog_page` (`status`,`is_deleted`,`created_at`,`id`),
  KEY `idx_practice_sets_catalog_class_page` (`status`,`is_deleted`,`scope`,`class_id`,`created_at`,`id`),
  KEY `idx_practice_sets_catalog_owner_page` (`status`,`is_deleted`,`created_by`,`created_at`,`id`),
  KEY `idx_practice_sets_created_by` (`created_by`),
  CONSTRAINT `fk_ps_class` FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_ps_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_ps_scope` CHECK ((`scope` in (_utf8mb4'GLOBAL',_utf8mb4'CLASS'))),
  CONSTRAINT `chk_ps_skill` CHECK ((`skill` in (_utf8mb4'READING',_utf8mb4'LISTENING',_utf8mb4'WRITING',_utf8mb4'SPEAKING',_utf8mb4'MIXED'))),
  CONSTRAINT `chk_ps_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PUBLISHED',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_speaking_audio_consent_events`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_speaking_audio_consent_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_key` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `consent_chain_key` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `learner_id` bigint NOT NULL,
  `attempt_id` bigint NOT NULL,
  `purpose_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `disclosure_version` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `evidence_id` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `occurred_at` datetime NOT NULL,
  `recorded_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_psace_event_key` (`event_key`),
  UNIQUE KEY `uk_psace_evidence_id` (`evidence_id`),
  UNIQUE KEY `uk_psace_chain_event` (`consent_chain_key`,`event_type`),
  KEY `idx_psace_authority` (`learner_id`,`attempt_id`,`purpose_code`,`occurred_at`,`id`),
  KEY `fk_psace_attempt` (`attempt_id`),
  CONSTRAINT `fk_psace_attempt` FOREIGN KEY (`attempt_id`) REFERENCES `practice_attempts` (`id`),
  CONSTRAINT `fk_psace_learner` FOREIGN KEY (`learner_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_psace_chain_key` CHECK (regexp_like(`consent_chain_key`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9._:-]{7,79}$')),
  CONSTRAINT `chk_psace_event` CHECK ((`event_type` in (_utf8mb4'GRANTED',_utf8mb4'WITHDRAWN'))),
  CONSTRAINT `chk_psace_event_key` CHECK (regexp_like(`event_key`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9._:-]{7,79}$')),
  CONSTRAINT `chk_psace_purpose` CHECK ((`purpose_code` = _utf8mb4'PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_speaking_audio_grant_manager_events`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_speaking_audio_grant_manager_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_key` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `subject_user_id` bigint NOT NULL,
  `authority_code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `evidence_id` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_user_id` bigint NOT NULL,
  `occurred_at` datetime NOT NULL,
  `recorded_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_psagme_event_key` (`event_key`),
  UNIQUE KEY `uk_psagme_evidence_id` (`evidence_id`),
  KEY `idx_psagme_current` (`subject_user_id`,`authority_code`,`occurred_at`,`id`),
  KEY `fk_psagme_actor` (`actor_user_id`),
  CONSTRAINT `fk_psagme_actor` FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_psagme_subject` FOREIGN KEY (`subject_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_psagme_authority` CHECK ((`authority_code` in (_utf8mb4'ACADEMIC_LEADER',_utf8mb4'PRIVACY_RELEASE_OWNER'))),
  CONSTRAINT `chk_psagme_event` CHECK ((`event_type` in (_utf8mb4'ASSIGNED',_utf8mb4'REVOKED'))),
  CONSTRAINT `chk_psagme_event_key` CHECK (regexp_like(`event_key`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9._:-]{7,79}$'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_speaking_audio_reviewer_grants`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_speaking_audio_reviewer_grants` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `grant_key` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `attempt_id` bigint NOT NULL,
  `reviewer_id` bigint NOT NULL,
  `granted_by` bigint NOT NULL,
  `purpose_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `evidence_id` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `granted_at` datetime NOT NULL,
  `expires_at` datetime NOT NULL,
  `revoked_at` datetime DEFAULT NULL,
  `revoked_by` bigint DEFAULT NULL,
  `revoke_evidence_id` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lock_version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_psarg_grant_key` (`grant_key`),
  UNIQUE KEY `uk_psarg_evidence_id` (`evidence_id`),
  KEY `idx_psarg_authority` (`reviewer_id`,`attempt_id`,`purpose_code`,`expires_at`,`revoked_at`),
  KEY `fk_psarg_attempt` (`attempt_id`),
  KEY `fk_psarg_granted_by` (`granted_by`),
  KEY `fk_psarg_revoked_by` (`revoked_by`),
  CONSTRAINT `fk_psarg_attempt` FOREIGN KEY (`attempt_id`) REFERENCES `practice_attempts` (`id`),
  CONSTRAINT `fk_psarg_granted_by` FOREIGN KEY (`granted_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_psarg_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_psarg_revoked_by` FOREIGN KEY (`revoked_by`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_psarg_expiry` CHECK ((`expires_at` > `granted_at`)),
  CONSTRAINT `chk_psarg_grant_key` CHECK (regexp_like(`grant_key`,_utf8mb4'^[A-Za-z0-9][A-Za-z0-9._:-]{7,79}$')),
  CONSTRAINT `chk_psarg_purpose` CHECK ((`purpose_code` = _utf8mb4'PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION')),
  CONSTRAINT `chk_psarg_revocation` CHECK ((((`revoked_at` is null) and (`revoked_by` is null) and (`revoke_evidence_id` is null)) or ((`revoked_at` is not null) and (`revoked_by` is not null) and (`revoke_evidence_id` is not null) and (`revoked_at` >= `granted_at`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_speaking_media`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_speaking_media` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attempt_id` bigint NOT NULL,
  `question_id` bigint NOT NULL,
  `storage_provider` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `storage_profile_code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `storage_key` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `mime_type` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `container` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `codec` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `byte_size` bigint NOT NULL,
  `duration_ms` bigint NOT NULL,
  `content_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `lock_version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_psm_profile_storage` (`storage_profile_code`,`storage_key`),
  KEY `idx_psm_attempt_question_status` (`attempt_id`,`question_id`,`status`),
  KEY `idx_psm_attempt_status` (`attempt_id`,`status`),
  KEY `fk_psm_question` (`question_id`),
  CONSTRAINT `fk_psm_attempt` FOREIGN KEY (`attempt_id`) REFERENCES `practice_attempts` (`id`),
  CONSTRAINT `fk_psm_question` FOREIGN KEY (`question_id`) REFERENCES `practice_questions` (`id`),
  CONSTRAINT `fk_psm_storage_profile` FOREIGN KEY (`storage_profile_code`) REFERENCES `storage_profiles` (`profile_code`),
  CONSTRAINT `chk_psm_byte_size` CHECK ((`byte_size` > 0)),
  CONSTRAINT `chk_psm_duration_ms` CHECK ((`duration_ms` > 0)),
  CONSTRAINT `chk_psm_status` CHECK ((`status` in (_utf8mb4'UNREFERENCED_TEMPORARY',_utf8mb4'READY',_utf8mb4'SUPERSEDED',_utf8mb4'DELETION_PENDING',_utf8mb4'DELETED'))),
  CONSTRAINT `chk_psm_storage_provider` CHECK ((`storage_provider` in (_utf8mb4'LOCAL',_utf8mb4'OBJECT_STORAGE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_speaking_media_cleanup_tasks`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_speaking_media_cleanup_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `cleanup_reason` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `authorization_evidence_id` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `media_id` bigint DEFAULT NULL,
  `storage_provider` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `storage_profile_code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `storage_key` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `due_at` datetime NOT NULL,
  `next_attempt_at` datetime NOT NULL,
  `status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `claim_token` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_expires_at` datetime DEFAULT NULL,
  `attempt_count` bigint NOT NULL DEFAULT '0',
  `last_error_code` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `lock_version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_psm_cleanup_profile_storage` (`storage_profile_code`,`storage_key`),
  KEY `idx_psm_cleanup_status_next_attempt` (`status`,`next_attempt_at`),
  KEY `idx_psm_cleanup_due_at` (`due_at`),
  KEY `idx_psm_cleanup_status_lease` (`status`,`lease_expires_at`,`next_attempt_at`,`due_at`),
  KEY `idx_psm_cleanup_media` (`media_id`,`status`),
  CONSTRAINT `fk_psm_cleanup_media` FOREIGN KEY (`media_id`) REFERENCES `practice_speaking_media` (`id`),
  CONSTRAINT `fk_psm_cleanup_storage_profile` FOREIGN KEY (`storage_profile_code`) REFERENCES `storage_profiles` (`profile_code`),
  CONSTRAINT `chk_psm_cleanup_attempt_count` CHECK ((`attempt_count` >= 0)),
  CONSTRAINT `chk_psm_cleanup_authorization_evidence` CHECK ((((`cleanup_reason` = _utf8mb4'CONSENT_WITHDRAWAL') and (`authorization_evidence_id` is not null)) or ((`cleanup_reason` <> _utf8mb4'CONSENT_WITHDRAWAL') and (`authorization_evidence_id` is null)))),
  CONSTRAINT `chk_psm_cleanup_provider` CHECK ((`storage_provider` in (_utf8mb4'LOCAL',_utf8mb4'OBJECT_STORAGE'))),
  CONSTRAINT `chk_psm_cleanup_reason` CHECK ((`cleanup_reason` in (_utf8mb4'TEMPORARY_EXPIRY',_utf8mb4'SUPERSEDED_RETENTION',_utf8mb4'LOGICAL_DELETE',_utf8mb4'DISCARD_ATTEMPT',_utf8mb4'ACTIVATION_COMPENSATION',_utf8mb4'MIGRATION_SOURCE_DELETE',_utf8mb4'CONSENT_WITHDRAWAL'))),
  CONSTRAINT `chk_psm_cleanup_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'PROCESSING',_utf8mb4'RETRY',_utf8mb4'COMPLETED',_utf8mb4'TERMINAL')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_speaking_prompt_ai_artifacts`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_speaking_prompt_ai_artifacts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `owner_lecturer_id` bigint NOT NULL,
  `operation` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operation_fingerprint` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `input_source_revision` bigint NOT NULL,
  `input_sha256` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `input_audio_asset_id` bigint DEFAULT NULL,
  `provider_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `model_code` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `language_tag` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `voice_code` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `speed` decimal(5,2) DEFAULT NULL,
  `output_format` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `contract_version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `purpose_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `retention_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_request_reference` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `provider_transcript_text` mediumtext COLLATE utf8mb4_unicode_ci,
  `current_context_text` mediumtext COLLATE utf8mb4_unicode_ci,
  `current_context_sha256` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `generated_audio_asset_id` bigint DEFAULT NULL,
  `confidence` decimal(6,5) DEFAULT NULL,
  `artifact_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `public_error_category` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ready_at` datetime DEFAULT NULL,
  `failed_at` datetime DEFAULT NULL,
  `superseded_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_speaking_prompt_artifact_owner_fingerprint` (`owner_lecturer_id`,`operation`,`operation_fingerprint`),
  UNIQUE KEY `uk_speaking_prompt_artifact_owner_operation_identity` (`id`,`owner_lecturer_id`,`operation`),
  UNIQUE KEY `uk_speaking_prompt_artifact_task_identity` (`id`,`owner_lecturer_id`,`operation`,`operation_fingerprint`),
  UNIQUE KEY `uk_speaking_prompt_artifact_input_asset_identity` (`id`,`owner_lecturer_id`,`operation`,`input_audio_asset_id`),
  UNIQUE KEY `uk_speaking_prompt_artifact_output_asset_identity` (`id`,`owner_lecturer_id`,`operation`,`generated_audio_asset_id`),
  KEY `idx_speaking_prompt_artifact_status` (`operation`,`artifact_status`,`updated_at`),
  KEY `idx_speaking_prompt_artifact_input_asset` (`input_audio_asset_id`),
  KEY `idx_speaking_prompt_artifact_generated_asset` (`generated_audio_asset_id`),
  KEY `fk_speaking_prompt_artifact_input_asset` (`input_audio_asset_id`,`owner_lecturer_id`),
  KEY `fk_speaking_prompt_artifact_generated_asset` (`generated_audio_asset_id`,`owner_lecturer_id`),
  CONSTRAINT `fk_speaking_prompt_artifact_generated_asset` FOREIGN KEY (`generated_audio_asset_id`, `owner_lecturer_id`) REFERENCES `lecturer_assets` (`id`, `owner_lecturer_id`),
  CONSTRAINT `fk_speaking_prompt_artifact_input_asset` FOREIGN KEY (`input_audio_asset_id`, `owner_lecturer_id`) REFERENCES `lecturer_assets` (`id`, `owner_lecturer_id`),
  CONSTRAINT `fk_speaking_prompt_artifact_owner` FOREIGN KEY (`owner_lecturer_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_speaking_prompt_artifact_confidence` CHECK (((`confidence` is null) or ((`confidence` >= 0) and (`confidence` <= 1)))),
  CONSTRAINT `chk_speaking_prompt_artifact_context_sha` CHECK (((`current_context_sha256` is null) or regexp_like(`current_context_sha256`,_utf8mb4'^[0-9a-f]{64}$'))),
  CONSTRAINT `chk_speaking_prompt_artifact_fingerprint` CHECK (regexp_like(`operation_fingerprint`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `chk_speaking_prompt_artifact_input_sha` CHECK (regexp_like(`input_sha256`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `chk_speaking_prompt_artifact_operation` CHECK ((`operation` in (_utf8mb4'stt',_utf8mb4'tts'))),
  CONSTRAINT `chk_speaking_prompt_artifact_revision` CHECK ((`input_source_revision` >= 0)),
  CONSTRAINT `chk_speaking_prompt_artifact_shape` CHECK ((((`operation` = _utf8mb4'stt') and (`input_audio_asset_id` is not null) and (`generated_audio_asset_id` is null) and (`voice_code` is null) and (`speed` is null) and (`output_format` is null)) or ((`operation` = _utf8mb4'tts') and (`input_audio_asset_id` is null) and (`provider_transcript_text` is null) and (`current_context_text` is null) and (`current_context_sha256` is null) and (`confidence` is null) and (`voice_code` is not null) and (`speed` is not null) and (`speed` between 0.25 and 4.00) and (`output_format` is not null)))),
  CONSTRAINT `chk_speaking_prompt_artifact_status` CHECK ((`artifact_status` in (_utf8mb4'idle',_utf8mb4'queued',_utf8mb4'processing',_utf8mb4'ready',_utf8mb4'needs_review',_utf8mb4'stale',_utf8mb4'failed_retryable',_utf8mb4'failed_final',_utf8mb4'superseded',_utf8mb4'cancelled')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_speaking_prompt_ai_tasks`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_speaking_prompt_ai_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `artifact_id` bigint NOT NULL,
  `source_id` bigint DEFAULT NULL,
  `owner_lecturer_id` bigint NOT NULL,
  `operation` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_input_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operation_fingerprint` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expected_source_revision` bigint NOT NULL,
  `task_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `attempt_count` int NOT NULL DEFAULT '0',
  `max_attempts` int NOT NULL DEFAULT '4',
  `next_attempt_at` datetime DEFAULT NULL,
  `lease_owner` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_expires_at` datetime DEFAULT NULL,
  `retryable` tinyint(1) NOT NULL DEFAULT '0',
  `public_error_category` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `requested_by` bigint NOT NULL,
  `started_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `active_fingerprint_key` varchar(128) COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case when (`task_status` in (_utf8mb4'queued',_utf8mb4'processing',_utf8mb4'retry_wait')) then concat(`owner_lecturer_id`,_utf8mb4':',`operation`,_utf8mb4':',`operation_fingerprint`) else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_speaking_prompt_task_active_fingerprint` (`active_fingerprint_key`),
  KEY `idx_speaking_prompt_task_due` (`task_status`,`next_attempt_at`,`id`),
  KEY `idx_speaking_prompt_task_lease` (`task_status`,`lease_expires_at`),
  KEY `idx_speaking_prompt_task_source_revision` (`source_id`,`expected_source_revision`),
  KEY `idx_speaking_prompt_task_artifact` (`artifact_id`),
  KEY `fk_speaking_prompt_task_artifact` (`artifact_id`,`owner_lecturer_id`,`operation`,`operation_fingerprint`),
  KEY `fk_speaking_prompt_task_source` (`source_id`,`owner_lecturer_id`),
  KEY `fk_speaking_prompt_task_owner` (`owner_lecturer_id`),
  KEY `fk_speaking_prompt_task_requested_by` (`requested_by`),
  CONSTRAINT `fk_speaking_prompt_task_artifact` FOREIGN KEY (`artifact_id`, `owner_lecturer_id`, `operation`, `operation_fingerprint`) REFERENCES `practice_speaking_prompt_ai_artifacts` (`id`, `owner_lecturer_id`, `operation`, `operation_fingerprint`),
  CONSTRAINT `fk_speaking_prompt_task_owner` FOREIGN KEY (`owner_lecturer_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_speaking_prompt_task_requested_by` FOREIGN KEY (`requested_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_speaking_prompt_task_source` FOREIGN KEY (`source_id`, `owner_lecturer_id`) REFERENCES `practice_speaking_prompt_sources` (`id`, `owner_lecturer_id`),
  CONSTRAINT `chk_speaking_prompt_task_attempts` CHECK (((`attempt_count` >= 0) and (`max_attempts` between 1 and 10) and (`attempt_count` <= `max_attempts`))),
  CONSTRAINT `chk_speaking_prompt_task_fingerprint` CHECK (regexp_like(`operation_fingerprint`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `chk_speaking_prompt_task_operation` CHECK ((`operation` in (_utf8mb4'stt',_utf8mb4'tts'))),
  CONSTRAINT `chk_speaking_prompt_task_retryable` CHECK ((`retryable` in (0,1))),
  CONSTRAINT `chk_speaking_prompt_task_revision` CHECK ((`expected_source_revision` >= 0)),
  CONSTRAINT `chk_speaking_prompt_task_source_operation` CHECK ((((`source_input_type` = _utf8mb4'audio_upload') and (`operation` = _utf8mb4'stt')) or ((`source_input_type` = _utf8mb4'manual_text') and (`operation` = _utf8mb4'tts')))),
  CONSTRAINT `chk_speaking_prompt_task_status` CHECK ((`task_status` in (_utf8mb4'queued',_utf8mb4'processing',_utf8mb4'retry_wait',_utf8mb4'succeeded',_utf8mb4'failed',_utf8mb4'superseded',_utf8mb4'cancelled')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_speaking_prompt_sources`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_speaking_prompt_sources` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `draft_id` bigint NOT NULL,
  `question_client_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_lecturer_id` bigint NOT NULL,
  `input_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tts_enabled` tinyint(1) NOT NULL DEFAULT '0',
  `manual_text_sha256` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `original_audio_asset_id` bigint DEFAULT NULL,
  `generated_audio_asset_id` bigint DEFAULT NULL,
  `active_audio_asset_id` bigint DEFAULT NULL,
  `current_stt_artifact_id` bigint DEFAULT NULL,
  `current_tts_artifact_id` bigint DEFAULT NULL,
  `current_transcript_revision_id` bigint DEFAULT NULL,
  `current_stt_operation` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'stt',
  `current_tts_operation` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'tts',
  `transcript_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'idle',
  `audio_sync_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'idle',
  `lecturer_transcript_confirmed_at` datetime DEFAULT NULL,
  `source_revision` bigint NOT NULL DEFAULT '0',
  `lock_version` bigint NOT NULL DEFAULT '0',
  `created_by` bigint NOT NULL,
  `updated_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_speaking_prompt_source_draft_question` (`draft_id`,`question_client_id`),
  UNIQUE KEY `uk_speaking_prompt_source_owner_identity` (`id`,`owner_lecturer_id`),
  KEY `idx_speaking_prompt_source_owner` (`owner_lecturer_id`,`draft_id`),
  KEY `idx_speaking_prompt_source_original_asset` (`original_audio_asset_id`),
  KEY `idx_speaking_prompt_source_generated_asset` (`generated_audio_asset_id`),
  KEY `idx_speaking_prompt_source_active_asset` (`active_audio_asset_id`),
  KEY `fk_speaking_prompt_source_draft` (`draft_id`,`owner_lecturer_id`),
  KEY `fk_speaking_prompt_source_original_asset` (`original_audio_asset_id`,`owner_lecturer_id`),
  KEY `fk_speaking_prompt_source_generated_asset` (`generated_audio_asset_id`,`owner_lecturer_id`),
  KEY `fk_speaking_prompt_source_active_asset` (`active_audio_asset_id`,`owner_lecturer_id`),
  KEY `fk_speaking_prompt_source_created_by` (`created_by`),
  KEY `fk_speaking_prompt_source_updated_by` (`updated_by`),
  KEY `idx_speaking_prompt_source_stt_artifact` (`current_stt_artifact_id`),
  KEY `idx_speaking_prompt_source_tts_artifact` (`current_tts_artifact_id`),
  KEY `fk_speaking_prompt_source_stt_artifact` (`current_stt_artifact_id`,`owner_lecturer_id`,`current_stt_operation`,`original_audio_asset_id`),
  KEY `fk_speaking_prompt_source_tts_artifact_output` (`current_tts_artifact_id`,`owner_lecturer_id`,`current_tts_operation`,`generated_audio_asset_id`),
  KEY `idx_speaking_prompt_source_transcript_revision` (`current_transcript_revision_id`),
  KEY `fk_speaking_prompt_source_transcript_revision` (`current_transcript_revision_id`,`current_stt_artifact_id`,`owner_lecturer_id`),
  CONSTRAINT `fk_speaking_prompt_source_active_asset` FOREIGN KEY (`active_audio_asset_id`, `owner_lecturer_id`) REFERENCES `lecturer_assets` (`id`, `owner_lecturer_id`),
  CONSTRAINT `fk_speaking_prompt_source_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_speaking_prompt_source_draft` FOREIGN KEY (`draft_id`, `owner_lecturer_id`) REFERENCES `practice_drafts` (`id`, `owner_id`),
  CONSTRAINT `fk_speaking_prompt_source_generated_asset` FOREIGN KEY (`generated_audio_asset_id`, `owner_lecturer_id`) REFERENCES `lecturer_assets` (`id`, `owner_lecturer_id`),
  CONSTRAINT `fk_speaking_prompt_source_original_asset` FOREIGN KEY (`original_audio_asset_id`, `owner_lecturer_id`) REFERENCES `lecturer_assets` (`id`, `owner_lecturer_id`),
  CONSTRAINT `fk_speaking_prompt_source_owner` FOREIGN KEY (`owner_lecturer_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_speaking_prompt_source_stt_artifact` FOREIGN KEY (`current_stt_artifact_id`, `owner_lecturer_id`, `current_stt_operation`, `original_audio_asset_id`) REFERENCES `practice_speaking_prompt_ai_artifacts` (`id`, `owner_lecturer_id`, `operation`, `input_audio_asset_id`),
  CONSTRAINT `fk_speaking_prompt_source_transcript_revision` FOREIGN KEY (`current_transcript_revision_id`, `current_stt_artifact_id`, `owner_lecturer_id`) REFERENCES `practice_speaking_prompt_transcript_revisions` (`id`, `artifact_id`, `owner_lecturer_id`),
  CONSTRAINT `fk_speaking_prompt_source_tts_artifact_identity` FOREIGN KEY (`current_tts_artifact_id`, `owner_lecturer_id`, `current_tts_operation`) REFERENCES `practice_speaking_prompt_ai_artifacts` (`id`, `owner_lecturer_id`, `operation`),
  CONSTRAINT `fk_speaking_prompt_source_tts_artifact_output` FOREIGN KEY (`current_tts_artifact_id`, `owner_lecturer_id`, `current_tts_operation`, `generated_audio_asset_id`) REFERENCES `practice_speaking_prompt_ai_artifacts` (`id`, `owner_lecturer_id`, `operation`, `generated_audio_asset_id`),
  CONSTRAINT `fk_speaking_prompt_source_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_speaking_prompt_source_artifact_operations` CHECK (((`current_stt_operation` = _utf8mb4'stt') and (`current_tts_operation` = _utf8mb4'tts'))),
  CONSTRAINT `chk_speaking_prompt_source_audio_status` CHECK ((`audio_sync_status` in (_utf8mb4'idle',_utf8mb4'queued',_utf8mb4'processing',_utf8mb4'ready',_utf8mb4'needs_review',_utf8mb4'stale',_utf8mb4'failed_retryable',_utf8mb4'failed_final',_utf8mb4'superseded',_utf8mb4'cancelled'))),
  CONSTRAINT `chk_speaking_prompt_source_current_artifact_assets` CHECK ((((`current_stt_artifact_id` is null) or (`original_audio_asset_id` is not null)) and ((`current_transcript_revision_id` is null) or (`current_stt_artifact_id` is not null)))),
  CONSTRAINT `chk_speaking_prompt_source_input` CHECK ((`input_type` in (_utf8mb4'audio_upload',_utf8mb4'manual_text'))),
  CONSTRAINT `chk_speaking_prompt_source_lock` CHECK ((`lock_version` >= 0)),
  CONSTRAINT `chk_speaking_prompt_source_mode_assets` CHECK ((((`input_type` = _utf8mb4'audio_upload') and ((`active_audio_asset_id` is null) or ((`original_audio_asset_id` is not null) and (`active_audio_asset_id` = `original_audio_asset_id`)))) or ((`input_type` = _utf8mb4'manual_text') and (((`tts_enabled` = 0) and (`active_audio_asset_id` is null)) or ((`tts_enabled` = 1) and ((`active_audio_asset_id` is null) or ((`generated_audio_asset_id` is not null) and (`active_audio_asset_id` = `generated_audio_asset_id`)))))))),
  CONSTRAINT `chk_speaking_prompt_source_revision` CHECK ((`source_revision` >= 0)),
  CONSTRAINT `chk_speaking_prompt_source_text_identity` CHECK ((((`input_type` = _utf8mb4'audio_upload') and (`tts_enabled` = 0) and ((`manual_text_sha256` is null) or regexp_like(`manual_text_sha256`,_utf8mb4'^[0-9a-f]{64}$'))) or ((`input_type` = _utf8mb4'manual_text') and (`manual_text_sha256` is not null) and regexp_like(`manual_text_sha256`,_utf8mb4'^[0-9a-f]{64}$')))),
  CONSTRAINT `chk_speaking_prompt_source_transcript_status` CHECK ((`transcript_status` in (_utf8mb4'idle',_utf8mb4'queued',_utf8mb4'processing',_utf8mb4'ready',_utf8mb4'needs_review',_utf8mb4'stale',_utf8mb4'failed_retryable',_utf8mb4'failed_final',_utf8mb4'superseded',_utf8mb4'cancelled'))),
  CONSTRAINT `chk_speaking_prompt_source_tts_enabled` CHECK ((`tts_enabled` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_speaking_prompt_transcript_revisions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_speaking_prompt_transcript_revisions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `artifact_id` bigint NOT NULL,
  `owner_lecturer_id` bigint NOT NULL,
  `artifact_operation` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'stt',
  `revision_number` int NOT NULL,
  `revision_source` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `context_text` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `context_sha256` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `edited_by` bigint DEFAULT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_speaking_prompt_transcript_revision` (`artifact_id`,`revision_number`),
  UNIQUE KEY `uk_speaking_prompt_transcript_source_identity` (`id`,`artifact_id`,`owner_lecturer_id`),
  KEY `idx_speaking_prompt_transcript_editor` (`edited_by`,`created_at`),
  KEY `fk_speaking_prompt_transcript_artifact` (`artifact_id`,`owner_lecturer_id`,`artifact_operation`),
  KEY `fk_speaking_prompt_transcript_owner` (`owner_lecturer_id`),
  CONSTRAINT `fk_speaking_prompt_transcript_artifact` FOREIGN KEY (`artifact_id`, `owner_lecturer_id`, `artifact_operation`) REFERENCES `practice_speaking_prompt_ai_artifacts` (`id`, `owner_lecturer_id`, `operation`),
  CONSTRAINT `fk_speaking_prompt_transcript_editor` FOREIGN KEY (`edited_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_speaking_prompt_transcript_owner` FOREIGN KEY (`owner_lecturer_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_speaking_prompt_transcript_operation` CHECK ((`artifact_operation` = _utf8mb4'stt')),
  CONSTRAINT `chk_speaking_prompt_transcript_revision_number` CHECK ((`revision_number` >= 1)),
  CONSTRAINT `chk_speaking_prompt_transcript_sha` CHECK (regexp_like(`context_sha256`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `chk_speaking_prompt_transcript_source` CHECK ((((`revision_source` = _utf8mb4'provider') and (`edited_by` is null)) or ((`revision_source` = _utf8mb4'lecturer_edit') and (`edited_by` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_speaking_prompt_version_contexts`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_speaking_prompt_version_contexts` (
  `question_version_id` bigint NOT NULL,
  `owner_lecturer_id` bigint NOT NULL,
  `input_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `delivery_mode` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `audio_origin` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `prompt_context_source` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `prompt_context_text` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `prompt_context_sha256` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `prompt_context_fingerprint` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `original_audio_asset_id` bigint DEFAULT NULL,
  `active_audio_asset_id` bigint DEFAULT NULL,
  `stt_artifact_id` bigint DEFAULT NULL,
  `tts_artifact_id` bigint DEFAULT NULL,
  `stt_operation` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'stt',
  `tts_operation` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'tts',
  `stt_provider_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stt_model_code` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stt_contract_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stt_purpose_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stt_retention_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tts_provider_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tts_model_code` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tts_contract_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tts_purpose_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tts_retention_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`question_version_id`),
  KEY `idx_speaking_prompt_context_fingerprint` (`prompt_context_fingerprint`),
  KEY `idx_speaking_prompt_context_original_asset` (`original_audio_asset_id`),
  KEY `idx_speaking_prompt_context_active_asset` (`active_audio_asset_id`),
  KEY `idx_speaking_prompt_context_stt_artifact` (`stt_artifact_id`),
  KEY `idx_speaking_prompt_context_tts_artifact` (`tts_artifact_id`),
  KEY `fk_speaking_prompt_context_owner` (`owner_lecturer_id`),
  KEY `fk_speaking_prompt_context_original_asset` (`original_audio_asset_id`,`owner_lecturer_id`),
  KEY `fk_speaking_prompt_context_active_asset` (`active_audio_asset_id`,`owner_lecturer_id`),
  KEY `fk_speaking_prompt_context_stt_artifact` (`stt_artifact_id`,`owner_lecturer_id`,`stt_operation`,`original_audio_asset_id`),
  KEY `fk_speaking_prompt_context_tts_artifact` (`tts_artifact_id`,`owner_lecturer_id`,`tts_operation`,`active_audio_asset_id`),
  KEY `fk_speaking_prompt_context_created_by` (`created_by`),
  CONSTRAINT `fk_speaking_prompt_context_active_asset` FOREIGN KEY (`active_audio_asset_id`, `owner_lecturer_id`) REFERENCES `lecturer_assets` (`id`, `owner_lecturer_id`),
  CONSTRAINT `fk_speaking_prompt_context_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_speaking_prompt_context_original_asset` FOREIGN KEY (`original_audio_asset_id`, `owner_lecturer_id`) REFERENCES `lecturer_assets` (`id`, `owner_lecturer_id`),
  CONSTRAINT `fk_speaking_prompt_context_owner` FOREIGN KEY (`owner_lecturer_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_speaking_prompt_context_question_version` FOREIGN KEY (`question_version_id`) REFERENCES `practice_question_versions` (`id`),
  CONSTRAINT `fk_speaking_prompt_context_stt_artifact` FOREIGN KEY (`stt_artifact_id`, `owner_lecturer_id`, `stt_operation`, `original_audio_asset_id`) REFERENCES `practice_speaking_prompt_ai_artifacts` (`id`, `owner_lecturer_id`, `operation`, `input_audio_asset_id`),
  CONSTRAINT `fk_speaking_prompt_context_tts_artifact` FOREIGN KEY (`tts_artifact_id`, `owner_lecturer_id`, `tts_operation`, `active_audio_asset_id`) REFERENCES `practice_speaking_prompt_ai_artifacts` (`id`, `owner_lecturer_id`, `operation`, `generated_audio_asset_id`),
  CONSTRAINT `chk_speaking_prompt_context_artifact_operations` CHECK (((`stt_operation` = _utf8mb4'stt') and (`tts_operation` = _utf8mb4'tts'))),
  CONSTRAINT `chk_speaking_prompt_context_fingerprint` CHECK (regexp_like(`prompt_context_fingerprint`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `chk_speaking_prompt_context_sha` CHECK (regexp_like(`prompt_context_sha256`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `chk_speaking_prompt_context_shape` CHECK ((((`input_type` = _utf8mb4'audio_upload') and (`delivery_mode` = _utf8mb4'audio_only') and (`audio_origin` = _utf8mb4'teacher_upload') and (`prompt_context_source` = _utf8mb4'stt_transcript') and (`original_audio_asset_id` is not null) and (`active_audio_asset_id` is not null) and (`active_audio_asset_id` = `original_audio_asset_id`) and (`stt_artifact_id` is not null) and (`tts_artifact_id` is null) and (`stt_provider_code` is not null) and (`stt_model_code` is not null) and (`stt_contract_version` is not null) and (`stt_purpose_code` is not null) and (`stt_retention_code` is not null) and (`tts_provider_code` is null) and (`tts_model_code` is null) and (`tts_contract_version` is null) and (`tts_purpose_code` is null) and (`tts_retention_code` is null)) or ((`input_type` = _utf8mb4'manual_text') and (`delivery_mode` = _utf8mb4'text_only') and (`audio_origin` = _utf8mb4'none') and (`prompt_context_source` = _utf8mb4'manual_text') and (`original_audio_asset_id` is null) and (`active_audio_asset_id` is null) and (`stt_artifact_id` is null) and (`tts_artifact_id` is null) and (`stt_provider_code` is null) and (`stt_model_code` is null) and (`stt_contract_version` is null) and (`stt_purpose_code` is null) and (`stt_retention_code` is null) and (`tts_provider_code` is null) and (`tts_model_code` is null) and (`tts_contract_version` is null) and (`tts_purpose_code` is null) and (`tts_retention_code` is null)) or ((`input_type` = _utf8mb4'manual_text') and (`delivery_mode` = _utf8mb4'text_and_audio') and (`audio_origin` = _utf8mb4'ai_tts') and (`prompt_context_source` = _utf8mb4'manual_text') and (`original_audio_asset_id` is null) and (`active_audio_asset_id` is not null) and (`stt_artifact_id` is null) and (`tts_artifact_id` is not null) and (`stt_provider_code` is null) and (`stt_model_code` is null) and (`stt_contract_version` is null) and (`stt_purpose_code` is null) and (`stt_retention_code` is null) and (`tts_provider_code` is not null) and (`tts_model_code` is not null) and (`tts_contract_version` is not null) and (`tts_purpose_code` is not null) and (`tts_retention_code` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_storage_migration_jobs`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_storage_migration_jobs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `logical_type` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `logical_id` bigint NOT NULL,
  `source_profile_code` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_storage_key` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_profile_code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_storage_key` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_storage_provider` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `expected_size` bigint NOT NULL,
  `expected_sha256` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `copy_attempt_count` int NOT NULL DEFAULT '0',
  `cleanup_attempt_count` int NOT NULL DEFAULT '0',
  `last_error_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `next_attempt_at` datetime DEFAULT NULL,
  `claim_token` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_expires_at` datetime DEFAULT NULL,
  `verified_at` datetime DEFAULT NULL,
  `logical_updated_at` datetime DEFAULT NULL,
  `cleanup_not_before` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_practice_storage_migration_logical` (`logical_type`,`logical_id`,`target_profile_code`),
  UNIQUE KEY `uk_practice_storage_migration_target` (`target_profile_code`,`target_storage_key`),
  KEY `fk_practice_storage_migration_source_profile` (`source_profile_code`),
  KEY `idx_practice_storage_migration_status` (`status`,`next_attempt_at`,`updated_at`,`id`),
  CONSTRAINT `fk_practice_storage_migration_source_profile` FOREIGN KEY (`source_profile_code`) REFERENCES `storage_profiles` (`profile_code`),
  CONSTRAINT `fk_practice_storage_migration_target_profile` FOREIGN KEY (`target_profile_code`) REFERENCES `storage_profiles` (`profile_code`),
  CONSTRAINT `chk_practice_storage_migration_status` CHECK ((`status` in (_utf8mb4'PLANNED',_utf8mb4'COPYING',_utf8mb4'COPIED_VERIFIED',_utf8mb4'LOGICAL_UPDATED',_utf8mb4'CLEANUP_PENDING',_utf8mb4'DELETING_SOURCE',_utf8mb4'COMPLETED',_utf8mb4'FAILED'))),
  CONSTRAINT `chk_practice_storage_migration_type` CHECK ((`logical_type` in (_utf8mb4'LECTURER_ASSET',_utf8mb4'PDF_IMPORT_SESSION',_utf8mb4'SPEAKING_MEDIA')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_test_versions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_test_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `published_version_id` bigint NOT NULL,
  `set_version_id` bigint NOT NULL,
  `test_id` bigint NOT NULL,
  `title` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `display_order` int NOT NULL,
  `estimated_minutes` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ptv_published_test` (`published_version_id`,`test_id`),
  KEY `idx_ptv_set_version` (`set_version_id`,`display_order`),
  KEY `fk_ptv_test` (`test_id`),
  CONSTRAINT `fk_ptv_published` FOREIGN KEY (`published_version_id`) REFERENCES `practice_published_versions` (`id`),
  CONSTRAINT `fk_ptv_set_version` FOREIGN KEY (`set_version_id`) REFERENCES `practice_set_versions` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_tests`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_tests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `set_id` bigint NOT NULL,
  `title` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `display_order` int NOT NULL DEFAULT '0',
  `estimated_minutes` int DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ptest_set` (`set_id`,`display_order`),
  CONSTRAINT `fk_ptest_set` FOREIGN KEY (`set_id`) REFERENCES `practice_sets` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_user_preferences`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_user_preferences` (
  `user_id` bigint NOT NULL,
  `korean_font` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NANUM_MYEONGJO',
  `korean_font_size` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DEFAULT',
  `preference_schema_version` int NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_practice_user_preferences_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_practice_user_preferences_korean_font` CHECK ((`korean_font` in (_utf8mb4'NANUM_MYEONGJO',_utf8mb4'DIPHYLLEIA',_utf8mb4'GOWUN_BATANG',_utf8mb4'NOTO_SERIF_KR',_utf8mb4'NANUM_GOTHIC',_utf8mb4'GOTHIC_A1',_utf8mb4'GOWUN_DODUM',_utf8mb4'ORBIT',_utf8mb4'SUNFLOWER',_utf8mb4'BLACK_AND_WHITE_PICTURE',_utf8mb4'GUGI',_utf8mb4'POOR_STORY',_utf8mb4'SINGLE_DAY',_utf8mb4'GAEGU',_utf8mb4'HI_MELODY',_utf8mb4'NANUM_GOTHIC_CODING',_utf8mb4'NANUM_PEN_SCRIPT'))),
  CONSTRAINT `chk_practice_user_preferences_korean_font_size` CHECK ((`korean_font_size` in (_utf8mb4'DEFAULT',_utf8mb4'LARGE',_utf8mb4'EXTRA_LARGE'))),
  CONSTRAINT `chk_practice_user_preferences_schema_version` CHECK ((`preference_schema_version` = 2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `practice_writing_evaluation_cache`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `practice_writing_evaluation_cache` (
  `cache_key` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_scope_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `task_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `model` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `prompt_version` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `rubric_version` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `evaluation_schema_version` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `result_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`cache_key`),
  KEY `idx_pwec_expires_at` (`expires_at`),
  KEY `idx_pwec_user_scope_hash` (`user_scope_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `public_view_tokens`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `public_view_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attachment_id` bigint NOT NULL,
  `token` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `token` (`token`),
  KEY `idx_pvt_token` (`token`),
  KEY `idx_pvt_expires` (`expires_at`),
  KEY `fk_pvt_attachment` (`attachment_id`),
  CONSTRAINT `fk_pvt_attachment` FOREIGN KEY (`attachment_id`) REFERENCES `lesson_attachments` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `question_bank_items`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `question_bank_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `subject_id` bigint NOT NULL,
  `lesson_template_id` bigint DEFAULT NULL,
  `chapter_title_snapshot` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lesson_title_snapshot` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `chapter_order_snapshot` int DEFAULT NULL,
  `lesson_order_snapshot` int DEFAULT NULL,
  `contributor_id` bigint NOT NULL,
  `reviewed_by` bigint DEFAULT NULL,
  `question_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `workflow_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `status_before_archive` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `explanation` text COLLATE utf8mb4_unicode_ci,
  `review_note` text COLLATE utf8mb4_unicode_ci,
  `approved_at` datetime DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_qbi_subject_status` (`subject_id`,`workflow_status`,`updated_at`),
  KEY `idx_qbi_contributor_status` (`contributor_id`,`workflow_status`),
  KEY `idx_qbi_reviewer` (`reviewed_by`),
  KEY `idx_qb_items_lesson_status` (`lesson_template_id`,`workflow_status`),
  CONSTRAINT `fk_qb_items_lesson_template` FOREIGN KEY (`lesson_template_id`) REFERENCES `lesson_templates` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_qbi_contributor` FOREIGN KEY (`contributor_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_qbi_reviewer` FOREIGN KEY (`reviewed_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_qbi_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`id`) ON DELETE CASCADE,
  CONSTRAINT `question_bank_items_chk_1` CHECK ((`question_type` in (_utf8mb4'MCQ',_utf8mb4'MR'))),
  CONSTRAINT `question_bank_items_chk_2` CHECK ((`workflow_status` in (_utf8mb4'DRAFT',_utf8mb4'REVIEW',_utf8mb4'APPROVED',_utf8mb4'REJECTED',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB AUTO_INCREMENT=128 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `question_bank_options`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `question_bank_options` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_id` bigint NOT NULL,
  `content` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_correct` tinyint(1) DEFAULT '0',
  `sort_order` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_qbo_item_order` (`item_id`,`sort_order`),
  CONSTRAINT `fk_qbo_item` FOREIGN KEY (`item_id`) REFERENCES `question_bank_items` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=512 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `question_explanation_artifacts`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `question_explanation_artifacts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `fingerprint` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `legacy_cache_id` bigint DEFAULT NULL,
  `skill` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `question_type` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `assessment_schema_version` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_model` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `prompt_version` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `response_schema_version` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `explanation_language` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `question_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `stimulus_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `answer_spec_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `media_bundle_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `input_contract_json` json NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `explanation_json` json DEFAULT NULL,
  `error_category` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ready_at` datetime DEFAULT NULL,
  `failed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qea_fingerprint` (`fingerprint`),
  UNIQUE KEY `uk_qea_legacy_cache` (`legacy_cache_id`),
  KEY `idx_qea_status_updated` (`status`,`updated_at`),
  CONSTRAINT `chk_qea_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'READY',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `question_explanation_generation_tasks`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `question_explanation_generation_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `artifact_id` bigint NOT NULL,
  `source_question_version_id` bigint NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `attempt_count` int NOT NULL DEFAULT '0',
  `max_attempts` int NOT NULL DEFAULT '4',
  `next_attempt_at` datetime DEFAULT NULL,
  `lease_owner` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_expires_at` datetime DEFAULT NULL,
  `error_category` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `manual_retry_count` int NOT NULL DEFAULT '0',
  `last_retry_requested_by` bigint DEFAULT NULL,
  `last_retry_requested_at` datetime DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qegt_artifact` (`artifact_id`),
  KEY `idx_qegt_due` (`status`,`next_attempt_at`,`id`),
  KEY `idx_qegt_lease` (`status`,`lease_expires_at`),
  KEY `fk_qegt_source_question_version` (`source_question_version_id`),
  KEY `fk_qegt_retry_user` (`last_retry_requested_by`),
  CONSTRAINT `fk_qegt_artifact` FOREIGN KEY (`artifact_id`) REFERENCES `question_explanation_artifacts` (`id`),
  CONSTRAINT `fk_qegt_retry_user` FOREIGN KEY (`last_retry_requested_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_qegt_source_question_version` FOREIGN KEY (`source_question_version_id`) REFERENCES `practice_question_versions` (`id`),
  CONSTRAINT `chk_qegt_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'PROCESSING',_utf8mb4'RETRY_WAIT',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `question_options`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `question_options` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question_id` bigint NOT NULL,
  `content` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_correct` tinyint(1) DEFAULT '0',
  `sort_order` int DEFAULT '0',
  `match_key` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_qo_question` (`question_id`,`sort_order`),
  CONSTRAINT `fk_qo_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=128 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `question_version_explanation_bindings`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `question_version_explanation_bindings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question_version_id` bigint NOT NULL,
  `artifact_id` bigint NOT NULL,
  `explanation_language` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `fingerprint` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `binding_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `bound_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `superseded_at` datetime DEFAULT NULL,
  `active_explanation_language` varchar(16) COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case when (`binding_status` = _utf8mb4'ACTIVE') then `explanation_language` else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qveb_active_question_language` (`question_version_id`,`active_explanation_language`),
  KEY `idx_qveb_artifact` (`artifact_id`),
  KEY `idx_qveb_fingerprint` (`fingerprint`),
  KEY `idx_qveb_question_language_history` (`question_version_id`,`explanation_language`,`binding_status`,`id`),
  CONSTRAINT `fk_qveb_artifact` FOREIGN KEY (`artifact_id`) REFERENCES `question_explanation_artifacts` (`id`),
  CONSTRAINT `fk_qveb_question_version` FOREIGN KEY (`question_version_id`) REFERENCES `practice_question_versions` (`id`),
  CONSTRAINT `chk_qveb_binding_status` CHECK ((`binding_status` in (_utf8mb4'ACTIVE',_utf8mb4'SUPERSEDED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `questions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `questions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `test_id` bigint NOT NULL,
  `question_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `explanation` text COLLATE utf8mb4_unicode_ci,
  `points` decimal(5,2) DEFAULT '1.00',
  `sort_order` int DEFAULT '0',
  `image_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_q_test` (`test_id`,`sort_order`),
  CONSTRAINT `fk_q_test` FOREIGN KEY (`test_id`) REFERENCES `tests` (`id`) ON DELETE CASCADE,
  CONSTRAINT `questions_chk_1` CHECK ((`question_type` in (_utf8mb4'MCQ',_utf8mb4'MR',_utf8mb4'FILL_IN',_utf8mb4'MATCHING')))
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `role_hierarchy`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_hierarchy` (
  `parent_role_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `child_role_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`parent_role_code`,`child_role_code`),
  KEY `fk_rh_child` (`child_role_code`),
  CONSTRAINT `fk_rh_child` FOREIGN KEY (`child_role_code`) REFERENCES `roles` (`code`) ON DELETE CASCADE,
  CONSTRAINT `fk_rh_parent` FOREIGN KEY (`parent_role_code`) REFERENCES `roles` (`code`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `role_permissions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_permissions` (
  `role_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `permission_id` bigint NOT NULL,
  `granted_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`role_code`,`permission_id`),
  KEY `fk_rp_permission` (`permission_id`),
  CONSTRAINT `fk_rp_permission` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_rp_role` FOREIGN KEY (`role_code`) REFERENCES `roles` (`code`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `roles`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles` (
  `code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `is_system` tinyint(1) DEFAULT '1' COMMENT '1=role hệ thống không thể xoá',
  `priority` int DEFAULT '0' COMMENT 'Độ ưu tiên (càng cao càng nhiều quyền)',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sections`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sections` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `class_id` bigint NOT NULL,
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_order` smallint DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_section_class_order` (`class_id`,`display_order`),
  KEY `fk_section_creator` (`created_by`),
  KEY `idx_section_class_id` (`class_id`,`is_deleted`),
  CONSTRAINT `fk_section_class` FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_section_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `storage_profiles`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `storage_profiles` (
  `profile_code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `backend` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `account_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `access_key_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `secret_access_key` varchar(4096) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bucket` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `endpoint` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `region` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `key_prefix` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '0',
  `revision` bigint NOT NULL DEFAULT '0',
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`profile_code`),
  CONSTRAINT `chk_storage_profile_backend` CHECK ((`backend` in (_utf8mb4'LOCAL',_utf8mb4'R2'))),
  CONSTRAINT `chk_storage_profile_code` CHECK ((`profile_code` in (_utf8mb4'GENERAL_UPLOADS',_utf8mb4'PRACTICE_AUTHORING',_utf8mb4'PRACTICE_SPEAKING'))),
  CONSTRAINT `chk_storage_profile_revision` CHECK ((`revision` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `subjects`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subjects` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `leader_user_id` bigint DEFAULT NULL,
  `is_active` tinyint(1) DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_dept_code` (`code`),
  KEY `idx_dept_leader` (`leader_user_id`),
  CONSTRAINT `fk_dept_leader` FOREIGN KEY (`leader_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `subjects_activities`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subjects_activities` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `subject_id` bigint NOT NULL,
  `type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `message` text COLLATE utf8mb4_unicode_ci,
  `metadata` text COLLATE utf8mb4_unicode_ci,
  `performed_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_subjects_activity_subject` (`subject_id`),
  KEY `idx_dact_type` (`type`),
  KEY `idx_dact_created` (`created_at`),
  KEY `fk_dact_actor` (`performed_by`),
  CONSTRAINT `fk_dact_actor` FOREIGN KEY (`performed_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_subjects_activity_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `system_settings`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `setting_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `setting_value` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `setting_group` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_encrypted` tinyint(1) DEFAULT '0',
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_ss_key` (`setting_key`),
  KEY `idx_ss_group` (`setting_group`),
  KEY `fk_ss_updater` (`updated_by`),
  CONSTRAINT `fk_ss_updater` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `test_attempts`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_attempts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `test_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'IN_PROGRESS',
  `score` decimal(6,2) DEFAULT NULL,
  `total_points` decimal(6,2) DEFAULT NULL,
  `correct_count` int DEFAULT NULL,
  `total_questions` int DEFAULT NULL,
  `started_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `submitted_at` datetime DEFAULT NULL,
  `time_spent_seconds` int DEFAULT NULL,
  `last_activity_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ta_test` (`test_id`),
  KEY `idx_ta_user` (`user_id`),
  KEY `idx_ta_user_test` (`user_id`,`test_id`),
  CONSTRAINT `fk_ta_test` FOREIGN KEY (`test_id`) REFERENCES `tests` (`id`),
  CONSTRAINT `fk_ta_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `test_attempts_chk_1` CHECK ((`status` in (_utf8mb4'IN_PROGRESS',_utf8mb4'SUBMITTED',_utf8mb4'TIMED_OUT')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `test_responses`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_responses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attempt_id` bigint NOT NULL,
  `question_id` bigint NOT NULL,
  `selected_option_ids` json DEFAULT NULL,
  `fill_in_text` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `matching_pairs` json DEFAULT NULL,
  `is_correct` tinyint(1) DEFAULT NULL,
  `points_earned` decimal(5,2) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tr_attempt` (`attempt_id`),
  KEY `fk_tr_question` (`question_id`),
  CONSTRAINT `fk_tr_attempt` FOREIGN KEY (`attempt_id`) REFERENCES `test_attempts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_tr_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tests`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `class_id` bigint DEFAULT NULL,
  `subject_id` bigint DEFAULT NULL,
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `duration_minutes` int DEFAULT NULL,
  `passing_score` decimal(5,2) DEFAULT NULL,
  `start_at` datetime DEFAULT NULL,
  `end_at` datetime DEFAULT NULL,
  `time_mode` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FIXED_WINDOW',
  `media_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `media_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `total_questions` int DEFAULT '0',
  `shuffle_questions` tinyint(1) DEFAULT '0',
  `shuffle_options` tinyint(1) DEFAULT '0',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'DRAFT',
  `created_by` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_test_class` (`class_id`),
  KEY `idx_test_type` (`type`),
  KEY `idx_test_status` (`status`),
  KEY `fk_test_creator` (`created_by`),
  KEY `idx_tests_subject_status` (`subject_id`,`status`,`is_deleted`),
  CONSTRAINT `fk_test_class` FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_test_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_tests_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_test_media_type` CHECK (((`media_type` is null) or (`media_type` in (_utf8mb4'YOUTUBE',_utf8mb4'VIDEO',_utf8mb4'AUDIO')))),
  CONSTRAINT `chk_test_time_mode` CHECK ((`time_mode` in (_utf8mb4'FIXED_WINDOW',_utf8mb4'INDIVIDUAL'))),
  CONSTRAINT `tests_chk_1` CHECK ((`type` in (_utf8mb4'MOCK',_utf8mb4'MODULE',_utf8mb4'PRACTICE'))),
  CONSTRAINT `tests_chk_2` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PUBLISHED',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_activities`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_activities` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `target_user_id` bigint NOT NULL,
  `type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `message` text COLLATE utf8mb4_unicode_ci,
  `metadata` text COLLATE utf8mb4_unicode_ci,
  `performed_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_uact_target` (`target_user_id`),
  KEY `idx_uact_type` (`type`),
  KEY `idx_uact_created` (`created_at`),
  KEY `fk_uact_actor` (`performed_by`),
  CONSTRAINT `fk_uact_actor` FOREIGN KEY (`performed_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_uact_target` FOREIGN KEY (`target_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_oauth_providers`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_oauth_providers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `provider` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_user_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `access_token` text COLLATE utf8mb4_unicode_ci,
  `refresh_token` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_oauth_provider` (`provider`,`provider_user_id`),
  KEY `fk_oauth_user` (`user_id`),
  CONSTRAINT `fk_oauth_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_permission_overrides`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_permission_overrides` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  `override_type` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Lý do override',
  `granted_by` bigint NOT NULL COMMENT 'Ai thực hiện override (thường là Admin)',
  `expires_at` datetime DEFAULT NULL COMMENT 'Hết hạn override (NULL = vĩnh viễn)',
  `is_active` tinyint(1) DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_upo_user_perm` (`user_id`,`permission_id`),
  KEY `idx_upo_user` (`user_id`),
  KEY `idx_upo_active` (`is_active`,`expires_at`),
  KEY `fk_upo_permission` (`permission_id`),
  KEY `fk_upo_grantor` (`granted_by`),
  CONSTRAINT `fk_upo_grantor` FOREIGN KEY (`granted_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_upo_permission` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_upo_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `user_permission_overrides_chk_1` CHECK ((`override_type` in (_utf8mb4'GRANT',_utf8mb4'REVOKE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `users`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `full_name` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `subject_id` bigint DEFAULT NULL,
  `avatar_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bio` text COLLATE utf8mb4_unicode_ci,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_email_verified` tinyint(1) DEFAULT '0',
  `is_active` tinyint(1) DEFAULT '1',
  `is_locked` tinyint(1) DEFAULT '0',
  `locked_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_login_at` datetime DEFAULT NULL,
  `google_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_users_email` (`email`),
  UNIQUE KEY `idx_users_google_id` (`google_id`),
  KEY `idx_users_role` (`role`),
  KEY `idx_users_subject_id` (`subject_id`),
  CONSTRAINT `fk_user_role` FOREIGN KEY (`role`) REFERENCES `roles` (`code`),
  CONSTRAINT `fk_user_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_users_role` CHECK ((`role` in (_utf8mb4'STUDENT',_utf8mb4'LECTURER',_utf8mb4'LEADER',_utf8mb4'ADMIN')))
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Temporary view structure for view `v_user_effective_permissions`
--

SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `v_user_effective_permissions` AS SELECT
 1 AS `user_id`,
 1 AS `feature_key`,
 1 AS `permission_group`,
 1 AS `is_granted`,
 1 AS `source`*/;
SET character_set_client = @saved_cs_client;

--
-- Dumping events for database 'ksh_dev_20260809'
--

--
-- Dumping routines for database 'ksh_dev_20260809'
--

--
-- Final view structure for view `v_user_effective_permissions`
--


/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 CREATE OR REPLACE VIEW `v_user_effective_permissions` AS with recursive `role_tree` as (select `r`.`code` AS `role_code`,`r`.`code` AS `inherited_from` from `roles` `r` union all select `rt`.`role_code` AS `role_code`,`rh`.`parent_role_code` AS `parent_role_code` from (`role_tree` `rt` join `role_hierarchy` `rh` on((`rt`.`inherited_from` = `rh`.`child_role_code`)))), `user_base_permissions` as (select distinct `u`.`id` AS `user_id`,`rp`.`permission_id` AS `permission_id` from ((`users` `u` join `role_tree` `rt` on((`u`.`role` = `rt`.`role_code`))) join `role_permissions` `rp` on((`rp`.`role_code` = `rt`.`inherited_from`)))) select `ubp`.`user_id` AS `user_id`,`p`.`feature_key` AS `feature_key`,`p`.`permission_group` AS `permission_group`,(case when (`revoke_override`.`id` is not null) then 0 when (`grant_override`.`id` is not null) then 1 else 1 end) AS `is_granted`,(case when (`revoke_override`.`id` is not null) then 'REVOKED' when (`grant_override`.`id` is not null) then 'GRANTED_OVERRIDE' else 'FROM_ROLE' end) AS `source` from (((`user_base_permissions` `ubp` join `permissions` `p` on((`ubp`.`permission_id` = `p`.`id`))) left join `user_permission_overrides` `revoke_override` on(((`revoke_override`.`user_id` = `ubp`.`user_id`) and (`revoke_override`.`permission_id` = `ubp`.`permission_id`) and (`revoke_override`.`override_type` = 'REVOKE') and (`revoke_override`.`is_active` = 1) and ((`revoke_override`.`expires_at` is null) or (`revoke_override`.`expires_at` > now()))))) left join `user_permission_overrides` `grant_override` on(((`grant_override`.`user_id` = `ubp`.`user_id`) and (`grant_override`.`permission_id` = `ubp`.`permission_id`) and (`grant_override`.`override_type` = 'GRANT') and (`grant_override`.`is_active` = 1) and ((`grant_override`.`expires_at` is null) or (`grant_override`.`expires_at` > now()))))) union select `upo`.`user_id` AS `user_id`,`p`.`feature_key` AS `feature_key`,`p`.`permission_group` AS `permission_group`,1 AS `is_granted`,'GRANTED_OVERRIDE' AS `source` from (`user_permission_overrides` `upo` join `permissions` `p` on((`upo`.`permission_id` = `p`.`id`))) where ((`upo`.`override_type` = 'GRANT') and (`upo`.`is_active` = 1) and ((`upo`.`expires_at` is null) or (`upo`.`expires_at` > now())) and exists(select 1 from `user_base_permissions` `ubp` where ((`ubp`.`user_id` = `upo`.`user_id`) and (`ubp`.`permission_id` = `upo`.`permission_id`))) is false) */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-09 21:50:27
