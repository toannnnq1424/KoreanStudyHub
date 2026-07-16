package com.ksh.features.practice.service;

import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.PracticeSpeakingMedia;
import com.ksh.entities.PracticeSpeakingMediaStatus;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticeSpeakingMediaRepository;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingMediaView;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PracticeSpeakingMediaService {

    private final PracticeAttemptRepository attemptRepository;
    private final PracticeSpeakingMediaRepository mediaRepository;
    private final PracticeSpeakingMediaCleanupTaskService cleanupTaskService;
    private final PracticePublishedVersionService publishedVersionService;

    public PracticeSpeakingMediaService(PracticeAttemptRepository attemptRepository,
                                        PracticeSpeakingMediaRepository mediaRepository,
                                        PracticeSpeakingMediaCleanupTaskService cleanupTaskService,
                                        PracticePublishedVersionService publishedVersionService) {
        this.attemptRepository = attemptRepository;
        this.mediaRepository = mediaRepository;
        this.cleanupTaskService = cleanupTaskService;
        this.publishedVersionService = publishedVersionService;
    }

    @Transactional
    public SpeakingMediaActivationResult activateValidatedMediaForOwner(
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
        Optional<Long> supersededCleanupTaskId = readyRows.stream()
                .findFirst()
                .map(this::enqueueSupersededCleanup);
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
        return activationResult(saved, supersededCleanupTaskId);
    }

    @Transactional(readOnly = true)
    public void validateUploadTargetForOwner(Long userId, Long attemptId, Long questionId) {
        PracticeAttempt attempt = loadOwnedAttempt(attemptId, userId);
        validateMutableAttempt(attempt);
        validateQuestionScope(attempt, questionId);
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

    @Transactional(readOnly = true)
    public List<SpeakingMediaView> findReadyMediaViewsForOwner(Long userId, Long attemptId) {
        PracticeAttempt attempt = loadOwnedAttempt(attemptId, userId);
        if (!"SPEAKING".equals(attempt.getSkill())) {
            return List.of();
        }
        Set<Long> allowedQuestionIds = immutableSpeakingQuestionIds(attempt);
        return mediaRepository.findByAttemptIdAndStatus(attemptId, PracticeSpeakingMediaStatus.READY).stream()
                .filter(media -> allowedQuestionIds.contains(media.getQuestionId()))
                .map(this::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, SpeakingMediaIdentity> requireReadyMediaForOwner(
            Long userId, Long attemptId, List<Long> questionIds) {
        PracticeAttempt attempt = loadOwnedAttempt(attemptId, userId);
        validateMutableAttempt(attempt);
        Set<Long> allowedQuestionIds = immutableSpeakingQuestionIds(attempt);
        if (questionIds == null || questionIds.isEmpty()
                || questionIds.stream().anyMatch(id -> id == null || !allowedQuestionIds.contains(id))) {
            throw new IllegalStateException("Speaking submission contains an invalid immutable question scope.");
        }

        Map<Long, SpeakingMediaIdentity> readyByQuestion = new LinkedHashMap<>();
        for (PracticeSpeakingMedia media : mediaRepository.findByAttemptIdAndStatus(
                attemptId, PracticeSpeakingMediaStatus.READY)) {
            if (!questionIds.contains(media.getQuestionId())) {
                continue;
            }
            if (readyByQuestion.put(media.getQuestionId(), identity(media)) != null) {
                throw new IllegalStateException("Multiple READY speaking media rows detected.");
            }
        }
        List<Long> missing = questionIds.stream()
                .filter(questionId -> !readyByQuestion.containsKey(questionId))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Mỗi câu Speaking phải có bản ghi âm đã lưu trước khi nộp bài.");
        }
        return Map.copyOf(readyByQuestion);
    }

    @Transactional
    public SpeakingMediaDeletionResult markDeletedForOwner(
            Long userId, Long attemptId, Long questionId, Long mediaId) {
        PracticeAttempt attempt = loadOwnedAttemptForUpdate(attemptId, userId);
        validateMutableAttempt(attempt);
        validateQuestionScope(attempt, questionId);
        PracticeSpeakingMedia media = mediaRepository.findByIdAndAttemptIdAndQuestionId(mediaId, attemptId, questionId)
                .orElseThrow(this::uploadTargetNotFound);
        media.markDeleted();
        Long cleanupTaskId = cleanupTaskService.enqueueLogicalDelete(
                media.getStorageProvider(),
                media.getStorageKey());
        mediaRepository.flush();
        return new SpeakingMediaDeletionResult(
                media.getId(),
                PracticeSpeakingMediaStatus.DELETED,
                cleanupTaskId);
    }

    private PracticeAttempt loadOwnedAttempt(Long attemptId, Long userId) {
        return attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(this::uploadTargetNotFound);
    }

    private PracticeAttempt loadOwnedAttemptForUpdate(Long attemptId, Long userId) {
        return attemptRepository.findByIdAndUserIdForUpdate(attemptId, userId)
                .orElseThrow(this::uploadTargetNotFound);
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
        if (!immutableSpeakingQuestionIds(attempt).contains(questionId)) {
            throw uploadTargetNotFound();
        }
    }

    private Set<Long> immutableSpeakingQuestionIds(PracticeAttempt attempt) {
        PracticeVersionSnapshot snapshot = publishedVersionService.snapshot(
                        attempt.getPublishedVersionId(),
                        attempt.getSetVersionId(),
                        attempt.getTestVersionId(),
                        attempt.getSectionVersionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Speaking attempt is missing an immutable delivery version."));

        if (!attempt.getSetId().equals(snapshot.setVersion().getSetId())
                || !attempt.getTestId().equals(snapshot.testVersion().getTestId())
                || !attempt.getSectionId().equals(snapshot.sectionVersion().getSectionId())
                || !"SPEAKING".equals(snapshot.sectionVersion().getSkill())) {
            throw new IllegalStateException("Speaking attempt delivery version is inconsistent.");
        }

        return snapshot.questions().stream()
                .filter(question -> "SPEAKING".equals(question.getQuestionType()))
                .map(PracticeQuestionVersion::getQuestionId)
                .collect(Collectors.toUnmodifiableSet());
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

    private SpeakingMediaView view(PracticeSpeakingMedia media) {
        return new SpeakingMediaView(
                media.getId(),
                media.getQuestionId(),
                media.getStatus().name(),
                media.getByteSize(),
                media.getDurationMs(),
                media.getMimeType(),
                "/practice/attempts/" + media.getAttemptId()
                        + "/questions/" + media.getQuestionId()
                        + "/speaking-media/" + media.getId() + "/content",
                media.getLockVersion());
    }

    private SpeakingMediaActivationResult activationResult(
            PracticeSpeakingMedia media,
            Optional<Long> supersededCleanupTaskId) {
        return new SpeakingMediaActivationResult(
                media.getId(),
                media.getQuestionId(),
                media.getStatus(),
                media.getByteSize(),
                media.getDurationMs(),
                media.getMimeType(),
                media.getLockVersion(),
                supersededCleanupTaskId);
    }

    private Long enqueueSupersededCleanup(PracticeSpeakingMedia media) {
        return cleanupTaskService.enqueueSupersededRetention(
                media.getStorageProvider(),
                media.getStorageKey());
    }

    private EntityNotFoundException uploadTargetNotFound() {
        return new EntityNotFoundException("Speaking media target not found.");
    }
}
