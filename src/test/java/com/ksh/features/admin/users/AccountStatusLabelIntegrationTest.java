package com.ksh.features.admin.users;

import com.ksh.entities.User;
import com.ksh.entities.UserFactory;
import com.ksh.features.admin.users.dto.StatusFilter;
import com.ksh.features.admin.users.dto.UserRow;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the consolidated account status label and the PENDING
 * status filter (change: admin-user-import-activation, tasks 3.1-3.7).
 *
 * <p>Covers the scenarios in
 * {@code openspec/changes/admin-user-import-activation/specs/account-status-model/spec.md}:
 * a never-activated account reports PENDING on both admin screens, PENDING is
 * excluded from the ACTIVE and INACTIVE filters, every filter's reported total
 * matches the rows it lists, and pre-existing states are unchanged for accounts
 * whose {@code activated_at} is set.
 *
 * <p>Each test runs inside a {@link Transactional} rollback so seeded rows do
 * not leak between cases.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountStatusLabelIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // ──────────────── The anti-divergence test ────────────────
    // This is the point of consolidating the two copies of the label logic:
    // if one copy grows a branch the other lacks, these two assertions diverge.

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void pendingAccount_reportsPending_onBothListAndEditPage() throws Exception {
        User pending = savePending("pending-both@ksh.edu.vn");

        String listLabel = findRowLabel(pending.getId(), null);
        String editLabel = fetchEditPageStatusLabel(pending.getId());

        assertThat(listLabel).isEqualTo("PENDING");
        assertThat(editLabel).isEqualTo("PENDING");
        assertThat(listLabel).isEqualTo(editLabel);
    }

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void activatedAccount_reportsSameLabel_onBothListAndEditPage() throws Exception {
        User activated = saveActivated("agree-active@ksh.edu.vn", true, false);

        assertThat(findRowLabel(activated.getId(), null))
                .isEqualTo(fetchEditPageStatusLabel(activated.getId()))
                .isEqualTo("ACTIVE");
    }

    // ──────────────── Pending excluded from other filters ────────────────

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void pendingAccount_isAbsentFromActiveAndInactiveFilters() {
        // is_active = 1 would have matched ACTIVE before the branches were
        // narrowed; activated_at IS NULL must now exclude it from both.
        User pendingButEnabled = savePending("pending-enabled@ksh.edu.vn");
        pendingButEnabled.setActive(true);
        userRepository.saveAndFlush(pendingButEnabled);

        User pendingDisabled = savePending("pending-disabled@ksh.edu.vn");

        assertThat(idsForStatus("ACTIVE"))
                .doesNotContain(pendingButEnabled.getId(), pendingDisabled.getId());
        assertThat(idsForStatus("INACTIVE"))
                .doesNotContain(pendingButEnabled.getId(), pendingDisabled.getId());
        assertThat(idsForStatus("PENDING"))
                .contains(pendingButEnabled.getId(), pendingDisabled.getId());
    }

    // ──────────────── Counts agree with listed rows ────────────────
    // Guards the separate countQuery block: a PENDING branch added to the
    // query but missed in the countQuery shows up here as a mismatch.

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void everyStatusFilter_reportedTotalMatchesListedRows() {
        savePending("count-pending@ksh.edu.vn");
        saveActivated("count-active@ksh.edu.vn", true, false);
        saveActivated("count-inactive@ksh.edu.vn", false, false);
        saveActivated("count-locked@ksh.edu.vn", true, true);
        userRepository.flush();

        for (StatusFilter filter : StatusFilter.values()) {
            // A page large enough to hold every row the filter can return, so
            // the listed size is directly comparable to the reported total.
            Page<UserRow> page = userRepository.searchUsersForAdmin(
                    null, null, filter.name(), PageRequest.of(0, 500));

            assertThat(page.getContent().size())
                    .as("rows listed vs reported total for filter %s", filter)
                    .isEqualTo((int) page.getTotalElements());
        }
    }

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void pendingFilter_listsOnlyNotDeletedNotLockedNeverActivatedAccounts() {
        savePending("pending-listed@ksh.edu.vn");
        User lockedPending = savePending("pending-locked@ksh.edu.vn");
        lockedPending.lock("test");
        userRepository.saveAndFlush(lockedPending);

        List<UserRow> rows = userRepository.searchUsersForAdmin(
                null, null, "PENDING", PageRequest.of(0, 500)).getContent();

        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.isDeleted()).isFalse();
            assertThat(row.isLocked()).isFalse();
            assertThat(row.getActivatedAt()).isNull();
            assertThat(row.statusLabel()).isEqualTo("PENDING");
        });
        assertThat(rows).noneMatch(row -> row.getId().equals(lockedPending.getId()));
    }

    // ──────────────── Pre-change states still render as before ────────────────

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void accountsWithActivatedAtSet_keepTheirPreChangeLabels() throws Exception {
        User active = saveActivated("keep-active@ksh.edu.vn", true, false);
        User inactive = saveActivated("keep-inactive@ksh.edu.vn", false, false);
        User locked = saveActivated("keep-locked@ksh.edu.vn", true, true);
        User deleted = saveActivated("keep-deleted@ksh.edu.vn", true, false);
        deleted.softDelete();
        userRepository.saveAndFlush(deleted);

        assertThat(findRowLabel(active.getId(), null)).isEqualTo("ACTIVE");
        assertThat(findRowLabel(inactive.getId(), null)).isEqualTo("INACTIVE");
        assertThat(findRowLabel(locked.getId(), null)).isEqualTo("LOCKED");
        assertThat(findRowLabel(deleted.getId(), "DELETED")).isEqualTo("DELETED");

        assertThat(fetchEditPageStatusLabel(active.getId())).isEqualTo("ACTIVE");
        assertThat(fetchEditPageStatusLabel(inactive.getId())).isEqualTo("INACTIVE");
        assertThat(fetchEditPageStatusLabel(locked.getId())).isEqualTo("LOCKED");
    }

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void lockedOutranksPending_forNeverActivatedAccount() {
        User lockedPending = savePending("locked-pending@ksh.edu.vn");
        lockedPending.lock("disciplinary");
        userRepository.saveAndFlush(lockedPending);

        assertThat(findRowLabel(lockedPending.getId(), "LOCKED")).isEqualTo("LOCKED");
    }

    // ──────────────── Helpers ────────────────

    /** Saves an account whose owner never activated: is_active = 0, activated_at NULL. */
    private User savePending(String email) {
        User u = UserFactory.newPendingActivation(
                email, passwordEncoder.encode("irrelevant-secret"),
                "Pending " + email, Role.STUDENT, null, null);
        return userRepository.saveAndFlush(u);
    }

    /** Saves an account that has self-activated, so activated_at is set. */
    private User saveActivated(String email, boolean active, boolean locked) {
        User u = UserFactory.newPendingActivation(
                email, passwordEncoder.encode("irrelevant-secret"),
                "Activated " + email, Role.STUDENT, null, null);
        u.markActivated(LocalDateTime.now().minusDays(1));
        u.setActive(active);
        if (locked) {
            u.lock("test lock");
        }
        return userRepository.saveAndFlush(u);
    }

    /** Ids the admin list returns under one status filter. */
    private List<Long> idsForStatus(String statusFilter) {
        userRepository.flush();
        return userRepository.searchUsersForAdmin(null, null, statusFilter, PageRequest.of(0, 500))
                .getContent().stream()
                .map(UserRow::getId)
                .toList();
    }

    /** Reads the status label the admin list would render for one account. */
    private String findRowLabel(Long userId, String statusFilter) {
        userRepository.flush();
        return userRepository.searchUsersForAdmin(null, null, statusFilter, PageRequest.of(0, 500))
                .getContent().stream()
                .filter(row -> row.getId().equals(userId))
                .map(UserRow::statusLabel)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "user " + userId + " absent from list with filter " + statusFilter));
    }

    /** Reads the status label the admin edit page puts on the model. */
    private String fetchEditPageStatusLabel(Long userId) throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/users/" + userId + "/edit"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("statusLabel"))
                .andReturn();
        return (String) result.getModelAndView().getModel().get("statusLabel");
    }
}