package com.ksh.features.questionbank.imports;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** JVM-local store for pending question bank import previews. */
@Service
public class QuestionBankImportSessionStore {

    private static final Logger log = LoggerFactory.getLogger(QuestionBankImportSessionStore.class);

    private final ConcurrentHashMap<UUID, QuestionBankImportSession> sessions = new ConcurrentHashMap<>();

    public UUID save(QuestionBankImportSession session) {
        sessions.put(session.getId(), session);
        return session.getId();
    }

    public Optional<QuestionBankImportSession> get(UUID id, Long actorId) {
        if (id == null) {
            return Optional.empty();
        }
        QuestionBankImportSession session = sessions.get(id);
        if (session == null) {
            return Optional.empty();
        }
        if (session.isExpired(Instant.now())) {
            sessions.remove(id);
            return Optional.empty();
        }
        if (!session.getActorId().equals(actorId)) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /** Atomically consumes an owned, live session for one confirmation attempt. */
    public Optional<QuestionBankImportSession> claim(UUID id, Long actorId) {
        if (id == null) return Optional.empty();
        AtomicReference<QuestionBankImportSession> claimed = new AtomicReference<>();
        Instant now = Instant.now();
        sessions.computeIfPresent(id, (key, session) -> {
            if (session.isExpired(now)) return null;
            if (!session.getActorId().equals(actorId)) return session;
            claimed.set(session);
            return null;
        });
        return Optional.ofNullable(claimed.get());
    }

    /** Restores a claim whose validation or database transaction failed. */
    public void restore(QuestionBankImportSession session) {
        if (session != null && !session.isExpired(Instant.now())) {
            sessions.putIfAbsent(session.getId(), session);
        }
    }

    @Scheduled(initialDelay = 60_000L, fixedDelay = 60_000L)
    public void evictExpired() {
        Instant now = Instant.now();
        int before = sessions.size();
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        int after = sessions.size();
        if (before != after) {
            log.debug("Evicted {} expired question bank import sessions", before - after);
        }
    }
}
