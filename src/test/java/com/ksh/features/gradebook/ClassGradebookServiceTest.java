package com.ksh.features.gradebook;

import com.ksh.entities.Enrollment;
import com.ksh.entities.User;
import com.ksh.entities.UserFactory;
import com.ksh.features.assignments.entity.Assignment;
import com.ksh.features.assignments.entity.AssignmentFeedback;
import com.ksh.features.assignments.entity.AssignmentSubmission;
import com.ksh.features.assignments.repository.AssignmentFeedbackRepository;
import com.ksh.features.assignments.repository.AssignmentRepository;
import com.ksh.features.assignments.repository.AssignmentSubmissionRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.gradebook.service.ClassGradebookService;
import com.ksh.features.tests.repository.TestAttemptRepository;
import com.ksh.features.tests.repository.TestRepository;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyCollection;

class ClassGradebookServiceTest {
    @Test
    void assignmentGradeIsJoinedToActiveStudentAndNormalizedToTen() {
        EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
        TestRepository tests = mock(TestRepository.class);
        TestAttemptRepository attempts = mock(TestAttemptRepository.class);
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        AssignmentSubmissionRepository submissions = mock(AssignmentSubmissionRepository.class);
        AssignmentFeedbackRepository feedback = mock(AssignmentFeedbackRepository.class);
        ClassGradebookService service = new ClassGradebookService(
                enrollments, tests, attempts, assignments, submissions, feedback);

        User student = UserFactory.newAdminCreated("student@test.local", "hash", "Kim Mina",
                Role.STUDENT, true, null, null);
        ReflectionTestUtils.setField(student, "id", 7L);
        Enrollment enrollment = Enrollment.createFor(student, 3L, Enrollment.JoinedVia.REQUEST, null);
        Assignment assignment = new Assignment();
        assignment.setId(11L);
        assignment.setTitle("Bài viết");
        assignment.setMaxScore(BigDecimal.valueOf(40));
        AssignmentSubmission submission = new AssignmentSubmission();
        submission.setId(21L);
        submission.setAssignmentId(11L);
        submission.setUserId(7L);
        AssignmentFeedback grade = new AssignmentFeedback();
        grade.setSubmissionId(21L);
        grade.setScore(BigDecimal.valueOf(30));

        when(enrollments.findAllByClassIdAndStatusOrderByJoinedAtDesc(3L, "ACTIVE"))
                .thenReturn(List.of(enrollment));
        when(tests.findGradebookTestsByClassId(3L)).thenReturn(List.of());
        when(assignments.findAllByClassIdNotDeleted(3L)).thenReturn(List.of(assignment));
        when(submissions.findAllByAssignmentIds(List.of(11L))).thenReturn(List.of(submission));
        when(feedback.findAllBySubmissionIdIn(anyCollection())).thenReturn(List.of(grade));

        var view = service.build(3L);

        assertThat(view.columns()).hasSize(1);
        assertThat(view.students()).hasSize(1);
        assertThat(view.students().get(0).cells().get("ASSIGNMENT:11").normalizedTen())
                .isEqualByComparingTo("7.50");
    }
}
