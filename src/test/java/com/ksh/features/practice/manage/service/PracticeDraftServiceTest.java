package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class PracticeDraftServiceTest {

    private final PracticeDraftRepository draftRepository = Mockito.mock(PracticeDraftRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PracticeDraftService service = new PracticeDraftService(draftRepository, objectMapper);

    @Test
    public void testCreateEmptyDraft() {
        when(draftRepository.save(any(PracticeDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PracticeDraft draft = service.getOrCreateEmptyDraft(99L);
        assertNotNull(draft);
        assertEquals("DRAFT", draft.getStatus());
        assertEquals(99L, draft.getOwnerId());
        assertTrue(draft.getDraftJson().contains("sections"));
    }

    @Test
    public void testSaveDraftState() {
        PracticeDraft draft = new PracticeDraft("Tiêu đề", "Mô tả", "TOPIK_II", "GLOBAL", null, "DRAFT", 99L, "{}");
        when(draftRepository.findById(1L)).thenReturn(Optional.of(draft));
        when(draftRepository.save(any(PracticeDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(draftRepository.saveAndFlush(any(PracticeDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PracticeDraft saved = service.saveDraftState(1L, 99L, "{\"sections\": []}", "Tiêu đề mới", "Mô tả mới", null);
        assertEquals("Tiêu đề mới", saved.getTitle());
        assertEquals("Mô tả mới", saved.getDescription());
        assertEquals("{\"sections\": []}", saved.getDraftJson());
    }
}
