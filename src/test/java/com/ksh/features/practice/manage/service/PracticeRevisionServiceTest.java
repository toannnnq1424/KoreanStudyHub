package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticeEditLog;
import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeSet;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.governance.PracticeGovernanceAuditService;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.repository.PracticeEditLogRepository;
import com.ksh.features.practice.repository.PracticePublishedVersionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.service.PracticePublishedVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeRevisionServiceTest {

    private final PracticeEditLogRepository editLogRepository =
            mock(PracticeEditLogRepository.class);
    private final PracticeDraftRepository draftRepository =
            mock(PracticeDraftRepository.class);
    private final PracticeSetRepository setRepository = mock(PracticeSetRepository.class);
    private final PracticePublishedVersionRepository versionRepository =
            mock(PracticePublishedVersionRepository.class);
    private final PracticePublishedVersionService publishedVersionService =
            mock(PracticePublishedVersionService.class);
    private final PracticePublisherService publisherService = mock(PracticePublisherService.class);
    private final PracticeAuthorizationService authorizationService =
            mock(PracticeAuthorizationService.class);
    private final PracticeGovernanceAuditService auditService =
            mock(PracticeGovernanceAuditService.class);

    private PracticeRevisionService service;

    @BeforeEach
    void setUp() {
        service = new PracticeRevisionService(
                editLogRepository,
                draftRepository,
                setRepository,
                versionRepository,
                publishedVersionService,
                publisherService,
                authorizationService,
                auditService,
                new ObjectMapper());
    }

    @Test
    void restorePublishedVersionPublishesFreshVersionAndPreservesSource() throws Exception {
        PracticeSet set = publishedSet(9L, 11L);
        PracticePublishedVersion source = publishedVersion(71L, 9L, 3);
        PracticePublishedVersion created = publishedVersion(88L, 9L, 4);
        PracticeAuthorizationService.Decision decision =
                new PracticeAuthorizationService.Decision(11L, true, false, false);

        when(authorizationService.requireSet(9L, 22L, PracticeAction.RESTORE,
                "Khôi phục khẩn cấp")).thenReturn(decision);
        when(versionRepository.findById(71L)).thenReturn(Optional.of(source));
        when(publishedVersionService.draftSnapshotJson(71L, 9L))
                .thenReturn(validSnapshot());
        when(setRepository.findById(9L)).thenReturn(Optional.of(set));
        when(draftRepository.saveAndFlush(any(PracticeDraft.class)))
                .thenAnswer(invocation -> {
                    PracticeDraft draft = invocation.getArgument(0);
                    setId(draft, 50L);
                    return draft;
                });
        when(publisherService.publishRestored(50L, 22L, "Khôi phục khẩn cấp"))
                .thenReturn(9L);
        when(versionRepository.findFirstBySetIdOrderByVersionNumberDesc(9L))
                .thenReturn(Optional.of(source), Optional.of(created));
        when(publishedVersionService.draftSnapshotJson(88L, 9L))
                .thenReturn(validSnapshot());

        Long result = service.restorePublishedVersion(
                9L, 71L, 22L, "Khôi phục khẩn cấp");

        assertEquals(88L, result);
        ArgumentCaptor<PracticeDraft> draftCaptor = ArgumentCaptor.forClass(PracticeDraft.class);
        verify(draftRepository).saveAndFlush(draftCaptor.capture());
        PracticeDraft temporary = draftCaptor.getValue();
        assertEquals(11L, temporary.getOwnerId());
        assertEquals(9L, temporary.getPublishedSetId());
        assertEquals("RESTORE", temporary.getCreationMethod());
        assertEquals(123L, temporary.getAssessmentProgramVersionId());
        verify(publisherService).publishRestored(50L, 22L, "Khôi phục khẩn cấp");
        verify(draftRepository).delete(temporary);
        verify(draftRepository).flush();
        ArgumentCaptor<String> beforeAudit = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> afterAudit = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(
                eq("VERSION_RESTORED"), eq("SET"), eq(9L), eq(11L), eq(22L),
                eq(71L), eq(true), eq("Khôi phục khẩn cấp"),
                beforeAudit.capture(), afterAudit.capture());
        var before = new ObjectMapper().readTree(beforeAudit.getValue());
        var after = new ObjectMapper().readTree(afterAudit.getValue());
        assertEquals("published-version:3", before.path("source").asText());
        assertEquals(71L, before.path("publishedVersionId").asLong());
        assertEquals(3, before.path("versionNumber").asInt());
        assertTrue(before.path("snapshot").path("sections").isArray());
        assertEquals("published-version:3", after.path("source").asText());
        assertEquals(88L, after.path("publishedVersionId").asLong());
        assertEquals(4, after.path("versionNumber").asInt());
        assertTrue(after.path("snapshot").path("sections").isArray());
    }

    @Test
    void restoreLegacyEditLogAlsoCreatesFreshPublishedVersion() throws Exception {
        PracticeEditLog log = new PracticeEditLog(
                9L, 11L, "Legacy", "{}", validSnapshot(), null, "QUESTIONS");
        setId(log, 7L);
        PracticeSet set = publishedSet(9L, 11L);
        PracticePublishedVersion current = publishedVersion(70L, 9L, 4);
        PracticePublishedVersion created = publishedVersion(89L, 9L, 5);
        when(editLogRepository.findById(7L)).thenReturn(Optional.of(log));
        when(authorizationService.requireSet(9L, 11L, PracticeAction.RESTORE, null))
                .thenReturn(new PracticeAuthorizationService.Decision(11L, false, false, true));
        when(setRepository.findById(9L)).thenReturn(Optional.of(set));
        when(draftRepository.saveAndFlush(any(PracticeDraft.class)))
                .thenAnswer(invocation -> {
                    PracticeDraft draft = invocation.getArgument(0);
                    setId(draft, 51L);
                    return draft;
                });
        when(publisherService.publishRestored(51L, 11L, null)).thenReturn(9L);
        when(versionRepository.findFirstBySetIdOrderByVersionNumberDesc(9L))
                .thenReturn(Optional.of(current), Optional.of(created));
        when(publishedVersionService.draftSnapshotJson(70L, 9L))
                .thenReturn(validSnapshot());
        when(publishedVersionService.draftSnapshotJson(89L, 9L))
                .thenReturn(validSnapshot());

        assertEquals(89L, service.restoreRevision(7L, 11L));

        verify(publisherService).publishRestored(51L, 11L, null);
        verify(auditService).record(
                eq("VERSION_RESTORED"), eq("SET"), eq(9L), eq(11L), eq(11L),
                isNull(), eq(false), isNull(), anyString(), anyString());
    }

    @Test
    void restoreRejectsVersionFromAnotherSetBeforeBuildingDraft() throws Exception {
        PracticePublishedVersion source = publishedVersion(71L, 10L, 3);
        when(authorizationService.requireSet(9L, 11L, PracticeAction.RESTORE, null))
                .thenReturn(new PracticeAuthorizationService.Decision(11L, false, false, true));
        when(versionRepository.findById(71L)).thenReturn(Optional.of(source));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.restorePublishedVersion(9L, 71L, 11L, null));

        assertTrue(error.getMessage().contains("không thuộc"));
        verify(publishedVersionService, never()).draftSnapshotJson(anyLong(), anyLong());
        verify(draftRepository, never()).saveAndFlush(any());
    }

    @Test
    void restoreRejectsMissingSnapshotBeforeAuthorizationOrMutation() throws Exception {
        PracticeEditLog log = new PracticeEditLog(
                9L, 11L, "Legacy", "{}", "{}", null, "QUESTIONS");
        setId(log, 7L);
        when(editLogRepository.findById(7L)).thenReturn(Optional.of(log));

        assertThrows(IllegalArgumentException.class,
                () -> service.restoreRevision(7L, 11L));

        verify(authorizationService, never()).requireSet(
                anyLong(), anyLong(), any(PracticeAction.class), any());
        verify(draftRepository, never()).saveAndFlush(any());
        verify(publisherService, never()).publishRestored(anyLong(), anyLong(), any());
    }

    @Test
    void crossOwnerDenialStopsBeforeDraftCreation() throws Exception {
        PracticeEditLog log = new PracticeEditLog(
                9L, 11L, "Legacy", "{}", validSnapshot(), null, "QUESTIONS");
        setId(log, 7L);
        when(editLogRepository.findById(7L)).thenReturn(Optional.of(log));
        when(authorizationService.requireSet(9L, 22L, PracticeAction.RESTORE, null))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> service.restoreRevision(7L, 22L));

        verify(draftRepository, never()).saveAndFlush(any());
        verify(publisherService, never()).publishRestored(anyLong(), anyLong(), any());
    }

    @Test
    void versionsRequiresReadAndReturnsNewestFirst() {
        List<PracticePublishedVersion> versions = List.of(
                new PracticePublishedVersion(9L, 2, "PUBLISHED", "hash", 11L));
        when(versionRepository.findBySetIdOrderByVersionNumberDesc(9L))
                .thenReturn(versions);

        assertEquals(versions, service.versions(9L, 11L));

        verify(authorizationService).requireSet(9L, 11L, PracticeAction.READ, null);
    }

    private static PracticeSet publishedSet(Long id, Long ownerId) throws Exception {
        PracticeSet set = new PracticeSet(
                "Set", "Description", "READING", "TOPIK_II", "GLOBAL",
                null, null, "{}", "PUBLISHED", ownerId);
        set.setAssessmentProgramCode("TOPIK");
        set.setAssessmentProgramVersionId(123L);
        set.setExamTemplateCode("TOPIK_II");
        setId(set, id);
        return set;
    }

    private static PracticePublishedVersion publishedVersion(
            Long id, Long setId, int versionNumber) throws Exception {
        PracticePublishedVersion version = new PracticePublishedVersion(
                setId, versionNumber, "PUBLISHED", "hash", 11L);
        setId(version, id);
        return version;
    }

    private static String validSnapshot() {
        return """
                {
                  "document": {
                    "title": "Restored set",
                    "description": "Description",
                    "detectedCategory": "TOPIK_II",
                    "assessmentProgramCode": "TOPIK",
                    "assessmentProgramVersionId": 123,
                    "examTemplateCode": "TOPIK_II"
                  },
                  "sections": []
                }
                """;
    }

    private static void setId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
