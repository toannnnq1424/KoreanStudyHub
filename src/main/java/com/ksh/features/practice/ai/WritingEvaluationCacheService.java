package com.ksh.features.practice.ai;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

    public Optional<String> get(String prompt, String learnerAnswer) {
        String key = key(prompt, learnerAnswer);
        Entry entry = cache.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.createdAt().plus(TTL).isBefore(Instant.now())) {
            cache.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public void put(String prompt, String learnerAnswer, String value) {
        cache.put(key(prompt, learnerAnswer), new Entry(value, Instant.now()));
    }

    private static String key(String prompt, String learnerAnswer) {
        String raw = (prompt == null ? "" : prompt.trim()) + "|" + (learnerAnswer == null ? "" : learnerAnswer.trim());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private record Entry(String value, Instant createdAt) {
    }
}
