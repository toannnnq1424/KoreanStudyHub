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
            "sourceQuestionId", "questionType", "essayTaskType", "prompt",
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
        Map<String, Object> root = object(
                List.of("schemaVersion", "operation", "sourceDigest", "groups", "warnings"),
                props(
                        "schemaVersion", Map.of("type", "string", "const", SCHEMA_VERSION),
                        "operation", enumString("EXTRACT", "GENERATE"),
                        "sourceDigest", Map.of(
                                "type", "string",
                                "pattern", "^sha256:[0-9a-f]{64}$"),
                        "groups", array(groupSchema(), 1, 100),
                        "warnings", array(warningSchema(), 0, 200)));
        root.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        return root;
    }

    private static Map<String, Object> groupSchema() {
        return object(
                List.of("sourceGroupId", "label", "instruction", "stimulus",
                        "sourceRefs", "questions"),
                props(
                        "sourceGroupId", stableId(),
                        "label", text(1, 255),
                        "instruction", text(0, 4000),
                        "stimulus", stimulusSchema(),
                        "sourceRefs", array(sourceRefSchema(), 0, 200),
                        "questions", array(questionSchema(), 1, 200)));
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

    private static Map<String, Object> questionSchema() {
        return object(
                List.of("sourceQuestionId", "questionType", "prompt", "points",
                        "questionContent", "answerSpec", "sourceRefs", "confidence"),
                props(
                        "sourceQuestionId", stableId(),
                        "questionType", enumString(
                                "SINGLE_CHOICE", "MULTIPLE_ANSWER",
                                "TRUE_FALSE_NOT_GIVEN", "FILL_BLANK", "MATCHING",
                                "ESSAY", "SPEAKING"),
                        "essayTaskType", enumString("Q51", "Q52", "Q53", "Q54"),
                        "prompt", text(1, 100_000),
                        "points", Map.of("type", "number", "exclusiveMinimum", 0),
                        "explanationVi", text(0, 100_000),
                        "questionContent", questionContentSchema(),
                        "answerSpec", answerSpecSchema(),
                        "sourceRefs", array(sourceRefSchema(), 1, 200),
                        "confidence", Map.of(
                                "type", "number", "minimum", 0, "maximum", 1)));
    }

    private static Map<String, Object> warningSchema() {
        return object(
                List.of("code", "messageVi", "sourceRefs"),
                props(
                        "code", Map.of(
                                "type", "string",
                                "pattern", "^[A-Z][A-Z0-9_]{2,100}$"),
                        "messageVi", text(1, 2000),
                        "sourceRefs", array(sourceRefSchema(), 0, 200)));
    }

    private static Map<String, Object> sourceRefSchema() {
        return object(
                List.of("kind", "sourceId"),
                props(
                        "kind", enumString("TEXT_SPAN", "PAGE", "REGION"),
                        "sourceId", text(1, 200),
                        "pageNumber", integer(1, null),
                        "start", integer(0, null),
                        "end", integer(0, null)));
    }

    private static Map<String, Object> questionContentSchema() {
        return object(
                List.of("schemaVersion", "options", "blanks"),
                props(
                        "schemaVersion", enumString(
                                "question-content-v1", "question-content-v2",
                                "question-content-v3"),
                        "options", array(optionSchema(), 0, 8),
                        "blanks", array(contentBlankSchema(), 0, 100),
                        "imageReference", nullableString(512),
                        "audioReference", nullableString(512),
                        "speakingDelivery", speakingSchema(),
                        "writingResponse", writingResponseSchema(),
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
                        "inputType", Map.of("type", "string", "const", "manual_text"),
                        "deliveryMode", Map.of("type", "string", "const", "text_only"),
                        "promptAudioReference", Map.of("type", "null"),
                        "audioOrigin", Map.of("type", "string", "const", "none"),
                        "promptPlayLimit", Map.of("type", "null"),
                        "preparationSeconds", integer(0, 600),
                        "responseSeconds", integer(1, 1800)));
    }

    private static Map<String, Object> writingResponseSchema() {
        return object(
                List.of("responseSchemaVersion", "responseMode", "taskType", "blanks"),
                props(
                        "responseSchemaVersion", Map.of(
                                "type", "string", "const", "writing-blanks.v1"),
                        "responseMode", Map.of(
                                "type", "string", "const", "STRUCTURED_BLANKS"),
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

    private static Map<String, Object> answerSpecSchema() {
        return object(
                List.of("schemaVersion", "questionType", "correctOptionIds",
                        "correctValue", "blanks", "scoringPolicyCode"),
                props(
                        "schemaVersion", Map.of(
                                "type", "string", "const", "answer-spec-v1"),
                        "questionType", enumString(
                                "SINGLE_CHOICE", "MULTIPLE_ANSWER",
                                "TRUE_FALSE_NOT_GIVEN", "FILL_BLANK", "MATCHING",
                                "ESSAY", "SPEAKING"),
                        "correctOptionIds", array(stableId(), 0, 100),
                        "correctValue", nullableString(10_000),
                        "blanks", array(answerBlankSchema(), 0, 100),
                        "scoringPolicyCode", enumString(
                                "ALL_OR_NOTHING", "NORMALIZED_EXACT", "PROFILE_BASED"),
                        "writingBlankAuthority", writingAuthoritySchema()));
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
                        "contractVersion", Map.of(
                                "type", "string", "const", "writing-blank-authority.v1"),
                        "taskType", enumString("Q51", "Q52"),
                        "normalization", Map.of("type", "string", "const", "NFC"),
                        "whitespacePolicy", Map.of(
                                "type", "string", "const", "TRIM_COLLAPSE"),
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
                        "equivalence", Map.of("type", "string", "const", "EXACT"),
                        "reason", nullableString(1000),
                        "evidenceIds", array(stableId(), 0, 100)));
    }

    private static Map<String, Object> stableId() {
        return Map.of(
                "type", "string",
                "pattern", "^[A-Za-z0-9._-]{1,80}$");
    }

    private static Map<String, Object> enumString(String... values) {
        return Map.of("type", "string", "enum", List.of(values));
    }

    private static Map<String, Object> text(int min, int max) {
        return Map.of(
                "type", "string", "minLength", min, "maxLength", max);
    }

    private static Map<String, Object> nullableString(int max) {
        return Map.of("type", List.of("string", "null"), "maxLength", max);
    }

    private static Map<String, Object> integer(Integer min, Integer max) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", "integer");
        if (min != null) value.put("minimum", min);
        if (max != null) value.put("maximum", max);
        return value;
    }

    private static Map<String, Object> array(
            Map<String, Object> items, int min, int max) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", "array");
        value.put("items", items);
        value.put("minItems", min);
        value.put("maxItems", max);
        return value;
    }

    private static Map<String, Object> object(
            List<String> required, Map<String, Object> properties) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", "object");
        value.put("additionalProperties", false);
        value.put("required", required);
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
