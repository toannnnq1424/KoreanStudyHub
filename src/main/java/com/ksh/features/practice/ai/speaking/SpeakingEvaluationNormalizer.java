package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HexFormat;

@Component
public class SpeakingEvaluationNormalizer {
    public static final String SCHEMA_VERSION = SpeakingPromptRules.SCHEMA_VERSION;
    public static final String RUBRIC_VERSION = SpeakingPromptRules.RUBRIC_VERSION;
    public static final String PROMPT_VERSION = SpeakingPromptRules.PROMPT_VERSION;
    private static final BigDecimal LOW_CONFIDENCE = new BigDecimal("0.50");

    public SpeakingEvaluationResult normalize(JsonNode input) {
        try {
            if (input == null || !input.isObject()) {
                return contractFailure("PROVIDER_MALFORMED_JSON");
            }
            SpeakingEvaluationStatus status = enumValue(
                    SpeakingEvaluationStatus.class, text(input, "evaluation_status"));
            if (status == null) {
                return contractFailure("INVALID_EVALUATION_STATUS");
            }
            if (!status.scoreBearing()) {
                return unavailable(status, input);
            }

            BigDecimal transcriptConfidence = confidence(input, "transcript_confidence");
            if (transcriptConfidence == null && input.hasNonNull("transcript_confidence")) {
                return invalidProviderResult("INVALID_TRANSCRIPT_CONFIDENCE");
            }

            boolean lowConfidence = status == SpeakingEvaluationStatus.TRANSCRIPTION_LOW_CONFIDENCE
                    || transcriptConfidence != null
                    && transcriptConfidence.compareTo(LOW_CONFIDENCE) < 0;
            String actuallyHeardTranscript = text(input, "actually_heard_transcript");
            if (actuallyHeardTranscript == null) {
                return invalidProviderResult("MISSING_AUTHORITATIVE_TRANSCRIPT");
            }
            List<SpeakingEvaluationResult.Evidence> evidence = lowConfidence
                    ? List.of()
                    : evidence(input.path("evidence"), actuallyHeardTranscript);
            Map<String, SpeakingEvaluationResult.Evidence> evidenceById =
                    evidenceById(evidence);
            List<SpeakingEvaluationResult.RubricScore> rubrics = lowConfidence
                    ? List.of()
                    : rubrics(input.path("rubric_scores"), evidenceById);
            if (!lowConfidence && rubrics.size() != SpeakingRubricCriterion.values().length) {
                return contractFailure("INVALID_RUBRIC_CONTRACT");
            }
            List<SpeakingEvaluationResult.TranscriptAnnotation> annotations =
                    lowConfidence ? List.of() : transcriptAnnotations(
                            input.path("transcript_annotations"),
                            actuallyHeardTranscript,
                            evidenceById);
            if (!lowConfidence
                    && !scoreEvidenceReconciled(rubrics, annotations, evidenceById)) {
                return contractFailure("SPEAKING_SCORE_EVIDENCE_CONTRADICTION");
            }
            List<SpeakingEvaluationResult.FeedbackItem> strengths =
                    feedbackItems(annotations, true);
            List<SpeakingEvaluationResult.FeedbackItem> needsImprovement =
                    feedbackItems(annotations, false);
            List<String> recommendations = strings(input.path("recommendations"));
            if (lowConfidence) {
                recommendations = appendWarning(recommendations,
                        "Độ tin cậy của bản chép lời thấp; không tạo hồ sơ điểm ngôn ngữ từ bản chép lời này.");
                status = SpeakingEvaluationStatus.TRANSCRIPTION_LOW_CONFIDENCE;
            }

            return new SpeakingEvaluationResult(
                    status,
                    false,
                    source(input, status),
                    text(input, "model"),
                    text(input, "transcription_model"),
                    defaultText(input, "prompt_version", PROMPT_VERSION),
                    defaultText(input, "rubric_version", RUBRIC_VERSION),
                    defaultText(input, "schema_version", SCHEMA_VERSION),
                    text(input, "policy_bundle_id"),
                    SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION,
                    SpeakingEvidenceMode.TRANSCRIPT_ONLY,
                    SpeakingPromptRules.EVIDENCE_CONTRACT_VERSION,
                    SpeakingContractTrust.CURRENT_VERIFIED,
                    longValue(input, "question_version_id"),
                    text(input, "prompt_context_fingerprint"),
                    text(input, "prompt_context_contract_identity"),
                    longValue(input, "audio_media_id"),
                    longValue(input, "media_version"),
                    text(input, "transcript"),
                    text(input, "normalized_transcript"),
                    actuallyHeardTranscript,
                    null,
                    null,
                    transcriptConfidence,
                    null,
                    null,
                    null,
                    transcriptGroundedText(text(input, "overall_summary")),
                    transcriptGroundedText(text(input, "task_achievement_summary")),
                    strengths.stream().map(SpeakingEvaluationResult.FeedbackItem::explanationVi).toList(),
                    needsImprovement.stream().map(SpeakingEvaluationResult.FeedbackItem::explanationVi).toList(),
                    actionPlan(input.path("action_plan")),
                    lowConfidence ? List.of() : criterionFeedback(input.path("criterion_feedback"), rubrics),
                    annotations,
                    strengths,
                    needsImprovement,
                    transcriptGroundedText(text(input, "confidence_notes")),
                    rubrics,
                    // The legacy generic finding has no evidence scope or span and
                    // therefore cannot be promoted to CURRENT_VERIFIED output.
                    List.of(),
                    evidence,
                    recommendations.stream().filter(this::transcriptGroundedClaim).toList(),
                    text(input, "upgraded_answer"),
                    text(input, "sample_answer"),
                    List.of(),
                    List.of(),
                    null,
                    input.path("retryable").asBoolean(false),
                    text(input, "policy_bundle_fingerprint"));
        } catch (RuntimeException ex) {
            return contractFailure("PROVIDER_CONTRACT_INVALID");
        }
    }

    private SpeakingEvaluationResult unavailable(SpeakingEvaluationStatus status, JsonNode input) {
        return emptyResult(
                status,
                source(input, status),
                text(input, "error_category"),
                input.path("retryable").asBoolean(true));
    }

    public SpeakingEvaluationResult contractFailure(String errorCategory) {
        return emptyResult(
                SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED,
                SpeakingEvaluationSource.SYSTEM,
                errorCategory,
                true);
    }

    public SpeakingEvaluationResult invalidProviderResult(String errorCategory) {
        return emptyResult(
                SpeakingEvaluationStatus.INVALID_PROVIDER_RESULT,
                SpeakingEvaluationSource.SYSTEM,
                errorCategory,
                true);
    }

    private SpeakingEvaluationResult emptyResult(
            SpeakingEvaluationStatus status,
            SpeakingEvaluationSource source,
            String errorCategory,
            boolean retryable
    ) {
        return new SpeakingEvaluationResult(
                status, false, source, null, null,
                PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION,
                SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID,
                SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION,
                SpeakingEvidenceMode.TRANSCRIPT_ONLY,
                SpeakingPromptRules.EVIDENCE_CONTRACT_VERSION,
                SpeakingContractTrust.CURRENT_VERIFIED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                errorCategory,
                retryable,
                SpeakingAssessmentPolicyBundle.fingerprint());
    }

    private List<SpeakingEvaluationResult.RubricScore> rubrics(
            JsonNode array,
            Map<String, SpeakingEvaluationResult.Evidence> evidenceById
    ) {
        if (!array.isArray()) {
            return List.of();
        }
        Map<SpeakingRubricCriterion, SpeakingEvaluationResult.RubricScore> values =
                new EnumMap<>(SpeakingRubricCriterion.class);
        for (JsonNode node : array) {
            SpeakingRubricCriterion criterion = SpeakingRubricCriterion.fromExternalId(text(node, "criterion"));
            if (criterion == null) {
                criterion = SpeakingRubricCriterion.fromExternalId(text(node, "criterion_id"));
            }
            if (criterion == null) {
                criterion = SpeakingRubricCriterion.fromExternalId(text(node, "criterionId"));
            }
            if (criterion == null) {
                return List.of();
            }
            if (criterion.requiresAcousticEvidence()) {
                // Compatibility input may still contain six rows. Acoustic rows
                // are never accepted as measurements by this capability.
                continue;
            }
            final SpeakingRubricCriterion currentCriterion = criterion;
            BigDecimal score = decimal(node, "score");
            BigDecimal suppliedMax = decimal(node, "max_score");
            List<String> evidenceIds = strings(node.path("evidence_ids"));
            if (score == null
                    || score.compareTo(BigDecimal.ZERO) < 0
                    || score.compareTo(criterion.maxScore()) > 0
                    || (suppliedMax != null && suppliedMax.compareTo(criterion.maxScore()) != 0)
                    || values.containsKey(criterion)
                    || evidenceIds.isEmpty()
                    || evidenceIds.stream().distinct().count() != evidenceIds.size()
                    || evidenceIds.stream().anyMatch(id -> {
                        SpeakingEvaluationResult.Evidence row = evidenceById.get(id);
                        return row == null
                                || row.criterion() != currentCriterion;
                    })) {
                return List.of();
            }
            values.put(criterion, new SpeakingEvaluationResult.RubricScore(
                    criterion,
                    score.setScale(2, RoundingMode.HALF_UP),
                    criterion.maxScore(),
                    transcriptGroundedText(text(node, "feedback")),
                    SpeakingCriterionAvailability.SCORED,
                    evidenceIds));
        }
        List<SpeakingEvaluationResult.RubricScore> ordered = new ArrayList<>();
        for (SpeakingRubricCriterion criterion : SpeakingRubricCriterion.values()) {
            if (criterion.requiresAcousticEvidence()) {
                ordered.add(new SpeakingEvaluationResult.RubricScore(
                        criterion,
                        null,
                        null,
                        "Chưa chấm: evaluator không nhận bằng chứng âm thanh.",
                        SpeakingCriterionAvailability.NOT_SCORABLE));
            } else {
                if (!values.containsKey(criterion)) {
                    return List.of();
                }
                ordered.add(values.get(criterion));
            }
        }
        return List.copyOf(ordered);
    }

    private List<SpeakingEvaluationResult.Evidence> evidence(
            JsonNode array,
            String actuallyHeardTranscript
    ) {
        if (!array.isArray() || actuallyHeardTranscript == null) {
            throw new IllegalArgumentException("Speaking evidence ledger is required");
        }
        List<SpeakingEvaluationResult.Evidence> rows = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        String expectedHash = sourceHash(actuallyHeardTranscript);
        for (JsonNode node : array) {
            String evidenceId = text(node, "evidence_id");
            SpeakingEvidenceSource source = enumValue(
                    SpeakingEvidenceSource.class, text(node, "source"));
            SpeakingRubricCriterion criterion = SpeakingRubricCriterion.fromExternalId(
                    firstText(node, "criterion_id", "criterion"));
            String subCriterionId = text(node, "sub_criterion_id");
            String scope = text(node, "evidence_scope");
            BigDecimal confidence = confidence(node, "confidence");
            String excerpt = rawText(node, "exact_text");
            Integer startOffset = intValue(node, "start_offset");
            Integer endOffset = intValue(node, "end_offset");
            Integer occurrenceIndex = intValue(node, "occurrence_index");
            Integer occurrenceCount = intValue(node, "occurrence_count");
            String normalization = text(node, "normalization");
            String suppliedHash = text(node, "source_hash");
            if (evidenceId == null || !ids.add(evidenceId)
                    || source != SpeakingEvidenceSource.TRANSCRIPT
                    || criterion == null || !criterion.transcriptGrounded()
                    || !criterion.ownsSubcriterion(subCriterionId)
                    || confidence == null
                    || !"TEXT_SPAN".equals(scope)
                    || !"UTF16_EXACT_V1".equals(normalization)
                    || !expectedHash.equals(suppliedHash)
                    || !validAuthoritativeOccurrence(
                    actuallyHeardTranscript, excerpt, startOffset, endOffset,
                    occurrenceIndex, occurrenceCount)) {
                throw new IllegalArgumentException("Invalid Speaking evidence ledger row");
            }
            rows.add(new SpeakingEvaluationResult.Evidence(
                    evidenceId,
                    source,
                    criterion,
                    subCriterionId,
                    scope,
                    excerpt,
                    startOffset,
                    endOffset,
                    occurrenceIndex,
                    occurrenceCount,
                    normalization,
                    suppliedHash,
                    confidence));
        }
        return List.copyOf(rows);
    }

    private Map<String, SpeakingEvaluationResult.Evidence> evidenceById(
            List<SpeakingEvaluationResult.Evidence> evidence
    ) {
        Map<String, SpeakingEvaluationResult.Evidence> result =
                new LinkedHashMap<>();
        evidence.forEach(row -> result.put(row.evidenceId(), row));
        return Map.copyOf(result);
    }

    private List<SpeakingEvaluationResult.CriterionFeedback> criterionFeedback(
            JsonNode array,
            List<SpeakingEvaluationResult.RubricScore> rubrics
    ) {
        if (!array.isArray()) {
            return List.of();
        }
        List<SpeakingEvaluationResult.CriterionFeedback> rows = new ArrayList<>();
        for (JsonNode node : array) {
            SpeakingRubricCriterion parsedCriterion =
                    SpeakingRubricCriterion.fromExternalId(text(node, "criterion_id"));
            if (parsedCriterion == null) {
                parsedCriterion = SpeakingRubricCriterion.fromExternalId(text(node, "criterion"));
            }
            final SpeakingRubricCriterion criterion = parsedCriterion;
            if (criterion != null && criterion.transcriptGrounded()
                    && transcriptGroundedClaim(text(node, "summary"))) {
                SpeakingEvaluationResult.RubricScore score = rubrics.stream()
                        .filter(row -> row.criterion() == criterion && row.scored())
                        .findFirst()
                        .orElse(null);
                if (score == null) {
                    continue;
                }
                rows.add(new SpeakingEvaluationResult.CriterionFeedback(
                        criterion,
                        text(node, "display_name"),
                        score.score(),
                        score.maxScore(),
                        text(node, "level_label"),
                        transcriptGroundedText(text(node, "summary")),
                        transcriptGroundedStrings(node.path("strengths")),
                        transcriptGroundedStrings(node.path("needs_improvement")),
                        subcriteria(node.path("subcriteria"), criterion)));
            }
        }
        return List.copyOf(rows);
    }

    private List<SpeakingEvaluationResult.SubCriterionFeedback> subcriteria(
            JsonNode array,
            SpeakingRubricCriterion parent
    ) {
        if (!array.isArray()) {
            return List.of();
        }
        List<SpeakingEvaluationResult.SubCriterionFeedback> rows = new ArrayList<>();
        for (JsonNode node : array) {
            String id = text(node, "sub_criterion_id");
            if (id == null) {
                id = text(node, "subCriterionId");
            }
            if (parent != null && parent.ownsSubcriterion(id)
                    && transcriptGroundedClaim(text(node, "summary"))) {
                rows.add(new SpeakingEvaluationResult.SubCriterionFeedback(
                        id,
                        text(node, "display_name"),
                        text(node, "level_label"),
                        transcriptGroundedText(text(node, "summary")),
                        transcriptGroundedStrings(node.path("strengths")),
                        transcriptGroundedStrings(node.path("needs_improvement"))));
            }
        }
        return List.copyOf(rows);
    }

    private List<SpeakingEvaluationResult.TranscriptAnnotation> transcriptAnnotations(
            JsonNode array,
            String actuallyHeardTranscript,
            Map<String, SpeakingEvaluationResult.Evidence> evidenceById
    ) {
        if (!array.isArray()) {
            throw new IllegalArgumentException("Speaking findings ledger is required");
        }
        List<SpeakingEvaluationResult.TranscriptAnnotation> rows = new ArrayList<>();
        Set<String> findingIds = new HashSet<>();
        Set<String> claimedEvidenceIds = new HashSet<>();
        for (JsonNode node : array) {
            String findingId = text(node, "finding_id");
            String evidenceId = text(node, "evidence_id");
            SpeakingEvaluationResult.Evidence evidence = evidenceById.get(evidenceId);
            SpeakingEvidenceSource source = enumValue(SpeakingEvidenceSource.class, text(node, "evidence_source"));
            SpeakingRubricCriterion criterion = SpeakingRubricCriterion.fromExternalId(text(node, "criterion_id"));
            String subCriterionId = text(node, "sub_criterion_id");
            String annotationType = text(node, "annotation_type");
            String operation = text(node, "operation");
            String explanationVi = text(node, "explanation_vi");
            String suggestionKo = rawText(node, "suggestion_ko");
            BigDecimal findingConfidence = confidence(node, "confidence");
            boolean strength = "strength".equals(annotationType);
            boolean improvement = "needs_improvement".equals(annotationType);
            if (findingId == null || !findingIds.add(findingId)
                    || evidenceId == null || !claimedEvidenceIds.add(evidenceId)
                    || evidence == null
                    || source != SpeakingEvidenceSource.TRANSCRIPT
                    || criterion == null || !criterion.transcriptGrounded()
                    || criterion != evidence.criterion()
                    || !java.util.Objects.equals(
                    subCriterionId, evidence.subCriterionId())
                    || !criterion.ownsSubcriterion(subCriterionId)
                    || (!strength && !improvement)
                    || !validFindingOperation(operation, strength)
                    || !validContextualRepetitionFinding(
                    subCriterionId, operation, improvement, evidence)
                    || !transcriptGroundedClaim(explanationVi)
                    || explanationVi == null
                    || !meaningfulFindingFeedback(
                    explanationVi, suggestionKo, evidence.excerpt(), improvement)
                    || findingConfidence == null
                    || strength && suggestionKo != null && !suggestionKo.isEmpty()
                    || improvement && (suggestionKo == null || suggestionKo.isBlank())
                    || !sourceHash(actuallyHeardTranscript).equals(
                    evidence.sourceHash())) {
                throw new IllegalArgumentException(
                        "Invalid Speaking finding/evidence linkage");
            }
            rows.add(new SpeakingEvaluationResult.TranscriptAnnotation(
                    findingId,
                    evidenceId,
                    annotationType,
                    text(node, "category"),
                    criterion,
                    subCriterionId,
                    evidence.excerpt(),
                    suggestionKo,
                    evidence.startOffset(),
                    evidence.endOffset(),
                    evidence.occurrenceIndex(),
                    evidence.occurrenceCount(),
                    evidence.normalization(),
                    evidence.sourceHash(),
                    operation,
                    explanationVi,
                    text(node, "severity"),
                    source,
                    evidence.evidenceScope(),
                    evidence.excerpt(),
                    explanationVi,
                    suggestionKo,
                    findingConfidence));
        }
        return List.copyOf(rows);
    }

    private List<SpeakingEvaluationResult.FeedbackItem> feedbackItems(
            List<SpeakingEvaluationResult.TranscriptAnnotation> annotations,
            boolean strengths
    ) {
        String requiredType = strengths ? "strength" : "needs_improvement";
        return annotations.stream()
                .filter(row -> requiredType.equals(row.annotationType()))
                .map(row -> new SpeakingEvaluationResult.FeedbackItem(
                        row.findingId(),
                        row.evidenceId(),
                        row.criterion(),
                        row.subCriterionId(),
                        row.evidenceScope(),
                        row.evidence(),
                        row.evidenceSource(),
                        row.startOffset(),
                        row.endOffset(),
                        row.occurrenceIndex(),
                        row.occurrenceCount(),
                        row.normalization(),
                        row.sourceHash(),
                        row.operation(),
                        row.category(),
                        row.explanationVi(),
                        strengths ? "" : row.suggestionKo()))
                .toList();
    }

    private boolean scoreEvidenceReconciled(
            List<SpeakingEvaluationResult.RubricScore> rubrics,
            List<SpeakingEvaluationResult.TranscriptAnnotation> annotations,
            Map<String, SpeakingEvaluationResult.Evidence> evidenceById
    ) {
        for (SpeakingEvaluationResult.RubricScore score : rubrics) {
            if (score == null || !score.scored()) {
                continue;
            }
            if (score.evidenceIds().isEmpty()
                    || score.evidenceIds().stream().anyMatch(id -> {
                SpeakingEvaluationResult.Evidence evidence =
                        evidenceById.get(id);
                return evidence == null
                        || evidence.criterion() != score.criterion();
            })) {
                return false;
            }
            boolean confirmedImprovement = annotations.stream().anyMatch(row ->
                    row.criterion() == score.criterion()
                            && "needs_improvement".equals(
                            row.annotationType()));
            if (confirmedImprovement
                    && score.score().compareTo(score.maxScore()) == 0) {
                return false;
            }
        }
        return annotations.stream().allMatch(row ->
                evidenceById.containsKey(row.evidenceId()));
    }

    private boolean validFindingOperation(String operation, boolean strength) {
        if (strength) {
            return "KEEP".equals(operation);
        }
        return Set.of("REPLACE", "REDUNDANT").contains(operation);
    }

    private boolean validContextualRepetitionFinding(
            String subCriterionId,
            String operation,
            boolean improvement,
            SpeakingEvaluationResult.Evidence evidence
    ) {
        if (!"S_VOCAB_REPETITION_CONTROL".equals(subCriterionId)) {
            return true;
        }
        return evidence != null
                && evidence.occurrenceCount() != null
                && evidence.occurrenceCount() >= 2
                && (!improvement || "REDUNDANT".equals(operation));
    }

    private boolean meaningfulFindingFeedback(
            String explanationVi,
            String suggestionKo,
            String evidence,
            boolean improvement
    ) {
        if (explanationVi == null || explanationVi.isBlank()) {
            return false;
        }
        String explanation = explanationVi.trim();
        String exact = evidence == null ? "" : evidence.trim();
        if (explanation.equals(exact)
                || explanation.matches(
                "(?iu)^Bằng chứng bản chép lời xác nhận .+\\.$")
                || explanation.matches(
                "(?iu)^Cần điều chỉnh .+ trong bản chép lời\\.$")) {
            return false;
        }
        if (!improvement) {
            return true;
        }
        return suggestionKo != null
                && !suggestionKo.isBlank()
                && !suggestionKo.trim().equals(exact)
                && !"표현을 더 정확하고 자연스럽게 고쳐 보세요."
                .equals(suggestionKo.trim());
    }

    private boolean validAuthoritativeOccurrence(
            String source,
            String exactText,
            Integer startOffset,
            Integer endOffset,
            Integer occurrenceIndex,
            Integer occurrenceCount
    ) {
        if (source == null || exactText == null || exactText.isBlank()
                || startOffset == null || endOffset == null
                || occurrenceIndex == null || occurrenceCount == null
                || startOffset < 0
                || endOffset != startOffset + exactText.length()
                || endOffset > source.length()
                || !source.substring(startOffset, endOffset).equals(exactText)) {
            return false;
        }
        List<Integer> positions = new ArrayList<>();
        for (int cursor = 0;
             cursor + exactText.length() <= source.length();
             cursor++) {
            if (source.regionMatches(
                    cursor, exactText, 0, exactText.length())) {
                positions.add(cursor);
            }
        }
        return occurrenceCount == positions.size()
                && occurrenceIndex >= 1
                && occurrenceIndex <= positions.size()
                && positions.get(occurrenceIndex - 1).equals(startOffset);
    }

    private String sourceHash(String source) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is required for Speaking evidence identity",
                    exception);
        }
    }

    private List<SpeakingEvaluationResult.ActionPlanItem> actionPlan(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        List<SpeakingEvaluationResult.ActionPlanItem> rows = new ArrayList<>();
        for (JsonNode node : array) {
            SpeakingRubricCriterion criterion = SpeakingRubricCriterion.fromExternalId(text(node, "criterion_id"));
            String subcriterion = text(node, "sub_criterion_id");
            if (criterion != null && criterion.transcriptGrounded()
                    && criterion.ownsSubcriterion(subcriterion)
                    && transcriptGroundedClaim(text(node, "title"))
                    && transcriptGroundedClaim(text(node, "instruction"))
                    && transcriptGroundedClaim(text(node, "reason"))) {
                rows.add(new SpeakingEvaluationResult.ActionPlanItem(
                        criterion,
                        subcriterion,
                        text(node, "title"),
                        text(node, "instruction"),
                        text(node, "reason"),
                        text(node, "priority")));
            }
        }
        return List.copyOf(rows);
    }

    private boolean evidenceAllowed(
            SpeakingEvidenceSource source,
            SpeakingRubricCriterion criterion
    ) {
        // The backend authoritatively injects only the transcript into the
        // normalized provider envelope. Provider-authored prompt/intent claims
        // cannot become CURRENT_VERIFIED evidence at this boundary.
        return source == SpeakingEvidenceSource.TRANSCRIPT
                && criterion != null && criterion.transcriptGrounded();
    }

    private List<String> transcriptGroundedStrings(JsonNode array) {
        return strings(array).stream().filter(this::transcriptGroundedClaim).toList();
    }

    private String transcriptGroundedText(String value) {
        return transcriptGroundedClaim(value) ? value : null;
    }

    private boolean transcriptGroundedClaim(String value) {
        if (value == null || value.isBlank()) {
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

    private List<String> appendWarning(List<String> values, String warning) {
        List<String> result = new ArrayList<>(values);
        result.add(warning);
        return List.copyOf(result);
    }

    private SpeakingEvaluationSource source(JsonNode input, SpeakingEvaluationStatus status) {
        SpeakingEvaluationSource parsed = enumValue(
                SpeakingEvaluationSource.class, text(input, "source"));
        if (parsed != null) {
            return parsed;
        }
        return switch (status) {
            case MOCK_EVALUATED -> SpeakingEvaluationSource.MOCK;
            case TEXT_FALLBACK_EVALUATED -> SpeakingEvaluationSource.TEXT_FALLBACK;
            default -> SpeakingEvaluationSource.PROVIDER;
        };
    }

    private BigDecimal confidence(JsonNode input, String field) {
        BigDecimal value = decimal(input, field);
        return value != null
                && value.compareTo(BigDecimal.ZERO) >= 0
                && value.compareTo(BigDecimal.ONE) <= 0 ? value : null;
    }

    private BigDecimal decimal(JsonNode input, String field) {
        JsonNode value = input.get(field);
        return value != null && value.isNumber() ? value.decimalValue() : null;
    }

    private Long longValue(JsonNode input, String field) {
        JsonNode value = input.get(field);
        return value != null && value.canConvertToLong() ? value.longValue() : null;
    }

    private Integer intValue(JsonNode input, String field) {
        JsonNode value = input.get(field);
        return value != null && value.canConvertToInt()
                ? value.intValue() : null;
    }

    private String defaultText(JsonNode input, String field, String fallback) {
        String value = text(input, field);
        return value == null ? fallback : value;
    }

    private String text(JsonNode input, String field) {
        if (input == null) {
            return null;
        }
        JsonNode value = input.get(field);
        if (value == null || !value.isTextual()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private String rawText(JsonNode input, String field) {
        if (input == null) {
            return null;
        }
        JsonNode value = input.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private String firstText(JsonNode input, String first, String second) {
        String value = text(input, first);
        return value == null ? text(input, second) : value;
    }

    private List<String> strings(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        array.forEach(node -> {
            if (node.isTextual() && !node.asText().isBlank()) {
                values.add(node.asText().trim());
            }
        });
        return List.copyOf(values);
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
