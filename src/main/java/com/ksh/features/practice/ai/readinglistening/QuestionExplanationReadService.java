package com.ksh.features.practice.ai.readinglistening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.QuestionExplanationArtifact;
import com.ksh.entities.QuestionVersionExplanationBinding;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.dto.PracticeDtos.ResultFeedbackAvailability;
import com.ksh.features.practice.repository.PracticeQuestionVersionRepository;
import com.ksh.features.practice.repository.QuestionExplanationArtifactRepository;
import com.ksh.features.practice.repository.QuestionVersionExplanationBindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QuestionExplanationReadService {

    private static final Logger log = LoggerFactory.getLogger(QuestionExplanationReadService.class);
    private static final String LANGUAGE = ReadingListeningExplanationClient.EXPLANATION_LANGUAGE;

    private final QuestionVersionExplanationBindingRepository bindingRepository;
    private final QuestionExplanationArtifactRepository artifactRepository;
    private final PracticeQuestionVersionRepository questionRepository;
    private final AssessmentContractCodec contractCodec;
    private final QuestionTypeResolver typeResolver;
    private final ObjectMapper objectMapper;

    public QuestionExplanationReadService(
            QuestionVersionExplanationBindingRepository bindingRepository,
            QuestionExplanationArtifactRepository artifactRepository,
            PracticeQuestionVersionRepository questionRepository,
            AssessmentContractCodec contractCodec,
            QuestionTypeResolver typeResolver,
            ObjectMapper objectMapper) {
        this.bindingRepository = bindingRepository;
        this.artifactRepository = artifactRepository;
        this.questionRepository = questionRepository;
        this.contractCodec = contractCodec;
        this.typeResolver = typeResolver;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ResultFeedbackAvailability availability(Collection<Long> questionVersionIds) {
        List<Long> ids = questionVersionIds == null
                ? List.of()
                : questionVersionIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        int total = ids.size();
        if (total == 0) {
            return new ResultFeedbackAvailability(
                    "UNAVAILABLE", "Chưa có câu hỏi để tạo giải thích", 0, 0);
        }
        List<QuestionVersionExplanationBinding> bindings = bindingRepository
                .findByQuestionVersionIdInAndExplanationLanguage(ids, LANGUAGE);
        Map<Long, QuestionVersionExplanationBinding> bindingsByQuestion = bindings.stream()
                .collect(Collectors.toMap(
                        QuestionVersionExplanationBinding::getQuestionVersionId,
                        Function.identity()));
        Map<Long, QuestionExplanationArtifact> artifacts = artifactRepository
                .findAllById(bindings.stream().map(QuestionVersionExplanationBinding::getArtifactId).toList())
                .stream()
                .collect(Collectors.toMap(QuestionExplanationArtifact::getId, Function.identity()));
        int ready = 0;
        int pending = 0;
        int failed = 0;
        for (Long questionVersionId : ids) {
            QuestionVersionExplanationBinding binding = bindingsByQuestion.get(questionVersionId);
            if (binding == null) {
                continue;
            }
            QuestionExplanationArtifact artifact = artifacts.get(binding.getArtifactId());
            if (!validBinding(binding, artifact)) {
                continue;
            }
            if (QuestionExplanationArtifact.STATUS_READY.equals(artifact.getStatus())
                    && validReadyArtifact(artifact)) {
                ready++;
            } else if (QuestionExplanationArtifact.STATUS_FAILED.equals(artifact.getStatus())) {
                failed++;
            } else if (QuestionExplanationArtifact.STATUS_PENDING.equals(artifact.getStatus())) {
                pending++;
            }
        }
        if (ready == total) {
            return new ResultFeedbackAvailability(
                    "READY", "Giải thích đáp án đã sẵn sàng", ready, total);
        }
        if (failed == total) {
            return new ResultFeedbackAvailability(
                    "FAILED", "Chưa thể tạo giải thích đáp án", ready, total);
        }
        if (ready > 0) {
            return new ResultFeedbackAvailability(
                    "PARTIAL", "Giải thích đáp án đã sẵn sàng một phần", ready, total);
        }
        if (pending > 0) {
            return new ResultFeedbackAvailability(
                    "PENDING", "Giải thích đáp án đang được chuẩn bị", 0, total);
        }
        if (failed > 0) {
            return new ResultFeedbackAvailability(
                    "FAILED", "Chưa thể tạo giải thích đáp án", 0, total);
        }
        return new ResultFeedbackAvailability(
                "UNAVAILABLE", "Chưa có explanation artifact cho phiên bản đề này", 0, total);
    }

    @Transactional(readOnly = true)
    public Optional<String> readDisplayJson(Long questionVersionId, String optionLabelMode) {
        if (questionVersionId == null) return Optional.empty();
        PracticeQuestionVersion question = questionRepository.findById(questionVersionId)
                .orElse(null);
        if (question == null) return Optional.empty();
        String raw = readReadyJson(questionVersionId).orElse(null);
        if (raw == null) return Optional.empty();
        return Optional.ofNullable(prepareForDisplay(raw, question, optionLabelMode));
    }

    @Transactional(readOnly = true)
    public Optional<String> readReadyJson(Long questionVersionId) {
        if (questionVersionId == null) return Optional.empty();
        QuestionVersionExplanationBinding binding = bindingRepository
                .findByQuestionVersionIdAndExplanationLanguage(questionVersionId, LANGUAGE)
                .orElse(null);
        if (binding == null) return Optional.empty();
        QuestionExplanationArtifact artifact = artifactRepository.findById(binding.getArtifactId()).orElse(null);
        if (!validBinding(binding, artifact)
                || !QuestionExplanationArtifact.STATUS_READY.equals(artifact.getStatus())
                || !validLegacyDisplayJson(artifact.getExplanationJson())) {
            return Optional.empty();
        }
        return Optional.of(artifact.getExplanationJson());
    }

    /**
     * Canonical, read-only Result Detail artifact read. The immutable assessment
     * snapshot remains authoritative; this method only returns type-specific
     * explanatory material whose discriminator and evidence can be proven
     * against the artifact's locked input contract.
     */
    @Transactional(readOnly = true)
    public Optional<ObjectiveExplanationArtifact> readObjective(
            Long questionVersionId,
            CanonicalQuestionType expectedType) {
        if (questionVersionId == null || expectedType == null
                || expectedType == CanonicalQuestionType.ESSAY
                || expectedType == CanonicalQuestionType.SPEAKING) {
            return Optional.empty();
        }
        QuestionVersionExplanationBinding binding = bindingRepository
                .findByQuestionVersionIdAndExplanationLanguage(questionVersionId, LANGUAGE)
                .orElse(null);
        if (binding == null) {
            return Optional.empty();
        }
        QuestionExplanationArtifact artifact = artifactRepository
                .findById(binding.getArtifactId())
                .orElse(null);
        if (!validBinding(binding, artifact)
                || !QuestionExplanationArtifact.STATUS_READY.equals(artifact.getStatus())) {
            return Optional.empty();
        }
        try {
            return Optional.of(parseObjectiveArtifact(artifact, expectedType));
        } catch (IllegalArgumentException exception) {
            log.warn(
                    "[ReadingListeningAI] Result Detail artifact unavailable questionVersionId={} reason={}",
                    questionVersionId,
                    exception.getMessage());
            return Optional.empty();
        }
    }

    private String prepareForDisplay(
            String explanationJson,
            PracticeQuestionVersion question,
            String optionLabelMode) {
        try {
            CanonicalQuestionType type = typeResolver.resolve(question.getQuestionType());
            QuestionContent content = blank(question.getQuestionContentJson())
                    ? contractCodec.adaptLegacyContent(question.getOptionsJson(), question.getQuestionType())
                    : contractCodec.readQuestionContent(question.getQuestionContentJson(), type);
            AnswerSpec answerSpec = blank(question.getAnswerSpecJson())
                    ? contractCodec.adaptLegacyAnswerSpec(
                            question.getQuestionType(), question.getAnswerKey(), content)
                    : contractCodec.readAnswerSpec(question.getAnswerSpecJson(), content);
            JsonNode parsed = objectMapper.readTree(explanationJson);
            if (!(parsed instanceof ObjectNode root)) return null;

            Map<String, String> labelsByOptionId = new LinkedHashMap<>();
            for (int index = 0; index < content.options().size(); index++) {
                String label = optionLabel(index, optionLabelMode);
                labelsByOptionId.put(content.options().get(index).id(), label);
                labelsByOptionId.put("option_" + (index + 1), label);
            }
            ArrayNode eliminated = objectMapper.createArrayNode();
            if (root.path("eliminatedOptions").isArray()) {
                for (JsonNode item : root.path("eliminatedOptions")) {
                    if (!(item instanceof ObjectNode option)) continue;
                    String label = labelsByOptionId.get(option.path("optionKey").asText(""));
                    if (label == null) continue;
                    ObjectNode display = option.deepCopy();
                    display.put("optionKey", label);
                    eliminated.add(display);
                }
            }
            root.set("eliminatedOptions", eliminated);
            List<String> correctAnswers = answerSpec.correctOptionIds().stream()
                    .map(labelsByOptionId::get)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            if (correctAnswers.isEmpty() && !blank(answerSpec.correctValue())) {
                correctAnswers.add(answerSpec.correctValue());
            }
            if (correctAnswers.isEmpty()) {
                answerSpec.blanks().forEach(answer -> correctAnswers.addAll(answer.acceptedValues()));
            }
            root.put("correctAnswer", String.join(", ", correctAnswers));
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            log.warn("[ReadingListeningAI] Could not map READY explanation for display questionVersionId={} exception={}",
                    question.getId(), exception.getClass().getSimpleName());
            return null;
        }
    }

    private boolean validReadyArtifact(QuestionExplanationArtifact artifact) {
        try {
            CanonicalQuestionType type = typeResolver.resolve(artifact.getQuestionType());
            parseObjectiveArtifact(artifact, type);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private static boolean validBinding(
            QuestionVersionExplanationBinding binding,
            QuestionExplanationArtifact artifact) {
        return binding != null
                && artifact != null
                && artifact.getId() != null
                && artifact.getId().equals(binding.getArtifactId())
                && !blank(binding.getFingerprint())
                && binding.getFingerprint().equals(artifact.getFingerprint());
    }

    private ObjectiveExplanationArtifact parseObjectiveArtifact(
            QuestionExplanationArtifact artifact,
            CanonicalQuestionType expectedType) {
        if (!expectedType.name().equals(artifact.getQuestionType())) {
            throw new IllegalArgumentException("artifact discriminator does not match question");
        }
        JsonNode input = readObject(artifact.getInputContractJson(), "artifact input contract");
        if (!ExplanationArtifactInput.SCHEMA_VERSION.equals(text(input, "schemaVersion"))
                || !expectedType.name().equals(text(input, "questionType"))) {
            throw new IllegalArgumentException("artifact input contract does not match question type");
        }
        return switch (artifact.getResponseSchemaVersion()) {
            case ReadingListeningExplanationClient.EXPLANATION_SCHEMA_VERSION ->
                    parseV3(artifact, expectedType, input);
            case ReadingListeningExplanationClient.LEGACY_EXPLANATION_SCHEMA_VERSION ->
                    parseV2SingleChoice(artifact, expectedType, input);
            default -> throw new IllegalArgumentException(
                    "unsupported explanation response schema");
        };
    }

    private ObjectiveExplanationArtifact parseV3(
            QuestionExplanationArtifact artifact,
            CanonicalQuestionType expectedType,
            JsonNode input) {
        JsonNode root = readObject(artifact.getExplanationJson(), "explanation artifact");
        requireFields(root, Set.of("schemaVersion", "questionType", "explanation"));
        if (!ReadingListeningExplanationClient.EXPLANATION_SCHEMA_VERSION.equals(
                text(root, "schemaVersion"))
                || !expectedType.name().equals(text(root, "questionType"))) {
            throw new IllegalArgumentException("explanation root discriminator mismatch");
        }
        JsonNode explanation = object(root, "explanation");
        return switch (expectedType) {
            case SINGLE_CHOICE -> parseV3SingleChoice(artifact, input, explanation);
            case FILL_BLANK -> parseV3FillBlank(artifact, input, explanation);
            case TRUE_FALSE_NOT_GIVEN -> parseV3Tfng(artifact, input, explanation);
            case ESSAY, SPEAKING -> throw new IllegalArgumentException(
                    "subjective explanation is not objective detail");
        };
    }

    private ObjectiveExplanationArtifact parseV3SingleChoice(
            QuestionExplanationArtifact artifact,
            JsonNode input,
            JsonNode explanation) {
        requireFields(explanation, Set.of(
                "meaningVi", "correctReasonVi", "optionRationales",
                "textEvidenceRefs", "imageEvidenceRefs", "relevantTranslations"));
        CommonExplanation common = commonExplanation(explanation, input);
        if (common.evidence().isEmpty()) {
            throw new IllegalArgumentException(
                    "single-choice explanation needs approved evidence");
        }
        JsonNode inputOptions = input.path("questionContent").path("options");
        JsonNode correctOptions = input.path("answerSpec").path("correctOptionIds");
        if (!inputOptions.isArray() || inputOptions.isEmpty()
                || !correctOptions.isArray() || correctOptions.size() != 1) {
            throw new IllegalArgumentException("single-choice input authority is incomplete");
        }
        Set<String> expectedOptionIds = new LinkedHashSet<>();
        for (int index = 0; index < inputOptions.size(); index++) {
            String optionId = text(inputOptions.get(index), "id");
            if (!("option_" + (index + 1)).equals(optionId)) {
                throw new IllegalArgumentException(
                        "single-choice artifact input lacks canonical stable option IDs");
            }
            expectedOptionIds.add(optionId);
        }
        String correctOptionId = correctOptions.get(0).asText("");
        JsonNode rationalesNode = array(explanation, "optionRationales");
        List<OptionRationale> rationales = new java.util.ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode node : rationalesNode) {
            requireFields(node, Set.of("optionId", "reasonVi", "evidenceIds"));
            String optionId = text(node, "optionId");
            if (!expectedOptionIds.contains(optionId) || !seen.add(optionId)) {
                throw new IllegalArgumentException(
                        "single-choice rationale references an unknown option");
            }
            List<String> evidenceIds = stringList(node, "evidenceIds");
            requireKnownEvidence(evidenceIds, common.evidenceIds());
            rationales.add(new OptionRationale(
                    optionId, text(node, "reasonVi"), evidenceIds));
        }
        if (!seen.equals(expectedOptionIds) || !expectedOptionIds.contains(correctOptionId)) {
            throw new IllegalArgumentException(
                    "single-choice rationale coverage is incomplete");
        }
        return new ObjectiveExplanationArtifact(
                ReadingListeningExplanationClient.EXPLANATION_SCHEMA_VERSION,
                CanonicalQuestionType.SINGLE_CHOICE,
                common.meaningVi(),
                common.correctReasonVi(),
                common.evidence(),
                common.translations(),
                new SingleChoiceExplanation(correctOptionId, rationales),
                artifact.getId());
    }

    private ObjectiveExplanationArtifact parseV3FillBlank(
            QuestionExplanationArtifact artifact,
            JsonNode input,
            JsonNode explanation) {
        requireFields(explanation, Set.of(
                "meaningVi", "correctReasonVi", "blankExplanations",
                "textEvidenceRefs", "imageEvidenceRefs", "relevantTranslations"));
        CommonExplanation common = commonExplanation(explanation, input);
        if (common.evidence().isEmpty()) {
            throw new IllegalArgumentException(
                    "fill-blank explanation needs approved evidence");
        }
        JsonNode inputBlanks = input.path("questionContent").path("blanks");
        if (!inputBlanks.isArray() || inputBlanks.isEmpty()) {
            throw new IllegalArgumentException("fill-blank input authority is incomplete");
        }
        Set<String> expectedBlankIds = new LinkedHashSet<>();
        for (int index = 0; index < inputBlanks.size(); index++) {
            String blankId = text(inputBlanks.get(index), "id");
            if (!("blank_" + (index + 1)).equals(blankId)) {
                throw new IllegalArgumentException(
                        "fill-blank artifact input lacks canonical stable blank IDs");
            }
            expectedBlankIds.add(blankId);
        }
        JsonNode explanations = array(explanation, "blankExplanations");
        List<BlankExplanation> blanks = new java.util.ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode node : explanations) {
            requireFields(node, Set.of(
                    "blankId", "contextExplanationVi", "semanticConstraintVi",
                    "grammarConstraintVi", "registerConstraintVi", "evidenceIds"));
            String blankId = text(node, "blankId");
            if (!expectedBlankIds.contains(blankId) || !seen.add(blankId)) {
                throw new IllegalArgumentException(
                        "fill-blank explanation references an unknown blank");
            }
            List<String> evidenceIds = stringList(node, "evidenceIds");
            requireKnownEvidence(evidenceIds, common.evidenceIds());
            blanks.add(new BlankExplanation(
                    blankId,
                    text(node, "contextExplanationVi"),
                    textAllowBlank(node, "semanticConstraintVi"),
                    textAllowBlank(node, "grammarConstraintVi"),
                    textAllowBlank(node, "registerConstraintVi"),
                    evidenceIds));
        }
        if (!seen.equals(expectedBlankIds)) {
            throw new IllegalArgumentException("fill-blank explanation coverage is incomplete");
        }
        return new ObjectiveExplanationArtifact(
                ReadingListeningExplanationClient.EXPLANATION_SCHEMA_VERSION,
                CanonicalQuestionType.FILL_BLANK,
                common.meaningVi(),
                common.correctReasonVi(),
                common.evidence(),
                common.translations(),
                new FillBlankExplanation(blanks),
                artifact.getId());
    }

    private ObjectiveExplanationArtifact parseV3Tfng(
            QuestionExplanationArtifact artifact,
            JsonNode input,
            JsonNode explanation) {
        requireFields(explanation, Set.of(
                "meaningVi", "correctReasonVi", "relationExplanationVi",
                "whyTrueVi", "whyFalseVi", "whyNotGivenVi", "missingInformationVi",
                "textEvidenceRefs", "imageEvidenceRefs", "relevantTranslations"));
        CommonExplanation common = commonExplanation(explanation, input);
        String officialValue = normalizeTfngValue(
                text(input.path("answerSpec"), "correctValue"));
        if (!Set.of("TRUE", "FALSE", "NOT_GIVEN").contains(officialValue)) {
            throw new IllegalArgumentException("TFNG input authority is invalid");
        }
        String missingInformation = textAllowBlank(explanation, "missingInformationVi");
        if ("NOT_GIVEN".equals(officialValue) && missingInformation.isBlank()) {
            throw new IllegalArgumentException(
                    "NOT_GIVEN explanation needs an explicit missing-information statement");
        }
        if (!"NOT_GIVEN".equals(officialValue) && common.evidence().isEmpty()) {
            throw new IllegalArgumentException(
                    "TRUE/FALSE explanation needs approved evidence");
        }
        return new ObjectiveExplanationArtifact(
                ReadingListeningExplanationClient.EXPLANATION_SCHEMA_VERSION,
                CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN,
                common.meaningVi(),
                common.correctReasonVi(),
                common.evidence(),
                common.translations(),
                new TfngExplanation(
                        text(explanation, "relationExplanationVi"),
                        text(explanation, "whyTrueVi"),
                        text(explanation, "whyFalseVi"),
                        text(explanation, "whyNotGivenVi"),
                        missingInformation),
                artifact.getId());
    }

    private ObjectiveExplanationArtifact parseV2SingleChoice(
            QuestionExplanationArtifact artifact,
            CanonicalQuestionType expectedType,
            JsonNode input) {
        if (expectedType != CanonicalQuestionType.SINGLE_CHOICE) {
            throw new IllegalArgumentException(
                    "v2 compatibility is bounded to SINGLE_CHOICE");
        }
        JsonNode root = readObject(artifact.getExplanationJson(), "v2 explanation artifact");
        requireAllowedFields(root, Set.of(
                "meaningVi", "evidenceQuote", "evidenceKind", "correctReasonVi",
                "relatedTranslationVi", "eliminatedOptions"));
        requirePresentFields(root, Set.of(
                "meaningVi", "evidenceQuote", "correctReasonVi",
                "relatedTranslationVi", "eliminatedOptions"));
        String evidenceKind = root.path("evidenceKind").asText("TEXT");
        if (!"TEXT".equals(evidenceKind)) {
            throw new IllegalArgumentException(
                    "v2 image evidence lacks authoritative digest and region");
        }
        String quote = text(root, "evidenceQuote");
        JsonNode stimulus = object(input, "stimulus");
        TextEvidence evidence = exactTextEvidence(
                "v2-text-1",
                "ANSWER_RATIONALE",
                stimulus,
                quote,
                -1,
                -1);
        JsonNode inputOptions = input.path("questionContent").path("options");
        JsonNode correctOptions = input.path("answerSpec").path("correctOptionIds");
        if (!inputOptions.isArray() || inputOptions.isEmpty()
                || !correctOptions.isArray() || correctOptions.size() != 1) {
            throw new IllegalArgumentException("v2 single-choice authority is incomplete");
        }
        Set<String> optionIds = new LinkedHashSet<>();
        for (int index = 0; index < inputOptions.size(); index++) {
            String optionId = text(inputOptions.get(index), "id");
            if (!("option_" + (index + 1)).equals(optionId)) {
                throw new IllegalArgumentException(
                        "v2 artifact input lacks canonical stable option IDs");
            }
            optionIds.add(optionId);
        }
        String correctOptionId = correctOptions.get(0).asText("");
        List<OptionRationale> rationales = new java.util.ArrayList<>();
        rationales.add(new OptionRationale(
                correctOptionId, text(root, "correctReasonVi"), List.of(evidence.evidenceId())));
        Set<String> seenWrong = new LinkedHashSet<>();
        for (JsonNode node : array(root, "eliminatedOptions")) {
            requireFields(node, Set.of("optionKey", "reasonVi"));
            String optionId = text(node, "optionKey");
            if (!optionIds.contains(optionId) || correctOptionId.equals(optionId)
                    || !seenWrong.add(optionId)) {
                throw new IllegalArgumentException(
                        "v2 option elimination is not authoritative");
            }
            rationales.add(new OptionRationale(
                    optionId, text(node, "reasonVi"), List.of(evidence.evidenceId())));
        }
        Set<String> expectedWrong = new LinkedHashSet<>(optionIds);
        expectedWrong.remove(correctOptionId);
        if (!seenWrong.equals(expectedWrong)) {
            throw new IllegalArgumentException(
                    "v2 option elimination does not cover every wrong option");
        }
        // Validate the legacy field's shape, but do not expose an unscoped v2
        // translation as if it were bound to this exact evidence span.
        textAllowBlank(root, "relatedTranslationVi");
        return new ObjectiveExplanationArtifact(
                ReadingListeningExplanationClient.LEGACY_EXPLANATION_SCHEMA_VERSION,
                CanonicalQuestionType.SINGLE_CHOICE,
                text(root, "meaningVi"),
                text(root, "correctReasonVi"),
                List.of(evidence),
                List.of(),
                new SingleChoiceExplanation(correctOptionId, rationales),
                artifact.getId());
    }

    private CommonExplanation commonExplanation(JsonNode explanation, JsonNode input) {
        List<ArtifactEvidence> evidence = parseEvidence(
                array(explanation, "textEvidenceRefs"),
                array(explanation, "imageEvidenceRefs"),
                input);
        Set<String> evidenceIds = evidence.stream()
                .map(ArtifactEvidence::evidenceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<EvidenceTranslation> translations = parseTranslations(
                array(explanation, "relevantTranslations"), evidenceIds);
        return new CommonExplanation(
                text(explanation, "meaningVi"),
                text(explanation, "correctReasonVi"),
                evidence,
                evidenceIds,
                translations);
    }

    private List<ArtifactEvidence> parseEvidence(
            JsonNode textNodes,
            JsonNode imageNodes,
            JsonNode input) {
        List<ArtifactEvidence> evidence = new java.util.ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode node : textNodes) {
            String evidenceId = text(node, "evidenceId");
            if (!ids.add(evidenceId)) {
                throw new IllegalArgumentException("duplicate evidence ID");
            }
            String kind = text(node, "kind");
            if (!"TEXT_SPAN".equals(kind) && !"TRANSCRIPT_SPAN".equals(kind)) {
                throw new IllegalArgumentException(
                        "unsupported objective text evidence kind");
            }
            evidence.add(parseTextEvidence(node, input, kind));
        }
        for (JsonNode node : imageNodes) {
            String evidenceId = text(node, "evidenceId");
            if (!ids.add(evidenceId)) {
                throw new IllegalArgumentException("duplicate evidence ID");
            }
            if (!"IMAGE_REGION".equals(text(node, "kind"))) {
                throw new IllegalArgumentException(
                        "unsupported objective image evidence kind");
            }
            evidence.add(parseImageEvidence(node, input));
        }
        return List.copyOf(evidence);
    }

    private static List<EvidenceTranslation> parseTranslations(
            JsonNode nodes,
            Set<String> evidenceIds) {
        List<EvidenceTranslation> translations = new java.util.ArrayList<>();
        Set<String> translatedEvidenceIds = new LinkedHashSet<>();
        for (JsonNode node : nodes) {
            requireFields(node, Set.of("evidenceId", "translationVi"));
            String evidenceId = text(node, "evidenceId");
            if (!evidenceIds.contains(evidenceId)
                    || !translatedEvidenceIds.add(evidenceId)) {
                throw new IllegalArgumentException(
                        "translation references foreign or duplicate evidence");
            }
            translations.add(new EvidenceTranslation(
                    evidenceId, text(node, "translationVi")));
        }
        return List.copyOf(translations);
    }

    private TextEvidence parseTextEvidence(
            JsonNode node,
            JsonNode input,
            String kind) {
        requireFields(node, Set.of(
                "evidenceId", "kind", "purpose", "sourceRole",
                "exactQuoteKo", "startOffset", "endOffset"));
        String sourceRole = text(node, "sourceRole");
        if (("TEXT_SPAN".equals(kind) && !"PASSAGE".equals(sourceRole))
                || ("TRANSCRIPT_SPAN".equals(kind) && !"TRANSCRIPT".equals(sourceRole))) {
            throw new IllegalArgumentException("text evidence source role is incompatible");
        }
        JsonNode stimulus = object(input, "stimulus");
        String source = "PASSAGE".equals(sourceRole)
                ? textAllowBlank(stimulus, "passageText")
                : approvedTranscript(stimulus);
        int start = integer(node, "startOffset");
        int end = integer(node, "endOffset");
        String quote = text(node, "exactQuoteKo");
        if (start < 0 || end <= start || end > source.length()
                || !source.substring(start, end).equals(quote)
                || quote.contains("[IMAGE]")) {
            throw new IllegalArgumentException(
                    "text evidence is not an exact approved source span");
        }
        String purpose = text(node, "purpose");
        requireEvidencePurpose(purpose);
        return new TextEvidence(
                text(node, "evidenceId"),
                kind,
                purpose,
                sourceRole,
                quote,
                start,
                end);
    }

    private TextEvidence exactTextEvidence(
            String evidenceId,
            String purpose,
            JsonNode stimulus,
            String quote,
            int requestedStart,
            int requestedEnd) {
        if (quote.contains("[IMAGE]")) {
            throw new IllegalArgumentException(
                    "free-text image marker is not factual evidence");
        }
        String passage = textAllowBlank(stimulus, "passageText");
        String transcript = passage.isBlank() ? approvedTranscript(stimulus) : "";
        String source = passage.isBlank() ? transcript : passage;
        String role = passage.isBlank() ? "TRANSCRIPT" : "PASSAGE";
        int start = requestedStart >= 0 ? requestedStart : source.indexOf(quote);
        int end = requestedEnd >= 0 ? requestedEnd : start + quote.length();
        if (start < 0 || end > source.length()
                || !source.substring(start, end).equals(quote)) {
            throw new IllegalArgumentException(
                    "v2 quote is not an exact approved source span");
        }
        return new TextEvidence(
                evidenceId,
                "PASSAGE".equals(role) ? "TEXT_SPAN" : "TRANSCRIPT_SPAN",
                purpose,
                role,
                quote,
                start,
                end);
    }

    private ImageEvidence parseImageEvidence(JsonNode node, JsonNode input) {
        requireFields(node, Set.of(
                "evidenceId", "kind", "purpose", "sourceRole", "assetDigest",
                "imageIndex", "regionMode", "x", "y", "width", "height"));
        int imageIndex = integer(node, "imageIndex");
        List<JsonNode> images = new java.util.ArrayList<>();
        JsonNode media = input.path("media");
        if (media.isArray()) {
            media.forEach(item -> {
                if ("IMAGE".equals(item.path("kind").asText())) {
                    images.add(item);
                }
            });
        }
        if (imageIndex < 0 || imageIndex >= images.size()) {
            throw new IllegalArgumentException("image evidence index is outside media bundle");
        }
        JsonNode mediaItem = images.get(imageIndex);
        String sourceRole = text(node, "sourceRole");
        String digest = text(node, "assetDigest").toLowerCase(java.util.Locale.ROOT);
        if (!sourceRole.equals(text(mediaItem, "role"))
                || !digest.equals(text(mediaItem, "sha256")
                        .toLowerCase(java.util.Locale.ROOT))
                || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "image evidence does not match authoritative media digest");
        }
        String regionMode = text(node, "regionMode");
        BigDecimal x = decimalOrNull(node, "x");
        BigDecimal y = decimalOrNull(node, "y");
        BigDecimal width = decimalOrNull(node, "width");
        BigDecimal height = decimalOrNull(node, "height");
        if ((!"WHOLE_IMAGE".equals(regionMode)
                        && !"RECTANGLE".equals(regionMode))) {
            throw new IllegalArgumentException("image evidence location is invalid");
        }
        if ("WHOLE_IMAGE".equals(regionMode)) {
            if (x != null || y != null || width != null || height != null) {
                throw new IllegalArgumentException(
                        "WHOLE_IMAGE evidence must not fabricate a rectangle");
            }
        } else if (x == null || y == null || width == null || height == null
                || x.signum() < 0 || y.signum() < 0
                || width.signum() <= 0 || height.signum() <= 0) {
            throw new IllegalArgumentException(
                    "image evidence rectangle is incomplete");
        }
        String purpose = text(node, "purpose");
        requireEvidencePurpose(purpose);
        return new ImageEvidence(
                text(node, "evidenceId"),
                purpose,
                sourceRole,
                digest,
                imageIndex,
                regionMode,
                x, y, width, height);
    }

    private static String approvedTranscript(JsonNode stimulus) {
        if (!stimulus.path("approved").asBoolean(false)) {
            throw new IllegalArgumentException(
                    "listening transcript is not approved immutable evidence");
        }
        String transcript = textAllowBlank(stimulus, "transcriptText");
        if (transcript.isBlank()) {
            throw new IllegalArgumentException(
                    "approved listening transcript is unavailable");
        }
        return transcript;
    }

    private static String normalizeTfngValue(String value) {
        return value == null
                ? ""
                : value.trim()
                        .replace('-', '_')
                        .replace(' ', '_')
                        .toUpperCase(java.util.Locale.ROOT);
    }

    private static void requireKnownEvidence(
            List<String> references,
            Set<String> evidenceIds) {
        if (!evidenceIds.containsAll(references)
                || new LinkedHashSet<>(references).size() != references.size()) {
            throw new IllegalArgumentException(
                    "explanation references unknown evidence");
        }
    }

    private static void requireEvidencePurpose(String purpose) {
        if (!Set.of(
                "ANSWER_RATIONALE",
                "OPTION_ELIMINATION",
                "BLANK_CONSTRAINT",
                "SUPPORTING",
                "CONTRASTING",
                "MISSING_INFORMATION").contains(purpose)) {
            throw new IllegalArgumentException(
                    "evidence purpose is outside the objective registry");
        }
    }

    private JsonNode readObject(String json, String label) {
        if (blank(json)) {
            throw new IllegalArgumentException("missing " + label);
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject()) {
                throw new IllegalArgumentException(label + " must be an object");
            }
            return node;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid " + label, exception);
        }
    }

    private boolean validLegacyDisplayJson(String value) {
        try {
            JsonNode root = readObject(value, "legacy display explanation");
            if (!nonBlankText(root, "meaningVi")
                    || !nonBlankText(root, "evidenceQuote")
                    || !nonBlankText(root, "correctReasonVi")
                    || !root.path("relatedTranslationVi").isTextual()
                    || !root.path("eliminatedOptions").isArray()) {
                return false;
            }
            for (JsonNode option : root.path("eliminatedOptions")) {
                if (!option.isObject()
                        || !nonBlankText(option, "optionKey")
                        || !nonBlankText(option, "reasonVi")) {
                    return false;
                }
            }
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean nonBlankText(JsonNode root, String field) {
        JsonNode value = root.path(field);
        return value.isTextual() && !value.asText().isBlank();
    }

    private static JsonNode object(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (!node.isObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return node;
    }

    private static JsonNode array(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (!node.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return node;
    }

    private static String text(JsonNode parent, String field) {
        String value = textAllowBlank(parent, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank text");
        }
        return value;
    }

    private static String textAllowBlank(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (!node.isTextual()) {
            throw new IllegalArgumentException(field + " must be text");
        }
        return node.asText().trim();
    }

    private static int integer(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (!node.isInt() && !node.isLong()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return node.intValue();
    }

    private static BigDecimal decimalOrNull(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (node.isNull()) {
            return null;
        }
        if (!node.isNumber()) {
            throw new IllegalArgumentException(field + " must be numeric or null");
        }
        return node.decimalValue();
    }

    private static List<String> stringList(JsonNode parent, String field) {
        JsonNode array = array(parent, field);
        List<String> values = new java.util.ArrayList<>();
        for (JsonNode node : array) {
            if (!node.isTextual() || node.asText().isBlank()) {
                throw new IllegalArgumentException(field + " contains invalid text");
            }
            values.add(node.asText().trim());
        }
        if (new LinkedHashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException(field + " contains duplicate values");
        }
        return List.copyOf(values);
    }

    private static void requireFields(JsonNode node, Set<String> expected) {
        requireAllowedFields(node, expected);
        requirePresentFields(node, expected);
    }

    private static void requireAllowedFields(JsonNode node, Set<String> allowed) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("typed explanation node must be an object");
        }
        Set<String> actual = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!allowed.containsAll(actual)) {
            throw new IllegalArgumentException(
                    "typed explanation contains cross-type or unknown fields");
        }
    }

    private static void requirePresentFields(JsonNode node, Set<String> required) {
        for (String field : required) {
            if (!node.has(field)) {
                throw new IllegalArgumentException(
                        "typed explanation is missing field " + field);
            }
        }
    }

    public record ObjectiveExplanationArtifact(
            String schemaVersion,
            CanonicalQuestionType questionType,
            String meaningVi,
            String correctReasonVi,
            List<ArtifactEvidence> evidence,
            List<EvidenceTranslation> relevantTranslations,
            TypeExplanation typeExplanation,
            Long artifactId
    ) {
        public ObjectiveExplanationArtifact {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            relevantTranslations = relevantTranslations == null
                    ? List.of()
                    : List.copyOf(relevantTranslations);
        }
    }

    public sealed interface TypeExplanation
            permits SingleChoiceExplanation, FillBlankExplanation, TfngExplanation {
    }

    public record SingleChoiceExplanation(
            String correctOptionId,
            List<OptionRationale> optionRationales
    ) implements TypeExplanation {
        public SingleChoiceExplanation {
            optionRationales = optionRationales == null
                    ? List.of()
                    : List.copyOf(optionRationales);
        }
    }

    public record OptionRationale(
            String optionId,
            String reasonVi,
            List<String> evidenceIds
    ) {
        public OptionRationale {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }

    public record FillBlankExplanation(
            List<BlankExplanation> blanks
    ) implements TypeExplanation {
        public FillBlankExplanation {
            blanks = blanks == null ? List.of() : List.copyOf(blanks);
        }
    }

    public record BlankExplanation(
            String blankId,
            String contextExplanationVi,
            String semanticConstraintVi,
            String grammarConstraintVi,
            String registerConstraintVi,
            List<String> evidenceIds
    ) {
        public BlankExplanation {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }

    public record TfngExplanation(
            String relationExplanationVi,
            String whyTrueVi,
            String whyFalseVi,
            String whyNotGivenVi,
            String missingInformationVi
    ) implements TypeExplanation {
        public String reasonFor(String label) {
            return switch (label) {
                case "TRUE" -> whyTrueVi;
                case "FALSE" -> whyFalseVi;
                case "NOT_GIVEN" -> whyNotGivenVi;
                default -> throw new IllegalArgumentException("Unknown TFNG label");
            };
        }
    }

    public sealed interface ArtifactEvidence permits TextEvidence, ImageEvidence {
        String evidenceId();
        String kind();
        String purpose();
        String sourceRole();
    }

    public record TextEvidence(
            String evidenceId,
            String kind,
            String purpose,
            String sourceRole,
            String exactQuoteKo,
            int startOffset,
            int endOffset
    ) implements ArtifactEvidence {
    }

    public record ImageEvidence(
            String evidenceId,
            String purpose,
            String sourceRole,
            String assetDigest,
            int imageIndex,
            String regionMode,
            BigDecimal x,
            BigDecimal y,
            BigDecimal width,
            BigDecimal height
    ) implements ArtifactEvidence {
        @Override
        public String kind() {
            return "IMAGE_REGION";
        }
    }

    public record EvidenceTranslation(
            String evidenceId,
            String translationVi
    ) {
        public EvidenceTranslation {
            if (blank(evidenceId) || blank(translationVi)) {
                throw new IllegalArgumentException(
                        "Evidence translation is incomplete");
            }
        }
    }

    private record CommonExplanation(
            String meaningVi,
            String correctReasonVi,
            List<ArtifactEvidence> evidence,
            Set<String> evidenceIds,
            List<EvidenceTranslation> translations
    ) {
    }

    private static String optionLabel(int index, String mode) {
        return "ALPHA".equalsIgnoreCase(mode)
                ? String.valueOf((char) ('A' + index))
                : String.valueOf(index + 1);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
