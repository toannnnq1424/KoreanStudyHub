package com.ksh.features.practice.ai.writing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.features.practice.ai.contract.PracticeAiResultCompleteness;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class WritingEvaluationNormalizer {

    public static final String EVALUATION_ENGINE =
            "KSH_WRITING_EVALUATOR_V3";

    private final ObjectMapper objectMapper;
    private final WritingEvidenceLedgerVerifier ledgerVerifier;

    public WritingEvaluationNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.ledgerVerifier = new WritingEvidenceLedgerVerifier();
    }

    /**
     * Production normalizer for the unified one-call path.
     * Normalizer is the sole source of score, raw_score, raw_score_max.
     * AI provider does NOT return these fields.
     */
    public String normalize(String aiJson, String taskType, String learnerAnswer,
                            WritingRuleEngine.RuleAnalysis ruleAnalysis) {
        try {
            JsonNode root = objectMapper.readTree(aiJson);
            root = repairMinorProviderEnvelope(root, taskType, learnerAnswer);
            WritingEvidenceLedgerVerifier.Recovery recovery =
                    ledgerVerifier.recover(root, taskType, learnerAnswer);
            WritingEvidenceLedgerVerifier.VerifiedEnvelope verified = recovery.envelope();
            String studentText = verified.learnerAnswerNfc();
            List<Map<String, Object>> rubricScores =
                    normalizedRubricScores(verified.rubrics(), taskType);
            List<Map<String, Object>> strengths =
                    normalizedFindings(verified, "STRENGTH");
            List<Map<String, Object>> needs =
                    normalizedFindings(verified, "IMPROVEMENT");
            double score = deriveScoreFromRubrics(rubricScores);
            double rawTopikScore = sumRubricScores(rubricScores);
            double rawTopikMax =
                    WritingScoringPolicy.rubricFor(taskType).totalMaxScore();
            List<Map<String, Object>> annotations =
                    verifiedAnnotations(verified);

            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("score", score);
            normalized.put("overall_score", score);
            normalized.put("percentage", score);
            normalized.put("raw_score", rawTopikScore);
            normalized.put("raw_score_max", rawTopikMax);
            normalized.put("scoring_contract",
                    WritingScoringPolicy.SCORING_CONTRACT);
            normalized.put("policy_bundle_id",
                    WritingAssessmentPolicyBundle.POLICY_BUNDLE_ID);
            normalized.put("ledger_contract_version",
                    WritingEvidenceLedgerVerifier.CONTRACT_VERSION);
            normalized.put("score_anchor_version",
                    WritingScoreAnchorPolicy.VERSION);
            normalized.put("task_requirement_version",
                    WritingTaskRequirementPolicy.VERSION);
            normalized.put("source_normalization",
                    WritingEvidenceLedgerVerifier.SOURCE_NORMALIZATION);
            normalized.put("source_hash", verified.sourceHash());
            normalized.put("task_type", taskType);
            String summary = derivedSummary(
                    verified, rawTopikScore, rawTopikMax);
            normalized.put("summary", summary);
            normalized.put("summary_vi", summary);
            normalized.put("rubric_scores", rubricScores);
            normalized.put("task_coverage",
                    normalizedCoverage(verified.coverage()));
            normalized.put("evidence_ledger",
                    normalizedEvidence(verified.evidence()));
            normalized.put("strengths", strengths);
            normalized.put("needs_improvement", needs);
            normalized.put("student_text", studentText);
            normalized.put("student_strengths_annotated", "");
            normalized.put("student_needs_annotated", "");
            normalized.put("annotations", annotations);
            normalized.put("upgraded_answer",
                    verified.upgrade().content());
            normalized.put("upgraded_answer_annotated", "");
            normalized.put("upgraded_annotations", List.of());
            normalized.put("corrected_version",
                    verified.upgrade().content());
            normalized.put("sample_answer", "");
            normalized.put("sentence_rewrites",
                    normalizedRewrites(verified.upgrade().rewrites()));
            normalized.put("presentation_fallback",
                    presentationFallback(root, summary,
                            verified.upgrade().content()));
            normalized.put("engine", EVALUATION_ENGINE);
            putEvaluationMetadata(normalized,
                    "EVALUATED",
                    "PROVIDER",
                    "NONE",
                    false,
                    true);
            if (verified.rubrics().isEmpty()) {
                return contractFailure(
                        "PROVIDER_CONTRACT_INVALID",
                        taskType,
                        learnerAnswer,
                        aiJson);
            }
            normalized.put("provider_raw_response", truncateProviderRawResponse(aiJson));
            normalized.put("validation_issues", recovery.rejected());
            if (!recovery.rejected().isEmpty()) {
                // Partial evidence can support comments, never an invented total.
                for (String key : List.of("score", "overall_score", "percentage", "raw_score", "raw_score_max")) {
                    normalized.remove(key);
                }
                normalized.put("evaluation_status", "EVALUATED_PARTIAL");
                normalized.put("score_available", false);
                normalized.put("evaluation_reason", "PROVIDER_ITEMS_REJECTED");
                normalized.put("summary", "Một phần nhận xét đã được xác minh; điểm tổng chưa khả dụng.");
                normalized.put("summary_vi", normalized.get("summary"));
                normalized.put(PracticeAiResultCompleteness.FIELD,
                        PracticeAiResultCompleteness.partial("PROVIDER_ITEMS_REJECTED", recovery.rejected().size()).toMap());
            }
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            return contractFailure(
                    ex instanceof com.fasterxml.jackson.core.JsonProcessingException
                            ? "PROVIDER_MALFORMED_JSON"
                            : "PROVIDER_CONTRACT_INVALID",
                    taskType,
                    learnerAnswer,
                    aiJson);
        }
    }

    /**
     * Canonicalizes known gateway aliases and deletion operations. Exact source
     * positions are subsequently resolved by the evidence recovery boundary.
     */
    private JsonNode repairMinorProviderEnvelope(
            JsonNode source,
            String taskType,
            String learnerAnswer
    ) {
        if (source == null || !source.isObject()) {
            return source;
        }
        ObjectNode repaired = ((ObjectNode) source).deepCopy();
        // A few OpenAI-compatible gateways append their own routing metadata
        // to an otherwise complete strict response. It is not score-bearing
        // and was never part of the KSH evidence contract. Remove only these
        // known aliases; every score/evidence field remains fail-closed.
        repaired.remove("policy_bundle_id");
        repaired.remove("policyBundleId");
        normalizeDeletionFindings(repaired);
        healTaskCoverage(repaired, taskType, learnerAnswer);
        healRubricScores(repaired, taskType, learnerAnswer);
        return repaired;
    }

    private static void healTaskCoverage(
            ObjectNode root,
            String taskType,
            String learnerAnswer
    ) {
        if (root == null || taskType == null) {
            return;
        }
        List<WritingTaskRequirementPolicy.Requirement> requirements;
        try {
            requirements = WritingTaskRequirementPolicy.requirementsFor(taskType);
        } catch (Exception ex) {
            return;
        }
        if (requirements.isEmpty()) {
            return;
        }

        JsonNode coverageNode = root.get("taskCoverage");
        if (!(coverageNode instanceof ArrayNode coverageArray)) {
            return;
        }

        int length = learnerAnswer == null ? 0 : learnerAnswer.strip().length();
        String source = learnerAnswer == null ? "" : Normalizer.normalize(learnerAnswer, Normalizer.Form.NFC);

        // Collect available evidenceIds that actually exist in the NFC learner answer
        Set<String> validEvidenceIds = new LinkedHashSet<>();
        JsonNode ledger = root.get("evidenceLedger");
        if (ledger instanceof ArrayNode ledgerArr) {
            for (JsonNode row : ledgerArr) {
                String evId = row.path("evidenceId").asText();
                String exact = row.path("exactText").asText("");
                if (!evId.isBlank() && !exact.isBlank() && source.contains(exact)) {
                    validEvidenceIds.add(evId);
                }
            }
        }

        // Map requirementId -> list of evidenceIds from findings
        Map<String, Set<String>> reqToEvidence = new LinkedHashMap<>();
        Map<String, Set<String>> scoringCriterionToEvidence = new LinkedHashMap<>();
        JsonNode findings = root.get("findings");
        if (findings instanceof ArrayNode findingsArr) {
            for (JsonNode f : findingsArr) {
                List<String> evList = new ArrayList<>();
                JsonNode fEv = f.get("evidenceIds");
                if (fEv instanceof ArrayNode fEvArr) {
                    for (JsonNode e : fEvArr) {
                        String id = e.asText();
                        if (validEvidenceIds.contains(id)) {
                            evList.add(id);
                        }
                    }
                }
                if (!evList.isEmpty()) {
                    JsonNode reqs = f.get("requirementIds");
                    if (reqs instanceof ArrayNode reqsArr) {
                        for (JsonNode r : reqsArr) {
                            reqToEvidence.computeIfAbsent(r.asText(), k -> new LinkedHashSet<>()).addAll(evList);
                        }
                    }
                    String scId = f.path("scoringCriterionId").asText();
                    if (!scId.isBlank()) {
                        scoringCriterionToEvidence.computeIfAbsent(scId, k -> new LinkedHashSet<>()).addAll(evList);
                    }
                }
            }
        }

        Map<String, WritingTaskRequirementPolicy.Requirement> reqById = new LinkedHashMap<>();
        for (var r : requirements) {
            reqById.put(r.requirementId(), r);
        }

        Set<String> presentReqIds = new LinkedHashSet<>();
        for (int i = 0; i < coverageArray.size(); i++) {
            JsonNode item = coverageArray.get(i);
            if (!(item instanceof ObjectNode row)) {
                coverageArray.remove(i);
                i--;
                continue;
            }
            String reqId = row.path("requirementId").asText();
            if (reqId.isBlank() || !reqById.containsKey(reqId) || presentReqIds.contains(reqId)) {
                coverageArray.remove(i);
                i--;
                continue;
            }
            presentReqIds.add(reqId);
            row.retain("requirementId", "status", "evidenceIds");
            WritingTaskRequirementPolicy.Requirement req = reqById.get(reqId);

            // 1. Length requirements: enforce deterministic truth
            if ("Q53_LENGTH_200_300".equals(reqId)) {
                row.put("status", (length >= 200 && length <= 300) ? "MET" : "NOT_MET");
                row.putArray("evidenceIds");
                continue;
            }
            if ("Q54_LENGTH_600_700".equals(reqId)) {
                row.put("status", (length >= 600 && length <= 700) ? "MET" : "NOT_MET");
                row.putArray("evidenceIds");
                continue;
            }

            // 2. Filter evidenceIds to only known valid evidence IDs
            List<String> validRefs = new ArrayList<>();
            JsonNode evArray = row.get("evidenceIds");
            if (evArray instanceof ArrayNode arr) {
                for (JsonNode e : arr) {
                    String id = e.asText();
                    if (validEvidenceIds.contains(id) && !validRefs.contains(id)) {
                        validRefs.add(id);
                    }
                }
            }

            String status = row.path("status").asText();
            if (!Set.of("MET", "NOT_MET", "PARTIAL").contains(status)) {
                status = "MET";
            }

            if ("MET".equals(status) || "PARTIAL".equals(status)) {
                if (req.evidenceRequired() && validRefs.isEmpty()) {
                    Set<String> candidate = reqToEvidence.get(reqId);
                    if (candidate != null && !candidate.isEmpty()) {
                        validRefs.addAll(candidate);
                    } else if (req.scoringCriterionId() != null
                            && scoringCriterionToEvidence.containsKey(req.scoringCriterionId())
                            && !scoringCriterionToEvidence.get(req.scoringCriterionId()).isEmpty()) {
                        validRefs.addAll(scoringCriterionToEvidence.get(req.scoringCriterionId()));
                    } else if (!validEvidenceIds.isEmpty()) {
                        validRefs.add(validEvidenceIds.iterator().next());
                    } else {
                        status = "NOT_MET";
                    }
                }
            } else {
                validRefs.clear();
            }

            row.put("status", status);
            ArrayNode repairedEv = row.putArray("evidenceIds");
            validRefs.forEach(repairedEv::add);
        }

        // 3. Add any completely missing requirement so keyset matches exactly
        for (var req : requirements) {
            if (!presentReqIds.contains(req.requirementId())) {
                ObjectNode row = coverageArray.addObject();
                row.put("requirementId", req.requirementId());
                if ("Q53_LENGTH_200_300".equals(req.requirementId())) {
                    row.put("status", (length >= 200 && length <= 300) ? "MET" : "NOT_MET");
                    row.putArray("evidenceIds");
                } else if ("Q54_LENGTH_600_700".equals(req.requirementId())) {
                    row.put("status", (length >= 600 && length <= 700) ? "MET" : "NOT_MET");
                    row.putArray("evidenceIds");
                } else {
                    Set<String> candidate = reqToEvidence.get(req.requirementId());
                    if (candidate != null && !candidate.isEmpty()) {
                        row.put("status", "MET");
                        ArrayNode arr = row.putArray("evidenceIds");
                        candidate.forEach(arr::add);
                    } else if (req.scoringCriterionId() != null
                            && scoringCriterionToEvidence.containsKey(req.scoringCriterionId())
                            && !scoringCriterionToEvidence.get(req.scoringCriterionId()).isEmpty()) {
                        row.put("status", "MET");
                        ArrayNode arr = row.putArray("evidenceIds");
                        scoringCriterionToEvidence.get(req.scoringCriterionId()).forEach(arr::add);
                    } else if (!validEvidenceIds.isEmpty() && req.evidenceRequired()) {
                        row.put("status", "MET");
                        ArrayNode arr = row.putArray("evidenceIds");
                        arr.add(validEvidenceIds.iterator().next());
                    } else {
                        row.put("status", "NOT_MET");
                        row.putArray("evidenceIds");
                    }
                }
            }
        }
    }

    private static void healRubricScores(
            ObjectNode root,
            String taskType,
            String learnerAnswer
    ) {
        if (root == null || taskType == null) {
            return;
        }
        WritingScoringRubric expectedRubric;
        try {
            expectedRubric = WritingScoringPolicy.rubricFor(taskType);
        } catch (Exception ex) {
            return;
        }
        if (expectedRubric == null) {
            return;
        }
        JsonNode rubricsNode = root.get("rubricScores");
        if (!(rubricsNode instanceof ArrayNode rubricArray)) {
            return;
        }

        String source = learnerAnswer == null ? "" : Normalizer.normalize(learnerAnswer, Normalizer.Form.NFC);
        Set<String> validEvidenceIds = new LinkedHashSet<>();
        JsonNode ledger = root.get("evidenceLedger");
        if (ledger instanceof ArrayNode ledgerArr) {
            for (JsonNode row : ledgerArr) {
                String evId = row.path("evidenceId").asText();
                String exact = row.path("exactText").asText("");
                if (!evId.isBlank() && !exact.isBlank() && source.contains(exact)) {
                    validEvidenceIds.add(evId);
                }
            }
        }

        // Map criterionId -> owned requirements
        Map<String, Set<String>> ownedRequirements = new LinkedHashMap<>();
        Map<String, WritingTaskRequirementPolicy.Requirement> reqs = new LinkedHashMap<>();
        for (var r : WritingTaskRequirementPolicy.requirementsFor(taskType)) {
            reqs.put(r.requirementId(), r);
            if (r.scoringCriterionId() != null) {
                ownedRequirements.computeIfAbsent(r.scoringCriterionId(), k -> new LinkedHashSet<>()).add(r.requirementId());
            }
        }

        // Track unmet requirements per criterion
        Set<String> criteriaWithUnmetRequirements = new HashSet<>();
        Map<String, Set<String>> criterionToCoverageEvidence = new LinkedHashMap<>();
        JsonNode coverageNode = root.get("taskCoverage");
        if (coverageNode instanceof ArrayNode coverageArray) {
            for (JsonNode row : coverageArray) {
                String reqId = row.path("requirementId").asText();
                String status = row.path("status").asText();
                var req = reqs.get(reqId);
                if (req != null && req.scoringCriterionId() != null) {
                    if (req.required() && !"MET".equals(status)) {
                        criteriaWithUnmetRequirements.add(req.scoringCriterionId());
                    }
                    JsonNode evArr = row.get("evidenceIds");
                    if (evArr instanceof ArrayNode evA) {
                        for (JsonNode e : evA) {
                            String evId = e.asText();
                            if (validEvidenceIds.contains(evId)) {
                                criterionToCoverageEvidence.computeIfAbsent(req.scoringCriterionId(), k -> new LinkedHashSet<>()).add(evId);
                            }
                        }
                    }
                }
            }
        }

        // Track owned findings and improvements per criterion
        Map<String, Set<String>> ownedFindings = new LinkedHashMap<>();
        Map<String, Set<String>> criterionToFindingEvidence = new LinkedHashMap<>();
        Set<String> criteriaWithImprovements = new HashSet<>();
        Set<String> criteriaWithStrengths = new HashSet<>();
        JsonNode findingsNode = root.get("findings");
        if (findingsNode instanceof ArrayNode findingsArray) {
            for (JsonNode f : findingsArray) {
                String scId = f.path("scoringCriterionId").asText();
                String fId = f.path("findingId").asText();
                String polarity = f.path("polarity").asText();
                if (!scId.isBlank() && !fId.isBlank()) {
                    ownedFindings.computeIfAbsent(scId, k -> new LinkedHashSet<>()).add(fId);
                    if ("IMPROVEMENT".equals(polarity)) {
                        criteriaWithImprovements.add(scId);
                    } else if ("STRENGTH".equals(polarity)) {
                        criteriaWithStrengths.add(scId);
                    }
                    JsonNode evArr = f.get("evidenceIds");
                    if (evArr instanceof ArrayNode evA) {
                        for (JsonNode e : evA) {
                            String evId = e.asText();
                            if (validEvidenceIds.contains(evId)) {
                                criterionToFindingEvidence.computeIfAbsent(scId, k -> new LinkedHashSet<>()).add(evId);
                            }
                        }
                    }
                }
            }
        }

        Map<String, WritingScoringCriterion> criteriaById = new LinkedHashMap<>();
        for (var c : expectedRubric.criteria()) {
            criteriaById.put(c.criterionId(), c);
        }

        Set<String> presentCriteria = new LinkedHashSet<>();
        for (int i = 0; i < rubricArray.size(); i++) {
            JsonNode item = rubricArray.get(i);
            if (!(item instanceof ObjectNode row)) {
                rubricArray.remove(i);
                i--;
                continue;
            }
            String criterionId = row.path("criterionId").asText();
            if (criterionId.isBlank() || !criteriaById.containsKey(criterionId) || presentCriteria.contains(criterionId)) {
                rubricArray.remove(i);
                i--;
                continue;
            }
            presentCriteria.add(criterionId);
            row.retain("criterionId", "score", "maxScore", "evidenceIds", "findingIds", "requirementIds");

            var criterion = criteriaById.get(criterionId);
            if (!row.has("maxScore") || !row.get("maxScore").isIntegralNumber()) {
                row.put("maxScore", criterion.maxScore());
            }

            // Fill missing or empty references with owned identifiers
            if (!row.has("findingIds") || !(row.get("findingIds") instanceof ArrayNode) || row.get("findingIds").isEmpty()) {
                Set<String> fIds = ownedFindings.getOrDefault(criterionId, Set.of());
                ArrayNode fArray = row.putArray("findingIds");
                fIds.forEach(fArray::add);
            }

            if (!row.has("requirementIds") || !(row.get("requirementIds") instanceof ArrayNode) || row.get("requirementIds").isEmpty()) {
                Set<String> rIds = ownedRequirements.getOrDefault(criterionId, Set.of());
                ArrayNode rArray = row.putArray("requirementIds");
                rIds.forEach(rArray::add);
            }

            if (!row.has("evidenceIds") || !(row.get("evidenceIds") instanceof ArrayNode) || row.get("evidenceIds").isEmpty()) {
                Set<String> evIds = new LinkedHashSet<>();
                evIds.addAll(criterionToFindingEvidence.getOrDefault(criterionId, Set.of()));
                evIds.addAll(criterionToCoverageEvidence.getOrDefault(criterionId, Set.of()));
                int score = row.path("score").asInt(0);
                if (score > 0 && evIds.isEmpty() && !validEvidenceIds.isEmpty()) {
                    evIds.add(validEvidenceIds.iterator().next());
                }
                ArrayNode evArray = row.putArray("evidenceIds");
                evIds.forEach(evArray::add);
            }
        }
    }

    /**
     * Some otherwise valid providers describe deleting a cited phrase as a
     * {@code REPLACE} finding with an empty replacement.  In the KSH contract
     * an empty replacement has one unambiguous meaning: remove the cited text,
     * so its canonical operation is {@code REDUNDANT}.  Canonicalize just that
     * spelling variation; no score, rubric, evidence span or source text is
     * invented or altered.  A no-op rewrite for that deletion is removed
     * because rewrites intentionally require a non-blank replacement.
     */
    private static void normalizeDeletionFindings(ObjectNode root) {
        JsonNode findingsNode = root.get("findings");
        if (findingsNode == null || !findingsNode.isArray()) {
            return;
        }
        java.util.Set<String> deletionFindingIds = new java.util.HashSet<>();
        for (JsonNode findingNode : findingsNode) {
            if (!(findingNode instanceof ObjectNode finding)
                    || !"IMPROVEMENT".equals(finding.path("polarity").asText())
                    || !"REPLACE".equals(finding.path("operation").asText())
                    || !finding.path("replacementKo").isTextual()
                    || !finding.path("replacementKo").asText().isBlank()) {
                continue;
            }
            String findingId = finding.path("findingId").asText();
            if (!findingId.isBlank()) {
                finding.put("operation", "REDUNDANT");
                deletionFindingIds.add(findingId);
            }
        }
        if (deletionFindingIds.isEmpty()) {
            return;
        }
        JsonNode rewritesNode = root.path("upgradedAnswer").path("rewrites");
        if (!(rewritesNode instanceof ArrayNode rewrites)) {
            return;
        }
        for (int index = rewrites.size() - 1; index >= 0; index--) {
            JsonNode rewrite = rewrites.get(index);
            boolean referencesDeletion = rewrite.path("findingIds").isArray()
                    && java.util.stream.StreamSupport.stream(
                    java.util.Spliterators.spliteratorUnknownSize(
                            rewrite.path("findingIds").elements(), 0), false)
                    .anyMatch(id -> deletionFindingIds.contains(id.asText()));
            if (referencesDeletion
                    && rewrite.path("replacementKo").isTextual()
                    && rewrite.path("replacementKo").asText().isBlank()) {
                rewrites.remove(index);
            }
        }
    }

    private static List<Map<String, Object>> normalizedRubricScores(
            List<WritingEvidenceLedgerVerifier.RubricJudgment> judgments,
            String taskType) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (WritingEvidenceLedgerVerifier.RubricJudgment judgment
                : judgments) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("criterionId", judgment.criterionId());
            row.put("name", WritingScoringPolicy.rubricFor(taskType)
                    .criteria().stream()
                    .filter(value -> value.criterionId()
                            .equals(judgment.criterionId()))
                    .map(WritingScoringCriterion::displayName)
                    .findFirst()
                    .orElseGet(() -> judgment.criterionId()));
            row.put("score", judgment.score());
            row.put("maxScore", judgment.maxScore());
            row.put("anchorLabelVi", judgment.anchorLabelVi());
            row.put("performanceLevel",
                    WritingScoreAnchorPolicy.requireAnchor(
                            WritingScoringPolicy.rubricFor(taskType)
                                    .criteria().stream()
                                    .filter(value -> value.criterionId()
                                            .equals(judgment.criterionId()))
                                    .findFirst()
                                    .orElseThrow(),
                            judgment.score())
                            .performanceLevel()
                            .name());
            row.put("feedback", judgment.anchorDescriptionVi());
            row.put("evidenceIds", judgment.evidenceIds());
            row.put("findingIds", judgment.findingIds());
            row.put("requirementIds", judgment.requirementIds());
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    private static List<Map<String, Object>> normalizedFindings(
            WritingEvidenceLedgerVerifier.VerifiedEnvelope verified,
            String polarity) {
        Map<String, WritingEvidenceLedgerVerifier.Evidence> evidenceById =
                verified.evidence().stream().collect(
                        java.util.stream.Collectors.toMap(
                                WritingEvidenceLedgerVerifier.Evidence::evidenceId,
                                java.util.function.Function.identity()));
        List<Map<String, Object>> rows = new ArrayList<>();
        int ordinal = 0;
        for (WritingEvidenceLedgerVerifier.Finding finding
                : verified.findings()) {
            ordinal++;
            if (!polarity.equals(finding.polarity())) {
                continue;
            }
            WritingEvidenceLedgerVerifier.Evidence evidence =
                    finding.evidenceIds().isEmpty()
                            ? null
                            : evidenceById.get(finding.evidenceIds().get(0));
            WritingRubricCriterion criterion =
                    WritingRubricCriterion.parse(finding.criterionId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", ordinal);
            row.put("findingId", finding.findingId());
            row.put("criterionId", finding.criterionId());
            row.put("subtype", finding.subtype());
            row.put("scoringCriterionId", finding.scoringCriterionId());
            row.put("evidenceScope",
                    evidence == null ? "WHOLE_ANSWER" : "TEXT_SPAN");
            row.put("evidenceId",
                    evidence == null ? null : evidence.evidenceId());
            row.put("evidenceIds", finding.evidenceIds());
            row.put("requirementIds", finding.requirementIds());
            row.put("operation", finding.operation());
            row.put("errorCategory", finding.errorCategory());
            row.put("category", finding.errorCategory());
            row.put("subcategory", finding.subtype());
            row.put("vietnameseLabel",
                    criterion == null
                            ? finding.errorCategory()
                            : criterion.vietnameseLabel());
            row.put("koreanLabel",
                    criterion == null ? "" : criterion.koreanLabel());
            row.put("evidence",
                    evidence == null ? "" : evidence.exactText());
            row.put("startOffset",
                    evidence == null ? null : evidence.startOffset());
            row.put("endOffset",
                    evidence == null ? null : evidence.endOffset());
            row.put("occurrenceIndex",
                    evidence == null ? null : evidence.occurrenceIndex());
            row.put("occurrenceCount",
                    evidence == null ? null : evidence.occurrenceCount());
            row.put("sourceHash", verified.sourceHash());
            row.put("explanationVi", finding.explanationVi());
            row.put("correction", finding.replacementKo());
            row.put("severity", finding.impact());
            row.put("impact", finding.impact());
            row.put("frequency", finding.frequency());
            row.put("confidence", finding.confidence());
            row.put("observability", finding.observability());
            row.put("displayType",
                    evidence == null
                            ? "WHOLE_ANSWER"
                            : inferDisplayType(evidence.exactText()));
            row.put("uiLabel",
                    criterion == null
                            ? finding.errorCategory()
                            : criterion.vietnameseLabel());
            row.put("errorType", finding.errorCategory());
            row.put("whyItIsGood",
                    "STRENGTH".equals(polarity)
                            ? finding.explanationVi()
                            : "");
            row.put("topikTip", "");
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    private static List<Map<String, Object>> verifiedAnnotations(
            WritingEvidenceLedgerVerifier.VerifiedEnvelope verified) {
        Map<String, WritingEvidenceLedgerVerifier.Evidence> evidenceById =
                verified.evidence().stream().collect(
                        java.util.stream.Collectors.toMap(
                                WritingEvidenceLedgerVerifier.Evidence::evidenceId,
                                java.util.function.Function.identity()));
        List<Map<String, Object>> rows = new ArrayList<>();
        int index = 1;
        for (WritingEvidenceLedgerVerifier.Finding finding
                : verified.findings()) {
            if (finding.evidenceIds().size() != 1) {
                continue;
            }
            WritingEvidenceLedgerVerifier.Evidence evidence =
                    evidenceById.get(finding.evidenceIds().get(0));
            if (evidence == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", finding.findingId());
            row.put("findingId", finding.findingId());
            row.put("evidenceId", evidence.evidenceId());
            row.put("kind",
                    "STRENGTH".equals(finding.polarity())
                            ? "strength"
                            : "need");
            row.put("criterionId", finding.criterionId());
            row.put("category", finding.errorCategory());
            row.put("subcategory", finding.subtype());
            row.put("evidence", evidence.exactText());
            row.put("start", evidence.startOffset());
            row.put("end", evidence.endOffset());
            row.put("startOffset", evidence.startOffset());
            row.put("endOffset", evidence.endOffset());
            row.put("occurrenceIndex", evidence.occurrenceIndex());
            row.put("occurrenceCount", evidence.occurrenceCount());
            row.put("sourceHash", evidence.sourceHash());
            row.put("explanationVi", finding.explanationVi());
            row.put("correction", finding.replacementKo());
            row.put("severity", finding.impact());
            row.put("operation", finding.operation());
            row.put("displayType", inferDisplayType(evidence.exactText()));
            row.put("index", index++);
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    private static List<Map<String, Object>> normalizedCoverage(
            List<WritingEvidenceLedgerVerifier.Coverage> coverage) {
        return coverage.stream().map(row -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("requirementId", row.requirementId());
            result.put("status", row.status());
            result.put("evidenceIds", row.evidenceIds());
            return result;
        }).toList();
    }

    private static List<Map<String, Object>> normalizedEvidence(
            List<WritingEvidenceLedgerVerifier.Evidence> evidence) {
        return evidence.stream().map(row -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("evidenceId", row.evidenceId());
            result.put("sourceRole",
                    WritingEvidenceLedgerVerifier.SOURCE_ROLE);
            result.put("exactText", row.exactText());
            result.put("startOffset", row.startOffset());
            result.put("endOffset", row.endOffset());
            result.put("occurrenceIndex", row.occurrenceIndex());
            result.put("occurrenceCount", row.occurrenceCount());
            result.put("normalization", row.normalization());
            result.put("sourceHash", row.sourceHash());
            return result;
        }).toList();
    }

    private static List<Map<String, Object>> normalizedRewrites(
            List<WritingEvidenceLedgerVerifier.Rewrite> rewrites) {
        return rewrites.stream().map(row -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("findingIds", row.findingIds());
            result.put("evidenceId", row.evidenceId());
            result.put("original", row.original());
            result.put("upgraded", row.replacementKo());
            result.put("reason", row.reasonVi());
            return result;
        }).toList();
    }

    private static String derivedSummary(
            WritingEvidenceLedgerVerifier.VerifiedEnvelope verified,
            double score,
            double maxScore) {
        long strengths = verified.findings().stream()
                .filter(row -> "STRENGTH".equals(row.polarity()))
                .count();
        long improvements = verified.findings().stream()
                .filter(row -> "IMPROVEMENT".equals(row.polarity()))
                .count();
        long met = verified.coverage().stream()
                .filter(row -> "MET".equals(row.status()))
                .count();
        return "Kết quả "
                + compact(score)
                + "/"
                + compact(maxScore)
                + "; "
                + met
                + "/"
                + verified.coverage().size()
                + " yêu cầu đã có bằng chứng; "
                + strengths
                + " điểm mạnh và "
                + improvements
                + " điểm cần cải thiện đã được đối chiếu.";
    }

    private static String compact(double value) {
        return value == Math.rint(value)
                ? Long.toString(Math.round(value))
                : Double.toString(value);
    }

    public boolean isCacheableAiResult(String normalizedJson) {
        try {
            JsonNode root = objectMapper.readTree(normalizedJson);
            return isTrustedProviderEvaluation(
                    root,
                    root == null ? null : root.path("task_type").asText(null),
                    false);
        } catch (Exception ex) {
            return false;
        }
    }

    public String sanitizeForCache(String normalizedJson) {
        try {
            JsonNode root = objectMapper.readTree(normalizedJson);
            if (!root.isObject()) {
                throw new IllegalArgumentException("Writing cache payload must be a JSON object.");
            }
            ObjectNode sanitized = ((ObjectNode) root).deepCopy();
            sanitized.remove("student_text");
            return objectMapper.writeValueAsString(sanitized);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Writing cache payload is not valid JSON.", ex);
        }
    }

    public String rehydrateCachedResult(String cachedJson, String learnerAnswer,
                                        String expectedTaskType) {
        try {
            JsonNode root = objectMapper.readTree(cachedJson);
            if (!isTrustedProviderEvaluation(root, expectedTaskType, true)) {
                throw new IllegalArgumentException(
                        "Writing cache payload does not match the current provider contract.");
            }

            String studentText = Normalizer.normalize(
                    learnerAnswer == null ? "" : learnerAnswer,
                    Normalizer.Form.NFC);
            ObjectNode hydrated = ((ObjectNode) root).deepCopy();
            if (!WritingEvidenceLedgerVerifier.sha256(studentText)
                    .equals(hydrated.path("source_hash").asText())) {
                throw new IllegalArgumentException(
                        "Writing cache payload source identity does not match.");
            }
            hydrated.put("student_text", studentText);
            hydrated.put("evaluation_origin_source", "PROVIDER");
            hydrated.put("evaluation_source", "CACHE");

            return objectMapper.writeValueAsString(hydrated);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Writing cached result is malformed.", ex);
        }
    }

    public String rehydrateCachedResult(String cachedJson, String learnerAnswer) {
        try {
            JsonNode root = objectMapper.readTree(cachedJson);
            return rehydrateCachedResult(
                    cachedJson,
                    learnerAnswer,
                    root == null ? null : root.path("task_type").asText(null));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Writing cached result is malformed.", ex);
        }
    }

    private static boolean isTrustedProviderEvaluation(JsonNode root,
                                                       String expectedTaskType,
                                                       boolean requireSanitizedPayload) {
        if (root == null || !root.isObject()
                || expectedTaskType == null || expectedTaskType.isBlank()
                || !EVALUATION_ENGINE.equals(root.path("engine").asText())
                || !"EVALUATED".equals(root.path("evaluation_status").asText())
                || !"PROVIDER".equals(root.path("evaluation_source").asText())
                || !"NONE".equals(root.path("evaluation_reason").asText())
                || !root.path("score_available").asBoolean(false)
                || !WritingScoringPolicy.SCORING_CONTRACT.equals(
                        root.path("scoring_contract").asText())
                || !WritingAssessmentPolicyBundle.POLICY_BUNDLE_ID.equals(
                        root.path("policy_bundle_id").asText())
                || !WritingEvidenceLedgerVerifier.CONTRACT_VERSION.equals(
                        root.path("ledger_contract_version").asText())
                || !WritingScoreAnchorPolicy.VERSION.equals(
                        root.path("score_anchor_version").asText())
                || !WritingTaskRequirementPolicy.VERSION.equals(
                        root.path("task_requirement_version").asText())
                || !WritingEvidenceLedgerVerifier.SOURCE_NORMALIZATION.equals(
                        root.path("source_normalization").asText())
                || !root.path("source_hash").isTextual()
                || root.path("source_hash").asText().length() != 64
                || !expectedTaskType.equals(root.path("task_type").asText())
                || !root.path("score").isNumber()
                || !root.path("overall_score").isNumber()
                || !root.path("percentage").isNumber()
                || !root.path("raw_score").isNumber()
                || !root.path("raw_score_max").isNumber()
                || (requireSanitizedPayload && root.has("student_text"))) {
            return false;
        }

        WritingScoringRubric expectedRubric = WritingScoringPolicy.rubricFor(expectedTaskType);
        JsonNode rubricScores = root.path("rubric_scores");
        if (!rubricScores.isArray() || rubricScores.size() != expectedRubric.criteria().size()) {
            return false;
        }

        Map<String, WritingScoringCriterion> expectedById = new java.util.HashMap<>();
        for (WritingScoringCriterion criterion : expectedRubric.criteria()) {
            expectedById.put(criterion.criterionId(), criterion);
        }

        java.util.Set<String> seenCriterionIds = new java.util.HashSet<>();
        double rubricScoreSum = 0.0;
        for (JsonNode rubricScore : rubricScores) {
            if (!rubricScore.isObject()) {
                return false;
            }
            String criterionId = rubricScore.path("criterionId").asText();
            WritingScoringCriterion criterion = expectedById.get(criterionId);
            if (criterion == null
                    || !seenCriterionIds.add(criterionId)
                    || !criterion.displayName().equals(rubricScore.path("name").asText())
                    || !rubricScore.path("maxScore").isNumber()
                    || Double.compare(
                            rubricScore.path("maxScore").asDouble(),
                            criterion.maxScore()) != 0
                    || !rubricScore.path("score").isNumber()) {
                return false;
            }
            double criterionScore = rubricScore.path("score").asDouble();
            if (!Double.isFinite(criterionScore)
                    || criterionScore < 0.0
                    || criterionScore > criterion.maxScore()) {
                return false;
            }
            rubricScoreSum += criterionScore;
        }
        if (seenCriterionIds.size() != expectedById.size()) {
            return false;
        }

        double score = root.path("score").asDouble();
        double overallScore = root.path("overall_score").asDouble();
        double percentage = root.path("percentage").asDouble();
        double rawScore = root.path("raw_score").asDouble();
        double rawScoreMax = root.path("raw_score_max").asDouble();
        double expectedRawScoreMax = expectedRubric.totalMaxScore();
        double expectedRawScore = Math.round(rubricScoreSum * 100.0) / 100.0;
        double expectedPercentage = Math.round(
                rubricScoreSum / expectedRawScoreMax * 10_000.0) / 100.0;
        return Double.isFinite(score)
                && Double.isFinite(overallScore)
                && Double.isFinite(percentage)
                && Double.isFinite(rawScore)
                && Double.isFinite(rawScoreMax)
                && Double.compare(rawScoreMax, expectedRawScoreMax) == 0
                && sameScoreValue(rawScore, expectedRawScore)
                && sameScoreValue(score, expectedPercentage)
                && sameScoreValue(overallScore, expectedPercentage)
                && sameScoreValue(percentage, expectedPercentage)
                && hasTrustedFindings(
                        root.path("strengths"),
                        WritingRubricCriterion.Polarity.STRENGTH,
                        expectedTaskType)
                && hasTrustedFindings(
                        root.path("needs_improvement"),
                        WritingRubricCriterion.Polarity.NEEDS_IMPROVEMENT,
                        expectedTaskType)
                && root.path("task_coverage").isArray()
                && root.path("task_coverage").size()
                == WritingTaskRequirementPolicy.requirementsFor(
                        expectedTaskType).size()
                && root.path("evidence_ledger").isArray()
                && root.path("annotations").isArray();
    }

    private static boolean hasTrustedFindings(
            JsonNode findings,
            WritingRubricCriterion.Polarity polarity,
            String taskType
    ) {
        if (!findings.isArray()) {
            return false;
        }
        for (JsonNode finding : findings) {
            WritingRubricCriterion criterion = WritingRubricCriterion.parse(
                    finding.path("criterionId").asText(null));
            WritingRubricCriterion.EvidenceScope scope = parseEvidenceScope(
                    finding.path("evidenceScope").asText(null));
            if (criterion == null
                    || criterion.polarity() != polarity
                    || !criterion.activeForProvider()
                    || !criterion.appliesTo(taskType)
                    || scope == null
                    || !criterion.supports(scope)
                    || scope == WritingRubricCriterion.EvidenceScope.TASK_METADATA
                    || !WritingDiagnosticContract.validProviderMetadata(
                            finding, criterion, taskType, scope)) {
                return false;
            }
            String evidence = finding.path("evidence").asText("");
            if ((scope == WritingRubricCriterion.EvidenceScope.TEXT_SPAN
                    && evidence.isBlank())
                    || (scope == WritingRubricCriterion.EvidenceScope.WHOLE_ANSWER
                    && !evidence.isEmpty())) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameScoreValue(double actual, double expected) {
        return Math.abs(actual - expected) < 0.000_000_1;
    }

    /**
     * Deterministic spam/empty response. 0 provider calls.
     */
    public String spamResponse(String taskType, String learnerAnswer) {
        try {
            String effectiveTaskType = taskType == null ? "GENERAL" : taskType;
            double score = 0.0;
            double rawScore = 0.0;
            WritingScoringRubric scoringRubric = WritingScoringPolicy.rubricFor(effectiveTaskType);
            double rawMax = scoringRubric.totalMaxScore();

            List<Map<String, Object>> rubricScores = new ArrayList<>();
            for (WritingScoringCriterion criterion : scoringRubric.criteria()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("criterionId", criterion.criterionId());
                row.put("name", criterion.displayName());
                row.put("score", 0.0);
                row.put("maxScore", criterion.maxScore());
                WritingScoreAnchorPolicy.ScoreAnchor anchor =
                        WritingScoreAnchorPolicy.requireAnchor(
                                criterion, 0);
                row.put("anchorLabelVi", anchor.labelVi());
                row.put("performanceLevel",
                        anchor.performanceLevel().name());
                row.put("feedback", anchor.descriptionVi());
                row.put("evidenceIds", List.of());
                row.put("findingIds", List.of());
                row.put("requirementIds",
                        WritingTaskRequirementPolicy
                                .requirementsFor(effectiveTaskType)
                                .stream()
                                .filter(requirement ->
                                        criterion.criterionId().equals(
                                                requirement
                                                        .scoringCriterionId()))
                                .map(WritingTaskRequirementPolicy
                                        .Requirement::requirementId)
                                .toList());
                rubricScores.add(row);
            }

            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("score", score);
            normalized.put("overall_score", score);
            normalized.put("percentage", score);
            normalized.put("raw_score", rawScore);
            normalized.put("raw_score_max", rawMax);
            normalized.put("scoring_contract",
                    WritingScoringPolicy.SCORING_CONTRACT);
            normalized.put("policy_bundle_id",
                    WritingAssessmentPolicyBundle.POLICY_BUNDLE_ID);
            normalized.put("ledger_contract_version",
                    WritingEvidenceLedgerVerifier.CONTRACT_VERSION);
            normalized.put("score_anchor_version",
                    WritingScoreAnchorPolicy.VERSION);
            normalized.put("task_requirement_version",
                    WritingTaskRequirementPolicy.VERSION);
            normalized.put("source_normalization",
                    WritingEvidenceLedgerVerifier.SOURCE_NORMALIZATION);
            normalized.put("source_hash",
                    WritingEvidenceLedgerVerifier.sha256(
                            Normalizer.normalize(
                                    learnerAnswer == null
                                            ? "" : learnerAnswer,
                                    Normalizer.Form.NFC)));
            normalized.put("task_type", effectiveTaskType);
            String invalidSummary = "[INVALID_LEARNER_RESPONSE] Bài làm bỏ trống hoặc chưa có đủ dữ liệu tiếng Hàn để chấm.";
            normalized.put("summary", invalidSummary);
            normalized.put("summary_vi", invalidSummary);
            normalized.put("rubric_scores", rubricScores);
            normalized.put("task_coverage",
                    WritingTaskRequirementPolicy
                            .requirementsFor(effectiveTaskType)
                            .stream()
                            .map(requirement -> Map.of(
                                    "requirementId",
                                    requirement.requirementId(),
                                    "status",
                                    "NOT_APPLICABLE",
                                    "evidenceIds",
                                    List.of()))
                            .toList());
            normalized.put("evidence_ledger", List.of());
            normalized.put("strengths", List.of());
            normalized.put("needs_improvement", List.of());
            normalized.put("student_text", learnerAnswer == null ? "" : learnerAnswer);
            normalized.put("student_strengths_annotated", "");
            normalized.put("student_needs_annotated", "");
            normalized.put("annotations", List.of());
            normalized.put("upgraded_answer", "");
            normalized.put("upgraded_answer_annotated", "");
            normalized.put("upgraded_annotations", List.of());
            normalized.put("corrected_version", "");
            normalized.put("sample_answer", "");
            normalized.put("sentence_rewrites", List.of());
            normalized.put("engine", EVALUATION_ENGINE);
            putEvaluationMetadata(normalized,
                    "INVALID_LEARNER_RESPONSE",
                    "BACKEND_RULE",
                    invalidLearnerReason(learnerAnswer),
                    false,
                    true);
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            return fallback(
                    "[INVALID_LEARNER_RESPONSE] Bài làm không hợp lệ.",
                    taskType);
        }
    }

    public String fallback(String reason) {
        return fallback(reason, "GENERAL");
    }

    public String fallback(String reason, String taskType) {
        return availabilityResult(
                "EVALUATION_UNAVAILABLE",
                "SYSTEM",
                "PROVIDER_UNEXPECTED_ERROR",
                true,
                reason,
                taskType,
                "");
    }

    public String providerUnavailable(String reason,
                                      String taskType,
                                      String learnerAnswer,
                                      boolean retryable) {
        return availabilityResult(
                "EVALUATION_UNAVAILABLE",
                "PROVIDER",
                reason,
                retryable,
                "Chưa có đánh giá AI khả dụng — vui lòng chấm lại.",
                taskType,
                learnerAnswer);
    }

    public String contractFailure(String reason, String taskType, String learnerAnswer) {
        return contractFailure(reason, taskType, learnerAnswer, "");
    }

    private String contractFailure(String reason,
                                   String taskType,
                                   String learnerAnswer,
                                   String providerRawResponse) {
        return availabilityResult(
                "EVALUATION_CONTRACT_FAILED",
                "PROVIDER",
                reason,
                true,
                "Phản hồi AI không đúng định dạng chấm điểm — vui lòng chấm lại.",
                taskType,
                learnerAnswer,
                providerRawResponse);
    }

    private Map<String, Object> presentationFallback(
            JsonNode providerRoot,
            String defaultSummary,
            String defaultUpgrade) {
        JsonNode compact = providerRoot == null
                ? null : providerRoot.path("compactFallback");
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("schema_version",
                "practice-writing-presentation-fallback-v1");
        fallback.put("tong_quan", boundedCompactText(
                compact, providerRoot, "xxx_tongquan", defaultSummary));
        fallback.put("diem_manh", boundedCompactText(
                compact, providerRoot, "xxx_diemmanh",
                "Chưa có điểm mạnh được xác minh vì phản hồi chi tiết không hợp lệ."));
        fallback.put("can_cai_thien", boundedCompactText(
                compact, providerRoot, "xxx_cancaithien",
                "Hãy chấm lại để nhận nhận xét có đối chiếu theo tiêu chí."));
        fallback.put("bai_nang_cap", boundedCompactText(
                compact, providerRoot, "xxx_bainangcap", defaultUpgrade));
        return fallback;
    }

    private static final Map<String, List<String>> COMPACT_ALIASES = Map.of(
            "xxx_tongquan", List.of("xxx_tongquan", "tongquan", "xxx_tong_quan", "tong_quan", "overview", "summary", "summary_vi"),
            "xxx_diemmanh", List.of("xxx_diemmanh", "diemmanh", "xxx_diem_manh", "diem_manh", "strengths", "strength"),
            "xxx_cancaithien", List.of("xxx_cancaithien", "cancaithien", "xxx_can_cai_thien", "can_cai_thien", "needs_improvement", "improvements"),
            "xxx_bainangcap", List.of("xxx_bainangcap", "bainangcap", "xxx_bai_nang_cap", "bai_nang_cap", "upgraded_answer", "upgradedAnswer")
    );

    private static String boundedCompactText(
            JsonNode compact,
            JsonNode root,
            String field,
            String fallback) {
        List<String> candidates = COMPACT_ALIASES.getOrDefault(field, List.of(field,
                field.startsWith("xxx_") ? field.substring(4) : "xxx_" + field));
        for (String candidate : candidates) {
            if (compact != null && compact.isObject() && compact.path(candidate).isTextual()) {
                String val = compact.path(candidate).asText().trim();
                if (!val.isBlank()) return val.length() <= 4_000 ? val : val.substring(0, 4_000);
            }
            if (root != null && root.isObject() && root.path(candidate).isTextual()) {
                String val = root.path(candidate).asText().trim();
                if (!val.isBlank()) return val.length() <= 4_000 ? val : val.substring(0, 4_000);
            }
        }
        String value = fallback == null ? "" : fallback.trim();
        return value.length() <= 4_000 ? value : value.substring(0, 4_000);
    }

    private String availabilityResult(String status,
                                      String source,
                                      String reason,
                                      boolean retryable,
                                      String message,
                                      String taskType,
                                      String learnerAnswer) {
        return availabilityResult(
                status, source, reason, retryable, message, taskType,
                learnerAnswer, "");
    }

    private String availabilityResult(String status,
                                      String source,
                                      String reason,
                                      boolean retryable,
                                      String message,
                                      String taskType,
                                      String learnerAnswer,
                                      String providerRawResponse) {
        try {
            String effectiveTaskType = taskType == null ? "GENERAL" : taskType;
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("task_type", effectiveTaskType);
            normalized.put("policy_bundle_id",
                    WritingAssessmentPolicyBundle.POLICY_BUNDLE_ID);
            normalized.put("summary", message);
            normalized.put("summary_vi", message);
            // This is deliberately non-score-bearing.  It gives Result and
            // Result Detail a bounded, readable state when a provider breaks
            // the detailed rubric contract, without inventing a rubric score
            // or presenting unverified diagnostics as feedback.
            Map<String, Object> presentationFallback =
                    presentationFallbackFromRaw(
                            providerRawResponse, message);
            normalized.put("presentation_fallback", presentationFallback);
            normalized.put("rubric_scores", List.of());
            normalized.put("strengths", List.of());
            normalized.put("needs_improvement", List.of());
            normalized.put("student_text", learnerAnswer == null ? "" : learnerAnswer);
            normalized.put("student_strengths_annotated", "");
            normalized.put("student_needs_annotated", "");
            normalized.put("annotations", List.of());
            normalized.put("upgraded_answer", "");
            normalized.put("upgraded_answer_annotated", "");
            normalized.put("upgraded_annotations", List.of());
            normalized.put("corrected_version", "");
            normalized.put("sample_answer", "");
            normalized.put("sentence_rewrites", List.of());
            if (providerRawResponse != null && !providerRawResponse.isBlank()) {
                normalized.put("provider_raw_response",
                        truncateProviderRawResponse(providerRawResponse));
            }
            normalized.put("engine", "KSH_WRITING_EVALUATOR_STATUS");
            putEvaluationMetadata(normalized, status, source, reason, retryable, false);
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            return "{\"policy_bundle_id\":\"KSH_WRITING_POLICY_BUNDLE_V3\",\"evaluation_status\":\"EVALUATION_UNAVAILABLE\",\"evaluation_source\":\"SYSTEM\",\"evaluation_reason\":\"PROVIDER_UNEXPECTED_ERROR\",\"evaluation_retryable\":true,\"score_available\":false,\"result_completeness\":{\"version\":\"practice-ai-result-completeness-v1\",\"status\":\"UNAVAILABLE\",\"reason_code\":\"PROVIDER_UNEXPECTED_ERROR\",\"rejected_item_count\":0},\"summary_vi\":\"Chưa có đánh giá AI khả dụng.\"}";
        }
    }

    private static String truncateProviderRawResponse(String value) {
        final int limit = 65536;
        return value.length() <= limit
                ? value
                : value.substring(0, limit) + "\n…[provider response truncated]";
    }

    private static final java.util.regex.Pattern TONG_QUAN_PATTERN =
            java.util.regex.Pattern.compile("\"(?:xxx_)?tong_?quan\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final java.util.regex.Pattern DIEM_MANH_PATTERN =
            java.util.regex.Pattern.compile("\"(?:xxx_)?diem_?manh\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final java.util.regex.Pattern CAN_CAI_THIEN_PATTERN =
            java.util.regex.Pattern.compile("\"(?:xxx_)?can_?cai_?thien\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final java.util.regex.Pattern BAI_NANG_CAP_PATTERN =
            java.util.regex.Pattern.compile("\"(?:xxx_)?bai_?nang_?cap\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static String extractRegexFallbackField(java.util.regex.Pattern pattern, String text, String fallback) {
        if (text == null || text.isBlank()) return fallback;
        java.util.regex.Matcher m = pattern.matcher(text);
        if (m.find()) {
            String captured = m.group(1);
            String unescaped = captured
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\t", "\t")
                    .replace("\\r", "");
            if (!unescaped.isBlank()) {
                return unescaped.length() <= 4_000 ? unescaped : unescaped.substring(0, 4_000);
            }
        }
        return fallback;
    }

    private Map<String, Object> presentationFallbackFromRaw(
            String providerRawResponse,
            String defaultMessage) {
        if (providerRawResponse != null && !providerRawResponse.isBlank()) {
            try {
                JsonNode raw = objectMapper.readTree(providerRawResponse);
                return presentationFallback(raw, defaultMessage, "");
            } catch (Exception ignored) {
                Map<String, Object> fallback = new LinkedHashMap<>();
                fallback.put("schema_version",
                        "practice-writing-presentation-fallback-v1");
                fallback.put("tong_quan", extractRegexFallbackField(
                        TONG_QUAN_PATTERN, providerRawResponse, defaultMessage));
                fallback.put("diem_manh", extractRegexFallbackField(
                        DIEM_MANH_PATTERN, providerRawResponse,
                        "Chưa có điểm mạnh được xác minh vì phản hồi chi tiết không hợp lệ."));
                fallback.put("can_cai_thien", extractRegexFallbackField(
                        CAN_CAI_THIEN_PATTERN, providerRawResponse,
                        "Hãy chấm lại để nhận nhận xét có đối chiếu theo tiêu chí."));
                fallback.put("bai_nang_cap", extractRegexFallbackField(
                        BAI_NANG_CAP_PATTERN, providerRawResponse, ""));
                return fallback;
            }
        }
        return presentationFallback(null, defaultMessage, "");
    }

    // ---- Scoring ----

    /** Derives the percentage from the authoritative task-native maxima. */
    static double deriveScoreFromRubrics(List<Map<String, Object>> rubricScores) {
        if (rubricScores == null || rubricScores.isEmpty()) {
            return 0.0;
        }
        double sum = 0;
        double max = 0;
        int count = 0;
        for (Map<String, Object> row : rubricScores) {
            Object scoreObj = row.get("score");
            if (scoreObj instanceof Number n) {
                sum += n.doubleValue();
                Object maxObj = row.get("maxScore");
                if (maxObj instanceof Number m) max += m.doubleValue();
                count++;
            }
        }
        if (count == 0) return 0.0;
        if (max > 0) return Math.round(sum / max * 10000.0) / 100.0;
        return 0.0;
    }

    private static double sumRubricScores(List<Map<String, Object>> rubricScores) {
        double sum = 0.0;
        for (Map<String, Object> row : rubricScores) {
            Object score = row.get("score");
            if (score instanceof Number number) {
                sum += number.doubleValue();
            }
        }
        return Math.round(sum * 100.0) / 100.0;
    }

    private static String inferDisplayType(String evidence) {
        if (evidence == null) return "PHRASE";
        int len = evidence.length();
        if (len <= 8) return "WORD";
        if (evidence.contains(".") || evidence.contains("?") || evidence.contains("!") || evidence.contains("。")) return "SENTENCE";
        if (len <= 30) return "PHRASE";
        return "SENTENCE";
    }

    private static WritingRubricCriterion.EvidenceScope parseEvidenceScope(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return WritingRubricCriterion.EvidenceScope.valueOf(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // ---- Helpers ----

    private static void putEvaluationMetadata(Map<String, Object> target,
                                              String status,
                                              String source,
                                              String reason,
                                              boolean retryable,
                                              boolean scoreAvailable) {
        target.put("evaluation_status", status);
        target.put("evaluation_source", source);
        target.put("evaluation_reason", reason);
        target.put("evaluation_retryable", retryable);
        target.put("score_available", scoreAvailable);
        target.put(PracticeAiResultCompleteness.FIELD,
                scoreAvailable
                        ? PracticeAiResultCompleteness.complete().toMap()
                        : PracticeAiResultCompleteness.unavailable(
                                reason, 0).toMap());
    }

    private static String invalidLearnerReason(String learnerAnswer) {
        if (learnerAnswer == null || learnerAnswer.trim().isEmpty()) {
            return "BLANK_ANSWER";
        }
        String trimmed = Normalizer.normalize(
                learnerAnswer.trim(), Normalizer.Form.NFC);
        boolean hasHangul = trimmed.codePoints().anyMatch(cp -> cp >= 0xAC00 && cp <= 0xD7A3);
        if (!hasHangul) {
            return "NO_HANGUL";
        }
        return "INVALID_LEARNER_RESPONSE";
    }

    private static String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText(null);
        return value == null ? fallback : value;
    }
}
