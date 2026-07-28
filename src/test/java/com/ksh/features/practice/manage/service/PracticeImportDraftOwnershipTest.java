package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.features.practice.manage.speaking.SpeakingPromptSource;
import com.ksh.features.practice.manage.speaking.SpeakingPromptSourceRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.repository.PracticeMaterialReferenceRepository;
import com.ksh.features.practice.repository.PracticePdfImportSessionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PracticeImportDraftOwnershipTest {

    @Test
    void assemblerCannotOverwriteLinkedDraftOwnedByAnotherLecturer() {
        PracticeDraftRepository draftRepository = mock(PracticeDraftRepository.class);
        PracticePdfImportSessionService sessionService = mock(PracticePdfImportSessionService.class);
        PracticePdfDraftAssembler assembler = new PracticePdfDraftAssembler(
                draftRepository, sessionService, new ObjectMapper());
        PracticePdfImportSession session = session(7L, 100L, 55L);
        when(draftRepository.findByIdAndOwnerId(55L, 7L)).thenReturn(Optional.empty());

        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> assembler.assembleAndSaveDraft(session, "{\"sections\":[]}", 7L));

        verify(draftRepository, never()).save(any());
        verify(sessionService, never()).updateDraftId(any(), any());
    }

    @Test
    void manualCopyCannotReadLinkedAiDraftOwnedByAnotherLecturer() {
        PracticeDraftRepository draftRepository = mock(PracticeDraftRepository.class);
        PracticePdfImportSessionRepository sessionRepository =
                mock(PracticePdfImportSessionRepository.class);
        PracticeImportDraftService service = new PracticeImportDraftService(
                draftRepository, sessionRepository, new ObjectMapper());
        PracticePdfImportSession session = session(7L, 100L, 55L);
        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(draftRepository.findByIdAndOwnerId(55L, 7L)).thenReturn(Optional.empty());

        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> service.createManualDraftFromSession(100L, 7L));

        verify(draftRepository, never()).save(any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void manualCopyRejectsSourceBearingDraftBeforeCreatingAnything() {
        Fixture fixture = fixture();
        SpeakingPromptSource source = mock(SpeakingPromptSource.class);
        when(fixture.sourceRepository.findByDraftIdForUpdate(55L))
                .thenReturn(List.of(source));

        assertThrows(IllegalStateException.class,
                () -> fixture.service.createManualDraftFromSession(100L, 7L));

        verify(fixture.draftRepository, never()).save(any());
        verify(fixture.draftRepository, never()).delete(any());
        verify(fixture.sessionRepository, never()).save(any());
    }

    @Test
    void manualCopyRejectsMaterialBearingDraftBeforeCreatingAnything() {
        Fixture fixture = fixture();
        when(fixture.materialReferenceRepository.findByDraftId(55L))
                .thenReturn(List.of(
                        PracticeMaterialReference.draft(
                                91L, 55L, "SPEAKING_PROMPT_ORIGINAL",
                                "question-a", null)));

        assertThrows(IllegalStateException.class,
                () -> fixture.service.createManualDraftFromSession(100L, 7L));

        verify(fixture.draftRepository, never()).save(any());
        verify(fixture.draftRepository, never()).delete(any());
        verify(fixture.sessionRepository, never()).save(any());
    }

    @Test
    void manualCopyRejectsExplicitSpeakingV2IdentityWithoutCopyingJson() {
        Fixture fixture = fixture();
        when(fixture.aiDraft.getDraftJson()).thenReturn("""
                {"sections":[{"skill":"SPEAKING","groups":[{"questions":[{
                  "clientId":"question-a",
                  "questionType":"SPEAKING",
                  "questionContent":{
                    "schemaVersion":"question-content-v2",
                    "speakingDelivery":{
                      "inputType":"audio_upload",
                      "deliveryMode":"audio_only",
                      "promptAudioReference":
                        "/practice/manage/drafts/55/questions/question-a/speaking-prompt/media/original",
                      "audioOrigin":"teacher_upload"
                    }
                  }
                }]}]}]}
                """);

        assertThrows(IllegalStateException.class,
                () -> fixture.service.createManualDraftFromSession(100L, 7L));

        verify(fixture.draftRepository, never()).save(any());
        verify(fixture.sessionRepository, never()).save(any());
    }

    @Test
    void attachRejectsSourceBearingDraftBeforeTargetMutationOrSourceDelete() {
        Fixture fixture = fixture();
        when(fixture.sourceRepository.findByDraftIdForUpdate(55L))
                .thenReturn(List.of(mock(SpeakingPromptSource.class)));

        assertThrows(IllegalStateException.class,
                () -> fixture.service.attachToExistingDraft(
                        100L, 77L, 7L));

        verify(fixture.targetDraft, never()).setDraftJson(anyString());
        verify(fixture.draftRepository, never()).save(any());
        verify(fixture.draftRepository, never()).delete(fixture.aiDraft);
        verify(fixture.sessionRepository, never()).save(any());
    }

    @Test
    void attachRejectsMaterialBearingDraftBeforeTargetMutationOrSourceDelete() {
        Fixture fixture = fixture();
        when(fixture.materialReferenceRepository.findByDraftId(55L))
                .thenReturn(List.of(
                        PracticeMaterialReference.draft(
                                92L, 55L, "EXCEL_SPEAKING_STAGING",
                                "question-a", null)));

        assertThrows(IllegalStateException.class,
                () -> fixture.service.attachToExistingDraft(
                        100L, 77L, 7L));

        verify(fixture.targetDraft, never()).setDraftJson(anyString());
        verify(fixture.draftRepository, never()).save(any());
        verify(fixture.draftRepository, never()).delete(fixture.aiDraft);
        verify(fixture.sessionRepository, never()).save(any());
    }

    @Test
    void attachRejectsTemporarySourceAsItsOwnTargetWithoutAnyDraftSideEffect() {
        Fixture fixture = fixture();

        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.attachToExistingDraft(
                        100L, 55L, 7L));

        verifyNoInteractions(
                fixture.draftRepository,
                fixture.sourceRepository,
                fixture.materialReferenceRepository);
        verify(fixture.aiDraft, never()).getDraftJson();
        verify(fixture.aiDraft, never()).setDraftJson(anyString());
        verify(fixture.draftRepository, never()).save(any());
        verify(fixture.draftRepository, never()).delete(any());
        verify(fixture.sessionRepository, never()).save(any());
    }

    @Test
    void pristinePdfOnlyDraftStillCreatesManualCopy() {
        Fixture fixture = fixture();
        when(fixture.draftRepository.save(any(PracticeDraft.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PracticeDraft result =
                fixture.service.createManualDraftFromSession(100L, 7L);

        verify(fixture.draftRepository).save(result);
        verify(fixture.draftRepository, never()).delete(any());
        verify(fixture.sessionRepository).save(fixture.session);
    }

    @Test
    void pristinePdfOnlyDraftStillAttachesAndDeletesTemporaryAiDraft() {
        Fixture fixture = fixture();
        when(fixture.draftRepository.save(fixture.targetDraft))
                .thenReturn(fixture.targetDraft);

        PracticeDraft result = fixture.service.attachToExistingDraft(
                100L, 77L, 7L);

        assertSame(fixture.targetDraft, result);
        verify(fixture.targetDraft).setDraftJson(
                "{\"sections\":[{\"title\":\"existing\"},{\"title\":\"imported\"}]}");
        verify(fixture.draftRepository).delete(fixture.aiDraft);
        verify(fixture.sessionRepository).save(fixture.session);
    }

    private static Fixture fixture() {
        PracticeDraftRepository draftRepository =
                mock(PracticeDraftRepository.class);
        PracticePdfImportSessionRepository sessionRepository =
                mock(PracticePdfImportSessionRepository.class);
        SpeakingPromptSourceRepository sourceRepository =
                mock(SpeakingPromptSourceRepository.class);
        PracticeMaterialReferenceRepository materialReferenceRepository =
                mock(PracticeMaterialReferenceRepository.class);
        PracticeImportDraftService service = new PracticeImportDraftService(
                draftRepository,
                sessionRepository,
                new ObjectMapper(),
                sourceRepository,
                materialReferenceRepository);
        PracticePdfImportSession session = session(7L, 100L, 55L);
        PracticeDraft aiDraft = draft(
                55L, 7L,
                "{\"sections\":[{\"title\":\"imported\"}]}");
        PracticeDraft targetDraft = draft(
                77L, 7L,
                "{\"sections\":[{\"title\":\"existing\"}]}");
        when(sessionRepository.findById(100L))
                .thenReturn(Optional.of(session));
        when(draftRepository.findByIdAndOwnerId(55L, 7L))
                .thenReturn(Optional.of(aiDraft));
        when(draftRepository.findByIdForUpdate(55L))
                .thenReturn(Optional.of(aiDraft));
        when(draftRepository.findByIdAndOwnerId(77L, 7L))
                .thenReturn(Optional.of(targetDraft));
        when(sourceRepository.findByDraftIdForUpdate(55L))
                .thenReturn(List.of());
        when(materialReferenceRepository.findByDraftId(55L))
                .thenReturn(List.of());
        return new Fixture(
                service,
                draftRepository,
                sessionRepository,
                sourceRepository,
                materialReferenceRepository,
                session,
                aiDraft,
                targetDraft);
    }

    private static PracticeDraft draft(Long id, Long ownerId, String json) {
        PracticeDraft draft = mock(PracticeDraft.class);
        when(draft.getId()).thenReturn(id);
        when(draft.getOwnerId()).thenReturn(ownerId);
        when(draft.getTitle()).thenReturn("Import");
        when(draft.getDescription()).thenReturn("");
        when(draft.getScope()).thenReturn("PRIVATE");
        when(draft.getDraftJson()).thenReturn(json);
        return draft;
    }

    private static PracticePdfImportSession session(Long ownerId, Long sessionId, Long linkedDraftId) {
        PracticePdfImportSession session = new PracticePdfImportSession(
                ownerId, "exam.pdf", "stored/exam.pdf", 2, "AI_COMPLETED",
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        session.setId(sessionId);
        session.setLinkedDraftId(linkedDraftId);
        return session;
    }

    private record Fixture(
            PracticeImportDraftService service,
            PracticeDraftRepository draftRepository,
            PracticePdfImportSessionRepository sessionRepository,
            SpeakingPromptSourceRepository sourceRepository,
            PracticeMaterialReferenceRepository materialReferenceRepository,
            PracticePdfImportSession session,
            PracticeDraft aiDraft,
            PracticeDraft targetDraft) {
    }
}
