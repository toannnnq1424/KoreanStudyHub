package com.ksh.features.practice.ai.readinglistening;

import com.ksh.entities.PracticeQuestionGroupVersion;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.PracticeSectionVersion;
import com.ksh.entities.QuestionExplanationArtifact;
import com.ksh.entities.QuestionVersionExplanationBinding;
import com.ksh.features.practice.ai.readinglistening.ExplanationInputFactory.PreparedExplanation;
import com.ksh.features.practice.repository.PracticeQuestionGroupVersionRepository;
import com.ksh.features.practice.repository.PracticeQuestionVersionRepository;
import com.ksh.features.practice.repository.PracticeSectionVersionRepository;
import com.ksh.features.practice.repository.QuestionExplanationArtifactRepository;
import com.ksh.features.practice.repository.QuestionExplanationGenerationTaskRepository;
import com.ksh.features.practice.repository.QuestionVersionExplanationBindingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QuestionExplanationPreparationService {

    private static final int MAX_ATTEMPTS = 4;

    private final PracticeSectionVersionRepository sectionRepository;
    private final PracticeQuestionGroupVersionRepository groupRepository;
    private final PracticeQuestionVersionRepository questionRepository;
    private final ExplanationInputFactory inputFactory;
    private final QuestionExplanationArtifactRepository artifactRepository;
    private final QuestionVersionExplanationBindingRepository bindingRepository;
    private final QuestionExplanationGenerationTaskRepository taskRepository;

    public QuestionExplanationPreparationService(
            PracticeSectionVersionRepository sectionRepository,
            PracticeQuestionGroupVersionRepository groupRepository,
            PracticeQuestionVersionRepository questionRepository,
            ExplanationInputFactory inputFactory,
            QuestionExplanationArtifactRepository artifactRepository,
            QuestionVersionExplanationBindingRepository bindingRepository,
            QuestionExplanationGenerationTaskRepository taskRepository) {
        this.sectionRepository = sectionRepository;
        this.groupRepository = groupRepository;
        this.questionRepository = questionRepository;
        this.inputFactory = inputFactory;
        this.artifactRepository = artifactRepository;
        this.bindingRepository = bindingRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED)
    public PreparationSummary preparePublishedVersion(Long publishedVersionId) {
        Map<Long, PracticeSectionVersion> sections = sectionRepository
                .findByPublishedVersionIdOrderByTestVersionIdAscDisplayOrderAscIdAsc(publishedVersionId)
                .stream()
                .collect(Collectors.toMap(PracticeSectionVersion::getId, Function.identity()));
        Map<Long, PracticeQuestionGroupVersion> groups = groupRepository
                .findByPublishedVersionIdOrderBySectionVersionIdAscDisplayOrderAscIdAsc(publishedVersionId)
                .stream()
                .collect(Collectors.toMap(PracticeQuestionGroupVersion::getId, Function.identity()));

        int eligible = 0;
        int reused = 0;
        int queued = 0;
        int failed = 0;
        for (PracticeQuestionVersion question : questionRepository
                .findByPublishedVersionIdOrderBySectionVersionIdAscDisplayOrderAscQuestionNoAscIdAsc(
                        publishedVersionId)) {
            PracticeSectionVersion section = sections.get(question.getSectionVersionId());
            if (section == null || !isObjectiveSkill(section.getSkill())) {
                continue;
            }
            eligible++;
            QuestionVersionExplanationBinding existingBinding = bindingRepository
                    .findByQuestionVersionIdAndExplanationLanguage(
                            question.getId(), ReadingListeningExplanationClient.EXPLANATION_LANGUAGE)
                    .orElse(null);
            if (existingBinding != null) {
                QuestionExplanationArtifact existingArtifact = artifactRepository
                        .findById(existingBinding.getArtifactId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Bound explanation artifact was not available"));
                if (QuestionExplanationArtifact.STATUS_READY.equals(existingArtifact.getStatus())) {
                    reused++;
                } else if (QuestionExplanationArtifact.STATUS_FAILED.equals(existingArtifact.getStatus())) {
                    failed++;
                } else if (QuestionExplanationArtifact.STATUS_PENDING.equals(existingArtifact.getStatus())) {
                    PracticeQuestionGroupVersion group = question.getGroupVersionId() == null
                            ? null
                            : groups.get(question.getGroupVersionId());
                    PreparedExplanation prepared = inputFactory.prepare(question, group, section);
                    if (!Objects.equals(
                            existingBinding.getFingerprint(),
                            prepared.fingerprint().fingerprint())) {
                        throw new IllegalStateException(
                                "Bound explanation fingerprint does not match immutable question input");
                    }
                    if (prepared.input().readinessIssue() != null) {
                        markReadinessFailure(existingArtifact, prepared.input().readinessIssue());
                        failed++;
                    } else {
                        queued += taskRepository.insertPendingIfAbsent(
                                existingArtifact.getId(), question.getId(), MAX_ATTEMPTS);
                    }
                }
                continue;
            }
            PracticeQuestionGroupVersion group = question.getGroupVersionId() == null
                    ? null
                    : groups.get(question.getGroupVersionId());
            PreparedExplanation prepared = inputFactory.prepare(question, group, section);
            ExplanationFingerprint fingerprint = prepared.fingerprint();
            artifactRepository.insertPendingIfAbsent(
                    fingerprint.fingerprint(),
                    prepared.input().skill().name(),
                    prepared.input().questionType().name(),
                    fingerprint.assessmentSchemaVersion(),
                    fingerprint.providerModel(),
                    fingerprint.promptVersion(),
                    fingerprint.responseSchemaVersion(),
                    fingerprint.explanationLanguage(),
                    fingerprint.questionHash(),
                    fingerprint.stimulusHash(),
                    fingerprint.answerSpecHash(),
                    fingerprint.mediaBundleHash(),
                    fingerprint.inputContractJson());
            QuestionExplanationArtifact artifact = artifactRepository
                    .findByFingerprint(fingerprint.fingerprint())
                    .orElseThrow(() -> new IllegalStateException(
                            "Explanation artifact was not available after insertion"));

            bindingRepository.bindIfAbsent(
                    question.getId(),
                    artifact.getId(),
                    fingerprint.explanationLanguage(),
                    fingerprint.fingerprint());
            QuestionVersionExplanationBinding binding = bindingRepository
                    .findByQuestionVersionIdAndExplanationLanguage(
                            question.getId(), fingerprint.explanationLanguage())
                    .orElseThrow(() -> new IllegalStateException(
                            "Explanation binding was not available after insertion"));
            if (!artifact.getId().equals(binding.getArtifactId())) {
                QuestionExplanationArtifact concurrentlyBound = artifactRepository
                        .findById(binding.getArtifactId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Concurrently bound explanation artifact was not available"));
                if (QuestionExplanationArtifact.STATUS_READY.equals(concurrentlyBound.getStatus())) {
                    reused++;
                } else if (QuestionExplanationArtifact.STATUS_FAILED.equals(concurrentlyBound.getStatus())) {
                    failed++;
                }
                continue;
            }

            if (QuestionExplanationArtifact.STATUS_READY.equals(artifact.getStatus())) {
                reused++;
                continue;
            }
            if (prepared.input().readinessIssue() != null) {
                markReadinessFailure(artifact, prepared.input().readinessIssue());
                failed++;
                continue;
            }
            if (QuestionExplanationArtifact.STATUS_FAILED.equals(artifact.getStatus())) {
                failed++;
                continue;
            }
            if (QuestionExplanationArtifact.STATUS_PENDING.equals(artifact.getStatus())) {
                queued += taskRepository.insertPendingIfAbsent(
                        artifact.getId(), question.getId(), MAX_ATTEMPTS);
            }
        }
        return new PreparationSummary(eligible, reused, queued, failed);
    }

    private void markReadinessFailure(QuestionExplanationArtifact artifact, String readinessIssue) {
        if (!QuestionExplanationArtifact.STATUS_PENDING.equals(artifact.getStatus())) {
            return;
        }
        QuestionExplanationArtifact locked = artifactRepository.findByIdForUpdate(artifact.getId())
                .orElseThrow();
        if (QuestionExplanationArtifact.STATUS_PENDING.equals(locked.getStatus())) {
            locked.markFailed(
                    readinessIssue,
                    readinessMessage(readinessIssue),
                    LocalDateTime.now());
            artifactRepository.save(locked);
        }
    }

    private static boolean isObjectiveSkill(String skill) {
        return "READING".equalsIgnoreCase(skill) || "LISTENING".equalsIgnoreCase(skill);
    }

    private static String readinessMessage(String issue) {
        return switch (issue) {
            case ExplanationInputFactory.ISSUE_EVIDENCE_UNAVAILABLE ->
                    "Published question has no approved text or image evidence.";
            case ExplanationInputFactory.ISSUE_MEDIA_DIGEST_UNAVAILABLE ->
                    "Published question references media without a verified immutable digest.";
            default -> "Published explanation input is not ready.";
        };
    }

    public record PreparationSummary(int eligible, int reused, int queued, int failed) {
    }
}
