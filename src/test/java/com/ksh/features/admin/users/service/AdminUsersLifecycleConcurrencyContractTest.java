package com.ksh.features.admin.users.service;

import com.ksh.features.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUsersLifecycleConcurrencyContractTest {

    @Test
    void usable_admin_count_excludes_every_login_blocking_state() throws Exception {
        Method method = UserRepository.class.getMethod("countActiveAdmins", String.class);
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(normalize(query.value()))
                .contains("role = :role")
                .contains("is_active = 1")
                .contains("is_locked = 0")
                .contains("is_deleted = 0");
    }

    @Test
    void mutex_locks_one_stable_admin_row_including_soft_deleted_rows() throws Exception {
        Method method = UserRepository.class.getMethod(
                "findAdminLifecycleMutexForUpdate", String.class);
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.nativeQuery()).isTrue();
        assertThat(normalize(query.value()))
                .contains("where role = :role")
                .contains("order by id")
                .contains("limit 1")
                .contains("for update");
    }

    @Test
    void pool_reducing_mutations_lock_mutex_before_their_distinct_targets() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/ksh/features/admin/users/service/AdminUsersLifecycleService.java"),
                StandardCharsets.UTF_8);

        assertLockOrder(source, "void deactivate(Long id");
        assertLockOrder(source, "void lock(Long id");
        assertLockOrder(source, "void softDelete(Long id");
    }

    private static void assertLockOrder(String source, String signature) {
        int method = source.indexOf(signature);
        int mutex = source.indexOf("lockAdminLifecycleMutex();", method);
        int target = source.indexOf("lockForLifecycle(id)", method);

        assertThat(method).as(signature + " must exist").isGreaterThanOrEqualTo(0);
        assertThat(mutex).as(signature + " must acquire the shared mutex").isGreaterThan(method);
        assertThat(target).as(signature + " must lock its target after the mutex").isGreaterThan(mutex);
    }

    private static String normalize(String sql) {
        return sql.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
