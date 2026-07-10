package com.ksh.features.practice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.PracticeQuestionGroup;
import com.ksh.entities.PracticeQuestionGroupVersion;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.PracticeSection;
import com.ksh.entities.PracticeSectionVersion;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.PracticeSetVersion;
import com.ksh.entities.PracticeTest;
import com.ksh.entities.PracticeTestVersion;
import com.ksh.features.practice.repository.PracticePublishedVersionRepository;
import com.ksh.features.practice.repository.PracticeQuestionGroupRepository;
import com.ksh.features.practice.repository.PracticeQuestionGroupVersionRepository;
import com.ksh.features.practice.repository.PracticeQuestionRepository;
import com.ksh.features.practice.repository.PracticeQuestionVersionRepository;
import com.ksh.features.practice.repository.PracticeSectionRepository;
import com.ksh.features.practice.repository.PracticeSectionVersionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.repository.PracticeSetVersionRepository;
import com.ksh.features.practice.repository.PracticeTestRepository;
import com.ksh.features.practice.repository.PracticeTestVersionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PracticePublishedVersionService {
    private static final Logger log = LoggerFactory.getLogger(PracticePublishedVersionService.class);


    private final PracticePublishedVersionRepository publishedVersionRepository;
    private final PracticeSetVersionRepository setVersionRepository;
    private final PracticeTestVersionRepository testVersionRepository;
    private final PracticeSectionVersionRepository sectionVersionRepository;
    private final PracticeQuestionGroupVersionRepository groupVersionRepository;
    private final PracticeQuestionVersionRepository questionVersionRepository;
    private final PracticeSetRepository setRepository;
    private final PracticeTestRepository testRepository;
    private final PracticeSectionRepository sectionRepository;
    private final PracticeQuestionGroupRepository groupRepository;
    private final PracticeQuestionRepository questionRepository;
    private final ObjectMapper objectMapper;

    public PracticePublishedVersionService(
            PracticePublishedVersionRepository publishedVersionRepository,
            PracticeSetVersionRepository setVersionRepository,
            PracticeTestVersionRepository testVersionRepository,
            PracticeSectionVersionRepository sectionVersionRepository,
            PracticeQuestionGroupVersionRepository groupVersionRepository,
            PracticeQuestionVersionRepository questionVersionRepository,
            PracticeSetRepository setRepository,
            PracticeTestRepository testRepository,
            PracticeSectionRepository sectionRepository,
            PracticeQuestionGroupRepository groupRepository,
            PracticeQuestionRepository questionRepository,
            ObjectMapper objectMapper) {
        this.publishedVersionRepository = publishedVersionRepository;
        this.setVersionRepository = setVersionRepository;
        this.testVersionRepository = testVersionRepository;
        this.sectionVersionRepository = sectionVersionRepository;
        this.groupVersionRepository = groupVersionRepository;
        this.questionVersionRepository = questionVersionRepository;
        this.setRepository = setRepository;
        this.testRepository = testRepository;
        this.sectionRepository = sectionRepository;
        this.groupRepository = groupRepository;
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PracticePublishedVersion createPublishedVersion(Long setId, Long publishedBy) {
        PracticeSet set = setRepository.findById(setId)
                .orElseThrow(() -> new EntityNotFoundException("Practice set not found."));
        List<PracticeTest> tests = testRepository.findBySetIdOrderByDisplayOrderAsc(setId);
        List<PracticeSection> sections = sectionRepository.findBySetIdOrderByDisplayOrderAsc(setId);
        List<PracticeQuestionGroup> groups = groupRepository.findBySetIdOrderByDisplayOrderAsc(setId);
        List<PracticeQuestion> questions = questionRepository.findBySetIdOrderByDisplayOrderAsc(setId);
        validateUngroupedQuestionScope(setId, tests, sections, questions);

        Integer nextVersion = publishedVersionRepository.maxVersionNumberBySetId(setId) + 1;
        PracticePublishedVersion publishedVersion = publishedVersionRepository.save(
                new PracticePublishedVersion(setId, nextVersion, PracticePublishedVersion.STATUS_PUBLISHED,
                        contentHash(setId), publishedBy));

        PracticeSetVersion setVersion = setVersionRepository.save(new PracticeSetVersion(publishedVersion.getId(), set));
        for (PracticeTest test : tests) {
            PracticeTestVersion testVersion = testVersionRepository.save(
                    new PracticeTestVersion(publishedVersion.getId(), setVersion.getId(), test));
            List<PracticeSection> testSections = sections.stream()
                    .filter(section -> test.getId().equals(section.getTestId()))
                    .sorted(Comparator.comparing(PracticeSection::getDisplayOrder, Comparator.nullsLast(Integer::compareTo)))
                    .toList();
            for (PracticeSection section : testSections) {
                PracticeSectionVersion sectionVersion = sectionVersionRepository.save(
                        new PracticeSectionVersion(publishedVersion.getId(), testVersion.getId(), section));
                Map<Long, Long> groupVersionIds = new LinkedHashMap<>();
                for (PracticeQuestionGroup group : groups.stream()
                        .filter(group -> section.getId().equals(group.getSectionId()))
                        .sorted(Comparator.comparing(PracticeQuestionGroup::getDisplayOrder, Comparator.nullsLast(Integer::compareTo)))
                        .toList()) {
                    PracticeQuestionGroupVersion groupVersion = groupVersionRepository.save(
                            new PracticeQuestionGroupVersion(publishedVersion.getId(), sectionVersion.getId(), group));
                    groupVersionIds.put(group.getId(), groupVersion.getId());
                }
                for (PracticeQuestion question : questions.stream()
                        .filter(question -> groupVersionIds.containsKey(question.getGroupId()) ||
                                (question.getGroupId() == null && testSections.size() == 1))
                        .sorted(Comparator.comparing(PracticeQuestion::getDisplayOrder, Comparator.nullsLast(Integer::compareTo))
                                .thenComparing(PracticeQuestion::getQuestionNo, Comparator.nullsLast(Integer::compareTo)))
                        .toList()) {
                    questionVersionRepository.save(new PracticeQuestionVersion(
                            publishedVersion.getId(),
                            sectionVersion.getId(),
                            question.getGroupId() == null ? null : groupVersionIds.get(question.getGroupId()),
                            question));
                }
            }
        }
        return publishedVersion;
    }

    private void validateUngroupedQuestionScope(Long setId, List<PracticeTest> tests,
                                                List<PracticeSection> sections,
                                                List<PracticeQuestion> questions) {
        boolean hasUngroupedQuestions = questions.stream().anyMatch(question -> question.getGroupId() == null);
        if (!hasUngroupedQuestions) {
            return;
        }
        for (PracticeTest test : tests) {
            long sectionCount = sections.stream()
                    .filter(section -> test.getId().equals(section.getTestId()))
                    .count();
            if (sectionCount > 1) {
                throw new IllegalStateException(
                        "Cannot publish immutable practice version for setId=" + setId +
                        " because ungrouped questions are ambiguous in multi-section testId=" + test.getId());
            }
        }
        if (tests.size() > 1) {
            throw new IllegalStateException(
                    "Cannot publish immutable practice version for setId=" + setId +
                    " because ungrouped questions are ambiguous across multiple tests.");
        }
    }

    @Transactional(readOnly = true)
    public Optional<PracticeAttemptVersionLock> latestLock(Long setId, Long testId, Long sectionId) {
        Optional<PracticePublishedVersion> publishedVersion = publishedVersionRepository
                .findFirstBySetIdAndStatusOrderByVersionNumberDesc(setId, PracticePublishedVersion.STATUS_PUBLISHED);
        if (publishedVersion.isEmpty()) {
            return Optional.empty();
        }
        Long publishedVersionId = publishedVersion.get().getId();
        Optional<PracticeSetVersion> setVersion = setVersionRepository.findByPublishedVersionId(publishedVersionId);
        Optional<PracticeTestVersion> testVersion = testVersionRepository.findByPublishedVersionIdAndTestId(publishedVersionId, testId);
        Optional<PracticeSectionVersion> sectionVersion = sectionVersionRepository.findByPublishedVersionIdAndSectionId(publishedVersionId, sectionId);
        if (setVersion.isEmpty() || testVersion.isEmpty() || sectionVersion.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PracticeAttemptVersionLock(
                publishedVersionId,
                setVersion.get().getId(),
                testVersion.get().getId(),
                sectionVersion.get().getId()));
    }

    @Transactional(readOnly = true)
    public Optional<PracticeVersionSnapshot> snapshot(Long publishedVersionId, Long setVersionId,
                                                      Long testVersionId, Long sectionVersionId) {
        if (publishedVersionId == null || setVersionId == null || testVersionId == null || sectionVersionId == null) {
            return Optional.empty();
        }
        Optional<PracticePublishedVersion> published = publishedVersionRepository.findById(publishedVersionId);
        Optional<PracticeSetVersion> set = setVersionRepository.findById(setVersionId);
        Optional<PracticeTestVersion> test = testVersionRepository.findById(testVersionId);
        Optional<PracticeSectionVersion> section = sectionVersionRepository.findById(sectionVersionId);
        if (published.isEmpty() || set.isEmpty() || test.isEmpty() || section.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PracticeVersionSnapshot(
                published.get(),
                set.get(),
                test.get(),
                section.get(),
                groupVersionRepository.findBySectionVersionIdOrderByDisplayOrderAscIdAsc(sectionVersionId),
                questionVersionRepository.findBySectionVersionIdOrderByDisplayOrderAscQuestionNoAscIdAsc(sectionVersionId)));
    }

    private String contentHash(Long setId) {
        try {
            Map<String, Object> graph = new LinkedHashMap<>();
            graph.put("set", setRepository.findById(setId).orElse(null));
            graph.put("tests", testRepository.findBySetIdOrderByDisplayOrderAsc(setId));
            graph.put("sections", sectionRepository.findBySetIdOrderByDisplayOrderAsc(setId));
            graph.put("groups", groupRepository.findBySetIdOrderByDisplayOrderAsc(setId));
            graph.put("questions", questionRepository.findBySetIdOrderByDisplayOrderAsc(setId));
            byte[] payload = objectMapper.writeValueAsString(graph).getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("[PracticePublishedVersion] Failed to compute immutable content hash for setId={}", setId, e);
            throw new IllegalStateException("Cannot publish immutable practice version because content hash could not be computed.", e);
        }
    }
}
