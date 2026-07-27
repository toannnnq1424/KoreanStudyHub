package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeMaterialReferenceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeMaterialReferenceServiceTest {

    @Test
    void promotionPreservesEveryPlacementForSharedAsset() {
        PracticeMaterialReferenceRepository referenceRepository =
                mock(PracticeMaterialReferenceRepository.class);
        LecturerAssetRepository assetRepository = mock(LecturerAssetRepository.class);
        PracticeMaterialReferenceService service = new PracticeMaterialReferenceService(
                referenceRepository, assetRepository);
        LecturerAsset asset = new LecturerAsset();
        asset.setId(7L);
        asset.setStatus("ACTIVE");
        asset.setRetentionUntil(LocalDateTime.now().plusDays(1));
        when(referenceRepository.findByDraftId(10L)).thenReturn(List.of(
                PracticeMaterialReference.draft(7L, 10L, "GROUP_IMAGE"),
                PracticeMaterialReference.draft(7L, 10L, "OPTION_A")));
        when(assetRepository.findByIdForUpdate(7L))
                .thenReturn(Optional.of(asset));
        when(referenceRepository.save(any(PracticeMaterialReference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.promoteDraftReferences(10L, 20L, 30L);

        ArgumentCaptor<PracticeMaterialReference> references =
                ArgumentCaptor.forClass(PracticeMaterialReference.class);
        verify(referenceRepository, org.mockito.Mockito.times(2)).save(references.capture());
        Set<String> placements = references.getAllValues().stream()
                .map(PracticeMaterialReference::getPlacement)
                .collect(Collectors.toSet());
        assertEquals(Set.of("GROUP_IMAGE", "OPTION_A"), placements);
        assertEquals("ACTIVE", asset.getStatus());
        assertEquals("PUBLISHED", asset.getVisibility());
        assertNull(asset.getDeletedAt());
        assertNull(asset.getRetentionUntil());
        verify(assetRepository).save(asset);
    }

    @Test
    void promotionLocksEveryAssetInIdOrderBeforePublishedReferenceInsert() {
        PracticeMaterialReferenceRepository referenceRepository =
                mock(PracticeMaterialReferenceRepository.class);
        LecturerAssetRepository assetRepository =
                mock(LecturerAssetRepository.class);
        PracticeMaterialReferenceService service =
                new PracticeMaterialReferenceService(
                        referenceRepository, assetRepository);
        LecturerAsset first = activeAsset(7L);
        LecturerAsset second = activeAsset(9L);
        when(referenceRepository.findByDraftId(10L)).thenReturn(List.of(
                PracticeMaterialReference.draft(9L, 10L, "OPTION_A"),
                PracticeMaterialReference.draft(7L, 10L, "GROUP_IMAGE")));
        when(assetRepository.findByIdForUpdate(7L))
                .thenReturn(Optional.of(first));
        when(assetRepository.findByIdForUpdate(9L))
                .thenReturn(Optional.of(second));

        service.promoteDraftReferences(10L, 20L, 30L);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
                referenceRepository, assetRepository);
        order.verify(referenceRepository).findByDraftId(10L);
        order.verify(assetRepository).findByIdForUpdate(7L);
        order.verify(assetRepository).findByIdForUpdate(9L);
        order.verify(referenceRepository)
                .existsByAssetIdAndPublishedVersionIdAndPlacement(
                        9L, 30L, "OPTION_A");
        order.verify(referenceRepository).save(
                any(PracticeMaterialReference.class));
    }

    @Test
    void promotionRejectsLogicallyDeletedAssetWithoutReferenceOrStateMutation() {
        PracticeMaterialReferenceRepository referenceRepository =
                mock(PracticeMaterialReferenceRepository.class);
        LecturerAssetRepository assetRepository =
                mock(LecturerAssetRepository.class);
        PracticeMaterialReferenceService service =
                new PracticeMaterialReferenceService(
                        referenceRepository, assetRepository);
        LecturerAsset archived = new LecturerAsset();
        archived.setId(7L);
        archived.setStatus("ARCHIVED");
        LocalDateTime deletedAt = LocalDateTime.now();
        archived.setDeletedAt(deletedAt);
        when(referenceRepository.findByDraftId(10L)).thenReturn(List.of(
                PracticeMaterialReference.draft(
                        7L, 10L, "GROUP_IMAGE")));
        when(assetRepository.findByIdForUpdate(7L))
                .thenReturn(Optional.of(archived));

        assertThrows(
                IllegalStateException.class,
                () -> service.promoteDraftReferences(10L, 20L, 30L));

        assertEquals("ARCHIVED", archived.getStatus());
        assertEquals(deletedAt, archived.getDeletedAt());
        verify(referenceRepository, never()).save(
                any(PracticeMaterialReference.class));
        verify(assetRepository, never()).save(archived);
    }

    private static LecturerAsset activeAsset(Long id) {
        LecturerAsset asset = new LecturerAsset();
        asset.setId(id);
        asset.setStatus("ACTIVE");
        return asset;
    }
}
