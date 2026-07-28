package com.ksh.features.auth.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Small process-local abuse guard for password-reset requests. Keys are hashed
 * before storage, expired windows are pruned eagerly, and the access-ordered map
 * has a hard cap so attacker-controlled input cannot grow memory without bound.
 */
@Component
class PasswordResetRequestThrottle {

    static final int MAX_REQUESTS = 3;
    static final int MAX_KEYS = 10_000;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<String, Window> windows = new LinkedHashMap<>(128, 0.75f, true);

    PasswordResetRequestThrottle() {
        this(Clock.systemUTC());
    }

    PasswordResetRequestThrottle(Clock clock) {
        this.clock = clock;
    }

    synchronized boolean allow(String email, String clientIp) {
        Instant now = clock.instant();
        prune(now);
        String emailKey = key("email", normalizeEmail(email));
        String ipKey = key("ip", clientIp == null ? "" : clientIp.trim());
        Window emailWindow = current(emailKey, now);
        Window ipWindow = current(ipKey, now);
        if (emailWindow.count >= MAX_REQUESTS || ipWindow.count >= MAX_REQUESTS) {
            return false;
        }
        windows.put(emailKey, emailWindow.increment());
        windows.put(ipKey, ipWindow.increment());
        trimToCap();
        return true;
    }

    synchronized int trackedKeyCount() {
        return windows.size();
    }

    private Window current(String key, Instant now) {
        Window value = windows.get(key);
        if (value == null || !now.isBefore(value.started.plus(WINDOW))) {
            return new Window(now, 0);
        }
        return value;
    }

    private void prune(Instant now) {
        windows.entrySet().removeIf(entry ->
                !now.isBefore(entry.getValue().started.plus(WINDOW)));
    }

    private void trimToCap() {
        while (windows.size() > MAX_KEYS) {
            var iterator = windows.entrySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String key(String kind, String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((kind + ":" + value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record Window(Instant started, int count) {
        Window increment() {
            return new Window(started, count + 1);
        }
    }
}
