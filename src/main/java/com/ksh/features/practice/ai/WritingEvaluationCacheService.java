package com.ksh.features.practice.ai;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WritingEvaluationCacheService {

    private static final Duration TTL = Duration.ofMinutes(30);

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final Clock clock;

    public WritingEvaluationCacheService() {
        this.clock = Clock.systemUTC();
    }

    /** Test-only constructor for injectable clock (avoids Thread.sleep in TTL tests). */
    WritingEvaluationCacheService(Clock clock) {
        this.clock = clock;
    }

    public Optional<String> get(String prompt, String learnerAnswer,
                                String taskType, String model,
                                String promptVersion, String rubricVersion,
                                String schemaVersion) {
        String key = key(prompt, learnerAnswer, taskType, model, promptVersion, rubricVersion, schemaVersion);
        Entry entry = cache.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.createdAt().plus(TTL).isBefore(Instant.now(clock))) {
            cache.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public void put(String prompt, String learnerAnswer,
                    String taskType, String model,
                    String promptVersion, String rubricVersion,
                    String schemaVersion,
                    String value) {
        cache.put(
                key(prompt, learnerAnswer, taskType, model, promptVersion, rubricVersion, schemaVersion),
                new Entry(value, Instant.now(clock))
        );
    }

    static String key(String prompt, String learnerAnswer,
                      String taskType, String model,
                      String promptVersion, String rubricVersion,
                      String schemaVersion) {
        String raw = normalizeForKey(prompt)
                + "|" + normalizeForKey(learnerAnswer)
                + "|" + (taskType == null ? "" : taskType)
                + "|" + (model == null ? "" : model)
                + "|" + (promptVersion == null ? "" : promptVersion)
                + "|" + (rubricVersion == null ? "" : rubricVersion)
                + "|" + (schemaVersion == null ? "" : schemaVersion);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private static String normalizeForKey(String s) {
        if (s == null) return "";
        return s.trim().replace("\r\n", "\n").replace("\r", "\n");
    }

    private record Entry(String value, Instant createdAt) {
    }
}
