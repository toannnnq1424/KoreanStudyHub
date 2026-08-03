package com.ksh.features.practice;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeStorageProfilesStaticContractTest {
    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void v85AddsOnlyExactProfilesAndMigrationQueueWithoutDestructiveDdl() throws Exception {
        String sql = read("src/main/resources/db/migration/V85__practice_storage_profiles.sql");

        assertThat(sql).contains(
                "'GENERAL_UPLOADS', 'PRACTICE_AUTHORING', 'PRACTICE_SPEAKING'",
                "ADD COLUMN storage_profile_code VARCHAR(40) NULL",
                "'UNREFERENCED_TEMPORARY', 'READY', 'SUPERSEDED'",
                "practice_storage_migration_jobs",
                "copy_attempt_count",
                "cleanup_not_before");
        assertThat(sql.toLowerCase()).doesNotContain(
                "drop table", "truncate", "flyway_schema_history");
    }

    @Test
    void practiceAdaptersCannotReferenceGeneralFallbackOrPublicUrls() throws Exception {
        List<Path> adapterFiles = List.of(
                ROOT.resolve("src/main/java/com/ksh/features/practice/manage/service/ProfiledPracticeAuthoringStorage.java"),
                ROOT.resolve("src/main/java/com/ksh/features/practice/service/audio/ProfiledPracticeSpeakingAudioStorage.java"),
                ROOT.resolve("src/main/java/com/ksh/features/practice/service/storage/ProfiledPracticeStorageMigrationObjectPort.java"));
        String sources = adapterFiles.stream().map(path -> {
            try { return Files.readString(path); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        }).reduce("", String::concat);

        assertThat(sources).doesNotContain(
                "GENERAL_UPLOADS", "GeneralUploadsObjectStorage",
                "presign", "Presign", "publicUrl", "public URL",
                "storageProfileCode == null", "legacyLocal");
    }

    @Test
    void v87FailsClosedBeforeDroppingOnlyRetiredPdfSchemaAndTightensPracticeProfiles()
            throws Exception {
        String sql = read("src/main/resources/db/migration/"
                + "V87__practice_legacy_import_schema_compaction.sql");

        assertThat(sql).contains(
                "C4_PDF_WORKSPACE_ROWS_MUST_BE_EMPTY",
                "C4_PDF_PROVENANCE_MUST_BE_EMPTY",
                "C4_RETAINED_STORAGE_PROFILE_REQUIRED",
                "C4_STORAGE_WORK_QUEUES_MUST_BE_EMPTY",
                "DROP TABLE practice_pdf_import_sessions",
                "DROP TABLE practice_ai_request_audits",
                "DROP COLUMN source_import_session_id",
                "MODIFY source_type VARCHAR(64) NOT NULL DEFAULT 'MANUAL_UPLOAD'",
                "MODIFY storage_profile_code VARCHAR(40) NOT NULL",
                "DROP INDEX uk_psm_storage",
                "DROP INDEX uk_psm_cleanup_storage");
        assertThat(sql).doesNotContain(
                "DROP TABLE practice_authoring_candidates",
                "DROP TABLE practice_ai_execution_audits",
                "DROP TABLE storage_profiles",
                "DROP TABLE practice_storage_migration_jobs",
                "DELETE FROM", "TRUNCATE", "flyway_schema_history");

        assertThat(Files.exists(ROOT.resolve(
                "src/main/java/com/ksh/features/practice/manage/service/LocalAssetStorageService.java")))
                .isFalse();
        assertThat(Files.exists(ROOT.resolve(
                "src/main/java/com/ksh/features/practice/service/audio/LocalPrivateSpeakingAudioStorage.java")))
                .isFalse();
    }

    @Test
    void productionLocalIsOptInAndMigrationHasNoAutomaticWorker() throws Exception {
        assertThat(read("src/main/resources/application.properties"))
                .contains("app.storage-profiles.allow-local=${STORAGE_PROFILES_ALLOW_LOCAL:false}");
        assertThat(read("src/main/java/com/ksh/features/practice/service/storage/PracticeStorageMigrationCoordinator.java"))
                .doesNotContain("@Scheduled")
                .contains("copyAndHash", "switchVerifiedTarget", "processDelayedSourceDelete");
    }

    private static String read(String path) throws Exception {
        return Files.readString(ROOT.resolve(path));
    }
}
