package com.ksh.security;

import com.ksh.features.admin.permissions.service.PermissionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
        try {
            Set<String> featureKeys = permissionResolver.resolvePermissions(userId);
            return featureKeys == null ? Set.of() : featureKeys;
        } catch (Exception ex) {
            log.warn("Permission resolution failed for login user {}; continuing with role-only "
                    + "authorities", userId, ex);
            return Set.of();
        }
    }
}
