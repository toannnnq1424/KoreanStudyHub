package com.ksh.features.practice.manage.speaking;

import org.springframework.data.jpa.repository.JpaRepository;
public interface SpeakingPromptVersionContextRepository
        extends JpaRepository<SpeakingPromptVersionContext, Long> {
    boolean existsByOriginalAudioAssetIdOrActiveAudioAssetId(
            Long originalAudioAssetId, Long activeAudioAssetId);

    boolean existsBySttArtifactIdOrTtsArtifactId(
            Long sttArtifactId, Long ttsArtifactId);
}
