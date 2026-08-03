package com.ksh.features.student.controller;

import com.ksh.features.messaging.controller.StudentClassMessagesController;
import com.ksh.features.progress.controller.LearningProgressController;
import com.ksh.features.tests.controller.StudentClassTestsController;
import com.ksh.security.Roles;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

/** Prevents elevated roles from entering learner-only class routes. */
class StudentClassRoleBoundaryContractTest {

    @Test
    void everyLearnerClassControllerRequiresExactStudentRole() {
        assertStudentGate(StudentClassesController.class);
        assertStudentGate(StudentLessonsController.class);
        assertStudentGate(StudentClassTestsController.class);
        assertStudentGate(StudentClassMessagesController.class);
        assertStudentGate(LearningProgressController.class);
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
