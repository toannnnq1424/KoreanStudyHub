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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
            if (artifact == null) {
                continue;
            }
            if (QuestionExplanationArtifact.STATUS_READY.equals(artifact.getStatus())
                    && validJson(artifact.getExplanationJson())) {
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
        if (artifact == null || !QuestionExplanationArtifact.STATUS_READY.equals(artifact.getStatus())
                || !validJson(artifact.getExplanationJson())) {
            return Optional.empty();
        }
        return Optional.of(artifact.getExplanationJson());
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

    private boolean validJson(String value) {
        if (blank(value)) return false;
        try {
            JsonNode root = objectMapper.readTree(value);
            return root.isObject()
                    && nonBlankText(root, "meaningVi")
                    && nonBlankText(root, "evidenceQuote")
                    && nonBlankText(root, "correctReasonVi")
                    && root.path("relatedTranslationVi").isTextual()
                    && validEliminatedOptions(root.path("eliminatedOptions"));
        } catch (Exception exception) {
            return false;
        }
    }

    private static boolean validEliminatedOptions(JsonNode options) {
        if (!options.isArray()) return false;
        for (JsonNode option : options) {
            if (!option.isObject()
                    || !nonBlankText(option, "optionKey")
                    || !nonBlankText(option, "reasonVi")) {
                return false;
            }
        }
        return true;
    }

    private static boolean nonBlankText(JsonNode root, String field) {
        JsonNode value = root.path(field);
        return value.isTextual() && !value.asText().isBlank();
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
