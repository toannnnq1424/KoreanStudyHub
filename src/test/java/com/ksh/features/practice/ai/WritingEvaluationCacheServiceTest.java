package com.ksh.features.practice.ai;

import com.ksh.entities.WritingEvaluationCacheEntry;
import com.ksh.features.practice.ai.metrics.PracticeAiMetrics;
import com.ksh.features.practice.repository.WritingEvaluationCacheRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WritingEvaluationCacheServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long OTHER_USER_ID = 84L;
    private static final String PROMPT = "Write about learning Korean";
    private static final String ANSWER = "한국어를 공부합니다";
    private static final String TASK_TYPE = "Q53";
    private static final String MODEL = "gemini-2.5-flash";
    private static final String PROMPT_VERSION = "v2.0";
    private static final String RUBRIC_VERSION = "v2.0";
    private static final String SCHEMA_VERSION = "v2.0";
    private static final String CACHED_VALUE = "{\"score\":7.0}";

    @Test
    void testPutGetRoundTrip() {
        var fixture = fixture(Clock.systemUTC());
        fixture.service.put(USER_ID, PROMPT, ANSWER, TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION, CACHED_VALUE);

        Optional<String> result = fixture.service.get(USER_ID, PROMPT, ANSWER, TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION);

        assertTrue(result.isPresent());
        assertEquals(CACHED_VALUE, result.get());
        assertMetric(fixture.registry, "writing", "write", "success", 1.0);
        assertMetric(fixture.registry, "writing", "lookup", "hit", 1.0);
    }

    @Test
    void testNewServiceInstanceWithSameRepositoryReadsRow() {
        var fixture = fixture(Clock.systemUTC());
        fixture.service.put(USER_ID, PROMPT, ANSWER, TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION, CACHED_VALUE);

        var restartedService = new WritingEvaluationCacheService(fixture.repository, Clock.systemUTC());
        Optional<String> result = restartedService.get(USER_ID, PROMPT, ANSWER, TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION);

        assertTrue(result.isPresent());
        assertEquals(CACHED_VALUE, result.get());
    }

    @Test
    void testDifferentUserSameContentMiss() {
        var fixture = fixture(Clock.systemUTC());
        fixture.service.put(USER_ID, PROMPT, ANSWER, TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION, CACHED_VALUE);

        Optional<String> result = fixture.service.get(OTHER_USER_ID, PROMPT, ANSWER, TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION);

        assertTrue(result.isEmpty());
        assertMetric(fixture.registry, "writing", "lookup", "miss", 1.0);
        assertNotEquals(
                WritingEvaluationCacheService.scopedKey(USER_ID, PROMPT, ANSWER, TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION),
                WritingEvaluationCacheService.scopedKey(OTHER_USER_ID, PROMPT, ANSWER, TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION)
        );
    }

    @Test
    void testNullUserBypassesCache() {
        var fixture = fixture(Clock.systemUTC());
        fixture.service.put(null, PROMPT, ANSWER, TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION, CACHED_VALUE);

        Optional<String> result = fixture.service.get(null, PROMPT, ANSWER, TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION);

        assertTrue(result.isEmpty());
        verify(fixture.repository, never()).upsert(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void testVersioningAndContentMisses() {
        var fixture = fixture(Clock.systemUTC());
        fixture.service.put(USER_ID, PROMPT, ANSWER, TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION, CACHED_VALUE);

        assertTrue(fixture.service.get(USER_ID, PROMPT, ANSWER, "Q54", MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION).isEmpty());
        assertTrue(fixture.service.get(USER_ID, PROMPT, ANSWER, TASK_TYPE, "gpt-4o", PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION).isEmpty());
        assertTrue(fixture.service.get(USER_ID, PROMPT, ANSWER, TASK_TYPE, MODEL, "v1.0", RUBRIC_VERSION, SCHEMA_VERSION).isEmpty());
        assertTrue(fixture.service.get(USER_ID, PROMPT, ANSWER, TASK_TYPE, MODEL, PROMPT_VERSION, "v1.0", SCHEMA_VERSION).isEmpty());
        assertTrue(fixture.service.get(USER_ID, PROMPT, ANSWER, TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, "v1.0").isEmpty());
        assertTrue(fixture.service.get(USER_ID, PROMPT + " changed", ANSWER, TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION).isEmpty());
        assertTrue(fixture.service.get(USER_ID, PROMPT, ANSWER + " changed", TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION).isEmpty());
    }

    @Test
    void testLineEndingNormalizationInContentKey() {
        String key1 = WritingEvaluationCacheService.key("prompt\r\n", "answer\r\n", TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION);
        String key2 = WritingEvaluationCacheService.key("prompt\n", "answer\n", TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION);
        assertEquals(key1, key2);
    }

    @Test
    void testLengthPrefixedFramingPreventsDelimiterCollision() {
        String key1 = WritingEvaluationCacheService.key("a", "b|c", TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION);
        String key2 = WritingEvaluationCacheService.key("a|b", "c", TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION);

        assertNotEquals(key1, key2);
    }

    @Test
    void testOverwriteSameKeyAndNoDuplicateRow() {
        var fixture = fixture(Clock.systemUTC());

        fixture.service.put(USER_ID, PROMPT, ANSWER, TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION, "{\"score\":5.0}");
        fixture.service.put(USER_ID, PROMPT, ANSWER, TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION, "{\"score\":7.0}");

        Optional<String> result = fixture.service.get(USER_ID, PROMPT, ANSWER, TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION);
        assertTrue(result.isPresent());
        assertEquals("{\"score\":7.0}", result.get());
        assertEquals(1, fixture.rows.size());
    }

    @Test
    void testExpiredRowIsNotReturnedAndLazyDeleted() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        var fixture = fixture(Clock.fixed(base, ZoneId.of("UTC")));
        fixture.service.put(USER_ID, PROMPT, ANSWER, TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION, CACHED_VALUE);

        var expiredService = new WritingEvaluationCacheService(fixture.repository,
                Clock.fixed(base.plus(Duration.ofMinutes(31)), ZoneId.of("UTC")),
                new PracticeAiMetrics(fixture.registry));
        Optional<String> result = expiredService.get(USER_ID, PROMPT, ANSWER, TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION);

        assertTrue(result.isEmpty());
        assertTrue(fixture.rows.isEmpty());
        assertMetric(fixture.registry, "writing", "lookup", "expired", 1.0);
        assertTrue(fixture.registry.counter(PracticeAiMetrics.CACHE_OPERATIONS,
                "cache", "writing", "operation", "delete", "outcome", "success").count() >= 1.0);
    }

    @Test
    void repositoryFailuresRecordFailureMetricsAndStillPropagateToCaller() {
        WritingEvaluationCacheRepository repository = mock(WritingEvaluationCacheRepository.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WritingEvaluationCacheService service = new WritingEvaluationCacheService(
                repository, Clock.systemUTC(), new PracticeAiMetrics(registry));
        when(repository.findById(anyString())).thenThrow(new RuntimeException("db down"));

        assertThrows(RuntimeException.class, () -> service.get(USER_ID, PROMPT, ANSWER, TASK_TYPE,
                MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION));

        assertMetric(registry, "writing", "lookup", "failure", 1.0);
    }

    @Test
    void writeFailureRecordsMetricAndStillPropagatesToCaller() {
        WritingEvaluationCacheRepository repository = mock(WritingEvaluationCacheRepository.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WritingEvaluationCacheService service = new WritingEvaluationCacheService(
                repository, Clock.systemUTC(), new PracticeAiMetrics(registry));
        when(repository.deleteExpired(any(LocalDateTime.class))).thenReturn(0);
        when(repository.upsert(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("write down"));

        assertThrows(RuntimeException.class, () -> service.put(USER_ID, PROMPT, ANSWER, TASK_TYPE,
                MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION, CACHED_VALUE));

        assertMetric(registry, "writing", "write", "failure", 1.0);
    }

    @Test
    void deleteFailureRecordsMetricAndStillPropagatesToCaller() {
        WritingEvaluationCacheRepository repository = mock(WritingEvaluationCacheRepository.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WritingEvaluationCacheService service = new WritingEvaluationCacheService(
                repository, Clock.systemUTC(), new PracticeAiMetrics(registry));
        when(repository.deleteByCacheKey(anyString())).thenThrow(new RuntimeException("delete down"));

        assertThrows(RuntimeException.class, () -> service.delete(USER_ID, PROMPT, ANSWER, TASK_TYPE,
                MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION));

        assertMetric(registry, "writing", "delete", "failure", 1.0);
    }

    @Test
    void testPutOpportunisticallyDeletesExpiredRows() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        var fixture = fixture(Clock.fixed(base, ZoneId.of("UTC")));
        fixture.rows.put("expired", new WritingEvaluationCacheEntry(
                "expired", WritingEvaluationCacheService.userScopeHash(USER_ID), TASK_TYPE, MODEL,
                PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION, "{}", LocalDateTime.of(2025, 12, 31, 23, 59)));

        fixture.service.put(USER_ID, PROMPT, ANSWER, TASK_TYPE, MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION, CACHED_VALUE);

        assertFalse(fixture.rows.containsKey("expired"));
        assertEquals(1, fixture.rows.size());
    }

    @Test
    void testQuestionPromptAnswerIndependence() {
        var fixture = fixture(Clock.systemUTC());
        fixture.service.put(USER_ID, "Q51 prompt", ANSWER, "Q51_52", MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION, "{\"q\":51}");
        fixture.service.put(USER_ID, "Q52 prompt", ANSWER, "Q51_52", MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION, "{\"q\":52}");

        assertEquals("{\"q\":51}", fixture.service.get(USER_ID, "Q51 prompt", ANSWER, "Q51_52", MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION).orElseThrow());
        assertEquals("{\"q\":52}", fixture.service.get(USER_ID, "Q52 prompt", ANSWER, "Q51_52", MODEL, PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION).orElseThrow());
        assertEquals(2, fixture.rows.size());
    }

    private static Fixture fixture(Clock clock) {
        WritingEvaluationCacheRepository repository = mock(WritingEvaluationCacheRepository.class);
        Map<String, WritingEvaluationCacheEntry> rows = new HashMap<>();

        when(repository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(rows.get(inv.getArgument(0))));
        when(repository.deleteByCacheKey(anyString())).thenAnswer(inv -> rows.remove(inv.getArgument(0)) == null ? 0 : 1);
        when(repository.deleteExpired(any(LocalDateTime.class))).thenAnswer(inv -> {
            LocalDateTime now = inv.getArgument(0);
            int before = rows.size();
            rows.entrySet().removeIf(e -> !e.getValue().getExpiresAt().isAfter(now));
            return before - rows.size();
        });
        when(repository.upsert(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(LocalDateTime.class)))
                .thenAnswer(inv -> {
                    rows.put(inv.getArgument(0), new WritingEvaluationCacheEntry(
                            inv.getArgument(0),
                            inv.getArgument(1),
                            inv.getArgument(2),
                            inv.getArgument(3),
                            inv.getArgument(4),
                            inv.getArgument(5),
                            inv.getArgument(6),
                            inv.getArgument(7),
                            inv.getArgument(8)
                    ));
                    return 1;
                });

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        return new Fixture(new WritingEvaluationCacheService(repository, clock, new PracticeAiMetrics(registry)),
                repository, rows, registry);
    }

    private static void assertMetric(SimpleMeterRegistry registry, String cache,
                                     String operation, String outcome, double expected) {
        assertEquals(expected, registry.counter(PracticeAiMetrics.CACHE_OPERATIONS,
                "cache", cache, "operation", operation, "outcome", outcome).count());
    }

    private record Fixture(WritingEvaluationCacheService service,
                           WritingEvaluationCacheRepository repository,
                           Map<String, WritingEvaluationCacheEntry> rows,
                           SimpleMeterRegistry registry) {
    }
}
