package com.ksh.security;

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
 * Bounded, process-local guard against repeated form-login failures.
 *
 * <p>Account and client-address keys are normalized and hashed before they are
 * retained. This guard deliberately does not trust forwarding headers; a
 * trusted-proxy policy must be configured before those can identify a client.</p>
 */
@Component
public class LoginAttemptThrottle {

    static final int MAX_ACCOUNT_FAILURES = 6;
    static final int MAX_IP_FAILURES = 30;
    static final int MAX_KEYS = 20_000;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<String, Window> windows =
            new LinkedHashMap<>(256, 0.75f, true);

    public LoginAttemptThrottle() {
        this(Clock.systemUTC());
    }

    LoginAttemptThrottle(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean isBlocked(String username, String clientIp) {
        Instant now = clock.instant();
        prune(now);
        return current(accountKey(username), now).count >= MAX_ACCOUNT_FAILURES
                || current(ipKey(clientIp), now).count >= MAX_IP_FAILURES;
    }

    public synchronized void recordFailure(String username, String clientIp) {
        Instant now = clock.instant();
        prune(now);
        increment(accountKey(username), now);
        increment(ipKey(clientIp), now);
        trimToCap();
    }

    public synchronized void recordSuccess(String username) {
        windows.remove(accountKey(username));
    }

    synchronized int trackedKeyCount() {
        return windows.size();
    }

    private void increment(String key, Instant now) {
        Window window = current(key, now);
        windows.put(key, new Window(window.started, window.count + 1));
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

    private static String accountKey(String username) {
        String normalized = username == null
                ? ""
                : username.trim().toLowerCase(Locale.ROOT);
        return hash("account", normalized);
    }

    private static String ipKey(String clientIp) {
        return hash("ip", clientIp == null ? "" : clientIp.trim());
    }

    private static String hash(String kind, String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((kind + ":" + value)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record Window(Instant started, int count) {
    }
}
