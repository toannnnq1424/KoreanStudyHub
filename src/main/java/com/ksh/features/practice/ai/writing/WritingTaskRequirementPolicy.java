package com.ksh.features.practice.ai.writing;

import java.util.List;

/**
 * Stable requirement IDs used by the Writing task-coverage ledger.
 *
 * <p>The labels describe what the evaluator must prove. They are not a second
 * scoring rubric and do not add points independently.</p>
 */
public final class WritingTaskRequirementPolicy {

    public static final String VERSION = "writing-task-requirements-v1";

    private WritingTaskRequirementPolicy() {
    }

    public static List<Requirement> requirementsFor(String taskType) {
        return switch (normalize(taskType)) {
            case "Q51", "Q52" -> List.of(
                    requirement("CLOZE_BLANK_1_CONTEXT",
                            "Ô thứ nhất phù hợp ngữ cảnh trước và sau chỗ trống",
                            null),
                    requirement("CLOZE_BLANK_2_CONTEXT",
                            "Ô thứ hai phù hợp ngữ cảnh trước và sau chỗ trống",
                            null));
            case "Q53" -> List.of(
                    requirement("Q53_FOUR_TRANSPORT_MODES",
                            "Bao phủ đủ bốn phương tiện trong nguồn đề",
                            "W_CONTENT_TASK_ACHIEVEMENT"),
                    requirement("Q53_DATA_2024",
                            "Nêu đúng số liệu năm 2024",
                            "W_CONTENT_TASK_ACHIEVEMENT"),
                    requirement("Q53_DATA_2026",
                            "Nêu đúng số liệu năm 2026",
                            "W_CONTENT_TASK_ACHIEVEMENT"),
                    requirement("Q53_MAIN_CHANGES",
                            "Mô tả các thay đổi chính giữa hai mốc",
                            "W_CONTENT_TASK_ACHIEVEMENT"),
                    requirement("Q53_PLAUSIBLE_CAUSE",
                            "Nêu nguyên nhân khả dĩ khi đề yêu cầu",
                            "W_CONTENT_TASK_ACHIEVEMENT"),
                    deterministicRequirement("Q53_LENGTH_200_300",
                            "Bài nằm trong phạm vi 200–300 ký tự",
                            "W_CONTENT_TASK_ACHIEVEMENT"));
            case "Q54" -> List.of(
                    requirement("Q54_POSITION",
                            "Nêu lập trường hoặc luận điểm chính phù hợp đề",
                            "W_CONTENT_TASK_ACHIEVEMENT"),
                    requirement("Q54_PROMPT_COVERAGE",
                            "Bao phủ các gợi ý được nêu rõ trong đề",
                            "W_CONTENT_TASK_ACHIEVEMENT"),
                    requirement("Q54_SUPPORT",
                            "Phát triển lập luận bằng lý do, giải thích hoặc ví dụ",
                            "W_CONTENT_TASK_ACHIEVEMENT"),
                    requirement("Q54_LOGICAL_DEVELOPMENT",
                            "Tổ chức lập luận theo trình tự logic",
                            "W_ORGANIZATION_COHERENCE"),
                    deterministicRequirement("Q54_LENGTH_600_700",
                            "Bài nằm trong phạm vi 600–700 ký tự",
                            "W_CONTENT_TASK_ACHIEVEMENT"));
            default -> List.of(
                    requirement("GENERAL_PROMPT_COVERAGE",
                            "Trả lời đầy đủ các yêu cầu được nêu rõ trong đề",
                            "W_CONTENT_TASK_ACHIEVEMENT"),
                    requirement("GENERAL_RELEVANCE",
                            "Nội dung liên quan trực tiếp đến nhiệm vụ",
                            "W_CONTENT_TASK_ACHIEVEMENT"));
        };
    }

    private static Requirement requirement(
            String id,
            String labelVi,
            String scoringCriterionId) {
        return new Requirement(
                id,
                labelVi,
                scoringCriterionId,
                true,
                true);
    }

    private static Requirement deterministicRequirement(
            String id,
            String labelVi,
            String scoringCriterionId) {
        return new Requirement(
                id,
                labelVi,
                scoringCriterionId,
                true,
                false);
    }

    private static String normalize(String taskType) {
        if ("Q51_52".equals(taskType)) {
            return "Q51";
        }
        return taskType == null || taskType.isBlank()
                ? "GENERAL"
                : taskType;
    }

    public record Requirement(
            String requirementId,
            String labelVi,
            String scoringCriterionId,
            boolean required,
            boolean evidenceRequired
    ) {
        public Requirement {
            if (requirementId == null || requirementId.isBlank()
                    || labelVi == null || labelVi.isBlank()) {
                throw new IllegalArgumentException(
                        "Writing task requirement is incomplete");
            }
        }
    }
}
