package com.ksh.features.practice.manage.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PracticeOverrideContextService {

    private static final String SESSION_ATTRIBUTE =
            PracticeOverrideContextService.class.getName() + ".contexts";
    private static final int MAX_REASON_LENGTH = 500;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

    private final Clock clock;
    private final Duration ttl;

    public PracticeOverrideContextService() {
        this(Clock.systemUTC(), DEFAULT_TTL);
    }

    PracticeOverrideContextService(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    public void establishForSet(HttpSession session, Long setId, String reason) {
        establish(session, key("SET", setId), requiredReason(reason));
    }

    public void establishForDraft(HttpSession session, Long draftId, String reason) {
        establish(session, key("DRAFT", draftId), requiredReason(reason));
    }

    public String reasonForSet(HttpSession session, Long setId, String explicitReason) {
        return resolve(session, key("SET", setId), explicitReason);
    }

    public String reasonForDraft(HttpSession session, Long draftId, String explicitReason) {
        return resolve(session, key("DRAFT", draftId), explicitReason);
    }

    public void clearDraft(HttpSession session, Long draftId) {
        remove(session, key("DRAFT", draftId));
    }

    private void establish(HttpSession session, String key, String reason) {
        synchronized (session) {
            Map<String, OverrideContext> contexts = contexts(session);
            contexts.put(key, new OverrideContext(reason, Instant.now(clock).plus(ttl)));
            session.setAttribute(SESSION_ATTRIBUTE, Map.copyOf(contexts));
        }
    }

    private String resolve(HttpSession session, String key, String explicitReason) {
        if (explicitReason != null && !explicitReason.isBlank()) {
            return requiredReason(explicitReason);
        }
        synchronized (session) {
            Map<String, OverrideContext> contexts = contexts(session);
            OverrideContext context = contexts.get(key);
            if (context == null) {
                return null;
            }
            if (!context.expiresAt().isAfter(Instant.now(clock))) {
                contexts.remove(key);
                session.setAttribute(SESSION_ATTRIBUTE, Map.copyOf(contexts));
                return null;
            }
            return context.reason();
        }
    }

    private void remove(HttpSession session, String key) {
        synchronized (session) {
            Map<String, OverrideContext> contexts = contexts(session);
            contexts.remove(key);
            session.setAttribute(SESSION_ATTRIBUTE, Map.copyOf(contexts));
        }
    }

    private Map<String, OverrideContext> contexts(HttpSession session) {
        Object stored = session.getAttribute(SESSION_ATTRIBUTE);
        Map<String, OverrideContext> result = new LinkedHashMap<>();
        if (stored instanceof Map<?, ?> values) {
            values.forEach((key, value) -> {
                if (key instanceof String stringKey && value instanceof OverrideContext context) {
                    result.put(stringKey, context);
                }
            });
        }
        return result;
    }

    private static String key(String targetType, Long targetId) {
        if (targetId == null) {
            throw new IllegalArgumentException("Override target là bắt buộc.");
        }
        return targetType + ":" + targetId;
    }

    private static String requiredReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Override khẩn cấp phải có lý do.");
        }
        String normalized = reason.trim();
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "Lý do override không được vượt quá " + MAX_REASON_LENGTH + " ký tự.");
        }
        return normalized;
    }

    private record OverrideContext(String reason, Instant expiresAt)
            implements Serializable {
    }
}
