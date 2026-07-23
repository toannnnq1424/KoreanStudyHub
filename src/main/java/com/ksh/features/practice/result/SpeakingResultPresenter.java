package com.ksh.features.practice.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationResult;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationStatus;
import com.ksh.features.practice.ai.speaking.SpeakingEvidenceMode;
import com.ksh.features.practice.ai.speaking.SpeakingEvidenceSource;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluatorCapability;
import com.ksh.features.practice.ai.speaking.SpeakingFeedbackCompatibilityReader;
import com.ksh.features.practice.ai.speaking.SpeakingRubricCriterion;
import com.ksh.features.practice.ai.writing.WritingFeedbackCompatibilityReader;
import com.ksh.features.practice.ai.writing.WritingFeedbackViewMapper;
import com.ksh.features.practice.dto.PracticeDtos.ResultAnswerDistribution;
import com.ksh.features.practice.dto.PracticeDtos.PracticeAttemptResultView;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailDiagnosticFinding;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailFilterChip;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailPayload;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailPolarity;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailScoreCriterion;
import com.ksh.features.practice.dto.PracticeDtos.ResultEvaluationBand;
import com.ksh.features.practice.dto.PracticeDtos.ResultFeedbackAvailability;
import com.ksh.features.practice.dto.PracticeDtos.ResultScoreSummary;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingAnswerArtifact;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingActionPlanView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingCriterionResult;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingDiagnosticGroup;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingEvidenceView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingMediaView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingPhraseRewriteView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingResultPayload;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingDetailPayload;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingTaskDetail;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingUpgradeView;
import com.ksh.features.practice.dto.PracticeDtos.WritingFeedbackView;
import com.ksh.features.practice.dto.PracticeDtos.WritingFindingView;
import com.ksh.features.practice.service.PracticeSpeakingMediaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
final class SpeakingResultPresenter implements PracticeResultPresenter, PracticeResultDetailPresenter {

    private static final String CONTRACT_FIELD = "_contract";
    private static final String AI_CONTRACT = "speaking_ai_v1";
    private static final String MIXED_CONTRACT = "speaking_mixed_v1";
    private static final String FEEDBACK_BY_QUESTION = "speaking_feedback_by_question";
    private static final String ESSAY_FEEDBACK_BY_QUESTION = "essay_feedback_by_question";

    private final ObjectMapper objectMapper;
    private final SpeakingFeedbackCompatibilityReader feedbackReader;
    private final WritingFeedbackCompatibilityReader writingFeedbackReader;
    private final WritingFeedbackViewMapper writingFeedbackMapper;
    private final PracticeSpeakingMediaService speakingMediaService;
    private final boolean speakingMediaPlaybackEnabled;

    @Autowired
    SpeakingResultPresenter(
            ObjectMapper objectMapper,
            SpeakingFeedbackCompatibilityReader feedbackReader,
            WritingFeedbackCompatibilityReader writingFeedbackReader,
            WritingFeedbackViewMapper writingFeedbackMapper,
            PracticeSpeakingMediaService speakingMediaService,
            @Value("${app.practice.speaking-media.playback-api-enabled:false}")
            boolean speakingMediaPlaybackEnabled) {
        this.objectMapper = objectMapper;
        this.feedbackReader = feedbackReader;
        this.writingFeedbackReader = writingFeedbackReader;
        this.writingFeedbackMapper = writingFeedbackMapper;
        this.speakingMediaService = speakingMediaService;
        this.speakingMediaPlaybackEnabled = speakingMediaPlaybackEnabled;
    }

    SpeakingResultPresenter(
            ObjectMapper objectMapper,
            SpeakingFeedbackCompatibilityReader feedbackReader,
            WritingFeedbackCompatibilityReader writingFeedbackReader,
            WritingFeedbackViewMapper writingFeedbackMapper) {
        this(
                objectMapper,
                feedbackReader,
                writingFeedbackReader,
                writingFeedbackMapper,
                null,
                false);
    }

    @Override
    public boolean supports(String skill) {
        return "SPEAKING".equals(skill);
    }

    @Override
    public Presentation present(PracticeResultContext context) {
        List<PracticeQuestionVersion> questions = context.snapshot().questions().stream()
                .filter(question -> "SPEAKING".equals(question.getQuestionType())
                        || "ESSAY".equals(question.getQuestionType()))
                .toList();
        String storedFeedback = context.attempt().getAiFeedbackJson();
        JsonNode root = readTree(storedFeedback);
        boolean malformedStoredFeedback = storedFeedback != null
                && !storedFeedback.isBlank()
                && (root == null || !root.isObject());
        boolean unsupportedContract = hasUnsupportedContract(root);
        List<SegmentFeedback> segments = new ArrayList<>();
        List<SegmentFeedback> lowConfidenceSegments = new ArrayList<>();
        List<LegacyEssayFeedback> legacyEssayFeedbacks = new ArrayList<>();
        long legacyEssayQuestionCount = questions.stream()
                .filter(question -> "ESSAY".equals(question.getQuestionType()))
                .count();
        int notAnswered = 0;
        int pending = 0;
        int unscorable = 0;
        int legacyUnverified = 0;

        for (PracticeQuestionVersion question : questions) {
            String answer = context.answers().getOrDefault(String.valueOf(question.getQuestionId()), "");
            if (answer.isBlank()) {
                notAnswered++;
                continue;
            }
            if (malformedStoredFeedback || unsupportedContract) {
                legacyUnverified++;
                unscorable++;
                continue;
            }
            if ("ESSAY".equals(question.getQuestionType())) {
                JsonNode node = legacyEssayFeedbackNode(
                        root, question.getQuestionId(), legacyEssayQuestionCount == 1);
                WritingFeedbackView feedback = writingFeedbackMapper.map(node);
                WritingFeedbackCompatibilityReader.EntryResult contract =
                        writingFeedbackReader.parseStoredEntry(node);
                switch (legacyEssayFeedbackState(node, feedback, contract)) {
                    case "READY" -> {
                        // Historical ESSAY rows remain readable as compatibility
                        // copy, but they are not verified Speaking evidence.
                        legacyEssayFeedbacks.add(new LegacyEssayFeedback(feedback));
                        legacyUnverified++;
                        unscorable++;
                    }
                    case "PENDING" -> pending++;
                    default -> unscorable++;
                }
                continue;
            }
            JsonNode node = feedbackNode(root, question.getQuestionId(), questions.size() == 1);
            SpeakingEvaluationResult feedback = node == null ? null : feedbackReader.read(node);
            switch (feedbackState(node, feedback)) {
                case "READY" -> segments.add(new SegmentFeedback(question.getQuestionId(), feedback));
                case "LOW_CONFIDENCE" -> {
                    lowConfidenceSegments.add(new SegmentFeedback(question.getQuestionId(), feedback));
                    unscorable++;
                }
                case "LEGACY" -> {
                    legacyUnverified++;
                    unscorable++;
                }
                case "PENDING" -> pending++;
                default -> unscorable++;
            }
        }

        boolean transcriptOnlyCapability = java.util.stream.Stream
                .concat(segments.stream(), lowConfidenceSegments.stream())
                .map(SegmentFeedback::feedback)
                .anyMatch(feedback -> feedback.currentEvidenceContract()
                        && feedback.evidenceMode() == SpeakingEvidenceMode.TRANSCRIPT_ONLY);
        List<SpeakingCriterionResult> criteria = criteria(
                segments, questions.size(), legacyUnverified, transcriptOnlyCapability);
        int coveredSpeakingSegments = (int) segments.stream()
                .filter(segment -> segment.feedback().profileAvailable())
                .count();
        int coveredSegments = coveredSpeakingSegments;
        int answered = questions.size() - notAnswered;
        ResultFeedbackAvailability feedback = feedbackAvailability(
                coveredSegments, pending, unscorable, answered, lowConfidenceSegments.size());
        ResultAnswerDistribution distribution = new ResultAnswerDistribution(
                0, 0, 0, notAnswered, pending, unscorable, questions.size(), coveredSegments);
        boolean holisticAvailable = !segments.isEmpty()
                && segments.stream().allMatch(segment -> segment.feedback().holisticScoreAvailable());
        // Current transcript-only results always take the unavailable branch.
        // The alternate branch is the stable seam for a future authorized,
        // calibrated direct-audio capability.
        ResultScoreSummary displayScore = feedback.ready() && holisticAvailable
                ? context.score()
                : context.score().unavailableView();

        SpeakingEvaluationResult representative = segments.stream()
                .map(SegmentFeedback::feedback)
                .findFirst()
                .orElseGet(() -> lowConfidenceSegments.stream()
                        .map(SegmentFeedback::feedback)
                        .findFirst().orElse(null));
        String contractTrust = representative == null
                ? "LEGACY_UNVERIFIED"
                : legacyUnverified > 0
                ? "MIXED_WITH_LEGACY_UNVERIFIED"
                : representative.contractTrust().name();
        String evaluatorCapability = representative == null
                ? "LEGACY_UNKNOWN"
                : representative.evaluatorCapability().name();
        String evidenceMode = representative == null
                ? "UNKNOWN"
                : representative.evidenceMode().name();
        String evidenceContractVersion = representative == null
                ? null
                : representative.evidenceContractVersion();
        String profileState;
        if (coveredSegments == 0 && !lowConfidenceSegments.isEmpty()) {
            profileState = "LOW_CONFIDENCE";
        } else if (legacyUnverified > 0 && coveredSegments == 0) {
            profileState = "LEGACY_UNVERIFIED";
        } else {
            profileState = feedback.state();
        }
        String evidenceNote = evidenceNote(
                profileState, evidenceMode, contractTrust, holisticAvailable);

        SpeakingResultPayload payload = new SpeakingResultPayload(
                displayScore,
                coveredSegments,
                questions.size(),
                profileState,
                evidenceMode,
                evidenceNote,
                mergeUnique(uniqueText(segments, TextKind.SUMMARY),
                        legacyEssayText(legacyEssayFeedbacks, TextKind.SUMMARY)),
                mergeUnique(uniqueText(segments, TextKind.STRENGTH),
                        legacyEssayText(legacyEssayFeedbacks, TextKind.STRENGTH)),
                mergeUnique(uniqueText(segments, TextKind.NEED),
                        legacyEssayText(legacyEssayFeedbacks, TextKind.NEED)),
                actionPlan(segments),
                criteria,
                evaluatorCapability,
                evidenceContractVersion,
                contractTrust,
                holisticAvailable,
                legacyUnverified);
        return new Presentation(displayScore, distribution, feedback, payload);
    }

    @Override
    public ResultDetailPayload presentDetail(
            PracticeResultContext context,
            PracticeAttemptResultView overview,
            Long questionId
    ) {
        if (!(overview.payload() instanceof SpeakingResultPayload)) {
            throw new IllegalStateException("Speaking Result Detail requires a Speaking payload.");
        }
        List<PracticeQuestionVersion> questions = context.snapshot().questions().stream()
                .filter(question -> "SPEAKING".equals(question.getQuestionType())
                        || "ESSAY".equals(question.getQuestionType()))
                .sorted(Comparator
                        .comparing(PracticeQuestionVersion::getDisplayOrder,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(PracticeQuestionVersion::getQuestionId))
                .toList();
        PracticeQuestionVersion selected = questions.stream()
                .filter(question -> questionId != null
                        && questionId.equals(question.getQuestionId()))
                .findFirst()
                .orElseGet(() -> questions.stream()
                        .filter(question ->
                                "SPEAKING".equals(question.getQuestionType()))
                        .findFirst()
                        .orElseGet(() -> questions.stream().findFirst().orElse(null)));
        JsonNode root = readTree(context.attempt().getAiFeedbackJson());
        long canonicalQuestionCount = questions.stream()
                .filter(question -> "SPEAKING".equals(question.getQuestionType()))
                .count();
        List<SpeakingTaskDetail> tasks = questions.stream()
                .map(question -> detailTask(
                        context,
                        root,
                        question,
                        selected == null ? null : selected.getQuestionId(),
                        canonicalQuestionCount == 1))
                .toList();
        if (selected == null) {
            return new SpeakingDetailPayload(
                    new ResultFeedbackAvailability(
                            "UNAVAILABLE", "Không có nhiệm vụ Nói để hiển thị", 0, 0),
                    tasks,
                    null,
                    "KSH_TRANSCRIPT_GROUNDED_LANGUAGE_CRITERIA_V1",
                    "UNAVAILABLE",
                    "UNKNOWN",
                    "LEGACY_UNKNOWN",
                    "Bài làm bất biến không có nhiệm vụ Nói phù hợp cho trang chi tiết.",
                    "UNAVAILABLE",
                    List.of(),
                    null,
                    "NO_DETAIL_TASK",
                    diagnosticScopeNoteVi(),
                    diagnosticScopeNoteKo(),
                    "Không có nhiệm vụ Nói để đối chiếu bằng chứng.",
                    "근거를 대조할 말하기 과제가 없습니다.",
                    List.of(),
                    null);
        }

        String rawAnswer = context.answers().getOrDefault(
                String.valueOf(selected.getQuestionId()), "");
        boolean submittedAudioMarker = audioSubmissionMarker(rawAnswer);
        SpeakingMediaView media = selectedMedia(context, selected);
        boolean canonicalSpeaking = "SPEAKING".equals(selected.getQuestionType());
        JsonNode selectedNode = canonicalSpeaking
                ? feedbackNode(
                root, selected.getQuestionId(), canonicalQuestionCount == 1)
                : legacyEssayFeedbackNode(
                root, selected.getQuestionId(),
                questions.stream().filter(question ->
                        "ESSAY".equals(question.getQuestionType())).count() == 1);
        SpeakingEvaluationResult selectedFeedback = canonicalSpeaking && selectedNode != null
                ? feedbackReader.read(selectedNode)
                : null;
        String authoritativeTranscript = authoritativeTranscript(selectedFeedback);
        boolean currentEvidence = canonicalSpeaking
                && selectedFeedback != null
                && selectedFeedback.currentEvidenceContract()
                && (!selectedFeedback.evaluationStatus().scoreBearing()
                || present(authoritativeTranscript));
        String evaluationState = selectedEvaluationState(
                canonicalSpeaking,
                rawAnswer,
                selectedNode,
                selectedFeedback,
                currentEvidence);
        String evidenceMode = selectedEvidenceMode(
                canonicalSpeaking, currentEvidence, selectedFeedback,
                submittedAudioMarker, media);
        String profileState = selectedProfileState(evaluationState);
        String evaluatorCapability = currentEvidence && selectedFeedback != null
                ? selectedFeedback.evaluatorCapability().name()
                : "LEGACY_UNKNOWN";
        String taskScoreState = selectedTaskScoreState(
                evaluationState, selectedFeedback, currentEvidence);
        List<ResultDetailScoreCriterion> scoreCriteria = detailCriteria(
                selected.getQuestionId(),
                selectedFeedback,
                currentEvidence,
                evaluationState);
        SpeakingEvidenceView evidence = detailEvidence(
                selected.getQuestionId(),
                authoritativeTranscript,
                currentEvidence,
                submittedAudioMarker,
                media,
                selectedFeedback);

        List<ResolvedDiagnostic> resolved = new ArrayList<>();
        if (currentEvidence
                && selectedFeedback.profileAvailable()
                && selectedFeedback.evidenceMode() == SpeakingEvidenceMode.TRANSCRIPT_ONLY) {
            int sequence = addSpeakingDiagnostics(
                    resolved,
                    selected.getQuestionId(),
                    selectedFeedback.strengths(),
                    ResultDetailPolarity.STRENGTH,
                    0);
            addSpeakingDiagnostics(
                    resolved,
                    selected.getQuestionId(),
                    selectedFeedback.needsImprovement(),
                    ResultDetailPolarity.NEEDS_IMPROVEMENT,
                    sequence);
        }
        DiagnosticState diagnosticState = diagnosticState(
                evaluationState, currentEvidence, resolved);

        return new SpeakingDetailPayload(
                selectedFeedbackAvailability(evaluationState),
                tasks,
                selected.getQuestionId(),
                "KSH_TRANSCRIPT_GROUNDED_LANGUAGE_CRITERIA_V1",
                profileState,
                evidenceMode,
                evaluatorCapability,
                detailEvidenceNote(
                        evidenceMode, evaluationState, evidence.recordingAvailable()),
                taskScoreState,
                scoreCriteria,
                evidence,
                diagnosticState.code(),
                diagnosticScopeNoteVi(),
                diagnosticScopeNoteKo(),
                diagnosticState.noteVi(),
                diagnosticState.noteKo(),
                detailGroups(resolved),
                detailUpgrade(
                        selected.getQuestionId(),
                        selectedFeedback,
                        currentEvidence,
                        authoritativeTranscript));
    }

    private SpeakingTaskDetail detailTask(
            PracticeResultContext context,
            JsonNode root,
            PracticeQuestionVersion question,
            Long selectedQuestionId,
            boolean singleCanonicalQuestion
    ) {
        boolean selected = selectedQuestionId != null
                && selectedQuestionId.equals(question.getQuestionId());
        boolean canonical = "SPEAKING".equals(question.getQuestionType());
        if (!selected) {
            return new SpeakingTaskDetail(
                    question.getQuestionId(),
                    question.getId(),
                    question.getQuestionNo(),
                    question.getQuestionType(),
                    canonical
                            ? "CANONICAL_SPEAKING"
                            : "LEGACY_ESSAY_COMPATIBILITY",
                    "",
                    "",
                    "NAVIGATION_ONLY",
                    "NAVIGATION_ONLY",
                    null);
        }
        String answer = context.answers().getOrDefault(
                String.valueOf(question.getQuestionId()), "");
        SpeakingEvaluationResult feedback = null;
        JsonNode node = null;
        if (canonical) {
            node = feedbackNode(root, question.getQuestionId(), singleCanonicalQuestion);
            feedback = node == null ? null : feedbackReader.read(node);
        }
        String transcript = authoritativeTranscript(feedback);
        boolean currentEvidence = canonical
                && feedback != null
                && feedback.currentEvidenceContract()
                && (!feedback.evaluationStatus().scoreBearing() || present(transcript));
        String evaluationState = selectedEvaluationState(
                canonical, answer, node, feedback, currentEvidence);
        String submissionText = audioSubmissionMarker(answer) ? "" : answer;
        String submissionState;
        if (!canonical) {
            submissionState = present(submissionText)
                    ? "LEGACY_ESSAY_TEXT_COMPATIBILITY"
                    : "NOT_ANSWERED";
        } else if (audioSubmissionMarker(answer)) {
            submissionState = present(transcript)
                    ? "AUDIO_SOURCE_WITH_AUTHORITATIVE_TRANSCRIPT"
                    : "AUDIO_SOURCE_TRANSCRIPT_UNAVAILABLE";
        } else if (present(answer)) {
            submissionState = "TEXT_COMPATIBILITY";
        } else {
            submissionState = "NOT_ANSWERED";
        }
        return new SpeakingTaskDetail(
                question.getQuestionId(),
                question.getId(),
                question.getQuestionNo(),
                question.getQuestionType(),
                canonical
                        ? "CANONICAL_SPEAKING"
                        : "LEGACY_ESSAY_COMPATIBILITY",
                question.getPrompt(),
                submissionText,
                submissionState,
                evaluationState,
                currentEvidence && feedback.profileAvailable()
                        ? transcriptGroundedText(feedback.overallSummary())
                        : null);
    }

    private SpeakingMediaView selectedMedia(
            PracticeResultContext context,
            PracticeQuestionVersion selected
    ) {
        if (speakingMediaService == null
                || !"SPEAKING".equals(selected.getQuestionType())) {
            return null;
        }
        List<SpeakingMediaView> matches = speakingMediaService
                .findReadyMediaViewsForOwner(
                        context.attempt().getUserId(), context.attempt().getId())
                .stream()
                .filter(view -> selected.getQuestionId().equals(view.questionId()))
                .toList();
        if (matches.size() > 1) {
            throw new IllegalStateException(
                    "Multiple READY media rows exist for the selected Speaking task.");
        }
        return matches.stream().findFirst().orElse(null);
    }

    private SpeakingEvidenceView detailEvidence(
            Long questionId,
            String authoritativeTranscript,
            boolean currentEvidence,
            boolean submittedAudioMarker,
            SpeakingMediaView media,
            SpeakingEvaluationResult feedback
    ) {
        boolean transcriptAvailable = currentEvidence && present(authoritativeTranscript);
        boolean playbackAvailable = media != null
                && speakingMediaPlaybackEnabled
                && present(media.playbackPath());
        String recordingState = media != null
                ? "READY_OWNER_BOUND_RECORDING"
                : submittedAudioMarker
                ? "SUBMISSION_MARKER_ONLY"
                : "UNAVAILABLE";
        return new SpeakingEvidenceView(
                questionId,
                transcriptAvailable ? authoritativeTranscript : "",
                transcriptAvailable ? "AVAILABLE" : "UNAVAILABLE",
                transcriptAvailable
                        ? "CURRENT_AUTHORITATIVE_TRANSCRIPT"
                        : "UNAVAILABLE",
                !transcriptAvailable
                        ? "NOT_APPLICABLE"
                        : media != null
                        && feedback != null
                        && java.util.Objects.equals(
                                feedback.audioMediaId(), media.mediaId())
                        && java.util.Objects.equals(
                                feedback.mediaVersion(), media.lockVersion())
                        ? "MATCHED_CURRENT_EVALUATION"
                        : "UNVERIFIED",
                recordingState,
                media == null ? null : media.mediaId(),
                media == null ? null : media.durationMs(),
                media == null ? null : media.byteSize(),
                media == null ? "" : media.mimeType(),
                playbackAvailable ? media.playbackPath() : "",
                playbackAvailable,
                "NOT_SCORABLE");
    }

    private static String authoritativeTranscript(SpeakingEvaluationResult feedback) {
        if (feedback == null || !present(feedback.actuallyHeardTranscript())) {
            return "";
        }
        String transcript = feedback.actuallyHeardTranscript().trim();
        return audioSubmissionMarker(transcript) ? "" : transcript;
    }

    private static boolean audioSubmissionMarker(String value) {
        return value != null && "AUDIO_SUBMITTED".equalsIgnoreCase(value.trim());
    }

    private static String selectedEvaluationState(
            boolean canonicalSpeaking,
            String answer,
            JsonNode node,
            SpeakingEvaluationResult feedback,
            boolean currentEvidence
    ) {
        if (!present(answer)) {
            return "UNAVAILABLE";
        }
        if (!canonicalSpeaking) {
            return "LEGACY_UNVERIFIED";
        }
        String state = feedbackState(node, feedback);
        if (("READY".equals(state) || "LOW_CONFIDENCE".equals(state))
                && !currentEvidence) {
            return "LEGACY_UNVERIFIED";
        }
        return switch (state) {
            case "READY", "LOW_CONFIDENCE", "PENDING" -> state;
            case "LEGACY" -> "LEGACY_UNVERIFIED";
            default -> "FAILED";
        };
    }

    private static String selectedProfileState(String evaluationState) {
        return switch (evaluationState) {
            case "READY", "LOW_CONFIDENCE", "PENDING", "FAILED",
                    "LEGACY_UNVERIFIED" -> evaluationState;
            default -> "UNAVAILABLE";
        };
    }

    private static String selectedEvidenceMode(
            boolean canonicalSpeaking,
            boolean currentEvidence,
            SpeakingEvaluationResult feedback,
            boolean submittedAudioMarker,
            SpeakingMediaView media
    ) {
        if (!canonicalSpeaking) {
            return "LEGACY_ESSAY_TEXT_COMPATIBILITY";
        }
        if (currentEvidence
                && feedback.evidenceMode() == SpeakingEvidenceMode.TRANSCRIPT_ONLY) {
            return "TRANSCRIPT_ONLY";
        }
        if (submittedAudioMarker || media != null) {
            return "RECORDING_SOURCE_ONLY";
        }
        return "UNKNOWN";
    }

    private static String selectedTaskScoreState(
            String evaluationState,
            SpeakingEvaluationResult feedback,
            boolean currentEvidence
    ) {
        if (currentEvidence && feedback.profileAvailable()
                && feedback.rubricScores().stream().anyMatch(
                SpeakingEvaluationResult.RubricScore::scored)) {
            return "LANGUAGE_CRITERIA_AVAILABLE_NO_TASK_TOTAL";
        }
        return switch (evaluationState) {
            case "PENDING" -> "PENDING";
            case "LEGACY_UNVERIFIED" -> "LEGACY_UNVERIFIED";
            default -> "UNAVAILABLE";
        };
    }

    private static List<ResultDetailScoreCriterion> detailCriteria(
            Long questionId,
            SpeakingEvaluationResult feedback,
            boolean currentEvidence,
            String evaluationState
    ) {
        List<ResultDetailScoreCriterion> result = new ArrayList<>();
        for (SpeakingRubricCriterion criterion : SpeakingRubricCriterion.values()) {
            SpeakingEvaluationResult.RubricScore row = currentEvidence
                    ? feedback.rubricScores().stream()
                    .filter(candidate -> candidate.criterion() == criterion)
                    .findFirst().orElse(null)
                    : null;
            String availability;
            BigDecimal score = null;
            BigDecimal maxScore = null;
            if (row != null && row.scored() && criterion.transcriptGrounded()) {
                availability = "SCORED";
                score = row.score();
                maxScore = row.maxScore();
            } else if (criterion.requiresAcousticEvidence()
                    && !"LEGACY_UNVERIFIED".equals(evaluationState)) {
                availability = "NOT_SCORABLE";
            } else if ("LEGACY_UNVERIFIED".equals(evaluationState)) {
                availability = "LEGACY_UNVERIFIED";
            } else {
                availability = "UNAVAILABLE";
            }
            result.add(new ResultDetailScoreCriterion(
                    questionId,
                    criterion.id(),
                    ResultDetailDescriptorRegistry.scoreLabelVi(criterion.id()),
                    ResultDetailDescriptorRegistry.scoreLabelKo(criterion.id()),
                    score,
                    maxScore,
                    availability,
                    criterion.ordinal() + 1));
        }
        return List.copyOf(result);
    }

    private static int addSpeakingDiagnostics(
            List<ResolvedDiagnostic> target,
            Long questionId,
            List<SpeakingEvaluationResult.FeedbackItem> findings,
            ResultDetailPolarity polarity,
            int sequence
    ) {
        for (SpeakingEvaluationResult.FeedbackItem finding : findings) {
            if (finding == null
                    || finding.criterion() == null
                    || !finding.criterion().transcriptGrounded()
                    || finding.evidenceSource() != SpeakingEvidenceSource.TRANSCRIPT
                    || acousticSubcriterion(finding.subCriterionId())
                    || !present(finding.explanationVi())
                    || !transcriptGroundedClaim(finding.explanationVi())) {
                continue;
            }
            String evidenceAvailability = ResultDetailDescriptorRegistry
                    .evidenceAvailability(finding.evidenceScope());
            ResultDetailDescriptorRegistry.Definition descriptor =
                    ResultDetailDescriptorRegistry.speaking(
                            finding.criterion(), finding.subCriterionId(), polarity);
            ResultDetailDescriptorRegistry.SpeakingFamily family =
                    ResultDetailDescriptorRegistry.speakingFamily(
                            finding.subCriterionId());
            if (descriptor == null || family == null || evidenceAvailability == null) {
                continue;
            }
            sequence++;
            ResultDetailDiagnosticFinding diagnostic = new ResultDetailDiagnosticFinding(
                    questionId,
                    "S-" + questionId + "-" + sequence,
                    descriptor.id(),
                    polarity,
                    descriptor.parentCriterionId(),
                    descriptor.applicability(),
                    evidenceAvailability,
                    finding.evidenceScope(),
                    finding.evidence(),
                    finding.explanationVi(),
                    finding.correction());
            target.add(new ResolvedDiagnostic(family, descriptor, diagnostic));
        }
        return sequence;
    }

    private static List<SpeakingDiagnosticGroup> detailGroups(
            List<ResolvedDiagnostic> diagnostics
    ) {
        Map<String, List<ResolvedDiagnostic>> grouped = new LinkedHashMap<>();
        diagnostics.stream()
                .sorted(Comparator
                        .comparingInt((ResolvedDiagnostic row) ->
                                row.family().stableOrder())
                        .thenComparingInt(row -> row.definition().stableOrder()))
                .forEach(row -> grouped.computeIfAbsent(
                        row.family().code(), ignored -> new ArrayList<>()).add(row));
        return grouped.values().stream().map(rows -> {
            ResultDetailDescriptorRegistry.SpeakingFamily family =
                    rows.get(0).family();
            List<ResultDetailFilterChip> chips = detailChips(rows);
            return new SpeakingDiagnosticGroup(
                    family.code(),
                    family.labelVi(),
                    family.labelKo(),
                    family.stableOrder(),
                    rows.stream()
                            .map(ResolvedDiagnostic::finding)
                            .filter(finding ->
                                    finding.polarity()
                                            == ResultDetailPolarity.STRENGTH)
                            .toList(),
                    rows.stream()
                            .map(ResolvedDiagnostic::finding)
                            .filter(finding ->
                                    finding.polarity()
                                            == ResultDetailPolarity.NEEDS_IMPROVEMENT)
                            .toList(),
                    chips.stream()
                            .filter(chip ->
                                    chip.polarity()
                                            == ResultDetailPolarity.STRENGTH)
                            .toList(),
                    chips.stream()
                            .filter(chip ->
                                    chip.polarity()
                                            == ResultDetailPolarity.NEEDS_IMPROVEMENT)
                            .toList());
        }).toList();
    }

    private static List<ResultDetailFilterChip> detailChips(
            List<ResolvedDiagnostic> diagnostics
    ) {
        Map<String, ChipCount> counts = new LinkedHashMap<>();
        for (ResolvedDiagnostic resolved : diagnostics) {
            counts.compute(resolved.definition().id(), (ignored, current) ->
                    current == null
                            ? new ChipCount(
                                    resolved.definition(),
                                    resolved.finding().polarity(),
                                    1,
                                    resolved.finding().evidenceAvailability())
                            : current.incremented(resolved.finding().evidenceAvailability()));
        }
        return counts.values().stream()
                .sorted(Comparator.comparingInt(value -> value.definition().stableOrder()))
                .map(value -> new ResultDetailFilterChip(
                        value.definition().id(),
                        value.definition().labelVi(),
                        value.definition().labelKo(),
                        value.polarity(),
                        value.definition().parentCriterionId(),
                        value.definition().applicability(),
                        value.definition().stableOrder(),
                        value.count(),
                        false,
                        value.evidenceAvailability()))
                .toList();
    }

    private static ResultFeedbackAvailability selectedFeedbackAvailability(
            String evaluationState
    ) {
        return switch (evaluationState) {
            case "READY" -> new ResultFeedbackAvailability(
                    "READY", "Phản hồi ngôn ngữ đã sẵn sàng", 1, 1);
            case "LOW_CONFIDENCE" -> new ResultFeedbackAvailability(
                    "LOW_CONFIDENCE", "Bản chép lời có độ tin cậy thấp", 0, 1);
            case "PENDING" -> new ResultFeedbackAvailability(
                    "PENDING", "Bằng chứng đang được xử lý", 0, 1);
            case "LEGACY_UNVERIFIED" -> new ResultFeedbackAvailability(
                    "LEGACY_UNVERIFIED",
                    "Dữ liệu tương thích cũ chưa được xác minh", 0, 1);
            case "FAILED" -> new ResultFeedbackAvailability(
                    "FAILED", "Chưa thể tạo phản hồi cho nhiệm vụ này", 0, 1);
            default -> new ResultFeedbackAvailability(
                    "UNAVAILABLE", "Chưa có phản hồi khả dụng", 0, 1);
        };
    }

    private static String detailEvidenceNote(
            String evidenceMode,
            String evaluationState,
        boolean recordingAvailable
    ) {
        if ("TRANSCRIPT_ONLY".equals(evidenceMode)) {
            if ("LOW_CONFIDENCE".equals(evaluationState)) {
                return "Bản chép lời thuộc nguồn hiện tại nhưng có độ tin cậy thấp, "
                        + "nên không dùng để chấm tiêu chí. Bộ đánh giá không nghe bản ghi.";
            }
            if ("PENDING".equals(evaluationState)) {
                return "Bộ xử lý hiện tại chỉ đánh giá từ bản chép lời và chưa xử lý xong "
                        + "câu đang chọn. Chưa có điểm nào được suy đoán; bộ đánh giá không nghe bản ghi.";
            }
            if (!"READY".equals(evaluationState)) {
                return "Năng lực hiện tại chỉ hỗ trợ đánh giá ngôn ngữ từ bản chép lời, "
                        + "nhưng câu đang chọn chưa có bằng chứng đủ điều kiện. "
                        + "Không có tiêu chí nào được quy đổi thành 0 điểm.";
            }
            return "Các điểm hiện có chỉ dựa trên bản chép lời đã khóa của câu đang chọn. "
                    + "Bộ đánh giá không nhận âm thanh trực tiếp, nên không chấm độ lưu loát, "
                    + "nhịp điệu, phát âm hoặc cách thể hiện.";
        }
        if ("RECORDING_SOURCE_ONLY".equals(evidenceMode)) {
            return recordingAvailable
                    ? "Có bản ghi thuộc bài làm và có thể phát lại khi tính năng được bật, "
                    + "nhưng điều đó chỉ xác nhận nguồn nộp. Chưa có bản chép lời đủ thẩm quyền "
                    + "và bộ đánh giá chưa nghe âm thanh để chấm các tiêu chí âm học."
                    : "Hệ thống chỉ có trạng thái đã nộp âm thanh; không dùng trạng thái này "
                    + "làm bản chép lời và không suy ra rằng bộ đánh giá đã nghe bản ghi.";
        }
        if ("LEGACY_ESSAY_TEXT_COMPATIBILITY".equals(evidenceMode)) {
            return "Đây là nhiệm vụ Nói cũ được lưu theo dạng văn bản tự luận. "
                    + "Nội dung này chỉ được hiển thị để tương thích, không được coi là "
                    + "bản chép lời hay bằng chứng âm thanh theo hợp đồng hiện tại.";
        }
        if ("PENDING".equals(evaluationState)) {
            return "Bằng chứng của câu đang chọn chưa xử lý xong; không có điểm nào "
                    + "được suy đoán trong thời gian chờ.";
        }
        return "Chưa có nguồn bằng chứng Nói đủ điều kiện. Trạng thái thiếu dữ liệu "
                + "không được quy đổi thành 0 điểm.";
    }

    private static DiagnosticState diagnosticState(
            String evaluationState,
            boolean currentEvidence,
            List<ResolvedDiagnostic> diagnostics
    ) {
        if (!diagnostics.isEmpty()) {
            return new DiagnosticState(
                    "AVAILABLE",
                    "Chỉ hiển thị phát hiện có bằng chứng chính xác trong bản chép lời của câu đang chọn.",
                    "선택한 문항의 전사문에서 정확한 근거가 확인된 진단만 표시합니다.");
        }
        if ("LOW_CONFIDENCE".equals(evaluationState)) {
            return new DiagnosticState(
                    "LOW_CONFIDENCE_TRANSCRIPT",
                    "Bản chép lời chưa đủ tin cậy để tạo phát hiện chẩn đoán.",
                    "전사 신뢰도가 낮아 진단 결과를 생성하지 않습니다.");
        }
        if ("PENDING".equals(evaluationState)) {
            return new DiagnosticState(
                    "PENDING",
                    "Bằng chứng đang được xử lý; chưa có phát hiện nào được suy đoán.",
                    "근거 처리 중이며 진단 결과를 추정하지 않습니다.");
        }
        if ("LEGACY_UNVERIFIED".equals(evaluationState)) {
            return new DiagnosticState(
                    "LEGACY_UNVERIFIED",
                    "Phản hồi cũ không được chuyển thành chẩn đoán Nói theo nhãn Viết.",
                    "이전 피드백을 쓰기 진단 라벨로 변환하지 않습니다.");
        }
        if (currentEvidence) {
            return new DiagnosticState(
                    "NO_VALIDATED_EVIDENCE",
                    "Chưa có phát hiện nào đáp ứng hợp đồng bằng chứng chính xác.",
                    "정확한 근거 계약을 충족하는 진단 결과가 없습니다.");
        }
        return new DiagnosticState(
                "FEEDBACK_UNAVAILABLE",
                "Chưa có phản hồi Nói đủ điều kiện để hiển thị.",
                "표시할 수 있는 말하기 피드백이 없습니다.");
    }

    private static String diagnosticScopeNoteVi() {
        return "Các nhóm hiện tại chỉ bao phủ đúng các mã Nói tiếng Hàn đã được xác thực: "
                + "mức độ phù hợp với nhiệm vụ, diễn ngôn, hình thái-cú pháp, từ vựng-kết hợp từ "
                + "và kính ngữ-văn phong-ngữ dụng. Phát âm và độ lưu loát cần bộ chấm âm thanh trực tiếp.";
    }

    private static String diagnosticScopeNoteKo() {
        return "현재 진단은 검증된 한국어 말하기 항목인 과제 적합성, 담화, 형태·통사, "
                + "어휘·연어, 높임법·문체·화용만 다룹니다. 발음과 유창성은 직접 음성 평가가 필요합니다.";
    }

    private static SpeakingUpgradeView detailUpgrade(
            Long questionId,
            SpeakingEvaluationResult feedback,
            boolean currentEvidence,
            String authoritativeTranscript
    ) {
        boolean eligible = currentEvidence && feedback != null
                && feedback.profileAvailable()
                && feedback.evidenceMode() == SpeakingEvidenceMode.TRANSCRIPT_ONLY;
        String upgraded = eligible && present(feedback.upgradedAnswer())
                ? feedback.upgradedAnswer()
                : "";
        String sample = eligible && present(feedback.sampleAnswer())
                ? feedback.sampleAnswer()
                : "";
        return new SpeakingUpgradeView(
                questionId,
                new SpeakingAnswerArtifact(
                        upgraded,
                        upgraded.isBlank() ? "UNAVAILABLE" : "AVAILABLE",
                        "LEARNER_TRANSCRIPT_DERIVED_EVALUATOR_OUTPUT",
                        "Bài nói nâng cấp dựa trên câu trả lời của bạn",
                        "학습자 답변 기반 개선 말하기"),
                eligible
                        ? significantSpeakingRewrites(
                        feedback.needsImprovement(), authoritativeTranscript)
                        : List.of(),
                new SpeakingAnswerArtifact(
                        sample,
                        sample.isBlank() ? "UNAVAILABLE" : "AVAILABLE",
                        "EVALUATOR_GENERATED_NOT_TEACHER_REFERENCE",
                        "Câu trả lời mẫu do bộ đánh giá tạo",
                        "평가기가 생성한 예시 답변"));
    }

    private static List<SpeakingPhraseRewriteView> significantSpeakingRewrites(
            List<SpeakingEvaluationResult.FeedbackItem> findings,
            String authoritativeTranscript
    ) {
        Map<String, SpeakingPhraseRewriteView> unique = new LinkedHashMap<>();
        for (SpeakingEvaluationResult.FeedbackItem finding : findings) {
            if (finding == null
                    || !"TEXT_SPAN".equals(finding.evidenceScope())
                    || finding.evidenceSource() != SpeakingEvidenceSource.TRANSCRIPT
                    || !present(finding.evidence())
                    || !present(finding.correction())
                    || finding.evidence().equals(finding.correction())
                    || !authoritativeTranscript.contains(finding.evidence())
                    || !present(finding.explanationVi())
                    || !transcriptGroundedClaim(finding.explanationVi())) {
                continue;
            }
            String key = normalizeKey(finding.evidence())
                    + "|" + normalizeKey(finding.correction());
            unique.putIfAbsent(
                    key,
                    new SpeakingPhraseRewriteView(
                            finding.evidence(),
                            finding.correction(),
                            finding.explanationVi()));
        }
        return unique.values().stream().limit(6).toList();
    }

    private static List<SpeakingCriterionResult> criteria(
            List<SegmentFeedback> segments,
            int totalSegments,
            int legacyUnverifiedSegments,
            boolean transcriptOnlyCapability) {
        Map<SpeakingRubricCriterion, List<CriterionEvidence>> evidence =
                new EnumMap<>(SpeakingRubricCriterion.class);
        for (SpeakingRubricCriterion criterion : SpeakingRubricCriterion.values()) {
            evidence.put(criterion, new ArrayList<>());
        }
        for (SegmentFeedback segment : segments) {
            if (!segment.feedback().profileAvailable()) {
                continue;
            }
            for (SpeakingEvaluationResult.RubricScore row : segment.feedback().rubricScores()) {
                if (row.criterion() == null || !row.scored()) {
                    continue;
                }
                evidence.get(row.criterion()).add(new CriterionEvidence(
                        normalizedWeightedScore(row, row.criterion()),
                        transcriptGroundedText(firstPresent(
                                row.feedback(),
                                criterionSummary(segment.feedback(), row.criterion())))));
            }
        }

        List<SpeakingCriterionResult> result = new ArrayList<>();
        boolean transcriptOnly = transcriptOnlyCapability
                || !segments.isEmpty() && segments.stream()
                        .allMatch(segment -> segment.feedback().evidenceMode()
                                == SpeakingEvidenceMode.TRANSCRIPT_ONLY);
        for (SpeakingRubricCriterion criterion : SpeakingRubricCriterion.values()) {
            List<CriterionEvidence> rows = evidence.get(criterion);
            BigDecimal score = rows.isEmpty()
                    ? null
                    : rows.stream().map(CriterionEvidence::weightedScore)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP);
            BigDecimal percentage = score == null
                    ? null
                    : score.multiply(BigDecimal.valueOf(100))
                            .divide(criterion.maxScore(), 2, RoundingMode.HALF_UP);
            String availability;
            if (score != null) {
                availability = "SCORED";
            } else if (transcriptOnly && criterion.requiresAcousticEvidence()) {
                // Current transcript capability always owns the acoustic state,
                // even when legacy-unverified rows coexist with low-confidence
                // current segments.
                availability = "NOT_SCORABLE";
            } else if (segments.isEmpty() && legacyUnverifiedSegments > 0) {
                availability = "LEGACY_UNVERIFIED";
            } else {
                availability = "UNAVAILABLE";
            }
            result.add(new SpeakingCriterionResult(
                    criterion.id(),
                    criterionLabel(criterion),
                    score == null ? null : criterion.maxScore(),
                    score,
                    percentage,
                    rows.size(),
                    totalSegments,
                    score == null ? ResultEvaluationBand.UNAVAILABLE
                            : ResultEvaluationBand.fromPercentage(percentage),
                    rows.stream().map(CriterionEvidence::summary)
                            .filter(SpeakingResultPresenter::present)
                            .findFirst().orElse(null),
                    false,
                    availability,
                    criterion.requiresAcousticEvidence()));
        }
        return List.copyOf(result);
    }

    private static BigDecimal normalizedWeightedScore(
            SpeakingEvaluationResult.RubricScore row,
            SpeakingRubricCriterion criterion) {
        return row.score().multiply(criterion.maxScore())
                .divide(row.maxScore(), 4, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO)
                .min(criterion.maxScore());
    }

    private static String criterionSummary(
            SpeakingEvaluationResult feedback,
            SpeakingRubricCriterion criterion) {
        return feedback.criterionFeedback().stream()
                .filter(item -> criterion == item.criterion())
                .map(SpeakingEvaluationResult.CriterionFeedback::summary)
                .filter(SpeakingResultPresenter::present)
                .filter(SpeakingResultPresenter::transcriptGroundedClaim)
                .findFirst()
                .orElse(null);
    }

    private static List<String> uniqueText(List<SegmentFeedback> segments, TextKind kind) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (SegmentFeedback segment : segments) {
            SpeakingEvaluationResult feedback = segment.feedback();
            switch (kind) {
                case SUMMARY -> addTranscriptGrounded(values, feedback.overallSummary());
                case STRENGTH -> {
                    feedback.majorStrengths().forEach(value -> addTranscriptGrounded(values, value));
                    feedback.strengths().forEach(item -> addTranscriptGrounded(
                            values, item.explanationVi()));
                }
                case NEED -> {
                    feedback.majorNeedsImprovement().forEach(value -> addTranscriptGrounded(values, value));
                    feedback.needsImprovement().forEach(item -> addTranscriptGrounded(
                            values, item.explanationVi()));
                }
            }
        }
        return List.copyOf(values);
    }

    private static List<String> legacyEssayText(
            List<LegacyEssayFeedback> feedbacks,
            TextKind kind) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (LegacyEssayFeedback legacy : feedbacks) {
            WritingFeedbackView feedback = legacy.feedback();
            switch (kind) {
                case SUMMARY -> addTranscriptGrounded(
                        values, firstPresent(feedback.summaryVi(), feedback.summary()));
                case STRENGTH -> feedback.strengths().stream()
                        .map(SpeakingResultPresenter::findingText)
                        .forEach(value -> addTranscriptGrounded(values, value));
                case NEED -> feedback.needsImprovement().stream()
                        .map(SpeakingResultPresenter::findingText)
                        .forEach(value -> addTranscriptGrounded(values, value));
            }
        }
        return List.copyOf(values);
    }

    private static String findingText(WritingFindingView finding) {
        return firstPresent(finding.explanationVi(), finding.correction());
    }

    private static List<String> mergeUnique(List<String> first, List<String> second) {
        LinkedHashSet<String> values = new LinkedHashSet<>(first);
        values.addAll(second);
        return List.copyOf(values);
    }

    private static List<SpeakingActionPlanView> actionPlan(List<SegmentFeedback> segments) {
        Map<String, SpeakingActionPlanView> unique = new LinkedHashMap<>();
        for (SegmentFeedback segment : segments) {
            for (SpeakingEvaluationResult.ActionPlanItem item : segment.feedback().actionPlan()) {
                if (item.criterion() == null
                        || !item.criterion().transcriptGrounded()
                        || acousticSubcriterion(item.subCriterionId())
                        || !transcriptGroundedClaim(item.title())
                        || !transcriptGroundedClaim(item.instruction())
                        || !transcriptGroundedClaim(item.reason())
                        || (!present(item.title()) && !present(item.instruction()))) {
                    continue;
                }
                SpeakingActionPlanView view = new SpeakingActionPlanView(
                        item.criterion() == null ? null : item.criterion().id(),
                        item.subCriterionId(),
                        item.title(),
                        item.instruction(),
                        item.reason(),
                        item.priority());
                String key = String.join("|",
                        normalizeKey(item.title()),
                        normalizeKey(item.instruction()),
                        normalizeKey(item.subCriterionId()));
                unique.putIfAbsent(key, view);
            }
        }
        return unique.values().stream().limit(4).toList();
    }

    private static ResultFeedbackAvailability feedbackAvailability(
            int ready,
            int pending,
            int failed,
            int total,
            int lowConfidence) {
        if (total == 0) {
            return new ResultFeedbackAvailability(
                    "UNAVAILABLE", "Không có phần trả lời nói để đánh giá", 0, 0);
        }
        if (ready == total) {
            return new ResultFeedbackAvailability("READY", "Đánh giá tổng quan đã sẵn sàng", ready, total);
        }
        if (ready > 0) {
            return new ResultFeedbackAvailability("PARTIAL", "Một phần bằng chứng đánh giá đã sẵn sàng", ready, total);
        }
        if (pending > 0) {
            return new ResultFeedbackAvailability(
                    "PENDING", "Đánh giá bài nói đang được xử lý", 0, total);
        }
        if (lowConfidence > 0) {
            return new ResultFeedbackAvailability(
                    "LOW_CONFIDENCE", "Bản chép lời có độ tin cậy thấp", 0, total);
        }
        if (failed > 0) {
            return new ResultFeedbackAvailability(
                    "FAILED", "Chưa có đánh giá bài nói khả dụng", 0, total);
        }
        return new ResultFeedbackAvailability(
                "UNAVAILABLE", "Chưa có dữ liệu đánh giá bài nói", 0, total);
    }

    private static String feedbackState(JsonNode node, SpeakingEvaluationResult feedback) {
        if (node == null || !node.isObject()) {
            return "PENDING";
        }
        String rawStatus = firstPresent(text(node, "evaluationStatus"),
                text(node, "evaluation_status"));
        String normalized = rawStatus == null
                ? ""
                : rawStatus.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.contains("PENDING") || normalized.contains("QUEUED")
                || normalized.contains("PROCESSING")) {
            return "PENDING";
        }
        if (feedback == null) {
            return "FAILED";
        }
        if (!trustedOverviewCapability(feedback)) {
            return "LEGACY";
        }
        if (feedback.evaluationStatus() == SpeakingEvaluationStatus.TRANSCRIPTION_LOW_CONFIDENCE) {
            return "LOW_CONFIDENCE";
        }
        return feedback.profileAvailable() ? "READY" : "FAILED";
    }

    private static boolean trustedOverviewCapability(SpeakingEvaluationResult feedback) {
        if (!feedback.currentEvidenceContract()) {
            return false;
        }
        if (feedback.evaluatorCapability()
                == SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION) {
            return feedback.evidenceMode() == SpeakingEvidenceMode.TRANSCRIPT_ONLY;
        }
        // AUDIO_DIRECT_FULL_RESERVED deliberately has both readiness flags off.
        // A later production capability can enter this branch only after it
        // explicitly enables governed acoustic and holistic scoring.
        return feedback.evaluatorCapability() != SpeakingEvaluatorCapability.AUDIO_DIRECT_FULL_RESERVED
                && feedback.evaluatorCapability().directLearnerAudioRequired()
                && feedback.evaluatorCapability().acousticCriteriaSupported()
                && feedback.evaluatorCapability().holisticScoreSupported()
                && feedback.evidenceMode() == SpeakingEvidenceMode.DIRECT_AUDIO_AND_TRANSCRIPT;
    }

    private static String evidenceNote(
            String profileState,
            String evidenceMode,
            String contractTrust,
            boolean holisticAvailable
    ) {
        if (holisticAvailable) {
            return "Kết quả tổng hợp chỉ được hiển thị khi bộ đánh giá đã trực tiếp nhận bản ghi được cấp quyền và năng lực chấm âm thanh đã qua hiệu chuẩn.";
        }
        if ("LEGACY_UNVERIFIED".equals(profileState)
                || "LEGACY_UNVERIFIED".equals(contractTrust)) {
            return "Kết quả lưu trước đây hoặc năng lực đánh giá không xác định không đủ điều kiện để tạo hồ sơ Nói theo quy tắc hiện tại; mọi điểm cũ đều được ẩn.";
        }
        if ("PENDING".equals(profileState)) {
            return "Bằng chứng đang được xử lý. Chưa có tiêu chí nào được xem là 0 điểm trong thời gian chờ.";
        }
        if ("LOW_CONFIDENCE".equals(profileState)) {
            return "Bản chép lời được tạo theo hợp đồng bằng chứng hiện tại nhưng có độ tin cậy thấp, nên không được dùng để chấm tiêu chí, tính độ bao phủ hoặc tạo điểm Nói tổng hợp.";
        }
        if ("FAILED".equals(profileState) || "UNAVAILABLE".equals(profileState)) {
            return "Chưa có đủ bằng chứng đã xác minh để tạo hồ sơ. Trạng thái thiếu dữ liệu không được quy đổi thành 0 điểm.";
        }
        if ("TRANSCRIPT_ONLY".equals(evidenceMode)) {
            return "Hồ sơ này chỉ đánh giá ngôn ngữ từ bản chép lời. Bộ đánh giá không nhận âm thanh trực tiếp của người học, nên độ lưu loát, phát âm và điểm Nói tổng hợp chưa thể chấm.";
        }
        return "Chưa có năng lực đánh giá Nói đã được cấp quyền và xác minh cho bằng chứng hiện tại.";
    }

    private static String legacyEssayFeedbackState(
            JsonNode node,
            WritingFeedbackView feedback,
            WritingFeedbackCompatibilityReader.EntryResult contract) {
        if (node == null || !node.isObject()) {
            return "PENDING";
        }
        String rawStatus = feedback == null ? null : feedback.evaluationStatus();
        String normalized = rawStatus == null
                ? ""
                : rawStatus.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.contains("PENDING") || normalized.contains("QUEUED")
                || normalized.contains("PROCESSING")) {
            return "PENDING";
        }
        if (feedback != null && Boolean.FALSE.equals(feedback.scoreAvailable())) {
            return "FAILED";
        }
        if (contract.value() != null && contract.value().scoreAvailableFlag()) {
            return "READY";
        }
        return hasLegacyScore(node) ? "READY" : "FAILED";
    }

    private JsonNode feedbackNode(JsonNode root, Long questionId, boolean singleQuestion) {
        if (root == null || !root.isObject()) {
            return null;
        }
        String contract = text(root, CONTRACT_FIELD);
        if (contract != null && !AI_CONTRACT.equals(contract) && !MIXED_CONTRACT.equals(contract)) {
            return null;
        }
        JsonNode byQuestion = root.path(FEEDBACK_BY_QUESTION);
        JsonNode candidate = byQuestion.isObject()
                ? byQuestion.get(String.valueOf(questionId))
                : root.get(String.valueOf(questionId));
        if (candidate != null && candidate.isTextual()) {
            candidate = readTree(candidate.asText());
        }
        if (candidate != null && candidate.isObject()) {
            return candidate;
        }
        return singleQuestion && hasStoredFeedback(root) ? root : null;
    }

    private JsonNode legacyEssayFeedbackNode(JsonNode root, Long questionId, boolean singleEssay) {
        if (root == null || !root.isObject()) {
            return null;
        }
        String contract = text(root, CONTRACT_FIELD);
        if (contract != null && !MIXED_CONTRACT.equals(contract)) {
            return null;
        }
        if (MIXED_CONTRACT.equals(contract)) {
            JsonNode candidate = root.path(ESSAY_FEEDBACK_BY_QUESTION)
                    .get(String.valueOf(questionId));
            return candidate != null && candidate.isObject() ? candidate : null;
        }
        JsonNode candidate = root.get(String.valueOf(questionId));
        if (candidate != null && candidate.isTextual()) {
            candidate = readTree(candidate.asText());
        }
        if (candidate != null && candidate.isObject()) {
            return candidate;
        }
        return singleEssay ? root : null;
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            return null;
        }
    }

    private static boolean hasStoredFeedback(JsonNode node) {
        return node != null && node.isObject() && (node.has("rubric_scores")
                || node.has("evaluation_status")
                || node.has("evaluationStatus")
                || node.has("overall_summary")
                || node.has("overallSummary")
                || node.has("summary")
                || node.has("summary_vi"));
    }

    private static boolean hasUnsupportedContract(JsonNode root) {
        String contract = text(root, CONTRACT_FIELD);
        return contract != null && !AI_CONTRACT.equals(contract) && !MIXED_CONTRACT.equals(contract);
    }

    private static boolean hasLegacyScore(JsonNode node) {
        return number(node, "score") || number(node, "overall_score")
                || number(node, "percentage");
    }

    private static boolean number(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isNumber();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank()
                ? value.asText()
                : null;
    }

    private static String criterionLabel(SpeakingRubricCriterion criterion) {
        return switch (criterion) {
            case CONTENT_TASK_FULFILLMENT -> "Nội dung và hoàn thành yêu cầu";
            case GRAMMAR_SENTENCE_CONTROL -> "Ngữ pháp và kiểm soát câu";
            case VOCABULARY_EXPRESSIONS -> "Từ vựng và biểu đạt";
            case COHERENCE_ORGANIZATION -> "Mạch lạc và tổ chức ý";
            case FLUENCY -> "Độ lưu loát";
            case PRONUNCIATION_DELIVERY -> "Phát âm và thể hiện";
        };
    }

    private static String firstPresent(String first, String second) {
        return present(first) ? first : second;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static void add(Set<String> values, String value) {
        if (present(value)) {
            values.add(value.trim());
        }
    }

    private static void addTranscriptGrounded(Set<String> values, String value) {
        if (transcriptGroundedClaim(value)) {
            add(values, value);
        }
    }

    private static String transcriptGroundedText(String value) {
        return transcriptGroundedClaim(value) ? value : null;
    }

    private static boolean transcriptGroundedClaim(String value) {
        if (!present(value)) {
            return true;
        }
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        return java.util.stream.Stream.of(
                        "pronunciation", "delivery", "fluency", "hesitation", "pacing",
                        "pause", "rhythm", "intonation", "listener burden", "linking",
                        "batchim", "phoneme", "phát âm", "độ lưu loát", "lưu loát",
                        "ngập ngừng", "nhịp điệu", "ngữ điệu", "nối âm", "tốc độ nói",
                        "gánh nặng người nghe", "발음", "유창", "억양", "리듬", "받침")
                .noneMatch(normalized::contains);
    }

    private static boolean acousticSubcriterion(String value) {
        if (!present(value)) {
            return false;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.startsWith("S_FLUENCY_")
                || normalized.startsWith("S_PRONUNCIATION_")
                || normalized.startsWith("S_DELIVERY_")
                || normalized.startsWith("S_INTONATION_");
    }

    private static String normalizeKey(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    private record SegmentFeedback(Long questionId, SpeakingEvaluationResult feedback) {
    }

    private record LegacyEssayFeedback(WritingFeedbackView feedback) {
    }

    private record CriterionEvidence(BigDecimal weightedScore, String summary) {
    }

    private record ResolvedDiagnostic(
            ResultDetailDescriptorRegistry.SpeakingFamily family,
            ResultDetailDescriptorRegistry.Definition definition,
            ResultDetailDiagnosticFinding finding
    ) {
    }

    private record DiagnosticState(
            String code,
            String noteVi,
            String noteKo
    ) {
    }

    private record ChipCount(
            ResultDetailDescriptorRegistry.Definition definition,
            ResultDetailPolarity polarity,
            int count,
            String evidenceAvailability
    ) {
        private ChipCount incremented(String nextEvidenceAvailability) {
            String merged = evidenceAvailability.equals(nextEvidenceAvailability)
                    ? evidenceAvailability
                    : "MIXED_EVIDENCE_AVAILABLE";
            return new ChipCount(definition, polarity, count + 1, merged);
        }
    }

    private enum TextKind {
        SUMMARY,
        STRENGTH,
        NEED
    }
}
