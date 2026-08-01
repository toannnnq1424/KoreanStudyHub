package com.ksh.features.practice.manage.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.ksh.entities.LecturerAsset;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.manage.material.PracticeMaterialPlacements;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeMaterialReferenceRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The single publication owner for mutable Speaking prompt authoring state.
 * It is intentionally provider/task free: publication validates and snapshots
 * an already verified outcome; it never creates one.
 */
@Service
public class SpeakingPromptPublicationService {
    private static final BigDecimal LOW_CONFIDENCE = new BigDecimal("0.50");

    private final SpeakingPromptSourceRepository sourceRepository;
    private final SpeakingPromptAiArtifactRepository artifactRepository;
    private final SpeakingPromptTranscriptRevisionRepository revisionRepository;
    private final LecturerAssetRepository assetRepository;
    private final PracticeMaterialReferenceRepository referenceRepository;
    private final SpeakingPromptFingerprintService fingerprintService;
    private final SpeakingPromptAuthoringAiProperties properties;
    private final AssessmentContractCodec contractCodec;
    private final SpeakingPromptVersionContextRepository contextRepository;

    public SpeakingPromptPublicationService(
            SpeakingPromptSourceRepository sourceRepository,
            SpeakingPromptAiArtifactRepository artifactRepository,
            SpeakingPromptTranscriptRevisionRepository revisionRepository,
            LecturerAssetRepository assetRepository,
            PracticeMaterialReferenceRepository referenceRepository,
            SpeakingPromptFingerprintService fingerprintService,
            SpeakingPromptAuthoringAiProperties properties,
            AssessmentContractCodec contractCodec,
            SpeakingPromptVersionContextRepository contextRepository) {
        this.sourceRepository = sourceRepository;
        this.artifactRepository = artifactRepository;
        this.revisionRepository = revisionRepository;
        this.assetRepository = assetRepository;
        this.referenceRepository = referenceRepository;
        this.fingerprintService = fingerprintService;
        this.properties = properties;
        this.contractCodec = contractCodec;
        this.contextRepository = contextRepository;
    }

    public PublicationPlan prepare(
            Long draftId,
            Long ownerId,
            JsonNode root) {
        Map<String, JsonNode> questions = speakingQuestions(root);
        if (questions.isEmpty()) {
            return PublicationPlan.empty();
        }
        List<SpeakingPromptSource> sources =
                sourceRepository.findByDraftIdForUpdate(draftId);
        Map<String, SpeakingPromptSource> sourcesByClient = new LinkedHashMap<>();
        for (SpeakingPromptSource source : sources) {
            if (!questions.containsKey(source.getQuestionClientId())) {
                /*
                 * 13C3-04 draft/question lifecycle reconciliation owns exact
                 * source-local teardown and orphan cleanup. Publication must
                 * neither duplicate that cleanup nor let a locked source whose
                 * clientId is absent block an otherwise coherent snapshot.
                 */
                continue;
            }
            if (!Objects.equals(ownerId, source.getOwnerLecturerId())) {
                throw invalid(source.getQuestionClientId(),
                        "nguồn đề không thuộc chủ sở hữu bản nháp");
            }
            if (sourcesByClient.put(source.getQuestionClientId(), source) != null) {
                throw invalid(source.getQuestionClientId(),
                        "có nhiều nguồn đề hiện hành cho cùng một câu");
            }
        }
        if (!sourcesByClient.keySet().containsAll(questions.keySet())) {
            throw new IllegalStateException(
                    "Không thể xuất bản Speaking: mỗi câu phải có đúng một nguồn đề hiện hành. "
                            + "Vui lòng tải lại Editor và lưu từng câu.");
        }

        Set<Long> artifactIds = new LinkedHashSet<>();
        for (SpeakingPromptSource source : sourcesByClient.values()) {
            if (source.getCurrentSttArtifactId() != null) {
                artifactIds.add(source.getCurrentSttArtifactId());
            }
            if (source.getCurrentTtsArtifactId() != null) {
                artifactIds.add(source.getCurrentTtsArtifactId());
            }
        }
        Map<Long, SpeakingPromptAiArtifact> artifacts = new LinkedHashMap<>();
        artifactIds.stream().sorted().forEach(id -> artifacts.put(
                id,
                artifactRepository.findByIdForUpdate(id)
                        .orElseThrow(() -> new IllegalStateException(
                                "Không thể xuất bản Speaking: artifact hiện hành không tồn tại."))));

        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : questions.entrySet()) {
            String clientId = entry.getKey();
            SpeakingPromptSource source = sourcesByClient.get(clientId);
            QuestionContent draftContent =
                    readTypedContent(entry.getValue(), clientId);
            Candidate candidate = SpeakingPromptSource.INPUT_AUDIO_UPLOAD.equals(
                    source.getInputType())
                    ? uploaded(draftId, ownerId, clientId, entry.getValue(),
                            draftContent, source, artifacts)
                    : SpeakingPromptSource.INPUT_MANUAL_TEXT.equals(
                            source.getInputType())
                            ? manual(draftId, ownerId, clientId, entry.getValue(),
                                    draftContent, source, artifacts)
                            : throwInvalidInputType(clientId);
            candidates.put(clientId, candidate);
        }
        return new PublicationPlan(Map.copyOf(candidates));
    }

    public void persistContexts(
            PublicationPlan plan,
            Map<String, Long> questionVersionIds,
            Long actorId) {
        if (plan == null || plan.candidates().isEmpty()) {
            if (questionVersionIds != null && !questionVersionIds.isEmpty()) {
                throw new IllegalStateException(
                        "Không thể liên kết ngữ cảnh Speaking ngoài kế hoạch xuất bản.");
            }
            return;
        }
        if (questionVersionIds == null
                || !plan.candidates().keySet().equals(questionVersionIds.keySet())) {
            throw new IllegalStateException(
                    "Không thể xuất bản Speaking: ánh xạ câu hỏi sang phiên bản bất biến không đầy đủ.");
        }
        List<SpeakingPromptVersionContext> contexts = new ArrayList<>();
        for (Map.Entry<String, Candidate> entry : plan.candidates().entrySet()) {
            Candidate candidate = entry.getValue();
            candidate.verifySourceStillLockedAndCurrent();
            Long questionVersionId = questionVersionIds.get(entry.getKey());
            if (questionVersionId == null || contextRepository.existsById(questionVersionId)) {
                throw new IllegalStateException(
                        "Không thể xuất bản Speaking: phiên bản câu hỏi đã có ngữ cảnh hoặc bị thiếu.");
            }
            contexts.add(SpeakingPromptVersionContext.create(
                    questionVersionId, candidate.context(), actorId));
        }
        contextRepository.saveAll(contexts);
        contextRepository.flush();
    }

    private Candidate uploaded(
            Long draftId,
            Long ownerId,
            String clientId,
            JsonNode question,
            QuestionContent draftContent,
            SpeakingPromptSource source,
            Map<Long, SpeakingPromptAiArtifact> artifacts) {
        QuestionContent.SpeakingDelivery delivery = draftContent.speakingDelivery();
        requireCombination(clientId, delivery,
                QuestionContent.SpeakingPromptInputType.AUDIO_UPLOAD,
                QuestionContent.SpeakingDeliveryMode.AUDIO_ONLY,
                QuestionContent.SpeakingAudioOrigin.TEACHER_UPLOAD);
        if (source.isTtsEnabled() || source.getCurrentTtsArtifactId() != null) {
            throw invalid(clientId, "chế độ audio tải lên không được có TTS hiện hành");
        }
        if (!SpeakingPromptSource.STATUS_READY.equals(source.getTranscriptStatus())) {
            throw invalid(clientId,
                    "bản chép lời STT chưa Sẵn sàng; hãy xử lý hoặc xác nhận trước khi xuất bản");
        }
        Long assetId = source.getOriginalAudioAssetId();
        if (assetId == null || !Objects.equals(assetId, source.getActiveAudioAssetId())) {
            throw invalid(clientId, "audio gốc đã xác minh không còn là audio đang hoạt động");
        }
        LecturerAsset asset = requireAsset(
                draftId, ownerId, clientId, assetId,
                PracticeMaterialPlacements.SPEAKING_PROMPT_ORIGINAL,
                "MANUAL_UPLOAD");
        SpeakingPromptAiArtifact artifact = artifacts.get(source.getCurrentSttArtifactId());
        if (artifact == null
                || !source.currentForArtifact(artifact)
                || !artifact.isReady()
                || artifact.getReadyAt() == null
                || !SpeakingPromptAiContract.Operation.STT.code().equals(
                        artifact.getOperation())
                || !Objects.equals(assetId, artifact.getInputAudioAssetId())
                || !hashEquals(asset.getSha256(), artifact.getInputSha256())) {
            throw invalid(clientId, "artifact STT không khớp audio gốc hiện hành");
        }
        String expectedFingerprint = fingerprintService.sttFingerprint(
                ownerId, assetId, asset.getSha256(), properties.sttConfig());
        requireArtifactIdentity(
                clientId, artifact, expectedFingerprint,
                properties.sttConfig().provider(),
                properties.sttConfig().model(),
                properties.sttConfig().language(),
                SpeakingPromptAiContract.CONTRACT_VERSION,
                properties.sttConfig().purposeCode(),
                properties.sttConfig().retentionCode());
        Long transcriptRevisionId = source.getCurrentTranscriptRevisionId();
        if (transcriptRevisionId == null) {
            throw invalid(clientId,
                    "thiếu bản chép lời hiện hành đã được xác nhận");
        }
        SpeakingPromptTranscriptRevision revision = revisionRepository
                .findById(transcriptRevisionId)
                .orElseThrow(() -> invalid(clientId,
                        "thiếu bản chép lời hiện hành đã được xác nhận"));
        if (artifact.getConfidence() != null
                && artifact.getConfidence().compareTo(LOW_CONFIDENCE) < 0
                && source.getLecturerTranscriptConfirmedAt() == null) {
            throw invalid(clientId,
                    "bản chép lời có độ tin cậy thấp chưa được giảng viên xác nhận");
        }
        if (!Objects.equals(revision.getArtifactId(), artifact.getId())
                || !Objects.equals(revision.getOwnerLecturerId(), ownerId)
                || revision.getConfirmedAt() == null
                || !Objects.equals(
                        fingerprintService.exactTextSha256(revision.getContextText()),
                        revision.getContextSha256())) {
            throw invalid(clientId, "bản chép lời xác nhận không khớp artifact STT");
        }
        requireDraftAudioReference(
                draftId, clientId, "original",
                delivery.promptAudioReference(), assetId);

        QuestionContent learnerContent = learnerContent(
                draftContent, QuestionContent.SpeakingPromptInputType.AUDIO_UPLOAD,
                QuestionContent.SpeakingDeliveryMode.AUDIO_ONLY,
                QuestionContent.SpeakingAudioOrigin.TEACHER_UPLOAD,
                materialUrl(assetId));
        SpeakingPromptVersionContext.ImmutableData context =
                new SpeakingPromptVersionContext.ImmutableData(
                        ownerId, "audio_upload", "audio_only", "teacher_upload",
                        "stt_transcript", revision.getContextText(), "", "",
                        assetId, assetId, artifact.getId(), null,
                        artifact.getProviderCode(), artifact.getModelCode(),
                        artifact.getContractVersion(), artifact.getPurposeCode(),
                        artifact.getRetentionCode(),
                        null, null, null, null, null).withFingerprint();
        return new Candidate(
                draftId, clientId, source, source.getSourceRevision(),
                source.getCurrentSttArtifactId(), null, assetId,
                PracticeMaterialPlacements.SPEAKING_PROMPT_ORIGINAL,
                learnerContent, context);
    }

    private Candidate manual(
            Long draftId,
            Long ownerId,
            String clientId,
            JsonNode question,
            QuestionContent draftContent,
            SpeakingPromptSource source,
            Map<Long, SpeakingPromptAiArtifact> artifacts) {
        String prompt = question.path("prompt").asText("");
        if (prompt.isBlank()
                || prompt.length() > SpeakingPromptAiContract.MAX_PROMPT_TEXT_CHARS
                || !containsKorean(prompt)
                || !Objects.equals(
                        source.getManualTextSha256(),
                        fingerprintService.exactTextSha256(prompt))) {
            throw invalid(clientId, "nội dung văn bản và mã băm nguồn không đồng bộ");
        }
        QuestionContent.SpeakingDelivery delivery = draftContent.speakingDelivery();
        if (!source.isTtsEnabled()) {
            requireCombination(clientId, delivery,
                    QuestionContent.SpeakingPromptInputType.MANUAL_TEXT,
                    QuestionContent.SpeakingDeliveryMode.TEXT_ONLY,
                    QuestionContent.SpeakingAudioOrigin.NONE);
            if (source.getCurrentTtsArtifactId() != null
                    || source.getActiveAudioAssetId() != null
                    || delivery.promptPlayLimit() != null
                    || delivery.promptAudioReference() != null) {
                throw invalid(clientId,
                        "câu chỉ dùng văn bản không được có audio, artifact hoặc giới hạn phát");
            }
            QuestionContent learnerContent = learnerContent(
                    draftContent, QuestionContent.SpeakingPromptInputType.MANUAL_TEXT,
                    QuestionContent.SpeakingDeliveryMode.TEXT_ONLY,
                    QuestionContent.SpeakingAudioOrigin.NONE, null);
            SpeakingPromptVersionContext.ImmutableData context =
                    new SpeakingPromptVersionContext.ImmutableData(
                            ownerId, "manual_text", "text_only", "none",
                            "manual_text", prompt, "", "",
                            null, null, null, null,
                            null, null, null, null, null,
                            null, null, null, null, null).withFingerprint();
            return new Candidate(
                    draftId, clientId, source, source.getSourceRevision(),
                    null, null, null, null, learnerContent, context);
        }

        requireCombination(clientId, delivery,
                QuestionContent.SpeakingPromptInputType.MANUAL_TEXT,
                QuestionContent.SpeakingDeliveryMode.TEXT_AND_AUDIO,
                QuestionContent.SpeakingAudioOrigin.AI_TTS);
        if (!SpeakingPromptSource.STATUS_READY.equals(source.getAudioSyncStatus())) {
            throw invalid(clientId,
                    "audio AI chưa Đồng bộ; trạng thái chờ, xử lý, cũ hoặc lỗi không thể xuất bản");
        }
        SpeakingPromptAiArtifact artifact = artifacts.get(source.getCurrentTtsArtifactId());
        Long assetId = source.getActiveAudioAssetId();
        if (artifact == null
                || assetId == null
                || !source.currentForArtifact(artifact)
                || !SpeakingPromptSource.STATUS_READY.equals(artifact.getArtifactStatus())
                || artifact.getReadyAt() == null
                || !Objects.equals(assetId, source.getGeneratedAudioAssetId())
                || !Objects.equals(assetId, artifact.getGeneratedAudioAssetId())) {
            throw invalid(clientId, "artifact/audio TTS hiện hành không đồng bộ");
        }
        LecturerAsset asset = requireAsset(
                draftId, ownerId, clientId, assetId,
                PracticeMaterialPlacements.SPEAKING_PROMPT_TTS, "AI_TTS");
        SpeakingPromptAuthoringAiProperties.TtsConfig config =
                selectedTtsConfig(question.path("speakingPromptAuthoring"));
        String expectedFingerprint =
                fingerprintService.ttsFingerprint(ownerId, prompt, config);
        if (!Objects.equals(
                artifact.getInputSha256(),
                SpeakingPromptAiContract.unicodeNfcUtf8Sha256(prompt))
                || !Objects.equals(artifact.getVoiceCode(), config.voice())
                || artifact.getSpeed() == null
                || artifact.getSpeed().compareTo(config.speed()) != 0
                || !Objects.equals(artifact.getOutputFormat(), config.outputFormat())) {
            throw invalid(clientId, "audio AI không khớp văn bản hoặc cấu hình hiện tại");
        }
        requireArtifactIdentity(
                clientId, artifact, expectedFingerprint,
                config.provider(), config.model(), config.language(),
                SpeakingPromptAiContract.CONTRACT_VERSION,
                config.purposeCode(), config.retentionCode());
        requireDraftAudioReference(
                draftId, clientId, "generated",
                delivery.promptAudioReference(), assetId);
        QuestionContent learnerContent = learnerContent(
                draftContent, QuestionContent.SpeakingPromptInputType.MANUAL_TEXT,
                QuestionContent.SpeakingDeliveryMode.TEXT_AND_AUDIO,
                QuestionContent.SpeakingAudioOrigin.AI_TTS,
                materialUrl(assetId));
        SpeakingPromptVersionContext.ImmutableData context =
                new SpeakingPromptVersionContext.ImmutableData(
                        ownerId, "manual_text", "text_and_audio", "ai_tts",
                        "manual_text", prompt, "", "",
                        null, assetId, null,
                        artifact.getId(),
                        null, null, null, null, null,
                        artifact.getProviderCode(), artifact.getModelCode(),
                        artifact.getContractVersion(), artifact.getPurposeCode(),
                        artifact.getRetentionCode()).withFingerprint();
        return new Candidate(
                draftId, clientId, source, source.getSourceRevision(),
                null, artifact.getId(), assetId,
                PracticeMaterialPlacements.SPEAKING_PROMPT_TTS,
                learnerContent, context);
    }

    private LecturerAsset requireAsset(
            Long draftId,
            Long ownerId,
            String clientId,
            Long assetId,
            String placement,
            String sourceType) {
        LecturerAsset asset = assetRepository
                .findByIdAndOwnerLecturerId(assetId, ownerId)
                .orElseThrow(() -> invalid(clientId,
                        "không tìm thấy audio thuộc chủ sở hữu"));
        if (!asset.isContentVerified()
                || asset.getDeletedAt() != null
                || !"ACTIVE".equalsIgnoreCase(asset.getStatus())
                || !"AUDIO".equalsIgnoreCase(asset.getAssetType())
                || !sourceType.equalsIgnoreCase(asset.getSourceType())
                || asset.getSha256() == null
                || !asset.getSha256().matches("[0-9a-fA-F]{64}")
                || !referenceRepository
                    .existsByAssetIdAndDraftIdAndPlacementAndReferenceKey(
                            assetId, draftId, placement, clientId)) {
            throw invalid(clientId,
                    "audio không còn là tài nguyên riêng đã xác minh của đúng câu hỏi");
        }
        return asset;
    }

    private void requireArtifactIdentity(
            String clientId,
            SpeakingPromptAiArtifact artifact,
            String fingerprint,
            String provider,
            String model,
            String language,
            String contract,
            String purpose,
            String retention) {
        if (!Objects.equals(artifact.getOperationFingerprint(), fingerprint)
                || !Objects.equals(artifact.getProviderCode(), provider)
                || !Objects.equals(artifact.getModelCode(), model)
                || !Objects.equals(artifact.getLanguageTag(), language)
                || !Objects.equals(artifact.getContractVersion(), contract)
                || !Objects.equals(artifact.getPurposeCode(), purpose)
                || !Objects.equals(artifact.getRetentionCode(), retention)) {
            throw invalid(clientId,
                    "dấu vân tay/model/hợp đồng artifact không khớp nguồn hiện hành");
        }
    }

    private SpeakingPromptAuthoringAiProperties.TtsConfig selectedTtsConfig(
            JsonNode options) {
        SpeakingPromptAuthoringAiProperties.TtsConfig base =
                properties.ttsConfig();
        String voice = options.path("voiceCode").asText(base.voice());
        BigDecimal speed;
        try {
            speed = new BigDecimal(options.path("speed")
                    .asText(base.speed().toPlainString()));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "Không thể xuất bản Speaking: tốc độ audio AI không hợp lệ.");
        }
        String format = options.path("outputFormat").asText(base.outputFormat());
        if (!Objects.equals(voice, base.voice())
                || !base.allowedOutputFormats().contains(format)) {
            throw new IllegalStateException(
                    "Không thể xuất bản Speaking: cấu hình audio AI chưa được duyệt.");
        }
        return new SpeakingPromptAuthoringAiProperties.TtsConfig(
                base.enabled(), base.provider(), base.baseUrl(), base.apiKey(),
                base.model(), base.language(), voice, speed, format,
                base.maxInputCharacters(), base.purposeCode(), base.retentionCode(),
                base.maxOutputBytes(), base.maxOutputDuration(),
                base.connectTimeout(), base.readTimeout(),
                base.allowedOutputFormats(), base.allowedOutputMimeTypes());
    }

    private QuestionContent readTypedContent(
            JsonNode question,
            String clientId) {
        try {
            QuestionContent content = contractCodec.readQuestionContent(
                    question.path("questionContent").toString(),
                    CanonicalQuestionType.SPEAKING);
            if (!QuestionContent.supportsTypedSpeakingDelivery(
                    content.schemaVersion())) {
                throw invalid(clientId,
                        "lần xuất bản mới phải dùng question-content-v2/v3");
            }
            return content;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid(clientId,
                    "hợp đồng giao đề typed không hợp lệ: "
                            + exception.getMessage());
        }
    }

    private static void requireCombination(
            String clientId,
            QuestionContent.SpeakingDelivery delivery,
            QuestionContent.SpeakingPromptInputType input,
            QuestionContent.SpeakingDeliveryMode mode,
            QuestionContent.SpeakingAudioOrigin origin) {
        if (delivery == null
                || delivery.inputType() != input
                || delivery.deliveryMode() != mode
                || delivery.audioOrigin() != origin) {
            throw invalid(clientId,
                    "chế độ nguồn, cách giao đề và nguồn audio không đồng nhất");
        }
    }

    private static QuestionContent learnerContent(
            QuestionContent draftContent,
            QuestionContent.SpeakingPromptInputType input,
            QuestionContent.SpeakingDeliveryMode mode,
            QuestionContent.SpeakingAudioOrigin origin,
            String audioReference) {
        QuestionContent.SpeakingDelivery draft =
                draftContent.speakingDelivery();
        Integer playLimit = mode == QuestionContent.SpeakingDeliveryMode.TEXT_ONLY
                ? null : draft.promptPlayLimit();
        return new QuestionContent(
                draftContent.schemaVersion(),
                List.of(),
                List.of(),
                null,
                null,
                new QuestionContent.SpeakingDelivery(
                        input, mode, audioReference, origin, playLimit,
                        draft.preparationSeconds(), draft.responseSeconds()),
                null,
                draftContent.languageTag());
    }

    private static Map<String, JsonNode> speakingQuestions(JsonNode root) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode section : root.path("sections")) {
            if (!"SPEAKING".equalsIgnoreCase(section.path("skill").asText())) {
                continue;
            }
            for (JsonNode group : section.path("groups")) {
                for (JsonNode question : group.path("questions")) {
                    if (!"SPEAKING".equalsIgnoreCase(
                            question.path("questionType").asText())) {
                        continue;
                    }
                    String clientId = question.path("clientId").asText("").trim();
                    if (clientId.isBlank() || result.putIfAbsent(clientId, question) != null) {
                        throw new IllegalStateException(
                                "Không thể xuất bản Speaking: clientId câu hỏi bị thiếu hoặc trùng.");
                    }
                }
            }
        }
        return result;
    }

    private static void requireDraftAudioReference(
            Long draftId,
            String clientId,
            String origin,
            String actual,
            Long assetId) {
        String encoded = URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                .replace("+", "%20");
        String authoring = "/practice/manage/drafts/" + draftId
                + "/questions/" + encoded + "/speaking-prompt/media/" + origin;
        String material = materialUrl(assetId);
        if (!Objects.equals(actual, authoring)
                && !Objects.equals(actual, material)) {
            throw invalid(clientId,
                    "tham chiếu audio trong bản nháp không khớp tài nguyên đang hoạt động");
        }
    }

    private static String materialUrl(Long assetId) {
        return "/practice/materials/" + assetId + "/content";
    }

    private static boolean hashEquals(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static boolean containsKorean(String value) {
        return value != null && value.codePoints().anyMatch(codePoint ->
                (codePoint >= 0xAC00 && codePoint <= 0xD7A3)
                        || (codePoint >= 0x1100 && codePoint <= 0x11FF)
                        || (codePoint >= 0x3130 && codePoint <= 0x318F));
    }

    private static Candidate throwInvalidInputType(String clientId) {
        throw invalid(clientId, "loại nguồn đề hiện hành không được hỗ trợ");
    }

    private static IllegalStateException invalid(String clientId, String detail) {
        return new IllegalStateException(
                "Không thể xuất bản câu Speaking '" + clientId + "': " + detail + ".");
    }

    public record PublicationPlan(Map<String, Candidate> candidates) {
        public PublicationPlan {
            candidates = candidates == null ? Map.of() : Map.copyOf(candidates);
        }

        static PublicationPlan empty() {
            return new PublicationPlan(Map.of());
        }

        public Candidate require(String clientId) {
            Candidate candidate = candidates.get(clientId);
            if (candidate == null) {
                throw invalid(clientId, "thiếu kế hoạch nguồn đề đã xác minh");
            }
            return candidate;
        }

        public Set<ActiveAssetBinding> activeAssetBindings() {
            Set<ActiveAssetBinding> result = new LinkedHashSet<>();
            candidates.values().stream()
                    .filter(candidate -> candidate.activeAssetId() != null)
                    .sorted(Comparator.comparing(Candidate::questionClientId))
                    .forEach(candidate -> result.add(new ActiveAssetBinding(
                            candidate.activeAssetId(),
                            candidate.activePlacement(),
                            candidate.questionClientId())));
            return Set.copyOf(result);
        }
    }

    public record ActiveAssetBinding(
            Long assetId,
            String placement,
            String questionClientId) {
    }

    public record Candidate(
            Long draftId,
            String questionClientId,
            SpeakingPromptSource source,
            Long sourceRevision,
            Long sttArtifactId,
            Long ttsArtifactId,
            Long activeAssetId,
            String activePlacement,
            QuestionContent learnerContent,
            SpeakingPromptVersionContext.ImmutableData context) {

        void verifySourceStillLockedAndCurrent() {
            if (source == null
                    || !Objects.equals(draftId, source.getDraftId())
                    || !Objects.equals(questionClientId, source.getQuestionClientId())
                    || !Objects.equals(sourceRevision, source.getSourceRevision())
                    || !Objects.equals(sttArtifactId, source.getCurrentSttArtifactId())
                    || !Objects.equals(ttsArtifactId, source.getCurrentTtsArtifactId())
                    || !Objects.equals(activeAssetId, source.getActiveAudioAssetId())) {
                throw invalid(questionClientId,
                        "nguồn đề đã thay đổi trong giao dịch xuất bản");
            }
            SpeakingPromptContextIdentity.fingerprint(context);
        }
    }
}
