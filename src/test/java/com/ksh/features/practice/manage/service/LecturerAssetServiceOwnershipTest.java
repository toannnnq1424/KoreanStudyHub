package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.entities.PracticeAssetLifecycleTask;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeAssetLifecycleTaskRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.manage.speaking.SpeakingPromptAiContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

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

    private final LecturerAssetRepository assetRepository = mock(LecturerAssetRepository.class);
    private final PracticeDraftRepository draftRepository = mock(PracticeDraftRepository.class);
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
                assetRepository, draftRepository, storage, null, references, tasks, null);
        service.setAssetReferenceGuard(referenceGuard);
    }

    @Test
    void ownerCanLinkOwnedAssetToOwnedDraft() {
        PracticeDraft draft = new PracticeDraft("Draft", "",  "GLOBAL",
                null, "DRAFT", 7L, "{}");
        LecturerAsset asset = new LecturerAsset();
        asset.setOwnerLecturerId(7L);
        when(draftRepository.findByIdAndOwnerId(10L, 7L)).thenReturn(Optional.of(draft));
        when(assetRepository.findByIdForUpdate(20L))
                .thenReturn(Optional.of(asset));
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
        when(assetRepository.findByIdForUpdate(20L))
                .thenReturn(Optional.of(otherAsset));

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
        when(assetRepository.findByIdForUpdate(20L))
                .thenReturn(Optional.of(asset));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.promoteSessionRegionAsset(101L, 200L, 20L, 7L));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.promoteSessionRegionAsset(100L, 201L, 20L, 7L));

        verifyNoInteractions(storage);
        verify(assetRepository, never()).save(any());
    }

    @Test
    void ownerMetadataPatchLocksAndChecksAllReferencesBeforeMutation() {
        LecturerAsset asset = retainedAsset("ACTIVE");
        asset.setAssetType("AUDIO");
        asset.setTitle("Old title");
        asset.setTagsJson("[]");
        asset.setLecturerNote("Old note");
        when(assetRepository.findByIdForUpdate(20L))
                .thenReturn(Optional.of(asset));
        when(referenceGuard.isRetained(20L)).thenReturn(false);
        when(assetRepository.save(asset)).thenReturn(asset);

        LecturerAsset updated = service.updateAssetMetadata(
                20L,
                7L,
                "New title",
                "[\"speaking\"]",
                "audio",
                "New note",
                "active");

        assertEquals("New title", updated.getTitle());
        assertEquals("[\"speaking\"]", updated.getTagsJson());
        assertEquals("New note", updated.getLecturerNote());
        assertEquals("AUDIO", updated.getAssetType());
        assertEquals("ACTIVE", updated.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(updated.getUpdatedAt());
        org.mockito.InOrder exactOrder =
                org.mockito.Mockito.inOrder(
                        assetRepository, referenceGuard);
        exactOrder.verify(assetRepository).findByIdForUpdate(20L);
        exactOrder.verify(referenceGuard).isRetained(20L);
        exactOrder.verify(assetRepository).save(asset);
        verifyNoInteractions(storage);
    }

    @Test
    void retainedAssetMetadataPatchFailsClosedWithoutAnyMutation() {
        LecturerAsset asset = retainedAsset("ACTIVE");
        asset.setAssetType("AUDIO");
        asset.setTitle("Immutable title");
        asset.setTagsJson("[]");
        asset.setLecturerNote("Immutable note");
        when(assetRepository.findByIdForUpdate(20L))
                .thenReturn(Optional.of(asset));
        when(referenceGuard.isRetained(20L)).thenReturn(true);

        IllegalStateException denied = assertThrows(
                IllegalStateException.class,
                () -> service.updateAssetMetadata(
                        20L,
                        7L,
                        "Changed title",
                        "[\"changed\"]",
                        "IMAGE",
                        "Changed note",
                        "TEMPORARY"));

        org.assertj.core.api.Assertions.assertThat(denied.getMessage())
                .contains("đang được")
                .contains("gỡ đúng liên kết");
        assertEquals("Immutable title", asset.getTitle());
        assertEquals("[]", asset.getTagsJson());
        assertEquals("Immutable note", asset.getLecturerNote());
        assertEquals("AUDIO", asset.getAssetType());
        assertEquals("ACTIVE", asset.getStatus());
        assertNull(asset.getUpdatedAt());
        org.mockito.InOrder exactOrder =
                org.mockito.Mockito.inOrder(
                        assetRepository, referenceGuard);
        exactOrder.verify(assetRepository).findByIdForUpdate(20L);
        exactOrder.verify(referenceGuard).isRetained(20L);
        verify(assetRepository, never()).save(asset);
        verifyNoInteractions(storage);
    }

    @Test
    void metadataPatchWithoutCentralGuardFailsClosedWithoutMutation() {
        LecturerAssetService incompletelyConfigured =
                new LecturerAssetService(
                        assetRepository,
                        draftRepository,
                        storage,
                        null,
                        references,
                        tasks,
                        null);
        LecturerAsset asset = retainedAsset("ACTIVE");
        asset.setAssetType("AUDIO");
        asset.setTitle("Original");
        when(assetRepository.findByIdForUpdate(20L))
                .thenReturn(Optional.of(asset));

        assertThrows(
                IllegalStateException.class,
                () -> incompletelyConfigured.updateAssetMetadata(
                        20L,
                        7L,
                        "Changed",
                        null,
                        null,
                        null,
                        null));

        assertEquals("Original", asset.getTitle());
        assertNull(asset.getUpdatedAt());
        verify(assetRepository, never()).save(asset);
        verifyNoInteractions(storage);
    }

    @Test
    void knowingAssetIdCannotPatchAnotherOwnersAssetOrProbeReferences() {
        LecturerAsset asset = retainedAsset("ACTIVE");
        asset.setAssetType("AUDIO");
        asset.setTitle("Owner title");
        when(assetRepository.findByIdForUpdate(20L))
                .thenReturn(Optional.of(asset));

        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> service.updateAssetMetadata(
                        20L,
                        8L,
                        "Attacker title",
                        null,
                        null,
                        null,
                        null));

        assertEquals("Owner title", asset.getTitle());
        assertNull(asset.getUpdatedAt());
        verifyNoInteractions(referenceGuard);
        verify(assetRepository, never()).save(asset);
        verifyNoInteractions(storage);
    }

    @Test
    void genericPatchCannotChangeVerifiedTypeOrLifecycleStatus() {
        LecturerAsset asset = retainedAsset("ACTIVE");
        asset.setAssetType("AUDIO");
        when(assetRepository.findByIdForUpdate(20L))
                .thenReturn(Optional.of(asset));
        when(referenceGuard.isRetained(20L)).thenReturn(false);

        IllegalArgumentException typeDenied = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateAssetMetadata(
                        20L, 7L, null, null, "IMAGE", null, null));
        IllegalArgumentException statusDenied = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateAssetMetadata(
                        20L, 7L, null, null, null, null, "TEMPORARY"));

        org.assertj.core.api.Assertions.assertThat(typeDenied.getMessage())
                .contains("nội dung đã xác minh")
                .contains("không thể đổi");
        org.assertj.core.api.Assertions.assertThat(statusDenied.getMessage())
                .contains("promote")
                .contains("xóa chuyên biệt");
        assertEquals("AUDIO", asset.getAssetType());
        assertEquals("ACTIVE", asset.getStatus());
        assertNull(asset.getUpdatedAt());
        verify(assetRepository, never()).save(asset);
        verifyNoInteractions(storage);
    }

    @Test
    void retainedAssetDeleteIsDeniedAfterAssetLockWithoutLogicalMutation() {
        LecturerAsset asset = retainedAsset("ACTIVE");
        when(assetRepository.findByIdForUpdate(20L))
                .thenReturn(Optional.of(asset));
        when(referenceGuard.isRetained(20L)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> service.deleteAsset(20L, 7L));

        assertEquals("ACTIVE", asset.getStatus());
        assertNull(asset.getDeletedAt());
        org.mockito.InOrder lockOrder =
                org.mockito.Mockito.inOrder(assetRepository, referenceGuard);
        lockOrder.verify(assetRepository).findByIdForUpdate(20L);
        lockOrder.verify(referenceGuard).isRetained(20L);
        verify(assetRepository, never()).save(asset);
        verify(tasks, never()).save(any(PracticeAssetLifecycleTask.class));
        verifyNoInteractions(storage);
    }

    @Test
    void missingCentralGuardFailsClosedWithoutLogicalMutation() {
        LecturerAssetService incompletelyConfigured =
                new LecturerAssetService(
                        assetRepository,
                        draftRepository,
                        storage,
                        null,
                        references,
                        tasks,
                        null);
        LecturerAsset asset = retainedAsset("ACTIVE");
        when(assetRepository.findByIdForUpdate(20L))
                .thenReturn(Optional.of(asset));

        assertThrows(IllegalStateException.class,
                () -> incompletelyConfigured.deleteAsset(20L, 7L));

        assertEquals("ACTIVE", asset.getStatus());
        assertNull(asset.getDeletedAt());
        verify(assetRepository, never()).save(asset);
        verify(tasks, never()).save(any(PracticeAssetLifecycleTask.class));
        verifyNoInteractions(storage);
    }

    @Test
    void temporaryCleanupPreservesRetainedAssetUnchangedAfterExactLock() {
        LecturerAsset asset = new LecturerAsset();
        asset.setId(20L);
        asset.setOwnerLecturerId(7L);
        asset.setSourceImportSessionId(100L);
        asset.setStorageKey("private/source.png");
        asset.setStatus("TEMPORARY");
        when(assetRepository
                .findIdsBySourceImportSessionIdAndOwnerLecturerId(100L, 7L))
                .thenReturn(List.of(20L));
        when(assetRepository.findByIdForUpdate(20L))
                .thenReturn(Optional.of(asset));
        when(referenceGuard.isRetained(20L)).thenReturn(true);

        service.cleanupTemporaryAssets(100L, 7L);

        assertEquals("TEMPORARY", asset.getStatus());
        assertNull(asset.getDeletedAt());
        org.mockito.InOrder lockOrder =
                org.mockito.Mockito.inOrder(assetRepository, referenceGuard);
        lockOrder.verify(assetRepository)
                .findIdsBySourceImportSessionIdAndOwnerLecturerId(100L, 7L);
        lockOrder.verify(assetRepository).findByIdForUpdate(20L);
        lockOrder.verify(referenceGuard).isRetained(20L);
        verify(assetRepository, never()).save(asset);
        verify(tasks, never()).save(any(PracticeAssetLifecycleTask.class));
        verifyNoInteractions(storage);
    }

    @Test
    void unreferencedTemporaryCleanupQueuesOnlyAfterExactLockedDecision() {
        LecturerAsset asset = retainedAsset("TEMPORARY");
        asset.setSourceImportSessionId(100L);
        when(assetRepository
                .findIdsBySourceImportSessionIdAndOwnerLecturerId(100L, 7L))
                .thenReturn(List.of(20L));
        when(assetRepository.findByIdForUpdate(20L))
                .thenReturn(Optional.of(asset));

        service.cleanupTemporaryAssets(100L, 7L);

        assertEquals("DELETION_PENDING", asset.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(asset.getDeletedAt());
        org.mockito.InOrder lockOrder =
                org.mockito.Mockito.inOrder(
                        assetRepository, referenceGuard, tasks);
        lockOrder.verify(assetRepository)
                .findIdsBySourceImportSessionIdAndOwnerLecturerId(100L, 7L);
        lockOrder.verify(assetRepository).findByIdForUpdate(20L);
        lockOrder.verify(referenceGuard).isRetained(20L);
        lockOrder.verify(assetRepository).save(asset);
        lockOrder.verify(tasks).save(
                org.mockito.ArgumentMatchers.any(
                        PracticeAssetLifecycleTask.class));
        verifyNoInteractions(storage);
    }

    @Test
    void transactionalUploadAndQueueMethodsNeverDeleteStorageInline()
            throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Path.of(
                        "src/main/java/com/ksh/features/practice/manage/service/"
                                + "LecturerAssetService.java"));
        String uploadMethods = source.substring(
                source.indexOf("public LecturerAsset createTemporaryAsset("),
                source.indexOf("public LecturerAsset promoteToActiveLibrary("));
        String enqueue = source.substring(
                source.indexOf("private void enqueueLifecycle("),
                source.indexOf("private void registerRollbackCleanup("));

        org.assertj.core.api.Assertions.assertThat(uploadMethods)
                .doesNotContain("assetStorage.delete(")
                .contains("enqueueLifecycle(");
        org.assertj.core.api.Assertions.assertThat(enqueue)
                .doesNotContain("assetStorage.delete(")
                .contains("registerCompletionCleanup(");
    }

    @Test
    void runningCleanupForFreshStorageKeyBlocksRegistrationRegardlessOfLeaseAge()
            throws Exception {
        byte[] bytes = "same-private-object".getBytes(
                java.nio.charset.StandardCharsets.UTF_8);
        String hash = SpeakingPromptAiContract.exactBytesSha256(bytes);
        when(storage.store(any(), any(), any()))
                .thenAnswer(invocation -> {
                    String namespace = invocation.getArgument(2);
                    return new AssetStorageService.StoredAsset(
                            namespace + "/" + hash + ".mp3",
                            bytes.length,
                            hash,
                            true);
                });
        when(assetRepository
                .findByOwnerLecturerIdAndSha256AndStatusAndDeletedAtIsNull(
                        7L, hash, "ACTIVE"))
                .thenReturn(List.of());
        when(tasks.findActiveBySourceStorageKeyForUpdate(any()))
                .thenAnswer(invocation -> {
                    PracticeAssetLifecycleTask running =
                            new PracticeAssetLifecycleTask(
                                    null,
                                    PracticeAssetLifecycleTask.ORPHAN_RECONCILE,
                                    invocation.getArgument(0),
                                    null);
                    running.markRunning(
                            "claim-1",
                            java.time.LocalDateTime.now().minusMinutes(1));
                    return List.of(running);
                });

        assertThrows(
                IllegalStateException.class,
                () -> service.createTemporaryAsset(
                        7L,
                        100L,
                        200L,
                        new java.io.ByteArrayInputStream(bytes),
                        "audio.mp3",
                        "audio/mpeg",
                        null,
                        null,
                        (long) bytes.length,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));

        verify(assetRepository, never()).save(
                org.mockito.ArgumentMatchers.any(LecturerAsset.class));
    }

    @Test
    void freshUploadNamespaceCannotReuseAnOldWorkersExactCleanupKey()
            throws Exception {
        byte[] bytes = "same-private-object".getBytes(
                java.nio.charset.StandardCharsets.UTF_8);
        String hash = SpeakingPromptAiContract.exactBytesSha256(bytes);
        String oldWorkerKey = "lecturer-assets/7/imports/100/temporary/"
                + hash + ".mp3";
        when(storage.store(any(), any(), any()))
                .thenAnswer(invocation -> {
                    String namespace = invocation.getArgument(2);
                    return new AssetStorageService.StoredAsset(
                            namespace + "/" + hash + ".mp3",
                            bytes.length,
                            hash,
                            true);
                });
        when(assetRepository
                .findByOwnerLecturerIdAndSha256AndStatusAndDeletedAtIsNull(
                        7L, hash, "ACTIVE"))
                .thenReturn(List.of());
        when(assetRepository.save(any(LecturerAsset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LecturerAsset created = service.createTemporaryAsset(
                7L,
                100L,
                200L,
                new java.io.ByteArrayInputStream(bytes),
                "audio.mp3",
                "audio/mpeg",
                null,
                null,
                (long) bytes.length,
                null,
                null,
                null,
                null,
                null,
                null);

        org.assertj.core.api.Assertions.assertThat(created.getStorageKey())
                .contains("/temporary/objects/")
                .isNotEqualTo(oldWorkerKey);
        verify(tasks).findActiveBySourceStorageKeyForUpdate(
                created.getStorageKey());
    }

    @Test
    void draftUploadUsesFreshKeyAndLeavesOnlyRetentionBoundUnboundAsset()
            throws Exception {
        byte[] bytes = "ID3-private-audio".getBytes(
                java.nio.charset.StandardCharsets.UTF_8);
        String hash = SpeakingPromptAiContract.exactBytesSha256(bytes);
        PracticeDraft draft = new PracticeDraft(
                "Draft", "", "GLOBAL", null, "DRAFT", 7L, "{}");
        org.springframework.web.multipart.MultipartFile file =
                mock(org.springframework.web.multipart.MultipartFile.class);
        when(draftRepository.findByIdAndOwnerId(10L, 7L))
                .thenReturn(Optional.of(draft));
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) bytes.length);
        when(file.getBytes()).thenReturn(bytes);
        when(file.getOriginalFilename()).thenReturn("original.mp3");
        when(storage.store(any(), any(), any()))
                .thenAnswer(invocation -> {
                    String namespace = invocation.getArgument(2);
                    return new AssetStorageService.StoredAsset(
                            namespace + "/" + hash + ".mp3",
                            bytes.length,
                            hash,
                            true);
                });
        when(assetRepository
                .findByOwnerLecturerIdAndSha256AndStatusAndDeletedAtIsNull(
                        7L, hash, "ACTIVE"))
                .thenReturn(List.of());
        when(assetRepository.save(any(LecturerAsset.class)))
                .thenAnswer(invocation -> {
                    LecturerAsset value = invocation.getArgument(0);
                    value.setId(40L);
                    return value;
                });

        LecturerAsset created = service.createUnboundDraftUploadAsset(
                10L,
                7L,
                file,
                "AUDIO",
                1_024L);

        org.assertj.core.api.Assertions.assertThat(created.getStorageKey())
                .contains("/drafts/10/private/audio/objects/");
        assertEquals("TEMPORARY", created.getStatus());
        assertEquals("PRIVATE", created.getVisibility());
        assertEquals("MANUAL_UPLOAD", created.getSourceType());
        org.junit.jupiter.api.Assertions.assertNotNull(
                created.getRetentionUntil());
        verify(tasks).findActiveBySourceStorageKeyForUpdate(
                created.getStorageKey());
        verify(references, never()).linkDraft(
                any(), any(), any(), any(), any());
    }

    @Test
    void generatedCandidateCleanupChoicePermanentlyClosesRegistration()
            throws Exception {
        byte[] bytes = "generated-private-audio".getBytes(
                java.nio.charset.StandardCharsets.UTF_8);
        String hash = SpeakingPromptAiContract.exactBytesSha256(bytes);
        PracticeDraft draft = new PracticeDraft(
                "Draft", "", "GLOBAL", null, "DRAFT", 7L, "{}");
        when(draftRepository.findByIdAndOwnerId(10L, 7L))
                .thenReturn(Optional.of(draft));
        when(storage.store(any(), any(), any()))
                .thenAnswer(invocation -> {
                    String namespace = invocation.getArgument(2);
                    return new AssetStorageService.StoredAsset(
                            namespace + "/" + hash + ".mp3",
                            bytes.length,
                            hash,
                            true);
                });
        java.util.concurrent.atomic.AtomicReference<LecturerAsset> staged =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(assetRepository.save(any(LecturerAsset.class)))
                .thenAnswer(invocation -> {
                    LecturerAsset value = invocation.getArgument(0);
                    if (value.getId() == null) {
                        value.setId(30L);
                    }
                    staged.set(value);
                    return value;
                });
        LecturerAssetService.GeneratedAudioCandidate candidate =
                service.storeGeneratedDraftAudio(
                        7L,
                        10L,
                        bytes,
                        "generated.mp3",
                        "audio/mpeg",
                        hash,
                        "AI_TTS");
        when(assetRepository.findByIdForUpdate(30L))
                .thenAnswer(invocation -> Optional.of(staged.get()));

        org.assertj.core.api.Assertions.assertThat(staged.get())
                .satisfies(value -> {
                    assertEquals("TEMPORARY", value.getStatus());
                    assertEquals("PRIVATE", value.getVisibility());
                    assertEquals("AI_TTS", value.getSourceType());
                    org.junit.jupiter.api.Assertions.assertNotNull(
                            value.getRetentionUntil());
                    assertNull(value.getDeletedAt());
                });

        service.discardGeneratedDraftAudio(candidate);

        assertThrows(
                IllegalStateException.class,
                () -> service.registerGeneratedDraftAudio(
                        candidate,
                        "Generated",
                        "SPEAKING_PROMPT_GENERATED",
                        "question-1"));
        assertEquals("DELETION_PENDING", staged.get().getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(
                staged.get().getDeletedAt());
        verify(tasks).save(
                org.mockito.ArgumentMatchers.argThat(task ->
                        PracticeAssetLifecycleTask.DELETE.equals(
                                task.getOperation())
                                && task.getSourceStorageKey().contains(
                                        "/generated-audio/objects/")));
        verify(assetRepository, org.mockito.Mockito.times(2))
                .save(staged.get());
        verify(references, never()).linkDraft(
                any(), any(), any(), any(), any());
        verify(storage, never()).delete(any());
    }

    @Test
    void generatedCandidateRegistrationLocksExactStagingAndMakesItActive()
            throws Exception {
        byte[] bytes = "generated-private-audio".getBytes(
                java.nio.charset.StandardCharsets.UTF_8);
        String hash = SpeakingPromptAiContract.exactBytesSha256(bytes);
        PracticeDraft draft = new PracticeDraft(
                "Draft", "", "GLOBAL", null, "DRAFT", 7L, "{}");
        when(draftRepository.findByIdAndOwnerId(10L, 7L))
                .thenReturn(Optional.of(draft));
        when(storage.store(any(), any(), any()))
                .thenAnswer(invocation -> {
                    String namespace = invocation.getArgument(2);
                    return new AssetStorageService.StoredAsset(
                            namespace + "/" + hash + ".mp3",
                            bytes.length,
                            hash,
                            true);
                });
        java.util.concurrent.atomic.AtomicReference<LecturerAsset> staged =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(assetRepository.save(any(LecturerAsset.class)))
                .thenAnswer(invocation -> {
                    LecturerAsset value = invocation.getArgument(0);
                    if (value.getId() == null) {
                        value.setId(31L);
                    }
                    staged.set(value);
                    return value;
                });
        LecturerAssetService.GeneratedAudioCandidate candidate =
                service.storeGeneratedDraftAudio(
                        7L,
                        10L,
                        bytes,
                        "generated.mp3",
                        "audio/mpeg",
                        hash,
                        "AI_TTS");
        when(assetRepository.findByIdForUpdate(31L))
                .thenAnswer(invocation -> Optional.of(staged.get()));
        when(assetRepository
                .findByOwnerLecturerIdAndSha256AndStatusAndDeletedAtIsNull(
                        7L, hash, "ACTIVE"))
                .thenReturn(List.of());

        LecturerAsset registered = service.registerGeneratedDraftAudio(
                candidate,
                "Generated",
                "SPEAKING_PROMPT_GENERATED",
                "question-1");
        service.discardGeneratedDraftAudio(candidate);

        assertEquals(31L, registered.getId());
        assertEquals("ACTIVE", registered.getStatus());
        assertEquals("AI_TTS", registered.getSourceType());
        assertNull(registered.getRetentionUntil());
        assertNull(registered.getDeletedAt());
        org.mockito.InOrder exactOrder = org.mockito.Mockito.inOrder(
                draftRepository,
                assetRepository,
                referenceGuard,
                references);
        exactOrder.verify(
                draftRepository,
                org.mockito.Mockito.times(2))
                .findByIdAndOwnerId(10L, 7L);
        exactOrder.verify(assetRepository).findByIdForUpdate(31L);
        exactOrder.verify(referenceGuard).isRetained(31L);
        exactOrder.verify(references).linkDraft(
                10L,
                31L,
                "SPEAKING_PROMPT_GENERATED",
                "question-1",
                null);
        verify(tasks, never()).save(
                any(PracticeAssetLifecycleTask.class));
        verify(storage, never()).delete(any());
    }

    private static LecturerAsset retainedAsset(String status) {
        LecturerAsset asset = new LecturerAsset();
        asset.setId(20L);
        asset.setOwnerLecturerId(7L);
        asset.setStorageKey("private/source.mp3");
        asset.setStatus(status);
        return asset;
    }
}
