package com.ksh.features.practice.manage.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.manage.material.PracticeMaterialPlacements;
import com.ksh.features.practice.manage.service.AssetStorageService;
import com.ksh.features.practice.manage.service.PracticeMaterialReferenceService;
import com.ksh.features.practice.manage.service.PracticeSeedAssetStorage;
import com.ksh.features.practice.manage.service.PracticeUploadContentVerifier;
import com.ksh.features.practice.manage.validator.PracticeDraftValidator;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Explicit local/disposable binding for the approved TOPIK 35 speaker-check
 * fixture. This is not a startup seed, controller or global material lookup.
 */
@Service
public class PracticeTopik35CheckAudioBinder {

    public static final String FULL_SHA256 =
            "7896983a2a4c869a30c4cf2e9cf396282ae66a281bdeb271b24d7af5cefda913";
    public static final long FILE_SIZE = 158_804L;
    public static final String ORIGINAL_FILENAME =
            "listening-speaker-check.wav";
    public static final String MEDIA_TYPE = "audio/wav";
    public static final String SECTION_CLIENT_ID =
            "topik35-v1-section-listening";
    public static final String PROVENANCE_SCHEMA =
            "practice-topik35-check-audio-provenance-v1";
    public static final String BINDING_SCHEMA =
            "practice-topik35-check-audio-binding-v1";

    private static final Pattern DISPOSABLE_CATALOG = Pattern.compile(
            "^ksh_test_topik35_candidate_[a-z0-9_]+$");
    private static final Pattern MATERIAL_REFERENCE = Pattern.compile(
            "^/practice/materials/([1-9][0-9]*)/content$");
    private static final Resource APPROVED_RESOURCE = new ClassPathResource(
            "static/audio/practice/" + ORIGINAL_FILENAME);

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    private final PracticeDraftRepository drafts;
    private final LecturerAssetRepository assets;
    private final PracticeSeedAssetStorage seedStorage;
    private final AssetStorageService assetStorage;
    private final PracticeUploadContentVerifier contentVerifier;
    private final PracticeMaterialReferenceService references;
    private final PracticeDraftValidator draftValidator;

    public PracticeTopik35CheckAudioBinder(
            ObjectMapper objectMapper,
            JdbcTemplate jdbc,
            PracticeDraftRepository drafts,
            LecturerAssetRepository assets,
            PracticeSeedAssetStorage seedStorage,
            AssetStorageService assetStorage,
            PracticeUploadContentVerifier contentVerifier,
            PracticeMaterialReferenceService references,
            PracticeDraftValidator draftValidator) {
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
        this.drafts = drafts;
        this.assets = assets;
        this.seedStorage = seedStorage;
        this.assetStorage = assetStorage;
        this.contentVerifier = contentVerifier;
        this.references = references;
        this.draftValidator = draftValidator;
    }

    @Transactional
    public BindResult bind(Long draftId, Long ownerId) throws IOException {
        requireDisposableCatalog();
        if (draftId == null || ownerId == null || ownerId < 1) {
            throw rejected("TOPIK35_CHECK_AUDIO_IDENTITY_REQUIRED");
        }
        PracticeDraft draft = drafts.findByIdForUpdate(draftId)
                .orElseThrow(() -> rejected("TOPIK35_CHECK_AUDIO_DRAFT_NOT_FOUND"));
        ObjectNode root = requireCandidate(draft, ownerId);
        ObjectNode listening = listeningDelivery(root);
        String currentReference = listening.path("checkAudioReference")
                .asText("").trim();
        if (!currentReference.isBlank()) {
            return requireExistingBinding(
                    draft, root, listening, ownerId, currentReference);
        }

        requireLocalAuthoringStorage();
        byte[] approvedBytes = approvedBytes();
        PracticeUploadContentVerifier.VerifiedContent verified =
                contentVerifier.verify(
                        approvedBytes, ORIGINAL_FILENAME, "AUDIO");
        if (!MEDIA_TYPE.equals(verified.mimeType())) {
            throw rejected("TOPIK35_CHECK_AUDIO_MEDIA_TYPE_INVALID");
        }
        PracticeSeedAssetStorage.StoredSeedAsset stored = seedStorage.store(
                PracticeTopik35CandidateImporter.BUNDLE_ID,
                PracticeSeedAssetStorage.AssetKind.REVIEW_ARTIFACT,
                new java.io.ByteArrayInputStream(approvedBytes),
                ORIGINAL_FILENAME,
                MEDIA_TYPE);
        requireStoredIdentity(stored);
        registerRollbackCleanup(stored);

        LecturerAsset asset = reusableOwnedAsset(ownerId, stored);
        boolean created = asset == null;
        if (created) {
            asset = new LecturerAsset();
            asset.setOwnerLecturerId(ownerId);
            asset.setStorageProvider(stored.storageProvider());
            asset.setStorageProfileCode(stored.storageProfileCode());
            asset.setStorageKey(stored.logicalKey());
            asset.setOriginalFilename(ORIGINAL_FILENAME);
            asset.setMimeType(MEDIA_TYPE);
            asset.setContentVerified(true);
            asset.setFileSize(FILE_SIZE);
            asset.setSha256(FULL_SHA256);
            asset.setAssetType("AUDIO");
            asset.setTitle("TOPIK 35 - Kiểm tra loa");
            asset.setSourceType("MANUAL_UPLOAD");
            asset.setLecturerNote(
                    "Owner-scoped TOPIK 35 speaker check; not global/shared.");
            asset.setTagsJson(provenanceJson(ownerId));
            asset.setStatus("ACTIVE");
            asset.setVisibility("PRIVATE");
            asset.setRetentionUntil(null);
            asset.setCreatedAt(LocalDateTime.now());
            asset.setUpdatedAt(LocalDateTime.now());
            asset = assets.saveAndFlush(asset);
        }
        requireAssetIdentity(asset, ownerId, stored.logicalKey());

        references.linkDraft(
                draft.getId(), asset.getId(),
                PracticeMaterialPlacements.LISTENING_CHECK_AUDIO,
                SECTION_CLIENT_ID, provenanceJson(ownerId));
        String reference = "/practice/materials/" + asset.getId() + "/content";
        applyBinding(root, listening, asset, reference);
        requirePublishableDraft(root);
        draft.setDraftJson(root.toString());
        drafts.saveAndFlush(draft);
        return new BindResult(
                created ? BindStatus.CREATED : BindStatus.REUSED,
                draft.getId(), ownerId, asset.getId(), reference,
                stored.logicalKey(), FULL_SHA256, FILE_SIZE);
    }

    /**
     * Removes only this owner-scoped draft binding. Physical deletion remains
     * owned by LecturerAssetService after its all-reference retention check.
     */
    @Transactional
    public WithdrawResult withdraw(Long draftId, Long ownerId) {
        requireDisposableCatalog();
        if (draftId == null || ownerId == null || ownerId < 1) {
            throw rejected("TOPIK35_CHECK_AUDIO_IDENTITY_REQUIRED");
        }
        PracticeDraft draft = drafts.findByIdForUpdate(draftId)
                .orElseThrow(() -> rejected("TOPIK35_CHECK_AUDIO_DRAFT_NOT_FOUND"));
        ObjectNode root = requireCandidate(draft, ownerId);
        ObjectNode listening = listeningDelivery(root);
        String currentReference = listening.path("checkAudioReference")
                .asText("").trim();
        Matcher matcher = MATERIAL_REFERENCE.matcher(currentReference);
        if (!matcher.matches()) {
            throw rejected("TOPIK35_CHECK_AUDIO_REFERENCE_INVALID");
        }
        Long assetId = Long.valueOf(matcher.group(1));
        LecturerAsset asset = assets.findByIdForUpdate(assetId)
                .orElseThrow(() -> rejected(
                        "TOPIK35_CHECK_AUDIO_ASSET_NOT_FOUND"));
        requireAssetIdentity(asset, ownerId, logicalKey());
        if (!references.hasDraftReference(
                draftId, assetId,
                PracticeMaterialPlacements.LISTENING_CHECK_AUDIO,
                SECTION_CLIENT_ID)) {
            throw rejected("TOPIK35_CHECK_AUDIO_REFERENCE_NOT_BOUND");
        }
        references.unlinkDraft(
                draftId, assetId,
                PracticeMaterialPlacements.LISTENING_CHECK_AUDIO,
                SECTION_CLIENT_ID);
        listening.putNull("checkAudioReference");
        listening.put("candidateCheckAudioState",
                "WITHDRAWN_MATERIAL_REQUIRED");
        ((ObjectNode) root.path("seedImport"))
                .remove("listeningCheckAudioBinding");
        draft.setDraftJson(root.toString());
        drafts.saveAndFlush(draft);
        return new WithdrawResult(
                draftId, ownerId, assetId,
                "WITHDRAWN_MATERIAL_REQUIRED");
    }

    private BindResult requireExistingBinding(
            PracticeDraft draft,
            ObjectNode root,
            ObjectNode listening,
            Long ownerId,
            String reference) throws IOException {
        Matcher matcher = MATERIAL_REFERENCE.matcher(reference);
        if (!matcher.matches()) {
            throw rejected("TOPIK35_CHECK_AUDIO_REFERENCE_INVALID");
        }
        Long assetId = Long.valueOf(matcher.group(1));
        LecturerAsset asset = assets.findByIdForUpdate(assetId)
                .orElseThrow(() -> rejected(
                        "TOPIK35_CHECK_AUDIO_ASSET_NOT_FOUND"));
        String expectedKey = logicalKey();
        requireAssetIdentity(asset, ownerId, expectedKey);
        if (!references.hasDraftReference(
                draft.getId(), assetId,
                PracticeMaterialPlacements.LISTENING_CHECK_AUDIO,
                SECTION_CLIENT_ID)) {
            throw rejected("TOPIK35_CHECK_AUDIO_REFERENCE_NOT_BOUND");
        }
        requireStoredBytes(asset);
        JsonNode binding = root.path("seedImport")
                .path("listeningCheckAudioBinding");
        if (!BINDING_SCHEMA.equals(binding.path("schemaVersion").asText())
                || binding.path("assetId").asLong() != assetId
                || binding.path("ownerId").asLong() != ownerId
                || !FULL_SHA256.equals(binding.path("sha256").asText())
                || !expectedKey.equals(binding.path("logicalKey").asText())
                || !"BOUND_OWNER_SCOPED".equals(
                        listening.path("candidateCheckAudioState").asText())) {
            throw rejected("TOPIK35_CHECK_AUDIO_BINDING_PROVENANCE_INVALID");
        }
        requirePublishableDraft(root);
        return new BindResult(
                BindStatus.REUSED, draft.getId(), ownerId, assetId,
                reference, expectedKey, FULL_SHA256, FILE_SIZE);
    }

    private LecturerAsset reusableOwnedAsset(
            Long ownerId,
            PracticeSeedAssetStorage.StoredSeedAsset stored) {
        List<LecturerAsset> candidates = assets
                .findByOwnerLecturerIdAndSha256AndStatusAndDeletedAtIsNull(
                        ownerId, FULL_SHA256, "ACTIVE")
                .stream()
                .filter(asset -> exactAssetIdentity(
                        asset, ownerId, stored.logicalKey()))
                .toList();
        if (candidates.size() > 1) {
            throw rejected("TOPIK35_CHECK_AUDIO_ASSET_AMBIGUOUS");
        }
        if (candidates.isEmpty()) return null;
        return assets.findByIdForUpdate(candidates.get(0).getId())
                .orElseThrow(() -> rejected(
                        "TOPIK35_CHECK_AUDIO_ASSET_NOT_FOUND"));
    }

    private ObjectNode requireCandidate(PracticeDraft draft, Long ownerId) {
        if (!ownerId.equals(draft.getOwnerId())) {
            throw rejected("TOPIK35_CHECK_AUDIO_OWNER_MISMATCH");
        }
        if (!"DRAFT".equals(draft.getStatus())
                || draft.getPublishedSetId() != null
                || !PracticeTopik35CandidateImporter.CREATION_METHOD.equals(
                        draft.getCreationMethod())) {
            throw rejected("TOPIK35_CHECK_AUDIO_CANDIDATE_STATE_INVALID");
        }
        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(draft.getDraftJson());
        } catch (Exception exception) {
            throw rejected("TOPIK35_CHECK_AUDIO_CANDIDATE_JSON_INVALID");
        }
        if (!(parsed instanceof ObjectNode root)) {
            throw rejected("TOPIK35_CHECK_AUDIO_CANDIDATE_JSON_INVALID");
        }
        JsonNode seed = root.path("seedImport");
        if (!PracticeTopik35CandidateImporter.BUNDLE_ID.equals(
                        seed.path("bundleId").asText())
                || !PracticeTopik35CandidateImporter.IMPORTER_VERSION.equals(
                        seed.path("importerVersion").asText())
                || seed.path("publicationAllowed").asBoolean(true)
                || seed.path("listeningTimingRequiredForCandidate")
                        .asBoolean(true)
                || seed.path("listeningTimingRequiredForPublication")
                        .asBoolean(true)
                || seed.path("listeningTimingAllowedForExamAssistance")
                        .asBoolean(true)) {
            throw rejected("TOPIK35_CHECK_AUDIO_CANDIDATE_IDENTITY_INVALID");
        }
        requireUnscoredWriting(root);
        return root;
    }

    private static ObjectNode listeningDelivery(ObjectNode root) {
        ObjectNode matched = null;
        for (JsonNode section : root.path("sections")) {
            if ("LISTENING".equals(section.path("skill").asText())
                    && SECTION_CLIENT_ID.equals(
                            section.path("clientId").asText())) {
                if (matched != null
                        || !(section.path("sectionDelivery")
                        .path("listeningDelivery") instanceof ObjectNode value)) {
                    throw rejected("TOPIK35_CHECK_AUDIO_LISTENING_SECTION_INVALID");
                }
                matched = value;
            }
        }
        if (matched == null
                || !matched.path("singleOrderedAudioProgram").asBoolean()
                || !matched.path("startOnce").asBoolean()
                || !matched.path("continuousPlayback").asBoolean()
                || matched.path("seekAllowed").asBoolean(true)
                || matched.path("replayAllowed").asBoolean(true)
                || matched.path("timestampAutoNavigation").asBoolean(true)
                || matched.path("timestampAutoHighlight").asBoolean(true)) {
            throw rejected("TOPIK35_CHECK_AUDIO_LISTENING_POLICY_INVALID");
        }
        return matched;
    }

    private static void requireUnscoredWriting(ObjectNode root) {
        int writingQuestions = 0;
        for (JsonNode section : root.path("sections")) {
            if (!"WRITING".equals(section.path("skill").asText())) continue;
            for (JsonNode group : section.path("groups")) {
                for (JsonNode question : group.path("questions")) {
                    writingQuestions++;
                    if (!PracticeTopik35CandidateImporter.WRITING_EVALUATION_MODE
                            .equals(question.path("answerSpec")
                                    .path("evaluationMode").asText())) {
                        throw rejected(
                                "TOPIK35_CHECK_AUDIO_WRITING_MODE_INVALID");
                    }
                }
            }
        }
        if (writingQuestions != 4) {
            throw rejected("TOPIK35_CHECK_AUDIO_WRITING_MODE_INVALID");
        }
    }

    private void applyBinding(
            ObjectNode root,
            ObjectNode listening,
            LecturerAsset asset,
            String reference) {
        listening.put("checkAudioReference", reference);
        listening.put("candidateCheckAudioState", "BOUND_OWNER_SCOPED");
        ObjectNode binding = ((ObjectNode) root.path("seedImport"))
                .putObject("listeningCheckAudioBinding");
        binding.put("schemaVersion", BINDING_SCHEMA);
        binding.put("assetId", asset.getId());
        binding.put("ownerId", asset.getOwnerLecturerId());
        binding.put("sha256", FULL_SHA256);
        binding.put("sizeBytes", FILE_SIZE);
        binding.put("mediaType", MEDIA_TYPE);
        binding.put("logicalKey", asset.getStorageKey());
        binding.put("purpose", PracticeMaterialPlacements.LISTENING_CHECK_AUDIO);
        binding.put("provenanceSchema", PROVENANCE_SCHEMA);
        binding.put("ownership", "DRAFT_OWNER_ONLY");
        binding.put("shared", false);
    }

    private void requirePublishableDraft(ObjectNode root) {
        PracticeDraftValidator.ValidationResult validation =
                draftValidator.validate(root.toString());
        List<String> blocking = validation.messages().stream()
                .filter(message -> "BLOCKING".equals(message.type()))
                .map(PracticeDraftValidator.ValidationMsg::code)
                .distinct()
                .sorted()
                .toList();
        if (!blocking.isEmpty()) {
            throw rejected("TOPIK35_CHECK_AUDIO_DRAFT_BLOCKED:" +
                    String.join(",", blocking));
        }
    }

    private void requireAssetIdentity(
            LecturerAsset asset,
            Long ownerId,
            String expectedKey) {
        if (!exactAssetIdentity(asset, ownerId, expectedKey)) {
            throw rejected("TOPIK35_CHECK_AUDIO_ASSET_IDENTITY_INVALID");
        }
    }

    private boolean exactAssetIdentity(
            LecturerAsset asset,
            Long ownerId,
            String expectedKey) {
        return asset != null
                && ownerId.equals(asset.getOwnerLecturerId())
                && asset.getDeletedAt() == null
                && "LOCAL".equals(asset.getStorageProvider())
                && "PRACTICE_AUTHORING".equals(asset.getStorageProfileCode())
                && expectedKey.equals(asset.getStorageKey())
                && ORIGINAL_FILENAME.equals(asset.getOriginalFilename())
                && MEDIA_TYPE.equals(asset.getMimeType())
                && asset.isContentVerified()
                && Objects.equals(FILE_SIZE, asset.getFileSize())
                && FULL_SHA256.equals(asset.getSha256())
                && "AUDIO".equals(asset.getAssetType())
                && "MANUAL_UPLOAD".equals(asset.getSourceType())
                && "ACTIVE".equals(asset.getStatus())
                && "PRIVATE".equals(asset.getVisibility())
                && asset.getRetentionUntil() == null
                && provenanceJson(ownerId).equals(asset.getTagsJson());
    }

    private void requireStoredBytes(LecturerAsset asset) throws IOException {
        if (!assetStorage.exists(
                asset.getStorageProfileCode(), asset.getStorageKey())) {
            throw rejected("TOPIK35_CHECK_AUDIO_OBJECT_MISSING");
        }
        byte[] bytes;
        try (InputStream input = assetStorage.load(
                asset.getStorageProfileCode(), asset.getStorageKey())
                .getInputStream()) {
            bytes = input.readAllBytes();
        }
        requireApprovedBytes(bytes);
    }

    private byte[] approvedBytes() throws IOException {
        byte[] bytes;
        try (InputStream input = APPROVED_RESOURCE.getInputStream()) {
            bytes = input.readAllBytes();
        }
        requireApprovedBytes(bytes);
        return bytes;
    }

    private static void requireApprovedBytes(byte[] bytes) {
        if (bytes == null || bytes.length != FILE_SIZE
                || !FULL_SHA256.equals(sha256(bytes))) {
            throw rejected("TOPIK35_CHECK_AUDIO_APPROVED_BYTES_MISMATCH");
        }
    }

    private void requireStoredIdentity(
            PracticeSeedAssetStorage.StoredSeedAsset stored) {
        if (stored == null
                || !logicalKey().equals(stored.logicalKey())
                || !MEDIA_TYPE.equals(stored.mediaType())
                || FILE_SIZE != stored.sizeBytes()
                || !FULL_SHA256.equals(stored.sha256())
                || !"PRACTICE_AUTHORING".equals(
                        stored.storageProfileCode())
                || !"LOCAL".equals(stored.storageProvider())) {
            throw rejected("TOPIK35_CHECK_AUDIO_STORAGE_IDENTITY_INVALID");
        }
    }

    private void requireLocalAuthoringStorage() {
        if (!"PRACTICE_AUTHORING".equals(assetStorage.profileCode())
                || !"LOCAL".equals(assetStorage.providerCode())) {
            throw rejected("TOPIK35_CHECK_AUDIO_LOCAL_STORAGE_REQUIRED");
        }
    }

    private void requireDisposableCatalog() {
        String catalog = jdbc.queryForObject("SELECT DATABASE()", String.class);
        if (catalog == null || !DISPOSABLE_CATALOG.matcher(catalog).matches()) {
            throw rejected("TOPIK35_CHECK_AUDIO_DISPOSABLE_DB_REQUIRED");
        }
    }

    private String provenanceJson(Long ownerId) {
        ObjectNode provenance = objectMapper.createObjectNode();
        provenance.put("schemaVersion", PROVENANCE_SCHEMA);
        provenance.put("bundleId", PracticeTopik35CandidateImporter.BUNDLE_ID);
        provenance.put("purpose", PracticeMaterialPlacements.LISTENING_CHECK_AUDIO);
        provenance.put("sourceIdentity",
                "repository:static/audio/practice/" + ORIGINAL_FILENAME);
        provenance.put("sha256", FULL_SHA256);
        provenance.put("sizeBytes", FILE_SIZE);
        provenance.put("mediaType", MEDIA_TYPE);
        provenance.put("ownerId", ownerId);
        provenance.put("ownership", "DRAFT_OWNER_ONLY");
        provenance.put("shared", false);
        provenance.put("approval",
                "USER_APPROVED_RECOMMENDED_NARROW_ACTION_2026-08-04");
        return provenance.toString();
    }

    private static String logicalKey() {
        return "practice-seed/topik35-v1/review/artifact/"
                + FULL_SHA256 + ".wav";
    }

    private void registerRollbackCleanup(
            PracticeSeedAssetStorage.StoredSeedAsset stored) {
        if (!stored.newlyCreated()
                || !TransactionSynchronizationManager
                        .isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_COMMITTED) return;
                        try {
                            assetStorage.delete(
                                    stored.storageProfileCode(),
                                    stored.logicalKey());
                        } catch (Exception ignored) {
                            // A failed rollback cleanup is handled by the
                            // existing orphan-reconciliation boundary.
                        }
                    }
                });
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static IllegalStateException rejected(String code) {
        return new IllegalStateException(code);
    }

    public enum BindStatus { CREATED, REUSED }

    public record BindResult(
            BindStatus status,
            Long draftId,
            Long ownerId,
            Long assetId,
            String materialReference,
            String logicalKey,
            String sha256,
            long sizeBytes) {
    }

    public record WithdrawResult(
            Long draftId,
            Long ownerId,
            Long assetId,
            String candidateCheckAudioState) {
    }
}
