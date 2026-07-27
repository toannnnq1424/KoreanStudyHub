package com.ksh.features.practice.manage.speaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.features.practice.manage.service.PracticeMaterialReferenceService;
import com.ksh.features.practice.manage.service.LecturerAssetService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeakingPromptLifecycleServiceTest {

    private final SpeakingPromptSourceRepository sources =
            mock(SpeakingPromptSourceRepository.class);
    private final SpeakingPromptAiArtifactRepository artifacts =
            mock(SpeakingPromptAiArtifactRepository.class);
    private final SpeakingPromptAiTaskRepository tasks =
            mock(SpeakingPromptAiTaskRepository.class);
    private final PracticeMaterialReferenceService references =
            mock(PracticeMaterialReferenceService.class);
    private final LecturerAssetService lecturerAssets =
            mock(LecturerAssetService.class);
    private final SpeakingPromptLifecycleService service =
            new SpeakingPromptLifecycleService(
                    sources, artifacts, tasks, references, lecturerAssets,
                    new ObjectMapper());

    @Test
    void removedClientCancelsSoleTaskUnlinksExactBindingThenDeletesSource() {
        SpeakingPromptSource removed = source(10L, 20L, "old-client", 7L, 30L);
        SpeakingPromptAiArtifact artifact = artifact(30L, 7L, "stt", "f".repeat(64));
        SpeakingPromptAiTask task = mock(SpeakingPromptAiTask.class);
        when(task.getArtifactId()).thenReturn(30L);
        when(sources.findByDraftIdForUpdate(20L)).thenReturn(List.of(removed));
        when(artifacts.findByIdForUpdate(30L)).thenReturn(Optional.of(artifact));
        when(sources.findByCurrentSttArtifactIdOrderByDraftIdAscIdAsc(30L))
                .thenReturn(List.of(removed));
        when(tasks.findActiveByFingerprint(7L, "stt", "f".repeat(64)))
                .thenReturn(Optional.of(task));

        service.reconcileDraftQuestions(
                20L, 7L, 9L,
                """
                {"sections":[{"groups":[{"questions":[
                  {"clientId":"new-client","questionType":"SPEAKING"}
                ]}]}]}
                """);

        verify(task).markCancelled(org.mockito.ArgumentMatchers.any());
        verify(tasks).saveAndFlush(task);
        verify(references).unlinkDraft(
                20L, 40L, "SPEAKING_PROMPT_ORIGINAL", "old-client");
        verify(sources).deleteAll(List.of(removed));
        verify(sources).flush();
        verify(lecturerAssets).queuePrivatePromptAssetIfUnreferenced(40L);
    }

    @Test
    void deletingOneSourceDoesNotCancelOwnerReusableTaskStillAttachedElsewhere() {
        SpeakingPromptSource removed = source(10L, 20L, "old-client", 7L, 30L);
        SpeakingPromptSource retained = source(11L, 21L, "other-client", 7L, 30L);
        SpeakingPromptAiArtifact artifact = artifact(30L, 7L, "stt", "f".repeat(64));
        when(sources.findByDraftIdForUpdate(20L)).thenReturn(List.of(removed));
        when(artifacts.findByIdForUpdate(30L)).thenReturn(Optional.of(artifact));
        when(sources.findByCurrentSttArtifactIdOrderByDraftIdAscIdAsc(30L))
                .thenReturn(List.of(removed, retained));

        service.teardownDraft(20L, 7L, 7L);

        verify(tasks, never()).findActiveByFingerprint(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(sources).deleteAll(List.of(removed));
    }

    @Test
    void wrongOwnerSourceFailsClosedBeforeAnyDelete() {
        SpeakingPromptSource other = source(10L, 20L, "q1", 8L, null);
        when(sources.findByDraftIdForUpdate(20L)).thenReturn(List.of(other));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> service.teardownDraft(20L, 7L, 7L));

        verify(sources, never()).deleteAll(
                org.mockito.ArgumentMatchers.anyList());
        verify(references, never()).unlinkDraftReference(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void sameClientChangedToNonSpeakingRemovesStagingAndQueuesAsset() {
        com.ksh.entities.PracticeMaterialReference staging =
                PracticeMaterialReference.draft(
                        44L,
                        20L,
                        com.ksh.features.practice.manage.service
                                .PracticeAssessmentExcelService
                                .EXCEL_SPEAKING_STAGING,
                        "same-client",
                        null);
        setReferenceId(staging, 90L);
        when(sources.findByDraftIdForUpdate(20L)).thenReturn(List.of());
        when(references.referencesForDraft(20L))
                .thenReturn(List.of(staging));

        service.reconcileDraftQuestions(
                20L,
                7L,
                7L,
                """
                {"sections":[{"groups":[{"questions":[
                  {"clientId":"same-client","questionType":"ESSAY"}
                ]}]}]}
                """);

        verify(references).unlinkDraftReference(20L, 90L);
        verify(lecturerAssets).queuePrivatePromptAssetIfUnreferenced(44L);
    }

    @Test
    void sameSpeakingClientWithReplacementAudioRemovesOldStaging() {
        com.ksh.entities.PracticeMaterialReference staging =
                PracticeMaterialReference.draft(
                        44L,
                        20L,
                        com.ksh.features.practice.manage.service
                                .PracticeAssessmentExcelService
                                .EXCEL_SPEAKING_STAGING,
                        "same-client",
                        null);
        setReferenceId(staging, 91L);
        SpeakingPromptSource current =
                source(10L, 20L, "same-client", 7L, null);
        when(sources.findByDraftIdForUpdate(20L))
                .thenReturn(List.of(current));
        when(references.referencesForDraft(20L))
                .thenReturn(List.of(staging));
        when(tasks.findBySourceIdsForUpdate(List.of(10L)))
                .thenReturn(List.of());

        service.reconcileDraftQuestions(
                20L,
                7L,
                7L,
                """
                {"sections":[{"groups":[{"questions":[{
                  "clientId":"same-client",
                  "questionType":"SPEAKING",
                  "questionContent":{"speakingDelivery":{
                    "promptAudioReference":"/practice/materials/55/content"
                  }}
                }]}]}]}
                """);

        verify(references).unlinkDraftReference(20L, 91L);
        verify(references).unlinkDraft(
                20L,
                40L,
                SpeakingPromptAssetService.ORIGINAL_PLACEMENT,
                "same-client");
        verify(sources).deleteAll(List.of(current));
        verify(sources).flush();
        verify(lecturerAssets).queuePrivatePromptAssetIfUnreferenced(44L);
        verify(lecturerAssets).queuePrivatePromptAssetIfUnreferenced(40L);
    }

    @Test
    void sameSpeakingClientWithoutManagedReplacementRetainsCurrentSource() {
        SpeakingPromptSource current =
                source(10L, 20L, "same-client", 7L, null);
        when(sources.findByDraftIdForUpdate(20L))
                .thenReturn(List.of(current));
        when(references.referencesForDraft(20L)).thenReturn(List.of());

        service.reconcileDraftQuestions(
                20L,
                7L,
                7L,
                """
                {"sections":[{"groups":[{"questions":[{
                  "clientId":"same-client",
                  "questionType":"SPEAKING"
                }]}]}]}
                """);

        verify(sources, never()).deleteAll(
                org.mockito.ArgumentMatchers.anyList());
        verify(references, never()).unlinkDraft(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(lecturerAssets, never())
                .queuePrivatePromptAssetIfUnreferenced(
                        org.mockito.ArgumentMatchers.anyLong());
    }

    private static void setReferenceId(
            com.ksh.entities.PracticeMaterialReference reference,
            Long id) {
        try {
            java.lang.reflect.Field field =
                    com.ksh.entities.PracticeMaterialReference.class
                            .getDeclaredField("id");
            field.setAccessible(true);
            field.set(reference, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static SpeakingPromptSource source(
            Long id, Long draftId, String clientId, Long ownerId, Long sttArtifactId) {
        SpeakingPromptSource source = mock(SpeakingPromptSource.class);
        when(source.getId()).thenReturn(id);
        when(source.getDraftId()).thenReturn(draftId);
        when(source.getQuestionClientId()).thenReturn(clientId);
        when(source.getOwnerLecturerId()).thenReturn(ownerId);
        when(source.getCurrentSttArtifactId()).thenReturn(sttArtifactId);
        when(source.getOriginalAudioAssetId()).thenReturn(40L);
        return source;
    }

    private static SpeakingPromptAiArtifact artifact(
            Long id, Long ownerId, String operation, String fingerprint) {
        SpeakingPromptAiArtifact artifact = mock(SpeakingPromptAiArtifact.class);
        when(artifact.getId()).thenReturn(id);
        when(artifact.getOwnerLecturerId()).thenReturn(ownerId);
        when(artifact.getOperation()).thenReturn(operation);
        when(artifact.getOperationFingerprint()).thenReturn(fingerprint);
        return artifact;
    }
}
