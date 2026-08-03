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
    void repositoryBundleDryRunIsDeterministicAndCandidateReady() {
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
        assertThat(first.blockers()).isEmpty();
        assertThat(first.candidateDraftId()).isNull();
    }

    @Test
    void contentBlockedImportIsAllOrNothingAndDoesNotEvenLockOwner(
            @TempDir Path temporary) throws Exception {
        copyPackages(temporary);
        mutate(temporary, "practice-topik35-reading-question-payload.json",
                root -> ((ObjectNode) root.path("loadPolicy"))
                        .put("contentQaComplete", false));

        var first = importer.importCandidate(temporary, 42L);
        var second = importer.importCandidate(temporary, 42L);

        assertThat(first.status()).isEqualTo(
                PracticeTopik35CandidateImporter.ImportStatus.REJECTED);
        assertThat(second).isEqualTo(first);
        assertThat(first.blockers()).contains(
                "READING_CONTENT_NOT_CANDIDATE_READY");
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
        assertThat(draft.path("sections").get(1)
                .path("sectionDelivery").path("listeningDelivery")
                .path("timestampAutoNavigation").asBoolean()).isFalse();
        assertThat(draft.path("sections").get(1)
                .path("sectionDelivery").path("listeningDelivery")
                .path("seekAllowed").asBoolean()).isFalse();
        for (JsonNode group : draft.path("sections").get(2).path("groups")) {
            assertThat(group.path("questions").get(0)
                    .path("answerSpec").path("evaluationMode").asText())
                    .isEqualTo("MANUAL_OR_EXPERIMENTAL_UNSCORED");
        }
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
