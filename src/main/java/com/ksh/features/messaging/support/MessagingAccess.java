package com.ksh.features.messaging.support;

import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.security.Role;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * Recipient eligibility gate for direct messaging (Epic #13, KSH-8.3).
 *
 * <p>The gate decides whether one user may START a new conversation with
 * another, and it powers recipient search. It is NOT consulted once a
 * conversation exists — {@code send} / {@code openConversation} check only
 * membership (see design decision D2). The rules:
 *
 * <ul>
 *   <li>Students may reach students and teaching staff (LECTURER or LEADER).</li>
 *   <li>LECTURER, LEADER and ADMIN may reach every active system role.</li>
 *   <li>The caller never appears as their own recipient.</li>
 *   <li>Inactive, locked and soft-deleted accounts are not eligible.</li>
 * </ul>
 */
@Component
public class MessagingAccess {

    private final UserRepository userRepository;

    public MessagingAccess(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Whether {@code meId} (with role {@code meRole}) may start a conversation
     * with {@code otherId}. Resolves the other user's role from the DB and
     * applies the caller's role scope.
     *
     * @param meId    the initiating user's id
     * @param meRole  the initiating user's role
     * @param otherId the prospective peer's id
     * @return {@code true} when the pair is eligible to start a conversation
     */
    public boolean canStartConversation(Long meId, Role meRole, Long otherId) {
        if (meId == null || otherId == null || meId.equals(otherId)) return false;
        User other = userRepository.findById(otherId).orElse(null);
        if (other == null) return false;
        return isEligibleRole(meRole, other.getRole())
                && other.isActive()
                && !other.isLocked();
    }

    /**
     * Returns the users the caller may start a conversation with, optionally
     * filtered by a case-insensitive name/email substring. Students see all
     * active students and teaching staff; staff users see every active role.
     *
     * @param meId   the caller's id
     * @param meRole the caller's role
     * @param q      optional case-insensitive name/email filter; null/blank disables
     * @return eligible recipient users
     */
    public List<User> eligibleRecipients(Long meId, Role meRole, String q) {
        String filter = (q == null) ? null : q.trim();
        if (meId == null || meRole == null) return List.of();

        List<User> candidates;
        if (meRole == Role.STUDENT) {
            candidates = userRepository
                    .findByActiveTrueAndLockedFalseAndRoleInAndIdNotOrderByFullNameAsc(
                            allowedRecipientRoles(meRole), meId);
        } else if (isStaff(meRole)) {
            candidates = userRepository
                    .findByActiveTrueAndLockedFalseAndIdNotOrderByFullNameAsc(meId);
        } else {
            return List.of();
        }

        return candidates.stream()
                .filter(u -> matches(u, filter))
                .toList();
    }

    private static boolean isEligibleRole(Role meRole, Role otherRole) {
        return otherRole != null && allowedRecipientRoles(meRole).contains(otherRole);
    }

    private static Collection<Role> allowedRecipientRoles(Role meRole) {
        if (meRole == Role.STUDENT) {
            return EnumSet.of(Role.STUDENT, Role.LECTURER, Role.LEADER);
        }
        if (meRole == Role.LECTURER || meRole == Role.LEADER || meRole == Role.ADMIN) {
            return EnumSet.allOf(Role.class);
        }
        return List.of();
    }

    private static boolean isStaff(Role role) {
        return role == Role.LECTURER || role == Role.LEADER || role == Role.ADMIN;
    }

    /** Case-insensitive match of the query against the user's name or email. */
    private static boolean matches(User u, String q) {
        if (q == null || q.isEmpty()) return true;
        String needle = q.toLowerCase(Locale.ROOT);
        return (u.getFullName() != null
                && u.getFullName().toLowerCase(Locale.ROOT).contains(needle))
                || (u.getEmail() != null
                && u.getEmail().toLowerCase(Locale.ROOT).contains(needle));
    }
}
