package com.ksh.features.practice.service;

import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeQuestionGroupVersion;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.PracticeSectionVersion;
import com.ksh.entities.PracticeSetVersion;
import com.ksh.entities.PracticeTestVersion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PracticeVersionSnapshotTest {

    @Test
    void acceptsOnlyCompleteSameSnapshotQuestionOwnership() {
        PracticeQuestionGroupVersion first = group(21L);
        PracticeQuestionGroupVersion second = group(22L);
        PracticeVersionSnapshot snapshot = snapshot(
                List.of(first, second),
                List.of(question(21L), question(22L)));

        assertThat(snapshot.hasCanonicalQuestionOwnership()).isTrue();
    }

    @Test
    void rejectsEmptyOrNullGroupOwnership() {
        assertThat(snapshot(List.of(), List.of(question(21L)))
                .hasCanonicalQuestionOwnership()).isFalse();
        assertThat(snapshot(List.of(group(21L)), List.of(question(null)))
                .hasCanonicalQuestionOwnership()).isFalse();
    }

    @Test
    void rejectsUnknownGroupAndGroupWithoutQuestion() {
        assertThat(snapshot(List.of(group(21L)), List.of(question(99L)))
                .hasCanonicalQuestionOwnership()).isFalse();
        assertThat(snapshot(
                List.of(group(21L), group(22L)),
                List.of(question(21L))).hasCanonicalQuestionOwnership())
                .isFalse();
    }

    @Test
    void rejectsCrossVersionOrCrossSectionOwnership() {
        PracticeQuestionGroupVersion crossVersion = group(21L);
        when(crossVersion.getPublishedVersionId()).thenReturn(999L);
        assertThat(snapshot(List.of(crossVersion), List.of(question(21L)))
                .hasCanonicalQuestionOwnership()).isFalse();

        PracticeQuestionVersion crossSection = question(21L);
        when(crossSection.getSectionVersionId()).thenReturn(999L);
        assertThat(snapshot(List.of(group(21L)), List.of(crossSection))
                .hasCanonicalQuestionOwnership()).isFalse();
    }

    private static PracticeVersionSnapshot snapshot(
            List<PracticeQuestionGroupVersion> groups,
            List<PracticeQuestionVersion> questions) {
        PracticePublishedVersion published = mock(PracticePublishedVersion.class);
        when(published.getId()).thenReturn(10L);
        PracticeSectionVersion section = mock(PracticeSectionVersion.class);
        when(section.getId()).thenReturn(20L);
        return new PracticeVersionSnapshot(
                published,
                mock(PracticeSetVersion.class),
                mock(PracticeTestVersion.class),
                section,
                groups,
                questions);
    }

    private static PracticeQuestionGroupVersion group(Long id) {
        PracticeQuestionGroupVersion group =
                mock(PracticeQuestionGroupVersion.class);
        when(group.getId()).thenReturn(id);
        when(group.getPublishedVersionId()).thenReturn(10L);
        when(group.getSectionVersionId()).thenReturn(20L);
        return group;
    }

    private static PracticeQuestionVersion question(Long groupId) {
        PracticeQuestionVersion question = mock(PracticeQuestionVersion.class);
        when(question.getPublishedVersionId()).thenReturn(10L);
        when(question.getSectionVersionId()).thenReturn(20L);
        when(question.getGroupVersionId()).thenReturn(groupId);
        return question;
    }
}
