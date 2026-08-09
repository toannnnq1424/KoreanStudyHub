package com.ksh.features.classes.service;

import com.ksh.entities.Enrollment;
import com.ksh.entities.User;
import com.ksh.entities.UserFactory;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClassJoinRequestQuickViewServiceTest {

    private final EnrollmentRepository repository = mock(EnrollmentRepository.class);
    private final ClassJoinRequestQuickViewService service =
            new ClassJoinRequestQuickViewService(repository);

    @Test
    void emptyClassPageDoesNotQueryDatabase() {
        assertThat(service.forOwnedClasses(List.of(), 7L)).isEmpty();
        verify(repository, never()).findPendingOwnedRequests(List.of(), 7L);
    }

    @Test
    void pendingStudentsAreGroupedByOwnedClass() {
        User first = user(31L, "An", "an@ksh.edu.vn");
        User second = user(32L, "Bình", "binh@ksh.edu.vn");
        Enrollment a = Enrollment.createPending(first, 10L, Enrollment.JoinedVia.REQUEST, null);
        Enrollment b = Enrollment.createPending(second, 10L, Enrollment.JoinedVia.REQUEST, null);
        when(repository.findPendingOwnedRequests(List.of(10L, 11L), 7L))
                .thenReturn(List.of(a, b));

        var result = service.forOwnedClasses(List.of(10L, 11L), 7L);

        assertThat(result).containsOnlyKeys(10L);
        assertThat(result.get(10L)).extracting(
                ClassJoinRequestQuickViewService.PendingJoinRow::email)
                .containsExactly("an@ksh.edu.vn", "binh@ksh.edu.vn");
    }

    private static User user(Long id, String name, String email) {
        User user = UserFactory.newAdminCreated(email, "hash", name,
                Role.STUDENT, true, null, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
