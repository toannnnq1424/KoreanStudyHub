package com.ksh.features.admin.permissions.service;

import com.ksh.features.auth.repository.UserRepository;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionMutationConcurrencyContractTest {

    @Test
    void userLockIsPessimisticWrite() throws Exception {
        Method method = UserRepository.class.getMethod("findByIdForUpdate", Long.class);
        Lock lock = method.getAnnotation(Lock.class);
        assertNotNull(lock);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }

    @Test
    void bothOverrideMutationPathsLockUserBeforeReadingOverride() throws Exception {
        assertBefore(read("admin/users/service/UserPermissionToggleService.java"),
                "userRepository.findByIdForUpdate(userId)",
                "overrideRepository.findByUserIdAndPermissionId");
        assertBefore(read("admin/permissions/service/PermissionOverrideService.java"),
                "lockUser(form.userId())",
                "overrideRepository.findByUserIdAndPermissionId");
    }

    @Test
    void permissionCacheEvictionsAreRegisteredAfterCommit() throws Exception {
        assertTrue(read("admin/users/service/UserPermissionToggleService.java")
                .contains("TransactionLifecycle.afterCommit(() -> permissionResolver.evictUser"));
        assertTrue(read("admin/permissions/service/PermissionOverrideService.java")
                .contains("TransactionLifecycle.afterCommit(() -> permissionResolver.evictUser"));
        assertTrue(read("admin/permissions/service/PermissionMatrixService.java")
                .contains("TransactionLifecycle.afterCommit(() -> permissionResolver.evictRole"));
    }

    @Test
    void overrideDeactivateLocksItsUserBeforeLoadingMutableEntity() throws Exception {
        String source = read("admin/permissions/service/PermissionOverrideService.java");
        int method = source.indexOf("void deactivate(Long overrideId");
        int userLookup = source.indexOf("findUserIdById(overrideId)", method);
        int lock = source.indexOf("lockUser(targetUserId)", userLookup);
        int entityLoad = source.indexOf("overrideRepository.findById(overrideId)", lock);
        assertTrue(method >= 0 && userLookup > method && lock > userLookup && entityLoad > lock);
    }

    private static void assertBefore(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second, firstIndex);
        assertTrue(firstIndex >= 0 && secondIndex > firstIndex);
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/ksh/features").resolve(relative),
                StandardCharsets.UTF_8);
    }
}
