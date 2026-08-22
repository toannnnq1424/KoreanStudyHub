package com.ksh.features.admin.users.imports.session;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** Single-node, owner-scoped and one-shot preview store. */
@Service
public class UserImportSessionStore {
    private final ConcurrentHashMap<UUID, UserImportSession> sessions = new ConcurrentHashMap<>();

    public UUID save(UserImportSession session) {
        sessions.put(session.getId(), session);
        return session.getId();
    }

    public Optional<UserImportSession> claim(UUID id, Long adminId) {
        if (id == null || adminId == null) return Optional.empty();
        AtomicReference<UserImportSession> claimed = new AtomicReference<>();
        Instant now = Instant.now();
        sessions.computeIfPresent(id, (key, session) -> {
            if (session.isExpired(now)) return null;
            if (!adminId.equals(session.getAdminId())) return session;
            claimed.set(session);
            return null;
        });
        return Optional.ofNullable(claimed.get());
    }

    public void restore(UserImportSession session) {
        if (session != null && !session.isExpired(Instant.now())) {
            sessions.putIfAbsent(session.getId(), session);
        }
    }

    int size() { return sessions.size(); }

    @Scheduled(initialDelay = 60_000L, fixedDelay = 60_000L)
    public void evictExpired() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }
}
