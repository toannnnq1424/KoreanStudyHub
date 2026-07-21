package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SpeakingEvaluationPromptBuilder {
    private final ObjectMapper objectMapper;
    private final SpeakingRuleEngine ruleEngine;

    @Autowired
    public SpeakingEvaluationPromptBuilder(ObjectMapper objectMapper) {
        this(objectMapper, new SpeakingRuleEngine());
    }

    public SpeakingEvaluationPromptBuilder(ObjectMapper objectMapper, SpeakingRuleEngine ruleEngine) {
        this.objectMapper = objectMapper;
        this.ruleEngine = ruleEngine;
    }

    public String systemPrompt(SpeakingEvaluationRequest request) {
        return SpeakingPromptRules.buildSystemPrompt(request != null && request.textFallback());
    }

    public String userPayload(SpeakingEvaluationRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("skill_type", "SPEAKING");
        payload.put("platform", "KSH Korean Study Hub");
        payload.put("score_policy", "Transcript-grounded KSH language profile only; holistic Speaking score unavailable.");
        payload.put("task", map(
                "attempt_id", request.attemptId(),
                "question_id", request.questionId(),
                "question_text", safeText(request.questionText()),
                "target_level", safeText(request.targetLevel()),
                "expected_answer_guidance", safeText(request.expectedAnswerGuidance())));
        payload.put("question_image", request.imageEvidence() == null
                ? Map.of("available", false)
                : map(
                        "available", true,
                        "mime_type", request.imageEvidence().mimeType()));
        payload.put("evaluator_capability", map(
                "capability", request.evaluatorCapability().name(),
                "contract_version", request.evidenceContractVersion(),
                "evidence_mode", request.evidenceMode().name(),
                "transcript_source", request.textFallback() ? "TEXT_FALLBACK" : "STT",
                "learner_audio_received_by_evaluator", false,
                "direct_learner_audio_required", request.evaluatorCapability().directLearnerAudioRequired(),
                "acoustic_criteria_available", request.evaluatorCapability().acousticCriteriaSupported(),
                "holistic_score_available", false,
                "not_scorable_criteria", Arrays.stream(SpeakingRubricCriterion.values())
                        .filter(SpeakingRubricCriterion::requiresAcousticEvidence)
                        .map(SpeakingRubricCriterion::id)
                        .toList()));
        payload.put("transcription", map(
                "provider", safeText(request.transcriptionProvider()),
                "model", safeText(request.transcriptionModel()),
                "language", safeText(request.language()),
                "transcript", safeText(request.transcript()),
                "normalized_transcript", safeText(request.normalizedTranscript()),
                "actually_heard_transcript", safeText(request.actuallyHeardTranscript()),
                "transcript_confidence", request.transcriptConfidence()));
        payload.put("versions", map(
                "prompt_version", request.promptVersion(),
                "rubric_version", request.rubricVersion(),
                "schema_version", request.schemaVersion(),
                "evidence_contract_version", request.evidenceContractVersion()));
        payload.put("allowed_evidence_sources", Arrays.stream(SpeakingEvidenceSource.values())
                .filter(SpeakingEvidenceSource::transcriptLanguageGrounding)
                .map(Enum::name)
                .toList());
        payload.put("allowed_rubric", rubricRows());
        payload.put("allowed_subcriteria", Arrays.stream(subcriterionIds()).toList());
        payload.put("pre_evaluation_signals", ruleSignals(request));
        payload.put("required_output", "Return strict JSON matching response_format. Use snake_case fields.");
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not build speaking evaluator payload.", ex);
        }
    }

    public Map<String, Object> responseFormat(SpeakingEvaluationRequest request) {
        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_schema");
        Map<String, Object> jsonSchema = new LinkedHashMap<>();
        jsonSchema.put("name", "ksh_speaking_evaluation");
        jsonSchema.put("strict", Boolean.TRUE);
        jsonSchema.put("schema", schema());
        responseFormat.put("json_schema", jsonSchema);
        return responseFormat;
    }

    private Map<String, Object> schema() {
        Map<String, Object> schema = objectSchema(List.of(
                "evaluation_status", "score_available", "interpreted_intent", "intent_confidence",
                "overall_score", "level_label", "overall_summary",
                "task_achievement_summary", "major_strengths", "major_needs_improvement",
                "action_plan", "criterion_feedback", "transcript_annotations",
                "rubric_scores", "strengths", "needs_improvement", "confidence_notes",
                "findings", "evidence", "recommendations",
                "upgraded_answer", "sample_answer", "error_category", "retryable"));
        schema.put("properties", props(
                "evaluation_status", enumSchema("EVALUATED"),
                "score_available", constantBoolean(false),
                "interpreted_intent", typed("null"),
                "intent_confidence", typed("null"),
                "overall_score", typed("null"),
                "level_label", typed("null"),
                "overall_summary", typed("string"),
                "task_achievement_summary", typed("string"),
                "major_strengths", arrayOf(typed("string")),
                "major_needs_improvement", arrayOf(typed("string")),
                "action_plan", arrayOf(actionPlanSchema()),
                "criterion_feedback", arrayOf(criterionFeedbackSchema()),
                "transcript_annotations", arrayOf(transcriptAnnotationSchema()),
                "rubric_scores", fixedArrayOf(rubricScoreSchema(),
                        SpeakingRubricCriterion.transcriptGroundedCriteria().size()),
                "strengths", arrayOf(feedbackItemSchema(true)),
                "needs_improvement", arrayOf(feedbackItemSchema(false)),
                "confidence_notes", typed("string"),
                "findings", arrayOf(findingSchema()),
                "evidence", arrayOf(evidenceSchema()),
                "recommendations", arrayOf(typed("string")),
                "upgraded_answer", typed("string"),
                "sample_answer", typed("string"),
                "error_category", typed("string"),
                "retryable", typed("boolean")));
        return schema;
    }

    private Map<String, Object> criterionFeedbackSchema() {
        return objectSchema(
                List.of("criterion_id", "display_name", "score", "max_score", "level_label", "summary",
                        "strengths", "needs_improvement", "subcriteria"),
                props("criterion_id", enumSchema(rubricIds()),
                        "display_name", typed("string"),
                        "score", typed("number"),
                        "max_score", typed("number"),
                        "level_label", typed("string"),
                        "summary", typed("string"),
                        "strengths", arrayOf(typed("string")),
                        "needs_improvement", arrayOf(typed("string")),
                        "subcriteria", arrayOf(subcriterionSchema())));
    }

    private Map<String, Object> subcriterionSchema() {
        return objectSchema(
                List.of("sub_criterion_id", "display_name", "level_label", "summary", "strengths", "needs_improvement"),
                props("sub_criterion_id", enumSchema(subcriterionIds()),
                        "display_name", typed("string"),
                        "level_label", typed("string"),
                        "summary", typed("string"),
                        "strengths", arrayOf(typed("string")),
                        "needs_improvement", arrayOf(typed("string"))));
    }

    private Map<String, Object> transcriptAnnotationSchema() {
        return objectSchema(
                List.of("criterion_id", "sub_criterion_id", "evidence_scope", "evidence", "evidence_source",
                        "start_offset", "end_offset", "annotation_type", "explanation_vi", "suggestion_ko"),
                props("criterion_id", enumSchema(rubricIds()),
                        "sub_criterion_id", enumSchema(subcriterionIds()),
                        "evidence_scope", enumSchema("TEXT_SPAN", "WHOLE_ANSWER"),
                        "evidence", typed("string"),
                        "evidence_source", enumSchema(evidenceSources()),
                        "start_offset", anyOf(typed("integer"), typed("null")),
                        "end_offset", anyOf(typed("integer"), typed("null")),
                        "annotation_type", enumSchema("strength", "needs_improvement", "advisory"),
                        "explanation_vi", typed("string"),
                        "suggestion_ko", typed("string")));
    }

    private Map<String, Object> feedbackItemSchema(boolean strength) {
        return objectSchema(
                List.of("criterion_id", "sub_criterion_id", "evidence_scope", "evidence",
                        "evidence_source", "explanation_vi", "correction"),
                props("criterion_id", enumSchema(rubricIds()),
                        "sub_criterion_id", enumSchema(subcriterionIds()),
                        "evidence_scope", enumSchema("TEXT_SPAN", "WHOLE_ANSWER"),
                        "evidence", typed("string"),
                        "evidence_source", enumSchema(evidenceSources()),
                        "explanation_vi", typed("string"),
                        "correction", strength ? constantString("") : typed("string")));
    }

    private Map<String, Object> actionPlanSchema() {
        return objectSchema(
                List.of("criterion_id", "sub_criterion_id", "title", "instruction", "reason", "priority"),
                props("criterion_id", enumSchema(rubricIds()),
                        "sub_criterion_id", enumSchema(subcriterionIds()),
                        "title", typed("string"),
                        "instruction", typed("string"),
                        "reason", typed("string"),
                        "priority", typed("string")));
    }

    private Map<String, Object> rubricScoreSchema() {
        return objectSchema(
                List.of("criterion", "score", "max_score", "feedback"),
                props("criterion", enumSchema(rubricIds()),
                        "score", typed("number"),
                        "max_score", typed("number"),
                        "feedback", typed("string")));
    }

    private Map<String, Object> findingSchema() {
        return objectSchema(
                List.of("category", "message", "recommendation"),
                props("category", typed("string"), "message", typed("string"), "recommendation", typed("string")));
    }

    private Map<String, Object> evidenceSchema() {
        return objectSchema(
                List.of("source", "criterion", "excerpt", "confidence"),
                props("source", enumSchema(evidenceSources()),
                        "criterion", enumSchema(rubricIds()),
                        "excerpt", typed("string"),
                        "confidence", typed("number")));
    }

    private List<Map<String, Object>> rubricRows() {
        return SpeakingRubricCriterion.transcriptGroundedCriteria().stream()
                .map(row -> Map.<String, Object>of(
                        "criterion_id", row.id(),
                        "label", row.label(),
                        "max_score", row.maxScore()))
                .toList();
    }

    private List<Map<String, Object>> ruleSignals(SpeakingEvaluationRequest request) {
        String transcript = request.normalizedTranscript() == null || request.normalizedTranscript().isBlank()
                ? request.transcript()
                : request.normalizedTranscript();
        return ruleEngine.analyze(transcript, request.transcriptConfidence(), request.textFallback())
                .signals()
                .stream()
                .map(signal -> Map.<String, Object>of(
                        "severity", signal.severity().name(),
                        "action", signal.action().name(),
                        "category", signal.category().name(),
                        "code", signal.code(),
                        "message", signal.message()))
                .toList();
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static String[] rubricIds() {
        return SpeakingRubricCriterion.transcriptGroundedCriteria().stream()
                .map(SpeakingRubricCriterion::id)
                .toArray(String[]::new);
    }

    private static String[] evidenceSources() {
        return new String[] {SpeakingEvidenceSource.TRANSCRIPT.name()};
    }

    private static String[] subcriterionIds() {
        return new String[] {
                "S_CONTENT_RELEVANCE",
                "S_CONTENT_PROMPT_COVERAGE",
                "S_CONTENT_SPECIFICITY_EXAMPLES",
                "S_VOCAB_TOPIC_WORDS",
                "S_VOCAB_NATURAL_EXPRESSIONS",
                "S_VOCAB_REPETITION_CONTROL",
                "S_VOCAB_WORD_CHOICE",
                "S_GRAMMAR_PARTICLES",
                "S_GRAMMAR_TENSE_ASPECT",
                "S_GRAMMAR_ENDINGS",
                "S_GRAMMAR_SENTENCE_STRUCTURE",
                "S_GRAMMAR_HONORIFIC_REGISTER",
                "S_GRAMMAR_CONNECTORS",
                "S_COHERENCE_ORGANIZATION",
                "S_COHERENCE_LOGICAL_FLOW",
                "S_COHERENCE_DISCOURSE_MARKERS"
        };
    }

    private static Map<String, Object> objectSchema(List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", required);
        schema.put("additionalProperties", Boolean.FALSE);
        return schema;
    }

    private static Map<String, Object> objectSchema(List<String> required, Map<String, Object> properties) {
        Map<String, Object> schema = objectSchema(required);
        schema.put("properties", properties);
        return schema;
    }

    private static Map<String, Object> typed(String type) {
        return Map.of("type", type);
    }

    @SafeVarargs
    private static Map<String, Object> anyOf(Map<String, Object>... values) {
        return Map.of("anyOf", List.of(values));
    }

    private static Map<String, Object> arrayOf(Map<String, Object> items) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", items);
        return schema;
    }

    private static Map<String, Object> fixedArrayOf(Map<String, Object> items, int size) {
        Map<String, Object> schema = new LinkedHashMap<>(arrayOf(items));
        schema.put("minItems", size);
        schema.put("maxItems", size);
        return schema;
    }

    private static Map<String, Object> constantBoolean(boolean value) {
        return Map.of("type", "boolean", "const", value);
    }

    private static Map<String, Object> constantString(String value) {
        return Map.of("type", "string", "const", value);
    }

    private static Map<String, Object> enumSchema(String... values) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("enum", List.of(values));
        return schema;
    }

    private static Map<String, Object> props(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put((String) values[i], values[i + 1]);
        }
        return map;
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put((String) values[i], values[i + 1]);
        }
        return map;
    }
}
