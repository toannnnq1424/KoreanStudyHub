package com.ksh.features.practice;

import com.ksh.features.practice.ai.controlplane.PracticeAiPurpose;
import com.ksh.features.storage.profile.StorageProfileCode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeAim8CompatibilityStaticContractTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();
    private static final Path MIGRATIONS = ROOT.resolve(
            "src/main/resources/db/migration");
    private static final Pattern MIGRATION = Pattern.compile(
            "V(\\d+)__.+\\.sql");

    @Test
    void migrationChainIsContinuousThroughV96AndHistoricalBytesStayLockedThroughV85()
            throws Exception {
        List<Path> migrations;
        try (var paths = Files.list(MIGRATIONS)) {
            migrations = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> MIGRATION.matcher(
                            path.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(
                            PracticeAim8CompatibilityStaticContractTest::version))
                    .toList();
        }
        assertThat(migrations).hasSize(96);
        assertThat(migrations.stream().map(
                PracticeAim8CompatibilityStaticContractTest::version).toList())
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, 96)
                                .boxed().toList());

        List<String> manifestEntries = new ArrayList<>();
        manifestEntries.addAll(lines(
                "docs/operations/practice-migrations-v1-v56.sha256"));
        manifestEntries.addAll(lines(
                "docs/operations/practice-migrations-v57-v85.sha256"));
        assertThat(manifestEntries).hasSize(85);

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        for (String entry : manifestEntries) {
            String[] fields = entry.split("  ", 2);
            assertThat(fields).hasSize(2);
            Path migration = MIGRATIONS.resolve(fields[1]);
            assertThat(migration).exists();
            String actual = HexFormat.of().formatHex(
                    sha256.digest(Files.readAllBytes(migration)));
            assertThat(actual).as(fields[1]).isEqualTo(fields[0]);
            sha256.reset();
        }

        String aimMigrations = read(
                "src/main/resources/db/migration/"
                        + "V83__practice_authoring_candidate_foundation.sql")
                + read("src/main/resources/db/migration/"
                        + "V84__practice_ai_control_plane.sql")
                + read("src/main/resources/db/migration/"
                        + "V85__practice_storage_profiles.sql");
        assertThat(aimMigrations.toLowerCase()).doesNotContain(
                "drop table", "drop column", "truncate ",
                "delete from", "flyway_schema_history");
    }

    @Test
    void everyExcelAndPdfImporterStopsAtCandidateReview() throws Exception {
        String excel = read("src/main/java/com/ksh/features/practice/manage/"
                + "controller/PracticeAssessmentExcelController.java")
                + read("src/main/java/com/ksh/features/practice/manage/service/"
                + "PracticeAssessmentExcelService.java")
                + read("src/main/java/com/ksh/features/practice/manage/service/"
                + "PracticeAssessmentQuickExcelCodec.java");
        assertThat(excel)
                .contains("candidateService.createOrReuse", "reviewUrl")
                .doesNotContain(
                        "setDraftJson(", "saveAndFlush(",
                        "mergeImportedLessons(", "importDraft(");

        String pdf = read("src/main/java/com/ksh/features/practice/manage/"
                + "controller/PracticePdfImportApiController.java")
                + read("src/main/java/com/ksh/features/practice/manage/service/"
                + "PracticePdfAuthoringCandidateAssembler.java")
                + read("src/main/java/com/ksh/features/practice/manage/service/"
                + "PracticePdfAiOrchestrator.java");
        assertThat(pdf)
                .contains(
                        "/pdf-authoring/candidates",
                        "candidateService.createOrReuse", "reviewUrl")
                .doesNotContain(
                        "/import-sessions/",
                        "PracticeDraftRepository", "setDraftJson(",
                        "PracticePdfDraftAssembler", "PracticeImportDraftService");
        assertThat(Files.exists(javaPath(
                "manage/service/PracticePdfDraftAssembler.java"))).isFalse();
        assertThat(Files.exists(javaPath(
                "manage/service/PracticeImportDraftService.java"))).isFalse();
    }

    @Test
    void currentContractDoesNotAdvertiseRetiredLegacyExcelReader()
            throws Exception {
        String excelService = read(
                "src/main/java/com/ksh/features/practice/manage/service/"
                        + "PracticeAssessmentExcelService.java");
        String contract = read(
                "docs/architecture/practice/"
                        + "PRACTICE_AUTHORING_IMPORT_MODERNIZATION_CONTRACT.md");

        assertThat(excelService)
                .contains("LEGACY_EXCEL_V1_RETIRED")
                .doesNotContain("LegacyPracticeAssessmentExcelCodec");
        assertThat(contract)
                .contains(
                        "current interactive workbook entry point "
                                + "deterministically rejects legacy v1",
                        "Retain the enum/schema identity until stored "
                                + "candidate inventory authorizes removal")
                .doesNotContain(
                        "Current bounded legacy reader semantics; "
                                + "no new legacy writer");
    }

    @Test
    void explicitApplyIsTheOnlyCandidatePackageDraftWriter()
            throws Exception {
        Path packageRoot = javaPath("manage/authoringcandidate");
        List<String> draftWriters = new ArrayList<>();
        try (var paths = Files.walk(packageRoot)) {
            for (Path path : paths.filter(
                    file -> file.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                if (source.contains("draft.setDraftJson(")
                        || source.contains("draftRepository.saveAndFlush(draft)")) {
                    draftWriters.add(path.getFileName().toString());
                }
            }
        }
        assertThat(draftWriters)
                .containsExactly("PracticeAuthoringCandidateApplyService.java");

        String preview = read("src/main/java/com/ksh/features/practice/manage/"
                + "authoringcandidate/PracticeAuthoringCandidatePreviewService.java");
        assertThat(preview)
                .contains("PracticeDraftPreviewService")
                .doesNotContain(
                        "save(", "saveAndFlush(", "setDraftJson(", "publish(");
    }

    @Test
    void exactAiAndStorageIdentitiesRemainClosedAndDomainOwned()
            throws Exception {
        assertThat(PracticeAiPurpose.values()).extracting(Enum::name)
                .containsExactlyInAnyOrder(
                        "PRACTICE_PDF_AUTHORING",
                        "PRACTICE_RL_EXPLANATION",
                        "PRACTICE_WRITING_EVALUATION",
                        "PRACTICE_SPEAKING_EVALUATION",
                        "PRACTICE_SPEAKING_STT",
                        "PRACTICE_SPEAKING_TTS",
                        "PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION");
        assertThat(StorageProfileCode.values()).extracting(Enum::name)
                .containsExactlyInAnyOrder(
                        "GENERAL_UPLOADS",
                        "PRACTICE_AUTHORING",
                        "PRACTICE_SPEAKING");

        try (var paths = Files.walk(javaPath(""))) {
            for (Path path : paths
                    .filter(file -> file.toString().endsWith(".java"))
                    .toList()) {
                assertThat(Files.readString(path)).as(path.toString())
                        .doesNotContain(
                                "com.ksh.features.ai.client.AiClient",
                                "com.ksh.features.admin.settings.repository."
                                        + "AiProviderRepository",
                                "findEnabledOrdered(");
            }
        }

        String privateAdapters = read(
                "src/main/java/com/ksh/features/practice/manage/service/"
                        + "ProfiledPracticeAuthoringStorage.java")
                + read("src/main/java/com/ksh/features/practice/service/audio/"
                        + "ProfiledPracticeSpeakingAudioStorage.java")
                + read("src/main/java/com/ksh/features/practice/service/storage/"
                        + "ProfiledPracticeStorageMigrationObjectPort.java");
        assertThat(privateAdapters)
                .doesNotContain(
                        "GENERAL_UPLOADS", "GeneralUploadsObjectStorage",
                        "presign", "publicUrl", "public URL",
                        "storageProfileCode == null");
    }

    @Test
    void recordsFirstBindingRevisionAsZeroAcrossEveryContractConsumer()
            throws Exception {
        String aiMigration = read(
                "src/main/resources/db/migration/V84__practice_ai_control_plane.sql");
        String candidateSchema = read(
                "docs/architecture/practice/schemas/"
                        + "practice-authoring-candidate-v1.schema.json");
        String candidateService = read(
                "src/main/java/com/ksh/features/practice/manage/"
                        + "authoringcandidate/PracticeAuthoringCandidateService.java");
        String pdfOrchestrator = read(
                "src/main/java/com/ksh/features/practice/manage/service/"
                        + "PracticePdfAiOrchestrator.java");

        assertThat(aiMigration)
                .contains("revision BIGINT NOT NULL DEFAULT 0")
                .contains("CHECK (revision >= 0)");
        assertThat(candidateSchema).contains(
                "\"bindingRevision\": { \"type\": \"integer\", \"minimum\": 0 }");
        assertThat(candidateService).contains(
                "aiExecution.path(\"bindingRevision\").asLong() < 0");
        assertThat(pdfOrchestrator).contains(
                "identity.bindingRevision() < 0");
        assertThat(candidateService).doesNotContain(
                "aiExecution.path(\"bindingRevision\").asInt(0) < 1");
        assertThat(pdfOrchestrator).doesNotContain(
                "identity.bindingRevision() < 1");
    }

    @Test
    void routesTemplatesAndSharedPreviewRetainOneCanonicalJourney()
            throws Exception {
        String excelController = read(
                "src/main/java/com/ksh/features/practice/manage/controller/"
                        + "PracticeAssessmentExcelController.java");
        String pdfController = read(
                "src/main/java/com/ksh/features/practice/manage/controller/"
                        + "PracticePdfImportApiController.java");
        String excelTemplate = read(
                "src/main/resources/templates/practice/manage/excel-import.html");
        String basicTemplate = read(
                "src/main/resources/templates/practice/manage/import-wizard.html");
        String reviewTemplate = read(
                "src/main/resources/templates/practice/manage/"
                        + "candidate-review.html");

        assertThat(excelController).contains(
                "@PostMapping(value = \"/import\"", "reviewUrl");
        assertThat(pdfController).contains(
                "@PostMapping(value = \"/pdf-authoring/candidates\"",
                "reviewUrl")
                .doesNotContain("/import-sessions/");
        assertThat(excelTemplate + basicTemplate)
                .contains("payload.reviewUrl")
                .doesNotContain("create-manual-draft", "attach-to-draft");
        assertThat(Files.exists(ROOT.resolve(
                "src/main/resources/templates/practice/manage/import-workspace.html")))
                .isFalse();
        assertThat(reviewTemplate).contains(
                "practice/manage/fragments/draft-preview :: modal",
                "/js/practice/manage-draft-preview.js");
    }

    @Test
    void operationsRunbookKeepsRollbackForwardOnlyAndLaterWorkBounded()
            throws Exception {
        String runbook = read(
                "docs/operations/practice-authoring-import-modernization-runbook.md");
        assertThat(runbook).contains(
                "Forward-only application rollback",
                "Keep V83-V85 and their Flyway history intact",
                "AI/R2/STT/TTS = 0/0/0/0",
                "Legacy Excel V1, Advanced Excel V2, Advanced PDF crop/region",
                "Provider API-console/documentation links remain a bounded",
                "does not close",
                "Do not declare Phase 14, Phase 15, Pre-15");
    }

    private static List<String> lines(String relative) throws Exception {
        return Files.readAllLines(ROOT.resolve(relative)).stream()
                .filter(line -> !line.isBlank()).toList();
    }

    private static int version(Path path) {
        Matcher matcher = MIGRATION.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a migration: " + path);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static Path javaPath(String suffix) {
        return ROOT.resolve("src/main/java/com/ksh/features/practice")
                .resolve(suffix);
    }

    private static String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative));
    }
}
