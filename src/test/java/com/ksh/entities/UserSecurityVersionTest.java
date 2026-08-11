package com.ksh.entities;

import com.ksh.security.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserSecurityVersionTest {

    @Test
    void credentialAndAccessMutationsAdvanceTheDurableVersion() {
        User user = UserFactory.newAdminCreated(
                "version@example.test", "old-hash", "Version User",
                Role.LECTURER, true, null, null);

        user.setPasswordHash("new-hash");
        user.lock("review");
        user.unlock();
        user.promoteToLeader(12L);
        user.demoteFromLeaderToLecturer();

        assertThat(user.getSecurityVersion()).isEqualTo(5L);
    }

    @Test
    void contactOnlyAdminEditDoesNotTerminateSessions() {
        User user = UserFactory.newAdminCreated(
                "same@example.test", "hash", "Old Name",
                Role.STUDENT, true, null, null);

        user.updateAdminFields(
                "same@example.test", "New Name", Role.STUDENT,
                true, "0123", "updated", null);

        assertThat(user.getSecurityVersion()).isZero();
    }

    @Test
    void roleEmailAndSubjectChangeAdvancesOnlyOncePerAtomicEdit() {
        User user = UserFactory.newAdminCreated(
                "old@example.test", "hash", "User",
                Role.LECTURER, true, null, null);

        user.updateAdminFields(
                "new@example.test", "User", Role.LEADER,
                true, null, null, 8L);

        assertThat(user.getSecurityVersion()).isOne();
    }
}
