package com.ksh.features.practice.manage.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.manage.service.PracticeMaterialReferenceService;
import com.ksh.features.practice.manage.service.PracticeAssessmentExcelService;
import com.ksh.features.practice.manage.service.LecturerAssetService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Exact draft/question teardown. The caller locks and authorizes the draft
 * first; this seam then locks its sources in repository order, cancels only
 * owner-local work with no remaining attachment, removes exact draft material
 * bindings and finally removes the mutable sources.
 */
@Service
public class SpeakingPromptLifecycleService {

    private static final java.util.regex.Pattern MANAGED_ASSET_REFERENCE =
            java.util.regex.Pattern.compile(
                    "^/practice/materials/(\\d+)/content$");

    private final SpeakingPromptSourceRepository sourceRepository;
    private final SpeakingPromptAiArtifactRepository artifactRepository;
    private final SpeakingPromptAiTaskRepository taskRepository;
    private final PracticeMaterialReferenceService materialReferenceService;
    private final LecturerAssetService lecturerAssetService;
    private final ObjectMapper objectMapper;

    public SpeakingPromptLifecycleService(
            SpeakingPromptSourceRepository sourceRepository,
            SpeakingPromptAiArtifactRepository artifactRepository,
            SpeakingPromptAiTaskRepository taskRepository,
            PracticeMaterialReferenceService materialReferenceService,
            LecturerAssetService lecturerAssetService,
            ObjectMapper objectMapper) {
        this.sourceRepository = sourceRepository;
        this.artifactRepository = artifactRepository;
        this.taskRepository = taskRepository;
        this.materialReferenceService = materialReferenceService;
        this.lecturerAssetService = lecturerAssetService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void reconcileDraftQuestions(
            Long draftId,
            Long ownerId,
            Long actorId,
            String normalizedDraftJson) {
        teardown(
                draftId,
                ownerId,
                actorId,
                speakingState(normalizedDraftJson));
    }

    @Transactional
    public void teardownDraft(Long draftId, Long ownerId, Long actorId) {
        teardown(
                draftId,
                ownerId,
                actorId,
                new DraftSpeakingState(Set.of(), Map.of()));
    }

    private void teardown(
            Long draftId,
            Long ownerId,
            Long actorId,
            DraftSpeakingState retained) {
        List<SpeakingPromptSource> locked =
                sourceRepository.findByDraftIdForUpdate(draftId);
        for (SpeakingPromptSource source : locked) {
            if (!Objects.equals(ownerId, source.getOwnerLecturerId())) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Nguồn Speaking không thuộc chủ sở hữu bản nháp.");
            }
        }
        Set<Long> cleanupCandidates = new LinkedHashSet<>();
        for (com.ksh.entities.PracticeMaterialReference reference
                : materialReferenceService.referencesForDraft(draftId)) {
            if (PracticeAssessmentExcelService.EXCEL_SPEAKING_STAGING.equals(
                        reference.getPlacement())
                    && !Objects.equals(
                            retained.stagingAssetIds().get(
                                    reference.getReferenceKey()),
                            reference.getAssetId())) {
                cleanupCandidates.add(reference.getAssetId());
                materialReferenceService.unlinkDraftReference(
                        draftId, reference.getId());
            }
        }
        List<SpeakingPromptSource> removed = locked.stream()
                .filter(source -> {
                    String clientId = source.getQuestionClientId();
                    if (!retained.clientIds().contains(clientId)) {
                        return true;
                    }
                    Long exactManagedAsset =
                            retained.stagingAssetIds().get(clientId);
                    return exactManagedAsset != null
                            && (!SpeakingPromptSource.INPUT_AUDIO_UPLOAD.equals(
                                        source.getInputType())
                                || !Objects.equals(
                                        exactManagedAsset,
                                        source.getOriginalAudioAssetId()));
                })
                .toList();
        if (removed.isEmpty()) {
            cleanupCandidates.forEach(
                    lecturerAssetService::queuePrivatePromptAssetIfUnreferenced);
            return;
        }
        Set<Long> removedIds = removed.stream()
                .map(SpeakingPromptSource::getId)
                .collect(java.util.stream.Collectors.toCollection(
                        LinkedHashSet::new));
        for (SpeakingPromptSource source : removed) {
            cancelIfSoleCurrent(
                    source.getOwnerLecturerId(),
                    source.getCurrentSttArtifactId(),
                    SpeakingPromptAiContract.Operation.STT,
                    removedIds);
            cancelIfSoleCurrent(
                    source.getOwnerLecturerId(),
                    source.getCurrentTtsArtifactId(),
                    SpeakingPromptAiContract.Operation.TTS,
                    removedIds);
            if (source.getOriginalAudioAssetId() != null) {
                cleanupCandidates.add(source.getOriginalAudioAssetId());
                materialReferenceService.unlinkDraft(
                        source.getDraftId(),
                        source.getOriginalAudioAssetId(),
                        SpeakingPromptAssetService.ORIGINAL_PLACEMENT,
                        source.getQuestionClientId());
            }
            if (source.getGeneratedAudioAssetId() != null) {
                cleanupCandidates.add(source.getGeneratedAudioAssetId());
                materialReferenceService.unlinkDraft(
                        source.getDraftId(),
                        source.getGeneratedAudioAssetId(),
                        SpeakingPromptAssetService.GENERATED_PLACEMENT,
                        source.getQuestionClientId());
            }
        }
        List<SpeakingPromptAiTask> sourceTasks =
                taskRepository.findBySourceIdsForUpdate(
                        removedIds.stream().toList());
        for (SpeakingPromptAiTask task : sourceTasks) {
            SpeakingPromptSource source = removed.stream()
                    .filter(candidate -> Objects.equals(
                            candidate.getId(), task.getSourceId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Locked task references an unexpected source."));
            task.detachDeletedSource(
                    source.getId(), source.getOwnerLecturerId());
        }
        if (!sourceTasks.isEmpty()) {
            taskRepository.saveAllAndFlush(sourceTasks);
        }
        sourceRepository.deleteAll(removed);
        sourceRepository.flush();
        cleanupCandidates.forEach(
                lecturerAssetService::queuePrivatePromptAssetIfUnreferenced);
    }

    private void cancelIfSoleCurrent(
            Long ownerId,
            Long artifactId,
            SpeakingPromptAiContract.Operation operation,
            Set<Long> removedSourceIds) {
        if (artifactId == null) {
            return;
        }
        SpeakingPromptAiArtifact artifact = artifactRepository
                .findByIdForUpdate(artifactId)
                .orElse(null);
        if (artifact == null
                || !Objects.equals(ownerId, artifact.getOwnerLecturerId())
                || !operation.code().equals(artifact.getOperation())) {
            return;
        }
        List<SpeakingPromptSource> attached =
                operation == SpeakingPromptAiContract.Operation.STT
                        ? sourceRepository
                            .findByCurrentSttArtifactIdOrderByDraftIdAscIdAsc(
                                    artifactId)
                        : sourceRepository
                            .findByCurrentTtsArtifactIdOrderByDraftIdAscIdAsc(
                                    artifactId);
        boolean retainedAttachment = attached.stream()
                .anyMatch(source -> !removedSourceIds.contains(source.getId()));
        if (retainedAttachment) {
            return;
        }
        SpeakingPromptAiTask active = taskRepository.findActiveByFingerprint(
                        ownerId,
                        operation.code(),
                        artifact.getOperationFingerprint())
                .orElse(null);
        if (active != null
                && Objects.equals(active.getArtifactId(), artifactId)) {
            active.markCancelled(LocalDateTime.now());
            taskRepository.saveAndFlush(active);
        }
    }

    private DraftSpeakingState speakingState(String json) {
        if (json == null || json.isBlank()) {
            return new DraftSpeakingState(Set.of(), Map.of());
        }
        try {
            Set<String> result = new HashSet<>();
            java.util.LinkedHashMap<String, Long> stagingAssets =
                    new java.util.LinkedHashMap<>();
            JsonNode root = objectMapper.readTree(json);
            for (JsonNode section : root.path("sections")) {
                for (JsonNode group : section.path("groups")) {
                    for (JsonNode question : group.path("questions")) {
                        if (!"SPEAKING".equalsIgnoreCase(
                                question.path("questionType").asText(""))) {
                            continue;
                        }
                        String clientId =
                                question.path("clientId").asText("").trim();
                        if (!clientId.isBlank()) {
                            result.add(clientId);
                            java.util.regex.Matcher matcher =
                                    MANAGED_ASSET_REFERENCE.matcher(
                                                    question.path(
                                                            "questionContent")
                                                            .path(
                                                                    "speakingDelivery")
                                                            .path(
                                                                    "promptAudioReference")
                                                            .asText("")
                                                            .trim());
                            if (matcher.matches()) {
                                stagingAssets.put(
                                        clientId,
                                        Long.valueOf(matcher.group(1)));
                            }
                        }
                    }
                }
            }
            return new DraftSpeakingState(
                    Set.copyOf(result),
                    Map.copyOf(stagingAssets));
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Không thể đối chiếu vòng đời câu hỏi Speaking.",
                    exception);
        }
    }

    private record DraftSpeakingState(
            Set<String> clientIds,
            Map<String, Long> stagingAssetIds) {
    }

}
