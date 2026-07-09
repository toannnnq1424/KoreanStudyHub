package com.ksh.features.practice.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeSpeakingMediaCleanupWorkerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(PracticeSpeakingMediaCleanupProcessor.class,
                    () -> mock(PracticeSpeakingMediaCleanupProcessor.class))
            .withUserConfiguration(PracticeSpeakingMediaCleanupWorker.class);

    @Test
    void workerIsAbsentWhenDisabledOrMissing() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(PracticeSpeakingMediaCleanupWorker.class));
    }

    @Test
    void enabledWorkerUsesConfiguredBatchSize() {
        contextRunner
                .withPropertyValues(
                        "app.practice.speaking-media.cleanup-worker-enabled=true",
                        "app.practice.speaking-media.cleanup-worker-batch-size=37")
                .run(context -> {
                    var processor = context.getBean(PracticeSpeakingMediaCleanupProcessor.class);
                    when(processor.processDueTasks(any(LocalDateTime.class), anyInt()))
                            .thenReturn(new PracticeSpeakingMediaCleanupProcessor.CleanupBatchResult(
                                    0, 0, 0, 0, 0, 0));

                    context.getBean(PracticeSpeakingMediaCleanupWorker.class).processDueTasks();

                    verify(processor).processDueTasks(
                            any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq(37));
                });
    }

    @Test
    void fixedDelayUsesDocumentedPropertyAndDefault() throws Exception {
        Scheduled scheduled = PracticeSpeakingMediaCleanupWorker.class
                .getDeclaredMethod("processDueTasks")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${app.practice.speaking-media.cleanup-worker-fixed-delay:PT5M}");
    }
}
