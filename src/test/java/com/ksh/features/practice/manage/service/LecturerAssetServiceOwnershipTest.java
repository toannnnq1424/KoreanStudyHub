package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeAssetLifecycleTask;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeAssetLifecycleTaskRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LecturerAssetServiceOwnershipTest {

    private final LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
    private final PracticeDraftRepository drafts = mock(PracticeDraftRepository.class);
    private final AssetStorageService storage = mock(AssetStorageService.class);
    private final PracticeMaterialReferenceService references =
            mock(PracticeMaterialReferenceService.class);
    private final PracticeAssetLifecycleTaskRepository tasks =
            mock(PracticeAssetLifecycleTaskRepository.class);
    private final PracticeAssetReferenceGuard referenceGuard =
            mock(PracticeAssetReferenceGuard.class);
    private LecturerAssetService service;

    @BeforeEach
    void setUp() {
        service = new LecturerAssetService(
                assets, drafts, storage, null, references, tasks, null);
        service.setAssetReferenceGuard(referenceGuard);
    }

    @Test
    void ownerCanLinkOwnedCanonicalAssetToOwnedDraft() {
        PracticeDraft draft = new PracticeDraft(
                "Draft", "", "GLOBAL", null, "DRAFT", 7L, "{}");
        LecturerAsset asset = asset(7L, "ACTIVE");
        when(drafts.findByIdAndOwnerId(10L, 7L)).thenReturn(Optional.of(draft));
        when(assets.findByIdForUpdate(20L)).thenReturn(Optional.of(asset));
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
    void crossOwnerCannotLinkCanonicalAsset() {
        PracticeDraft draft = new PracticeDraft(
                "Draft", "", "GLOBAL", null, "DRAFT", 7L, "{}");
        when(drafts.findByIdAndOwnerId(10L, 7L)).thenReturn(Optional.of(draft));
        when(assets.findByIdForUpdate(20L))
                .thenReturn(Optional.of(asset(8L, "ACTIVE")));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.linkAssetToDraft(
                        10L, 20L, 7L, "s1", "g1", "q1", "QUESTION", "alt"));

        verify(references, never()).linkDraft(any(), any(), any(), any(), any());
    }

    @Test
    void retainedAssetDeleteIsDeniedAfterExactAssetLock() {
        LecturerAsset asset = asset(7L, "ACTIVE");
        when(assets.findByIdForUpdate(20L)).thenReturn(Optional.of(asset));
        when(referenceGuard.isRetained(20L)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> service.deleteAsset(20L, 7L));

        assertEquals("ACTIVE", asset.getStatus());
        assertNull(asset.getDeletedAt());
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(assets, referenceGuard);
        order.verify(assets).findByIdForUpdate(20L);
        order.verify(referenceGuard).isRetained(20L);
        verify(assets, never()).save(asset);
        verify(tasks, never()).save(any(PracticeAssetLifecycleTask.class));
        verifyNoInteractions(storage);
    }

    @Test
    void missingReferenceGuardFailsClosedBeforeLogicalDelete() {
        LecturerAssetService incomplete = new LecturerAssetService(
                assets, drafts, storage, null, references, tasks, null);
        LecturerAsset asset = asset(7L, "ACTIVE");
        when(assets.findByIdForUpdate(20L)).thenReturn(Optional.of(asset));

        assertThrows(IllegalStateException.class,
                () -> incomplete.deleteAsset(20L, 7L));

        assertEquals("ACTIVE", asset.getStatus());
        assertNull(asset.getDeletedAt());
        verify(assets, never()).save(asset);
        verify(tasks, never()).save(any(PracticeAssetLifecycleTask.class));
    }

    private static LecturerAsset asset(Long ownerId, String status) {
        LecturerAsset asset = new LecturerAsset();
        asset.setId(20L);
        asset.setOwnerLecturerId(ownerId);
        asset.setStorageKey("private/source.png");
        asset.setStatus(status);
        return asset;
    }
}
