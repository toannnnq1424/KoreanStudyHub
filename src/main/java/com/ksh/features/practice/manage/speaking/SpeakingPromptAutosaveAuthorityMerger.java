package com.ksh.features.practice.manage.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Prevents the legacy whole-draft autosave endpoint from becoming a second
 * writer for prompt text or Speaking authoring options once a v2 source exists.
 * Delivery timing and other non-authority question fields remain editable by
 * the generic editor.
 */
@Component
public class SpeakingPromptAutosaveAuthorityMerger {

    private final SpeakingPromptSourceRepository sourceRepository;
    private final ObjectMapper objectMapper;

    public SpeakingPromptAutosaveAuthorityMerger(
            SpeakingPromptSourceRepository sourceRepository,
            ObjectMapper objectMapper) {
        this.sourceRepository = sourceRepository;
        this.objectMapper = objectMapper;
    }

    public String preserveAcceptedAuthority(
            Long draftId,
            String persistedJson,
            String incomingJson) {
        java.util.Set<String> managedClientIds = sourceRepository
                .findByDraftId(draftId)
                .stream()
                .map(SpeakingPromptSource::getQuestionClientId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (managedClientIds.isEmpty()) {
            return incomingJson;
        }
        ObjectNode persisted = object(persistedJson);
        ObjectNode incoming = object(incomingJson);
        Map<String, ObjectNode> acceptedQuestions = questionsByClientId(persisted);
        Map<String, ObjectNode> submittedQuestions = questionsByClientId(incoming);
        for (String clientId : managedClientIds) {
            ObjectNode accepted = acceptedQuestions.get(clientId);
            ObjectNode submitted = submittedQuestions.get(clientId);
            if (accepted == null && submitted == null) {
                continue;
            }
            requireSpeakingQuestion(accepted);
            if (submitted == null) {
                continue;
            }
            requireSpeakingQuestion(submitted);
            copyAuthorityField(accepted, submitted, "prompt");
            copyAuthorityField(
                    accepted, submitted, "speakingPromptAuthoring");
        }
        return incoming.toString();
    }

    private ObjectNode object(String json) {
        try {
            JsonNode parsed = objectMapper.readTree(json);
            if (parsed instanceof ObjectNode object) {
                return object;
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Định dạng dữ liệu JSON không hợp lệ.", exception);
        }
        throw new IllegalArgumentException(
                "Dữ liệu bản nháp phải là một JSON object.");
    }

    private static Map<String, ObjectNode> questionsByClientId(
            ObjectNode root) {
        Map<String, ObjectNode> indexed = new LinkedHashMap<>();
        for (JsonNode section : root.path("sections")) {
            for (JsonNode group : section.path("groups")) {
                for (JsonNode candidate : group.path("questions")) {
                    if (!(candidate instanceof ObjectNode question)) {
                        continue;
                    }
                    String clientId = question.path("clientId").asText("");
                    if (!clientId.isBlank()) {
                        ObjectNode previous = indexed.put(clientId, question);
                        if (previous != null) {
                            throw new SpeakingPromptAuthoringConflictException(
                                    "Question client ID is not unique inside the draft.");
                        }
                    }
                }
            }
        }
        return indexed;
    }

    private static void requireSpeakingQuestion(ObjectNode question) {
        if (question == null
                || !"SPEAKING".equalsIgnoreCase(
                        question.path("questionType").asText())) {
            throw new SpeakingPromptAuthoringConflictException(
                    "Managed Speaking prompt mode no longer matches the draft.");
        }
    }

    private static void copyAuthorityField(
            ObjectNode accepted,
            ObjectNode submitted,
            String field) {
        JsonNode value = accepted.get(field);
        if (value == null) {
            submitted.remove(field);
        } else {
            submitted.set(field, value.deepCopy());
        }
    }
}
