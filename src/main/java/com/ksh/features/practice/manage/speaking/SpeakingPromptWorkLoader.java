package com.ksh.features.practice.manage.speaking;

import com.ksh.features.practice.manage.speaking.SpeakingPromptTaskTransactions.ClaimedTask;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class SpeakingPromptWorkLoader {

    private final SpeakingPromptAiTaskRepository taskRepository;
    private final SpeakingPromptSourceRepository sourceRepository;
    private final SpeakingPromptAiArtifactRepository artifactRepository;
    private final SpeakingPromptDraftAuthority draftAuthority;
    private final SpeakingPromptAssetService assetService;
    private final SpeakingPromptFingerprintService fingerprintService;
    private final SpeakingPromptAuthoringAiProperties properties;

    public SpeakingPromptWorkLoader(
            SpeakingPromptAiTaskRepository taskRepository,
            SpeakingPromptSourceRepository sourceRepository,
            SpeakingPromptAiArtifactRepository artifactRepository,
            SpeakingPromptDraftAuthority draftAuthority,
            SpeakingPromptAssetService assetService,
            SpeakingPromptFingerprintService fingerprintService,
            SpeakingPromptAuthoringAiProperties properties) {
        this.taskRepository = taskRepository;
        this.sourceRepository = sourceRepository;
        this.artifactRepository = artifactRepository;
        this.draftAuthority = draftAuthority;
        this.assetService = assetService;
        this.fingerprintService = fingerprintService;
        this.properties = properties;
    }

    /**
     * Deliberately has no transaction annotation: private media loading,
     * ffprobe verification and prompt snapshot preparation happen after the
     * short claim transaction has committed.
     */
    public LoadedWork load(ClaimedTask claim) {
        properties.requireOperational(claim.operation());
        SpeakingPromptAiTask task = taskRepository.findById(claim.taskId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Speaking prompt task no longer exists."));
        SpeakingPromptSource source = sourceRepository
                .findById(claim.executionSourceId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Speaking prompt source no longer exists."));
        SpeakingPromptAiArtifact artifact = artifactRepository
                .findById(claim.artifactId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Speaking prompt artifact no longer exists."));
        if (!task.ownsLiveLease(claim.claimToken(), LocalDateTime.now())
                || !source.currentForArtifact(artifact)
                || !Objects.equals(task.getArtifactId(), artifact.getId())
                || !Objects.equals(task.getOwnerLecturerId(), claim.ownerId())
                || !Objects.equals(claim.ownerId(), source.getOwnerLecturerId())
                || !Objects.equals(
                        claim.operationFingerprint(),
                        artifact.getOperationFingerprint())
                || !Objects.equals(
                        claim.executionSourceRevision(),
                        source.getSourceRevision())) {
            throw stale();
        }
        return claim.operation() == SpeakingPromptAiContract.Operation.STT
                ? loadStt(claim, source, artifact)
                : loadTts(claim, source, artifact);
    }

    private LoadedWork loadStt(
            ClaimedTask claim,
            SpeakingPromptSource source,
            SpeakingPromptAiArtifact artifact) {
        SpeakingPromptAuthoringAiProperties.SttConfig config =
                properties.sttConfig();
        requireProviderSnapshot(artifact, config);
        draftAuthority.loadCurrent(
                claim.draftId(),
                claim.questionClientId(),
                claim.ownerId());
        SpeakingPromptAiContract.VerifiedAudio audio =
                assetService.loadVerifiedOriginal(
                        claim.draftId(),
                        claim.ownerId(),
                        source.getOriginalAudioAssetId(),
                        claim.questionClientId());
        if (!Objects.equals(audio.sha256(), artifact.getInputSha256())) {
            throw stale();
        }
        String fingerprint = fingerprintService.sttFingerprint(
                claim.ownerId(), source.getOriginalAudioAssetId(),
                audio.sha256(), config);
        if (!fingerprint.equals(artifact.getOperationFingerprint())) {
            throw stale();
        }
        return LoadedWork.stt(
                new SpeakingPromptAiContract.SttRequest(
                        audio,
                        artifact.getLanguageTag(),
                        artifact.getContractVersion()),
                audio.sha256());
    }

    private LoadedWork loadTts(
            ClaimedTask claim,
            SpeakingPromptSource source,
            SpeakingPromptAiArtifact artifact) {
        SpeakingPromptDraftAuthority.DraftPrompt prompt =
                draftAuthority.loadCurrent(
                        claim.draftId(),
                        claim.questionClientId(),
                        claim.ownerId());
        String exactHash = fingerprintService.exactTextSha256(
                prompt.promptText());
        String nfcHash = SpeakingPromptAiContract.unicodeNfcUtf8Sha256(
                prompt.promptText());
        if (!Objects.equals(exactHash, source.getManualTextSha256())
                || !Objects.equals(nfcHash, artifact.getInputSha256())) {
            throw stale();
        }
        SpeakingPromptAuthoringAiProperties.TtsConfig config =
                artifactTtsConfig(artifact);
        String fingerprint = fingerprintService.ttsFingerprint(
                claim.ownerId(), prompt.promptText(), config);
        if (!fingerprint.equals(artifact.getOperationFingerprint())) {
            throw stale();
        }
        return LoadedWork.tts(
                new SpeakingPromptAiContract.TtsRequest(
                        prompt.promptText(),
                        nfcHash,
                        artifact.getLanguageTag(),
                        artifact.getVoiceCode(),
                        artifact.getSpeed(),
                        artifact.getOutputFormat(),
                        artifact.getContractVersion()),
                exactHash);
    }

    private void requireProviderSnapshot(
            SpeakingPromptAiArtifact artifact,
            SpeakingPromptAuthoringAiProperties.SttConfig config) {
        if (!Objects.equals(artifact.getProviderCode(), config.provider())
                || !Objects.equals(artifact.getModelCode(), config.model())
                || !Objects.equals(artifact.getLanguageTag(), config.language())
                || !Objects.equals(artifact.getPurposeCode(), config.purposeCode())
                || !Objects.equals(
                        artifact.getRetentionCode(), config.retentionCode())
                || !SpeakingPromptAiContract.CONTRACT_VERSION.equals(
                        artifact.getContractVersion())) {
            throw configuration();
        }
    }

    private SpeakingPromptAuthoringAiProperties.TtsConfig artifactTtsConfig(
            SpeakingPromptAiArtifact artifact) {
        SpeakingPromptAuthoringAiProperties.TtsConfig base =
                properties.ttsConfig();
        if (!Objects.equals(artifact.getProviderCode(), base.provider())
                || !Objects.equals(artifact.getModelCode(), base.model())
                || !Objects.equals(artifact.getLanguageTag(), base.language())
                || !Objects.equals(artifact.getPurposeCode(), base.purposeCode())
                || !Objects.equals(
                        artifact.getRetentionCode(), base.retentionCode())
                || !SpeakingPromptAiContract.CONTRACT_VERSION.equals(
                        artifact.getContractVersion())) {
            throw configuration();
        }
        return new SpeakingPromptAuthoringAiProperties.TtsConfig(
                base.enabled(),
                base.provider(),
                base.baseUrl(),
                base.apiKey(),
                base.model(),
                base.language(),
                artifact.getVoiceCode(),
                artifact.getSpeed(),
                artifact.getOutputFormat(),
                base.maxInputCharacters(),
                base.purposeCode(),
                base.retentionCode(),
                base.maxOutputBytes(),
                base.maxOutputDuration(),
                base.connectTimeout(),
                base.readTimeout(),
                base.allowedOutputFormats(),
                base.allowedOutputMimeTypes());
    }

    private static SpeakingPromptAiContract.ProviderFailure stale() {
        return new SpeakingPromptAiContract.ProviderFailure(
                SpeakingPromptAiContract.PublicErrorCategory.STALE_COMPLETION,
                false,
                null,
                null);
    }

    private static SpeakingPromptAiContract.ProviderFailure configuration() {
        return new SpeakingPromptAiContract.ProviderFailure(
                SpeakingPromptAiContract.PublicErrorCategory.CONFIGURATION,
                false,
                null,
                null);
    }

    static final class LoadedWork {
        private final SpeakingPromptAiContract.SttRequest sttRequest;
        private final SpeakingPromptAiContract.TtsRequest ttsRequest;
        private final String exactInputSha256;

        private LoadedWork(
                SpeakingPromptAiContract.SttRequest sttRequest,
                SpeakingPromptAiContract.TtsRequest ttsRequest,
                String exactInputSha256) {
            this.sttRequest = sttRequest;
            this.ttsRequest = ttsRequest;
            this.exactInputSha256 = exactInputSha256;
        }

        static LoadedWork stt(
                SpeakingPromptAiContract.SttRequest request,
                String exactInputSha256) {
            return new LoadedWork(request, null, exactInputSha256);
        }

        static LoadedWork tts(
                SpeakingPromptAiContract.TtsRequest request,
                String exactInputSha256) {
            return new LoadedWork(null, request, exactInputSha256);
        }

        SpeakingPromptAiContract.SttRequest sttRequest() {
            return sttRequest;
        }

        SpeakingPromptAiContract.TtsRequest ttsRequest() {
            return ttsRequest;
        }

        String exactInputSha256() {
            return exactInputSha256;
        }

        @Override
        public String toString() {
            return "LoadedWork{operation="
                    + (sttRequest == null ? "TTS" : "STT") + '}';
        }
    }
}
