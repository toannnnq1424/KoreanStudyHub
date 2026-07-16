package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeSet;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticePublishedVersionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeMaterialAccessServiceTest {

    private final LecturerAssetRepository assetRepository = mock(LecturerAssetRepository.class);
    private final PracticeMaterialReferenceService referenceService =
            mock(PracticeMaterialReferenceService.class);
    private final PracticeSetRepository setRepository = mock(PracticeSetRepository.class);
    private final PracticeAttemptRepository attemptRepository = mock(PracticeAttemptRepository.class);
    private final PracticePublishedVersionRepository publishedVersionRepository =
            mock(PracticePublishedVersionRepository.class);
    private final EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
    private final PracticeAuthorizationService authorizationService =
            mock(PracticeAuthorizationService.class);
    private final AssetStorageService storageService = mock(AssetStorageService.class);

    private PracticeMaterialAccessService service;

    @BeforeEach
    void setUp() {
        service = new PracticeMaterialAccessService(
                assetRepository, referenceService, setRepository,
                attemptRepository, publishedVersionRepository, enrollmentRepository, authorizationService,
                storageService);
    }

    @Test
    void anonymousActorFailsClosedBeforeAssetLookup() {
        assertThrows(AccessDeniedException.class, () -> service.load(1L, null));
        verify(assetRepository, never()).findById(1L);
    }

    @Test
    void unverifiedAssetIsNeverDelivered() throws Exception {
        LecturerAsset asset = asset(1L, 11L);
        asset.setContentVerified(false);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));

        assertThrows(AccessDeniedException.class, () -> service.load(1L, 11L));
        verify(storageService, never()).load(anyString());
    }

    @Test
    void ownerCanReadVerifiedPrivateAsset() throws Exception {
        LecturerAsset asset = asset(1L, 11L);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(storageService.load("private/key.mp3"))
                .thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));

        PracticeMaterialAccessService.MaterialContent content = service.load(1L, 11L);

        assertEquals("audio/mpeg", content.mimeType());
        assertEquals(3L, content.sizeBytes());
    }

    @Test
    void draftCollaboratorCanReadThroughAuthorizedReference() throws Exception {
        LecturerAsset asset = asset(1L, 11L);
        PracticeMaterialReference reference =
                PracticeMaterialReference.draft(1L, 20L, "GROUP_AUDIO");
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(referenceService.references(1L)).thenReturn(List.of(reference));
        when(authorizationService.requireDraft(20L, 22L, PracticeAction.READ))
                .thenReturn(new PracticeAuthorizationService.Decision(11L, false));
        when(storageService.load("private/key.mp3"))
                .thenReturn(new ByteArrayResource(new byte[]{1}));

        service.load(1L, 22L);

        verify(storageService).load("private/key.mp3");
    }

    @Test
    void currentGlobalPublishedReferenceIsReadableByAuthenticatedLearner() throws Exception {
        LecturerAsset asset = asset(1L, 11L);
        PracticeMaterialReference reference =
                PracticeMaterialReference.published(1L, 44L, 55L, "GROUP_AUDIO");
        PracticeSet set = new PracticeSet(
                "Set", "", "LISTENING",  PracticeSet.SCOPE_GLOBAL,
                null, null, "{}", PracticeSet.STATUS_PUBLISHED, 11L);
        PracticePublishedVersion currentVersion = publishedVersion(55L);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(referenceService.references(1L)).thenReturn(List.of(reference));
        when(setRepository.findById(44L)).thenReturn(Optional.of(set));
        when(authorizationService.requireSet(44L, 99L, PracticeAction.READ))
                .thenThrow(new AccessDeniedException("learner"));
        when(publishedVersionRepository.findFirstBySetIdAndStatusOrderByVersionNumberDesc(
                44L, PracticePublishedVersion.STATUS_PUBLISHED))
                .thenReturn(Optional.of(currentVersion));
        when(storageService.load("private/key.mp3"))
                .thenReturn(new ByteArrayResource(new byte[]{1}));

        service.load(1L, 99L);

        verify(storageService).load("private/key.mp3");
    }

    @Test
    void historicalGlobalReferenceIsDeniedWithoutMatchingAttempt() throws Exception {
        LecturerAsset asset = asset(1L, 11L);
        PracticeMaterialReference reference =
                PracticeMaterialReference.published(1L, 44L, 55L, "GROUP_AUDIO");
        PracticeSet set = new PracticeSet(
                "Set", "", "LISTENING",  PracticeSet.SCOPE_GLOBAL,
                null, null, "{}", PracticeSet.STATUS_PUBLISHED, 11L);
        PracticePublishedVersion currentVersion = publishedVersion(66L);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(referenceService.references(1L)).thenReturn(List.of(reference));
        when(setRepository.findById(44L)).thenReturn(Optional.of(set));
        when(authorizationService.requireSet(44L, 99L, PracticeAction.READ))
                .thenThrow(new AccessDeniedException("learner"));
        when(publishedVersionRepository.findFirstBySetIdAndStatusOrderByVersionNumberDesc(
                44L, PracticePublishedVersion.STATUS_PUBLISHED))
                .thenReturn(Optional.of(currentVersion));

        assertThrows(AccessDeniedException.class, () -> service.load(1L, 99L));

        verify(storageService, never()).load(anyString());
    }

    @Test
    void collaboratorCanReadHistoricalReferenceThroughSetAuthorization() throws Exception {
        LecturerAsset asset = asset(1L, 11L);
        PracticeMaterialReference reference =
                PracticeMaterialReference.published(1L, 44L, 55L, "GROUP_AUDIO");
        PracticeSet set = new PracticeSet(
                "Set", "", "LISTENING",  PracticeSet.SCOPE_GLOBAL,
                null, null, "{}", PracticeSet.STATUS_PUBLISHED, 11L);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(referenceService.references(1L)).thenReturn(List.of(reference));
        when(setRepository.findById(44L)).thenReturn(Optional.of(set));
        when(authorizationService.requireSet(44L, 22L, PracticeAction.READ))
                .thenReturn(new PracticeAuthorizationService.Decision(11L, false));
        when(storageService.load("private/key.mp3"))
                .thenReturn(new ByteArrayResource(new byte[]{1}));

        service.load(1L, 22L);

        verify(storageService).load("private/key.mp3");
        verify(publishedVersionRepository, never())
                .findFirstBySetIdAndStatusOrderByVersionNumberDesc(
                        44L, PracticePublishedVersion.STATUS_PUBLISHED);
    }

    @Test
    void archivedPublishedVersionRemainsReadableToLearnerWithAttempt() throws Exception {
        LecturerAsset asset = asset(1L, 11L);
        PracticeMaterialReference reference =
                PracticeMaterialReference.published(1L, 44L, 55L, "GROUP_AUDIO");
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(referenceService.references(1L)).thenReturn(List.of(reference));
        when(attemptRepository.existsByPublishedVersionIdAndUserId(55L, 99L))
                .thenReturn(true);
        when(storageService.load("private/key.mp3"))
                .thenReturn(new ByteArrayResource(new byte[]{1}));

        service.load(1L, 99L);

        verify(storageService).load("private/key.mp3");
        verify(setRepository, never()).findById(44L);
    }

    @Test
    void explanationWorkerCanReadOnlyAssetBoundToItsImmutablePublishedVersion() throws Exception {
        LecturerAsset asset = asset(1L, 11L);
        asset.setStatus("ARCHIVED");
        when(referenceService.hasPublishedVersionReference(1L, 55L)).thenReturn(true);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(storageService.load("private/key.mp3"))
                .thenReturn(new ByteArrayResource(new byte[]{1}));

        service.loadForPublishedVersion(1L, 55L);

        verify(storageService).load("private/key.mp3");
    }

    @Test
    void explanationWorkerCannotReadAssetFromAnotherPublishedVersion() throws Exception {
        when(referenceService.hasPublishedVersionReference(1L, 66L)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> service.loadForPublishedVersion(1L, 66L));

        verify(assetRepository, never()).findById(1L);
        verify(storageService, never()).load(anyString());
    }

    @Test
    void unrelatedActorCannotReadPrivateDraftAsset() throws Exception {
        LecturerAsset asset = asset(1L, 11L);
        PracticeMaterialReference reference =
                PracticeMaterialReference.draft(1L, 20L, "GROUP_AUDIO");
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(referenceService.references(1L)).thenReturn(List.of(reference));
        when(authorizationService.requireDraft(20L, 99L, PracticeAction.READ))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class, () -> service.load(1L, 99L));
        verify(storageService, never()).load(anyString());
    }

    @Test
    void unrelatedReviewerRoleHasNoSpecialMaterialAccess() throws Exception {
        LecturerAsset asset = asset(1L, 11L);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(referenceService.references(1L)).thenReturn(List.of());
        assertThrows(AccessDeniedException.class, () -> service.load(1L, 33L));
        verify(storageService, never()).load(anyString());
    }

    @Test
    void pendingDeletionIsNotDeliveredEvenToOwner() throws Exception {
        LecturerAsset asset = asset(1L, 11L);
        asset.setStatus("DELETION_PENDING");
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));

        assertThrows(EntityNotFoundException.class, () -> service.load(1L, 11L));
        verify(storageService, never()).load(anyString());
    }

    @Test
    void deletedAssetIsNotDeliveredEvenIfStorageStillExists() throws Exception {
        LecturerAsset asset = asset(1L, 11L);
        asset.setStatus("DELETED");
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));

        assertThrows(EntityNotFoundException.class, () -> service.load(1L, 11L));
        verify(storageService, never()).load(anyString());
    }

    private static LecturerAsset asset(Long id, Long ownerId) {
        LecturerAsset asset = new LecturerAsset();
        asset.setId(id);
        asset.setOwnerLecturerId(ownerId);
        asset.setStorageProvider("LOCAL");
        asset.setStorageKey("private/key.mp3");
        asset.setOriginalFilename("listen.mp3");
        asset.setMimeType("audio/mpeg");
        asset.setFileSize(3L);
        asset.setAssetType("AUDIO");
        asset.setStatus("ACTIVE");
        asset.setContentVerified(true);
        return asset;
    }

    private static PracticePublishedVersion publishedVersion(Long id) {
        PracticePublishedVersion version = mock(PracticePublishedVersion.class);
        when(version.getId()).thenReturn(id);
        return version;
    }
}
