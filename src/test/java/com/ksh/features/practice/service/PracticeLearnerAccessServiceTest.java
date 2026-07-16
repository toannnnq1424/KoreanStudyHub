package com.ksh.features.practice.service;

import com.ksh.entities.Enrollment;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.User;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeLearnerAccessServiceTest {

    private static final long USER_ID = 7L;

    @Mock
    private PracticeSetRepository setRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @InjectMocks
    private PracticeLearnerAccessService service;

    @Test
    void globalAndOwnerPublishedSetsAreVisible() {
        PracticeSet global = set(PracticeSet.SCOPE_GLOBAL, null, 99L,
                PracticeSet.STATUS_PUBLISHED);
        PracticeSet ownerClassSet = set(PracticeSet.SCOPE_CLASS, 15L, USER_ID,
                PracticeSet.STATUS_PUBLISHED);

        assertThat(service.isVisiblePublishedSet(global, USER_ID)).isTrue();
        assertThat(service.isVisiblePublishedSet(ownerClassSet, USER_ID)).isTrue();
    }

    @Test
    void classSetRequiresAnActiveEnrollment() {
        PracticeSet classSet = set(PracticeSet.SCOPE_CLASS, 15L, 99L,
                PracticeSet.STATUS_PUBLISHED);
        User user = org.mockito.Mockito.mock(User.class);
        Enrollment enrollment = new Enrollment(user, 15L, "CODE", null);

        when(enrollmentRepository.findByUserIdAndClassId(USER_ID, 15L))
                .thenReturn(Optional.of(enrollment));

        assertThat(service.isVisiblePublishedSet(classSet, USER_ID)).isTrue();
        verify(enrollmentRepository).findByUserIdAndClassId(USER_ID, 15L);
    }

    @Test
    void unrelatedOrUnpublishedSetIsHiddenAsNotFound() {
        PracticeSet unrelated = set(PracticeSet.SCOPE_CLASS, 15L, 99L,
                PracticeSet.STATUS_PUBLISHED);
        when(setRepository.findById(21L)).thenReturn(Optional.of(unrelated));
        when(enrollmentRepository.findByUserIdAndClassId(USER_ID, 15L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireVisiblePublishedSet(21L, USER_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Practice set not found");

        PracticeSet archived = set(PracticeSet.SCOPE_GLOBAL, null, 99L,
                PracticeSet.STATUS_ARCHIVED);
        assertThat(service.isVisiblePublishedSet(archived, USER_ID)).isFalse();
    }

    @Test
    void activeClassIdsAreDistinctAndExcludeInactiveEnrollmentsAtRepositoryBoundary() {
        User user = org.mockito.Mockito.mock(User.class);
        Enrollment first = new Enrollment(user, 15L, "CODE", null);
        Enrollment duplicate = new Enrollment(user, 15L, "LINK", null);
        Enrollment second = new Enrollment(user, 16L, "IMPORT", null);
        when(enrollmentRepository.findAllByUserIdAndStatusOrderByJoinedAtDesc(
                USER_ID, Enrollment.STATUS_ACTIVE))
                .thenReturn(List.of(first, duplicate, second));

        assertThat(service.activeClassIds(USER_ID)).containsExactly(15L, 16L);
    }

    private PracticeSet set(String scope, Long classId, Long ownerId, String status) {
        return new PracticeSet(
                "Bộ đề", "Mô tả", PracticeSet.SKILL_READING,
                scope, classId, null, "{}", status, ownerId);
    }
}
