package com.ksh.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoleNavigationTest {

    @Test
    void homeUrlStartsEachRoleInItsOwnWorkspace() {
        assertThat(RoleNavigation.homeUrl(authentication(Roles.STUDENT)))
                .isEqualTo("/my/classes");
        assertThat(RoleNavigation.homeUrl(authentication(Roles.LECTURER)))
                .isEqualTo("/lecturer/classes");
        assertThat(RoleNavigation.homeUrl(authentication(Roles.LEADER)))
                .isEqualTo("/leader");
        assertThat(RoleNavigation.homeUrl(authentication(Roles.ADMIN)))
                .isEqualTo("/admin/dashboard");
    }

    @Test
    void staleWorkspaceFromAnotherRoleCannotBeResumed() {
        Authentication student = authentication(Roles.STUDENT);
        Authentication lecturer = authentication(Roles.LECTURER);

        assertThat(RoleNavigation.canResume(student, "/lecturer/classes")).isFalse();
        assertThat(RoleNavigation.canResume(lecturer, "/my/classes/1/lessons")).isFalse();
        assertThat(RoleNavigation.canResume(lecturer, "/my/tests/8/take")).isFalse();
    }

    @Test
    void deepLinksAndSharedAuthenticatedPagesRemainResumable() {
        Authentication student = authentication(Roles.STUDENT);

        assertThat(RoleNavigation.canResume(student, "/j/abc123")).isFalse();
        assertThat(RoleNavigation.canResume(student, "/my/flashcards/7/edit")).isTrue();
    }

    @Test
    void practiceNavigationMatchesStudentAndLecturerAreasWithoutChangingBusinessRules() {
        Authentication student = authentication(Roles.STUDENT);
        Authentication lecturer = authentication(Roles.LECTURER);
        Authentication leader = authentication(Roles.LEADER);

        assertThat(RoleNavigation.canResume(student, "/practice/progress")).isTrue();
        assertThat(RoleNavigation.canResume(student, "/practice/manage")).isFalse();
        assertThat(RoleNavigation.canResume(lecturer, "/practice/manage/revisions")).isTrue();
        assertThat(RoleNavigation.canResume(lecturer, "/practice/attempts/12")).isFalse();
        assertThat(RoleNavigation.canResume(leader, "/practice")).isFalse();
    }

    private static Authentication authentication(String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                "user", "password",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
