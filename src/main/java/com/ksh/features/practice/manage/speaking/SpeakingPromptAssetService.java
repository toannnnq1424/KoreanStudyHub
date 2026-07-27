package com.ksh.features.practice.manage.speaking;

import com.ksh.entities.LecturerAsset;
import com.ksh.features.practice.manage.service.LecturerAssetService;
import com.ksh.features.practice.manage.service.PracticeAssessmentExcelService;
import com.ksh.features.practice.manage.service.PracticeMaterialReferenceService;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeMaterialReferenceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Authoring-specific asset authority. Physical storage, content deduplication,
 * rollback compensation and lifecycle queuing remain owned by
 * {@link LecturerAssetService}.
 */
@Service
public class SpeakingPromptAssetService {

    static final String ORIGINAL_PLACEMENT = "SPEAKING_PROMPT_ORIGINAL";
    static final String GENERATED_PLACEMENT = "SPEAKING_PROMPT_TTS";

    private final LecturerAssetRepository assetRepository;
    private final PracticeMaterialReferenceRepository referenceRepository;
    private final LecturerAssetService lecturerAssetService;
    private final PracticeMaterialReferenceService materialReferenceService;
    private final SpeakingPromptAudioVerifier audioVerifier;
    private final SpeakingPromptAuthoringAiProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @org.springframework.beans.factory.annotation.Autowired
    public SpeakingPromptAssetService(
            LecturerAssetRepository assetRepository,
            PracticeMaterialReferenceRepository referenceRepository,
            LecturerAssetService lecturerAssetService,
            PracticeMaterialReferenceService materialReferenceService,
            SpeakingPromptAudioVerifier audioVerifier,
            SpeakingPromptAuthoringAiProperties properties,
            ApplicationEventPublisher eventPublisher) {
        this.assetRepository = assetRepository;
        this.referenceRepository = referenceRepository;
        this.lecturerAssetService = lecturerAssetService;
        this.materialReferenceService = materialReferenceService;
        this.audioVerifier = audioVerifier;
        this.properties = properties;
        this.eventPublisher = Objects.requireNonNull(
                eventPublisher, "eventPublisher");
    }

    SpeakingPromptAssetService(
            LecturerAssetRepository assetRepository,
            PracticeMaterialReferenceRepository referenceRepository,
            LecturerAssetService lecturerAssetService,
            PracticeMaterialReferenceService materialReferenceService,
            SpeakingPromptAudioVerifier audioVerifier,
            SpeakingPromptAuthoringAiProperties properties) {
        this(
                assetRepository,
                referenceRepository,
                lecturerAssetService,
                materialReferenceService,
                audioVerifier,
                properties,
                ignored -> {
                });
    }

    SpeakingPromptAssetService(
            LecturerAssetRepository assetRepository,
            PracticeMaterialReferenceRepository referenceRepository,
            LecturerAssetService lecturerAssetService,
            SpeakingPromptAudioVerifier audioVerifier,
            SpeakingPromptAuthoringAiProperties properties) {
        this(
                assetRepository,
                referenceRepository,
                lecturerAssetService,
                new PracticeMaterialReferenceService(
                        referenceRepository, assetRepository),
                audioVerifier,
                properties);
    }

    VerifiedOriginalUpload uploadOriginal(
            Long draftId,
            Long actorId,
            String questionClientId,
            org.springframework.web.multipart.MultipartFile file) {
        try {
            LecturerAsset asset =
                    lecturerAssetService.createUnboundDraftUploadAsset(
                            draftId,
                            actorId,
                            file,
                            "AUDIO",
                            properties.sttConfig().maxInputBytes());
            SpeakingPromptAiContract.VerifiedAudio verified =
                    loadVerifiedUnboundOriginal(
                            asset.getOwnerLecturerId(),
                            asset.getId());
            return new VerifiedOriginalUpload(
                    draftId,
                    questionClientId,
                    asset.getOwnerLecturerId(),
                    asset.getId(),
                    verified);
        } catch (IOException exception) {
            throw new SpeakingPromptAiContract.ProviderFailure(
                    SpeakingPromptAiContract.PublicErrorCategory.TRANSPORT,
                    true,
                    null,
                    exception);
        }
    }

    void unlinkOriginalBinding(
            Long draftId,
            Long ownerId,
            Long assetId,
            String questionClientId) {
        requireUsableOwnedAudio(ownerId, assetId);
        requireDraftBinding(draftId, assetId, questionClientId);
        materialReferenceService.unlinkDraft(
                draftId,
                assetId,
                ORIGINAL_PLACEMENT,
                questionClientId);
    }

    void queueIfUnreferenced(Long assetId) {
        lecturerAssetService.queuePrivatePromptAssetIfUnreferenced(assetId);
    }

    AssetPresentation originalPresentation(
            Long draftId,
            Long ownerId,
            Long assetId,
            String questionClientId,
            String contentUrl) {
        if (assetId == null) {
            return null;
        }
        LecturerAsset asset = requireUsableOwnedAudio(ownerId, assetId);
        requireDraftBinding(draftId, assetId, questionClientId);
        if (!"MANUAL_UPLOAD".equalsIgnoreCase(asset.getSourceType())) {
            throw new AccessDeniedException(
                    "Audio gốc không thuộc nguồn tải lên của giảng viên.");
        }
        return AssetPresentation.from(
                asset, "LECTURER_ORIGINAL", contentUrl);
    }

    AssetPresentation generatedPresentation(
            Long draftId,
            Long ownerId,
            Long assetId,
            String questionClientId,
            String contentUrl) {
        if (assetId == null) {
            return null;
        }
        LecturerAsset asset = requireUsableOwnedAudio(ownerId, assetId);
        if (!"AI_TTS".equalsIgnoreCase(asset.getSourceType())
                || !referenceRepository
                .existsByAssetIdAndDraftIdAndPlacementAndReferenceKey(
                        assetId,
                        draftId,
                        GENERATED_PLACEMENT,
                        questionClientId)) {
            throw new AccessDeniedException(
                    "Audio AI không thuộc đúng câu hỏi của bản nháp.");
        }
        return AssetPresentation.from(asset, "AI_GENERATED", contentUrl);
    }

    boolean hasExcelStaging(
            Long draftId,
            Long ownerId,
            String questionClientId) {
        return resolveExcelStagingAsset(
                draftId, ownerId, questionClientId, false) != null;
    }

    VerifiedOriginalUpload verifyExcelStaging(
            Long draftId,
            Long ownerId,
            String questionClientId) {
        LecturerAsset asset = resolveExcelStagingAsset(
                draftId, ownerId, questionClientId, true);
        return new VerifiedOriginalUpload(
                draftId,
                questionClientId,
                ownerId,
                asset.getId(),
                verifyOriginalBytes(asset, ownerId));
    }

    MediaResource loadMedia(Long ownerId, Long assetId) {
        LecturerAsset asset = requireUsableOwnedAudio(ownerId, assetId);
        try {
            return new MediaResource(
                    lecturerAssetService.loadAssetResource(assetId, ownerId),
                    asset.getOriginalFilename() == null
                            ? "audio"
                            : asset.getOriginalFilename(),
                    asset.getMimeType(),
                    asset.getFileSize() == null ? 0L : asset.getFileSize());
        } catch (IOException exception) {
            throw new SpeakingPromptAiContract.ProviderFailure(
                    SpeakingPromptAiContract.PublicErrorCategory.TRANSPORT,
                    true,
                    null,
                    exception);
        }
    }

    LecturerAsset requireBoundOriginalAsset(
            Long draftId,
            Long ownerId,
            Long assetId,
            String questionClientId,
            SpeakingPromptAiContract.VerifiedAudio verifiedAudio) {
        LecturerAsset asset = requireUsableOwnedAudio(ownerId, assetId);
        requireDraftBinding(draftId, assetId, questionClientId);
        requireVerifiedOriginalIdentity(asset, verifiedAudio);
        return asset;
    }

    LecturerAsset bindVerifiedOriginalAsset(
            Long draftId,
            Long ownerId,
            Long assetId,
            String questionClientId,
            SpeakingPromptAiContract.VerifiedAudio verifiedAudio) {
        return bindVerifiedOriginalAsset(
                draftId,
                ownerId,
                assetId,
                questionClientId,
                verifiedAudio,
                false);
    }

    LecturerAsset bindVerifiedExcelStagingAsset(
            Long draftId,
            Long ownerId,
            Long assetId,
            String questionClientId,
            SpeakingPromptAiContract.VerifiedAudio verifiedAudio) {
        return bindVerifiedOriginalAsset(
                draftId,
                ownerId,
                assetId,
                questionClientId,
                verifiedAudio,
                true);
    }

    private LecturerAsset bindVerifiedOriginalAsset(
            Long draftId,
            Long ownerId,
            Long assetId,
            String questionClientId,
            SpeakingPromptAiContract.VerifiedAudio verifiedAudio,
            boolean requireExcelStaging) {
        LecturerAsset asset = requireStagedOrActiveOwnedAudio(ownerId, assetId);
        if (requireExcelStaging) {
            List<com.ksh.entities.PracticeMaterialReference> exact =
                    referenceRepository
                            .findDraftPlacementAndReferenceKeyForUpdate(
                                    draftId,
                                    PracticeAssessmentExcelService
                                            .EXCEL_SPEAKING_STAGING,
                                    questionClientId);
            if (exact.size() != 1
                    || !Objects.equals(
                            exact.get(0).getAssetId(), assetId)) {
                throw new SpeakingPromptAuthoringConflictException(
                        "Audio Excel chờ liên kết đã thay đổi. "
                                + "Vui lòng tải lại bản nháp.");
            }
        }
        requireVerifiedOriginalIdentity(asset, verifiedAudio);
        asset.setStatus("ACTIVE");
        asset.setRetentionUntil(null);
        assetRepository.save(asset);
        materialReferenceService.linkDraft(
                draftId,
                assetId,
                ORIGINAL_PLACEMENT,
                questionClientId,
                null);
        retirePriorQuestionPlacementBindings(
                draftId,
                questionClientId,
                ORIGINAL_PLACEMENT,
                assetId);
        materialReferenceService.unlinkDraft(
                draftId,
                assetId,
                PracticeAssessmentExcelService.EXCEL_SPEAKING_STAGING,
                questionClientId);
        return asset;
    }

    SpeakingPromptAiContract.VerifiedAudio loadVerifiedOriginal(
            Long draftId,
            Long ownerId,
            Long assetId,
            String questionClientId) {
        LecturerAsset asset = requireUsableOwnedAudio(ownerId, assetId);
        requireDraftBinding(draftId, assetId, questionClientId);
        return verifyOriginalBytes(asset, ownerId);
    }

    private SpeakingPromptAiContract.VerifiedAudio loadVerifiedUnboundOriginal(
            Long ownerId,
            Long assetId) {
        LecturerAsset asset =
                requireStagedOrActiveOwnedAudioReadOnly(ownerId, assetId);
        return verifyOriginalBytes(asset, ownerId);
    }

    private SpeakingPromptAiContract.VerifiedAudio verifyOriginalBytes(
            LecturerAsset asset,
            Long ownerId) {
        try {
            byte[] bytes = lecturerAssetService.loadOwnedAssetBytes(
                    asset.getId(),
                    ownerId,
                    properties.sttConfig().maxInputBytes());
            return audioVerifier.verifySttInput(
                    bytes,
                    safeFilename(asset),
                    asset.getMimeType(),
                    asset.getSha256());
        } catch (IOException exception) {
            throw new SpeakingPromptAiContract.ProviderFailure(
                    SpeakingPromptAiContract.PublicErrorCategory.TRANSPORT,
                    true,
                    null,
                    exception);
        }
    }

    private static void requireVerifiedOriginalIdentity(
            LecturerAsset asset,
            SpeakingPromptAiContract.VerifiedAudio verifiedAudio) {
        if (!"MANUAL_UPLOAD".equalsIgnoreCase(asset.getSourceType())
                || !Objects.equals(
                        normalizedHash(asset.getSha256()),
                        normalizedHash(verifiedAudio.sha256()))
                || !Objects.equals(
                        asset.getFileSize(),
                        (long) verifiedAudio.bytes().length)
                || !mimeCompatible(
                        asset.getMimeType(),
                        verifiedAudio.mimeType())) {
            throw new IllegalArgumentException(
                    "Audio gốc không khớp tài nguyên đã xác minh.");
        }
    }

    StoredGeneratedCandidate storeGeneratedCandidate(
            Long ownerId,
            Long draftId,
            String questionClientId,
            SpeakingPromptAiContract.VerifiedAudio audio) {
        validateGeneratedAudio(audio);
        try {
            LecturerAssetService.GeneratedAudioCandidate stored =
                    lecturerAssetService.storeGeneratedDraftAudio(
                            ownerId,
                            draftId,
                            audio.bytes(),
                            generatedFilename(audio.mimeType()),
                            audio.mimeType(),
                            audio.sha256(),
                            "AI_TTS");
            return new StoredGeneratedCandidate(
                    stored,
                    ownerId,
                    draftId,
                    questionClientId,
                    audio.bytes().length,
                    audio.mimeType(),
                    audio.durationMillis());
        } catch (IOException exception) {
            throw new SpeakingPromptAiContract.ProviderFailure(
                    SpeakingPromptAiContract.PublicErrorCategory.TRANSPORT,
                    true,
                    null,
                    exception);
        }
    }

    private void validateGeneratedAudio(
            SpeakingPromptAiContract.VerifiedAudio audio) {
        SpeakingPromptAuthoringAiProperties.TtsConfig config =
                properties.ttsConfig();
        if (audio == null
                || audio.bytes().length == 0
                || audio.bytes().length > config.maxOutputBytes()
                || audio.durationMillis() <= 0L
                || audio.durationMillis()
                    > config.maxOutputDuration().toMillis()
                || !config.allowedOutputMimeTypes().contains(
                        normalized(audio.mimeType()))
                || !SpeakingPromptAiContract.exactBytesSha256(audio.bytes())
                    .equalsIgnoreCase(audio.sha256())) {
            throw new IllegalArgumentException(
                    "Generated audio is outside the verified output contract.");
        }
    }

    LecturerAsset registerGeneratedCandidate(
            StoredGeneratedCandidate candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException(
                    "Generated audio candidate is required.");
        }
        LecturerAsset registered =
                lecturerAssetService.registerGeneratedDraftAudio(
                        candidate.delegate,
                        "Audio đề bài do AI tạo",
                        GENERATED_PLACEMENT,
                        candidate.questionClientId);
        retirePriorQuestionPlacementBindings(
                candidate.draftId,
                candidate.questionClientId,
                GENERATED_PLACEMENT,
                registered.getId());
        return registered;
    }

    void linkExistingGeneratedAsset(
            Long draftId,
            Long ownerId,
            String questionClientId,
            Long assetId) {
        if (draftId == null
                || ownerId == null
                || assetId == null
                || questionClientId == null
                || questionClientId.isBlank()) {
            throw new IllegalArgumentException(
                    "Generated audio binding is incomplete.");
        }
        lecturerAssetService.linkExistingGeneratedDraftAudio(
                draftId,
                ownerId,
                assetId,
                "AI_TTS",
                GENERATED_PLACEMENT,
                questionClientId);
        retirePriorQuestionPlacementBindings(
                draftId,
                questionClientId,
                GENERATED_PLACEMENT,
                assetId);
    }

    void retireGeneratedAssetBinding(
            Long draftId,
            String questionClientId) {
        if (draftId == null
                || questionClientId == null
                || questionClientId.isBlank()) {
            throw new IllegalArgumentException(
                    "Generated audio binding identity is incomplete.");
        }
        retirePriorQuestionPlacementBindings(
                draftId,
                questionClientId,
                GENERATED_PLACEMENT,
                null);
    }

    void discardCandidate(StoredGeneratedCandidate candidate) {
        if (candidate != null) {
            lecturerAssetService.discardGeneratedDraftAudio(
                    candidate.delegate);
        }
    }

    /**
     * Retires only superseded bindings for one exact question placement.
     * The caller already holds the draft/source authority and has linked the
     * replacement under the new asset lock. Cleanup eligibility is evaluated
     * only after that surrounding source transition commits.
     */
    private void retirePriorQuestionPlacementBindings(
            Long draftId,
            String questionClientId,
            String placement,
            Long currentAssetId) {
        List<com.ksh.entities.PracticeMaterialReference> exact =
                referenceRepository.findDraftPlacementAndReferenceKeyForUpdate(
                        draftId, placement, questionClientId);
        Set<Long> retiredAssetIds = new LinkedHashSet<>();
        for (com.ksh.entities.PracticeMaterialReference reference : exact) {
            Long priorAssetId = reference.getAssetId();
            if (priorAssetId == null
                    || Objects.equals(priorAssetId, currentAssetId)
                    || !draftId.equals(reference.getDraftId())
                    || !placement.equals(reference.getPlacement())
                    || !questionClientId.equals(reference.getReferenceKey())) {
                continue;
            }
            materialReferenceService.unlinkDraft(
                    draftId,
                    priorAssetId,
                    placement,
                    questionClientId);
            retiredAssetIds.add(priorAssetId);
        }
        if (!retiredAssetIds.isEmpty()) {
            eventPublisher.publishEvent(
                    new RetiredPromptAssetCandidates(retiredAssetIds));
        }
    }

    /**
     * The source row and exact replacement reference are committed before a
     * fresh transaction takes the old asset lock and applies the centralized
     * all-reference guard. No provider or physical-storage action occurs here.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void queueRetiredPromptAssets(
            RetiredPromptAssetCandidates candidates) {
        for (Long assetId : candidates.assetIds()) {
            lecturerAssetService.queuePrivatePromptAssetIfUnreferenced(
                    assetId);
        }
    }

    private LecturerAsset requireUsableOwnedAudio(
            Long ownerId,
            Long assetId) {
        LecturerAsset asset = assetRepository
                .findByIdAndOwnerLecturerId(assetId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Không tìm thấy audio đề bài."));
        if (!asset.isContentVerified()
                || asset.getDeletedAt() != null
                || !"ACTIVE".equalsIgnoreCase(asset.getStatus())
                || (!"PRIVATE".equalsIgnoreCase(asset.getVisibility())
                    && !"PUBLISHED".equalsIgnoreCase(asset.getVisibility()))
                || !"AUDIO".equalsIgnoreCase(asset.getAssetType())
                || asset.getSha256() == null
                || !asset.getSha256().matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException(
                    "Audio đề bài không ở trạng thái đã xác minh.");
        }
        return asset;
    }

    private LecturerAsset requireStagedOrActiveOwnedAudio(
            Long ownerId,
            Long assetId) {
        LecturerAsset asset = assetRepository
                .findByIdForUpdate(assetId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Không tìm thấy audio đề bài."));
        if (!Objects.equals(ownerId, asset.getOwnerLecturerId())) {
            throw new AccessDeniedException(
                    "Audio đề bài không thuộc giảng viên.");
        }
        if (!asset.isContentVerified()
                || asset.getDeletedAt() != null
                || (!"ACTIVE".equalsIgnoreCase(asset.getStatus())
                    && !"TEMPORARY".equalsIgnoreCase(asset.getStatus()))
                || !"PRIVATE".equalsIgnoreCase(asset.getVisibility())
                || !"AUDIO".equalsIgnoreCase(asset.getAssetType())
                || !"MANUAL_UPLOAD".equalsIgnoreCase(asset.getSourceType())
                || asset.getSha256() == null
                || !asset.getSha256().matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException(
                    "Audio tải lên không ở trạng thái riêng tư đã xác minh.");
        }
        return asset;
    }

    private LecturerAsset requireStagedOrActiveOwnedAudioReadOnly(
            Long ownerId,
            Long assetId) {
        LecturerAsset asset = assetRepository
                .findByIdAndOwnerLecturerId(assetId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Không tìm thấy audio đề bài."));
        requirePrivateVerifiedManualAudio(asset);
        return asset;
    }

    private LecturerAsset resolveExcelStagingAsset(
            Long draftId,
            Long ownerId,
            String questionClientId,
            boolean required) {
        if (draftId == null
                || ownerId == null
                || questionClientId == null
                || questionClientId.isBlank()) {
            throw new IllegalArgumentException(
                    "Định danh audio Excel chưa đầy đủ.");
        }
        List<com.ksh.entities.PracticeMaterialReference> exact =
                referenceRepository
                        .findByDraftIdAndPlacementAndReferenceKey(
                                draftId,
                                PracticeAssessmentExcelService
                                        .EXCEL_SPEAKING_STAGING,
                                questionClientId);
        if (exact.isEmpty()) {
            if (!required) {
                return null;
            }
            throw new EntityNotFoundException(
                    "Không có audio Excel chờ liên kết cho câu hỏi này.");
        }
        if (exact.size() != 1) {
            throw new SpeakingPromptAuthoringConflictException(
                    "Audio Excel chờ liên kết không còn duy nhất. "
                            + "Vui lòng tải lại bản nháp.");
        }
        LecturerAsset asset = assetRepository
                .findByIdAndOwnerLecturerId(
                        exact.get(0).getAssetId(), ownerId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Audio Excel không thuộc chủ sở hữu bản nháp."));
        requirePrivateVerifiedManualAudio(asset);
        return asset;
    }

    private static void requirePrivateVerifiedManualAudio(
            LecturerAsset asset) {
        if (!asset.isContentVerified()
                || asset.getDeletedAt() != null
                || (!"ACTIVE".equalsIgnoreCase(asset.getStatus())
                    && !"TEMPORARY".equalsIgnoreCase(asset.getStatus()))
                || !"PRIVATE".equalsIgnoreCase(asset.getVisibility())
                || !"AUDIO".equalsIgnoreCase(asset.getAssetType())
                || !"MANUAL_UPLOAD".equalsIgnoreCase(asset.getSourceType())
                || asset.getSha256() == null
                || !asset.getSha256().matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException(
                    "Audio tải lên không ở trạng thái riêng tư đã xác minh.");
        }
    }

    private void requireDraftBinding(
            Long draftId,
            Long assetId,
            String questionClientId) {
        if (draftId == null
                || assetId == null
                || questionClientId == null
                || questionClientId.isBlank()
                || !referenceRepository
                    .existsByAssetIdAndDraftIdAndPlacementAndReferenceKey(
                            assetId,
                            draftId,
                            ORIGINAL_PLACEMENT,
                            questionClientId)) {
            throw new AccessDeniedException(
                    "Audio đề bài không thuộc đúng câu hỏi của bản nháp.");
        }
    }

    private static String safeFilename(LecturerAsset asset) {
        String filename = asset.getOriginalFilename();
        if (filename != null && filename.contains(".")) {
            return filename;
        }
        return "speaking-prompt" + extensionForMime(asset.getMimeType());
    }

    private static String generatedFilename(String mimeType) {
        return "speaking-prompt-ai" + extensionForMime(mimeType);
    }

    private static String extensionForMime(String mimeType) {
        return switch (normalized(mimeType)) {
            case "audio/mpeg" -> ".mp3";
            case "audio/wav", "audio/x-wav" -> ".wav";
            case "audio/mp4", "audio/x-m4a" -> ".m4a";
            case "audio/ogg" -> ".ogg";
            case "audio/webm" -> ".webm";
            default -> throw new IllegalArgumentException(
                    "Audio MIME type is unsupported.");
        };
    }

    private static boolean mimeCompatible(String left, String right) {
        String first = normalized(left);
        String second = normalized(right);
        return first.equals(second)
                || (MimeAliases.WAV.contains(first)
                    && MimeAliases.WAV.contains(second))
                || (MimeAliases.MP4.contains(first)
                    && MimeAliases.MP4.contains(second));
    }

    private static String normalized(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizedHash(String value) {
        return value == null
                ? null
                : value.toLowerCase(Locale.ROOT);
    }

    private static final class MimeAliases {
        private static final java.util.Set<String> WAV =
                java.util.Set.of("audio/wav", "audio/x-wav");
        private static final java.util.Set<String> MP4 =
                java.util.Set.of("audio/mp4", "audio/x-m4a");

        private MimeAliases() {
        }
    }

    static final class RetiredPromptAssetCandidates {
        private final Set<Long> assetIds;

        private RetiredPromptAssetCandidates(Set<Long> assetIds) {
            this.assetIds = Set.copyOf(assetIds);
        }

        Set<Long> assetIds() {
            return assetIds;
        }

        @Override
        public String toString() {
            return "RetiredPromptAssetCandidates{candidateCount="
                    + assetIds.size() + '}';
        }
    }

    static final class StoredGeneratedCandidate {
        private final LecturerAssetService.GeneratedAudioCandidate delegate;
        private final Long ownerId;
        private final Long draftId;
        private final String questionClientId;
        private final int sizeBytes;
        private final String mimeType;
        private final long durationMillis;

        private StoredGeneratedCandidate(
                LecturerAssetService.GeneratedAudioCandidate delegate,
                Long ownerId,
                Long draftId,
                String questionClientId,
                int sizeBytes,
                String mimeType,
                long durationMillis) {
            this.delegate = delegate;
            this.ownerId = ownerId;
            this.draftId = draftId;
            this.questionClientId = questionClientId;
            this.sizeBytes = sizeBytes;
            this.mimeType = mimeType;
            this.durationMillis = durationMillis;
        }

        Long ownerId() {
            return ownerId;
        }

        Long draftId() {
            return draftId;
        }

        String questionClientId() {
            return questionClientId;
        }

        @Override
        public String toString() {
            return "StoredGeneratedCandidate{"
                    + "sizeBytes=" + sizeBytes
                    + ", mimeType='" + mimeType + '\''
                    + ", durationMillis=" + durationMillis
                    + '}';
        }
    }

    record VerifiedOriginalUpload(
            Long draftId,
            String questionClientId,
            Long ownerId,
            Long assetId,
            SpeakingPromptAiContract.VerifiedAudio verifiedAudio) {
        VerifiedOriginalUpload {
            Objects.requireNonNull(draftId, "draftId");
            Objects.requireNonNull(questionClientId, "questionClientId");
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(assetId, "assetId");
            Objects.requireNonNull(verifiedAudio, "verifiedAudio");
        }

        @Override
        public String toString() {
            return "VerifiedOriginalUpload{verified=true}";
        }
    }

    public record AssetPresentation(
            String contentUrl,
            String filename,
            String mimeType,
            long sizeBytes,
            String provenance) {
        static AssetPresentation from(LecturerAsset asset, String provenance) {
            return from(
                    asset,
                    provenance,
                    "/practice/materials/" + asset.getId() + "/content");
        }

        static AssetPresentation from(
                LecturerAsset asset,
                String provenance,
                String contentUrl) {
            return new AssetPresentation(
                    contentUrl,
                    asset.getOriginalFilename() == null
                            ? "audio"
                            : asset.getOriginalFilename(),
                    asset.getMimeType(),
                    asset.getFileSize() == null ? 0L : asset.getFileSize(),
                    provenance);
        }
    }

    record MediaResource(
            org.springframework.core.io.Resource resource,
            String filename,
            String mimeType,
            long sizeBytes) {
    }
}
