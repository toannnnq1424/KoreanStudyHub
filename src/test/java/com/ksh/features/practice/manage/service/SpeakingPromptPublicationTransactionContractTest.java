package com.ksh.features.practice.manage.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingPromptPublicationTransactionContractTest {

    @Test
    void publisherValidatesBeforeMutationThenSnapshotsContextBeforePromotion()
            throws Exception {
        String publisher = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/manage/service/"
                        + "PracticePublisherService.java"));

        int prepare = publisher.indexOf(
                "speakingPromptPublicationService.prepare(");
        int deleteLive = publisher.indexOf(
                "questionRepository.deleteBySetId");
        int version = publisher.indexOf(
                "createPublishedVersionDetailed(");
        int context = publisher.indexOf(
                "speakingPromptPublicationService.persistContexts(");
        int promotion = publisher.indexOf(
                "materialReferenceService.promoteDraftReferences(");

        assertThat(prepare).isGreaterThanOrEqualTo(0);
        assertThat(deleteLive).isGreaterThan(prepare);
        assertThat(version).isGreaterThan(deleteLive);
        assertThat(context).isGreaterThan(version);
        assertThat(promotion).isGreaterThan(context);
        assertThat(publisher).contains(
                "@Transactional",
                "findByIdForUpdate(draftId)",
                "speakingQuestionVersionIdsByClient",
                "speakingPlan.activeAssetBindings()");
        assertThat(publisher).doesNotContain(
                "transcribe(",
                "generateTts(",
                "enqueue",
                "providerClient");
    }

    @Test
    void onlyExactActiveSpeakingAssetReferencesArePromoted()
            throws Exception {
        String materialService = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/manage/service/"
                        + "PracticeMaterialReferenceService.java"));
        String promotion = materialService.substring(
                materialService.indexOf(
                        "public void promoteDraftReferences(\n"
                                + "            Long draftId,"),
                materialService.indexOf(
                        "private static boolean isSpeakingPromptPlacement"));

        assertThat(promotion).contains(
                "isSpeakingPromptPlacement(reference.getPlacement())",
                "binding.assetId().equals(reference.getAssetId())",
                "binding.placement().equals(reference.getPlacement())",
                "binding.questionClientId().equals(",
                "reference.getReferenceKey())");
        assertThat(promotion).doesNotContain(
                "deleteByAssetId",
                "setDeletedAt(LocalDateTime");
    }

    @Test
    void immutableVersionCreatorReturnsExactLiveQuestionToVersionMap()
            throws Exception {
        String versions = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/service/"
                        + "PracticePublishedVersionService.java"));

        assertThat(versions).contains(
                "questionVersionIdByQuestionId.put(",
                "question.getId(), savedQuestionVersion.getId())",
                "A live question was snapshotted more than once",
                "Map.copyOf(questionVersionIdByQuestionId)");
    }
}
