package com.ksh.features.practice.manage.authoringcandidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateState;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateView;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CreateCommand;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ReviewUpdateCommand;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceKind;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceSnapshot;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ValidationIssue;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class PracticeAuthoringCandidateService {

    public static final String SCHEMA_VERSION =
            "practice-authoring-candidate-v1";
    private static final Duration MINIMUM_RETENTION = Duration.ofDays(7);
    private static final Set<CandidateState> EXPIRABLE_STATES = Set.of(
            CandidateState.PARSED,
            CandidateState.NORMALIZED,
            CandidateState.VALIDATED,
            CandidateState.REVIEWING,
            CandidateState.READY_TO_APPLY,
            CandidateState.FAILED);
    private static final Set<String> AI_EXECUTION_FIELDS = Set.of(
            "purpose", "bindingRevision", "providerProfileCode",
            "providerFamily", "model", "transportDialect", "requestId");

    private final PracticeAuthoringCandidateRepository candidateRepository;
    private final PracticeDraftRepository draftRepository;
    private final PracticeAuthorizationService authorizationService;
    private final PracticeAuthoringCandidateNormalizer normalizer;
    private final PracticeAuthoringCandidateValidator validator;
    private final PracticeAuthoringCandidateJson candidateJson;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration retention;

    @Autowired
    public PracticeAuthoringCandidateService(
            PracticeAuthoringCandidateRepository candidateRepository,
            PracticeDraftRepository draftRepository,
            PracticeAuthorizationService authorizationService,
            PracticeAuthoringCandidateNormalizer normalizer,
            PracticeAuthoringCandidateValidator validator,
            PracticeAuthoringCandidateJson candidateJson,
            ObjectMapper objectMapper,
            @Value("${app.practice.authoring-candidate.retention:P7D}")
            Duration retention) {
        this(candidateRepository, draftRepository, authorizationService,
                normalizer, validator, candidateJson, objectMapper,
                Clock.systemUTC(), retention);
    }

    PracticeAuthoringCandidateService(
            PracticeAuthoringCandidateRepository candidateRepository,
            PracticeDraftRepository draftRepository,
            PracticeAuthorizationService authorizationService,
            PracticeAuthoringCandidateNormalizer normalizer,
            PracticeAuthoringCandidateValidator validator,
            PracticeAuthoringCandidateJson candidateJson,
            ObjectMapper objectMapper,
            Clock clock,
            Duration retention) {
        this.candidateRepository = candidateRepository;
        this.draftRepository = draftRepository;
        this.authorizationService = authorizationService;
        this.normalizer = normalizer;
        this.validator = validator;
        this.candidateJson = candidateJson;
        this.objectMapper = objectMapper;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.retention = Objects.requireNonNull(retention, "retention");
        if (retention.compareTo(MINIMUM_RETENTION) < 0) {
            throw new IllegalArgumentException(
                    "Authoring candidate retention must be at least seven days");
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CandidateView createOrReuse(CreateCommand command) {
        return createOrReuse(command, List.of());
    }

    /**
     * Source adapters may add server-authored warnings after their own strict
     * validation. Provider issue envelopes are never accepted directly.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CandidateView createOrReuse(
            CreateCommand command,
            List<ValidationIssue> sourceIssues) {
        requireCommand(command);
        List<ValidationIssue> trustedSourceIssues = sourceIssues == null
                ? List.of() : List.copyOf(sourceIssues);
        if (trustedSourceIssues.size() > 200) {
            throw new IllegalArgumentException(
                    "Candidate source issue count exceeds the safe limit");
        }
        SourceSnapshot source = normalizeAndValidateSource(command.source());
        TargetRoute target = normalizeTarget(command.target());
        authorizationService.requireDraft(
                target.draftId(), command.actorId(), PracticeAction.EDIT);
        PracticeDraft draft = draftRepository.findByIdForUpdate(target.draftId())
                .orElseThrow(() -> new PracticeAuthoringCandidateException(
                        "CANDIDATE_TARGET_NOT_FOUND",
                        "Bản nháp target không tồn tại."));
        requireTargetSection(draft, target);
        int baseDraftVersion = version(draft);
        String sourceDigest = PracticeAuthoringCandidateJson
                .stripDigestPrefix(source.sourceDigest());

        PracticeAuthoringCandidate existing = candidateRepository
                .findIdempotent(
                        command.actorId(), source.kind(), source.contractVersion(),
                        sourceDigest, source.sourceRevision(), source.operation(),
                        target.draftId(), target.testNo(), target.skill(),
                        target.lessonCode(), baseDraftVersion,
                        PracticeAuthoringCandidate.NORMALIZER_VERSION)
                .orElse(null);
        if (existing != null) return view(existing);

        LocalDateTime now = now();
        LocalDateTime expiresAt = now.plus(retention);
        String candidateId = UUID.randomUUID().toString();
        PracticeAuthoringCandidateNormalizer.NormalizationResult normalized =
                normalizer.normalize(candidateId, source.kind(), command.groups());
        List<ValidationIssue> normalizationAndSourceIssues = new ArrayList<>(
                normalized.issues());
        normalizationAndSourceIssues.addAll(trustedSourceIssues);
        String digest = contentDigest(
                source, target, baseDraftVersion, normalized.groups());
        ObjectNode envelope = envelope(
                candidateId, command.actorId(), source, target,
                baseDraftVersion, CandidateState.PARSED,
                normalized.groups(), normalizationAndSourceIssues, digest,
                false, now, expiresAt);
        PracticeAuthoringCandidate candidate = new PracticeAuthoringCandidate(
                candidateId,
                command.actorId(),
                source.kind(),
                source.contractVersion(),
                sourceDigest,
                source.sourceRevision(),
                source.sourceName(),
                source.operation(),
                target.draftId(),
                target.testNo(),
                target.skill(),
                target.lessonCode(),
                baseDraftVersion,
                candidateJson.write(envelope),
                digest,
                now,
                expiresAt);

        setState(envelope, CandidateState.NORMALIZED);
        candidate.markNormalized(candidateJson.write(envelope), digest, now);
        PracticeAuthoringCandidateValidator.ValidationResult validation =
                validator.validate(
                        source.kind(), target, normalized.groups(),
                        normalizationAndSourceIssues);
        setIssues(envelope, validation.issues());
        setState(envelope, CandidateState.VALIDATED);
        candidate.markValidated(candidateJson.write(envelope), digest, now);
        setState(envelope, CandidateState.REVIEWING);
        candidate.beginReview(candidateJson.write(envelope), now);

        return view(candidateRepository.saveAndFlush(candidate));
    }

    @Transactional(readOnly = true)
    public CandidateView get(String candidateId, Long actorId) {
        PracticeAuthoringCandidate candidate = owned(candidateId, actorId);
        authorizationService.requireDraft(
                candidate.getTargetDraftId(), actorId, PracticeAction.READ);
        return view(candidate);
    }

    @Transactional
    public CandidateView updateReview(ReviewUpdateCommand command) {
        Objects.requireNonNull(command, "review update");
        PracticeAuthoringCandidate candidate = owned(
                command.candidateId(), command.actorId());
        authorizationService.requireDraft(
                candidate.getTargetDraftId(), command.actorId(),
                PracticeAction.EDIT);
        requireVersionAndDigest(
                candidate, command.expectedVersion(), command.expectedDigest());
        LocalDateTime now = now();
        if (!candidate.isTerminal() && candidate.isExpiredAt(now)) {
            expire(candidate, now);
            return view(candidateRepository.saveAndFlush(candidate));
        }
        requireReviewable(candidate);

        ObjectNode current = candidateJson.readObject(candidate.getCandidateJson());
        PracticeAuthoringCandidateNormalizer.NormalizationResult normalized =
                normalizer.normalize(
                        candidate.getId(), candidate.getSourceKind(),
                        command.groups());
        requireStableIdentity(current.path("groups"), normalized.groups());
        TargetRoute target = target(candidate);
        PracticeAuthoringCandidateValidator.ValidationResult validation =
                validator.validate(
                        candidate.getSourceKind(), target,
                        normalized.groups(), normalized.issues());
        String digest = contentDigest(
                source(candidate, current), target,
                candidate.getBaseDraftVersion(), normalized.groups());
        current.set("groups", normalized.groups());
        setIssues(current, validation.issues());
        current.put("contentDigest",
                PracticeAuthoringCandidateJson.prefixedDigest(digest));
        current.put("warningsAcknowledged", command.acknowledgeWarnings());
        setState(current, CandidateState.REVIEWING);
        candidate.replaceReview(
                candidateJson.write(current), digest, command.actorId(),
                command.acknowledgeWarnings(), now);
        return view(candidateRepository.saveAndFlush(candidate));
    }

    @Transactional
    public CandidateView markReady(
            String candidateId,
            Long actorId,
            long expectedVersion,
            String expectedDigest) {
        PracticeAuthoringCandidate candidate = owned(candidateId, actorId);
        authorizationService.requireDraft(
                candidate.getTargetDraftId(), actorId, PracticeAction.EDIT);
        requireVersionAndDigest(candidate, expectedVersion, expectedDigest);
        LocalDateTime now = now();
        if (!candidate.isTerminal() && candidate.isExpiredAt(now)) {
            expire(candidate, now);
            return view(candidateRepository.saveAndFlush(candidate));
        }
        if (candidate.getState() != CandidateState.REVIEWING) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_NOT_READY",
                    "Candidate không ở trạng thái rà soát để đánh dấu sẵn sàng.");
        }
        ObjectNode envelope = candidateJson.readObject(candidate.getCandidateJson());
        List<ValidationIssue> priorIssues = readIssues(envelope.path("issues"));
        PracticeAuthoringCandidateValidator.ValidationResult validation =
                validator.validate(
                        candidate.getSourceKind(), target(candidate),
                        envelope.path("groups"), priorIssues);
        setIssues(envelope, validation.issues());
        if (validation.hasBlocking()) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_VALIDATION_BLOCKED",
                    "Candidate vẫn còn lỗi chặn áp dụng.");
        }
        setState(envelope, CandidateState.READY_TO_APPLY);
        candidate.markReady(
                candidateJson.write(envelope), actorId,
                validation.hasWarnings(), now);
        return view(candidateRepository.saveAndFlush(candidate));
    }

    @Transactional
    public CandidateView reject(
            String candidateId,
            Long actorId,
            long expectedVersion,
            String expectedDigest) {
        PracticeAuthoringCandidate candidate = owned(candidateId, actorId);
        authorizationService.requireDraft(
                candidate.getTargetDraftId(), actorId, PracticeAction.EDIT);
        requireVersionAndDigest(candidate, expectedVersion, expectedDigest);
        LocalDateTime now = now();
        if (!candidate.isTerminal() && candidate.isExpiredAt(now)) {
            expire(candidate, now);
            return view(candidateRepository.saveAndFlush(candidate));
        }
        if (candidate.isTerminal()) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_NOT_REVIEWABLE",
                    "Candidate đã đóng và không thể từ chối lại.");
        }
        ObjectNode envelope = candidateJson.readObject(candidate.getCandidateJson());
        setState(envelope, CandidateState.REJECTED);
        candidate.reject(candidateJson.write(envelope), now);
        return view(candidateRepository.saveAndFlush(candidate));
    }

    @Transactional
    public int expireDueCandidates() {
        LocalDateTime now = now();
        List<PracticeAuthoringCandidate> due = candidateRepository
                .findByExpiresAtLessThanEqualAndStateIn(now, EXPIRABLE_STATES);
        int expired = 0;
        for (PracticeAuthoringCandidate candidate : due) {
            if (expire(candidate, now)) expired++;
        }
        if (expired > 0) candidateRepository.saveAll(due);
        return expired;
    }

    private boolean expire(
            PracticeAuthoringCandidate candidate,
            LocalDateTime now) {
        ObjectNode envelope = candidateJson.readObject(candidate.getCandidateJson());
        setState(envelope, CandidateState.EXPIRED);
        return candidate.expireIfDue(candidateJson.write(envelope), now);
    }

    private PracticeAuthoringCandidate owned(String id, Long actorId) {
        if (id == null || actorId == null) throw denied();
        return candidateRepository.findByIdAndOwnerId(id, actorId)
                .orElseThrow(PracticeAuthoringCandidateService::denied);
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException(
                "Bạn không có quyền truy cập authoring candidate này.");
    }

    private static void requireVersionAndDigest(
            PracticeAuthoringCandidate candidate,
            long expectedVersion,
            String expectedDigest) {
        String digest = PracticeAuthoringCandidateJson.stripDigestPrefix(
                expectedDigest);
        if (candidate.getLockVersion() != expectedVersion
                || !digest.matches("[0-9a-f]{64}")
                || !candidate.getContentDigest().equals(digest)) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_VERSION_CONFLICT",
                    "Candidate hoặc digest đã thay đổi; hãy tải lại trước khi tiếp tục.");
        }
    }

    private static void requireReviewable(
            PracticeAuthoringCandidate candidate) {
        if (!Set.of(
                CandidateState.VALIDATED,
                CandidateState.REVIEWING,
                CandidateState.READY_TO_APPLY)
                .contains(candidate.getState())) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_NOT_REVIEWABLE",
                    "Candidate không còn ở trạng thái có thể rà soát.");
        }
    }

    private SourceSnapshot normalizeAndValidateSource(SourceSnapshot raw) {
        if (raw == null || raw.kind() == null) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_SOURCE_INVALID", "Source candidate không hợp lệ.");
        }
        String contract = PracticeAuthoringCandidateJson.normalizedText(
                raw.contractVersion());
        String digest = PracticeAuthoringCandidateJson.normalizedText(
                raw.sourceDigest()).toLowerCase(java.util.Locale.ROOT);
        String revision = PracticeAuthoringCandidateJson.normalizedText(
                raw.sourceRevision());
        String sourceName = PracticeAuthoringCandidateJson.normalizedText(
                raw.sourceName());
        SourceOperation operation = raw.operation() == null
                ? SourceOperation.NONE : raw.operation();
        if (!raw.kind().contractVersion().equals(contract)
                || !digest.matches("sha256:[0-9a-f]{64}")
                || revision.isBlank() || revision.length() > 100
                || sourceName.length() > 255) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_SOURCE_INVALID",
                    "Source kind, contract, digest hoặc revision không hợp lệ.");
        }
        if (raw.kind() == SourceKind.PDF_AI) {
            requirePdfExecution(operation, raw.aiExecution());
        } else if (operation != SourceOperation.NONE
                || raw.aiExecution() != null) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_SOURCE_INVALID",
                    "Excel source không được chứa operation hoặc AI execution.");
        }
        return new SourceSnapshot(
                raw.kind(), contract, digest, revision,
                sourceName,
                operation,
                raw.aiExecution() == null ? null : raw.aiExecution().deepCopy());
    }

    private static void requirePdfExecution(
            SourceOperation operation,
            JsonNode aiExecution) {
        if (operation == SourceOperation.NONE
                || aiExecution == null || !aiExecution.isObject()) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_SOURCE_INVALID",
                    "PDF AI cần operation và execution snapshot đầy đủ.");
        }
        Set<String> fields = new HashSet<>();
        aiExecution.fieldNames().forEachRemaining(fields::add);
        if (!fields.equals(AI_EXECUTION_FIELDS)
                || !"PRACTICE_PDF_AUTHORING".equals(
                        aiExecution.path("purpose").asText())
                || !aiExecution.path("bindingRevision").isIntegralNumber()
                || aiExecution.path("bindingRevision").asLong() < 0
                || !boundedText(aiExecution, "providerProfileCode", 100)
                || !boundedText(aiExecution, "providerFamily", 100)
                || !boundedText(aiExecution, "model", 200)
                || !boundedText(aiExecution, "transportDialect", 100)
                || !boundedText(aiExecution, "requestId", 36)) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_SOURCE_INVALID",
                    "PDF AI execution snapshot không đúng contract.");
        }
        try {
            UUID.fromString(aiExecution.path("requestId").asText());
        } catch (IllegalArgumentException exception) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_SOURCE_INVALID",
                    "PDF AI requestId không hợp lệ.");
        }
    }

    private static boolean boundedText(
            JsonNode node, String field, int maxLength) {
        JsonNode value = node.path(field);
        return value.isTextual()
                && !value.asText().isBlank()
                && value.asText().length() <= maxLength;
    }

    private static TargetRoute normalizeTarget(TargetRoute raw) {
        if (raw == null || raw.draftId() == null || raw.draftId() < 1) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_TARGET_INVALID", "Target candidate không hợp lệ.");
        }
        String skill = upper(raw.skill());
        String lessonCode = upper(raw.lessonCode());
        String prefix = switch (skill) {
            case "READING" -> "R";
            case "LISTENING" -> "L";
            case "WRITING" -> "W";
            case "SPEAKING" -> "S";
            default -> "";
        };
        if (raw.testNo() < 1
                || !lessonCode.equals(prefix + raw.testNo())) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_TARGET_INVALID", "Target candidate không hợp lệ.");
        }
        return new TargetRoute(raw.draftId(), raw.testNo(), skill, lessonCode);
    }

    private void requireTargetSection(PracticeDraft draft, TargetRoute target) {
        ObjectNode root;
        try {
            JsonNode parsed = objectMapper.readTree(draft.getDraftJson());
            root = parsed instanceof ObjectNode object ? object : null;
        } catch (Exception exception) {
            root = null;
        }
        if (root == null) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_TARGET_INVALID",
                    "Bản nháp target không có JSON hợp lệ.");
        }
        int matches = 0;
        for (JsonNode section : root.path("sections")) {
            if (section.path("testNo").asInt() == target.testNo()
                    && target.skill().equals(upper(
                    section.path("skill").asText()))
                    && target.lessonCode().equals(upper(
                    section.path("lessonCode").asText()))) {
                matches++;
            }
        }
        if (matches != 1) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_TARGET_SECTION_NOT_FOUND",
                    "Không tìm thấy đúng một section khớp Test/skill/lesson.");
        }
    }

    private void requireStableIdentity(JsonNode previous, JsonNode updated) {
        Map<String, Set<String>> previousIds = identities(previous);
        Map<String, Set<String>> updatedIds = identities(updated);
        if (!previousIds.keySet().containsAll(updatedIds.keySet())) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_STABLE_ID_CHANGED",
                    "Review không được thêm hoặc đổi ID nhóm candidate.");
        }
        for (Map.Entry<String, Set<String>> entry : updatedIds.entrySet()) {
            Set<String> previousQuestions = previousIds.get(entry.getKey());
            if (previousQuestions == null
                    || !previousQuestions.containsAll(entry.getValue())) {
                throw new PracticeAuthoringCandidateException(
                        "CANDIDATE_STABLE_ID_CHANGED",
                        "Review không được thêm, đổi hoặc chuyển ID câu hỏi.");
            }
        }
        requireStableSourceReferences(previous, updated);
    }

    private void requireStableSourceReferences(
            JsonNode previous,
            JsonNode updated) {
        Map<String, JsonNode> previousGroups = nodesById(
                previous, "candidateGroupId");
        for (JsonNode updatedGroup : updated) {
            JsonNode previousGroup = previousGroups.get(
                    updatedGroup.path("candidateGroupId").asText());
            if (previousGroup == null
                    || !sameJson(previousGroup.path("sourceRefs"),
                    updatedGroup.path("sourceRefs"))
                    || !sameJson(previousGroup.path("stimulus")
                    .path("provenance").path("sourceRefs"),
                    updatedGroup.path("stimulus")
                            .path("provenance").path("sourceRefs"))) {
                throw changedSourceReference();
            }
            Map<String, JsonNode> previousQuestions = nodesById(
                    previousGroup.path("questions"), "candidateQuestionId");
            for (JsonNode updatedQuestion : updatedGroup.path("questions")) {
                JsonNode previousQuestion = previousQuestions.get(
                        updatedQuestion.path("candidateQuestionId").asText());
                if (previousQuestion == null
                        || !sameJson(previousQuestion.path("sourceRefs"),
                        updatedQuestion.path("sourceRefs"))) {
                    throw changedSourceReference();
                }
            }
        }
    }

    private boolean sameJson(JsonNode left, JsonNode right) {
        return candidateJson.canonical(left).equals(candidateJson.canonical(right));
    }

    private static Map<String, JsonNode> nodesById(
            JsonNode values, String field) {
        Map<String, JsonNode> result = new HashMap<>();
        if (!values.isArray()) return result;
        values.forEach(value -> result.put(value.path(field).asText(), value));
        return result;
    }

    private static PracticeAuthoringCandidateException changedSourceReference() {
        return new PracticeAuthoringCandidateException(
                "CANDIDATE_SOURCE_REFERENCE_CHANGED",
                "Review không được thay đổi source reference của nội dung giữ lại.");
    }

    private static Map<String, Set<String>> identities(JsonNode groups) {
        Map<String, Set<String>> result = new HashMap<>();
        if (!groups.isArray()) return result;
        for (JsonNode group : groups) {
            String groupId = group.path("candidateGroupId").asText();
            Set<String> questions = new HashSet<>();
            group.path("questions").forEach(question -> questions.add(
                    question.path("candidateQuestionId").asText()));
            result.put(groupId, questions);
        }
        return result;
    }

    private ObjectNode envelope(
            String candidateId,
            Long ownerId,
            SourceSnapshot source,
            TargetRoute target,
            int baseDraftVersion,
            CandidateState state,
            JsonNode groups,
            List<ValidationIssue> issues,
            String digest,
            boolean warningsAcknowledged,
            LocalDateTime createdAt,
            LocalDateTime expiresAt) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("candidateId", candidateId);
        root.put("ownerId", ownerId);
        ObjectNode sourceNode = root.putObject("source");
        sourceNode.put("kind", source.kind().name());
        sourceNode.put("contractVersion", source.contractVersion());
        sourceNode.put("sourceDigest", source.sourceDigest());
        sourceNode.put("sourceRevision", source.sourceRevision());
        if (source.sourceName() != null && !source.sourceName().isBlank()) {
            sourceNode.put("sourceName", source.sourceName());
        }
        if (source.kind() == SourceKind.PDF_AI) {
            sourceNode.put("operation", source.operation().name());
            sourceNode.set("aiExecution", source.aiExecution().deepCopy());
        }
        ObjectNode targetNode = root.putObject("target");
        targetNode.put("draftId", target.draftId());
        targetNode.put("baseDraftVersion", baseDraftVersion);
        targetNode.put("testNo", target.testNo());
        targetNode.put("skill", target.skill());
        targetNode.put("lessonCode", target.lessonCode());
        root.put("state", state.name());
        root.put("normalizerVersion",
                PracticeAuthoringCandidate.NORMALIZER_VERSION);
        root.put("validatorVersion",
                PracticeAuthoringCandidate.VALIDATOR_VERSION);
        root.set("groups", groups.deepCopy());
        setIssues(root, issues);
        root.put("contentDigest",
                PracticeAuthoringCandidateJson.prefixedDigest(digest));
        root.put("warningsAcknowledged", warningsAcknowledged);
        root.put("createdAt", utc(createdAt));
        root.put("expiresAt", utc(expiresAt));
        root.putNull("applied");
        return root;
    }

    private String contentDigest(
            SourceSnapshot source,
            TargetRoute target,
            int baseDraftVersion,
            JsonNode groups) {
        ObjectNode material = objectMapper.createObjectNode();
        material.put("schemaVersion", SCHEMA_VERSION);
        material.put("sourceKind", source.kind().name());
        material.put("sourceContractVersion", source.contractVersion());
        material.put("sourceDigest", source.sourceDigest());
        material.put("sourceRevision", source.sourceRevision());
        material.put("sourceOperation", source.operation().name());
        if (source.aiExecution() != null) {
            material.set("aiExecution", source.aiExecution().deepCopy());
        }
        material.put("targetDraftId", target.draftId());
        material.put("baseDraftVersion", baseDraftVersion);
        material.put("targetTestNo", target.testNo());
        material.put("targetSkill", target.skill());
        material.put("targetLessonCode", target.lessonCode());
        material.put("normalizerVersion",
                PracticeAuthoringCandidate.NORMALIZER_VERSION);
        material.put("validatorVersion",
                PracticeAuthoringCandidate.VALIDATOR_VERSION);
        material.set("groups", groups.deepCopy());
        return candidateJson.digest(material);
    }

    private SourceSnapshot source(
            PracticeAuthoringCandidate candidate,
            ObjectNode envelope) {
        JsonNode source = envelope.path("source");
        SourceOperation operation = candidate.getSourceOperation();
        return new SourceSnapshot(
                candidate.getSourceKind(),
                candidate.getSourceContractVersion(),
                PracticeAuthoringCandidateJson.prefixedDigest(
                        candidate.getSourceDigest()),
                candidate.getSourceRevision(),
                candidate.getSourceName(),
                operation,
                source.path("aiExecution").isObject()
                        ? source.path("aiExecution").deepCopy() : null);
    }

    private static TargetRoute target(PracticeAuthoringCandidate candidate) {
        return new TargetRoute(
                candidate.getTargetDraftId(),
                candidate.getTargetTestNo(),
                candidate.getTargetSkill(),
                candidate.getTargetLessonCode());
    }

    private CandidateView view(PracticeAuthoringCandidate candidate) {
        ObjectNode root = candidateJson.readObject(candidate.getCandidateJson());
        return new CandidateView(
                candidate.getId(), candidate.getState(),
                candidate.getLockVersion(),
                PracticeAuthoringCandidateJson.prefixedDigest(
                        candidate.getContentDigest()),
                root.deepCopy(), readIssues(root.path("issues")));
    }

    static void setState(ObjectNode envelope, CandidateState state) {
        envelope.put("state", state.name());
    }

    void setIssues(ObjectNode envelope, List<ValidationIssue> issues) {
        ArrayNode array = envelope.putArray("issues");
        if (issues == null) return;
        for (ValidationIssue issue : issues) {
            ObjectNode value = array.addObject();
            value.put("severity", issue.severity());
            value.put("code", issue.code());
            value.put("scope", issue.scope());
            value.put("path", issue.path());
            if (issue.sourceLocation() != null) {
                value.set("sourceLocation", issue.sourceLocation().deepCopy());
            }
            value.put("messageVi", issue.messageVi());
            value.put("remediation", issue.remediation());
            value.put("blocking", issue.blocking());
        }
    }

    private static List<ValidationIssue> readIssues(JsonNode values) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (!values.isArray()) return issues;
        for (JsonNode value : values) {
            issues.add(new ValidationIssue(
                    value.path("severity").asText(),
                    value.path("code").asText(),
                    value.path("scope").asText(),
                    value.path("path").asText(),
                    value.path("sourceLocation").isObject()
                            ? value.path("sourceLocation").deepCopy() : null,
                    value.path("messageVi").asText(),
                    value.path("remediation").asText(),
                    value.path("blocking").asBoolean()));
        }
        return List.copyOf(issues);
    }

    private static void requireCommand(CreateCommand command) {
        if (command == null || command.actorId() == null
                || command.actorId() < 1) {
            throw new IllegalArgumentException("Create candidate command is required");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String utc(LocalDateTime value) {
        return value.atOffset(ZoneOffset.UTC).toString();
    }

    private static int version(PracticeDraft draft) {
        return draft.getVersion() == null ? 0 : draft.getVersion();
    }

    private static String upper(String value) {
        return PracticeAuthoringCandidateJson.normalizedText(value)
                .toUpperCase(java.util.Locale.ROOT);
    }
}
