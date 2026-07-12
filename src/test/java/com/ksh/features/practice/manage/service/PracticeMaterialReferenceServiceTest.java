package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeDraftAssetUsageRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeMaterialReferenceServiceTest {

    @Test
    void promotionPreservesEveryPlacementForSharedAsset() {
        PracticeMaterialReferenceRepository referenceRepository =
                mock(PracticeMaterialReferenceRepository.class);
        PracticeDraftAssetUsageRepository usageRepository =
                mock(PracticeDraftAssetUsageRepository.class);
        LecturerAssetRepository assetRepository = mock(LecturerAssetRepository.class);
        PracticeMaterialReferenceService service = new PracticeMaterialReferenceService(
                referenceRepository, usageRepository, assetRepository);
        LecturerAsset asset = new LecturerAsset();
        asset.setId(7L);
        asset.setStatus("ARCHIVED");
        asset.setDeletedAt(LocalDateTime.now());
        asset.setRetentionUntil(LocalDateTime.now().plusDays(1));
        when(referenceRepository.findByDraftId(10L)).thenReturn(List.of(
                PracticeMaterialReference.draft(7L, 10L, "GROUP_IMAGE"),
                PracticeMaterialReference.draft(7L, 10L, "OPTION_A")));
        when(usageRepository.findByDraftId(10L)).thenReturn(List.of());
        when(assetRepository.findById(7L)).thenReturn(Optional.of(asset));
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
}
