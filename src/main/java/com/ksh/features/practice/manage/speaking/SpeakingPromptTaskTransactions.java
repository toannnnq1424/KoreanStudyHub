package com.ksh.features.practice.manage.speaking;

import com.ksh.entities.LecturerAsset;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.practice.manage.speaking.SpeakingPromptAssetService.StoredGeneratedCandidate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Short transaction boundary for durable Speaking prompt work.
 *
 * <p>The full order is: optional owner sentinel (claim/count only), draft(s),
 * source(s), reusable artifact, durable task. The existing owner row is the
 * cross-node quota/concurrency sentinel. Authoring/transcript paths never
 * acquire it after a domain lock. Provider/media IO is
 * deliberately outside this class. A task is the single owner/fingerprint
 * charge, while source rows are independent attachments to its reusable
 * artifact outcome. Task/source processing, failure, cancellation and retry
 * never mutate the shared artifact; only a verified READY provider outcome
 * does.</p>
 */
@Service
public class SpeakingPromptTaskTransactions {

    private static final BigDecimal LOW_CONFIDENCE =
            new BigDecimal("0.50");

    private final SpeakingPromptAiTaskRepository taskRepository;
    private final UserRepository userRepository;
    private final SpeakingPromptSourceRepository sourceRepository;
    private final SpeakingPromptAiArtifactRepository artifactRepository;
    private final SpeakingPromptTranscriptRevisionRepository revisionRepository;
    private final SpeakingPromptAssetService assetService;
    private final SpeakingPromptAuthoringAiProperties properties;
    private final SpeakingPromptFingerprintService fingerprintService;
    private final SpeakingPromptDraftAuthority draftAuthority;

    public SpeakingPromptTaskTransactions(
            SpeakingPromptAiTaskRepository taskRepository,
            UserRepository userRepository,
            SpeakingPromptSourceRepository sourceRepository,
            SpeakingPromptAiArtifactRepository artifactRepository,
            SpeakingPromptTranscriptRevisionRepository revisionRepository,
            SpeakingPromptAssetService assetService,
            SpeakingPromptAuthoringAiProperties properties,
            SpeakingPromptFingerprintService fingerprintService,
            SpeakingPromptDraftAuthority draftAuthority) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.sourceRepository = sourceRepository;
        this.artifactRepository = artifactRepository;
        this.revisionRepository = revisionRepository;
        this.assetService = assetService;
        this.properties = properties;
        this.fingerprintService = fingerprintService;
        this.draftAuthority = draftAuthority;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedTask> claim(
            Long taskId,
            String claimToken,
            LocalDateTime now) {
        SpeakingPromptAiTask ownerSnapshot = taskRepository.findById(taskId)
                .orElse(null);
        if (ownerSnapshot == null
                || userRepository.findByIdForUpdate(
                        ownerSnapshot.getOwnerLecturerId()).isEmpty()) {
            return Optional.empty();
        }
        Routing routing = lockRouting(taskId);
        if (!routing.present()) {
            return Optional.empty();
        }
        SpeakingPromptAiTask task = routing.task();
        SpeakingPromptAiArtifact artifact = routing.artifact();
        if (!task.canClaim(now)) {
            return Optional.empty();
        }
        if (task.hasExpiredProcessingLease(now)) {
            closeAttemptAndMaybeSchedule(
                    routing,
                    SpeakingPromptAiContract.PublicErrorCategory.TIMEOUT,
                    !task.attemptsExhausted(),
                    now);
            return Optional.empty();
        }
        if (routing.changed()) {
            task.defer(
                    SpeakingPromptAiContract.PublicErrorCategory.STALE_COMPLETION,
                    now.plus(properties.taskBounds().retryInitialDelay()));
            taskRepository.save(task);
            return Optional.empty();
        }
        if (routing.currentSources().isEmpty()) {
            task.markSuperseded(now);
            taskRepository.save(task);
            return Optional.empty();
        }
        if (artifact.isReady()) {
            attachReady(routing);
            task.markSucceeded(now);
            saveSources(routing.currentSources());
            taskRepository.save(task);
            return Optional.empty();
        }
        if (task.attemptsExhausted()
                && task.getPublicErrorCategory() != null) {
            SpeakingPromptAiContract.PublicErrorCategory category =
                    parseCategory(task.getPublicErrorCategory());
            reflectFailure(routing, false);
            task.markFailure(category, false, now);
            saveSources(routing.currentSources());
            taskRepository.save(task);
            return Optional.empty();
        }
        if (task.attemptsExhausted()) {
            closeAttemptAndMaybeSchedule(
                    routing,
                    SpeakingPromptAiContract.PublicErrorCategory.TIMEOUT,
                    false,
                    now);
            return Optional.empty();
        }

        SpeakingPromptAuthoringAiProperties.TaskBounds bounds =
                properties.taskBounds();
        SpeakingPromptSource executionSource = routing.currentSources().get(0);
        if (taskRepository.countProcessingByOwnerExcluding(
                    task.getOwnerLecturerId(), task.getId())
                    >= bounds.maxActiveTasksPerLecturer()
                || taskRepository.countProcessingByDraftExcluding(
                    executionSource.getDraftId(), task.getId())
                    >= bounds.maxActiveTasksPerDraft()
                || taskRepository.countProviderAttemptsSince(
                    task.getOwnerLecturerId(), now.minusHours(1))
                    >= bounds.maxRequestsPerLecturerPerHour()) {
            task.defer(
                    SpeakingPromptAiContract.PublicErrorCategory.RATE_LIMIT,
                    now.plus(bounds.retryInitialDelay()));
            taskRepository.save(task);
            return Optional.empty();
        }

        task.claim(claimToken, now, now.plus(bounds.leaseDuration()));
        for (SpeakingPromptSource source : routing.currentSources()) {
            if (routing.operation() == SpeakingPromptAiContract.Operation.STT) {
                source.markSttProcessing();
            } else {
                source.markTtsProcessing();
            }
        }
        taskRepository.save(task);
        saveSources(routing.currentSources());
        return Optional.of(new ClaimedTask(
                task.getId(),
                artifact.getId(),
                executionSource.getId(),
                task.getOwnerLecturerId(),
                executionSource.getDraftId(),
                executionSource.getQuestionClientId(),
                routing.operation(),
                artifact.getOperationFingerprint(),
                executionSource.getSourceRevision(),
                claimToken));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeStt(
            ClaimedTask claim,
            SpeakingPromptAiContract.SttResult result,
            String loadedAudioSha256,
            LocalDateTime now) {
        Routing routing = lockCompletion(claim, now);
        if (!completionCurrent(routing, claim)
                || claim.operation() != SpeakingPromptAiContract.Operation.STT
                || result == null
                || !Objects.equals(
                        routing.artifact().getInputSha256(), loadedAudioSha256)
                || !providerIdentityMatches(routing.artifact(), result)) {
            rejectLateCompletion(routing, now);
            return false;
        }
        SpeakingPromptAiArtifact artifact = routing.artifact();
        String contextHash = fingerprintService.exactTextSha256(
                result.providerTranscript());
        boolean needsReview = result.confidence() != null
                && result.confidence().compareTo(LOW_CONFIDENCE) < 0;
        artifact.markSttReady(result, contextHash, needsReview, now);
        int revisionNumber =
                revisionRepository.findMaximumRevisionNumber(artifact.getId()) + 1;
        SpeakingPromptTranscriptRevision revision = revisionRepository.saveAndFlush(
                SpeakingPromptTranscriptRevision.provider(
                        artifact,
                        revisionNumber,
                        result.providerTranscript(),
                        contextHash,
                        needsReview ? null : now));
        for (SpeakingPromptSource source : routing.currentSources()) {
            source.attachSttArtifact(
                    artifact.getId(), revision.getId(), needsReview);
        }
        completeOutcome(routing, now);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeTts(
            ClaimedTask claim,
            SpeakingPromptAiContract.TtsResult result,
            StoredGeneratedCandidate candidate,
            String loadedExactManualTextSha256,
            LocalDateTime now) {
        Routing routing = lockCompletion(claim, now);
        SpeakingPromptSource executionSource =
                routing.sourceById(claim.executionSourceId());
        if (!completionCurrent(routing, claim)
                || claim.operation() != SpeakingPromptAiContract.Operation.TTS
                || executionSource == null
                || !Objects.equals(
                        executionSource.getManualTextSha256(),
                        loadedExactManualTextSha256)
                || result == null
                || !providerIdentityMatches(routing.artifact(), result)
                || candidate == null
                || !Objects.equals(candidate.ownerId(), claim.ownerId())
                || !Objects.equals(candidate.draftId(), claim.draftId())
                || !Objects.equals(
                        candidate.questionClientId(),
                        claim.questionClientId())) {
            rejectLateCompletion(routing, now);
            return false;
        }
        LecturerAsset generated = assetService.registerGeneratedCandidate(candidate);
        SpeakingPromptAiArtifact artifact = routing.artifact();
        artifact.markTtsReady(result, generated.getId(), now);
        for (SpeakingPromptSource source : routing.currentSources()) {
            assetService.linkExistingGeneratedAsset(
                    source.getDraftId(),
                    source.getOwnerLecturerId(),
                    source.getQuestionClientId(),
                    generated.getId());
            source.attachTtsArtifact(artifact.getId(), generated.getId());
        }
        completeOutcome(routing, now);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fail(
            ClaimedTask claim,
            SpeakingPromptAiContract.PublicErrorCategory category,
            boolean retryable,
            LocalDateTime now) {
        Routing routing = lockCompletion(claim, now);
        if (!routing.present()
                || !routing.task().ownsLiveLease(claim.claimToken(), now)) {
            return false;
        }
        if (category
                == SpeakingPromptAiContract.PublicErrorCategory.STALE_COMPLETION) {
            routing.task().markAbandonedBeforeProvider(now);
            taskRepository.saveAndFlush(routing.task());
            if (!routing.currentSources().isEmpty()) {
                insertSuccessor(
                        routing,
                        Math.max(0, routing.task().getAttemptCount() - 1),
                        now.plus(properties.taskBounds().retryInitialDelay()));
            }
            return false;
        }
        closeAttemptAndMaybeSchedule(routing, category, retryable, now);
        return true;
    }

    private Routing lockCompletion(ClaimedTask claim, LocalDateTime now) {
        Routing routing = lockRouting(claim.taskId());
        if (!routing.present()
                || !routing.task().ownsLiveLease(claim.claimToken(), now)
                || !Objects.equals(
                        claim.operationFingerprint(),
                        routing.artifact().getOperationFingerprint())) {
            return Routing.missing();
        }
        return routing;
    }

    private boolean completionCurrent(Routing routing, ClaimedTask claim) {
        if (!routing.present() || routing.currentSources().isEmpty()) {
            return false;
        }
        SpeakingPromptSource execution =
                routing.sourceById(claim.executionSourceId());
        return execution != null
                && Objects.equals(
                        execution.getSourceRevision(),
                        claim.executionSourceRevision())
                && Objects.equals(execution.getOwnerLecturerId(), claim.ownerId())
                && execution.currentForArtifact(routing.artifact());
    }

    private Routing lockRouting(Long taskId) {
        SpeakingPromptAiTask taskSnapshot = taskRepository.findById(taskId)
                .orElse(null);
        if (taskSnapshot == null) {
            return Routing.missing();
        }
        SpeakingPromptSource taskSourceSnapshot =
                taskSnapshot.getSourceId() == null
                        ? null
                        : sourceRepository.findById(
                                taskSnapshot.getSourceId()).orElse(null);
        SpeakingPromptAiContract.Operation operation =
                operation(taskSnapshot.getOperation());
        List<SpeakingPromptSource> candidateSnapshots =
                findAttachedSources(operation, taskSnapshot.getArtifactId());
        Set<Long> initialCandidateIds = ids(candidateSnapshots);

        LinkedHashMap<Long, SpeakingPromptSource> sourceSnapshots =
                new LinkedHashMap<>();
        if (taskSourceSnapshot != null) {
            sourceSnapshots.put(taskSourceSnapshot.getId(), taskSourceSnapshot);
        }
        for (SpeakingPromptSource source : candidateSnapshots) {
            sourceSnapshots.put(source.getId(), source);
        }
        List<SpeakingPromptSource> orderedSnapshots =
                new ArrayList<>(sourceSnapshots.values());
        orderedSnapshots.sort(Comparator
                .comparing(SpeakingPromptSource::getDraftId)
                .thenComparing(SpeakingPromptSource::getId));

        Map<Long, SpeakingPromptDraftAuthority.LockedDraft> lockedDrafts =
                new LinkedHashMap<>();
        for (SpeakingPromptSource source : orderedSnapshots) {
            lockedDrafts.computeIfAbsent(
                    source.getDraftId(),
                    ignored -> draftAuthority.lockDraft(
                            source.getDraftId(), taskSnapshot.getOwnerLecturerId()));
        }
        List<SpeakingPromptSource> lockedSources =
                orderedSnapshots.isEmpty()
                        ? List.of()
                        : sourceRepository.findByIdsForUpdate(
                                orderedSnapshots.stream()
                                        .map(SpeakingPromptSource::getId)
                                        .toList());
        SpeakingPromptAiArtifact artifact = artifactRepository
                .findByIdForUpdate(taskSnapshot.getArtifactId())
                .orElse(null);
        SpeakingPromptAiTask task = taskRepository.findByIdForUpdate(taskId)
                .orElse(null);
        if (artifact == null
                || task == null
                || !taskIdentityMatches(taskSnapshot, task, artifact)) {
            return Routing.missing();
        }

        Set<Long> afterLockCandidateIds = ids(
                findAttachedSources(operation, artifact.getId()));
        boolean changed = !initialCandidateIds.equals(afterLockCandidateIds);
        List<SpeakingPromptSource> current = lockedSources.stream()
                .filter(source -> initialCandidateIds.contains(source.getId()))
                .filter(source -> sourceCurrent(
                        source, artifact, lockedDrafts.get(source.getDraftId())))
                .sorted(Comparator.comparing(SpeakingPromptSource::getId))
                .toList();
        return new Routing(
                task,
                artifact,
                operation,
                current,
                changed,
                true);
    }

    private boolean sourceCurrent(
            SpeakingPromptSource source,
            SpeakingPromptAiArtifact artifact,
            SpeakingPromptDraftAuthority.LockedDraft lockedDraft) {
        if (!source.currentForArtifact(artifact)) {
            return false;
        }
        try {
            SpeakingPromptDraftAuthority.DraftPrompt prompt =
                    draftAuthority.locateInLockedDraft(
                            lockedDraft, source.getQuestionClientId());
            if (SpeakingPromptAiContract.Operation.STT.code().equals(
                    artifact.getOperation())) {
                return true;
            }
            return Objects.equals(
                        source.getManualTextSha256(),
                        fingerprintService.exactTextSha256(prompt.promptText()))
                    && Objects.equals(
                        artifact.getInputSha256(),
                        SpeakingPromptAiContract.unicodeNfcUtf8Sha256(
                                prompt.promptText()));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void completeOutcome(Routing routing, LocalDateTime now) {
        artifactRepository.save(routing.artifact());
        saveSources(routing.currentSources());
        routing.task().markSucceeded(now);
        taskRepository.saveAndFlush(routing.task());
        if (routing.changed()) {
            insertSuccessor(
                    routing,
                    routing.task().getAttemptCount(),
                    now);
        }
    }

    private void rejectLateCompletion(Routing routing, LocalDateTime now) {
        if (!routing.present()) {
            return;
        }
        /*
         * A stale source/task never changes the owner-scoped reusable artifact.
         * The task is closed solely as stale evidence.
         */
        routing.task().markSuperseded(now);
        taskRepository.saveAndFlush(routing.task());
        if (routing.currentSources().isEmpty()) {
            return;
        }
        if (routing.task().attemptsExhausted()) {
            for (SpeakingPromptSource source : routing.currentSources()) {
                source.markOperationFailure(routing.operation(), false);
            }
            saveSources(routing.currentSources());
            return;
        }
        insertSuccessor(
                routing,
                routing.task().getAttemptCount(),
                now.plus(retryDelay(routing.task().getAttemptCount())));
    }

    private void closeAttemptAndMaybeSchedule(
            Routing routing,
            SpeakingPromptAiContract.PublicErrorCategory category,
            boolean retryable,
            LocalDateTime now) {
        boolean successorAllowed =
                retryable && !routing.task().attemptsExhausted();
        routing.task().markFailure(category, successorAllowed, now);
        for (SpeakingPromptSource source : routing.currentSources()) {
            source.markOperationFailure(routing.operation(), successorAllowed);
        }
        taskRepository.saveAndFlush(routing.task());
        saveSources(routing.currentSources());
        if (successorAllowed) {
            insertSuccessor(
                    routing,
                    routing.task().getAttemptCount(),
                    now.plus(retryDelay(routing.task().getAttemptCount())));
        } else if (routing.changed()) {
            insertOutcomeReconciler(routing, category, now);
        }
    }

    private void insertSuccessor(
            Routing routing,
            int attemptCount,
            LocalDateTime nextAttemptAt) {
        SpeakingPromptAiTask task = routing.task();
        SpeakingPromptSource successorSource =
                routing.currentSources().stream().findFirst().orElse(null);
        if (successorSource == null) {
            return;
        }
        int inserted = taskRepository.insertRetrySuccessor(
                task.getArtifactId(),
                successorSource.getId(),
                task.getOwnerLecturerId(),
                task.getOperation(),
                successorSource.getInputType(),
                task.getOperationFingerprint(),
                successorSource.getSourceRevision(),
                attemptCount,
                task.getMaxAttempts(),
                nextAttemptAt,
                task.getRequestedBy());
        if (inserted != 1 && !activeTaskExists(task)) {
            throw new IllegalStateException(
                    "Speaking prompt successor task was not inserted.");
        }
    }

    private void insertOutcomeReconciler(
            Routing routing,
            SpeakingPromptAiContract.PublicErrorCategory category,
            LocalDateTime nextAttemptAt) {
        SpeakingPromptAiTask task = routing.task();
        SpeakingPromptSource successorSource =
                routing.currentSources().stream().findFirst().orElse(null);
        if (successorSource == null) {
            return;
        }
        int inserted = taskRepository.insertOutcomeReconciler(
                task.getArtifactId(),
                successorSource.getId(),
                task.getOwnerLecturerId(),
                task.getOperation(),
                successorSource.getInputType(),
                task.getOperationFingerprint(),
                successorSource.getSourceRevision(),
                task.getMaxAttempts(),
                nextAttemptAt,
                category.name(),
                task.getRequestedBy());
        if (inserted != 1 && !activeTaskExists(task)) {
            throw new IllegalStateException(
                    "Speaking prompt outcome reconciler was not inserted.");
        }
    }

    private boolean activeTaskExists(SpeakingPromptAiTask task) {
        return taskRepository.findActiveByFingerprint(
                task.getOwnerLecturerId(),
                task.getOperation(),
                task.getOperationFingerprint()).isPresent();
    }

    private void attachReady(Routing routing) {
        SpeakingPromptAiArtifact artifact = routing.artifact();
        if (routing.operation() == SpeakingPromptAiContract.Operation.STT) {
            SpeakingPromptTranscriptRevision revision = revisionRepository
                    .findFirstByArtifactIdAndRevisionSourceOrderByRevisionNumberDesc(
                            artifact.getId(),
                            SpeakingPromptTranscriptRevision.SOURCE_PROVIDER)
                    .orElseThrow(() -> new IllegalStateException(
                            "Ready STT artifact has no provider revision."));
            boolean needsReview = SpeakingPromptSource.STATUS_NEEDS_REVIEW.equals(
                    artifact.getArtifactStatus());
            for (SpeakingPromptSource source : routing.currentSources()) {
                source.attachSttArtifact(
                        artifact.getId(), revision.getId(), needsReview);
            }
            return;
        }
        Long assetId = artifact.getGeneratedAudioAssetId();
        if (assetId == null) {
            throw new IllegalStateException(
                    "Ready TTS artifact has no generated lecturer asset.");
        }
        for (SpeakingPromptSource source : routing.currentSources()) {
            assetService.linkExistingGeneratedAsset(
                    source.getDraftId(),
                    source.getOwnerLecturerId(),
                    source.getQuestionClientId(),
                    assetId);
            source.attachTtsArtifact(artifact.getId(), assetId);
        }
    }

    private void reflectFailure(Routing routing, boolean retryable) {
        for (SpeakingPromptSource source : routing.currentSources()) {
            source.markOperationFailure(routing.operation(), retryable);
        }
    }

    private List<SpeakingPromptSource> findAttachedSources(
            SpeakingPromptAiContract.Operation operation,
            Long artifactId) {
        return operation == SpeakingPromptAiContract.Operation.STT
                ? sourceRepository
                        .findByCurrentSttArtifactIdOrderByDraftIdAscIdAsc(artifactId)
                : sourceRepository
                        .findByCurrentTtsArtifactIdOrderByDraftIdAscIdAsc(artifactId);
    }

    private static Set<Long> ids(List<SpeakingPromptSource> sources) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (SpeakingPromptSource source : sources) {
            result.add(source.getId());
        }
        return result;
    }

    private static boolean taskIdentityMatches(
            SpeakingPromptAiTask snapshot,
            SpeakingPromptAiTask locked,
            SpeakingPromptAiArtifact artifact) {
        return Objects.equals(snapshot.getId(), locked.getId())
                && Objects.equals(snapshot.getArtifactId(), locked.getArtifactId())
                && Objects.equals(snapshot.getSourceId(), locked.getSourceId())
                && Objects.equals(
                        locked.getArtifactId(), artifact.getId())
                && Objects.equals(
                        locked.getOwnerLecturerId(),
                        artifact.getOwnerLecturerId())
                && Objects.equals(locked.getOperation(), artifact.getOperation())
                && Objects.equals(
                        locked.getOperationFingerprint(),
                        artifact.getOperationFingerprint());
    }

    private static SpeakingPromptAiContract.Operation operation(String code) {
        if (SpeakingPromptAiContract.Operation.STT.code().equals(code)) {
            return SpeakingPromptAiContract.Operation.STT;
        }
        if (SpeakingPromptAiContract.Operation.TTS.code().equals(code)) {
            return SpeakingPromptAiContract.Operation.TTS;
        }
        throw new IllegalStateException("Speaking prompt task operation is invalid.");
    }

    private void saveSources(List<SpeakingPromptSource> sources) {
        if (!sources.isEmpty()) {
            sourceRepository.saveAll(sources);
        }
    }

    private Duration retryDelay(Integer attemptCount) {
        SpeakingPromptAuthoringAiProperties.TaskBounds bounds =
                properties.taskBounds();
        int exponent = Math.max(0, Math.min(
                attemptCount == null ? 0 : attemptCount - 1, 20));
        long multiplier = 1L << exponent;
        Duration proposed;
        try {
            proposed = bounds.retryInitialDelay().multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            proposed = bounds.retryMaxDelay();
        }
        return proposed.compareTo(bounds.retryMaxDelay()) > 0
                ? bounds.retryMaxDelay()
                : proposed;
    }

    private static boolean providerIdentityMatches(
            SpeakingPromptAiArtifact artifact,
            SpeakingPromptAiContract.SttResult result) {
        return Objects.equals(artifact.getProviderCode(), result.providerCode())
                && Objects.equals(artifact.getModelCode(), result.modelCode())
                && Objects.equals(artifact.getLanguageTag(), result.languageTag())
                && Objects.equals(artifact.getPurposeCode(), result.purposeCode())
                && Objects.equals(
                        artifact.getRetentionCode(), result.retentionCode());
    }

    private static SpeakingPromptAiContract.PublicErrorCategory parseCategory(
            String value) {
        try {
            return SpeakingPromptAiContract.PublicErrorCategory.valueOf(value);
        } catch (RuntimeException exception) {
            return SpeakingPromptAiContract.PublicErrorCategory.TRANSPORT;
        }
    }

    private static boolean providerIdentityMatches(
            SpeakingPromptAiArtifact artifact,
            SpeakingPromptAiContract.TtsResult result) {
        return Objects.equals(artifact.getProviderCode(), result.providerCode())
                && Objects.equals(artifact.getModelCode(), result.modelCode())
                && Objects.equals(artifact.getLanguageTag(), result.languageTag())
                && Objects.equals(artifact.getVoiceCode(), result.voiceCode())
                && artifact.getSpeed().compareTo(result.speed()) == 0
                && Objects.equals(
                        artifact.getOutputFormat(), result.outputFormat())
                && Objects.equals(artifact.getPurposeCode(), result.purposeCode())
                && Objects.equals(
                        artifact.getRetentionCode(), result.retentionCode());
    }

    static final class ClaimedTask {
        private final Long taskId;
        private final Long artifactId;
        private final Long executionSourceId;
        private final Long ownerId;
        private final Long draftId;
        private final String questionClientId;
        private final SpeakingPromptAiContract.Operation operation;
        private final String operationFingerprint;
        private final Long executionSourceRevision;
        private final String claimToken;

        private ClaimedTask(
                Long taskId,
                Long artifactId,
                Long executionSourceId,
                Long ownerId,
                Long draftId,
                String questionClientId,
                SpeakingPromptAiContract.Operation operation,
                String operationFingerprint,
                Long executionSourceRevision,
                String claimToken) {
            this.taskId = taskId;
            this.artifactId = artifactId;
            this.executionSourceId = executionSourceId;
            this.ownerId = ownerId;
            this.draftId = draftId;
            this.questionClientId = questionClientId;
            this.operation = operation;
            this.operationFingerprint = operationFingerprint;
            this.executionSourceRevision = executionSourceRevision;
            this.claimToken = claimToken;
        }

        Long taskId() { return taskId; }
        Long artifactId() { return artifactId; }
        Long executionSourceId() { return executionSourceId; }
        Long ownerId() { return ownerId; }
        Long draftId() { return draftId; }
        String questionClientId() { return questionClientId; }
        SpeakingPromptAiContract.Operation operation() { return operation; }
        String operationFingerprint() { return operationFingerprint; }
        Long executionSourceRevision() { return executionSourceRevision; }
        String claimToken() { return claimToken; }

        @Override
        public String toString() {
            return "ClaimedTask{taskId=" + taskId
                    + ", operation=" + operation + '}';
        }
    }

    private record Routing(
            SpeakingPromptAiTask task,
            SpeakingPromptAiArtifact artifact,
            SpeakingPromptAiContract.Operation operation,
            List<SpeakingPromptSource> currentSources,
            boolean changed,
            boolean present) {

        static Routing missing() {
            return new Routing(
                    null, null, null, List.of(), false, false);
        }

        SpeakingPromptSource sourceById(Long sourceId) {
            if (sourceId == null) {
                return null;
            }
            return currentSources.stream()
                    .filter(source -> Objects.equals(source.getId(), sourceId))
                    .findFirst()
                    .orElse(null);
        }
    }
}
