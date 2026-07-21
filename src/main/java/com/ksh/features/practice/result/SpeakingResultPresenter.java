package com.ksh.features.practice.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationResult;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationStatus;
import com.ksh.features.practice.ai.speaking.SpeakingEvidenceMode;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluatorCapability;
import com.ksh.features.practice.ai.speaking.SpeakingFeedbackCompatibilityReader;
import com.ksh.features.practice.ai.speaking.SpeakingRubricCriterion;
import com.ksh.features.practice.ai.writing.WritingFeedbackCompatibilityReader;
import com.ksh.features.practice.ai.writing.WritingFeedbackViewMapper;
import com.ksh.features.practice.dto.PracticeDtos.ResultAnswerDistribution;
import com.ksh.features.practice.dto.PracticeDtos.ResultEvaluationBand;
import com.ksh.features.practice.dto.PracticeDtos.ResultFeedbackAvailability;
import com.ksh.features.practice.dto.PracticeDtos.ResultScoreSummary;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingActionPlanView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingCriterionResult;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingResultPayload;
import com.ksh.features.practice.dto.PracticeDtos.WritingFeedbackView;
import com.ksh.features.practice.dto.PracticeDtos.WritingFindingView;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
final class SpeakingResultPresenter implements PracticeResultPresenter {

    private static final String CONTRACT_FIELD = "_contract";
    private static final String AI_CONTRACT = "speaking_ai_v1";
    private static final String MIXED_CONTRACT = "speaking_mixed_v1";
    private static final String FEEDBACK_BY_QUESTION = "speaking_feedback_by_question";
    private static final String ESSAY_FEEDBACK_BY_QUESTION = "essay_feedback_by_question";

    private final ObjectMapper objectMapper;
    private final SpeakingFeedbackCompatibilityReader feedbackReader;
    private final WritingFeedbackCompatibilityReader writingFeedbackReader;
    private final WritingFeedbackViewMapper writingFeedbackMapper;

    SpeakingResultPresenter(
            ObjectMapper objectMapper,
            SpeakingFeedbackCompatibilityReader feedbackReader,
            WritingFeedbackCompatibilityReader writingFeedbackReader,
            WritingFeedbackViewMapper writingFeedbackMapper) {
        this.objectMapper = objectMapper;
        this.feedbackReader = feedbackReader;
        this.writingFeedbackReader = writingFeedbackReader;
        this.writingFeedbackMapper = writingFeedbackMapper;
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

        List<SpeakingCriterionResult> criteria = criteria(
                segments, questions.size(), legacyUnverified);
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

    private static List<SpeakingCriterionResult> criteria(
            List<SegmentFeedback> segments,
            int totalSegments,
            int legacyUnverifiedSegments) {
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
        boolean transcriptOnly = !segments.isEmpty() && segments.stream()
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
            } else if (segments.isEmpty() && legacyUnverifiedSegments > 0) {
                availability = "LEGACY_UNVERIFIED";
            } else if (transcriptOnly && criterion.requiresAcousticEvidence()) {
                availability = "NOT_SCORABLE";
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

    private enum TextKind {
        SUMMARY,
        STRENGTH,
        NEED
    }
}
