package com.ksh.features.practice.manage.service;

import com.ksh.features.practice.manage.speaking.SpeakingPromptAiArtifactRepository;
import com.ksh.features.practice.manage.speaking.SpeakingPromptSourceRepository;
import com.ksh.features.practice.manage.speaking.SpeakingPromptVersionContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One fail-closed answer for logical and physical lecturer-asset retention.
 * Cleanup callers must already hold the exact asset row lock in their active
 * transaction. Material references cover normal draft/published content; the
 * V55 repositories cover mutable prompt state, reusable AI evidence and
 * immutable published context.
 */
@Service
public class PracticeAssetReferenceGuard {

    private final PracticeMaterialReferenceService materialReferenceService;
    private final SpeakingPromptSourceRepository sourceRepository;
    private final SpeakingPromptAiArtifactRepository artifactRepository;
    private final SpeakingPromptVersionContextRepository versionContextRepository;

    public PracticeAssetReferenceGuard(
            PracticeMaterialReferenceService materialReferenceService,
            SpeakingPromptSourceRepository sourceRepository,
            SpeakingPromptAiArtifactRepository artifactRepository,
            SpeakingPromptVersionContextRepository versionContextRepository) {
        this.materialReferenceService = materialReferenceService;
        this.sourceRepository = sourceRepository;
        this.artifactRepository = artifactRepository;
        this.versionContextRepository = versionContextRepository;
    }

    @Transactional(
            readOnly = true,
            propagation = Propagation.MANDATORY)
    public boolean isRetained(Long assetId) {
        if (assetId == null) {
            return true;
        }
        return materialReferenceService.hasAnyReference(assetId)
                || sourceRepository
                    .existsByOriginalAudioAssetIdOrGeneratedAudioAssetIdOrActiveAudioAssetId(
                            assetId, assetId, assetId)
                || artifactRepository
                    .existsByInputAudioAssetIdOrGeneratedAudioAssetId(
                            assetId, assetId)
                || versionContextRepository
                    .existsByOriginalAudioAssetIdOrActiveAudioAssetId(
                            assetId, assetId);
    }
}
