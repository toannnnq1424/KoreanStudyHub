package com.ksh.features.admin.users.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.UserActivity;
import com.ksh.features.admin.users.repository.UserActivityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Centralises writes to the {@code user_activities} audit table for the
 * admin user-management feature.
 *
 * <p>Pulled out of {@code AdminUsersService} as part of the C.2 structural
 * split so the three split services
 * ({@code AdminUsersReadService}, {@code AdminUsersWriteService},
 * {@code AdminUsersLifecycleService}) can share the same single insertion
 * point without depending on each other. Behaviour is identical to the
 * pre-split inlined {@code writeActivity} helper — it just persists one row
 * per call and serialises the metadata map via Jackson.
 *
 * <p>Failure to serialise the metadata map degrades to a {@code null}
 * payload + a warning log, mirroring the original semantics.
 *
 * <p>Public rather than package-private because the account-lifecycle events
 * introduced by the roster import — {@code IMPORTED}, {@code ACTIVATION_SENT},
 * {@code SELF_ACTIVATED} — originate in {@code admin.users.imports} and
 * {@code auth.service}. They audit the same {@code user_activities} table and
 * surface on the same admin history screen, so they route through this single
 * insertion point instead of each package growing its own writer.
 */
@Component
public class AdminUsersAuditWriter {

    private static final Logger log = LoggerFactory.getLogger(AdminUsersAuditWriter.class);

    private final UserActivityRepository activityRepository;
    private final ObjectMapper objectMapper;

    AdminUsersAuditWriter(UserActivityRepository activityRepository,
                          ObjectMapper objectMapper) {
        this.activityRepository = activityRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Writes a single activity row with the supplied (already serialised)
     * metadata payload.
     *
     * <p>Callers must not pass plaintext passwords or activation tokens in
     * {@code metadata} — the audit log is readable by every administrator, and
     * a leaked activation token is equivalent to account takeover.
     *
     * @param targetUserId the account the entry is about
     * @param type         one of the {@code UserActivity.TYPE_*} constants
     * @param message      human-readable Vietnamese summary
     * @param metadata     JSON payload, or {@code null} when there is nothing safe to record
     * @param actorId      who performed it, or {@code null} for owner-driven events
     */
    public void write(Long targetUserId, String type, String message,
                      String metadata, Long actorId) {
        activityRepository.save(new UserActivity(targetUserId, type, message, metadata, actorId));
    }

    /** Serialises an arbitrary payload to JSON; returns {@code null} when serialisation fails. */
    public String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize user activity metadata", ex);
            return null;
        }
    }
}
