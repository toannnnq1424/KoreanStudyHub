package com.ksh.features.practice.manage.speaking;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingPromptAuthoringFoundationMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V55__practice_speaking_prompt_authoring_foundation.sql");

    @Test
    void forwardMigrationSeparatesSourceArtifactTaskRevisionAndVersionAuthorities()
            throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains(
                "ALTER TABLE practice_asset_lifecycle_tasks",
                "ADD COLUMN claim_token VARCHAR(64) NULL",
                "idx_practice_asset_task_source_active",
                "(source_storage_key, status, id)",
                "idx_lecturer_assets_storage_key (storage_key, id)",
                "CREATE TABLE practice_speaking_prompt_sources",
                "UNIQUE (draft_id, question_client_id)",
                "FOREIGN KEY (draft_id, owner_lecturer_id)",
                "CREATE TABLE practice_speaking_prompt_ai_artifacts",
                "UNIQUE (owner_lecturer_id, operation, operation_fingerprint)",
                "CREATE TABLE practice_speaking_prompt_transcript_revisions",
                "UNIQUE (artifact_id, revision_number)",
                "current_transcript_revision_id BIGINT NULL",
                "FOREIGN KEY (\n"
                        + "            current_transcript_revision_id,\n"
                        + "            current_stt_artifact_id,\n"
                        + "            owner_lecturer_id)",
                "CREATE TABLE practice_speaking_prompt_ai_tasks",
                "source_id BIGINT NULL",
                "active_fingerprint_key",
                "UNIQUE (active_fingerprint_key)",
                "FOREIGN KEY (\n"
                        + "            artifact_id,\n"
                        + "            owner_lecturer_id,\n"
                        + "            operation,\n"
                        + "            operation_fingerprint)",
                "FOREIGN KEY (source_id, owner_lecturer_id)",
                "(source_input_type = 'audio_upload' AND operation = 'stt')",
                "(source_input_type = 'manual_text' AND operation = 'tts')",
                "current_stt_artifact_id IS NULL OR original_audio_asset_id IS NOT NULL",
                "current_transcript_revision_id IS NULL\n"
                        + "            OR current_stt_artifact_id IS NOT NULL",
                "speed IS NOT NULL",
                "fk_speaking_prompt_source_stt_artifact",
                "current_stt_operation,\n"
                        + "            original_audio_asset_id)",
                "fk_speaking_prompt_source_tts_artifact_identity",
                "current_tts_operation)\n"
                        + "        REFERENCES practice_speaking_prompt_ai_artifacts(\n"
                        + "            id,\n"
                        + "            owner_lecturer_id,\n"
                        + "            operation)",
                "fk_speaking_prompt_source_tts_artifact_output",
                "current_tts_operation,\n"
                        + "            generated_audio_asset_id)",
                "CREATE TABLE practice_speaking_prompt_version_contexts",
                "question_version_id BIGINT PRIMARY KEY",
                "prompt_context_source = 'stt_transcript'",
                "prompt_context_source = 'manual_text'",
                "fk_speaking_prompt_context_stt_artifact",
                "stt_operation,\n"
                        + "            original_audio_asset_id)");
    }

    @Test
    void ownerScopedReusableArtifactsRetainExactInputAndOutputIntegrity()
            throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains(
                "UNIQUE (owner_lecturer_id, operation, operation_fingerprint)",
                "UNIQUE (id, owner_lecturer_id, operation, input_audio_asset_id)",
                "UNIQUE (id, owner_lecturer_id, operation, generated_audio_asset_id)",
                "FOREIGN KEY (input_audio_asset_id, owner_lecturer_id)",
                "REFERENCES lecturer_assets(id, owner_lecturer_id)",
                "current_stt_artifact_id,\n"
                        + "            owner_lecturer_id,\n"
                        + "            current_stt_operation,\n"
                        + "            original_audio_asset_id)",
                "current_tts_artifact_id,\n"
                        + "            owner_lecturer_id,\n"
                        + "            current_tts_operation,\n"
                        + "            generated_audio_asset_id)",
                "stt_artifact_id,\n"
                        + "            owner_lecturer_id,\n"
                        + "            stt_operation,\n"
                        + "            original_audio_asset_id)");
    }

    @Test
    void taskIdentityAllowsOneActiveOwnerOperationFingerprintAndVisibleSuccessors()
            throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains(
                "WHEN task_status IN ('queued', 'processing', 'retry_wait')",
                "THEN CONCAT(owner_lecturer_id, ':', operation, ':', operation_fingerprint)",
                "CONSTRAINT uk_speaking_prompt_task_active_fingerprint",
                "UNIQUE (active_fingerprint_key)",
                "attempt_count INT NOT NULL DEFAULT 0",
                "max_attempts INT NOT NULL",
                "lease_owner VARCHAR(100) NULL",
                "lease_expires_at DATETIME NULL");
    }

    @Test
    void migrationIsAdditiveAndDoesNotRewriteV1QuestionsOrAttempts() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .doesNotContain(
                        "UPDATE practice_questions",
                        "UPDATE practice_question_versions",
                        "UPDATE practice_attempts",
                        "DELETE FROM practice_questions",
                        "DROP TABLE",
                        "FOREIGN KEY (source_id, owner_lecturer_id, source_input_type)",
                        "REFERENCES practice_speaking_prompt_sources(id) ON DELETE CASCADE");
    }
}
