package com.ksh.features.practice.manage.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.practice.manage.service.PracticeDraftContractService;
import com.ksh.features.practice.manage.validator.PracticeDraftValidator;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PracticeTopik35CandidateImporterTest {

    private static final Path OPERATIONS = Path.of("docs/operations");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PracticeDraftRepository drafts =
            mock(PracticeDraftRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PracticeDraftContractService draftContract =
            mock(PracticeDraftContractService.class);
    private final PracticeDraftValidator draftValidator =
            mock(PracticeDraftValidator.class);
    private PracticeTopik35CandidateImporter importer;

    @BeforeEach
    void setUp() {
        importer = new PracticeTopik35CandidateImporter(
                objectMapper, drafts, users, draftContract, draftValidator);
    }

    @Test
    void repositoryBundleDryRunIsDeterministicAndFailClosed() {
        var first = importer.dryRun(OPERATIONS);
        var second = importer.dryRun(OPERATIONS);

        assertThat(first).isEqualTo(second);
        assertThat(first.status()).isEqualTo(
                PracticeTopik35CandidateImporter.ImportStatus.DRY_RUN);
        assertThat(first.bundleId()).isEqualTo("topik35-v1");
        assertThat(first.identityDigest()).matches("[0-9a-f]{64}");
        assertThat(first.readingQuestionCount()).isEqualTo(50);
        assertThat(first.listeningQuestionCount()).isEqualTo(50);
        assertThat(first.writingQuestionCount()).isEqualTo(4);
        assertThat(first.readingGroupCount()).isEqualTo(42);
        assertThat(first.listeningGroupCount()).isEqualTo(20);
        assertThat(first.logicalAssetKeyCount()).isEqualTo(24);
        assertThat(first.providerCallCount()).isZero();
        assertThat(first.blockers()).contains(
                "LISTENING_TIMING_PENDING_MANUAL_AUDIO_QA",
                "READING_LOAD_NOT_READY",
                "LISTENING_LOAD_NOT_READY",
                "WRITING_LOAD_NOT_READY",
                "CANONICAL_VERSION_REFERENCES_INCOMPLETE");
        assertThat(first.candidateDraftId()).isNull();
    }

    @Test
    void blockedImportIsAllOrNothingAndDoesNotEvenLockOwner() {
        var first = importer.importCandidate(OPERATIONS, 42L);
        var second = importer.importCandidate(OPERATIONS, 42L);

        assertThat(first.status()).isEqualTo(
                PracticeTopik35CandidateImporter.ImportStatus.REJECTED);
        assertThat(second).isEqualTo(first);
        assertThat(first.providerCallCount()).isZero();
        verifyNoInteractions(drafts, users, draftContract, draftValidator);
    }

    @Test
    void adversarialLogicalKeyOrQuestionOwnershipIsRejected(
            @TempDir Path temporary) throws Exception {
        copyPackages(temporary);
        Path reading = temporary.resolve(
                "practice-topik35-reading-question-payload.json");
        ObjectNode root = (ObjectNode) objectMapper.readTree(
                Files.readString(reading));
        ObjectNode visual = (ObjectNode) root.path("visualAssets").get(0);
        visual.put("logicalKey", "/Users/example/private.png");
        visual.put("sha256", "missing-digest");
        root.put("schemaVersion", "unsupported-schema");
        ((ObjectNode) root.path("questions").get(0))
                .put("groupId", "R999");
        Files.writeString(reading, objectMapper.writeValueAsString(root));
        Path listening = temporary.resolve(
                "practice-topik35-listening-import-package.json");
        ObjectNode listeningRoot = (ObjectNode) objectMapper.readTree(
                Files.readString(listening));
        ((ObjectNode) listeningRoot.path("audioQaBinding"))
                .put("packageId", "wrong-audio-qa-package");
        Files.writeString(listening,
                objectMapper.writeValueAsString(listeningRoot));

        var result = importer.dryRun(temporary);

        assertThat(result.blockers()).contains(
                "LOCAL_OR_DELIVERY_PATH_LEAK:practice-topik35-reading-question-payload.json",
                "INVALID_LOGICAL_KEY:practice-topik35-reading-question-payload.json",
                "INVALID_SHA256:practice-topik35-reading-question-payload.json",
                "PACKAGE_SCHEMA_MISMATCH:practice-topik35-reading-question-payload.json",
                "R_QUESTION_VERSION_OWNERSHIP_INVALID",
                "LISTENING_PACKAGE_REFERENCE_MISMATCH");
    }

    @Test
    void readyPackageCreatesThenReusesOneDisabledDraft(
            @TempDir Path temporary) throws Exception {
        copyPackages(temporary);
        makeTestOnlyReady(temporary);
        when(draftContract.normalize(
                any(ObjectNode.class), anyString()))
                .thenAnswer(invocation ->
                        new PracticeDraftContractService.NormalizedDraft(
                                invocation.<ObjectNode>getArgument(0).toString()));
        when(draftValidator.validate(anyString())).thenReturn(
                new PracticeDraftValidator.ValidationResult(
                        false, List.of(), 3, 66, 104, 300));
        User owner = mock(User.class);
        when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(owner));
        when(drafts.findByOwnerIdOrderByUpdatedAtDesc(42L))
                .thenReturn(List.of());
        when(drafts.saveAndFlush(any(PracticeDraft.class)))
                .thenAnswer(invocation -> {
                    PracticeDraft draft = invocation.getArgument(0);
                    ReflectionTestUtils.setField(draft, "id", 9001L);
                    return draft;
                });

        var created = importer.importCandidate(temporary, 42L);
        var captor = org.mockito.ArgumentCaptor.forClass(PracticeDraft.class);
        verify(drafts).saveAndFlush(captor.capture());
        PracticeDraft persisted = captor.getValue();
        when(drafts.findByOwnerIdOrderByUpdatedAtDesc(42L))
                .thenReturn(List.of(persisted));
        var replay = importer.importCandidate(temporary, 42L);

        assertThat(created.status()).isEqualTo(
                PracticeTopik35CandidateImporter.ImportStatus.CREATED);
        assertThat(created.candidateDraftId()).isEqualTo(9001L);
        assertThat(replay.status()).isEqualTo(
                PracticeTopik35CandidateImporter.ImportStatus.REUSED);
        assertThat(replay.candidateDraftId()).isEqualTo(9001L);
        assertThat(replay.identityDigest()).isEqualTo(created.identityDigest());
        assertThat(persisted.getStatus()).isEqualTo("DRAFT");
        assertThat(persisted.getPublishedSetId()).isNull();
        assertThat(persisted.getCreationMethod()).isEqualTo("CANONICAL_SEED");
        JsonNode draft = objectMapper.readTree(persisted.getDraftJson());
        assertThat(draft.path("tests").get(0).path("clientId").asText())
                .isEqualTo("topik35-v1-test-1");
        assertThat(draft.path("sections")).hasSize(3);
        assertThat(draft.path("sections").get(0).path("groups")).hasSize(42);
        assertThat(draft.path("sections").get(1).path("groups")).hasSize(20);
        assertThat(draft.path("sections").get(2).path("groups")).hasSize(4);
        verify(drafts).saveAndFlush(any(PracticeDraft.class));
        verify(drafts, never()).delete(any());
    }

    private void copyPackages(Path target) throws Exception {
        try (var files = Files.list(OPERATIONS)) {
            for (Path source : files.filter(path -> path.getFileName()
                    .toString().startsWith("practice-topik35-"))
                    .filter(path -> path.getFileName().toString()
                            .endsWith(".json"))
                    .toList()) {
                Files.copy(source, target.resolve(source.getFileName()));
            }
        }
    }

    /** Test-only fixture mutation; the production importer exposes no bypass. */
    private void makeTestOnlyReady(Path target) throws Exception {
        mutate(target, "practice-topik35-reading-question-payload.json",
                root -> {
                    ((ObjectNode) root.path("loadPolicy"))
                            .put("loadReady", true);
                    ((ObjectNode) root).putArray("remainingLoadBlockers");
                });
        mutate(target, "practice-topik35-listening-import-package.json",
                root -> {
                    ((ObjectNode) root.path("validationSummary"))
                            .put("loadReady", true)
                            .put("timingReadyGroupCount", 20);
                    int index = 0;
                    for (JsonNode group : root.path("groups")) {
                        ObjectNode timing = (ObjectNode) group.path("timingQa");
                        timing.put("status",
                                PracticeTopik35CandidateImporter.TIMING_VERIFIED);
                        timing.put("startMs", index * 10_000L);
                        timing.put("endMs", (++index) * 10_000L);
                        timing.put("firstAudibleCueMatched", true);
                        timing.put("finalAudibleCueMatched", true);
                        timing.put("repeatPlaybackAccountedFor", true);
                        timing.put("neighborBoundaryChecked", true);
                        timing.put("transcriptBoundaryChecked", true);
                        timing.put("reviewerEvidenceId",
                                "TEST_ONLY_TIMING_" + index);
                    }
                });
        mutate(target, "practice-topik35-listening-question-payload.json",
                root -> ((ObjectNode) root)
                        .putArray("remainingLoadBlockers"));
        mutate(target, "practice-topik35-listening-transcript-payload.json",
                root -> {
                    int index = 0;
                    for (JsonNode group : root.path("groups")) {
                        ObjectNode object = (ObjectNode) group;
                        object.put("timingStatus",
                                PracticeTopik35CandidateImporter.TIMING_VERIFIED);
                        object.put("startMs", index * 10_000L);
                        object.put("endMs", (++index) * 10_000L);
                    }
                    ((ObjectNode) root).putArray("remainingLoadBlockers");
                });
        mutate(target, "practice-topik35-listening-audio-qa.json",
                root -> {
                    ((ObjectNode) root.path("validationSummary"))
                            .put("boundaryReadyGroupCount", 20)
                            .put("loadReady", true);
                    ObjectNode manual = (ObjectNode) root.path(
                            "manualBoundaryQa");
                    manual.put("auditoryReviewerAvailable", true);
                    int index = 0;
                    for (JsonNode group : manual.path("groups")) {
                        ObjectNode object = (ObjectNode) group;
                        object.put("status",
                                PracticeTopik35CandidateImporter.TIMING_VERIFIED);
                        object.put("startMs", index * 10_000L);
                        object.put("endMs", (++index) * 10_000L);
                        object.put("reviewerEvidenceId",
                                "TEST_ONLY_TIMING_" + index);
                    }
                });
        mutate(target, "practice-topik35-writing-import-audit.json",
                root -> {
                    ((ObjectNode) root.path("loadPolicy"))
                            .put("loadReady", true);
                    ((ObjectNode) root.path("targetContract"))
                            .put("candidateMaterialized", true);
                    ((ObjectNode) root).putArray("qaBlockers");
                });
    }

    private void mutate(Path directory,
                        String filename,
                        java.util.function.Consumer<ObjectNode> mutation)
            throws Exception {
        Path path = directory.resolve(filename);
        ObjectNode root = (ObjectNode) objectMapper.readTree(
                Files.readString(path));
        mutation.accept(root);
        Files.writeString(path, objectMapper.writeValueAsString(root));
    }
}
