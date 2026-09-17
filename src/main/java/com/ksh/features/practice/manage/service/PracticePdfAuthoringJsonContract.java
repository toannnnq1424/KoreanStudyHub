package com.ksh.features.practice.manage.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Code-owned strict provider-output vocabulary and JSON Schema. */
public final class PracticePdfAuthoringJsonContract {

    public static final String SCHEMA_VERSION =
            "practice-pdf-authoring-output-v1";
    public static final String PROMPT_VERSION =
            "practice-pdf-authoring-prompt-v1";
    public static final String RESPONSE_SCHEMA_NAME =
            "practice_pdf_authoring_output_v1";

    static final Set<String> ROOT_FIELDS = Set.of(
            "schemaVersion", "operation", "sourceDigest", "groups", "warnings");
    static final Set<String> GROUP_FIELDS = Set.of(
            "sourceGroupId", "label", "instruction", "stimulus",
            "sourceRefs", "questions");
    static final Set<String> STIMULUS_FIELDS = Set.of(
            "type", "passageText", "transcriptText", "sourceRefs");
    static final Set<String> QUESTION_FIELDS = Set.of(
            "sourceQuestionId", "sourceQuestionNumber", "questionType", "essayTaskType", "prompt",
            "points", "explanationVi", "questionContent", "answerSpec",
            "sourceRefs", "confidence");
    static final Set<String> WARNING_FIELDS = Set.of(
            "code", "messageVi", "sourceRefs");
    static final Set<String> SOURCE_REF_FIELDS = Set.of(
            "kind", "sourceId", "pageNumber", "start", "end");
    static final Set<String> CONTENT_FIELDS = Set.of(
            "schemaVersion", "options", "blanks", "imageReference",
            "audioReference", "speakingDelivery", "writingResponse", "languageTag");
    static final Set<String> OPTION_FIELDS = Set.of("id", "text", "imageReference");
    static final Set<String> CONTENT_BLANK_FIELDS = Set.of("id", "prompt");
    static final Set<String> SPEAKING_FIELDS = Set.of(
            "inputType", "deliveryMode", "promptAudioReference", "audioOrigin",
            "promptPlayLimit", "preparationSeconds", "responseSeconds");
    static final Set<String> WRITING_RESPONSE_FIELDS = Set.of(
            "responseSchemaVersion", "responseMode", "taskType", "blanks");
    static final Set<String> WRITING_RESPONSE_BLANK_FIELDS = Set.of(
            "blankId", "ordinal", "context");
    static final Set<String> ANSWER_FIELDS = Set.of(
            "schemaVersion", "questionType", "correctOptionIds", "correctValue",
            "blanks", "scoringPolicyCode", "writingBlankAuthority");
    static final Set<String> ANSWER_BLANK_FIELDS = Set.of("blankId", "acceptedValues");
    static final Set<String> WRITING_AUTHORITY_FIELDS = Set.of(
            "contractVersion", "taskType", "normalization", "whitespacePolicy",
            "blanks");
    static final Set<String> WRITING_AUTHORITY_BLANK_FIELDS = Set.of(
            "blankId", "ordinal", "acceptedAnswers");
    static final Set<String> WRITING_ACCEPTED_FIELDS = Set.of(
            "text", "equivalence", "reason", "evidenceIds");

    static final Set<String> FORBIDDEN_NORMALIZED_KEYS = Set.of(
            "evaluationstatus", "evaluationsource", "evaluationreason",
            "score", "scoresummary", "overallscore", "rubric", "rubricscores",
            "criteria", "taskcoverage", "diagnosticstates", "evidenceledger",
            "findings", "feedback", "upgradedanswer", "transcriptalignment",
            "audioalignment", "phonemes", "stress", "pronunciation",
            "fluencyscore", "publish", "draftid", "targetdraftid",
            "submission", "result");

    private PracticePdfAuthoringJsonContract() {
    }

    public static Map<String, Object> schema() {
        return schema(null);
    }

    /** The provider schema is narrowed to the immutable target skill before
     * dispatch, so an objective Reading request cannot emit ESSAY/SPEAKING. */
    public static Map<String, Object> schema(String targetSkill) {
        List<String> allowedQuestionTypes = allowedQuestionTypes(targetSkill);
        Map<String, Object> root = object(
                List.of("schemaVersion", "operation", "sourceDigest", "groups", "warnings"),
                props(
                        "schemaVersion", enumString(SCHEMA_VERSION),
                        "operation", enumString("EXTRACT", "GENERATE"),
                        "sourceDigest", Map.of("type", "string"),
                        "groups", array(groupSchema(allowedQuestionTypes), 1, 100),
                        "warnings", array(warningSchema(), 0, 200)));
        return root;
    }

    private static Map<String, Object> groupSchema(List<String> allowedQuestionTypes) {
        return object(
                List.of("sourceGroupId", "label", "instruction", "stimulus",
                        "sourceRefs", "questions"),
                props(
                        "sourceGroupId", stableId(),
                        "label", text(1, 255),
                        "instruction", text(0, 4000),
                        "stimulus", stimulusSchema(),
                        "sourceRefs", array(sourceRefSchema(), 0, 200),
                        "questions", array(questionSchema(allowedQuestionTypes), 1, 200)));
    }

    private static Map<String, Object> stimulusSchema() {
        return object(
                List.of("type", "passageText", "transcriptText", "sourceRefs"),
                props(
                        "type", enumString("NONE", "READING_PASSAGE", "LISTENING_AUDIO"),
                        "passageText", text(0, 1_000_000),
                        "transcriptText", text(0, 1_000_000),
                        "sourceRefs", array(sourceRefSchema(), 0, 200)));
    }

    private static Map<String, Object> questionSchema(List<String> allowedQuestionTypes) {
        // `questionType` is a discriminator in the persisted assessment
        // contract.  A broad enum here used to let a provider return, for
        // example, a SINGLE_CHOICE question containing a FILL_BLANK answer
        // spec.  That JSON is syntactically valid but cannot be published.
        // Keep the discriminator correlated at generation time instead of
        // asking lecturers to repair an otherwise usable candidate.
        if (allowedQuestionTypes.size() > 1) {
            return Map.of("anyOf", allowedQuestionTypes.stream()
                    .map(PracticePdfAuthoringJsonContract::questionSchemaForType)
                    .toList());
        }
        return questionSchemaForType(allowedQuestionTypes.get(0));
    }

    private static Map<String, Object> questionSchemaForType(String questionType) {
        return object(
                List.of("sourceQuestionId", "questionType", "prompt", "points",
                        "questionContent", "answerSpec", "sourceRefs", "confidence"),
                props(
                        "sourceQuestionId", stableId(),
                        "sourceQuestionNumber", nullableInteger(1, 200),
                        "questionType", enumString(questionType),
                        "essayTaskType", nullableEnumString("Q51", "Q52", "Q53", "Q54"),
                        "prompt", text(1, 100_000),
                        "points", nullableNumber(),
                        "explanationVi", nullableString(100_000),
                        "questionContent", questionContentSchemaForType(questionType),
                        "answerSpec", answerSpecSchemaForType(questionType),
                        "sourceRefs", array(sourceRefSchema(), 1, 200),
                        "confidence", nullableNumber()));
    }

    private static Map<String, Object> warningSchema() {
        return object(
                List.of("code", "messageVi", "sourceRefs"),
                props(
                        "code", Map.of("type", "string"),
                        "messageVi", text(1, 2000),
                        "sourceRefs", array(sourceRefSchema(), 0, 200)));
    }

    private static Map<String, Object> sourceRefSchema() {
        return object(
                List.of("kind", "sourceId"),
                props(
                        "kind", enumString("TEXT_SPAN", "PAGE"),
                        "sourceId", text(1, 200),
                        "pageNumber", nullableInteger(1, null),
                        "start", nullableInteger(0, null),
                        "end", nullableInteger(0, null)));
    }

    private static Map<String, Object> questionContentSchemaForType(
            String questionType) {
        // question-content-v2 is an audio/delivery contract reserved for
        // SPEAKING by AssessmentContractCodec.  Offering it to a Reading or
        // Listening provider produced JSON that passed provider schema mode
        // and then failed the canonical domain boundary.
        return "SPEAKING".equals(questionType)
                ? Map.of("anyOf", List.of(
                        questionContentV2Schema(), questionContentV3Schema()))
                : Map.of("anyOf", List.of(
                        questionContentV1Schema(), questionContentV3Schema()));
    }

    private static Map<String, Object> questionContentV1Schema() {
        return object(
                List.of("schemaVersion", "options", "blanks"),
                props(
                        "schemaVersion", enumString("question-content-v1"),
                        "options", array(optionSchema(), 0, 8),
                        "blanks", array(contentBlankSchema(), 0, 100),
                        "imageReference", nullableString(512),
                        "audioReference", nullableString(512),
                        "speakingDelivery", nullableObject(speakingSchema()),
                        "writingResponse", nullableObject(writingResponseSchema())));
    }

    private static Map<String, Object> questionContentV2Schema() {
        return object(
                List.of("schemaVersion", "options", "blanks"),
                props(
                        "schemaVersion", enumString("question-content-v2"),
                        "options", array(optionSchema(), 0, 8),
                        "blanks", array(contentBlankSchema(), 0, 100),
                        "imageReference", nullableString(512),
                        "audioReference", nullableString(512),
                        "speakingDelivery", nullableObject(speakingSchema()),
                        "writingResponse", nullableObject(writingResponseSchema())));
    }

    private static Map<String, Object> questionContentV3Schema() {
        return object(
                List.of("schemaVersion", "options", "blanks", "languageTag"),
                props(
                        "schemaVersion", enumString("question-content-v3"),
                        "options", array(optionSchema(), 0, 8),
                        "blanks", array(contentBlankSchema(), 0, 100),
                        "imageReference", nullableString(512),
                        "audioReference", nullableString(512),
                        "speakingDelivery", nullableObject(speakingSchema()),
                        "writingResponse", nullableObject(writingResponseSchema()),
                        "languageTag", enumString("ko", "vi")));
    }

    private static Map<String, Object> optionSchema() {
        return object(
                List.of("id", "text"),
                props(
                        "id", stableId(),
                        "text", text(1, 10_000),
                        "imageReference", nullableString(512)));
    }

    private static Map<String, Object> contentBlankSchema() {
        return object(
                List.of("id", "prompt"),
                props("id", stableId(), "prompt", text(0, 10_000)));
    }

    private static Map<String, Object> speakingSchema() {
        return object(
                List.of("inputType", "deliveryMode", "promptAudioReference",
                        "audioOrigin", "promptPlayLimit", "preparationSeconds",
                        "responseSeconds"),
                props(
                        "inputType", enumString("manual_text"),
                        "deliveryMode", enumString("text_only"),
                        "promptAudioReference", Map.of("type", "null"),
                        "audioOrigin", enumString("none"),
                        "promptPlayLimit", Map.of("type", "null"),
                        "preparationSeconds", integer(0, 600),
                        "responseSeconds", integer(1, 1800)));
    }

    private static Map<String, Object> writingResponseSchema() {
        return object(
                List.of("responseSchemaVersion", "responseMode", "taskType", "blanks"),
                props(
                        "responseSchemaVersion", enumString("writing-blanks.v1"),
                        "responseMode", enumString("STRUCTURED_BLANKS"),
                        "taskType", enumString("Q51", "Q52"),
                        "blanks", array(writingResponseBlankSchema(), 2, 2)));
    }

    private static Map<String, Object> writingResponseBlankSchema() {
        return object(
                List.of("blankId", "ordinal", "context"),
                props(
                        "blankId", stableId(),
                        "ordinal", integer(1, 2),
                        "context", text(1, 1000)));
    }

    private static Map<String, Object> answerSpecSchema(List<String> allowedQuestionTypes) {
        if (allowedQuestionTypes.size() > 1) {
            return Map.of("anyOf", allowedQuestionTypes.stream()
                    .map(PracticePdfAuthoringJsonContract::answerSpecSchemaForType)
                    .toList());
        }
        return answerSpecSchemaForType(allowedQuestionTypes.get(0));
    }

    private static Map<String, Object> answerSpecSchemaForType(String questionType) {
        return object(
                List.of("schemaVersion", "questionType", "correctOptionIds",
                        "correctValue", "blanks", "scoringPolicyCode"),
                props(
                        "schemaVersion", enumString("answer-spec-v1"),
                        "questionType", enumString(questionType),
                        "correctOptionIds", array(stableId(), 0, 100),
                        "correctValue", nullableString(10_000),
                        "blanks", array(answerBlankSchema(), 0, 100),
                        "scoringPolicyCode", scoringPolicyFor(questionType),
                        "writingBlankAuthority", nullableObject(writingAuthoritySchema())));
    }

    private static Map<String, Object> scoringPolicyFor(String questionType) {
        return switch (questionType) {
            case "SINGLE_CHOICE", "MULTIPLE_ANSWER", "TRUE_FALSE_NOT_GIVEN" ->
                    enumString("ALL_OR_NOTHING");
            case "FILL_BLANK", "MATCHING" -> enumString("NORMALIZED_EXACT");
            case "ESSAY", "SPEAKING" -> enumString("PROFILE_BASED");
            default -> throw new IllegalArgumentException("Unsupported question type: " + questionType);
        };
    }

    private static Map<String, Object> answerBlankSchema() {
        return object(
                List.of("blankId", "acceptedValues"),
                props(
                        "blankId", stableId(),
                        "acceptedValues", array(text(1, 10_000), 1, 100)));
    }

    private static Map<String, Object> writingAuthoritySchema() {
        return object(
                List.of("contractVersion", "taskType", "normalization",
                        "whitespacePolicy", "blanks"),
                props(
                        "contractVersion", enumString("writing-blank-authority.v1"),
                        "taskType", enumString("Q51", "Q52"),
                        "normalization", enumString("NFC"),
                        "whitespacePolicy", enumString("TRIM_COLLAPSE"),
                        "blanks", array(writingAuthorityBlankSchema(), 2, 2)));
    }

    private static Map<String, Object> writingAuthorityBlankSchema() {
        return object(
                List.of("blankId", "ordinal", "acceptedAnswers"),
                props(
                        "blankId", stableId(),
                        "ordinal", integer(1, 2),
                        "acceptedAnswers", array(writingAcceptedSchema(), 1, 100)));
    }

    private static Map<String, Object> writingAcceptedSchema() {
        return object(
                List.of("text", "equivalence", "evidenceIds"),
                props(
                        "text", text(1, 10_000),
                        "equivalence", enumString("EXACT"),
                        "reason", nullableString(1000),
                        "evidenceIds", array(stableId(), 0, 100)));
    }

    private static List<String> allowedQuestionTypes(String targetSkill) {
        return switch (targetSkill == null ? "" : targetSkill) {
            case "READING", "LISTENING" -> List.of(
                    "SINGLE_CHOICE", "MULTIPLE_ANSWER", "TRUE_FALSE_NOT_GIVEN",
                    "FILL_BLANK", "MATCHING");
            case "WRITING" -> List.of("ESSAY");
            case "SPEAKING" -> List.of("SPEAKING");
            default -> List.of(
                    "SINGLE_CHOICE", "MULTIPLE_ANSWER", "TRUE_FALSE_NOT_GIVEN",
                    "FILL_BLANK", "MATCHING", "ESSAY", "SPEAKING");
        };
    }

    private static Map<String, Object> stableId() {
        return Map.of("type", "string");
    }

    private static Map<String, Object> enumString(String... values) {
        return Map.of("type", "string", "enum", List.of(values));
    }

    private static Map<String, Object> enumString(List<String> values) {
        return Map.of("type", "string", "enum", List.copyOf(values));
    }

    private static Map<String, Object> nullableEnumString(String... values) {
        List<Object> nullableValues = new java.util.ArrayList<>(List.of(values));
        nullableValues.add(null);
        return Map.of("type", List.of("string", "null"), "enum", nullableValues);
    }

    private static Map<String, Object> text(int min, int max) {
        return Map.of("type", "string");
    }

    private static Map<String, Object> nullableString(int max) {
        return Map.of("type", List.of("string", "null"));
    }

    private static Map<String, Object> integer(Integer min, Integer max) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", "integer");
        return value;
    }

    private static Map<String, Object> nullableInteger(Integer min, Integer max) {
        Map<String, Object> value = integer(min, max);
        value.put("type", List.of("integer", "null"));
        return value;
    }

    private static Map<String, Object> nullableNumber() {
        return Map.of("type", List.of("number", "null"));
    }

    private static Map<String, Object> nullableObject(Map<String, Object> objectSchema) {
        Map<String, Object> value = new LinkedHashMap<>(objectSchema);
        value.put("type", List.of("object", "null"));
        return value;
    }

    private static Map<String, Object> array(
            Map<String, Object> items, int min, int max) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", "array");
        value.put("items", items);
        return value;
    }

    private static Map<String, Object> object(
            List<String> required, Map<String, Object> properties) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", "object");
        value.put("additionalProperties", false);
        // OpenAI-compatible strict JSON Schema requires every declared
        // property to be required. Optional domain fields are represented as
        // nullable and are normalized away by the server before validation.
        value.put("required", List.copyOf(properties.keySet()));
        value.put("properties", properties);
        return value;
    }

    private static Map<String, Object> props(Object... pairs) {
        Map<String, Object> value = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            value.put((String) pairs[index], pairs[index + 1]);
        }
        return value;
    }
}
