package com.ksh.features.admin.users.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.User;
import com.ksh.entities.UserFactory;
import com.ksh.features.admin.users.repository.UserActivityRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.profile.service.SessionRevocationService;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** MySQL contract for the shared last-usable-admin lifecycle mutex. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        AdminUsersLifecycleService.class,
        AdminUsersGuard.class,
        AdminUsersAuditWriter.class,
        AdminUsersLifecycleConcurrencyIntegrationTest.JsonConfig.class
})
class AdminUsersLifecycleConcurrencyIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private UserActivityRepository activityRepository;
    @Autowired private AdminUsersLifecycleService lifecycleService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private PasswordEncoder passwordEncoder;
    @MockitoBean private SessionRevocationService sessionRevocationService;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cross_target_mutations_serialize_and_second_rechecks_committed_pool() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        TestUsers users = transactions.execute(status -> {
            User first = userRepository.findByEmailIgnoreCase("admin@ksh.edu.vn")
                    .orElseThrow();
            User second = userRepository.saveAndFlush(newAdmin("concurrent-second"));
            assertThat(userRepository.countActiveAdmins("ADMIN")).isEqualTo(2L);
            return new TestUsers(first.getId(), second.getId());
        });
        assertThat(users).isNotNull();

        Set<Long> originalActivityIds = transactions.execute(status ->
                new HashSet<>(activityRepository.findAll().stream()
                        .map(activity -> activity.getId())
                        .toList()));
        assertThat(originalActivityIds).isNotNull();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstHasMutex = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        Future<Void> first = null;
        Future<Throwable> second = null;
        try {
            first = executor.submit(() -> {
                transactions.executeWithoutResult(status -> {
                    userRepository.findAdminLifecycleMutexForUpdate("ADMIN").orElseThrow();
                    firstHasMutex.countDown();
                    await(releaseFirst);
                    lifecycleService.deactivate(users.secondId(), users.firstId());
                });
                return null;
            });
            assertThat(firstHasMutex.await(5, TimeUnit.SECONDS)).isTrue();

            second = executor.submit(() -> {
                secondStarted.countDown();
                try {
                    lifecycleService.lock(
                            users.firstId(), "concurrent last-admin contract", users.secondId());
                    return null;
                } catch (Throwable failure) {
                    return failure;
                }
            });
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Throwable> blockedSecond = second;
            assertThatThrownBy(() -> blockedSecond.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            Throwable secondOutcome = second.get(10, TimeUnit.SECONDS);

            assertThat(secondOutcome).isInstanceOf(AccessDeniedException.class);
            transactions.executeWithoutResult(status -> {
                User firstReloaded = userRepository.findByIdIncludingDeleted(users.firstId())
                        .orElseThrow();
                User secondReloaded = userRepository.findByIdIncludingDeleted(users.secondId())
                        .orElseThrow();
                assertThat(firstReloaded.isActive()).isTrue();
                assertThat(firstReloaded.isLocked()).isFalse();
                assertThat(secondReloaded.isActive()).isFalse();
                assertThat(userRepository.countActiveAdmins("ADMIN")).isEqualTo(1L);
            });
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            cleanupConcurrentUsers(transactions, users, originalActivityIds);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void usable_admin_count_excludes_locked_inactive_and_soft_deleted_admins() {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        Long[] createdIds = transactions.execute(status -> {
            long baseline = userRepository.countActiveAdmins("ADMIN");

            User locked = newAdmin("count-locked");
            locked.lock("count contract");
            User inactive = newAdmin("count-inactive");
            inactive.setActive(false);
            User deleted = newAdmin("count-deleted");
            deleted.softDelete();
            userRepository.saveAll(java.util.List.of(locked, inactive, deleted));
            userRepository.flush();

            assertThat(userRepository.countActiveAdmins("ADMIN")).isEqualTo(baseline);
            return new Long[]{locked.getId(), inactive.getId(), deleted.getId()};
        });
        assertThat(createdIds).isNotNull();

        transactions.executeWithoutResult(status -> jdbcTemplate.update(
                "DELETE FROM users WHERE id IN (?, ?, ?)",
                createdIds[0], createdIds[1], createdIds[2]));
    }

    private void cleanupConcurrentUsers(TransactionTemplate transactions,
                                        TestUsers users,
                                        Set<Long> originalActivityIds) {
        transactions.executeWithoutResult(status -> {
            activityRepository.deleteAll(activityRepository.findAll().stream()
                    .filter(activity -> !originalActivityIds.contains(activity.getId()))
                    .toList());
            activityRepository.flush();

            userRepository.findByIdIncludingDeleted(users.firstId()).ifPresent(first -> {
                first.setActive(true);
                first.unlock();
                userRepository.save(first);
            });
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", users.secondId());
        });
    }

    private static User newAdmin(String prefix) {
        return UserFactory.newAdminCreated(
                prefix + "-" + UUID.randomUUID() + "@ksh.test",
                "encoded-password",
                "Concurrency Admin",
                Role.ADMIN,
                true,
                null,
                null);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrency latch timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrency test interrupted", interrupted);
        }
    }

    private record TestUsers(Long firstId, Long secondId) {
    }

    @TestConfiguration
    static class JsonConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
