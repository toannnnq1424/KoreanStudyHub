package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeAuthoringCollaboration;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeAuthoringCollaborationRepository;
import com.ksh.features.practice.repository.PracticeMaterialReferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeMaterialLibraryServiceTest {

    private final LecturerAssetRepository assetRepository = mock(LecturerAssetRepository.class);
    private final PracticeAuthoringCollaborationRepository collaborationRepository =
            mock(PracticeAuthoringCollaborationRepository.class);
    private final PracticeMaterialReferenceRepository referenceRepository =
            mock(PracticeMaterialReferenceRepository.class);
    private final PracticeAuthorizationService authorizationService =
            mock(PracticeAuthorizationService.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private PracticeMaterialLibraryService service;

    @BeforeEach
    void setUp() {
        service = new PracticeMaterialLibraryService(
                assetRepository, collaborationRepository, referenceRepository,
                authorizationService, userRepository);
        when(collaborationRepository
                .findByCollaboratorIdAndRevokedAtIsNullOrderByGrantedAtDesc(
                        any(Long.class), any(Pageable.class)))
                .thenReturn(List.of());
    }

    @Test
    void ownedCatalogUsesAuthorizedContentUrlAndSafeReferenceSummary() {
        LecturerAsset asset = asset(10L, 7L, "ACTIVE", true);
        PracticeMaterialReference reference =
                PracticeMaterialReference.draft(10L, 20L, "GROUP_AUDIO");
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(7L);
        when(owner.getFullName()).thenReturn("Giảng viên A");
        when(assetRepository.findByOwnerLecturerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
                any(Long.class), any(Pageable.class))).thenReturn(List.of(asset));
        when(referenceRepository.findByAssetId(10L)).thenReturn(List.of(reference));
        when(userRepository.findAllById(any())).thenReturn(List.of(owner));

        PracticeMaterialLibraryService.Catalog catalog = service.catalog(7L);

        assertEquals(1, catalog.mine().size());
        PracticeMaterialLibraryService.MaterialView view = catalog.mine().get(0);
        assertEquals("/practice/materials/10/content", view.contentUrl());
        assertEquals(List.of("GROUP_AUDIO"), view.placements());
        assertEquals(List.of("DRAFT"), view.scopes());
        assertEquals("Giảng viên A", view.ownerName());
        assertEquals(PracticeMaterialLibraryService.VIEW_LIMIT, catalog.limit());
        verify(authorizationService).requireGlobal(7L, PracticeAction.READ);
    }

    @Test
    void collaboratorCatalogIncludesReferencedRetainedAssetThroughSafeEndpoint() {
        LecturerAsset shared = asset(30L, 9L, "ARCHIVED", true);
        PracticeAuthoringCollaboration grant = new PracticeAuthoringCollaboration(
                40L, 7L);
        PracticeMaterialReference reference =
                PracticeMaterialReference.published(30L, 40L, 50L, "GROUP_IMAGE");
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(9L);
        when(owner.getFullName()).thenReturn("Giảng viên B");
        when(assetRepository.findByOwnerLecturerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
                any(Long.class), any(Pageable.class))).thenReturn(List.of());
        when(collaborationRepository
                .findByCollaboratorIdAndRevokedAtIsNullOrderByGrantedAtDesc(
                        any(Long.class), any(Pageable.class)))
                .thenReturn(List.of(grant));
        when(referenceRepository.findBySetId(40L)).thenReturn(List.of(reference));
        when(assetRepository.findAllById(List.of(30L))).thenReturn(List.of(shared));
        when(referenceRepository.findByAssetId(30L)).thenReturn(List.of(reference));
        when(userRepository.findAllById(any())).thenReturn(List.of(owner));

        PracticeMaterialLibraryService.Catalog catalog = service.catalog(7L);

        assertEquals(1, catalog.shared().size());
        PracticeMaterialLibraryService.MaterialView view = catalog.shared().get(0);
        assertEquals("Giảng viên B", view.ownerName());
        assertEquals(List.of("PUBLISHED_VERSION"), view.scopes());
        assertTrue(view.referenceCount() > 0);
        assertEquals("/practice/materials/30/content", view.contentUrl());
    }

    private static LecturerAsset asset(Long id, Long ownerId, String status,
                                        boolean verified) {
        LecturerAsset asset = new LecturerAsset();
        asset.setId(id);
        asset.setOwnerLecturerId(ownerId);
        asset.setTitle("Audio mẫu");
        asset.setOriginalFilename("audio.mp3");
        asset.setMimeType("audio/mpeg");
        asset.setFileSize(1024L);
        asset.setAssetType("AUDIO");
        asset.setStatus(status);
        asset.setVisibility("PRIVATE");
        asset.setContentVerified(verified);
        return asset;
    }
}
