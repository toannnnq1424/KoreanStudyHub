package com.ksh.features.practice.ai.readinglistening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticeExplanationEditorialRevision;
import com.ksh.entities.PracticeQuestionGroupVersion;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.PracticeSectionVersion;
import com.ksh.entities.QuestionExplanationArtifact;
import com.ksh.entities.QuestionVersionExplanationBinding;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.AssessmentSkill;
import com.ksh.features.practice.assessment.AssessmentStimulus;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.ExplanationContext;
import com.ksh.features.practice.assessment.ObjectiveExplanationStrategyRegistry;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.manage.service.PracticeDraftContractService;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.repository.PracticeExplanationEditorialRevisionRepository;
import com.ksh.features.practice.repository.PracticeQuestionGroupVersionRepository;
import com.ksh.features.practice.repository.PracticeQuestionVersionRepository;
import com.ksh.features.practice.repository.PracticeSectionVersionRepository;
import com.ksh.features.practice.repository.QuestionExplanationArtifactRepository;
import com.ksh.features.practice.repository.QuestionVersionExplanationBindingRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lecturer-owned editorial lifecycle for typed R/L explanations.
 *
 * <p>Provider output is never learner-visible until an actor with PUBLISH
 * authority approves the exact authority fingerprint and payload. Publication
 * re-verifies the immutable v4 contract before promoting the already approved
 * payload into the existing artifact/binding lifecycle.</p>
 */
@Service
public class ObjectiveExplanationEditorialService {

    private final PracticeAuthorizationService authorizationService;
    private final PracticeDraftRepository draftRepository;
    private final PracticeExplanationEditorialRevisionRepository revisionRepository;
    private final PracticeDraftContractService draftContractService;
    private final AssessmentContractCodec contractCodec;
    private final QuestionTypeResolver typeResolver;
    private final ReadingListeningExplanationClient client;
    private final ObjectMapper objectMapper;
    private final PracticeQuestionVersionRepository questionVersionRepository;
    private final PracticeSectionVersionRepository sectionVersionRepository;
    private final PracticeQuestionGroupVersionRepository groupVersionRepository;
    private final ExplanationInputFactory inputFactory;
    private final QuestionVersionExplanationBindingRepository bindingRepository;
    private final QuestionExplanationArtifactRepository artifactRepository;

    public ObjectiveExplanationEditorialService(
            PracticeAuthorizationService authorizationService,
            PracticeDraftRepository draftRepository,
            PracticeExplanationEditorialRevisionRepository revisionRepository,
            PracticeDraftContractService draftContractService,
            AssessmentContractCodec contractCodec,
            QuestionTypeResolver typeResolver,
            ReadingListeningExplanationClient client,
            ObjectMapper objectMapper,
            PracticeQuestionVersionRepository questionVersionRepository,
            PracticeSectionVersionRepository sectionVersionRepository,
            PracticeQuestionGroupVersionRepository groupVersionRepository,
            ExplanationInputFactory inputFactory,
            QuestionVersionExplanationBindingRepository bindingRepository,
            QuestionExplanationArtifactRepository artifactRepository) {
        this.authorizationService = authorizationService;
        this.draftRepository = draftRepository;
        this.revisionRepository = revisionRepository;
        this.draftContractService = draftContractService;
        this.contractCodec = contractCodec;
        this.typeResolver = typeResolver;
        this.client = client;
        this.objectMapper = objectMapper;
        this.questionVersionRepository = questionVersionRepository;
        this.sectionVersionRepository = sectionVersionRepository;
        this.groupVersionRepository = groupVersionRepository;
        this.inputFactory = inputFactory;
        this.bindingRepository = bindingRepository;
        this.artifactRepository = artifactRepository;
    }

    @Transactional
    public EditorialView generateDraft(
            Long draftId,
            String questionClientId,
            Long actorId) {
        authorizationService.requireDraft(
                draftId, actorId, PracticeAction.EDIT);
        DraftQuestionAuthority authority = currentAuthority(
                draftId, questionClientId);
        String explanationJson = client.generate(
                authority.context(), List.of());
        return saveRevision(
                authority, explanationJson, actorId);
    }

    @Transactional(readOnly = true)
    public Optional<EditorialView> current(
            Long draftId,
            String questionClientId,
            Long actorId) {
        authorizationService.requireDraft(
                draftId, actorId, PracticeAction.EDIT);
        DraftQuestionAuthority authority = currentAuthority(
                draftId, questionClientId);
        return revisionRepository
                .findByDraftIdAndQuestionClientIdOrderByRevisionNoDesc(
                        draftId, questionClientId)
                .stream()
                .findFirst()
                .filter(revision -> {
                    try {
                        requireRevisionMatches(authority, revision);
                        return client.cleanAndValidateJson(
                                revision.getExplanationJson(),
                                authority.context(),
                                List.of()) != null;
                    } catch (RuntimeException exception) {
                        return false;
                    }
                })
                .map(ObjectiveExplanationEditorialService::view);
    }

    @Transactional
    public EditorialView saveEditedDraft(
            Long draftId,
            String questionClientId,
            String explanationJson,
            Long actorId) {
        authorizationService.requireDraft(
                draftId, actorId, PracticeAction.EDIT);
        DraftQuestionAuthority authority = currentAuthority(
                draftId, questionClientId);
        String validated = client.cleanAndValidateJson(
                explanationJson,
                authority.context(),
                List.of());
        if (validated == null) {
            throw new IllegalArgumentException(
                    "Lời giải đã chỉnh sửa không khớp strategy/evidence contract.");
        }
        return saveRevision(authority, validated, actorId);
    }

    @Transactional
    public EditorialView approve(
            Long draftId,
            String questionClientId,
            Long revisionId,
            Long actorId) {
        authorizationService.requireDraft(
                draftId, actorId, PracticeAction.PUBLISH);
        PracticeExplanationEditorialRevision revision =
                revisionRepository.findById(revisionId)
                        .orElseThrow(() -> new EntityNotFoundException(
                                "Bản lời giải không tồn tại."));
        if (!draftId.equals(revision.getDraftId())
                || !questionClientId.equals(
                        revision.getQuestionClientId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bản lời giải không thuộc câu hỏi bản nháp này.");
        }
        DraftQuestionAuthority authority = currentAuthority(
                draftId, questionClientId);
        requireRevisionMatches(authority, revision);
        String validated = client.cleanAndValidateJson(
                revision.getExplanationJson(),
                authority.context(),
                List.of());
        if (validated == null) {
            throw new IllegalStateException(
                    "Không thể duyệt lời giải không còn khớp contract.");
        }
        revision.approve(actorId, LocalDateTime.now());
        return view(revisionRepository.save(revision));
    }

    /**
     * Called before the publisher mutates the live graph.
     */
    @Transactional(readOnly = true)
    public void requireApprovedForPublish(
            Long draftId,
            Long actorId,
            JsonNode normalizedDraftRoot) {
        List<PublishBlocker> blockers = publishBlockers(
                draftId, actorId, normalizedDraftRoot);
        if (!blockers.isEmpty()) {
            throw new IllegalStateException(blockers.get(0).content());
        }
    }

    /**
     * Read-only publication preflight used by both the editor and publisher.
     * Learner-facing or lecturer-facing messages deliberately use scoped
     * question numbers rather than internal client identifiers or strategy
     * codes.
     */
    @Transactional(readOnly = true)
    public List<PublishBlocker> publishBlockers(
            Long draftId,
            Long actorId,
            JsonNode normalizedDraftRoot) {
        authorizationService.requireDraft(
                draftId, actorId, PracticeAction.PUBLISH);
        List<PublishBlocker> blockers = new ArrayList<>();
        for (QuestionPointer pointer :
                objectiveQuestionPointers(normalizedDraftRoot)) {
            DraftQuestionAuthority authority = authority(
                    draftId, pointer);
            Optional<PracticeExplanationEditorialRevision> approved =
                    revisionRepository
                            .findFirstByDraftIdAndQuestionClientIdAndEditorialStateOrderByRevisionNoDesc(
                                    draftId,
                                    pointer.questionClientId(),
                                    PracticeExplanationEditorialRevision
                                            .STATE_APPROVED);
            String questionLabel = "Câu " + pointer.questionNo()
                    + " (" + skillLabel(pointer.skill()) + ")";
            if (approved.isEmpty()) {
                blockers.add(new PublishBlocker(
                        "BLOCKING",
                        "OBJECTIVE_EXPLANATION_APPROVAL_MISSING",
                        questionLabel
                                + " chưa có lời giải typed đã duyệt.",
                        pointer.sectionIndex(),
                        pointer.groupIndex(),
                        pointer.questionIndex()));
                continue;
            }
            if (!revisionMatches(authority, approved.get())) {
                blockers.add(new PublishBlocker(
                        "BLOCKING",
                        "OBJECTIVE_EXPLANATION_APPROVAL_STALE",
                        "Lời giải typed của " + questionLabel
                                + " đã cũ vì câu hỏi, nguồn hoặc đáp án đã đổi.",
                        pointer.sectionIndex(),
                        pointer.groupIndex(),
                        pointer.questionIndex()));
            }
        }
        return List.copyOf(blockers);
    }

    @Transactional(readOnly = true)
    public List<PublishBlocker> publishBlockers(
            Long draftId,
            Long actorId,
            String normalizedDraftJson) {
        try {
            return publishBlockers(
                    draftId,
                    actorId,
                    objectMapper.readTree(normalizedDraftJson));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Không thể đọc bản nháp để kiểm tra điều kiện xuất bản.",
                    exception);
        }
    }

    /**
     * Runs after immutable version creation and after preparation has created
     * the canonical artifact/binding identities.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int promoteApproved(
            Long draftId,
            Map<String, Long> questionVersionIdsByClient) {
        if (draftId == null
                || questionVersionIdsByClient == null
                || questionVersionIdsByClient.isEmpty()) {
            return 0;
        }
        int promoted = 0;
        for (Map.Entry<String, Long> entry :
                questionVersionIdsByClient.entrySet()) {
            PracticeExplanationEditorialRevision approved =
                    revisionRepository
                            .findFirstByDraftIdAndQuestionClientIdAndEditorialStateOrderByRevisionNoDesc(
                                    draftId,
                                    entry.getKey(),
                                    PracticeExplanationEditorialRevision
                                            .STATE_APPROVED)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Approved explanation revision is missing after publication."));
            PracticeQuestionVersion question =
                    questionVersionRepository.findById(entry.getValue())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Published question version is missing."));
            PracticeSectionVersion section =
                    sectionVersionRepository.findById(
                                    question.getSectionVersionId())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Published section version is missing."));
            PracticeQuestionGroupVersion group =
                    question.getGroupVersionId() == null
                            ? null
                            : groupVersionRepository.findById(
                                            question.getGroupVersionId())
                                    .orElseThrow(() -> new IllegalStateException(
                                            "Published group version is missing."));
            ExplanationInputFactory.PreparedExplanation prepared =
                    inputFactory.prepare(question, group, section);
            if (!approved.getStrategyRegistryVersion().equals(
                            prepared.input().explanationStrategy()
                                    .registryVersion())
                    || !approved.getStrategyCode().equals(
                            prepared.input().explanationStrategy()
                                    .strategyCode())
                    || !approved.getStrategyVersion().equals(
                            prepared.input().explanationStrategy()
                                    .strategyVersion())) {
                throw new IllegalStateException(
                        "Approved strategy does not match immutable question version.");
            }
            String validated = client.cleanAndValidateJson(
                    approved.getExplanationJson(),
                    prepared.context(),
                    List.of());
            if (validated == null) {
                throw new IllegalStateException(
                        "Approved explanation no longer matches immutable evidence.");
            }
            QuestionVersionExplanationBinding binding =
                    bindingRepository
                            .findByQuestionVersionIdAndExplanationLanguage(
                                    question.getId(),
                                    ReadingListeningExplanationClient
                                            .EXPLANATION_LANGUAGE)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Published explanation binding is missing."));
            if (!prepared.fingerprint().fingerprint()
                    .equals(binding.getFingerprint())) {
                throw new IllegalStateException(
                        "Published explanation fingerprint changed after approval.");
            }
            QuestionExplanationArtifact artifact =
                    artifactRepository.findByIdForUpdate(
                                    binding.getArtifactId())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Published explanation artifact is missing."));
            if (!prepared.fingerprint().fingerprint()
                    .equals(artifact.getFingerprint())) {
                throw new IllegalStateException(
                        "Published explanation artifact identity is inconsistent.");
            }
            artifact.markReady(validated, LocalDateTime.now());
            artifactRepository.save(artifact);
            promoted++;
        }
        return promoted;
    }

    private EditorialView saveRevision(
            DraftQuestionAuthority authority,
            String explanationJson,
            Long actorId) {
        List<PracticeExplanationEditorialRevision> revisions =
                revisionRepository
                        .findByDraftIdAndQuestionClientIdOrderByRevisionNoDesc(
                                authority.draftId(),
                                authority.questionClientId());
        LocalDateTime now = LocalDateTime.now();
        revisions.stream()
                .filter(revision ->
                        !PracticeExplanationEditorialRevision
                                .STATE_INVALIDATED
                                .equals(revision.getEditorialState()))
                .forEach(revision -> revision.invalidate(now));
        revisionRepository.saveAll(revisions);
        int nextRevision = revisions.stream()
                .map(PracticeExplanationEditorialRevision::getRevisionNo)
                .max(Integer::compareTo)
                .orElse(0) + 1;
        PracticeExplanationEditorialRevision revision =
                new PracticeExplanationEditorialRevision(
                        authority.draftId(),
                        authority.questionClientId(),
                        nextRevision,
                        authority.selection().registryVersion(),
                        authority.selection().strategyCode(),
                        authority.selection().strategyVersion(),
                        authority.authorityFingerprint(),
                        explanationJson,
                        actorId);
        return view(revisionRepository.save(revision));
    }

    private DraftQuestionAuthority currentAuthority(
            Long draftId,
            String questionClientId) {
        PracticeDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Bản nháp không tồn tại."));
        try {
            JsonNode parsed = objectMapper.readTree(draft.getDraftJson());
            if (!(parsed instanceof ObjectNode root)) {
                throw new IllegalArgumentException(
                        "Bản nháp không phải JSON object.");
            }
            String normalized = draftContractService
                    .normalize(root.deepCopy(), draft.getCreationMethod())
                    .json();
            JsonNode normalizedRoot = objectMapper.readTree(normalized);
            QuestionPointer pointer = objectiveQuestionPointers(
                    normalizedRoot).stream()
                    .filter(item -> questionClientId.equals(
                            item.questionClientId()))
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Câu hỏi R/L không tồn tại trong bản nháp."));
            return authority(draftId, pointer);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Không thể đọc authority câu hỏi trong bản nháp.",
                    exception);
        }
    }

    private DraftQuestionAuthority authority(
            Long draftId,
            QuestionPointer pointer) {
        JsonNode question = pointer.question();
        CanonicalQuestionType type =
                typeResolver.resolve(
                        question.path("questionType").asText(""));
        JsonNode strategy = question.path("explanationStrategy");
        ObjectiveExplanationStrategyRegistry.Selection selection =
                ObjectiveExplanationStrategyRegistry.requireSelection(
                        type,
                        strategy.path("registryVersion").asText(""),
                        strategy.path("strategyCode").asText(""),
                        strategy.path("strategyVersion").asText(""));
        QuestionContent content = contractCodec.readQuestionContent(
                question.path("questionContent").toString(), type);
        AnswerSpec answerSpec = contractCodec.readAnswerSpec(
                question.path("answerSpec").toString(), content);
        JsonNode stimulusNode = pointer.group().path("stimulus");
        String source = pointer.skill() == AssessmentSkill.READING
                ? firstNonBlank(
                        stimulusNode.path("passageText").asText(""),
                        pointer.group().path("passageText").asText(""))
                : firstNonBlank(
                        stimulusNode.path("transcriptText").asText(""),
                        pointer.group().path("transcriptText").asText(""));
        boolean approved = stimulusNode.path("provenance")
                .path("approved").asBoolean(
                        pointer.group().path("stimulusApproved")
                                .asBoolean(true));
        AssessmentStimulus stimulus =
                pointer.skill() == AssessmentSkill.READING
                        ? AssessmentStimulus.readingPassage(
                                source, "DRAFT_EDITORIAL_AUTHORITY")
                        : AssessmentStimulus.listeningAudio(
                                null,
                                source,
                                "DRAFT_EDITORIAL_AUTHORITY",
                                approved);
        ExplanationContext context = new ExplanationContext(
                ExplanationContext.SCHEMA_VERSION,
                draftId,
                null,
                question.path("questionNo").asInt(),
                pointer.skill(),
                type,
                question.path("prompt").asText(""),
                pointer.group().path("instruction").asText(""),
                content,
                answerSpec,
                null,
                stimulus,
                question.path("explanationVi").asText(""),
                ReadingListeningExplanationClient.EXPLANATION_LANGUAGE,
                "NUMERIC",
                selection);
        String fingerprint = authorityFingerprint(
                draftId, pointer, selection);
        return new DraftQuestionAuthority(
                draftId,
                pointer.questionClientId(),
                selection,
                fingerprint,
                context);
    }

    private String authorityFingerprint(
            Long draftId,
            QuestionPointer pointer,
            ObjectiveExplanationStrategyRegistry.Selection selection) {
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("draftId", draftId);
        material.put("questionClientId", pointer.questionClientId());
        material.put("skill", pointer.skill().name());
        material.put("question", pointer.question());
        material.put("stimulus", pointer.group().path("stimulus"));
        material.put("strategy", selection);
        material.put("promptVersion", client.promptVersion());
        material.put("responseSchemaVersion", client.schemaVersion());
        try {
            String canonical = objectMapper.writeValueAsString(material);
            return ExplanationFingerprintBuilder.sha256(canonical);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Không thể tạo fingerprint lời giải.", exception);
        }
    }

    private static List<QuestionPointer> objectiveQuestionPointers(
            JsonNode root) {
        List<QuestionPointer> pointers = new ArrayList<>();
        JsonNode sections = root.path("sections");
        if (!sections.isArray()) {
            return List.of();
        }
        for (int sectionIndex = 0;
             sectionIndex < sections.size();
             sectionIndex++) {
            JsonNode section = sections.get(sectionIndex);
            AssessmentSkill skill;
            try {
                skill = AssessmentSkill.valueOf(
                        section.path("skill").asText("")
                                .toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                continue;
            }
            if (skill != AssessmentSkill.READING
                    && skill != AssessmentSkill.LISTENING) {
                continue;
            }
            JsonNode groups = section.path("groups");
            for (int groupIndex = 0;
                 groupIndex < groups.size();
                 groupIndex++) {
                JsonNode group = groups.get(groupIndex);
                JsonNode questions = group.path("questions");
                for (int questionIndex = 0;
                     questionIndex < questions.size();
                     questionIndex++) {
                    JsonNode question = questions.get(questionIndex);
                    String clientId = question.path("clientId")
                            .asText("").trim();
                    if (clientId.isBlank()) {
                        throw new IllegalArgumentException(
                                "Câu R/L thiếu clientId ổn định.");
                    }
                    pointers.add(new QuestionPointer(
                            clientId,
                            skill,
                            sectionIndex,
                            groupIndex,
                            questionIndex,
                            question.path("questionNo")
                                    .asInt(questionIndex + 1),
                            group,
                            question));
                }
            }
        }
        return List.copyOf(pointers);
    }

    private static void requireRevisionMatches(
            DraftQuestionAuthority authority,
            PracticeExplanationEditorialRevision revision) {
        if (!revisionMatches(authority, revision)) {
            throw new IllegalStateException(
                    "Lời giải đã cũ vì câu hỏi, nguồn, đáp án hoặc strategy đã đổi.");
        }
    }

    private static boolean revisionMatches(
            DraftQuestionAuthority authority,
            PracticeExplanationEditorialRevision revision) {
        return authority.authorityFingerprint().equals(
                        revision.getAuthorityFingerprint())
                && authority.selection().registryVersion().equals(
                        revision.getStrategyRegistryVersion())
                && authority.selection().strategyCode().equals(
                        revision.getStrategyCode())
                && authority.selection().strategyVersion().equals(
                        revision.getStrategyVersion());
    }

    private static String skillLabel(AssessmentSkill skill) {
        return skill == AssessmentSkill.LISTENING ? "Nghe" : "Đọc";
    }

    private static EditorialView view(
            PracticeExplanationEditorialRevision revision) {
        return new EditorialView(
                revision.getId(),
                revision.getRevisionNo(),
                revision.getEditorialState(),
                revision.getStrategyRegistryVersion(),
                revision.getStrategyCode(),
                revision.getStrategyVersion(),
                revision.getAuthorityFingerprint(),
                revision.getExplanationJson(),
                revision.getApprovedAt());
    }

    private static String firstNonBlank(
            String first,
            String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private record QuestionPointer(
            String questionClientId,
            AssessmentSkill skill,
            Integer sectionIndex,
            Integer groupIndex,
            Integer questionIndex,
            Integer questionNo,
            JsonNode group,
            JsonNode question) {
    }

    private record DraftQuestionAuthority(
            Long draftId,
            String questionClientId,
            ObjectiveExplanationStrategyRegistry.Selection selection,
            String authorityFingerprint,
            ExplanationContext context) {
    }

    public record EditorialView(
            Long revisionId,
            Integer revisionNo,
            String state,
            String strategyRegistryVersion,
            String strategyCode,
            String strategyVersion,
            String authorityFingerprint,
            String explanationJson,
            LocalDateTime approvedAt) {
    }

    public record PublishBlocker(
            String type,
            String code,
            String content,
            Integer sIdx,
            Integer gIdx,
            Integer qIdx) {
    }
}
