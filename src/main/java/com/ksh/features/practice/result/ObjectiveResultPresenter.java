package com.ksh.features.practice.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeQuestionGroupVersion;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.AssessmentScoreResult;
import com.ksh.features.practice.assessment.AssessmentScoreStatus;
import com.ksh.features.practice.assessment.AssessmentScoringEngine;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.LearnerAnswer;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveResultPayload;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveDetailPayload;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveBlankResult;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveConstructDescriptor;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveEvidenceKind;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveEvidenceRef;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveEvidenceTranslation;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveExplanation;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveFillBlankDetail;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveImageEvidenceRef;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveOptionResult;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveOptionState;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveQuestionCore;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveQuestionDetail;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveResultGroup;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveSingleChoiceDetail;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveSourceGroup;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveTextEvidenceRef;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveTfngAlternative;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveTfngDetail;
import com.ksh.features.practice.dto.PracticeDtos.PracticeAttemptResultView;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailPayload;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveResultTypeBreakdown;
import com.ksh.features.practice.dto.PracticeDtos.ResultAnswerDistribution;
import com.ksh.features.practice.dto.PracticeDtos.ResultFeedbackAvailability;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService.ArtifactEvidence;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService.BlankExplanation;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService.FillBlankExplanation;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService.ImageEvidence;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService.ObjectiveExplanationArtifact;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService.OptionRationale;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService.SingleChoiceExplanation;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService.TextEvidence;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService.TfngExplanation;
import com.ksh.features.practice.dto.PracticeDtos;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
final class ObjectiveResultPresenter implements PracticeResultPresenter, PracticeResultDetailPresenter {

    private static final String UNSCORABLE_TYPE = "UNSCORABLE";
    private static final String AI_PROVENANCE =
            "Artifact AI đã liên kết với phiên bản câu hỏi bất biến";
    private static final String UNAVAILABLE_PROVENANCE =
            "Không có artifact AI hợp lệ cho phiên bản câu hỏi này";
    private static final String CONSTRUCT_REGISTRY_STATE =
            "DEFERRED_PRE_PHASE_14_REGISTRY";
    private static final String CONSTRUCT_REGISTRY_NOTE =
            "Chưa hiển thị construct chip: artifact hiện tại không mang mã typed thuộc registry VI/KO đã duyệt.";
    private static final String NORMALIZATION_POLICY =
            "NFC + bỏ khoảng trắng đầu/cuối + gộp khoảng trắng + không phân biệt hoa/thường";

    private final AssessmentContractCodec contractCodec;
    private final QuestionTypeResolver typeResolver;
    private final AssessmentScoringEngine scoringEngine;
    private final QuestionExplanationReadService explanationReadService;
    private final ObjectMapper objectMapper;

    ObjectiveResultPresenter(
            AssessmentContractCodec contractCodec,
            QuestionTypeResolver typeResolver,
            AssessmentScoringEngine scoringEngine,
            QuestionExplanationReadService explanationReadService,
            ObjectMapper objectMapper) {
        this.contractCodec = contractCodec;
        this.typeResolver = typeResolver;
        this.scoringEngine = scoringEngine;
        this.explanationReadService = explanationReadService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String skill) {
        return "READING".equals(skill) || "LISTENING".equals(skill);
    }

    @Override
    public Presentation present(PracticeResultContext context) {
        Map<String, TypeAccumulator> byType = new LinkedHashMap<>();
        StateAccumulator overall = new StateAccumulator();

        for (PracticeQuestionVersion question : context.snapshot().questions()) {
            CanonicalQuestionType canonicalType;
            try {
                canonicalType = typeResolver.resolve(question.getQuestionType());
            } catch (IllegalArgumentException | IllegalStateException exception) {
                byType.computeIfAbsent(
                                UNSCORABLE_TYPE,
                                ignored -> new TypeAccumulator(UNSCORABLE_TYPE))
                        .addUnscorable();
                overall.unscorable++;
                continue;
            }
            String canonicalCode = canonicalType.name();
            TypeAccumulator type = byType.computeIfAbsent(
                    canonicalCode,
                    ignored -> new TypeAccumulator(canonicalCode));
            try {
                AssessmentScoreResult score = score(question, canonicalType,
                        context.answers().getOrDefault(String.valueOf(question.getQuestionId()), ""));
                type.add(score);
                overall.add(score.status());
            } catch (IllegalArgumentException | IllegalStateException exception) {
                type.unscorable++;
                overall.unscorable++;
            }
        }

        List<ObjectiveResultTypeBreakdown> breakdown = new ArrayList<>();
        for (TypeAccumulator type : byType.values()) {
            breakdown.add(type.toView());
        }
        ResultAnswerDistribution distribution = overall.toDistribution(context.snapshot().questions().size());
        ResultFeedbackAvailability feedback = learnerFeedback(
                explanationReadService.availability(
                        context.snapshot().questions().stream()
                                .map(PracticeQuestionVersion::getId)
                                .toList()));
        return new Presentation(context.score(), distribution, feedback, new ObjectiveResultPayload(breakdown));
    }

    @Override
    public ResultDetailPayload presentDetail(
            PracticeResultContext context,
            PracticeAttemptResultView overview,
            Long questionId
    ) {
        if (!(overview.payload() instanceof ObjectiveResultPayload objective)) {
            throw new IllegalStateException("Objective Result Detail requires an objective payload.");
        }
        String optionLabelMode = PracticeDtos.getOptionLabelMode(
                context.snapshot().setVersion().getTitle(),
                context.snapshot().setVersion().getMetadataJson());
        List<PracticeQuestionVersion> questions = context.snapshot().questions().stream()
                .sorted(Comparator
                        .comparing(
                                PracticeQuestionVersion::getDisplayOrder,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(
                                PracticeQuestionVersion::getQuestionNo,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(PracticeQuestionVersion::getId))
                .toList();
        Map<Long, Integer> stableOrderByQuestionVersionId = new LinkedHashMap<>();
        for (int index = 0; index < questions.size(); index++) {
            stableOrderByQuestionVersionId.put(questions.get(index).getId(), index + 1);
        }
        List<ObjectiveResultGroup> groups = resultGroups(
                context, questions, stableOrderByQuestionVersionId, optionLabelMode);
        return new ObjectiveDetailPayload(
                overview.score(),
                overview.answers(),
                overview.feedback(),
                objective,
                groups,
                CONSTRUCT_REGISTRY_STATE,
                CONSTRUCT_REGISTRY_NOTE);
    }

    private List<ObjectiveResultGroup> resultGroups(
            PracticeResultContext context,
            List<PracticeQuestionVersion> orderedQuestions,
            Map<Long, Integer> stableOrderByQuestionVersionId,
            String optionLabelMode) {
        List<ObjectiveResultGroup> groups = new ArrayList<>();
        List<PracticeQuestionGroupVersion> orderedGroups =
                context.snapshot().groups().stream()
                        .sorted(Comparator
                                .comparing(
                                        PracticeQuestionGroupVersion::getDisplayOrder,
                                        Comparator.nullsLast(Integer::compareTo))
                                .thenComparing(PracticeQuestionGroupVersion::getId))
                        .toList();
        java.util.Set<Long> knownGroupVersionIds = orderedGroups.stream()
                .map(PracticeQuestionGroupVersion::getId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<Long> danglingQuestionVersionIds = orderedQuestions.stream()
                .filter(question -> question.getGroupVersionId() != null)
                .filter(question -> !knownGroupVersionIds.contains(
                        question.getGroupVersionId()))
                .map(PracticeQuestionVersion::getId)
                .toList();
        if (!danglingQuestionVersionIds.isEmpty()) {
            throw new IllegalStateException(
                    "Objective Result Detail immutable group ownership is missing for "
                            + "question versions " + danglingQuestionVersionIds + ".");
        }
        int nextFallbackOrder = 0;
        for (PracticeQuestionGroupVersion group : orderedGroups) {
            int groupOrder = nonNegativeOrder(
                    group.getDisplayOrder(), nextFallbackOrder);
            nextFallbackOrder = Math.max(nextFallbackOrder, groupOrder + 1);
            List<PracticeQuestionVersion> groupQuestions = orderedQuestions.stream()
                    .filter(question -> java.util.Objects.equals(
                            group.getId(), question.getGroupVersionId()))
                    .toList();
            if (groupQuestions.isEmpty()) {
                continue;
            }
            groups.add(resultGroup(
                    context,
                    group,
                    groupQuestions,
                    stableOrderByQuestionVersionId,
                    optionLabelMode,
                    groupOrder,
                    group.getGroupLabel(),
                    false));
        }

        List<PracticeQuestionVersion> legacyQuestions = orderedQuestions.stream()
                .filter(question -> question.getGroupVersionId() == null)
                .toList();
        if (!legacyQuestions.isEmpty()) {
            groups.add(resultGroup(
                    context,
                    null,
                    legacyQuestions,
                    stableOrderByQuestionVersionId,
                    optionLabelMode,
                    nextFallbackOrder,
                    "Nhóm dữ liệu cũ chưa phân nhóm",
                    true));
        }
        if (groups.isEmpty()) {
            throw new IllegalStateException(
                    "Objective Result Detail has no immutable question group ownership.");
        }
        return List.copyOf(groups);
    }

    private ObjectiveResultGroup resultGroup(
            PracticeResultContext context,
            PracticeQuestionGroupVersion group,
            List<PracticeQuestionVersion> questions,
            Map<Long, Integer> stableOrderByQuestionVersionId,
            String optionLabelMode,
            int groupOrder,
            String groupLabel,
            boolean legacyFallback) {
        String sourceId = group == null
                ? "objective-source-legacy-ungrouped"
                : "objective-source-group-" + group.getId();
        List<ObjectiveQuestionDetail> details = new ArrayList<>();
        for (int index = 0; index < questions.size(); index++) {
            PracticeQuestionVersion question = questions.get(index);
            details.add(questionDetail(
                    context,
                    question,
                    stableOrderByQuestionVersionId.get(question.getId()),
                    group,
                    groupOrder,
                    nonNegativeOrder(question.getDisplayOrder(), index),
                    groupLabel,
                    sourceId,
                    optionLabelMode));
        }
        ObjectiveSourceGroup source = sourceGroup(
                context,
                group,
                questions,
                groupOrder,
                groupLabel,
                legacyFallback,
                sourceId);
        return new ObjectiveResultGroup(
                legacyFallback ? "legacy-ungrouped" : "group-version-" + group.getId(),
                group == null ? null : group.getId(),
                group == null ? null : group.getGroupId(),
                groupOrder,
                groupLabel,
                legacyFallback,
                source,
                details);
    }

    private ObjectiveQuestionDetail questionDetail(
            PracticeResultContext context,
            PracticeQuestionVersion question,
            int stableOrder,
            PracticeQuestionGroupVersion group,
            int groupOrder,
            int questionOrder,
            String groupLabel,
            String sourceId,
            String optionLabelMode) {
        CanonicalQuestionType type = typeResolver.resolve(question.getQuestionType());
        if (type == CanonicalQuestionType.ESSAY || type == CanonicalQuestionType.SPEAKING) {
            throw new IllegalStateException(
                    "Objective Result Detail encountered a subjective immutable question.");
        }
        QuestionContent content = content(question, type);
        AnswerSpec answerSpec = answerSpec(question, content);
        String rawAnswer = context.answers()
                .getOrDefault(String.valueOf(question.getQuestionId()), "");
        LearnerAnswer learnerAnswer = learnerAnswer(question, content, rawAnswer);
        if (answerSpec.questionType() != type || learnerAnswer.questionType() != type) {
            throw new IllegalStateException(
                    "Objective Result Detail immutable discriminator mismatch.");
        }
        AssessmentScoreResult score = scoringEngine.score(
                answerSpec, learnerAnswer, question.getPoints());
        Optional<ObjectiveExplanationArtifact> artifact =
                explanationReadService.readObjective(question.getId(), type);
        ObjectiveQuestionCore core = new ObjectiveQuestionCore(
                question.getId(),
                question.getQuestionId(),
                question.getQuestionNo(),
                stableOrder,
                group == null ? null : group.getId(),
                group == null ? null : group.getGroupId(),
                groupOrder,
                questionOrder,
                groupLabel,
                sourceId,
                "objective-question-" + question.getId(),
                question.getPrompt(),
                score.status().name(),
                score.earnedPoints(),
                score.possiblePoints(),
                "Bài làm đã khóa của người học",
                "Đáp án chính thức từ answer spec bất biến",
                question.getExplanation(),
                "Giải thích của giảng viên trong phiên bản câu hỏi bất biến");
        ObjectiveExplanation explanation = explanation(artifact);
        return switch (type) {
            case SINGLE_CHOICE -> singleChoiceDetail(
                    core, content, answerSpec, learnerAnswer, artifact,
                    explanation, optionLabelMode);
            case FILL_BLANK -> fillBlankDetail(
                    core, content, answerSpec, learnerAnswer, artifact, explanation);
            case TRUE_FALSE_NOT_GIVEN -> tfngDetail(
                    core, answerSpec, learnerAnswer, artifact, explanation);
            case ESSAY, SPEAKING -> throw new IllegalStateException(
                    "Subjective question cannot use Objective Result Detail.");
        };
    }

    private ObjectiveSingleChoiceDetail singleChoiceDetail(
            ObjectiveQuestionCore core,
            QuestionContent content,
            AnswerSpec answerSpec,
            LearnerAnswer learnerAnswer,
            Optional<ObjectiveExplanationArtifact> artifact,
            ObjectiveExplanation explanation,
            String optionLabelMode) {
        String officialId = answerSpec.correctOptionIds().get(0);
        String learnerId = learnerAnswer.selectedOptionIds().size() == 1
                ? learnerAnswer.selectedOptionIds().get(0)
                : null;
        Map<String, OptionRationale> rationales = artifact
                .map(ObjectiveExplanationArtifact::typeExplanation)
                .filter(SingleChoiceExplanation.class::isInstance)
                .map(SingleChoiceExplanation.class::cast)
                .map(value -> value.optionRationales().stream().collect(Collectors.toMap(
                        OptionRationale::optionId,
                        Function.identity())))
                .orElse(Map.of());
        List<ObjectiveOptionResult> options = new ArrayList<>();
        for (int index = 0; index < content.options().size(); index++) {
            QuestionContent.Option option = content.options().get(index);
            String canonicalId = "option_" + (index + 1);
            OptionRationale rationale = rationales.get(canonicalId);
            boolean selected = option.id().equals(learnerId);
            boolean correct = option.id().equals(officialId);
            boolean revealed = !"PENDING_AI".equals(core.scoreState());
            options.add(new ObjectiveOptionResult(
                    option.id(),
                    optionLabel(index, optionLabelMode),
                    option.text(),
                    option.imageReference(),
                    selected,
                    correct,
                    optionState(selected, correct, revealed),
                    rationale == null ? "" : rationale.reasonVi(),
                    rationale == null ? UNAVAILABLE_PROVENANCE : AI_PROVENANCE,
                    rationale == null ? List.of() : rationale.evidenceIds()));
        }
        return new ObjectiveSingleChoiceDetail(core, options, explanation);
    }

    private ObjectiveFillBlankDetail fillBlankDetail(
            ObjectiveQuestionCore core,
            QuestionContent content,
            AnswerSpec answerSpec,
            LearnerAnswer learnerAnswer,
            Optional<ObjectiveExplanationArtifact> artifact,
            ObjectiveExplanation explanation) {
        Map<String, AnswerSpec.BlankAnswer> specs = answerSpec.blanks().stream()
                .collect(Collectors.toMap(
                        AnswerSpec.BlankAnswer::blankId,
                        Function.identity()));
        Map<String, BlankExplanation> explanations = artifact
                .map(ObjectiveExplanationArtifact::typeExplanation)
                .filter(FillBlankExplanation.class::isInstance)
                .map(FillBlankExplanation.class::cast)
                .map(value -> value.blanks().stream().collect(Collectors.toMap(
                        BlankExplanation::blankId,
                        Function.identity())))
                .orElse(Map.of());
        List<ObjectiveBlankResult> blanks = new ArrayList<>();
        for (int index = 0; index < content.blanks().size(); index++) {
            QuestionContent.Blank blank = content.blanks().get(index);
            AnswerSpec.BlankAnswer spec = specs.get(blank.id());
            if (spec == null) {
                throw new IllegalStateException(
                        "Immutable fill-blank answer authority is incomplete.");
            }
            String canonicalId = "blank_" + (index + 1);
            BlankExplanation ai = explanations.get(canonicalId);
            String learnerValue = learnerAnswer.blankAnswers()
                    .getOrDefault(blank.id(), "");
            boolean correct = !learnerValue.isBlank() && spec.acceptedValues().stream()
                    .map(ObjectiveResultPresenter::normalizeAnswer)
                    .anyMatch(normalizeAnswer(learnerValue)::equals);
            blanks.add(new ObjectiveBlankResult(
                    blank.id(),
                    blank.prompt(),
                    learnerValue,
                    spec.acceptedValues(),
                    NORMALIZATION_POLICY,
                    correct,
                    ai == null ? "" : ai.contextExplanationVi(),
                    ai == null ? "" : ai.semanticConstraintVi(),
                    ai == null ? "" : ai.grammarConstraintVi(),
                    ai == null ? "" : ai.registerConstraintVi(),
                    ai == null ? UNAVAILABLE_PROVENANCE : AI_PROVENANCE,
                    ai == null ? List.of() : ai.evidenceIds()));
        }
        return new ObjectiveFillBlankDetail(core, blanks, explanation);
    }

    private ObjectiveTfngDetail tfngDetail(
            ObjectiveQuestionCore core,
            AnswerSpec answerSpec,
            LearnerAnswer learnerAnswer,
            Optional<ObjectiveExplanationArtifact> artifact,
            ObjectiveExplanation explanation) {
        String official = normalizeTfng(answerSpec.correctValue());
        String learner = blank(learnerAnswer.selectedValue())
                ? ""
                : normalizeTfng(learnerAnswer.selectedValue());
        TfngExplanation ai = artifact
                .map(ObjectiveExplanationArtifact::typeExplanation)
                .filter(TfngExplanation.class::isInstance)
                .map(TfngExplanation.class::cast)
                .orElse(null);
        List<ObjectiveTfngAlternative> alternatives = new ArrayList<>();
        for (String label : List.of("TRUE", "FALSE", "NOT_GIVEN")) {
            if (!official.equals(label)) {
                alternatives.add(new ObjectiveTfngAlternative(
                        label,
                        tfngRelation(label),
                        ai == null
                                ? "Chưa có giải thích được kiểm chứng cho nhãn này."
                                : ai.reasonFor(label),
                        ai == null ? UNAVAILABLE_PROVENANCE : AI_PROVENANCE));
            }
        }
        return new ObjectiveTfngDetail(
                core,
                core.prompt(),
                learner,
                official,
                tfngRelation(official),
                ai == null ? "" : ai.relationExplanationVi(),
                ai == null ? "" : ai.missingInformationVi(),
                alternatives,
                explanation);
    }

    private ObjectiveExplanation explanation(
            Optional<ObjectiveExplanationArtifact> artifact) {
        if (artifact.isEmpty()) {
            return new ObjectiveExplanation(
                    "UNAVAILABLE",
                    "Chưa có lời giải đáp ứng hợp đồng bằng chứng cho câu này",
                    "",
                    "",
                    "",
                    UNAVAILABLE_PROVENANCE,
                    List.of(),
                    List.of(),
                    List.of(),
                    CONSTRUCT_REGISTRY_STATE);
        }
        ObjectiveExplanationArtifact value = artifact.orElseThrow();
        List<ObjectiveEvidenceRef> evidence = value.evidence().stream()
                .map(ObjectiveResultPresenter::evidence)
                .toList();
        List<ObjectiveEvidenceTranslation> translations =
                value.relevantTranslations().stream()
                        .map(translation -> new ObjectiveEvidenceTranslation(
                                translation.evidenceId(),
                                "Dịch đoạn liên quan",
                                translation.translationVi(),
                                AI_PROVENANCE))
                        .toList();
        return new ObjectiveExplanation(
                "READY",
                "Lời giải đã được kiểm chứng theo snapshot",
                value.schemaVersion(),
                value.meaningVi(),
                value.correctReasonVi(),
                AI_PROVENANCE,
                evidence,
                translations,
                List.<ObjectiveConstructDescriptor>of(),
                CONSTRUCT_REGISTRY_STATE);
    }

    private static ObjectiveEvidenceRef evidence(ArtifactEvidence evidence) {
        if (evidence instanceof TextEvidence text) {
            return new ObjectiveTextEvidenceRef(
                    text.evidenceId(),
                    ObjectiveEvidenceKind.valueOf(text.kind()),
                    evidencePurposeLabel(text.purpose()),
                    text.sourceRole(),
                    text.exactQuoteKo(),
                    text.startOffset(),
                    text.endOffset());
        }
        if (evidence instanceof ImageEvidence image) {
            return new ObjectiveImageEvidenceRef(
                    image.evidenceId(),
                    ObjectiveEvidenceKind.IMAGE_REGION,
                    evidencePurposeLabel(image.purpose()),
                    image.sourceRole(),
                    image.assetDigest(),
                    image.imageIndex(),
                    image.regionMode(),
                    image.x(),
                    image.y(),
                    image.width(),
                    image.height());
        }
        throw new IllegalArgumentException("Unsupported objective evidence subtype.");
    }

    private ObjectiveSourceGroup sourceGroup(
            PracticeResultContext context,
            PracticeQuestionGroupVersion group,
            List<PracticeQuestionVersion> questions,
            int groupOrder,
            String groupLabel,
            boolean legacyFallback,
            String sourceId) {
        boolean listening = "LISTENING".equals(context.attempt().getSkill());
        boolean transcriptApproved = listening && transcriptApproved(group);
        return new ObjectiveSourceGroup(
                sourceId,
                group == null ? null : group.getId(),
                group == null ? null : group.getGroupId(),
                groupOrder,
                groupLabel,
                legacyFallback,
                listening ? "LISTENING_AUDIO" : "READING_PASSAGE",
                group == null
                        ? context.snapshot().sectionVersion().getInstructions()
                        : group.getInstruction(),
                listening || group == null ? "" : group.getPassageText(),
                transcriptApproved ? group.getTranscriptText() : "",
                group == null ? "" : group.getImageUrl(),
                listening && group != null ? group.getAudioUrl() : "",
                legacyFallback
                        ? "LEGACY_UNGROUPED_IMMUTABLE_SNAPSHOT"
                        : sourceProvenance(group),
                transcriptApproved
                        ? "Chỉ dùng để chứng minh nội dung ngôn ngữ; không dùng cho nhận định âm học."
                        : "Không có bản chép lời được phê duyệt.",
                questions.stream()
                        .map(PracticeQuestionVersion::getId)
                        .toList());
    }

    private String sourceProvenance(PracticeQuestionGroupVersion group) {
        if (group == null || blank(group.getStimulusProvenanceJson())) {
            return "PUBLISHED_IMMUTABLE_SNAPSHOT";
        }
        try {
            String source = objectMapper.readTree(group.getStimulusProvenanceJson())
                    .path("source").asText("").trim();
            return source.isBlank() ? "PUBLISHED_IMMUTABLE_SNAPSHOT" : source;
        } catch (Exception exception) {
            return "PUBLISHED_IMMUTABLE_SNAPSHOT";
        }
    }

    private boolean transcriptApproved(PracticeQuestionGroupVersion group) {
        if (group == null || blank(group.getTranscriptText())) {
            return false;
        }
        if (blank(group.getStimulusProvenanceJson())) {
            return true;
        }
        try {
            return objectMapper.readTree(group.getStimulusProvenanceJson())
                    .path("approved").asBoolean(false);
        } catch (Exception exception) {
            return false;
        }
    }

    private QuestionContent content(
            PracticeQuestionVersion question,
            CanonicalQuestionType type) {
        return blank(question.getQuestionContentJson())
                ? contractCodec.adaptLegacyContent(
                        question.getOptionsJson(), question.getQuestionType())
                : contractCodec.readQuestionContent(
                        question.getQuestionContentJson(), type);
    }

    private AnswerSpec answerSpec(
            PracticeQuestionVersion question,
            QuestionContent content) {
        return blank(question.getAnswerSpecJson())
                ? contractCodec.adaptLegacyAnswerSpec(
                        question.getQuestionType(), question.getAnswerKey(), content)
                : contractCodec.readAnswerSpec(question.getAnswerSpecJson(), content);
    }

    private LearnerAnswer learnerAnswer(
            PracticeQuestionVersion question,
            QuestionContent content,
            String rawAnswer) {
        return !blank(rawAnswer) && rawAnswer.trim().startsWith("{")
                ? contractCodec.readLearnerAnswer(rawAnswer)
                : contractCodec.adaptLegacyLearnerAnswer(
                        question.getQuestionType(), rawAnswer, content);
    }

    private static int nonNegativeOrder(Integer persistedOrder, int fallback) {
        return persistedOrder == null || persistedOrder < 0
                ? Math.max(0, fallback)
                : persistedOrder;
    }

    private static ObjectiveOptionState optionState(
            boolean selected,
            boolean correct,
            boolean revealed) {
        if (!revealed) {
            return selected
                    ? ObjectiveOptionState.USER_SELECTED_PENDING
                    : ObjectiveOptionState.UNSELECTED_PENDING;
        }
        if (correct) {
            return ObjectiveOptionState.CORRECT;
        }
        return selected
                ? ObjectiveOptionState.SELECTED_INCORRECT
                : ObjectiveOptionState.UNSELECTED_INCORRECT;
    }

    private static String optionLabel(int index, String mode) {
        return "ALPHA".equalsIgnoreCase(mode)
                ? String.valueOf((char) ('A' + index))
                : String.valueOf(index + 1);
    }

    private static String evidencePurposeLabel(String purpose) {
        return switch (purpose) {
            case "ANSWER_RATIONALE" -> "Chứng minh đáp án";
            case "OPTION_ELIMINATION" -> "Loại phương án sai";
            case "BLANK_CONSTRAINT" -> "Giải thích ràng buộc ô trống";
            case "SUPPORTING" -> "Bằng chứng ủng hộ mệnh đề";
            case "CONTRASTING" -> "Bằng chứng mâu thuẫn với mệnh đề";
            case "MISSING_INFORMATION" -> "Xác định thông tin còn thiếu";
            default -> throw new IllegalArgumentException(
                    "Unknown objective evidence purpose.");
        };
    }

    private static String tfngRelation(String value) {
        return switch (value) {
            case "TRUE" -> "ENTAILED";
            case "FALSE" -> "CONTRADICTED";
            case "NOT_GIVEN" -> "NOT_STATED";
            default -> throw new IllegalArgumentException(
                    "Unknown authoritative TFNG value.");
        };
    }

    private static String normalizeTfng(String value) {
        String normalized = normalizeAnswer(value);
        if (!List.of("TRUE", "FALSE", "NOT_GIVEN").contains(normalized)) {
            throw new IllegalArgumentException("Invalid immutable TFNG value.");
        }
        return normalized;
    }

    private static String normalizeAnswer(String value) {
        return value == null
                ? ""
                : java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFC)
                        .trim()
                        .replaceAll("\\s+", " ")
                        .toUpperCase(java.util.Locale.ROOT);
    }

    private static ResultFeedbackAvailability learnerFeedback(ResultFeedbackAvailability availability) {
        String label = switch (availability.state()) {
            case "READY" -> "Giải thích đáp án đã sẵn sàng";
            case "PARTIAL" -> "Một phần giải thích đáp án đã sẵn sàng";
            case "PENDING" -> "Giải thích đáp án đang được chuẩn bị";
            case "FAILED" -> "Chưa thể cung cấp giải thích đáp án";
            case "UNAVAILABLE" -> "Đề này hiện chưa có giải thích đáp án";
            default -> blank(availability.label())
                    ? "Trạng thái giải thích chưa xác định"
                    : availability.label();
        };
        return new ResultFeedbackAvailability(
                availability.state(), label, availability.readyCount(), availability.totalCount());
    }

    private AssessmentScoreResult score(
            PracticeQuestionVersion question,
            CanonicalQuestionType type,
            String rawAnswer) {
        QuestionContent content = blank(question.getQuestionContentJson())
                ? contractCodec.adaptLegacyContent(question.getOptionsJson(), question.getQuestionType())
                : contractCodec.readQuestionContent(question.getQuestionContentJson(), type);
        AnswerSpec answerSpec = blank(question.getAnswerSpecJson())
                ? contractCodec.adaptLegacyAnswerSpec(question.getQuestionType(), question.getAnswerKey(), content)
                : contractCodec.readAnswerSpec(question.getAnswerSpecJson(), content);
        LearnerAnswer learnerAnswer = !blank(rawAnswer) && rawAnswer.trim().startsWith("{")
                ? contractCodec.readLearnerAnswer(rawAnswer)
                : contractCodec.adaptLegacyLearnerAnswer(question.getQuestionType(), rawAnswer, content);
        return scoringEngine.score(answerSpec, learnerAnswer, question.getPoints());
    }

    private static String questionTypeLabel(String type) {
        return switch (type == null ? "" : type) {
            case "SINGLE_CHOICE" -> "Trắc nghiệm một đáp án";
            case "TRUE_FALSE_NOT_GIVEN" -> "Đúng, sai hoặc không có thông tin";
            case "FILL_BLANK" -> "Điền từ";
            case UNSCORABLE_TYPE -> "Loại câu hỏi không thể chấm";
            default -> "Loại câu hỏi không xác định";
        };
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static BigDecimal percentage(BigDecimal earned, BigDecimal possible) {
        return possible == null || possible.signum() <= 0 || earned == null
                ? null
                : earned.multiply(BigDecimal.valueOf(100))
                        .divide(possible, 2, RoundingMode.HALF_UP);
    }

    private static final class TypeAccumulator {
        private final String questionType;
        private int total;
        private int correct;
        private int partial;
        private int incorrect;
        private int notAnswered;
        private int pending;
        private int unscorable;
        private int scoredDenominator;
        private BigDecimal earned = BigDecimal.ZERO;
        private BigDecimal possible = BigDecimal.ZERO;

        private TypeAccumulator(String questionType) {
            this.questionType = questionType;
        }

        private void add(AssessmentScoreResult score) {
            total++;
            switch (score.status()) {
                case CORRECT -> correct++;
                case PARTIALLY_CORRECT -> partial++;
                case INCORRECT -> incorrect++;
                case NOT_ANSWERED -> notAnswered++;
                case PENDING_AI -> pending++;
            }
            if (score.status() != AssessmentScoreStatus.PENDING_AI) {
                scoredDenominator++;
                earned = earned.add(score.earnedPoints());
                possible = possible.add(score.possiblePoints());
            }
        }

        private ObjectiveResultTypeBreakdown toView() {
            return new ObjectiveResultTypeBreakdown(
                    questionType,
                    questionTypeLabel(questionType),
                    new ResultAnswerDistribution(correct, partial, incorrect, notAnswered, pending,
                            unscorable, total + unscorable, scoredDenominator),
                    earned,
                    possible,
                    percentage(earned, possible));
        }

        private void addUnscorable() {
            unscorable++;
        }
    }

    private static final class StateAccumulator {
        private int correct;
        private int partial;
        private int incorrect;
        private int notAnswered;
        private int pending;
        private int unscorable;

        private void add(AssessmentScoreStatus status) {
            switch (status) {
                case CORRECT -> correct++;
                case PARTIALLY_CORRECT -> partial++;
                case INCORRECT -> incorrect++;
                case NOT_ANSWERED -> notAnswered++;
                case PENDING_AI -> pending++;
            }
        }

        private ResultAnswerDistribution toDistribution(int total) {
            int denominator = correct + partial + incorrect + notAnswered;
            return new ResultAnswerDistribution(correct, partial, incorrect, notAnswered,
                    pending, unscorable, total, denominator);
        }
    }
}
