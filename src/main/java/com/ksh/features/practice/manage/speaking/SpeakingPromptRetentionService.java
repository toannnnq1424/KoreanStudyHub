package com.ksh.features.practice.manage.speaking;

import com.ksh.features.practice.manage.service.LecturerAssetService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

@Service
public class SpeakingPromptRetentionService {

    private static final Set<String> ACTIVE_TASK_STATUSES = Set.of(
            SpeakingPromptAiTask.STATUS_QUEUED,
            SpeakingPromptAiTask.STATUS_PROCESSING,
            SpeakingPromptAiTask.STATUS_RETRY_WAIT);

    private final SpeakingPromptAiArtifactRepository artifactRepository;
    private final SpeakingPromptAiTaskRepository taskRepository;
    private final SpeakingPromptTranscriptRevisionRepository revisionRepository;
    private final SpeakingPromptSourceRepository sourceRepository;
    private final SpeakingPromptVersionContextRepository versionContextRepository;
    private final LecturerAssetService lecturerAssetService;
    private final Duration orphanRetention;

    public SpeakingPromptRetentionService(
            SpeakingPromptAiArtifactRepository artifactRepository,
            SpeakingPromptAiTaskRepository taskRepository,
            SpeakingPromptTranscriptRevisionRepository revisionRepository,
            SpeakingPromptSourceRepository sourceRepository,
            SpeakingPromptVersionContextRepository versionContextRepository,
            LecturerAssetService lecturerAssetService,
            @Value("${app.practice.speaking-prompt-authoring.orphan-retention:P30D}")
                    Duration orphanRetention) {
        this.artifactRepository = artifactRepository;
        this.taskRepository = taskRepository;
        this.revisionRepository = revisionRepository;
        this.sourceRepository = sourceRepository;
        this.versionContextRepository = versionContextRepository;
        this.lecturerAssetService = lecturerAssetService;
        this.orphanRetention = orphanRetention;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int reconcileExpired(int requestedLimit) {
        int processed = 0;
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        LocalDateTime cutoff = LocalDateTime.now().minus(orphanRetention);
        for (Long artifactId : artifactRepository.findExpiredUnretainedIds(
                cutoff, PageRequest.of(0, limit))) {
            SpeakingPromptAiArtifact artifact = artifactRepository
                    .findByIdForUpdate(artifactId)
                    .orElse(null);
            if (artifact == null
                    || !sourceRepository
                        .findByCurrentSttArtifactIdOrderByDraftIdAscIdAsc(
                                artifactId).isEmpty()
                    || !sourceRepository
                        .findByCurrentTtsArtifactIdOrderByDraftIdAscIdAsc(
                                artifactId).isEmpty()
                    || versionContextRepository
                        .existsBySttArtifactIdOrTtsArtifactId(
                                artifactId, artifactId)
                    || taskRepository.existsByArtifactIdAndTaskStatusIn(
                                artifactId, ACTIVE_TASK_STATUSES)) {
                continue;
            }
            Long inputAssetId = artifact.getInputAudioAssetId();
            Long generatedAssetId = artifact.getGeneratedAudioAssetId();
            taskRepository.deleteByArtifactId(artifactId);
            revisionRepository.deleteByArtifactId(artifactId);
            artifactRepository.delete(artifact);
            artifactRepository.flush();
            lecturerAssetService.queuePrivatePromptAssetIfUnreferenced(
                    inputAssetId);
            lecturerAssetService.queuePrivatePromptAssetIfUnreferenced(
                    generatedAssetId);
            processed++;
        }
        return processed;
    }
}
