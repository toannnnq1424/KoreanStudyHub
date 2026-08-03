package com.ksh.features.practice.manage.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.manage.service.AssetStorageService;
import com.ksh.features.practice.manage.service.PracticeMaterialReferenceService;
import com.ksh.features.practice.manage.service.PracticeSeedAssetStorage;
import com.ksh.features.practice.manage.service.PracticeUploadContentVerifier;
import com.ksh.features.practice.manage.validator.PracticeDraftValidator;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PracticeTopik35CheckAudioBinderTest {

    private static final Long OWNER = 42L;
    private static final Long DRAFT_ID = 500L;
    private static final Long ASSET_ID = 700L;
    private static final String KEY =
            "practice-seed/topik35-v1/review/artifact/"
                    + PracticeTopik35CheckAudioBinder.FULL_SHA256 + ".wav";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PracticeDraftRepository drafts =
            mock(PracticeDraftRepository.class);
    private final LecturerAssetRepository assets =
            mock(LecturerAssetRepository.class);
    private final PracticeSeedAssetStorage seedStorage =
            mock(PracticeSeedAssetStorage.class);
    private final AssetStorageService assetStorage =
            mock(AssetStorageService.class);
    private final PracticeUploadContentVerifier verifier =
            new PracticeUploadContentVerifier();
    private final PracticeMaterialReferenceService references =
            mock(PracticeMaterialReferenceService.class);
    private final PracticeDraftValidator draftValidator =
            mock(PracticeDraftValidator.class);
    private PracticeTopik35CheckAudioBinder binder;

    @BeforeEach
    void setUp() {
        binder = new PracticeTopik35CheckAudioBinder(
                objectMapper, jdbc, drafts, assets, seedStorage,
                assetStorage, verifier, references, draftValidator);
        when(jdbc.queryForObject("SELECT DATABASE()", String.class))
                .thenReturn("ksh_test_topik35_candidate_check_audio_unit");
        when(assetStorage.profileCode()).thenReturn("PRACTICE_AUTHORING");
        when(assetStorage.providerCode()).thenReturn("LOCAL");
        when(draftValidator.validate(anyString())).thenReturn(
                new PracticeDraftValidator.ValidationResult(
                        false, List.of(), 3, 66, 104, 300));
    }

    @Test
    void createsThenReusesExactOwnerScopedBindingWithoutChangingPolicy()
            throws Exception {
        PracticeDraft draft = candidate(OWNER);
        when(drafts.findByIdForUpdate(DRAFT_ID))
                .thenReturn(Optional.of(draft));
        when(seedStorage.store(
                org.mockito.ArgumentMatchers.eq("topik35-v1"),
                org.mockito.ArgumentMatchers.eq(
                        PracticeSeedAssetStorage.AssetKind.REVIEW_ARTIFACT),
                any(InputStream.class),
                org.mockito.ArgumentMatchers.eq(
                        PracticeTopik35CheckAudioBinder.ORIGINAL_FILENAME),
                org.mockito.ArgumentMatchers.eq(
                        PracticeTopik35CheckAudioBinder.MEDIA_TYPE)))
                .thenReturn(stored(true));
        when(assets.findByOwnerLecturerIdAndSha256AndStatusAndDeletedAtIsNull(
                OWNER, PracticeTopik35CheckAudioBinder.FULL_SHA256, "ACTIVE"))
                .thenReturn(List.of());
        when(assets.saveAndFlush(any(LecturerAsset.class)))
                .thenAnswer(invocation -> {
                    LecturerAsset asset = invocation.getArgument(0);
                    ReflectionTestUtils.setField(asset, "id", ASSET_ID);
                    return asset;
                });

        PracticeTopik35CheckAudioBinder.BindResult created =
                binder.bind(DRAFT_ID, OWNER);
        var assetCaptor = org.mockito.ArgumentCaptor
                .forClass(LecturerAsset.class);
        verify(assets).saveAndFlush(assetCaptor.capture());
        LecturerAsset asset = assetCaptor.getValue();

        assertThat(created.status()).isEqualTo(
                PracticeTopik35CheckAudioBinder.BindStatus.CREATED);
        assertThat(created.assetId()).isEqualTo(ASSET_ID);
        assertThat(asset.getOwnerLecturerId()).isEqualTo(OWNER);
        assertThat(asset.getSourceType()).isEqualTo("MANUAL_UPLOAD");
        assertThat(asset.getStorageProvider()).isEqualTo("LOCAL");
        assertThat(asset.getStorageKey()).isEqualTo(KEY);
        assertThat(asset.getSha256()).isEqualTo(
                PracticeTopik35CheckAudioBinder.FULL_SHA256);
        assertThat(asset.getTagsJson())
                .contains("\"ownership\":\"DRAFT_OWNER_ONLY\"")
                .contains("\"shared\":false")
                .doesNotContain("/Users/", "file://", "r2://");

        JsonNode bound = objectMapper.readTree(draft.getDraftJson());
        JsonNode listening = bound.path("sections").get(1)
                .path("sectionDelivery").path("listeningDelivery");
        assertThat(listening.path("checkAudioReference").asText())
                .isEqualTo("/practice/materials/700/content");
        assertThat(listening.path("candidateCheckAudioState").asText())
                .isEqualTo("BOUND_OWNER_SCOPED");
        assertThat(listening.path("seekAllowed").asBoolean()).isFalse();
        assertThat(listening.path("replayAllowed").asBoolean()).isFalse();
        assertThat(listening.path("timestampAutoNavigation").asBoolean())
                .isFalse();
        assertThat(bound.path("seedImport")
                .path("listeningTimingRequiredForPublication").asBoolean())
                .isFalse();
        assertWritingUnscored(bound);

        when(assets.findByIdForUpdate(ASSET_ID))
                .thenReturn(Optional.of(asset));
        when(references.hasDraftReference(
                DRAFT_ID, ASSET_ID, "LISTENING_CHECK_AUDIO",
                PracticeTopik35CheckAudioBinder.SECTION_CLIENT_ID))
                .thenReturn(true);
        when(assetStorage.exists("PRACTICE_AUTHORING", KEY))
                .thenReturn(true);
        when(assetStorage.load("PRACTICE_AUTHORING", KEY))
                .thenReturn(new ByteArrayResource(approvedBytes()));

        PracticeTopik35CheckAudioBinder.BindResult replay =
                binder.bind(DRAFT_ID, OWNER);

        assertThat(replay.status()).isEqualTo(
                PracticeTopik35CheckAudioBinder.BindStatus.REUSED);
        assertThat(replay).usingRecursiveComparison()
                .ignoringFields("status").isEqualTo(created);
        verify(seedStorage).store(
                org.mockito.ArgumentMatchers.eq("topik35-v1"),
                org.mockito.ArgumentMatchers.eq(
                        PracticeSeedAssetStorage.AssetKind.REVIEW_ARTIFACT),
                any(InputStream.class), anyString(), anyString());
        verify(drafts).saveAndFlush(draft);

        PracticeTopik35CheckAudioBinder.WithdrawResult withdrawn =
                binder.withdraw(DRAFT_ID, OWNER);
        JsonNode withdrawnDraft = objectMapper.readTree(draft.getDraftJson());
        JsonNode withdrawnListening = withdrawnDraft.path("sections").get(1)
                .path("sectionDelivery").path("listeningDelivery");

        assertThat(withdrawn.assetId()).isEqualTo(ASSET_ID);
        assertThat(withdrawn.candidateCheckAudioState())
                .isEqualTo("WITHDRAWN_MATERIAL_REQUIRED");
        assertThat(withdrawnListening.path("checkAudioReference").isNull())
                .isTrue();
        assertThat(withdrawnDraft.path("seedImport")
                .has("listeningCheckAudioBinding")).isFalse();
        assertWritingUnscored(withdrawnDraft);
        verify(references).unlinkDraft(
                DRAFT_ID, ASSET_ID, "LISTENING_CHECK_AUDIO",
                PracticeTopik35CheckAudioBinder.SECTION_CLIENT_ID);
    }

    @Test
    void wrongOwnerFailsBeforeAssetOrStorageAccess() throws Exception {
        PracticeDraft draft = candidate(OWNER);
        when(drafts.findByIdForUpdate(DRAFT_ID))
                .thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> binder.bind(DRAFT_ID, 43L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TOPIK35_CHECK_AUDIO_OWNER_MISMATCH");

        verifyNoInteractions(seedStorage, references);
        verify(assets, never()).saveAndFlush(any());
        verify(assetStorage, never()).store(any(), anyString(), anyString());
    }

    @Test
    void foreignOwnerAssetCannotSatisfyExistingReference() throws Exception {
        PracticeDraft draft = candidate(OWNER);
        ObjectNode root = (ObjectNode) objectMapper.readTree(
                draft.getDraftJson());
        ObjectNode listening = (ObjectNode) root.path("sections").get(1)
                .path("sectionDelivery").path("listeningDelivery");
        listening.put("checkAudioReference",
                "/practice/materials/700/content");
        draft.setDraftJson(root.toString());
        LecturerAsset foreign = exactAsset(43L);
        when(drafts.findByIdForUpdate(DRAFT_ID))
                .thenReturn(Optional.of(draft));
        when(assets.findByIdForUpdate(ASSET_ID))
                .thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> binder.bind(DRAFT_ID, OWNER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TOPIK35_CHECK_AUDIO_ASSET_IDENTITY_INVALID");

        verifyNoInteractions(seedStorage, references);
        verify(assetStorage, never()).load(anyString(), anyString());
    }

    @Test
    void nonDisposableCatalogFailsBeforeDraftLock() {
        when(jdbc.queryForObject("SELECT DATABASE()", String.class))
                .thenReturn("ksh_db");

        assertThatThrownBy(() -> binder.bind(DRAFT_ID, OWNER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TOPIK35_CHECK_AUDIO_DISPOSABLE_DB_REQUIRED");

        verifyNoInteractions(drafts, assets, seedStorage, references);
    }

    private PracticeDraft candidate(Long ownerId) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", "practice-draft-v3");
        ObjectNode seed = root.putObject("seedImport");
        seed.put("importerVersion",
                PracticeTopik35CandidateImporter.IMPORTER_VERSION);
        seed.put("bundleId", PracticeTopik35CandidateImporter.BUNDLE_ID);
        seed.put("identityDigest", "a".repeat(64));
        seed.put("publicationAllowed", false);
        seed.put("listeningTimingState",
                "PENDING_OPTIONAL_POST_TEST_QA");
        seed.put("listeningTimingRequiredForCandidate", false);
        seed.put("listeningTimingRequiredForPublication", false);
        seed.put("listeningTimingAllowedForExamAssistance", false);
        ArrayNode sections = root.putArray("sections");
        sections.addObject().put("clientId", "reading").put("skill", "READING");
        ObjectNode listeningSection = sections.addObject();
        listeningSection.put("clientId",
                PracticeTopik35CheckAudioBinder.SECTION_CLIENT_ID);
        listeningSection.put("skill", "LISTENING");
        ObjectNode delivery = listeningSection.putObject("sectionDelivery")
                .putObject("listeningDelivery");
        delivery.putNull("checkAudioReference");
        delivery.put("singleOrderedAudioProgram", true);
        delivery.put("startOnce", true);
        delivery.put("continuousPlayback", true);
        delivery.put("seekAllowed", false);
        delivery.put("replayAllowed", false);
        delivery.put("timestampAutoNavigation", false);
        delivery.put("timestampAutoHighlight", false);
        ObjectNode writing = sections.addObject();
        writing.put("clientId", "writing");
        writing.put("skill", "WRITING");
        ArrayNode groups = writing.putArray("groups");
        for (int number = 51; number <= 54; number++) {
            groups.addObject().putArray("questions").addObject()
                    .putObject("answerSpec")
                    .put("evaluationMode",
                            PracticeTopik35CandidateImporter
                                    .WRITING_EVALUATION_MODE);
        }
        PracticeDraft draft = new PracticeDraft(
                "TOPIK 35 canonical candidate", "", "GLOBAL", null,
                "DRAFT", ownerId, root.toString());
        ReflectionTestUtils.setField(draft, "id", DRAFT_ID);
        draft.setCreationMethod(
                PracticeTopik35CandidateImporter.CREATION_METHOD);
        return draft;
    }

    private LecturerAsset exactAsset(Long ownerId) throws Exception {
        LecturerAsset asset = new LecturerAsset();
        ReflectionTestUtils.setField(asset, "id", ASSET_ID);
        asset.setOwnerLecturerId(ownerId);
        asset.setStorageProvider("LOCAL");
        asset.setStorageProfileCode("PRACTICE_AUTHORING");
        asset.setStorageKey(KEY);
        asset.setOriginalFilename(
                PracticeTopik35CheckAudioBinder.ORIGINAL_FILENAME);
        asset.setMimeType(PracticeTopik35CheckAudioBinder.MEDIA_TYPE);
        asset.setContentVerified(true);
        asset.setFileSize(PracticeTopik35CheckAudioBinder.FILE_SIZE);
        asset.setSha256(PracticeTopik35CheckAudioBinder.FULL_SHA256);
        asset.setAssetType("AUDIO");
        asset.setSourceType("MANUAL_UPLOAD");
        asset.setStatus("ACTIVE");
        asset.setVisibility("PRIVATE");
        ObjectNode tags = objectMapper.createObjectNode();
        tags.put("schemaVersion",
                PracticeTopik35CheckAudioBinder.PROVENANCE_SCHEMA);
        tags.put("bundleId", "topik35-v1");
        tags.put("purpose", "LISTENING_CHECK_AUDIO");
        tags.put("sourceIdentity",
                "repository:static/audio/practice/listening-speaker-check.wav");
        tags.put("sha256", PracticeTopik35CheckAudioBinder.FULL_SHA256);
        tags.put("sizeBytes", PracticeTopik35CheckAudioBinder.FILE_SIZE);
        tags.put("mediaType", PracticeTopik35CheckAudioBinder.MEDIA_TYPE);
        tags.put("ownerId", ownerId);
        tags.put("ownership", "DRAFT_OWNER_ONLY");
        tags.put("shared", false);
        tags.put("approval",
                "USER_APPROVED_RECOMMENDED_NARROW_ACTION_2026-08-04");
        asset.setTagsJson(tags.toString());
        return asset;
    }

    private static PracticeSeedAssetStorage.StoredSeedAsset stored(
            boolean newlyCreated) {
        return new PracticeSeedAssetStorage.StoredSeedAsset(
                KEY, PracticeTopik35CheckAudioBinder.MEDIA_TYPE,
                PracticeTopik35CheckAudioBinder.FILE_SIZE,
                PracticeTopik35CheckAudioBinder.FULL_SHA256,
                newlyCreated, "PRACTICE_AUTHORING", "LOCAL");
    }

    private static byte[] approvedBytes() throws Exception {
        return Files.readAllBytes(Path.of(
                "src/main/resources/static/audio/practice/"
                        + PracticeTopik35CheckAudioBinder.ORIGINAL_FILENAME));
    }

    private static void assertWritingUnscored(JsonNode root) {
        JsonNode groups = root.path("sections").get(2).path("groups");
        assertThat(groups).hasSize(4);
        for (JsonNode group : groups) {
            assertThat(group.path("questions").get(0)
                    .path("answerSpec").path("evaluationMode").asText())
                    .isEqualTo(PracticeTopik35CandidateImporter
                            .WRITING_EVALUATION_MODE);
        }
    }
}
