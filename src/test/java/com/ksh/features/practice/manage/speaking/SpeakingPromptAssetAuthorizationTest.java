package com.ksh.features.practice.manage.speaking;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeAssetLifecycleTask;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.features.practice.manage.service.AssetStorageService;
import com.ksh.features.practice.manage.service.LecturerAssetService;
import com.ksh.features.practice.manage.service.PracticeAssetReferenceGuard;
import com.ksh.features.practice.manage.service.PracticeMaterialReferenceService;
import com.ksh.features.practice.repository.PracticeAssetLifecycleTaskRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeMaterialReferenceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeakingPromptAssetAuthorizationTest {

    @Test
    void originalAudioRequiresOwnerDraftPlacementAndExactQuestionBinding() {
        Long ownerId = 81L;
        Long draftId = 91L;
        Long assetId = 101L;
        String questionClientId = "speaking-question-a";
        byte[] bytes = "verified-private-original".getBytes(
                StandardCharsets.UTF_8);
        String hash = SpeakingPromptAiContract.exactBytesSha256(bytes);
        LecturerAsset asset = usableOriginalAsset(ownerId, assetId, hash, bytes.length);
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeMaterialReferenceRepository references =
                mock(PracticeMaterialReferenceRepository.class);
        when(assets.findByIdAndOwnerLecturerId(assetId, ownerId))
                .thenReturn(Optional.of(asset));
        when(assets.findByIdForUpdate(assetId))
                .thenReturn(Optional.of(asset));
        when(references.existsByAssetIdAndDraftIdAndPlacementAndReferenceKey(
                assetId,
                draftId,
                SpeakingPromptAssetService.ORIGINAL_PLACEMENT,
                questionClientId))
                .thenReturn(true);
        SpeakingPromptAssetService service = new SpeakingPromptAssetService(
                assets,
                references,
                mock(LecturerAssetService.class),
                mock(SpeakingPromptAudioVerifier.class),
                new SpeakingPromptAuthoringAiProperties());
        SpeakingPromptAiContract.VerifiedAudio verified =
                new SpeakingPromptAiContract.VerifiedAudio(
                        bytes, "private.mp3", "audio/mpeg", hash, 3_000L);

        assertThat(service.requireBoundOriginalAsset(
                draftId, ownerId, assetId, questionClientId, verified))
                .isSameAs(asset);
        verify(references)
                .existsByAssetIdAndDraftIdAndPlacementAndReferenceKey(
                        assetId,
                        draftId,
                        SpeakingPromptAssetService.ORIGINAL_PLACEMENT,
                        questionClientId);

        assertThatThrownBy(() -> service.requireBoundOriginalAsset(
                draftId, ownerId, assetId, "speaking-question-b", verified))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void knowingPrivateAssetIdWithoutMatchingOwnerGrantsNothing() {
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        SpeakingPromptAssetService service = new SpeakingPromptAssetService(
                assets,
                mock(PracticeMaterialReferenceRepository.class),
                mock(LecturerAssetService.class),
                mock(SpeakingPromptAudioVerifier.class),
                new SpeakingPromptAuthoringAiProperties());
        byte[] bytes = "verified-private-original".getBytes(
                StandardCharsets.UTF_8);
        SpeakingPromptAiContract.VerifiedAudio verified =
                new SpeakingPromptAiContract.VerifiedAudio(
                        bytes,
                        "private.mp3",
                        "audio/mpeg",
                        SpeakingPromptAiContract.exactBytesSha256(bytes),
                        3_000L);
        when(assets.findByIdAndOwnerLecturerId(101L, 999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireBoundOriginalAsset(
                91L, 999L, 101L, "speaking-question-a", verified))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("audio");
    }

    @Test
    void publicVerificationCommandDoesNotPrintPrivateAssetIdentifier() {
        SpeakingPromptAuthoringService.VerifyOriginalAudio command =
                new SpeakingPromptAuthoringService.VerifyOriginalAudio(
                        91L, "speaking-question-a", 81L, 101L);

        assertThat(command.toString())
                .doesNotContain("91")
                .doesNotContain("speaking-question-a")
                .doesNotContain("81")
                .doesNotContain("101")
                .contains("privateAssetSelected=true");
    }

    @Test
    void sharedReadyTtsAssetIsLinkedThroughExistingLecturerLifecycleAuthority() {
        LecturerAssetService lecturerAssets = mock(LecturerAssetService.class);
        SpeakingPromptAssetService service = new SpeakingPromptAssetService(
                mock(LecturerAssetRepository.class),
                mock(PracticeMaterialReferenceRepository.class),
                lecturerAssets,
                mock(SpeakingPromptAudioVerifier.class),
                new SpeakingPromptAuthoringAiProperties());

        service.linkExistingGeneratedAsset(
                91L, 81L, "speaking-question-a", 101L);

        verify(lecturerAssets).linkExistingGeneratedDraftAudio(
                91L,
                81L,
                101L,
                "AI_TTS",
                SpeakingPromptAssetService.GENERATED_PLACEMENT,
                "speaking-question-a");
    }

    @Test
    void verifiedOriginalIsBoundOnlyThroughTheLockedDomainBoundary() {
        Long ownerId = 81L;
        Long draftId = 91L;
        Long assetId = 101L;
        String questionClientId = "speaking-question-a";
        byte[] bytes = "verified-private-original".getBytes(
                StandardCharsets.UTF_8);
        String hash = SpeakingPromptAiContract.exactBytesSha256(bytes);
        LecturerAsset asset = usableOriginalAsset(
                ownerId, assetId, hash, bytes.length);
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeMaterialReferenceRepository references =
                mock(PracticeMaterialReferenceRepository.class);
        when(assets.findByIdAndOwnerLecturerId(assetId, ownerId))
                .thenReturn(Optional.of(asset));
        when(assets.findByIdForUpdate(assetId))
                .thenReturn(Optional.of(asset));
        PracticeMaterialReferenceService materialReferences =
                new PracticeMaterialReferenceService(references, assets);
        SpeakingPromptAssetService service = new SpeakingPromptAssetService(
                assets,
                references,
                mock(LecturerAssetService.class),
                materialReferences,
                mock(SpeakingPromptAudioVerifier.class),
                new SpeakingPromptAuthoringAiProperties());
        SpeakingPromptAiContract.VerifiedAudio verified =
                new SpeakingPromptAiContract.VerifiedAudio(
                        bytes, "private.mp3", "audio/mpeg", hash, 3_000L);

        assertThat(service.bindVerifiedOriginalAsset(
                draftId, ownerId, assetId, questionClientId, verified))
                .isSameAs(asset);

        ArgumentCaptor<PracticeMaterialReference> binding =
                ArgumentCaptor.forClass(PracticeMaterialReference.class);
        verify(references).save(binding.capture());
        assertThat(binding.getValue().getDraftId()).isEqualTo(draftId);
        assertThat(binding.getValue().getAssetId()).isEqualTo(assetId);
        assertThat(binding.getValue().getPlacement())
                .isEqualTo(SpeakingPromptAssetService.ORIGINAL_PLACEMENT);
        assertThat(binding.getValue().getReferenceKey())
                .isEqualTo(questionClientId);
    }

    @Test
    void publishedByteReuploadCreatesPrivateLogicalAssetThatCanBeVerifiedAndBound()
            throws Exception {
        Long ownerId = 81L;
        Long draftId = 91L;
        Long publishedAssetId = 100L;
        Long privateAssetId = 101L;
        String questionClientId = "speaking-question-a";
        String publishedStorageKey = "lecturer-assets/81/published/original.mp3";
        byte[] bytes = "ID3-private-audio".getBytes(StandardCharsets.UTF_8);
        String hash = SpeakingPromptAiContract.exactBytesSha256(bytes);

        LecturerAsset published = usableOriginalAsset(
                ownerId, publishedAssetId, hash, bytes.length);
        published.setStorageProvider("LOCAL");
        published.setStorageKey(publishedStorageKey);
        published.setVisibility("PUBLISHED");
        PracticeDraft draft = new PracticeDraft(
                "Draft", "", "GLOBAL", null, "DRAFT", ownerId, "{}");
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeDraftRepository drafts = mock(PracticeDraftRepository.class);
        AssetStorageService storage = mock(AssetStorageService.class);
        PracticeMaterialReferenceRepository references =
                mock(PracticeMaterialReferenceRepository.class);
        PracticeAssetLifecycleTaskRepository lifecycleTasks =
                mock(PracticeAssetLifecycleTaskRepository.class);
        PracticeAssetReferenceGuard referenceGuard =
                mock(PracticeAssetReferenceGuard.class);
        PracticeMaterialReferenceService materialReferences =
                new PracticeMaterialReferenceService(references, assets);
        LecturerAssetService lecturerAssets = new LecturerAssetService(
                assets,
                drafts,
                storage,
                null,
                materialReferences,
                lifecycleTasks,
                null);
        lecturerAssets.setAssetReferenceGuard(referenceGuard);
        SpeakingPromptAudioVerifier verifier =
                mock(SpeakingPromptAudioVerifier.class);
        SpeakingPromptAssetService service = new SpeakingPromptAssetService(
                assets,
                references,
                lecturerAssets,
                materialReferences,
                verifier,
                new SpeakingPromptAuthoringAiProperties());
        org.springframework.web.multipart.MultipartFile file =
                mock(org.springframework.web.multipart.MultipartFile.class);
        AtomicReference<LecturerAsset> privateAsset = new AtomicReference<>();
        AtomicReference<String> freshUploadKey = new AtomicReference<>();

        when(drafts.findByIdAndOwnerId(draftId, ownerId))
                .thenReturn(Optional.of(draft));
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) bytes.length);
        when(file.getBytes()).thenReturn(bytes);
        when(file.getOriginalFilename()).thenReturn("original.mp3");
        when(storage.providerCode()).thenReturn("LOCAL");
        when(storage.store(any(), any(), any()))
                .thenAnswer(invocation -> {
                    String namespace = invocation.getArgument(2);
                    String key = namespace + "/" + hash + ".mp3";
                    freshUploadKey.set(key);
                    return new AssetStorageService.StoredAsset(
                            key, bytes.length, hash, true);
                });
        when(storage.load(publishedStorageKey))
                .thenReturn(new ByteArrayResource(bytes));
        when(assets
                .findByOwnerLecturerIdAndSha256AndStatusAndDeletedAtIsNull(
                        ownerId, hash, "ACTIVE"))
                .thenReturn(List.of(published));
        when(lifecycleTasks.findActiveBySourceStorageKeyForUpdate(
                publishedStorageKey)).thenReturn(List.of());
        when(assets.findByStorageKeyForUpdate(publishedStorageKey))
                .thenReturn(List.of(published));
        when(assets.save(any(LecturerAsset.class)))
                .thenAnswer(invocation -> {
                    LecturerAsset value = invocation.getArgument(0);
                    if (value.getId() == null) {
                        value.setId(privateAssetId);
                        privateAsset.set(value);
                    }
                    return value;
                });
        when(assets.findByIdAndOwnerLecturerId(privateAssetId, ownerId))
                .thenAnswer(invocation -> Optional.ofNullable(
                        privateAsset.get()));
        when(assets.findByIdForUpdate(privateAssetId))
                .thenAnswer(invocation -> Optional.ofNullable(
                        privateAsset.get()));
        when(references.save(any(PracticeMaterialReference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(references.findDraftPlacementAndReferenceKeyForUpdate(
                draftId,
                SpeakingPromptAssetService.ORIGINAL_PLACEMENT,
                questionClientId)).thenReturn(List.of());
        SpeakingPromptAiContract.VerifiedAudio verified =
                new SpeakingPromptAiContract.VerifiedAudio(
                        bytes, "original.mp3", "audio/mpeg", hash, 3_000L);
        when(verifier.verifySttInput(
                any(byte[].class), any(), any(), any()))
                .thenReturn(verified);

        SpeakingPromptAssetService.VerifiedOriginalUpload upload =
                service.uploadOriginal(
                        draftId, ownerId, questionClientId, file);

        assertThat(upload.assetId()).isEqualTo(privateAssetId);
        assertThat(upload.verifiedAudio()).isSameAs(verified);
        assertThat(privateAsset.get())
                .isNotSameAs(published)
                .satisfies(asset -> {
                    assertThat(asset.getStorageKey())
                            .isEqualTo(publishedStorageKey);
                    assertThat(asset.getVisibility()).isEqualTo("PRIVATE");
                    assertThat(asset.getStatus()).isEqualTo("TEMPORARY");
                    assertThat(asset.getSourceType())
                            .isEqualTo("MANUAL_UPLOAD");
                    assertThat(asset.isContentVerified()).isTrue();
                    assertThat(asset.getRetentionUntil()).isNotNull();
                });
        assertThat(published.getVisibility()).isEqualTo("PUBLISHED");
        assertThat(service.bindVerifiedOriginalAsset(
                draftId,
                ownerId,
                upload.assetId(),
                questionClientId,
                upload.verifiedAudio()))
                .isSameAs(privateAsset.get());
        assertThat(privateAsset.get().getVisibility()).isEqualTo("PRIVATE");
        assertThat(privateAsset.get().getStatus()).isEqualTo("ACTIVE");
        assertThat(privateAsset.get().getRetentionUntil()).isNull();

        ArgumentCaptor<PracticeMaterialReference> binding =
                ArgumentCaptor.forClass(PracticeMaterialReference.class);
        verify(references).save(binding.capture());
        assertThat(binding.getValue().getAssetId()).isEqualTo(privateAssetId);
        assertThat(binding.getValue().getPlacement())
                .isEqualTo(SpeakingPromptAssetService.ORIGINAL_PLACEMENT);
        verify(lifecycleTasks).findActiveBySourceStorageKeyForUpdate(
                publishedStorageKey);
        verify(assets).findByStorageKeyForUpdate(publishedStorageKey);
        verify(lifecycleTasks).save(
                org.mockito.ArgumentMatchers.argThat(task ->
                        PracticeAssetLifecycleTask.ORPHAN_RECONCILE.equals(
                                task.getOperation())
                                && freshUploadKey.get().equals(
                                task.getSourceStorageKey())));
    }

    @Test
    void replacementOriginalRetiresOnlyExactPriorPlacementAndQueuesAfterCommit() {
        Long ownerId = 81L;
        Long draftId = 91L;
        Long priorAssetId = 100L;
        Long replacementAssetId = 101L;
        String questionClientId = "speaking-question-a";
        byte[] bytes = "verified-private-original".getBytes(
                StandardCharsets.UTF_8);
        String hash = SpeakingPromptAiContract.exactBytesSha256(bytes);
        LecturerAsset replacement = usableOriginalAsset(
                ownerId, replacementAssetId, hash, bytes.length);
        PracticeMaterialReference prior = PracticeMaterialReference.draft(
                priorAssetId,
                draftId,
                SpeakingPromptAssetService.ORIGINAL_PLACEMENT,
                questionClientId,
                null);
        PracticeMaterialReference current = PracticeMaterialReference.draft(
                replacementAssetId,
                draftId,
                SpeakingPromptAssetService.ORIGINAL_PLACEMENT,
                questionClientId,
                null);
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeMaterialReferenceRepository references =
                mock(PracticeMaterialReferenceRepository.class);
        LecturerAssetService lecturerAssets = mock(LecturerAssetService.class);
        ApplicationEventPublisher events =
                mock(ApplicationEventPublisher.class);
        when(assets.findByIdForUpdate(replacementAssetId))
                .thenReturn(Optional.of(replacement));
        when(references.findDraftPlacementAndReferenceKeyForUpdate(
                draftId,
                SpeakingPromptAssetService.ORIGINAL_PLACEMENT,
                questionClientId)).thenReturn(List.of(prior, current));
        SpeakingPromptAssetService service = new SpeakingPromptAssetService(
                assets,
                references,
                lecturerAssets,
                new PracticeMaterialReferenceService(references, assets),
                mock(SpeakingPromptAudioVerifier.class),
                new SpeakingPromptAuthoringAiProperties(),
                events);
        SpeakingPromptAiContract.VerifiedAudio verified =
                new SpeakingPromptAiContract.VerifiedAudio(
                        bytes, "private.mp3", "audio/mpeg", hash, 3_000L);

        service.bindVerifiedOriginalAsset(
                draftId,
                ownerId,
                replacementAssetId,
                questionClientId,
                verified);

        InOrder exactOrder = inOrder(references, events);
        exactOrder.verify(references).save(any(PracticeMaterialReference.class));
        exactOrder.verify(references)
                .findDraftPlacementAndReferenceKeyForUpdate(
                        draftId,
                        SpeakingPromptAssetService.ORIGINAL_PLACEMENT,
                        questionClientId);
        exactOrder.verify(references)
                .deleteByAssetIdAndDraftIdAndPlacementAndReferenceKey(
                        priorAssetId,
                        draftId,
                        SpeakingPromptAssetService.ORIGINAL_PLACEMENT,
                        questionClientId);
        ArgumentCaptor<SpeakingPromptAssetService
                .RetiredPromptAssetCandidates> retired =
                ArgumentCaptor.forClass(SpeakingPromptAssetService
                        .RetiredPromptAssetCandidates.class);
        exactOrder.verify(events).publishEvent(retired.capture());
        verify(references, never())
                .deleteByAssetIdAndDraftIdAndPlacementAndReferenceKey(
                        replacementAssetId,
                        draftId,
                        SpeakingPromptAssetService.ORIGINAL_PLACEMENT,
                        questionClientId);
        verify(lecturerAssets, never())
                .queuePrivatePromptAssetIfUnreferenced(priorAssetId);
        assertThat(retired.getValue().assetIds())
                .containsExactly(priorAssetId);
        assertThat(retired.getValue().toString())
                .doesNotContain(priorAssetId.toString());

        service.queueRetiredPromptAssets(retired.getValue());

        verify(lecturerAssets)
                .queuePrivatePromptAssetIfUnreferenced(priorAssetId);
        assertAfterCommitRequiresNewBoundary();
    }

    @Test
    void replacementGeneratedBindingRetiresOnlySameQuestionPlacement() {
        Long ownerId = 81L;
        Long draftId = 91L;
        Long priorAssetId = 201L;
        Long replacementAssetId = 202L;
        String questionClientId = "speaking-question-a";
        PracticeMaterialReference prior = PracticeMaterialReference.draft(
                priorAssetId,
                draftId,
                SpeakingPromptAssetService.GENERATED_PLACEMENT,
                questionClientId,
                null);
        PracticeMaterialReference current = PracticeMaterialReference.draft(
                replacementAssetId,
                draftId,
                SpeakingPromptAssetService.GENERATED_PLACEMENT,
                questionClientId,
                null);
        PracticeMaterialReference anotherQuestion =
                PracticeMaterialReference.draft(
                        priorAssetId,
                        draftId,
                        SpeakingPromptAssetService.GENERATED_PLACEMENT,
                        "speaking-question-b",
                        null);
        PracticeMaterialReferenceRepository references =
                mock(PracticeMaterialReferenceRepository.class);
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        LecturerAssetService lecturerAssets = mock(LecturerAssetService.class);
        ApplicationEventPublisher events =
                mock(ApplicationEventPublisher.class);
        when(references.findDraftPlacementAndReferenceKeyForUpdate(
                draftId,
                SpeakingPromptAssetService.GENERATED_PLACEMENT,
                questionClientId)).thenReturn(List.of(
                        prior, current, anotherQuestion));
        SpeakingPromptAssetService service = new SpeakingPromptAssetService(
                assets,
                references,
                lecturerAssets,
                new PracticeMaterialReferenceService(references, assets),
                mock(SpeakingPromptAudioVerifier.class),
                new SpeakingPromptAuthoringAiProperties(),
                events);

        service.linkExistingGeneratedAsset(
                draftId,
                ownerId,
                questionClientId,
                replacementAssetId);

        InOrder exactOrder = inOrder(lecturerAssets, references, events);
        exactOrder.verify(lecturerAssets).linkExistingGeneratedDraftAudio(
                draftId,
                ownerId,
                replacementAssetId,
                "AI_TTS",
                SpeakingPromptAssetService.GENERATED_PLACEMENT,
                questionClientId);
        exactOrder.verify(references)
                .findDraftPlacementAndReferenceKeyForUpdate(
                        draftId,
                        SpeakingPromptAssetService.GENERATED_PLACEMENT,
                        questionClientId);
        verify(references)
                .deleteByAssetIdAndDraftIdAndPlacementAndReferenceKey(
                        priorAssetId,
                        draftId,
                        SpeakingPromptAssetService.GENERATED_PLACEMENT,
                        questionClientId);
        verify(references, never())
                .deleteByAssetIdAndDraftIdAndPlacementAndReferenceKey(
                        replacementAssetId,
                        draftId,
                        SpeakingPromptAssetService.GENERATED_PLACEMENT,
                        questionClientId);
        verify(references, never())
                .deleteByAssetIdAndDraftIdAndPlacementAndReferenceKey(
                        priorAssetId,
                        draftId,
                        SpeakingPromptAssetService.GENERATED_PLACEMENT,
                        "speaking-question-b");
        verify(events).publishEvent(any(
                SpeakingPromptAssetService
                        .RetiredPromptAssetCandidates.class));
    }

    @Test
    void pendingGenerationRetiresOnlyTheExactStaleQuestionBinding() {
        Long draftId = 91L;
        Long priorAssetId = 201L;
        String questionClientId = "speaking-question-a";
        PracticeMaterialReference prior = PracticeMaterialReference.draft(
                priorAssetId,
                draftId,
                SpeakingPromptAssetService.GENERATED_PLACEMENT,
                questionClientId,
                null);
        PracticeMaterialReference anotherQuestion =
                PracticeMaterialReference.draft(
                        priorAssetId,
                        draftId,
                        SpeakingPromptAssetService.GENERATED_PLACEMENT,
                        "speaking-question-b",
                        null);
        PracticeMaterialReferenceRepository references =
                mock(PracticeMaterialReferenceRepository.class);
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        LecturerAssetService lecturerAssets = mock(LecturerAssetService.class);
        ApplicationEventPublisher events =
                mock(ApplicationEventPublisher.class);
        when(references.findDraftPlacementAndReferenceKeyForUpdate(
                draftId,
                SpeakingPromptAssetService.GENERATED_PLACEMENT,
                questionClientId)).thenReturn(List.of(prior, anotherQuestion));
        SpeakingPromptAssetService service = new SpeakingPromptAssetService(
                assets,
                references,
                lecturerAssets,
                new PracticeMaterialReferenceService(references, assets),
                mock(SpeakingPromptAudioVerifier.class),
                new SpeakingPromptAuthoringAiProperties(),
                events);

        service.retireGeneratedAssetBinding(draftId, questionClientId);

        verify(references)
                .deleteByAssetIdAndDraftIdAndPlacementAndReferenceKey(
                        priorAssetId,
                        draftId,
                        SpeakingPromptAssetService.GENERATED_PLACEMENT,
                        questionClientId);
        verify(references, never())
                .deleteByAssetIdAndDraftIdAndPlacementAndReferenceKey(
                        priorAssetId,
                        draftId,
                        SpeakingPromptAssetService.GENERATED_PLACEMENT,
                        "speaking-question-b");
        verify(lecturerAssets, never()).linkExistingGeneratedDraftAudio(
                anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString());
        verify(events).publishEvent(any(
                SpeakingPromptAssetService
                        .RetiredPromptAssetCandidates.class));
    }

    @Test
    void newlyRegisteredGeneratedCandidateAlsoRetiresPriorExactBinding()
            throws Exception {
        Long ownerId = 81L;
        Long draftId = 91L;
        Long priorAssetId = 201L;
        Long replacementAssetId = 202L;
        String questionClientId = "speaking-question-a";
        byte[] bytes = "generated-private-audio".getBytes(
                StandardCharsets.UTF_8);
        String hash = SpeakingPromptAiContract.exactBytesSha256(bytes);
        SpeakingPromptAiContract.VerifiedAudio audio =
                new SpeakingPromptAiContract.VerifiedAudio(
                        bytes, "generated.mp3", "audio/mpeg", hash, 3_000L);
        LecturerAsset replacement = new LecturerAsset();
        replacement.setId(replacementAssetId);
        PracticeMaterialReference prior = PracticeMaterialReference.draft(
                priorAssetId,
                draftId,
                SpeakingPromptAssetService.GENERATED_PLACEMENT,
                questionClientId,
                null);
        PracticeMaterialReference current = PracticeMaterialReference.draft(
                replacementAssetId,
                draftId,
                SpeakingPromptAssetService.GENERATED_PLACEMENT,
                questionClientId,
                null);
        PracticeMaterialReferenceRepository references =
                mock(PracticeMaterialReferenceRepository.class);
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        LecturerAssetService lecturerAssets = mock(LecturerAssetService.class);
        ApplicationEventPublisher events =
                mock(ApplicationEventPublisher.class);
        when(lecturerAssets.registerGeneratedDraftAudio(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(
                        SpeakingPromptAssetService.GENERATED_PLACEMENT),
                org.mockito.ArgumentMatchers.eq(questionClientId)))
                .thenReturn(replacement);
        when(references.findDraftPlacementAndReferenceKeyForUpdate(
                draftId,
                SpeakingPromptAssetService.GENERATED_PLACEMENT,
                questionClientId)).thenReturn(List.of(prior, current));
        SpeakingPromptAssetService service = new SpeakingPromptAssetService(
                assets,
                references,
                lecturerAssets,
                new PracticeMaterialReferenceService(references, assets),
                mock(SpeakingPromptAudioVerifier.class),
                new SpeakingPromptAuthoringAiProperties(),
                events);
        SpeakingPromptAssetService.StoredGeneratedCandidate candidate =
                service.storeGeneratedCandidate(
                        ownerId, draftId, questionClientId, audio);

        assertThat(service.registerGeneratedCandidate(candidate))
                .isSameAs(replacement);

        verify(references)
                .deleteByAssetIdAndDraftIdAndPlacementAndReferenceKey(
                        priorAssetId,
                        draftId,
                        SpeakingPromptAssetService.GENERATED_PLACEMENT,
                        questionClientId);
        verify(events).publishEvent(any(
                SpeakingPromptAssetService
                        .RetiredPromptAssetCandidates.class));
    }

    @Test
    void uploadStagesAnUnboundAssetBeforeLockedBinding() throws Exception {
        String assetService = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/manage/speaking/"
                        + "SpeakingPromptAssetService.java"));
        String lecturerAssets = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/manage/service/"
                        + "LecturerAssetService.java"));
        String upload = between(
                assetService,
                "VerifiedOriginalUpload uploadOriginal(",
                "void unlinkOriginalBinding(");

        assertThat(upload)
                .contains("createUnboundDraftUploadAsset(")
                .doesNotContain("createDraftUploadAsset(");
        assertThat(lecturerAssets).contains(
                "public LecturerAsset createUnboundDraftUploadAsset(",
                "if (bindDraftReference && materialReferenceService != null)");
    }

    @Test
    void explicitUnlinkQueuesOnlyAfterSourceReferenceIsFlushed()
            throws Exception {
        String authoring = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/manage/speaking/"
                        + "SpeakingPromptAuthoringService.java"));
        String unlink = between(
                authoring,
                "SourceResult unlinkCurrentOriginalAudio(",
                "boolean cancelCurrentOperation(");

        assertThat(unlink)
                .containsSubsequence(
                        "sourceRepository.saveAndFlush(source)",
                        "assetService.queueIfUnreferenced(assetId)");
    }

    private static LecturerAsset usableOriginalAsset(
            Long ownerId,
            Long assetId,
            String hash,
            int byteSize) {
        LecturerAsset asset = new LecturerAsset();
        asset.setId(assetId);
        asset.setOwnerLecturerId(ownerId);
        asset.setContentVerified(true);
        asset.setStatus("ACTIVE");
        asset.setVisibility("PRIVATE");
        asset.setAssetType("AUDIO");
        asset.setSourceType("MANUAL_UPLOAD");
        asset.setSha256(hash);
        asset.setFileSize((long) byteSize);
        asset.setMimeType("audio/mpeg");
        asset.setOriginalFilename("private.mp3");
        return asset;
    }

    private static void assertAfterCommitRequiresNewBoundary() {
        try {
            java.lang.reflect.Method listener =
                    SpeakingPromptAssetService.class.getDeclaredMethod(
                            "queueRetiredPromptAssets",
                            SpeakingPromptAssetService
                                    .RetiredPromptAssetCandidates.class);
            assertThat(listener.getAnnotation(
                    TransactionalEventListener.class).phase())
                    .isEqualTo(TransactionPhase.AFTER_COMMIT);
            assertThat(listener.getAnnotation(Transactional.class)
                    .propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static String between(String source, String start, String end) {
        return source.substring(source.indexOf(start), source.indexOf(end));
    }
}
