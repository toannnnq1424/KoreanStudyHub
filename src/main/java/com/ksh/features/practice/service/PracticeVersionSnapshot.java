package com.ksh.features.practice.service;

import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeQuestionGroupVersion;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.PracticeSectionVersion;
import com.ksh.entities.PracticeSetVersion;
import com.ksh.entities.PracticeTestVersion;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record PracticeVersionSnapshot(
        PracticePublishedVersion publishedVersion,
        PracticeSetVersion setVersion,
        PracticeTestVersion testVersion,
        PracticeSectionVersion sectionVersion,
        List<PracticeQuestionGroupVersion> groups,
        List<PracticeQuestionVersion> questions
) {
    /**
     * A learner-deliverable snapshot must not rely on the historical
     * null-group fallback. Every immutable question belongs to exactly one
     * immutable group in the same published version and section, and every
     * group owns at least one question.
     */
    public boolean hasCanonicalQuestionOwnership() {
        if (publishedVersion == null || sectionVersion == null
                || publishedVersion.getId() == null
                || sectionVersion.getId() == null
                || groups == null || groups.isEmpty()
                || questions == null || questions.isEmpty()) {
            return false;
        }

        Long publishedVersionId = publishedVersion.getId();
        Long sectionVersionId = sectionVersion.getId();
        Set<Long> groupIds = groups.stream()
                .filter(Objects::nonNull)
                .filter(group -> group.getId() != null
                        && group.getPublishedVersionId() != null
                        && group.getSectionVersionId() != null
                        && publishedVersionId.equals(group.getPublishedVersionId())
                        && sectionVersionId.equals(group.getSectionVersionId()))
                .map(PracticeQuestionGroupVersion::getId)
                .collect(Collectors.toSet());
        if (groupIds.size() != groups.size()) {
            return false;
        }

        Set<Long> ownedGroupIds = questions.stream()
                .filter(Objects::nonNull)
                .filter(question -> question.getGroupVersionId() != null
                        && question.getPublishedVersionId() != null
                        && question.getSectionVersionId() != null
                        && publishedVersionId.equals(question.getPublishedVersionId())
                        && sectionVersionId.equals(question.getSectionVersionId())
                        && groupIds.contains(question.getGroupVersionId()))
                .map(PracticeQuestionVersion::getGroupVersionId)
                .collect(Collectors.toSet());
        return ownedGroupIds.size() == groupIds.size()
                && questions.stream().allMatch(question -> question != null
                        && question.getGroupVersionId() != null
                        && publishedVersionId.equals(question.getPublishedVersionId())
                        && sectionVersionId.equals(question.getSectionVersionId())
                        && groupIds.contains(question.getGroupVersionId()));
    }
}
