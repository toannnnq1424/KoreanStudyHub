package com.ksh.features.practice.ai.readinglistening;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionExplanationLifecycleContractTest {

    private static final Path MAIN = Path.of("src/main/java/com/ksh");

    @Test
    void migrationSeparatesDeduplicatedArtifactsFromImmutableVersionBindingsAndTasks()
            throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V28__question_explanation_artifact_lifecycle.sql"));

        assertThat(migration).contains(
                "CREATE TABLE question_explanation_artifacts",
                "CONSTRAINT uk_qea_fingerprint UNIQUE (fingerprint)",
                "CREATE TABLE question_version_explanation_bindings",
                "CONSTRAINT uk_qveb_question_language UNIQUE (question_version_id, explanation_language)",
                "CREATE TABLE question_explanation_generation_tasks",
                "CONSTRAINT uk_qegt_artifact UNIQUE (artifact_id)",
                "legacy_explanation_ready",
                "'$.meaningVi'",
                "'$.evidenceQuote'",
                "'$.correctReasonVi'",
                "'$.relatedTranslationVi'",
                "'$.eliminatedOptions'",
                "LEGACY_EXPLANATION_INVALID",
                "MAX(id) AS newest_cache_id",
                "DROP TABLE question_explanation_cache");
        assertThat(migration).doesNotContain("legacyRawExplanation");
    }

    @Test
    void publisherEmitsPreparationEventForTheCommittedImmutableVersion() throws IOException {
        String publisher = Files.readString(MAIN.resolve(
                "features/practice/manage/service/PracticePublisherService.java"));
        String listener = Files.readString(MAIN.resolve(
                "features/practice/ai/readinglistening/PublishedVersionExplanationListener.java"));

        assertThat(publisher).contains(
                "new PublishedVersionExplanationEvent(publishedVersion.getId())",
                "applicationEventPublisher.publishEvent");
        assertThat(listener).contains(
                "@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)",
                "preparationService.preparePublishedVersion(event.publishedVersionId())");
    }

    @Test
    void activeReadPathIsReadOnlyAndLegacyParallelServicesAreRemoved() throws IOException {
        String readService = Files.readString(MAIN.resolve(
                "features/practice/ai/readinglistening/QuestionExplanationReadService.java"));
        String bindingRepository = Files.readString(MAIN.resolve(
                "features/practice/repository/QuestionVersionExplanationBindingRepository.java"));

        assertThat(readService).contains("@Transactional(readOnly = true)")
                .doesNotContain(".generate(", ".save(", "insertPendingIfAbsent", "bindIfAbsent");
        assertThat(bindingRepository).contains("INSERT IGNORE", "bindIfAbsent")
                .doesNotContain("UPDATE question_version_explanation_bindings");
        assertThat(Files.exists(MAIN.resolve("entities/QuestionExplanationCache.java"))).isFalse();
        assertThat(Files.exists(MAIN.resolve(
                "features/practice/service/ReadingListeningExplanationService.java"))).isFalse();
        assertThat(Files.exists(MAIN.resolve(
                "features/practice/ai/readinglistening/ReadingListeningMockExplanationService.java"))).isFalse();
    }

    @Test
    void preparationUsesFreshReadCommittedTransactionForCrossNodeDeduplication()
            throws NoSuchMethodException {
        Transactional transaction = QuestionExplanationPreparationService.class
                .getDeclaredMethod("preparePublishedVersion", Long.class)
                .getAnnotation(Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(transaction.isolation()).isEqualTo(Isolation.READ_COMMITTED);
    }
}
