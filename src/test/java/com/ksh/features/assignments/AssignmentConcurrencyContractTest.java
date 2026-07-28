package com.ksh.features.assignments;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AssignmentConcurrencyContractTest {

    @Test
    void submitAndGradeUseTheSamePessimisticLockOrder() throws Exception {
        String student = Files.readString(Path.of(
                "src/main/java/com/ksh/features/assignments/service/StudentAssignmentService.java"));
        String lecturer = Files.readString(Path.of(
                "src/main/java/com/ksh/features/assignments/service/LecturerAssignmentService.java"));
        String assignments = Files.readString(Path.of(
                "src/main/java/com/ksh/features/assignments/repository/AssignmentRepository.java"));
        String submissions = Files.readString(Path.of(
                "src/main/java/com/ksh/features/assignments/repository/AssignmentSubmissionRepository.java"));

        assertThat(student)
                .contains("findByIdAndClassIdNotDeletedForUpdate(assignmentId, classId)")
                .contains("findByAssignmentIdAndUserIdForUpdate(assignmentId, userId)");
        assertThat(lecturer)
                .contains("findByIdAndClassIdNotDeletedForUpdate(assignmentId, classId)")
                .contains("findByIdForUpdate(submissionId)");
        assertThat(assignments).contains("@Lock(LockModeType.PESSIMISTIC_WRITE)");
        assertThat(submissions)
                .contains("findByAssignmentIdAndUserIdForUpdate")
                .contains("findByIdForUpdate");
    }
}
