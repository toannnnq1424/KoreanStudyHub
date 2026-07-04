package com.ksh.features.practice.ai.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class PracticeAiMetrics {

    public static final String CACHE_OPERATIONS = "practice.ai.cache.operations";
    public static final String CACHE_DURATION = "practice.ai.cache.duration";
    public static final String PROVIDER_OPERATIONS = "practice.ai.provider.operations";
    public static final String PROVIDER_DURATION = "practice.ai.provider.duration";

    private final MeterRegistry registry;

    public PracticeAiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public static PracticeAiMetrics noop() {
        return new PracticeAiMetrics(null);
    }

    public static long startNanos() {
        return System.nanoTime();
    }

    public static Duration elapsedSince(long startNanos) {
        return Duration.ofNanos(Math.max(0L, System.nanoTime() - startNanos));
    }

    public void recordCacheOperation(CacheType cache, CacheOperation operation,
                                     CacheOutcome outcome, Duration duration) {
        if (registry == null) {
            return;
        }
        try {
            Tags tags = Tags.of(
                    "cache", cache.tag(),
                    "operation", operation.tag(),
                    "outcome", outcome.tag());
            registry.counter(CACHE_OPERATIONS, tags).increment();
            Timer.builder(CACHE_DURATION)
                    .tags(tags)
                    .register(registry)
                    .record(duration.toNanos(), TimeUnit.NANOSECONDS);
        } catch (RuntimeException ignored) {
            // Metrics are optional observability and must not alter grading/result behavior.
        }
    }

    public void recordProviderOperation(ProviderFeature feature, ProviderOutcome outcome,
                                        Duration duration) {
        if (registry == null) {
            return;
        }
        try {
            Tags tags = Tags.of(
                    "feature", feature.tag(),
                    "outcome", outcome.tag());
            registry.counter(PROVIDER_OPERATIONS, tags).increment();
            Timer.builder(PROVIDER_DURATION)
                    .tags(tags)
                    .register(registry)
                    .record(duration.toNanos(), TimeUnit.NANOSECONDS);
        } catch (RuntimeException ignored) {
            // Metrics are optional observability and must not alter grading/result behavior.
        }
    }

    public enum CacheType {
        WRITING("writing"),
        RL_EXPLANATION("rl_explanation");

        private final String tag;

        CacheType(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    public enum CacheOperation {
        LOOKUP("lookup"),
        PARSE("parse"),
        WRITE("write"),
        DELETE("delete");

        private final String tag;

        CacheOperation(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    public enum CacheOutcome {
        HIT("hit"),
        MISS("miss"),
        EXPIRED("expired"),
        MALFORMED("malformed"),
        SUCCESS("success"),
        FAILURE("failure");

        private final String tag;

        CacheOutcome(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    public enum ProviderFeature {
        WRITING("writing"),
        RL_EXPLANATION("rl_explanation");

        private final String tag;

        ProviderFeature(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    public enum ProviderOutcome {
        SUCCESS("success"),
        FALLBACK("fallback"),
        FAILURE("failure");

        private final String tag;

        ProviderOutcome(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }
}
