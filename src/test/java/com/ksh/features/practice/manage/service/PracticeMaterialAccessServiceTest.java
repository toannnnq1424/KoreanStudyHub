package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.entities.PracticeSet;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.governance.PracticeGovernanceAuditService;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeDraftAssetUsageRepository;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeMaterialAccessServiceTest {

    private final LecturerAssetRepository assetRepository = mock(LecturerAssetRepository.class);
    private final PracticeMaterialReferenceService referenceService =
            mock(PracticeMaterialReferenceService.class);
    private final PracticeDraftAssetUsageRepository usageRepository =
            mock(PracticeDraftAssetUsageRepository.class);
    private final PracticeSetRepository setRepository = mock(PracticeSetRepository.class);
    private final PracticeAttemptRepository attemptRepository = mock(PracticeAttemptRepository.class);
    private final EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
    private final PracticeAuthorizationService authorizationService =
            mock(PracticeAuthorizationService.class);
    private final PracticeGovernanceAuditService auditService =
            mock(PracticeGovernanceAuditService.class);
    private final AssetStorageService storageService = mock(AssetStorageService.class);

    private PracticeMaterialAccessService service;

    @BeforeEach
    void setUp() {
        service = new PracticeMaterialAccessService(
                assetRepository, referenceService, usageRepository, setRepository,
                attemptRepository, enrollmentRepository, authorizationService,
                auditService, storageService);
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
        when(authorizationService.requireDraft(20L, 22L, PracticeAction.READ, null))
                .thenReturn(new PracticeAuthorizationService.Decision(11L, false, false, true));
        when(storageService.load("private/key.mp3"))
                .thenReturn(new ByteArrayResource(new byte[]{1}));

        service.load(1L, 22L);

        verify(storageService).load("private/key.mp3");
    }

    @Test
    void globalPublishedReferenceIsReadableByAuthenticatedLearner() throws Exception {
        LecturerAsset asset = asset(1L, 11L);
        PracticeMaterialReference reference =
                PracticeMaterialReference.published(1L, 44L, 55L, "GROUP_AUDIO");
        PracticeSet set = new PracticeSet(
                "Set", "", "LISTENING", "TOPIK_II", PracticeSet.SCOPE_GLOBAL,
                null, null, "{}", PracticeSet.STATUS_PUBLISHED, 11L);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(referenceService.references(1L)).thenReturn(List.of(reference));
        when(setRepository.findById(44L)).thenReturn(Optional.of(set));
        when(storageService.load("private/key.mp3"))
                .thenReturn(new ByteArrayResource(new byte[]{1}));

        service.load(1L, 99L);

        verify(storageService).load("private/key.mp3");
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
    void unrelatedActorCannotReadPrivateDraftAsset() throws Exception {
        LecturerAsset asset = asset(1L, 11L);
        PracticeMaterialReference reference =
                PracticeMaterialReference.draft(1L, 20L, "GROUP_AUDIO");
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(referenceService.references(1L)).thenReturn(List.of(reference));
        when(authorizationService.requireDraft(20L, 99L, PracticeAction.READ, null))
                .thenThrow(new AccessDeniedException("denied"));
        when(authorizationService.hasPermission(99L, PracticeAction.MEDIA_REVIEW))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.load(1L, 99L));
        verify(storageService, never()).load(anyString());
    }

    @Test
    void reviewerAccessIsAuditedWithoutStorageKeyDisclosure() throws Exception {
        LecturerAsset asset = asset(1L, 11L);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(referenceService.references(1L)).thenReturn(List.of());
        when(usageRepository.findByAssetId(1L)).thenReturn(List.of());
        when(authorizationService.hasPermission(33L, PracticeAction.MEDIA_REVIEW))
                .thenReturn(true);
        when(storageService.load("private/key.mp3"))
                .thenReturn(new ByteArrayResource(new byte[]{1}));

        service.load(1L, 33L);

        verify(auditService).record(
                eq("MEDIA_REVIEWED"), eq("ASSET"), eq(1L), eq(11L), eq(33L),
                isNull(), eq(false), isNull(), isNull(),
                eq("{\"storageProvider\":\"LOCAL\"}"));
    }

    @Test
    void reviewerStorageFailureDoesNotWriteSuccessfulReviewAudit() throws Exception {
        LecturerAsset asset = asset(1L, 11L);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(referenceService.references(1L)).thenReturn(List.of());
        when(usageRepository.findByAssetId(1L)).thenReturn(List.of());
        when(authorizationService.hasPermission(33L, PracticeAction.MEDIA_REVIEW))
                .thenReturn(true);
        when(storageService.load("private/key.mp3"))
                .thenThrow(new IOException("missing object"));

        assertThrows(IOException.class, () -> service.load(1L, 33L));

        verify(auditService, never()).record(
                eq("MEDIA_REVIEWED"), eq("ASSET"), eq(1L), eq(11L), eq(33L),
                isNull(), eq(false), isNull(), isNull(),
                eq("{\"storageProvider\":\"LOCAL\"}"));
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
}
