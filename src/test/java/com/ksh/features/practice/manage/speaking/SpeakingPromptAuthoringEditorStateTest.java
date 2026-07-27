package com.ksh.features.practice.manage.speaking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingPromptAuthoringEditorStateTest {

    @Test
    void changedManualConfigurationKeepsOldAudioPlayableButNotCurrent() {
        SpeakingPromptSource source = SpeakingPromptSource.manualText(
                91L,
                "speaking-a",
                81L,
                "a".repeat(64),
                true,
                81L);
        source.markTtsQueued(201L, 81L);
        source.attachTtsArtifact(201L, 301L);

        long revision = source.markManualConfigurationChanged(
                "b".repeat(64), true, 81L);

        assertThat(revision).isEqualTo(2L);
        assertThat(source.getAudioSyncStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_STALE);
        assertThat(source.getGeneratedAudioAssetId()).isEqualTo(301L);
        assertThat(source.getActiveAudioAssetId()).isNull();
        assertThat(source.getCurrentTtsArtifactId()).isNull();
    }

    @Test
    void switchingModesRetainsInactiveGeneratedAudioWithoutProviderState() {
        SpeakingPromptSource source = SpeakingPromptSource.manualText(
                91L,
                "speaking-a",
                81L,
                "a".repeat(64),
                true,
                81L);
        source.markTtsQueued(201L, 81L);
        source.attachTtsArtifact(201L, 301L);

        source.switchToAudioUpload(401L, 81L);
        source.switchToManualText("a".repeat(64), true, 81L);

        assertThat(source.getOriginalAudioAssetId()).isEqualTo(401L);
        assertThat(source.getGeneratedAudioAssetId()).isEqualTo(301L);
        assertThat(source.getActiveAudioAssetId()).isNull();
        assertThat(source.getAudioSyncStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_STALE);
    }

    @Test
    void unlinkOnlyDetachesCurrentSourceAndLeavesCleanupOutOfScope() {
        SpeakingPromptSource source = SpeakingPromptSource.audioUpload(
                91L, "speaking-a", 81L, 401L, 81L);

        source.unlinkOriginalAudio(81L);

        assertThat(source.getOriginalAudioAssetId()).isNull();
        assertThat(source.getActiveAudioAssetId()).isNull();
        assertThat(source.getTranscriptStatus())
                .isEqualTo(SpeakingPromptSource.STATUS_IDLE);
        assertThat(source.getSourceRevision()).isEqualTo(2L);
    }
}
