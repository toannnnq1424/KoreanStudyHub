package com.ksh.features.practice.ai.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class PracticeAiMetricsTest {

    private static final Set<String> ALLOWED_TAG_KEYS = Set.of("cache", "operation", "outcome", "feature");
    private static final Set<String> ALLOWED_TAG_VALUES = Set.of(
            "writing", "rl_explanation",
            "lookup", "parse", "write", "delete",
            "hit", "miss", "expired", "malformed", "success", "failure", "fallback");

    @Test
    void cacheOperationRecordsCounterAndTimerWithBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PracticeAiMetrics metrics = new PracticeAiMetrics(registry);

        metrics.recordCacheOperation(
                PracticeAiMetrics.CacheType.WRITING,
                PracticeAiMetrics.CacheOperation.LOOKUP,
                PracticeAiMetrics.CacheOutcome.HIT,
                Duration.ofMillis(5));

        assertEquals(1.0, registry.counter(PracticeAiMetrics.CACHE_OPERATIONS,
                "cache", "writing", "operation", "lookup", "outcome", "hit").count());
        var timer = registry.timer(PracticeAiMetrics.CACHE_DURATION,
                "cache", "writing", "operation", "lookup", "outcome", "hit");
        assertEquals(1L, timer.count());
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS) > 0);
    }

    @Test
    void providerOperationRecordsCounterAndTimerWithBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PracticeAiMetrics metrics = new PracticeAiMetrics(registry);

        metrics.recordProviderOperation(
                PracticeAiMetrics.ProviderFeature.RL_EXPLANATION,
                PracticeAiMetrics.ProviderOutcome.FALLBACK,
                Duration.ofMillis(3));

        assertEquals(1.0, registry.counter(PracticeAiMetrics.PROVIDER_OPERATIONS,
                "feature", "rl_explanation", "outcome", "fallback").count());
        assertEquals(1L, registry.timer(PracticeAiMetrics.PROVIDER_DURATION,
                "feature", "rl_explanation", "outcome", "fallback").count());
    }

    @Test
    void repeatedEventsIncrementExistingMeters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PracticeAiMetrics metrics = new PracticeAiMetrics(registry);

        metrics.recordCacheOperation(PracticeAiMetrics.CacheType.WRITING,
                PracticeAiMetrics.CacheOperation.WRITE,
                PracticeAiMetrics.CacheOutcome.SUCCESS,
                Duration.ofMillis(1));
        metrics.recordCacheOperation(PracticeAiMetrics.CacheType.WRITING,
                PracticeAiMetrics.CacheOperation.WRITE,
                PracticeAiMetrics.CacheOutcome.SUCCESS,
                Duration.ofMillis(2));

        assertEquals(2.0, registry.counter(PracticeAiMetrics.CACHE_OPERATIONS,
                "cache", "writing", "operation", "write", "outcome", "success").count());
        assertEquals(2L, registry.timer(PracticeAiMetrics.CACHE_DURATION,
                "cache", "writing", "operation", "write", "outcome", "success").count());
    }

    @Test
    void repeatedEventsKeepMeterCardinalityBounded() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PracticeAiMetrics metrics = new PracticeAiMetrics(registry);

        for (int i = 0; i < 100; i++) {
            metrics.recordCacheOperation(PracticeAiMetrics.CacheType.WRITING,
                    PracticeAiMetrics.CacheOperation.LOOKUP,
                    PracticeAiMetrics.CacheOutcome.MISS,
                    Duration.ofNanos(1));
            metrics.recordProviderOperation(PracticeAiMetrics.ProviderFeature.WRITING,
                    PracticeAiMetrics.ProviderOutcome.FALLBACK,
                    Duration.ofNanos(1));
        }

        assertEquals(4, registry.getMeters().size());
        assertEquals(100.0, registry.counter(PracticeAiMetrics.CACHE_OPERATIONS,
                "cache", "writing", "operation", "lookup", "outcome", "miss").count());
        assertEquals(100L, registry.timer(PracticeAiMetrics.CACHE_DURATION,
                "cache", "writing", "operation", "lookup", "outcome", "miss").count());
        assertEquals(100.0, registry.counter(PracticeAiMetrics.PROVIDER_OPERATIONS,
                "feature", "writing", "outcome", "fallback").count());
        assertEquals(100L, registry.timer(PracticeAiMetrics.PROVIDER_DURATION,
                "feature", "writing", "outcome", "fallback").count());
        assertMetersUseOnlyAllowedTags(registry);
    }

    @Test
    void metricsRegistryFailuresAreSwallowed() {
        var registry = mock(io.micrometer.core.instrument.MeterRegistry.class);
        doThrow(new RuntimeException("sentinel failure")).when(registry).counter(any(String.class), any(Iterable.class));
        PracticeAiMetrics metrics = new PracticeAiMetrics(registry);

        assertDoesNotThrow(() -> metrics.recordCacheOperation(
                PracticeAiMetrics.CacheType.WRITING,
                PracticeAiMetrics.CacheOperation.LOOKUP,
                PracticeAiMetrics.CacheOutcome.FAILURE,
                Duration.ofMillis(1)));
    }

    @Test
    void recordingApiDoesNotAcceptArbitraryTags() {
        for (Method method : PracticeAiMetrics.class.getDeclaredMethods()) {
            if (!method.getName().startsWith("record")) {
                continue;
            }
            assertFalse(Arrays.asList(method.getParameterTypes()).contains(String.class),
                    "recording method must not accept raw tag values: " + method);
            assertFalse(Arrays.asList(method.getParameterTypes()).contains(Map.class),
                    "recording method must not accept arbitrary tag maps: " + method);
        }
    }

    @Test
    void meterIdsDoNotContainHighCardinalitySentinels() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PracticeAiMetrics metrics = new PracticeAiMetrics(registry);
        metrics.recordCacheOperation(
                PracticeAiMetrics.CacheType.RL_EXPLANATION,
                PracticeAiMetrics.CacheOperation.PARSE,
                PracticeAiMetrics.CacheOutcome.MALFORMED,
                Duration.ofMillis(1));
        metrics.recordProviderOperation(
                PracticeAiMetrics.ProviderFeature.WRITING,
                PracticeAiMetrics.ProviderOutcome.SUCCESS,
                Duration.ofMillis(1));

        String[] sentinels = {
                "USER-SECRET-123",
                "QUESTION-SECRET-456",
                "ANSWER-SECRET-789",
                "PROMPT-SECRET-ABC",
                "CACHEKEY-SECRET-DEF",
                "EXCEPTION-SECRET-GHI"
        };
        for (Meter meter : registry.getMeters()) {
            Meter.Id id = meter.getId();
            String renderedId = id.toString();
            for (String sentinel : sentinels) {
                assertFalse(renderedId.contains(sentinel));
                assertFalse(id.getName().contains(sentinel));
                assertFalse(String.valueOf(id.getDescription()).contains(sentinel));
                assertFalse(String.valueOf(id.getBaseUnit()).contains(sentinel));
                assertTrue(id.getTags().stream()
                        .noneMatch(tag -> tag.getKey().contains(sentinel)
                                || tag.getValue().contains(sentinel)));
            }
        }
        assertMetersUseOnlyAllowedTags(registry);
    }

    @Test
    void fallbackRegistryIsCreatedOnlyWhenNoRegistryExists() {
        new ApplicationContextRunner()
                .withUserConfiguration(PracticeAiMetricsConfiguration.class, PracticeAiMetrics.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(MeterRegistry.class);
                    assertThat(context).hasSingleBean(PracticeAiMetrics.class);
                    assertThat(context.getBean(MeterRegistry.class)).isInstanceOf(SimpleMeterRegistry.class);
                });
    }

    @Test
    void existingRegistryPreventsFallbackRegistry() {
        SimpleMeterRegistry customRegistry = new SimpleMeterRegistry();

        new ApplicationContextRunner()
                .withBean(MeterRegistry.class, () -> customRegistry)
                .withUserConfiguration(PracticeAiMetricsConfiguration.class, PracticeAiMetrics.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(MeterRegistry.class);
                    assertThat(context.getBean(MeterRegistry.class)).isSameAs(customRegistry);
                    assertThat(context).doesNotHaveBean("practiceAiMeterRegistry");
                    assertThat(context).hasSingleBean(PracticeAiMetrics.class);
                });
    }

    private static void assertMetersUseOnlyAllowedTags(SimpleMeterRegistry registry) {
        for (Meter meter : registry.getMeters()) {
            for (var tag : meter.getId().getTags()) {
                assertTrue(ALLOWED_TAG_KEYS.contains(tag.getKey()), "Unexpected tag key: " + tag);
                assertTrue(ALLOWED_TAG_VALUES.contains(tag.getValue()), "Unexpected tag value: " + tag);
            }
        }
    }
}
