package com.ksh.features.practice.ai;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record WritingScoringRubric(
        String taskType,
        List<WritingScoringCriterion> criteria
) {
    public WritingScoringRubric {
        taskType = taskType == null || taskType.isBlank() ? "GENERAL" : taskType;
        criteria = criteria == null ? List.of() : List.copyOf(criteria);
        if (criteria.isEmpty()) {
            throw new IllegalArgumentException("criteria must not be empty");
        }
        Set<String> ids = criteria.stream()
                .map(WritingScoringCriterion::criterionId)
                .collect(Collectors.toSet());
        if (ids.size() != criteria.size()) {
            throw new IllegalArgumentException("criterion IDs must be unique");
        }
    }

    public int totalMaxScore() {
        return criteria.stream().mapToInt(WritingScoringCriterion::maxScore).sum();
    }
}
