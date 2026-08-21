package com.ksh.features.admin.users.imports.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for staged {@link UserImportSession} previews.
 *
 * <p>Sessions are keyed by a server-generated UUID and live for at most
 * {@link UserImportSession#TTL_MINUTES} minutes. A {@link Scheduled} sweeper
 * evicts expired entries every minute; {@link ConcurrentHashMap} makes
 * concurrent access on the same key safe.
 *
 * <p>Ownership is enforced on every {@link #get}: a staged preview belongs to
 * the administrator who uploaded it, so one admin can never confirm another's
 * upload even if they somehow learn the UUID. This is the data-scope guard for
 * the import endpoints — the {@code ADMIN} role gate alone would let any
 * administrator replay any other's session.
 *
 * <p>Deliberately separate from the class-roster
 * {@code ImportSessionStore}, which pins each session to a {@code classId} an
 * admin import does not have.
 *
 * <p><b>Known limitations</b> (same as the class-roster store, accepted for
 * this single-node deployment): the map lives in the JVM heap, so a restart
 * between upload and confirm loses the preview and a load-balanced deployment
 * would route confirm to an instance that never saw the upload. The admin
 * re-uploads.
 */
@Service
public class UserImportSessionStore {

    private static final Logger log = LoggerFactory.getLogger(UserImportSessionStore.class);

    private final ConcurrentHashMap<UUID, UserImportSession> sessions = new ConcurrentHashMap<>();

    /**
     * Persists the session and returns its UUID. Callers construct the session
     * with {@code UUID.randomUUID()} so the id can be handed to the browser
     * without a reload.
     */
    public UUID save(UserImportSession session) {
        sessions.put(session.getId(), session);
        return session.getId();
    }

    /**
     * Returns the session when it exists, has not expired, and belongs to the
     * supplied administrator. Expired sessions are evicted on read.
     *
     * @param id      the staged session id
     * @param adminId the acting administrator
     * @return the session, or empty when missing / expired / owned by another admin
     */
    public Optional<UserImportSession> get(UUID id, Long adminId) {
        if (id == null) return Optional.empty();
        UserImportSession session = sessions.get(id);
        if (session == null) return Optional.empty();
        if (session.isExpired(Instant.now())) {
            sessions.remove(id);
            return Optional.empty();
        }
        if (!session.getAdminId().equals(adminId)) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /** Explicit removal — called after a successful confirm so the UUID cannot be replayed. */
    public void delete(UUID id) {
        if (id != null) sessions.remove(id);
    }

    /** Current number of staged sessions (test / observability helper). */
    public int size() {
        return sessions.size();
    }

    /**
     * Background cleanup dropping sessions older than
     * {@link UserImportSession#TTL_MINUTES}. The first run is delayed 60 s to
     * stay clear of application startup.
     */
    @Scheduled(initialDelay = 60_000L, fixedDelay = 60_000L)
    public void evictExpired() {
        Instant now = Instant.now();
        int before = sessions.size();
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        int after = sessions.size();
        if (before != after) {
            log.debug("Evicted {} expired roster import sessions (remaining={})", before - after, after);
        }
    }
}
