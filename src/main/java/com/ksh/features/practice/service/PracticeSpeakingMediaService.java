package com.ksh.features.practice.service;

import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.PracticeQuestionGroup;
import com.ksh.entities.PracticeSection;
import com.ksh.entities.PracticeSpeakingMedia;
import com.ksh.entities.PracticeSpeakingMediaStatus;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticeQuestionGroupRepository;
import com.ksh.features.practice.repository.PracticeQuestionRepository;
import com.ksh.features.practice.repository.PracticeSectionRepository;
import com.ksh.features.practice.repository.PracticeSpeakingMediaRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PracticeSpeakingMediaService {

    private final PracticeAttemptRepository attemptRepository;
    private final PracticeQuestionRepository questionRepository;
    private final PracticeSectionRepository sectionRepository;
    private final PracticeQuestionGroupRepository groupRepository;
    private final PracticeSpeakingMediaRepository mediaRepository;

    public PracticeSpeakingMediaService(PracticeAttemptRepository attemptRepository,
                                        PracticeQuestionRepository questionRepository,
                                        PracticeSectionRepository sectionRepository,
                                        PracticeQuestionGroupRepository groupRepository,
                                        PracticeSpeakingMediaRepository mediaRepository) {
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.sectionRepository = sectionRepository;
        this.groupRepository = groupRepository;
        this.mediaRepository = mediaRepository;
    }

    @Transactional
    public SpeakingMediaIdentity activateValidatedMediaForOwner(
            Long userId,
            Long attemptId,
            Long questionId,
            ValidatedSpeakingMediaDescriptor descriptor
    ) {
        PracticeAttempt attempt = loadOwnedAttemptForUpdate(attemptId, userId);
        validateMutableAttempt(attempt);
        validateQuestionScope(attempt, questionId);
        if (mediaRepository.existsByStorageProviderAndStorageKey(descriptor.storageProvider(), descriptor.storageKey())) {
            throw new IllegalStateException("Speaking media storage identity already exists.");
        }

        List<PracticeSpeakingMedia> readyRows = readyRows(attemptId, questionId);
        if (readyRows.size() > 1) {
            throw new IllegalStateException("Multiple READY speaking media rows detected.");
        }
        readyRows.forEach(PracticeSpeakingMedia::markSuperseded);

        PracticeSpeakingMedia media = PracticeSpeakingMedia.ready(
                attemptId,
                questionId,
                descriptor.storageProvider(),
                descriptor.storageKey(),
                descriptor.mimeType(),
                descriptor.container(),
                descriptor.codec(),
                descriptor.byteSize(),
                descriptor.durationMs(),
                descriptor.contentHash());
        PracticeSpeakingMedia saved = mediaRepository.saveAndFlush(media);
        return identity(saved);
    }

    @Transactional(readOnly = true)
    public Optional<SpeakingMediaIdentity> findReadyMediaForOwner(Long userId, Long attemptId, Long questionId) {
        PracticeAttempt attempt = loadOwnedAttempt(attemptId, userId);
        validateQuestionScope(attempt, questionId);
        List<PracticeSpeakingMedia> readyRows = readyRows(attemptId, questionId);
        if (readyRows.size() > 1) {
            throw new IllegalStateException("Multiple READY speaking media rows detected.");
        }
        return readyRows.stream().findFirst().map(this::identity);
    }

    @Transactional
    public void markDeletedForOwner(Long userId, Long attemptId, Long questionId) {
        PracticeAttempt attempt = loadOwnedAttemptForUpdate(attemptId, userId);
        validateMutableAttempt(attempt);
        validateQuestionScope(attempt, questionId);
        List<PracticeSpeakingMedia> rows = mediaRepository.findByAttemptIdAndQuestionIdAndStatus(
                attemptId, questionId, PracticeSpeakingMediaStatus.READY);
        rows.addAll(mediaRepository.findByAttemptIdAndQuestionIdAndStatus(
                attemptId, questionId, PracticeSpeakingMediaStatus.SUPERSEDED));
        rows.forEach(PracticeSpeakingMedia::markDeleted);
        mediaRepository.flush();
    }

    private PracticeAttempt loadOwnedAttempt(Long attemptId, Long userId) {
        return attemptRepository.findById(attemptId)
                .map(attempt -> {
                    if (!userId.equals(attempt.getUserId())) {
                        throw new AccessDeniedException("Attempt is not accessible.");
                    }
                    return attempt;
                })
                .orElseThrow(() -> new EntityNotFoundException("Attempt not found."));
    }

    private PracticeAttempt loadOwnedAttemptForUpdate(Long attemptId, Long userId) {
        return attemptRepository.findByIdAndUserIdForUpdate(attemptId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Attempt not found."));
    }

    private void validateMutableAttempt(PracticeAttempt attempt) {
        if (!"SPEAKING".equals(attempt.getSkill())) {
            throw new IllegalStateException("Only SPEAKING attempts can store learner speaking media.");
        }
        if (!PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw new IllegalStateException("Speaking media can only be changed before submit.");
        }
    }

    private void validateQuestionScope(PracticeAttempt attempt, Long questionId) {
        if (!"SPEAKING".equals(attempt.getSkill())) {
            throw new IllegalStateException("Only SPEAKING attempts can access learner speaking media.");
        }
        PracticeSection section = sectionRepository.findById(attempt.getSectionId())
                .orElseThrow(() -> new EntityNotFoundException("Section not found."));
        if (!attempt.getSetId().equals(section.getSetId()) || !attempt.getTestId().equals(section.getTestId())) {
            throw new IllegalStateException("Attempt section scope is inconsistent.");
        }
        PracticeQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found."));
        if (!attempt.getSetId().equals(question.getSetId())) {
            throw new IllegalStateException("Question does not belong to the attempt set.");
        }
        if (!PracticeQuestion.TYPE_SPEAKING.equals(question.getQuestionType())) {
            throw new IllegalStateException("Only SPEAKING questions can have learner audio.");
        }
        if (question.getGroupId() == null) {
            throw new IllegalStateException("Question is not scoped to the attempt section.");
        }
        PracticeQuestionGroup group = groupRepository.findById(question.getGroupId())
                .orElseThrow(() -> new EntityNotFoundException("Question group not found."));
        if (!attempt.getSectionId().equals(group.getSectionId())) {
            throw new IllegalStateException("Question does not belong to the attempt section.");
        }
    }

    private List<PracticeSpeakingMedia> readyRows(Long attemptId, Long questionId) {
        return mediaRepository.findByAttemptIdAndQuestionIdAndStatus(
                attemptId, questionId, PracticeSpeakingMediaStatus.READY);
    }

    private SpeakingMediaIdentity identity(PracticeSpeakingMedia media) {
        return new SpeakingMediaIdentity(
                media.getId(),
                media.getLockVersion(),
                media.getContentHash(),
                media.getByteSize(),
                media.getAttemptId(),
                media.getQuestionId());
    }
}
