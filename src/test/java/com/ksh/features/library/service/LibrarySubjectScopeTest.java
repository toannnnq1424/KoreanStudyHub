package com.ksh.features.library.service;

import com.ksh.entities.Subject;
import com.ksh.entities.User;
import com.ksh.features.admin.subjects.repository.SubjectRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.leader.service.LeaderSubjectResolver;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class LibrarySubjectScopeTest {
    @Test void leaderCanReadOtherSubjectsWithoutManagingThem() {
        var users = mock(UserRepository.class);
        var subjects = mock(SubjectRepository.class);
        var leaders = mock(LeaderSubjectResolver.class);
        var actor = mock(User.class);
        when(users.findById(7L)).thenReturn(Optional.of(actor));
        when(actor.getRole()).thenReturn(Role.LEADER);
        var own = mock(Subject.class); var other = mock(Subject.class);
        when(own.getId()).thenReturn(1L); when(own.getCode()).thenReturn("KOR111");
        when(other.getCode()).thenReturn("KOR222");
        when(subjects.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(own, other));
        when(leaders.resolveAll(7L)).thenReturn(List.of(own));
        var resolver = new LibrarySubjectResolver(users, subjects, leaders);
        assertThat(resolver.allowed(7L, Role.LEADER)).containsExactly(own, other);
        assertThat(resolver.manages(7L, Role.LEADER, 1L)).isTrue();
        assertThat(resolver.manages(7L, Role.LEADER, 2L)).isFalse();
    }
}
