package com.ksh.features.leader;

import com.ksh.features.leader.controller.LeaderController;
import com.ksh.features.leader.service.*;
import com.ksh.security.KshUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.ExtendedModelMap;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeaderPortfolioRoleTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean LeaderManagedSubjectService subjects() { return mock(LeaderManagedSubjectService.class); }
        @Bean LeaderController controller(LeaderManagedSubjectService service) {
            return new LeaderController(mock(LeaderDashboardService.class),
                    mock(LeaderLecturerAssignmentService.class), mock(LeaderReportService.class), service);
        }
    }

    @Test void only_leader_can_open_portfolio_and_detail() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(LeaderController.class);
            var service = context.getBean(LeaderManagedSubjectService.class);
            var user = mock(KshUserDetails.class);
            when(user.getId()).thenReturn(7L);
            for (String role : List.of("STUDENT", "LECTURER", "ADMIN")) {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        "user", "unused", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
                assertThatThrownBy(() -> controller.subjects(user, new ExtendedModelMap())).isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(() -> controller.subjectDetail(1L, user, new ExtendedModelMap())).isInstanceOf(AccessDeniedException.class);
            }
            verifyNoInteractions(service);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "leader", "unused", List.of(new SimpleGrantedAuthority("ROLE_LEADER"))));
            when(service.list(7L)).thenReturn(List.of());
            var model = new ExtendedModelMap();
            assertThat(controller.subjects(user, model)).isEqualTo("leader/subjects");
            assertThat(model).containsEntry("managedSubjectCount", 0).doesNotContainKey("leaderSubject");
        } finally { SecurityContextHolder.clearContext(); }
    }
}
