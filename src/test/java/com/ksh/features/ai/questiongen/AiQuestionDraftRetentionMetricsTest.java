package com.ksh.features.ai.questiongen;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AiQuestionDraftRetentionMetricsTest {

    @Test
    void metrics_report_only_aggregate_retention_counts_and_age() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiQuestionDraftRetentionMetrics metrics =
                new AiQuestionDraftRetentionMetrics(providerFor(registry));

        metrics.recordSuccess(7, 3L, 120L);
        metrics.recordFailure();

        assertThat(registry.get(AiQuestionDraftRetentionMetrics.RUNS)
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(AiQuestionDraftRetentionMetrics.DELETED)
                .counter().count()).isEqualTo(7.0);
        assertThat(registry.get(AiQuestionDraftRetentionMetrics.FAILURES)
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(AiQuestionDraftRetentionMetrics.EXPIRED_COUNT)
                .gauge().value()).isEqualTo(3.0);
        assertThat(registry.get(AiQuestionDraftRetentionMetrics.OLDEST_EXPIRED_AGE)
                .gauge().value()).isEqualTo(120.0);
        registry.getMeters().forEach(meter ->
                assertThat(meter.getId().getTags()).isEmpty());
    }

    @Test
    void missing_registry_never_blocks_cleanup() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        AiQuestionDraftRetentionMetrics metrics = new AiQuestionDraftRetentionMetrics(
                beans.getBeanProvider(MeterRegistry.class));

        assertThatCode(() -> {
            metrics.recordSuccess(7, 3L, 120L);
            metrics.recordFailure();
        }).doesNotThrowAnyException();
    }

    @Test
    void committed_deletes_from_a_partial_sweep_are_counted_without_a_success_run() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiQuestionDraftRetentionMetrics metrics =
                new AiQuestionDraftRetentionMetrics(providerFor(registry));

        metrics.recordCommittedDeletes(4);

        assertThat(registry.get(AiQuestionDraftRetentionMetrics.DELETED)
                .counter().count()).isEqualTo(4.0);
        assertThat(registry.get(AiQuestionDraftRetentionMetrics.RUNS)
                .counter().count()).isZero();
        assertThat(registry.get(AiQuestionDraftRetentionMetrics.FAILURES)
                .counter().count()).isZero();
    }

    private static org.springframework.beans.factory.ObjectProvider<MeterRegistry> providerFor(
            MeterRegistry registry) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("meterRegistry", registry);
        return beans.getBeanProvider(MeterRegistry.class);
    }
}
