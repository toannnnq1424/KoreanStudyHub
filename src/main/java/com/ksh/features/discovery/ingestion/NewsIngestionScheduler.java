package com.ksh.features.discovery.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NewsIngestionScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(NewsIngestionScheduler.class);

    private final NewsIngestionOrchestrator orchestrator;
    private final boolean enabled;

    public NewsIngestionScheduler(
            NewsIngestionOrchestrator orchestrator,
            @Value("${app.news.ingestion.enabled:false}") boolean enabled
    ) {
        this.orchestrator = orchestrator;
        this.enabled = enabled;
    }

    @Scheduled(
            initialDelayString = "${app.news.ingestion.initial-delay:PT2M}",
            fixedDelayString = "${app.news.ingestion.fixed-delay:PT5H}"
    )
    public void ingestEveryFiveHours() {
        if (!enabled) {
            return;
        }
        NewsIngestionOrchestrator.RunSummary summary =
                orchestrator.run(NewsIngestionOrchestrator.Trigger.SCHEDULED);
        log.info(
                "Korea Discovery run {}: status={}, fetched={}, published={}, rejected={}, duplicates={}, errors={}",
                summary.runId(),
                summary.status(),
                summary.fetched(),
                summary.published(),
                summary.rejected(),
                summary.duplicates(),
                summary.errors()
        );
    }
}
