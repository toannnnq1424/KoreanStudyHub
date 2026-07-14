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
import java.util.ArrayList;

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
        if (!setId.equals(publishedVersion.get().getSetId())
                || !setId.equals(setVersion.get().getSetId())
                || !publishedVersionId.equals(setVersion.get().getPublishedVersionId())
                || !publishedVersionId.equals(testVersion.get().getPublishedVersionId())
                || !publishedVersionId.equals(sectionVersion.get().getPublishedVersionId())
                || !setVersion.get().getId().equals(testVersion.get().getSetVersionId())
                || !testId.equals(testVersion.get().getTestId())
                || !testVersion.get().getId().equals(sectionVersion.get().getTestVersionId())
                || !sectionId.equals(sectionVersion.get().getSectionId())) {
            log.warn("Rejected inconsistent latest practice version chain for set={}, test={}, section={}",
                    setId, testId, sectionId);
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
        if (!publishedVersionId.equals(set.get().getPublishedVersionId())
                || !publishedVersionId.equals(test.get().getPublishedVersionId())
                || !publishedVersionId.equals(section.get().getPublishedVersionId())
                || !setVersionId.equals(test.get().getSetVersionId())
                || !testVersionId.equals(section.get().getTestVersionId())
                || !published.get().getSetId().equals(set.get().getSetId())) {
            log.warn("Rejected inconsistent immutable practice snapshot ids: published={}, set={}, test={}, section={}",
                    publishedVersionId, setVersionId, testVersionId, sectionVersionId);
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

    @Transactional(readOnly = true)
    public String draftSnapshotJson(Long publishedVersionId, Long expectedSetId) {
        PracticePublishedVersion published = publishedVersionRepository.findById(publishedVersionId)
                .orElseThrow(() -> new EntityNotFoundException("Phiên bản xuất bản không tồn tại."));
        if (expectedSetId != null && !expectedSetId.equals(published.getSetId())) {
            throw new IllegalArgumentException("Phiên bản không thuộc học liệu đã chọn.");
        }
        PracticeSetVersion set = setVersionRepository.findByPublishedVersionId(publishedVersionId)
                .orElseThrow(() -> new IllegalStateException("Phiên bản thiếu snapshot cấp set."));
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("schemaVersion", "practice-draft-v3");
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("title", set.getTitle());
            document.put("description", set.getDescription());
            document.put("restoredFromPublishedVersionId", publishedVersionId);
            document.put("restoredFromVersionNumber", published.getVersionNumber());
            root.put("document", document);

            if (set.getMetadataJson() != null && !set.getMetadataJson().isBlank()) {
                com.fasterxml.jackson.databind.JsonNode metadata = objectMapper.readTree(set.getMetadataJson());
                if (metadata.path("materials").isArray()) {
                    root.put("materials", objectMapper.convertValue(
                            metadata.path("materials"), List.class));
                }
            }

            List<Map<String, Object>> tests = new ArrayList<>();
            List<Map<String, Object>> sections = new ArrayList<>();
            List<PracticeTestVersion> testVersions = testVersionRepository
                    .findByPublishedVersionIdOrderByDisplayOrderAscIdAsc(publishedVersionId);
            for (int testIndex = 0; testIndex < testVersions.size(); testIndex++) {
                PracticeTestVersion test = testVersions.get(testIndex);
                int testNo = testIndex + 1;
                String testClientId = "published-test-" + test.getId();
                Map<String, Object> testNode = new LinkedHashMap<>();
                testNode.put("clientId", testClientId);
                testNode.put("testNo", testNo);
                testNode.put("title", test.getTitle());
                testNode.put("description", test.getDescription());
                testNode.put("estimatedMinutes", test.getEstimatedMinutes());
                tests.add(testNode);

                for (PracticeSectionVersion section : sectionVersionRepository
                        .findByTestVersionIdOrderByDisplayOrderAscIdAsc(test.getId())) {
                    Map<String, Object> sectionNode = new LinkedHashMap<>();
                    sectionNode.put("title", section.getTitle());
                    sectionNode.put("skill", section.getSkill());
                    sectionNode.put("sectionType", section.getSectionType());
                    sectionNode.put("lessonCode", section.getSectionType());
                    sectionNode.put("instructions", section.getInstructions());
                    sectionNode.put("durationMinutes", section.getDurationMinutes());
                    sectionNode.put("totalPoints", section.getTotalPoints());
                    sectionNode.put("testNo", testNo);
                    sectionNode.put("testClientId", testClientId);
                    sectionNode.put("groups", versionGroups(section));
                    sections.add(sectionNode);
                }
            }
            if (tests.isEmpty()) {
                throw new IllegalStateException("Phiên bản không có Practice Test.");
            }
            root.put("tests", tests);
            root.put("sections", sections);
            root.put("warnings", List.of());
            return objectMapper.writeValueAsString(root);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể dựng draft từ phiên bản bất biến.", exception);
        }
    }

    private List<Map<String, Object>> versionGroups(PracticeSectionVersion section) throws Exception {
        List<PracticeQuestionGroupVersion> groups = groupVersionRepository
                .findBySectionVersionIdOrderByDisplayOrderAscIdAsc(section.getId());
        List<PracticeQuestionVersion> questions = questionVersionRepository
                .findBySectionVersionIdOrderByDisplayOrderAscQuestionNoAscIdAsc(section.getId());
        List<Map<String, Object>> result = new ArrayList<>();
        for (PracticeQuestionGroupVersion group : groups) {
            Map<String, Object> groupNode = new LinkedHashMap<>();
            groupNode.put("label", group.getGroupLabel());
            groupNode.put("groupCode", group.getGroupLabel());
            groupNode.put("instruction", group.getInstruction());
            groupNode.put("stimulusType", group.getStimulusType());
            groupNode.put("passageText", group.getPassageText());
            groupNode.put("transcriptText", group.getTranscriptText());
            groupNode.put("imageUrl", group.getImageUrl());
            groupNode.put("audioUrl", group.getAudioUrl());
            Map<String, Object> stimulus = new LinkedHashMap<>();
            stimulus.put("schemaVersion", "practice-stimulus-v1");
            stimulus.put("type", group.getStimulusType());
            stimulus.put("instruction", group.getInstruction());
            stimulus.put("passageText", group.getPassageText());
            stimulus.put("transcriptText", group.getTranscriptText());
            stimulus.put("mediaReference", group.getAudioUrl());
            stimulus.put("imageReference", group.getImageUrl());
            if (group.getStimulusProvenanceJson() != null
                    && !group.getStimulusProvenanceJson().isBlank()) {
                stimulus.put("provenance", objectMapper.readTree(
                        group.getStimulusProvenanceJson()));
            }
            groupNode.put("stimulus", stimulus);
            groupNode.put("questions", questions.stream()
                    .filter(question -> group.getId().equals(question.getGroupVersionId()))
                    .map(this::versionQuestion)
                    .toList());
            result.add(groupNode);
        }
        List<PracticeQuestionVersion> ungrouped = questions.stream()
                .filter(question -> question.getGroupVersionId() == null)
                .toList();
        if (!ungrouped.isEmpty()) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("label", "Nhóm khôi phục");
            fallback.put("groupCode", "RESTORED_UNGROUPED");
            fallback.put("instruction", "");
            fallback.put("questions", ungrouped.stream().map(this::versionQuestion).toList());
            result.add(fallback);
        }
        return result;
    }

    private Map<String, Object> versionQuestion(PracticeQuestionVersion question) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("questionNo", question.getQuestionNo());
        node.put("questionType", question.getQuestionType());
        node.put("prompt", question.getPrompt());
        node.put("answerKey", question.getAnswerKey());
        node.put("answer", Map.of("value", question.getAnswerKey() == null
                ? "" : question.getAnswerKey()));
        node.put("explanationVi", question.getExplanation());
        node.put("points", question.getPoints());
        if (question.getWritingTaskType() != null) {
            node.put("essayTaskType", question.getWritingTaskType().name());
        }
        putJson(node, "options", question.getOptionsJson(), List.of());
        putJson(node, "questionContent", question.getQuestionContentJson(), null);
        putJson(node, "answerSpec", question.getAnswerSpecJson(), null);
        return node;
    }

    private void putJson(Map<String, Object> target, String key, String json,
                         Object fallback) {
        if (json == null || json.isBlank()) {
            if (fallback != null) target.put(key, fallback);
            return;
        }
        try {
            target.put(key, objectMapper.readTree(json));
        } catch (Exception exception) {
            throw new IllegalStateException("Snapshot field không phải JSON hợp lệ: " + key,
                    exception);
        }
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
