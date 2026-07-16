package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.entities.PracticeAssetLifecycleTask;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeAssetLifecycleTaskRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LecturerAssetServiceOwnershipTest {

    private final LecturerAssetRepository assetRepository = mock(LecturerAssetRepository.class);
    private final PracticeDraftRepository draftRepository = mock(PracticeDraftRepository.class);
    private final AssetStorageService storage = mock(AssetStorageService.class);
    private final PracticeMaterialReferenceService references =
            mock(PracticeMaterialReferenceService.class);
    private final PracticeAssetLifecycleTaskRepository tasks =
            mock(PracticeAssetLifecycleTaskRepository.class);

    private LecturerAssetService service;

    @BeforeEach
    void setUp() {
        service = new LecturerAssetService(
                assetRepository, draftRepository, storage, null, references, tasks, null);
    }

    @Test
    void ownerCanLinkOwnedAssetToOwnedDraft() {
        PracticeDraft draft = new PracticeDraft("Draft", "",  "GLOBAL",
                null, "DRAFT", 7L, "{}");
        LecturerAsset asset = new LecturerAsset();
        asset.setOwnerLecturerId(7L);
        when(draftRepository.findByIdAndOwnerId(10L, 7L)).thenReturn(Optional.of(draft));
        when(assetRepository.findById(20L)).thenReturn(Optional.of(asset));
        PracticeMaterialReference reference =
                PracticeMaterialReference.draft(20L, 10L, "QUESTION");
        when(references.linkDraft(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq("QUESTION"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(reference);

        PracticeMaterialReference usage = service.linkAssetToDraft(
                10L, 20L, 7L, "s1", "g1", "q1", "QUESTION", "alt");

        assertEquals(10L, usage.getDraftId());
        assertEquals(20L, usage.getAssetId());
    }

    @Test
    void crossOwnerCannotLinkAssetOrUnlinkUsage() {
        PracticeDraft draft = new PracticeDraft("Draft", "",  "GLOBAL",
                null, "DRAFT", 7L, "{}");
        LecturerAsset otherAsset = new LecturerAsset();
        otherAsset.setOwnerLecturerId(8L);
        when(draftRepository.findByIdAndOwnerId(10L, 7L)).thenReturn(Optional.of(draft));
        when(assetRepository.findById(20L)).thenReturn(Optional.of(otherAsset));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.linkAssetToDraft(
                        10L, 20L, 7L, "s1", "g1", "q1", "QUESTION", "alt"));

        when(draftRepository.findByIdAndOwnerId(10L, 8L)).thenReturn(Optional.empty());
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> service.unlinkAssetFromDraft(10L, 30L, 8L));

        verify(references, never()).linkDraft(
                any(), any(), any(), any(), any());
        verify(references, never()).unlinkDraftReference(any(), any());
    }

    @Test
    void assetCannotBePromotedThroughAnotherSessionOrRegionRoute() {
        LecturerAsset asset = new LecturerAsset();
        asset.setOwnerLecturerId(7L);
        asset.setSourceImportSessionId(100L);
        asset.setSourceRegionId(200L);
        asset.setStatus("TEMPORARY");
        when(assetRepository.findById(20L)).thenReturn(Optional.of(asset));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.promoteSessionRegionAsset(101L, 200L, 20L, 7L));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.promoteSessionRegionAsset(100L, 201L, 20L, 7L));

        verifyNoInteractions(storage);
        verify(assetRepository, never()).save(any());
    }

    @Test
    void temporaryCleanupPreservesAssetReferencedByDraft() {
        LecturerAssetService hardenedService = new LecturerAssetService(
                assetRepository, draftRepository, storage,
                null, references, tasks, null);
        LecturerAsset asset = new LecturerAsset();
        asset.setId(20L);
        asset.setOwnerLecturerId(7L);
        asset.setSourceImportSessionId(100L);
        asset.setStorageKey("private/source.png");
        asset.setStatus("TEMPORARY");
        when(assetRepository.findBySourceImportSessionIdAndOwnerLecturerId(100L, 7L))
                .thenReturn(List.of(asset));
        when(references.hasAnyReference(20L)).thenReturn(true);

        hardenedService.cleanupTemporaryAssets(100L, 7L);

        assertEquals("ARCHIVED", asset.getStatus());
        verify(assetRepository).save(asset);
        verify(tasks, never()).save(any(PracticeAssetLifecycleTask.class));
        verifyNoInteractions(storage);
    }
}
