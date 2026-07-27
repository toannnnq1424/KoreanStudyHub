package com.ksh.features.practice.manage.speaking;

import com.ksh.features.practice.governance.PracticeAction;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Read-only lecturer projection for the Speaking prompt editor. It deliberately
 * contains no provider port and exposes neither fingerprints nor storage or
 * provider request identity.
 */
@Service
public class SpeakingPromptAuthoringStateService {

    private final SpeakingPromptDraftAuthority draftAuthority;
    private final SpeakingPromptSourceRepository sourceRepository;
    private final SpeakingPromptAiArtifactRepository artifactRepository;
    private final SpeakingPromptAiTaskRepository taskRepository;
    private final SpeakingPromptTranscriptRevisionRepository revisionRepository;
    private final SpeakingPromptAssetService assetService;
    private final SpeakingPromptFingerprintService fingerprintService;
    private final SpeakingPromptAuthoringAiProperties properties;

    public SpeakingPromptAuthoringStateService(
            SpeakingPromptDraftAuthority draftAuthority,
            SpeakingPromptSourceRepository sourceRepository,
            SpeakingPromptAiArtifactRepository artifactRepository,
            SpeakingPromptAiTaskRepository taskRepository,
            SpeakingPromptTranscriptRevisionRepository revisionRepository,
            SpeakingPromptAssetService assetService,
            SpeakingPromptFingerprintService fingerprintService,
            SpeakingPromptAuthoringAiProperties properties) {
        this.draftAuthority = draftAuthority;
        this.sourceRepository = sourceRepository;
        this.artifactRepository = artifactRepository;
        this.taskRepository = taskRepository;
        this.revisionRepository = revisionRepository;
        this.assetService = assetService;
        this.fingerprintService = fingerprintService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public EditorState load(Long draftId, String questionClientId, Long actorId) {
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                draftAuthority.authorize(
                        draftId,
                        questionClientId,
                        actorId,
                        PracticeAction.EDIT);
        SpeakingPromptDraftAuthority.DraftAuthoringOptions draftOptions =
                draftAuthority.authoringOptions(authorized);
        SpeakingPromptAuthoringAiProperties.TtsConfig approved =
                properties.ttsConfig();
        SelectedTtsOptions selected = safeOptions(draftOptions, approved);
        boolean excelStagingAudioAvailable =
                assetService.hasExcelStaging(
                        draftId,
                        authorized.ownerId(),
                        questionClientId);
        SpeakingPromptSource source = sourceRepository
                .findByDraftIdAndQuestionClientId(draftId, questionClientId)
                .orElse(null);
        if (source == null) {
            return EditorState.empty(
                    safeDraftVersion(authorized),
                    SpeakingPromptSource.INPUT_MANUAL_TEXT.equals(
                            draftOptions.inputType())
                            ? SpeakingPromptSource.INPUT_MANUAL_TEXT
                            : SpeakingPromptSource.INPUT_AUDIO_UPLOAD,
                    draftOptions.ttsEnabled(),
                    authorized.question().path("prompt").asText(""),
                    excelStagingAudioAvailable,
                    selected,
                    approvedOptions(approved),
                    properties.sttConfig().maxInputBytes(),
                    properties.sttConfig().maxInputDuration().toSeconds());
        }
        if (!Objects.equals(source.getOwnerLecturerId(), authorized.ownerId())) {
            throw new AccessDeniedException(
                    "Nguồn đề Nói không thuộc chủ sở hữu bản nháp.");
        }
        if (!Objects.equals(source.getInputType(), draftOptions.inputType())) {
            throw new SpeakingPromptAuthoringConflictException(
                    "Chế độ đề Nói không khớp nguồn hiện tại. Vui lòng tải lại.");
        }

        SpeakingPromptAiArtifact sttArtifact = currentArtifact(
                source,
                source.getCurrentSttArtifactId(),
                SpeakingPromptAiContract.Operation.STT);
        SpeakingPromptAiArtifact ttsArtifact = currentArtifact(
                source,
                source.getCurrentTtsArtifactId(),
                SpeakingPromptAiContract.Operation.TTS);
        SpeakingPromptTranscriptRevision transcript =
                currentTranscript(source, sttArtifact);
        OperationView stt = operationView(source, sttArtifact);
        boolean manualIdentityCurrent =
                !SpeakingPromptSource.INPUT_MANUAL_TEXT.equals(source.getInputType())
                || Objects.equals(
                        source.getManualTextSha256(),
                        fingerprintService.exactTextSha256(
                                authorized.question().path("prompt").asText("")));
        boolean ttsIdentityCurrent = ttsArtifact == null
                || ttsArtifactMatchesSelection(ttsArtifact, selected, approved);
        boolean generatedIdentityCurrent =
                manualIdentityCurrent && ttsIdentityCurrent;
        OperationView tts = generatedIdentityCurrent
                ? operationView(source, ttsArtifact)
                : OperationView.idle();
        SpeakingPromptAssetService.AssetPresentation original =
                assetService.originalPresentation(
                        draftId,
                        authorized.ownerId(),
                        source.getOriginalAudioAssetId(),
                        questionClientId,
                        mediaUrl(draftId, questionClientId, "original"));
        SpeakingPromptAssetService.AssetPresentation generated =
                assetService.generatedPresentation(
                        draftId,
                        authorized.ownerId(),
                        source.getGeneratedAudioAssetId(),
                        questionClientId,
                        mediaUrl(draftId, questionClientId, "generated"));
        boolean generatedCurrent = generatedIdentityCurrent
                && generated != null
                && SpeakingPromptSource.INPUT_MANUAL_TEXT.equals(
                        source.getInputType())
                && source.isTtsEnabled()
                && SpeakingPromptSource.STATUS_READY.equals(
                        source.getAudioSyncStatus())
                && Objects.equals(
                        source.getGeneratedAudioAssetId(),
                        source.getActiveAudioAssetId());
        String effectiveAudioStatus =
                source.isTtsEnabled()
                        && generated != null
                        && !generatedIdentityCurrent
                        ? SpeakingPromptSource.STATUS_STALE
                        : source.getAudioSyncStatus();
        return new EditorState(
                safeDraftVersion(authorized),
                source.getSourceRevision(),
                source.getInputType(),
                SpeakingPromptSource.INPUT_MANUAL_TEXT.equals(
                        source.getInputType())
                        ? source.isTtsEnabled()
                        : draftOptions.ttsEnabled(),
                authorized.question().path("prompt").asText(""),
                source.getTranscriptStatus(),
                effectiveAudioStatus,
                original,
                excelStagingAudioAvailable,
                generated,
                generatedCurrent,
                transcript == null ? null : transcript.getContextText(),
                transcript == null ? null : transcript.getRevisionNumber(),
                transcript != null && transcript.getConfirmedAt() != null,
                sttArtifact == null ? null : sttArtifact.getConfidence(),
                stt,
                tts,
                selected,
                approvedOptions(approved),
                properties.sttConfig().maxInputBytes(),
                properties.sttConfig().maxInputDuration().toSeconds());
    }

    @Transactional(readOnly = true)
    public SpeakingPromptAssetService.MediaResource loadMedia(
            Long draftId,
            String questionClientId,
            Long actorId,
            String origin) {
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                draftAuthority.authorize(
                        draftId,
                        questionClientId,
                        actorId,
                        PracticeAction.EDIT);
        SpeakingPromptSource source = sourceRepository
                .findByDraftIdAndQuestionClientId(draftId, questionClientId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Nguồn đề Nói chưa tồn tại."));
        if (!Objects.equals(source.getOwnerLecturerId(), authorized.ownerId())) {
            throw new AccessDeniedException(
                    "Nguồn đề Nói không thuộc chủ sở hữu bản nháp.");
        }
        Long assetId;
        if ("original".equals(origin)) {
            assetService.originalPresentation(
                    draftId,
                    authorized.ownerId(),
                    source.getOriginalAudioAssetId(),
                    questionClientId,
                    "");
            assetId = source.getOriginalAudioAssetId();
        } else if ("generated".equals(origin)) {
            assetService.generatedPresentation(
                    draftId,
                    authorized.ownerId(),
                    source.getGeneratedAudioAssetId(),
                    questionClientId,
                    "");
            assetId = source.getGeneratedAudioAssetId();
        } else {
            throw new IllegalArgumentException(
                    "Nguồn audio xem trước không hợp lệ.");
        }
        if (assetId == null) {
            throw new jakarta.persistence.EntityNotFoundException(
                    "Audio xem trước không tồn tại.");
        }
        return assetService.loadMedia(authorized.ownerId(), assetId);
    }

    private static String mediaUrl(
            Long draftId,
            String questionClientId,
            String origin) {
        String encodedClient = org.springframework.web.util.UriUtils
                .encodePathSegment(
                        questionClientId,
                        java.nio.charset.StandardCharsets.UTF_8);
        return "/practice/manage/drafts/" + draftId
                + "/questions/" + encodedClient
                + "/speaking-prompt/media/" + origin;
    }

    private SpeakingPromptAiArtifact currentArtifact(
            SpeakingPromptSource source,
            Long artifactId,
            SpeakingPromptAiContract.Operation operation) {
        if (artifactId == null) {
            return null;
        }
        SpeakingPromptAiArtifact artifact = artifactRepository.findById(artifactId)
                .orElseThrow(() -> new IllegalStateException(
                        "Speaking prompt artifact is missing."));
        if (!operation.code().equals(artifact.getOperation())
                || !source.currentForArtifact(artifact)) {
            throw new SpeakingPromptAuthoringConflictException(
                    "Speaking prompt artifact is not current.");
        }
        return artifact;
    }

    private SpeakingPromptTranscriptRevision currentTranscript(
            SpeakingPromptSource source,
            SpeakingPromptAiArtifact artifact) {
        if (source.getCurrentTranscriptRevisionId() == null) {
            return null;
        }
        if (artifact == null) {
            throw new SpeakingPromptAuthoringConflictException(
                    "Bản chép lời không còn thuộc nguồn hiện tại.");
        }
        SpeakingPromptTranscriptRevision revision = revisionRepository
                .findById(source.getCurrentTranscriptRevisionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Current transcript revision is missing."));
        if (!Objects.equals(revision.getOwnerLecturerId(), source.getOwnerLecturerId())
                || !Objects.equals(revision.getArtifactId(), artifact.getId())) {
            throw new AccessDeniedException(
                    "Bản chép lời không thuộc nguồn đề Nói hiện tại.");
        }
        return revision;
    }

    private OperationView operationView(
            SpeakingPromptSource source,
            SpeakingPromptAiArtifact artifact) {
        if (artifact == null) {
            return OperationView.idle();
        }
        List<SpeakingPromptAiTask> latest = taskRepository
                .findLatestByFingerprint(
                        source.getOwnerLecturerId(),
                        artifact.getOperation(),
                        artifact.getOperationFingerprint(),
                        PageRequest.of(0, 1));
        if (latest.isEmpty()) {
            return new OperationView(
                    artifact.getArtifactStatus(),
                    artifact.getPublicErrorCategory(),
                    false,
                    null);
        }
        SpeakingPromptAiTask task = latest.get(0);
        return new OperationView(
                task.getTaskStatus(),
                task.getPublicErrorCategory(),
                task.isRetryable(),
                task.getNextAttemptAt());
    }

    private static SelectedTtsOptions safeOptions(
            SpeakingPromptDraftAuthority.DraftAuthoringOptions draft,
            SpeakingPromptAuthoringAiProperties.TtsConfig approved) {
        String voice = approved.voice().equals(draft.voiceCode())
                ? draft.voiceCode()
                : approved.voice();
        BigDecimal speed = draft.speed() == null
                ? approved.speed()
                : draft.speed();
        BigDecimal selectedSpeed = speed;
        if (approvedSpeeds(approved.speed()).stream()
                .noneMatch(candidate -> candidate.compareTo(selectedSpeed) == 0)) {
            speed = approved.speed();
        }
        String format = approved.allowedOutputFormats().contains(
                draft.outputFormat())
                ? draft.outputFormat()
                : approved.outputFormat();
        return new SelectedTtsOptions(voice, speed, format);
    }

    private static ApprovedTtsOptions approvedOptions(
            SpeakingPromptAuthoringAiProperties.TtsConfig approved) {
        return new ApprovedTtsOptions(
                List.of(new VoiceOption(approved.voice(), "Giọng đọc KSH")),
                approvedSpeeds(approved.speed()),
                approved.allowedOutputFormats().stream().sorted().toList(),
                SpeakingPromptAiContract.CONTRACT_VERSION);
    }

    private static boolean ttsArtifactMatchesSelection(
            SpeakingPromptAiArtifact artifact,
            SelectedTtsOptions selected,
            SpeakingPromptAuthoringAiProperties.TtsConfig approved) {
        return artifact != null
                && selected != null
                && SpeakingPromptAiContract.Operation.TTS.code().equals(
                        artifact.getOperation())
                && Objects.equals(
                        approved.provider(), artifact.getProviderCode())
                && Objects.equals(approved.model(), artifact.getModelCode())
                && Objects.equals(
                        approved.language(), artifact.getLanguageTag())
                && Objects.equals(
                        selected.voiceCode(), artifact.getVoiceCode())
                && artifact.getSpeed() != null
                && selected.speed().compareTo(artifact.getSpeed()) == 0
                && Objects.equals(
                        selected.outputFormat(), artifact.getOutputFormat())
                && Objects.equals(
                        SpeakingPromptAiContract.CONTRACT_VERSION,
                        artifact.getContractVersion())
                && Objects.equals(
                        approved.purposeCode(), artifact.getPurposeCode())
                && Objects.equals(
                        approved.retentionCode(), artifact.getRetentionCode());
    }

    private static int safeDraftVersion(
            SpeakingPromptDraftAuthority.AuthorizedDraft authorized) {
        return authorized.draft().getVersion() == null
                ? 0
                : authorized.draft().getVersion();
    }

    private static List<BigDecimal> approvedSpeeds(BigDecimal configured) {
        java.util.Set<BigDecimal> values =
                new java.util.TreeSet<>(BigDecimal::compareTo);
        values.add(new BigDecimal("0.75"));
        values.add(BigDecimal.ONE);
        values.add(new BigDecimal("1.25"));
        values.add(configured);
        return List.copyOf(values);
    }

    public record EditorState(
            int draftVersion,
            long sourceRevision,
            String inputType,
            boolean ttsEnabled,
            String manualText,
            String transcriptStatus,
            String audioStatus,
            SpeakingPromptAssetService.AssetPresentation originalAudio,
            boolean excelStagingAudioAvailable,
            SpeakingPromptAssetService.AssetPresentation generatedAudio,
            boolean generatedAudioCurrent,
            String lecturerContext,
            Integer transcriptRevision,
            boolean transcriptConfirmed,
            BigDecimal transcriptConfidence,
            OperationView sttOperation,
            OperationView ttsOperation,
            SelectedTtsOptions selectedTts,
            ApprovedTtsOptions approvedTts,
            long maximumUploadBytes,
            long maximumUploadSeconds) {

        @Override
        public String toString() {
            return "EditorState{sourceRevision=" + sourceRevision
                    + ", draftVersion=" + draftVersion
                    + ", inputType='" + inputType + '\''
                    + ", ttsEnabled=" + ttsEnabled
                    + ", manualTextLength="
                    + (manualText == null ? 0 : manualText.length())
                    + ", transcriptStatus='" + transcriptStatus + '\''
                    + ", audioStatus='" + audioStatus + '\''
                    + ", lecturerContextLength="
                    + (lecturerContext == null ? 0 : lecturerContext.length())
                    + '}';
        }

        static EditorState empty(
                int draftVersion,
                String inputType,
                boolean ttsEnabled,
                String manualText,
                boolean excelStagingAudioAvailable,
                SelectedTtsOptions selected,
                ApprovedTtsOptions approved,
                long maximumUploadBytes,
                long maximumUploadSeconds) {
            return new EditorState(
                    draftVersion,
                    0L,
                    inputType,
                    ttsEnabled,
                    manualText,
                    SpeakingPromptSource.STATUS_IDLE,
                    SpeakingPromptSource.STATUS_IDLE,
                    null,
                    excelStagingAudioAvailable,
                    null,
                    false,
                    null,
                    null,
                    false,
                    null,
                    OperationView.idle(),
                    OperationView.idle(),
                    selected,
                    approved,
                    maximumUploadBytes,
                    maximumUploadSeconds);
        }
    }

    public record OperationView(
            String taskStatus,
            String publicErrorCategory,
            boolean retryable,
            java.time.LocalDateTime nextAttemptAt) {
        static OperationView idle() {
            return new OperationView(
                    SpeakingPromptSource.STATUS_IDLE, null, false, null);
        }
    }

    public record SelectedTtsOptions(
            String voiceCode,
            BigDecimal speed,
            String outputFormat) {
    }

    public record ApprovedTtsOptions(
            List<VoiceOption> voices,
            List<BigDecimal> speeds,
            List<String> outputFormats,
            String contractVersion) {
    }

    public record VoiceOption(String code, String label) {
    }
}
