package com.ksh.features.practice.manage;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PracticePdfRetirementStaticContractTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void advancedSessionRuntimeGraphIsAbsentWhileHistoricalMigrationsRemain()
            throws Exception {
        List<String> retiredJava = List.of(
                "entities/PracticePdfImportSession.java",
                "entities/PracticePdfRegionAnnotation.java",
                "entities/PracticePdfPageExtraction.java",
                "entities/PracticePdfImportSectionDraft.java",
                "entities/PracticePdfImportGroupDraft.java",
                "entities/PracticeAiRequestAudit.java",
                "features/practice/manage/service/PracticePdfImportSessionService.java",
                "features/practice/manage/service/PracticeImportSnapshotService.java",
                "features/practice/manage/service/PracticePdfAiGenerationService.java",
                "features/practice/manage/service/PracticePdfCropService.java",
                "features/practice/manage/service/PracticePdfRegionService.java",
                "features/practice/manage/service/PracticePdfPageExtractionService.java",
                "features/practice/manage/service/PracticePdfPayloadPreviewService.java",
                "features/practice/manage/service/PracticePdfPreviewService.java",
                "features/practice/manage/dto/AiDocumentImportRequest.java",
                "features/practice/manage/validator/ImportAiPayloadValidator.java",
                "features/practice/pdf/PracticePdfStorageService.java");
        assertThat(retiredJava).allSatisfy(relative ->
                assertThat(Files.exists(ROOT.resolve("src/main/java/com/ksh/" + relative)))
                        .as(relative).isFalse());
        assertThat(Files.exists(ROOT.resolve(
                "src/main/resources/templates/practice/manage/import-workspace.html")))
                .isFalse();

        String migrations = migrationSources();
        assertThat(migrations).contains(
                "practice_pdf_import_sessions",
                "practice_pdf_page_extractions",
                "practice_pdf_region_annotations",
                "practice_pdf_import_section_drafts",
                "practice_pdf_import_group_drafts",
                "practice_ai_request_audits");
        try (var paths = Files.list(ROOT.resolve("src/main/resources/db/migration"))) {
            List<String> names = paths.map(path -> path.getFileName().toString()).toList();
            assertThat(names).anyMatch(name -> name.startsWith("V111__"));
            assertThat(names).filteredOn(name -> {
                int separator = name.indexOf("__");
                return separator > 1
                        && Integer.parseInt(name.substring(1, separator)) > 111
                        && name.contains("practice_");
            }).containsExactlyInAnyOrder(
                    "V114__retire_practice_collaboration_and_dark_audio_review.sql",
                    "V118__practice_demo_canonical_seed_catalog.sql",
                    "V119__practice_topik35_premium_canonical_catalog.sql");
        }
    }

    @Test
    void productionRuntimeHasNoLegacyTableOrPdfProvenanceConsumer()
            throws Exception {
        String production = javaSources();
        assertThat(production).doesNotContain(
                "practice_pdf_import_sessions",
                "practice_pdf_page_extractions",
                "practice_pdf_region_annotations",
                "practice_pdf_import_section_drafts",
                "practice_pdf_import_group_drafts",
                "practice_ai_request_audits",
                "source_import_session_id",
                "source_region_id",
                "source_page_number",
                "crop_x",
                "crop_y",
                "crop_width",
                "crop_height");
        String logicalTypes = read(
                "src/main/java/com/ksh/entities/PracticeStorageMigrationLogicalType.java");
        String identityMigration = read(
                "src/main/java/com/ksh/features/practice/service/storage/PracticeStorageMigrationIdentityService.java");
        String objectMigration = read(
                "src/main/java/com/ksh/features/practice/service/storage/ProfiledPracticeStorageMigrationObjectPort.java");
        assertThat(logicalTypes).contains("PDF_IMPORT_SESSION(");
        assertThat(identityMigration)
                .contains(
                        "case PDF_IMPORT_SESSION -> throw new IllegalStateException(",
                        "PDF_IMPORT_SESSION_MIGRATION_RETIRED")
                .doesNotContain("practice_pdf_import_sessions");
        assertThat(objectMigration)
                .contains(
                        "== com.ksh.entities.PracticeStorageMigrationLogicalType.PDF_IMPORT_SESSION",
                        "PDF_IMPORT_SESSION_MIGRATION_RETIRED")
                .doesNotContain("PracticePdfStorageService");
    }

    @Test
    void basicPdfIsBoundedRequestLocalAndCandidateOnly() throws Exception {
        String builder = read(
                "src/main/java/com/ksh/features/practice/manage/service/PracticePdfAiPayloadBuilder.java");
        String controller = read(
                "src/main/java/com/ksh/features/practice/manage/controller/PracticePdfImportApiController.java");
        String orchestrator = read(
                "src/main/java/com/ksh/features/practice/manage/service/PracticePdfAiOrchestrator.java");
        String jsonContract = read(
                "src/main/java/com/ksh/features/practice/manage/service/PracticePdfAuthoringJsonContract.java");
        String adapter = read(
                "src/main/java/com/ksh/features/practice/ai/transport/PracticeControlPlaneStructuredGenerationAdapter.java");

        assertThat(builder).contains(
                "20L * 1024L * 1024L",
                "\"%PDF-\"",
                "readNBytes((int) MAX_PDF_BYTES + 1)",
                "Arrays.fill(bytes, (byte) 0)",
                "try (PDDocument document = Loader.loadPDF(bytes))",
                "requirePageRange",
                "evidenceDigest",
                "sha256:")
                .doesNotContain(
                        "Repository", "StorageService", "createSession", "save(");
        assertThat(controller).contains(
                "targetService.requireExactTarget(",
                "payloadBuilder.buildBasicPdf(",
                "candidateAssembler.assemble(",
                "reviewUrl")
                .doesNotContain(
                        "PracticeDraftRepository", "setDraftJson(",
                        "import-sessions", "sessionService");
        assertThat(orchestrator).contains(
                "PracticeAiPurpose.PRACTICE_PDF_AUTHORING",
                "identity.bindingRevision() < 0",
                "PracticePdfAuthoringJsonContract.schema()")
                .doesNotContain("PracticeAiRequestAudit");
        assertThat(jsonContract).contains(
                "enumString(\"TEXT_SPAN\", \"PAGE\")")
                .doesNotContain("\"PAGE\", \"REGION\"");
        assertThat(adapter).contains(
                "auditService.start(", "auditService.success(",
                "auditService.failure(", "auditService.cancelled(");
    }

    @Test
    void basicUiAndSharedAssetApiHaveNoOrphanSessionConsumer() throws Exception {
        String wizard = read(
                "src/main/resources/templates/practice/manage/import-wizard.html");
        String editor = read(
                "src/main/resources/templates/practice/manage/editor.html");
        String dashboard = read(
                "src/main/resources/templates/practice/manage/dashboard.html");
        String controller = read(
                "src/main/java/com/ksh/features/practice/manage/controller/PracticePdfImportApiController.java");
        String storageProfiles = read(
                "src/main/java/com/ksh/features/admin/settings/service/StorageProfileAdminService.java");
        String practiceController = read(
                "src/main/java/com/ksh/features/practice/controller/PracticeController.java");
        String practiceRoutes = read(
                "src/main/java/com/ksh/features/practice/web/PracticeRoutes.java");

        assertThat(wizard).contains(
                "id=\"basic-source-type\"",
                "id=\"basic-target-section\"",
                "/practice/manage/pdf-authoring/candidates",
                "window.location.assign(payload.reviewUrl)")
                .doesNotContain(
                        "import-sessions", "advanced-authoring", "recentSessions",
                        "pdfjsLib", "cdnjs", "payload-preview", "annotations");
        assertThat(editor).contains(
                "`/practice/manage/import?draftId=${encodeURIComponent(DRAFT_ID)}`",
                "+ `&skill=${encodeURIComponent(section.skill)}`",
                "pdfAction.removeAttribute('href')",
                "editorJsonFetch('/practice/manage/assets'")
                .doesNotContain(
                        "currentSessionId", "asset-tab-session", "Từ tệp PDF này");
        assertThat(dashboard).doesNotContain("@{/practice/manage/import}");
        assertThat(practiceController).doesNotContain("redirect:/practice/manage/import");
        assertThat(practiceRoutes).doesNotContain("MANAGE_UPLOAD", "/manage/upload");
        assertThat(controller).contains(
                "@GetMapping(\"/assets\")",
                "@DeleteMapping(\"/assets/{assetId}\")",
                "@PostMapping(\"/drafts/{draftId}/assets\")")
                .doesNotContain("@PatchMapping", "sessionId", "unlinkAsset");
        assertThat(storageProfiles).doesNotContain("practice_pdf_import_sessions");
    }

    private static String javaSources() throws Exception {
        try (var paths = Files.walk(ROOT.resolve("src/main/java"))) {
            StringBuilder sources = new StringBuilder();
            for (Path path : paths.filter(file -> file.toString().endsWith(".java"))
                    .toList()) {
                sources.append(Files.readString(path));
            }
            return sources.toString();
        }
    }

    private static String migrationSources() throws Exception {
        try (var paths = Files.walk(ROOT.resolve("src/main/resources/db/migration"))) {
            StringBuilder sources = new StringBuilder();
            for (Path path : paths.filter(file -> file.toString().endsWith(".sql"))
                    .toList()) {
                sources.append(Files.readString(path));
            }
            return sources.toString();
        }
    }

    private static String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative));
    }
}
