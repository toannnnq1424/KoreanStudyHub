package com.ksh.features.leader.service;

import com.ksh.entities.Subject;
import com.ksh.features.admin.subjects.repository.SubjectRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeaderManagedSubjectServiceTest {
    private final SubjectRepository repository = mock(SubjectRepository.class);
    private final LeaderManagedSubjectService service = new LeaderManagedSubjectService(repository);

    private Subject subject(long id, Long leader, boolean active) {
        var s = new Subject("Môn " + id, "KOR" + id, "Mô tả", active);
        ReflectionTestUtils.setField(s, "id", id);
        s.assignLeader(leader);
        return s;
    }

    @Test void lists_active_assigned_subjects_and_preserves_repository_order() {
        when(repository.findByLeaderUserIdOrderByCodeAsc(7L)).thenReturn(List.of(
                subject(1, 7L, true), subject(2, 7L, false), subject(3, 7L, true)));
        assertThat(service.list(7L)).extracting(v -> v.id()).containsExactly(1L, 3L);
        verify(repository).findByLeaderUserIdOrderByCodeAsc(7L);
        verifyNoMoreInteractions(repository);
    }

    @Test void empty_assignment_is_not_replaced_by_legacy_primary_subject() {
        when(repository.findByLeaderUserIdOrderByCodeAsc(7L)).thenReturn(List.of());
        assertThat(service.list(7L)).isEmpty();
        verify(repository).findByLeaderUserIdOrderByCodeAsc(7L);
        verifyNoMoreInteractions(repository);
    }

    @Test void rejects_foreign_unassigned_and_inactive_subjects() {
        for (Subject s : List.of(subject(1, 8L, true), subject(1, null, true), subject(1, 7L, false))) {
            when(repository.findById(1L)).thenReturn(Optional.of(s));
            assertThatThrownBy(() -> service.require(7L, 1L)).isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test void missing_subject_is_not_found() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.require(7L, 99L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test void reassignment_is_checked_again_on_every_request() {
        var s = subject(1, 7L, true);
        when(repository.findById(1L)).thenReturn(Optional.of(s));
        assertThat(service.require(7L, 1L).id()).isEqualTo(1L);
        s.assignLeader(8L);
        assertThatThrownBy(() -> service.require(7L, 1L)).isInstanceOf(AccessDeniedException.class);
        verify(repository, times(2)).findById(1L);
        verifyNoMoreInteractions(repository);
    }
}
