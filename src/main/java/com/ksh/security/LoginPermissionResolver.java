package com.ksh.security;

import com.ksh.features.admin.permissions.service.PermissionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Adds effective permissions to an authenticated principal without making RBAC
 * availability a precondition for login.
 *
 * <p>The method suspends any caller transaction before entering
 * {@link PermissionResolver}. The resolver therefore owns its read transaction:
 * a repository failure can roll that transaction back without marking the
 * identity/link transaction rollback-only.
 */
@Service
public class LoginPermissionResolver {

    private static final Logger log = LoggerFactory.getLogger(LoginPermissionResolver.class);

    private final PermissionResolver permissionResolver;

    public LoginPermissionResolver(PermissionResolver permissionResolver) {
        this.permissionResolver = permissionResolver;
    }

    /** Returns effective feature keys, or an empty set when RBAC lookup fails. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Set<String> resolveSafely(Long userId) {
        return resolveSnapshotSafely(userId).featureKeys();
    }

    /**
     * Resolves login authorities together with their nearest natural expiry.
     *
     * <p>Expired override rows remain as audit history. If one is still marked
     * active, a five-minute cache entry produced before its deadline may contain
     * stale permissions. Evicting before the login resolution closes that gap.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PermissionSnapshot resolveSnapshotSafely(Long userId) {
        try {
            PermissionResolver.PermissionTimeline timeline =
                    permissionResolver.permissionTimeline(userId, LocalDateTime.now());
            if (timeline == null) {
                timeline = new PermissionResolver.PermissionTimeline(false, null);
            }
            if (timeline.hasElapsedActiveOverride()) {
                permissionResolver.evictUser(userId);
            }
            Set<String> featureKeys = permissionResolver.resolvePermissions(userId);
            return new PermissionSnapshot(
                    featureKeys == null ? Set.of() : Set.copyOf(featureKeys),
                    timeline.nextExpiry());
        } catch (Exception ex) {
            log.warn("Permission resolution failed for login user {}; continuing with role-only "
                    + "authorities", userId, ex);
            return new PermissionSnapshot(Set.of(), null);
        }
    }

    /** Immutable authority snapshot installed into an authenticated principal. */
    public record PermissionSnapshot(Set<String> featureKeys,
                                     LocalDateTime validUntil) {
    }
}
