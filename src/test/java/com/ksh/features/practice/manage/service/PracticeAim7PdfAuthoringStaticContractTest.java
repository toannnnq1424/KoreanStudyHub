package com.ksh.features.practice.manage.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeAim7PdfAuthoringStaticContractTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void retiredDirectDraftPathIsAbsentAndControllerReturnsCandidateOnly()
            throws Exception {
        assertThat(Files.exists(java("service/PracticePdfDraftAssembler.java")))
                .isFalse();
        assertThat(Files.exists(java("service/PracticeImportDraftService.java")))
                .isFalse();
        assertThat(Files.exists(java("service/PracticePdfAiPromptRules.java")))
                .isFalse();

        String controller = read(java("controller/PracticePdfImportApiController.java"));
        String assembler = read(java("service/PracticePdfAuthoringCandidateAssembler.java"));
        assertThat(controller)
                .contains("/pdf-authoring/candidates", "candidateResponse(candidate)")
                .doesNotContain(
                        "PracticeDraft", "PracticeDraftRepository",
                        "PracticePdfDraftAssembler", "PracticeImportDraftService",
                        "create-manual-draft", "attach-to-draft");
        assertThat(assembler)
                .contains("PracticeAuthoringCandidateService", "createOrReuse")
                .doesNotContain(
                        "PracticeDraft", "PracticeDraftRepository",
                        "snapshotJson", "setSnapshotJson", "saveAndFlush");
    }

    @Test
    void pdfDataPlaneUsesOnlyExactPracticePurposeWithoutFallback() throws Exception {
        String orchestrator = read(java("service/PracticePdfAiOrchestrator.java"));
        String payload = read(java("service/PracticePdfAiPayloadBuilder.java"));
        assertThat(orchestrator)
                .contains(
                        "PracticeStructuredGenerationPort",
                        "PracticeAiPurpose.PRACTICE_PDF_AUTHORING",
                        "findByNameAndEnabledTrue(ADMIN_PROMPT_NAME)",
                        "PROVIDER_PURPOSE_UNAVAILABLE",
                        "PROVIDER_BINDING_CHANGED")
                .doesNotContain(
                        "com.ksh.features.ai.client.AiClient",
                        "AiProviderRepository", "findEnabledOrdered(",
                        "RestClient.builder", "snapshotJson");
        assertThat(payload).doesNotContain("GENERAL_UPLOADS", "snapshotJson");
    }

    @Test
    void strictSchemaOwnsAllObjectVocabulariesAndNoEvaluationFields() {
        assertAllObjectSchemasClosed(PracticePdfAuthoringJsonContract.schema(), "/");
        Set<String> outputVocabulary = Set.of(
                PracticePdfAuthoringJsonContract.ROOT_FIELDS,
                PracticePdfAuthoringJsonContract.GROUP_FIELDS,
                PracticePdfAuthoringJsonContract.STIMULUS_FIELDS,
                PracticePdfAuthoringJsonContract.QUESTION_FIELDS,
                PracticePdfAuthoringJsonContract.WARNING_FIELDS,
                PracticePdfAuthoringJsonContract.SOURCE_REF_FIELDS,
                PracticePdfAuthoringJsonContract.CONTENT_FIELDS,
                PracticePdfAuthoringJsonContract.ANSWER_FIELDS).stream()
                .flatMap(Set::stream)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertThat(outputVocabulary).doesNotContain(
                "scoreSummary", "rubricScores", "taskCoverage",
                "diagnosticStates", "evidenceLedger", "findings",
                "upgradedAnswer", "submission", "result");
    }

    @Test
    void basicAndAdvancedUiShareTheExistingCandidateReviewJourney()
            throws Exception {
        String wizard = read(ROOT.resolve(
                "src/main/resources/templates/practice/manage/import-wizard.html"));
        String workspace = read(ROOT.resolve(
                "src/main/resources/templates/practice/manage/import-workspace.html"));
        assertThat(wizard)
                .contains(
                        "Text/PDF → candidate có thể chỉnh sửa",
                        "id=\"basic-source-type\"",
                        "id=\"basic-operation\"",
                        "id=\"advanced-authoring\"",
                        "/practice/manage/pdf-authoring/candidates",
                        "window.location.href = payload.reviewUrl");
        assertThat(workspace)
                .contains(
                        "candidate review hiện có",
                        "operation: 'EXTRACT'",
                        "window.location.href = payload.reviewUrl")
                .doesNotContain(
                        "create-manual-draft", "attach-to-draft",
                        "convertDraftToManual");
    }

    @Test
    void aim7AddsNoMigrationBeyondAim6StorageBaseline() throws Exception {
        Path migrations = ROOT.resolve("src/main/resources/db/migration");
        try (var paths = Files.list(migrations)) {
            List<String> names = paths.map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("V\\d+__.*\\.sql"))
                    .toList();
            assertThat(names).anyMatch(name -> name.startsWith("V85__"));
            assertThat(names).noneMatch(name -> {
                int separator = name.indexOf("__");
                return Integer.parseInt(name.substring(1, separator)) > 85;
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertAllObjectSchemasClosed(Object value, String path) {
        if (value instanceof Map<?, ?> map) {
            if ("object".equals(map.get("type"))) {
                assertThat(map.get("additionalProperties"))
                        .as("closed schema at %s", path)
                        .isEqualTo(false);
            }
            map.forEach((key, child) -> assertAllObjectSchemasClosed(
                    child, path + "/" + key));
        } else if (value instanceof Iterable<?> values) {
            int index = 0;
            for (Object child : values) {
                assertAllObjectSchemasClosed(child, path + "/" + index++);
            }
        }
    }

    private static Path java(String suffix) {
        return ROOT.resolve("src/main/java/com/ksh/features/practice/manage/" + suffix);
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path);
    }
}
