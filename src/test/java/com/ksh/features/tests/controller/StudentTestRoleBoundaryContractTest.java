package com.ksh.features.tests.controller;

import com.ksh.security.Roles;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

/** Prevents learner exam endpoints from falling back to authenticated-only access. */
class StudentTestRoleBoundaryContractTest {

    @Test
    void everyLearnerTestControllerRequiresExactStudentRole() {
        assertStudentGate(StudentTestController.class);
        assertStudentGate(StudentClassTestsController.class);
        assertStudentGate(StudentPracticeController.class);
        assertStudentGate(TestApiController.class);
    }

    private static void assertStudentGate(Class<?> controller) {
        PreAuthorize gate = controller.getAnnotation(PreAuthorize.class);
        assertThat(gate)
                .as("%s role gate", controller.getSimpleName())
                .isNotNull();
        assertThat(gate.value()).isEqualTo(Roles.PREAUTH_STUDENT);
        assertThat(gate.value()).isEqualTo("hasRole('STUDENT')");
    }
}
