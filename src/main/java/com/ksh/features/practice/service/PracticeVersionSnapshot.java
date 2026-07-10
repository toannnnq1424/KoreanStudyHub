package com.ksh.features.practice.service;

import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeQuestionGroupVersion;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.PracticeSectionVersion;
import com.ksh.entities.PracticeSetVersion;
import com.ksh.entities.PracticeTestVersion;

import java.util.List;

public record PracticeVersionSnapshot(
        PracticePublishedVersion publishedVersion,
        PracticeSetVersion setVersion,
        PracticeTestVersion testVersion,
        PracticeSectionVersion sectionVersion,
        List<PracticeQuestionGroupVersion> groups,
        List<PracticeQuestionVersion> questions
) {
}
