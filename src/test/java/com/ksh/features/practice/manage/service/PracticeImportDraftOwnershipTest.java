package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.repository.PracticePdfImportSessionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    private static PracticePdfImportSession session(Long ownerId, Long sessionId, Long linkedDraftId) {
        PracticePdfImportSession session = new PracticePdfImportSession(
                ownerId, "exam.pdf", "stored/exam.pdf", 2, "AI_COMPLETED",
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        session.setId(sessionId);
        session.setLinkedDraftId(linkedDraftId);
        return session;
    }
}
