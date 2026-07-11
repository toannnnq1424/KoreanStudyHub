package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PracticeDraftContractService {

    public static final String SCHEMA_VERSION = "practice-draft-v3";
    public static final String STIMULUS_SCHEMA_VERSION = "practice-stimulus-v1";

    private final ObjectMapper objectMapper;
    private final AssessmentAuthoringCatalogService catalogService;
    private final QuestionTypeResolver questionTypeResolver;
    private final AssessmentContractCodec contractCodec;

    public PracticeDraftContractService(ObjectMapper objectMapper,
                                        AssessmentAuthoringCatalogService catalogService,
                                        QuestionTypeResolver questionTypeResolver,
                                        AssessmentContractCodec contractCodec) {
        this.objectMapper = objectMapper;
        this.catalogService = catalogService;
        this.questionTypeResolver = questionTypeResolver;
        this.contractCodec = contractCodec;
    }

    public NormalizedDraft normalize(String draftJson, String source) {
        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(draftJson);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Định dạng dữ liệu JSON không hợp lệ.", exception);
        }
        if (!(parsed instanceof ObjectNode root)) {
            throw new IllegalArgumentException("Dữ liệu bản nháp phải là một JSON object.");
        }
        return normalize(root, source);
    }

    public NormalizedDraft normalize(ObjectNode root, String source) {
        root.put("schemaVersion", SCHEMA_VERSION);
        ObjectNode document = object(root, "document");
        String category = text(document, "detectedCategory", "CUSTOM").toUpperCase(Locale.ROOT);
        String templateCode = text(document, "examTemplateCode",
                AssessmentAuthoringCatalogService.defaultTemplateForCategory(category));
        AssessmentAuthoringCatalogService.ExamTemplatePolicy template = catalogService.requireTemplate(templateCode);

        document.put("detectedCategory", template.categoryCode());
        document.put("assessmentProgramCode", template.programCode());
        document.put("assessmentProgramVersionId", template.programVersionId());
        document.put("assessmentProgramVersion", template.programVersion());
        document.put("examTemplateCode", template.code());

        ArrayNode sections = array(root, "sections");
        ArrayNode tests = normalizeTests(root, sections);
        Map<Integer, ObjectNode> testsByNumber = indexTests(tests);
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            ObjectNode section = requireObject(sections.get(sectionIndex), "section");
            ensureId(section, "sec");
            if (!section.hasNonNull("title") && section.hasNonNull("label")) {
                section.set("title", section.get("label"));
            }
            String skill = text(section, "skill", "READING").toUpperCase(Locale.ROOT);
            section.put("skill", skill);
            AssessmentAuthoringCatalogService.SkillAuthoringPolicy skillPolicy = template.requireSkill(skill);
            int testNo = resolveTestNo(section, testsByNumber);
            ObjectNode test = testsByNumber.computeIfAbsent(testNo, number -> addTest(tests, number));
            section.put("testNo", testNo);
            section.put("testClientId", test.path("clientId").asText());
            String lessonCode = lessonCode(skill, testNo);
            section.put("lessonCode", lessonCode);
            if (!section.hasNonNull("durationMinutes") || section.path("durationMinutes").asInt() <= 0) {
                section.put("durationMinutes", skillPolicy.durationMinutes());
            }
            ArrayNode groups = array(section, "groups");
            int questionNo = 1;
            for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
                ObjectNode group = requireObject(groups.get(groupIndex), "group");
                ensureId(group, "grp");
                String groupCode = text(group, "groupCode", "").toUpperCase(Locale.ROOT);
                if (!groupCode.matches(java.util.regex.Pattern.quote(lessonCode) + "\\.\\d+")) {
                    groupCode = lessonCode + "." + (groupIndex + 1);
                }
                group.put("groupCode", groupCode);
                if (!group.hasNonNull("label")) {
                    group.put("label", groupCode);
                }
                normalizeStimulus(group, skill, source);
                ArrayNode questions = array(group, "questions");
                for (int questionIndex = 0; questionIndex < questions.size(); questionIndex++) {
                    ObjectNode question = requireObject(questions.get(questionIndex), "question");
                    ensureId(question, "q");
                    String rawType = text(question, "questionType", skillPolicy.questionTypes().get(0));
                    CanonicalQuestionType canonicalType = questionTypeResolver.resolve(rawType);
                    question.put("questionType", canonicalType.name());
                    question.put("canonicalQuestionType", canonicalType.name());
                    question.put("questionNo", questionNo++);
                    if (!question.hasNonNull("points") || question.path("points").decimalValue().signum() <= 0) {
                        question.put("points", skillPolicy.defaultPoints());
                    }
                    normalizeQuestionContract(question, canonicalType);
                    normalizeSourceRegionIds(question);
                }
                normalizeSourceRegionIds(group);
                if (!questions.isEmpty()) {
                    group.put("questionFrom", questions.get(0).path("questionNo").asInt());
                    group.put("questionTo", questions.get(questions.size() - 1).path("questionNo").asInt());
                }
            }
            normalizeSourceRegionIds(section);
        }
        if (!root.has("warnings") || !root.path("warnings").isArray()) {
            root.putArray("warnings");
        }
        if (!root.has("materials") || !root.path("materials").isArray()) {
            root.putArray("materials");
        }
        return new NormalizedDraft(
                root.toString(),
                template.categoryCode(),
                template.programCode(),
                template.programVersionId(),
                template.code()
        );
    }

    private ArrayNode normalizeTests(ObjectNode root, ArrayNode sections) {
        ArrayNode tests = array(root, "tests");
        if (tests.isEmpty()) {
            java.util.Set<Integer> discovered = new java.util.TreeSet<>();
            for (JsonNode section : sections) {
                int testNo = positive(section.path("testNo").asInt(0));
                if (testNo == 0) testNo = testNoFromLesson(section.path("lessonCode").asText(""));
                discovered.add(testNo == 0 ? 1 : testNo);
            }
            if (discovered.isEmpty()) discovered.add(1);
            discovered.forEach(number -> addTest(tests, number));
        }

        java.util.Set<Integer> used = new java.util.LinkedHashSet<>();
        for (int index = 0; index < tests.size(); index++) {
            ObjectNode test = requireObject(tests.get(index), "test");
            ensureId(test, "test");
            int testNo = positive(test.path("testNo").asInt(0));
            if (testNo == 0 || used.contains(testNo)) {
                testNo = nextTestNo(used);
            }
            used.add(testNo);
            test.put("testNo", testNo);
            if (!test.hasNonNull("title") || test.path("title").asText().isBlank()) {
                test.put("title", "Test " + testNo);
            }
            if (!test.hasNonNull("description")) test.put("description", "");
            if (!test.hasNonNull("estimatedMinutes")) test.putNull("estimatedMinutes");
        }
        return tests;
    }

    private Map<Integer, ObjectNode> indexTests(ArrayNode tests) {
        Map<Integer, ObjectNode> result = new LinkedHashMap<>();
        for (JsonNode value : tests) {
            ObjectNode test = requireObject(value, "test");
            result.put(test.path("testNo").asInt(), test);
        }
        return result;
    }

    private ObjectNode addTest(ArrayNode tests, int testNo) {
        ObjectNode test = tests.addObject();
        test.put("clientId", "test-" + UUID.randomUUID());
        test.put("testNo", testNo);
        test.put("title", "Test " + testNo);
        test.put("description", "");
        test.putNull("estimatedMinutes");
        return test;
    }

    private static int resolveTestNo(ObjectNode section, Map<Integer, ObjectNode> testsByNumber) {
        int testNo = positive(section.path("testNo").asInt(0));
        if (testNo == 0) testNo = testNoFromLesson(section.path("lessonCode").asText(""));
        if (testNo == 0 && section.hasNonNull("testClientId")) {
            String clientId = section.path("testClientId").asText();
            testNo = testsByNumber.entrySet().stream()
                    .filter(entry -> clientId.equals(entry.getValue().path("clientId").asText()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(0);
        }
        return testNo == 0 ? 1 : testNo;
    }

    private static int testNoFromLesson(String lessonCode) {
        if (lessonCode == null) return 0;
        String normalized = lessonCode.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[LRWS]\\d+")) return 0;
        try {
            return Integer.parseInt(normalized.substring(1));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static int positive(int value) {
        return value > 0 ? value : 0;
    }

    private static int nextTestNo(java.util.Set<Integer> used) {
        int number = 1;
        while (used.contains(number)) number++;
        return number;
    }

    private static String lessonCode(String skill, int testNo) {
        String prefix = switch (skill) {
            case "LISTENING" -> "L";
            case "WRITING" -> "W";
            case "SPEAKING" -> "S";
            default -> "R";
        };
        return prefix + testNo;
    }

    private void normalizeQuestionContract(ObjectNode question, CanonicalQuestionType type) {
        ArrayNode options = normalizeLegacyOptions(question);
        QuestionContent content = null;
        JsonNode existingContent = question.get("questionContent");
        if (existingContent != null && existingContent.isObject()) {
            try {
                content = contractCodec.readQuestionContent(existingContent.toString(), type);
            } catch (IllegalArgumentException ignored) {
                return;
            }
        } else {
            try {
                content = contractCodec.adaptLegacyContent(options.toString(), type.name());
                question.set("questionContent", objectMapper.readTree(
                        contractCodec.writeQuestionContent(content, type)));
            } catch (Exception ignored) {
                return;
            }
        }

        if (question.path("answerSpec").isObject()) {
            return;
        }
        String answerKey = legacyAnswer(question);
        boolean canCreateWithoutKey = type == CanonicalQuestionType.ESSAY
                || type == CanonicalQuestionType.SPEAKING;
        if (answerKey.isBlank() && !canCreateWithoutKey) {
            return;
        }
        try {
            AnswerSpec spec = contractCodec.adaptLegacyAnswerSpec(type.name(), answerKey, content);
            question.set("answerSpec", objectMapper.readTree(contractCodec.writeAnswerSpec(spec, content)));
        } catch (Exception ignored) {
            // Incomplete AI/Excel data remains editable and is blocked by the publisher validator.
        }
    }

    private ArrayNode normalizeLegacyOptions(ObjectNode question) {
        ArrayNode normalized = objectMapper.createArrayNode();
        JsonNode raw = question.path("options");
        if (raw.isArray()) {
            for (int index = 0; index < raw.size(); index++) {
                JsonNode option = raw.get(index);
                ObjectNode normalizedOption = normalized.addObject();
                String id = option.isObject() ? option.path("id").asText("").trim() : "";
                normalizedOption.put("id", id.isBlank() ? "opt_" + (index + 1) : id);
                normalizedOption.put("text", option.isObject()
                        ? option.path("text").asText("")
                        : option.asText(""));
                if (option.isObject()) {
                    String imageReference = option.path("imageReference").asText(
                            option.path("imageUrl").asText(""));
                    putOrNull(normalizedOption, "imageReference", imageReference);
                }
            }
        }
        question.set("options", normalized);
        return normalized;
    }

    private static String legacyAnswer(ObjectNode question) {
        JsonNode answer = question.path("answer");
        JsonNode value = answer.isObject() ? answer.path("value") : answer;
        if (value.isArray()) {
            java.util.List<String> values = new java.util.ArrayList<>();
            value.forEach(item -> values.add(item.asText("")));
            return String.join(",", values);
        }
        String direct = value.asText("").trim();
        return direct.isBlank() ? question.path("answerKey").asText("").trim() : direct;
    }

    private void normalizeStimulus(ObjectNode group, String skill, String source) {
        ObjectNode stimulus = group.path("stimulus") instanceof ObjectNode object
                ? object
                : objectMapper.createObjectNode();
        String passage = firstText(stimulus, "passageText", group, "passageText", group, "passage");
        String transcript = firstText(stimulus, "transcriptText", group, "transcriptText", group, "transcript");
        String audio = firstText(stimulus, "mediaReference", group, "audioUrl", group, "audioRef");
        String image = firstText(stimulus, "imageReference", group, "imageUrl", group, "imageRef");
        String instruction = text(group, "instruction", "");

        String type = text(stimulus, "type", "");
        if (type.isBlank() || "NONE".equals(type)) {
            if ("READING".equals(skill) && !passage.isBlank()) {
                type = "READING_PASSAGE";
            } else if ("LISTENING".equals(skill) && (!transcript.isBlank() || !audio.isBlank())) {
                type = "LISTENING_AUDIO";
            } else {
                type = "NONE";
            }
        }

        stimulus.put("schemaVersion", STIMULUS_SCHEMA_VERSION);
        stimulus.put("type", type);
        stimulus.put("instruction", instruction);
        putOrNull(stimulus, "passageText", passage);
        putOrNull(stimulus, "transcriptText", transcript);
        putOrNull(stimulus, "mediaReference", audio);
        putOrNull(stimulus, "imageReference", image);
        ObjectNode provenance;
        if (stimulus.path("provenance") instanceof ObjectNode existingProvenance) {
            provenance = existingProvenance;
        } else {
            provenance = objectMapper.createObjectNode();
            stimulus.set("provenance", provenance);
        }
        if (!provenance.hasNonNull("source")) {
            provenance.put("source", source == null || source.isBlank() ? "MANUAL" : source);
        }
        if (!provenance.has("approved")) {
            provenance.put("approved", "MANUAL".equalsIgnoreCase(provenance.path("source").asText()));
        }
        if (group.has("sourceRegionIds") && !provenance.has("sourceRegionIds")) {
            provenance.set("sourceRegionIds", group.path("sourceRegionIds").deepCopy());
        }
        group.set("stimulus", stimulus);

        // Compatibility fields remain readable by the existing editor while v2 is adopted.
        group.put("passageText", "LISTENING_AUDIO".equals(type) ? transcript : passage);
        group.put("stimulusKind", switch (type) {
            case "READING_PASSAGE" -> "PASSAGE";
            case "LISTENING_AUDIO" -> "TRANSCRIPT";
            default -> "NONE";
        });
        group.put("audioUrl", audio);
        group.put("imageUrl", image);
    }

    private void normalizeSourceRegionIds(ObjectNode node) {
        if (!node.has("sourceRegionIds") || !node.path("sourceRegionIds").isArray()) {
            node.putArray("sourceRegionIds");
        }
    }

    private ObjectNode object(ObjectNode parent, String field) {
        if (parent.path(field) instanceof ObjectNode object) {
            return object;
        }
        ObjectNode created = objectMapper.createObjectNode();
        parent.set(field, created);
        return created;
    }

    private ArrayNode array(ObjectNode parent, String field) {
        if (parent.path(field) instanceof ArrayNode array) {
            return array;
        }
        ArrayNode created = objectMapper.createArrayNode();
        parent.set(field, created);
        return created;
    }

    private static ObjectNode requireObject(JsonNode value, String label) {
        if (value instanceof ObjectNode object) {
            return object;
        }
        throw new IllegalArgumentException("Invalid " + label + " in draft contract");
    }

    private static String text(ObjectNode node, String field, String fallback) {
        String value = node.path(field).asText("").trim();
        return value.isBlank() ? fallback : value;
    }

    private static String firstText(ObjectNode first, String firstField,
                                    ObjectNode second, String secondField,
                                    ObjectNode third, String thirdField) {
        String value = text(first, firstField, "");
        if (!value.isBlank()) return value;
        value = text(second, secondField, "");
        return value.isBlank() ? text(third, thirdField, "") : value;
    }

    private static void ensureId(ObjectNode node, String prefix) {
        if (!node.hasNonNull("clientId") || node.path("clientId").asText().isBlank()) {
            node.put("clientId", prefix + "-" + UUID.randomUUID());
        }
    }

    private static void putOrNull(ObjectNode node, String field, String value) {
        if (value == null || value.isBlank()) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    public record NormalizedDraft(
            String json,
            String category,
            String programCode,
            Long programVersionId,
            String examTemplateCode
    ) {
    }
}
