package com.ksh.features.practice.ai.readinglistening;

import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeQuestionGroupVersion;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.PracticeSectionVersion;
import com.ksh.features.practice.ai.readinglistening.ExplanationInputFactory.PreparedExplanation;
import com.ksh.features.practice.repository.PracticePublishedVersionRepository;
import com.ksh.features.practice.repository.PracticeQuestionGroupVersionRepository;
import com.ksh.features.practice.repository.PracticeQuestionVersionRepository;
import com.ksh.features.practice.repository.PracticeSectionVersionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionExplanationWorkLoader {

    private final PracticeQuestionVersionRepository questionRepository;
    private final PracticeQuestionGroupVersionRepository groupRepository;
    private final PracticeSectionVersionRepository sectionRepository;
    private final PracticePublishedVersionRepository publishedVersionRepository;
    private final ExplanationInputFactory inputFactory;

    public QuestionExplanationWorkLoader(
            PracticeQuestionVersionRepository questionRepository,
            PracticeQuestionGroupVersionRepository groupRepository,
            PracticeSectionVersionRepository sectionRepository,
            PracticePublishedVersionRepository publishedVersionRepository,
            ExplanationInputFactory inputFactory) {
        this.questionRepository = questionRepository;
        this.groupRepository = groupRepository;
        this.sectionRepository = sectionRepository;
        this.publishedVersionRepository = publishedVersionRepository;
        this.inputFactory = inputFactory;
    }

    @Transactional(readOnly = true)
    public ExplanationWork load(
            QuestionExplanationTaskTransactions.ClaimedTask claim) {
        PracticeQuestionVersion question = questionRepository.findById(claim.sourceQuestionVersionId())
                .orElseThrow(() -> new EntityNotFoundException("Source question version not found"));
        PracticeSectionVersion section = sectionRepository.findById(question.getSectionVersionId())
                .orElseThrow(() -> new EntityNotFoundException("Source section version not found"));
        PracticeQuestionGroupVersion group = question.getGroupVersionId() == null
                ? null
                : groupRepository.findById(question.getGroupVersionId())
                        .orElseThrow(() -> new EntityNotFoundException("Source question group version not found"));
        PracticePublishedVersion published = publishedVersionRepository.findById(question.getPublishedVersionId())
                .orElseThrow(() -> new EntityNotFoundException("Published version not found"));
        PreparedExplanation prepared = inputFactory.prepare(question, group, section);
        if (!claim.fingerprint().equals(prepared.fingerprint().fingerprint())) {
            throw new ExplanationProviderException(
                    "IMMUTABLE_INPUT_MISMATCH",
                    "Source question no longer matches the bound explanation artifact.",
                    false);
        }
        if (prepared.input().readinessIssue() != null) {
            throw new ExplanationProviderException(
                    prepared.input().readinessIssue(),
                    "Immutable explanation input does not satisfy generation prerequisites.",
                    false);
        }
        return new ExplanationWork(prepared, published.getId());
    }

    public record ExplanationWork(PreparedExplanation prepared, Long publishedVersionId) {
    }
}
