package com.ksh.features.discovery.ingestion;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class NewsIngestionSchedulerTest {

    @Test
    void disabledSchedulerDoesNotStartExternalIngestion() {
        NewsIngestionOrchestrator orchestrator = mock(NewsIngestionOrchestrator.class);
        NewsIngestionScheduler scheduler = new NewsIngestionScheduler(orchestrator, false);

        scheduler.ingestEveryFiveHours();

        verifyNoInteractions(orchestrator);
    }
}
