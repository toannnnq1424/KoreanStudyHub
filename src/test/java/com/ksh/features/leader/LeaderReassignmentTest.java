package com.ksh.features.leader;

import com.ksh.entities.Subject;
import com.ksh.entities.User;
import com.ksh.features.admin.subjects.repository.SubjectRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.leader.service.LeaderSubjectResolver;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class LeaderReassignmentTest {
    @Test void staleUserSubjectCannotOverrideExplicitNewLeader() {
        var subjects = mock(SubjectRepository.class);
        var users = mock(UserRepository.class);
        var user = mock(User.class);
        var subject = mock(Subject.class);
        when(subjects.findByLeaderUserIdOrderByCodeAsc(1L)).thenReturn(List.of());
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(user.getSubjectId()).thenReturn(6L);
        when(subjects.findById(6L)).thenReturn(Optional.of(subject));
        when(subject.isActive()).thenReturn(true);
        when(subject.getLeaderUserId()).thenReturn(2L);
        assertThat(new LeaderSubjectResolver(subjects, users).resolveAll(1L)).isEmpty();
    }
}
