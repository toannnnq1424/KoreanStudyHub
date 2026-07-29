package com.ksh.features.assignments.controller;

import com.ksh.security.Roles;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the learner assignment surface against role-confusion regressions.
 * Enrollment is still checked by the service, while this outer gate prevents
 * elevated or formerly enrolled accounts from acting through student routes.
 */
class StudentAssignmentRoleBoundaryContractTest {

    @Test
    void studentAssignmentControllerRequiresTheExactStudentRole() {
        PreAuthorize gate = StudentAssignmentController.class.getAnnotation(PreAuthorize.class);

        assertThat(gate).isNotNull();
        assertThat(gate.value()).isEqualTo(Roles.PREAUTH_STUDENT);
        assertThat(gate.value()).isEqualTo("hasRole('STUDENT')");
    }
}
