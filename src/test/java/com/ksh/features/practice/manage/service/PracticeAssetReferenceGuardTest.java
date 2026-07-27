package com.ksh.features.practice.manage.service;

import com.ksh.features.practice.manage.speaking.SpeakingPromptAiArtifactRepository;
import com.ksh.features.practice.manage.speaking.SpeakingPromptSourceRepository;
import com.ksh.features.practice.manage.speaking.SpeakingPromptVersionContextRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PracticeAssetReferenceGuardTest {

    private final PracticeMaterialReferenceService materials =
            mock(PracticeMaterialReferenceService.class);
    private final SpeakingPromptSourceRepository sources =
            mock(SpeakingPromptSourceRepository.class);
    private final SpeakingPromptAiArtifactRepository artifacts =
            mock(SpeakingPromptAiArtifactRepository.class);
    private final SpeakingPromptVersionContextRepository versions =
            mock(SpeakingPromptVersionContextRepository.class);
    private final PracticeAssetReferenceGuard guard =
            new PracticeAssetReferenceGuard(
                    materials, sources, artifacts, versions);

    @Test
    void materialReferenceRetainsAsset() {
        when(materials.hasAnyReference(9L)).thenReturn(true);

        assertTrue(guard.isRetained(9L));
    }

    @Test
    void currentPromptSourceRetainsEveryAudioRole() {
        when(sources
                .existsByOriginalAudioAssetIdOrGeneratedAudioAssetIdOrActiveAudioAssetId(
                        9L, 9L, 9L)).thenReturn(true);

        assertTrue(guard.isRetained(9L));
    }

    @Test
    void immutablePublishedContextRetainsAssetWithoutMaterialReference() {
        when(versions.existsByOriginalAudioAssetIdOrActiveAudioAssetId(
                9L, 9L)).thenReturn(true);

        assertTrue(guard.isRetained(9L));
    }

    @Test
    void reusableArtifactInputRetainsAssetAfterMutableSourceIsGone() {
        when(artifacts.existsByInputAudioAssetIdOrGeneratedAudioAssetId(
                9L, 9L)).thenReturn(true);

        assertTrue(guard.isRetained(9L));
    }

    @Test
    void trulyUnreferencedPrivateAssetIsCleanupEligible() {
        assertFalse(guard.isRetained(9L));
    }
}
