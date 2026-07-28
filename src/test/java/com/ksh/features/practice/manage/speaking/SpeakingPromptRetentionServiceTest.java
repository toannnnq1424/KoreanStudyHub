package com.ksh.features.practice.manage.speaking;

import com.ksh.features.practice.manage.service.LecturerAssetService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeakingPromptRetentionServiceTest {

    private final SpeakingPromptAiArtifactRepository artifacts =
            mock(SpeakingPromptAiArtifactRepository.class);
    private final SpeakingPromptAiTaskRepository tasks =
            mock(SpeakingPromptAiTaskRepository.class);
    private final SpeakingPromptTranscriptRevisionRepository revisions =
            mock(SpeakingPromptTranscriptRevisionRepository.class);
    private final SpeakingPromptSourceRepository sources =
            mock(SpeakingPromptSourceRepository.class);
    private final SpeakingPromptVersionContextRepository versions =
            mock(SpeakingPromptVersionContextRepository.class);
    private final LecturerAssetService assets = mock(LecturerAssetService.class);
    private final SpeakingPromptRetentionService service =
            new SpeakingPromptRetentionService(
                    artifacts, tasks, revisions, sources, versions, assets,
                    Duration.ofDays(30));

    @Test
    void immutableVersionContextWinsOverExpiredCandidateSelection() {
        SpeakingPromptAiArtifact artifact = mock(SpeakingPromptAiArtifact.class);
        when(artifacts.findExpiredUnretainedIds(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(5L));
        when(artifacts.findByIdForUpdate(5L))
                .thenReturn(Optional.of(artifact));
        when(versions.existsBySttArtifactIdOrTtsArtifactId(5L, 5L))
                .thenReturn(true);

        assertEquals(0, service.reconcileExpired(10));

        verify(artifacts, never()).delete(artifact);
        verify(tasks, never()).deleteByArtifactId(5L);
    }

    @Test
    void expiredUnattachedArtifactDeletesFkDependentsBeforeAssetHandoff() {
        SpeakingPromptAiArtifact artifact = mock(SpeakingPromptAiArtifact.class);
        when(artifact.getInputAudioAssetId()).thenReturn(11L);
        when(artifact.getGeneratedAudioAssetId()).thenReturn(12L);
        when(artifacts.findExpiredUnretainedIds(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(5L));
        when(artifacts.findByIdForUpdate(5L))
                .thenReturn(Optional.of(artifact));

        assertEquals(1, service.reconcileExpired(10));

        verify(tasks).deleteByArtifactId(5L);
        verify(revisions).deleteByArtifactId(5L);
        verify(artifacts).delete(artifact);
        verify(artifacts).flush();
        verify(assets).queuePrivatePromptAssetIfUnreferenced(11L);
        verify(assets).queuePrivatePromptAssetIfUnreferenced(12L);
    }
}
