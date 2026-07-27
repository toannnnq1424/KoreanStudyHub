package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeAssetLifecycleTaskRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.entities.PracticeDraft;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PracticeAssessmentExcelSpeakingBoundaryTest {

    @Test
    void lecturerHelpStatesUploadOnlyNoTtsAndEditorHandoff()
            throws Exception {
        String page = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/templates/practice/manage/excel-import.html"));
        String codec = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/ksh/features/practice/manage/service/"
                        + "PracticeAssessmentExcelV2Codec.java"));
        String service = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/ksh/features/practice/manage/service/"
                        + "PracticeAssessmentExcelService.java"));

        org.assertj.core.api.Assertions.assertThat(page + codec)
                .contains("Excel không bật hoặc gọi TTS")
                .contains("mở từng câu Speaking")
                .contains("audio_upload + audio_only + teacher_upload");
        org.assertj.core.api.Assertions.assertThat(service)
                .doesNotContain(
                        "OpenAiSpeakingPrompt",
                        "SpeakingPromptAuthoringService",
                        "insertSttIfAbsent",
                        "insertTtsIfAbsent");
    }

    @Test
    void wrongOwnerUnknownUnverifiedOrNonPrivateAudioFailsClosed() {
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeMaterialReferenceService references =
                mock(PracticeMaterialReferenceService.class);
        LecturerAssetService service = service(assets, references);

        when(assets.findByIdForUpdate(9L))
                .thenReturn(Optional.empty());
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> service.requireVerifiedPrivateManualAudioForExcel(
                        9L, 7L, 20L, "q-1"));

        LecturerAsset unverified = new LecturerAsset();
        unverified.setId(10L);
        unverified.setOwnerLecturerId(7L);
        unverified.setStatus("ACTIVE");
        unverified.setVisibility("PUBLISHED");
        unverified.setAssetType("AUDIO");
        unverified.setSourceType("MANUAL_UPLOAD");
        unverified.setStorageKey("private/audio.mp3");
        when(assets.findByIdForUpdate(10L))
                .thenReturn(Optional.of(unverified));
        assertThrows(IllegalArgumentException.class,
                () -> service.requireVerifiedPrivateManualAudioForExcel(
                        10L, 7L, 20L, "q-1"));

        LecturerAsset otherOwner = new LecturerAsset();
        otherOwner.setId(11L);
        otherOwner.setOwnerLecturerId(8L);
        otherOwner.setContentVerified(true);
        otherOwner.setStatus("ACTIVE");
        otherOwner.setVisibility("PRIVATE");
        otherOwner.setAssetType("AUDIO");
        otherOwner.setSourceType("MANUAL_UPLOAD");
        otherOwner.setStorageKey("private/other.mp3");
        when(assets.findByIdForUpdate(11L))
                .thenReturn(Optional.of(otherOwner));
        assertThrows(IllegalArgumentException.class,
                () -> service.requireVerifiedPrivateManualAudioForExcel(
                        11L, 7L, 20L, "q-1"));
    }

    @Test
    void verifiedPrivateManualAudioIsResolvedWithoutSpeakingAiServices() {
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeMaterialReferenceService references =
                mock(PracticeMaterialReferenceService.class);
        LecturerAssetService service = service(assets, references);
        LecturerAsset audio = new LecturerAsset();
        audio.setId(9L);
        audio.setOwnerLecturerId(7L);
        audio.setContentVerified(true);
        audio.setStatus("ACTIVE");
        audio.setVisibility("PRIVATE");
        audio.setAssetType("AUDIO");
        audio.setSourceType("MANUAL_UPLOAD");
        audio.setStorageKey("private/audio.mp3");
        when(assets.findByIdForUpdate(9L))
                .thenReturn(Optional.of(audio));
        when(references.hasDraftReference(
                20L, 9L, "MANUAL_AUDIO", "")).thenReturn(true);

        org.junit.jupiter.api.Assertions.assertSame(
                audio,
                service.requireVerifiedPrivateManualAudioForExcel(
                        9L, 7L, 20L, "q-1"));
    }

    @Test
    void ownerPrivateIdWithoutExactDraftReferenceIsRejected() {
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeMaterialReferenceService references =
                mock(PracticeMaterialReferenceService.class);
        LecturerAssetService service = service(assets, references);
        LecturerAsset audio = new LecturerAsset();
        audio.setId(9L);
        audio.setOwnerLecturerId(7L);
        audio.setContentVerified(true);
        audio.setStatus("ACTIVE");
        audio.setVisibility("PRIVATE");
        audio.setAssetType("AUDIO");
        audio.setSourceType("MANUAL_UPLOAD");
        audio.setStorageKey("private/audio.mp3");
        when(assets.findByIdForUpdate(9L)).thenReturn(Optional.of(audio));
        when(references.hasDraftReference(
                20L, 9L, "MANUAL_AUDIO", "")).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.requireVerifiedPrivateManualAudioForExcel(
                        9L, 7L, 20L, "q-1"));
    }

    @Test
    void successfulImportConsumesOnlyDraftLevelUploadReference() {
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeDraftRepository drafts = mock(PracticeDraftRepository.class);
        PracticeMaterialReferenceService references =
                mock(PracticeMaterialReferenceService.class);
        LecturerAssetService service = new LecturerAssetService(
                assets,
                drafts,
                mock(AssetStorageService.class),
                null,
                references,
                mock(PracticeAssetLifecycleTaskRepository.class),
                null);
        PracticeDraft draft = new PracticeDraft(
                "Draft", "", "GLOBAL", null, "DRAFT", 7L, "{}");
        LecturerAsset audio = new LecturerAsset();
        audio.setOwnerLecturerId(7L);
        when(drafts.findByIdAndOwnerId(20L, 7L))
                .thenReturn(Optional.of(draft));
        when(assets.findByIdForUpdate(9L)).thenReturn(Optional.of(audio));
        when(references.hasDraftReference(
                20L,
                9L,
                PracticeAssessmentExcelService.EXCEL_SPEAKING_STAGING,
                "q-1")).thenReturn(true);

        service.consumeExcelSpeakingUploadReference(
                20L, 9L, 7L, "q-1");

        org.mockito.Mockito.verify(references).unlinkDraft(
                20L, 9L, "MANUAL_AUDIO", "");
        org.mockito.Mockito.verify(references, org.mockito.Mockito.never())
                .unlinkDraft(
                        org.mockito.ArgumentMatchers.eq(20L),
                        org.mockito.ArgumentMatchers.eq(9L),
                        org.mockito.ArgumentMatchers.eq(
                                PracticeAssessmentExcelService
                                        .EXCEL_SPEAKING_STAGING),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void genericExcelOverrideAlsoRequiresExactDraftUploadReference() {
        LecturerAssetRepository assets = mock(LecturerAssetRepository.class);
        PracticeDraftRepository drafts = mock(PracticeDraftRepository.class);
        PracticeMaterialReferenceService references =
                mock(PracticeMaterialReferenceService.class);
        LecturerAssetService service = new LecturerAssetService(
                assets,
                drafts,
                mock(AssetStorageService.class),
                null,
                references,
                mock(PracticeAssetLifecycleTaskRepository.class),
                null);
        PracticeDraft draft = new PracticeDraft(
                "Draft", "", "GLOBAL", null, "DRAFT", 7L, "{}");
        LecturerAsset image = new LecturerAsset();
        image.setId(12L);
        image.setOwnerLecturerId(7L);
        image.setContentVerified(true);
        image.setStatus("ACTIVE");
        image.setVisibility("PRIVATE");
        image.setAssetType("IMAGE");
        image.setSourceType("MANUAL_UPLOAD");
        when(drafts.findByIdAndOwnerId(20L, 7L))
                .thenReturn(Optional.of(draft));
        when(assets.findByIdForUpdate(12L)).thenReturn(Optional.of(image));

        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> service.linkExcelManagedUploadToDraft(
                        20L, 12L, 7L));

        org.mockito.Mockito.verify(references, org.mockito.Mockito.never())
                .linkDraft(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any());
    }

    private static LecturerAssetService service(
            LecturerAssetRepository assets,
            PracticeMaterialReferenceService references) {
        return new LecturerAssetService(
                assets,
                mock(PracticeDraftRepository.class),
                mock(AssetStorageService.class),
                null,
                references,
                mock(PracticeAssetLifecycleTaskRepository.class),
                null);
    }
}
