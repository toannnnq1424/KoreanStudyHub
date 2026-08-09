package com.ksh.features.gradebook.service;

import com.ksh.entities.Enrollment;
import com.ksh.features.assignments.entity.Assignment;
import com.ksh.features.assignments.entity.AssignmentFeedback;
import com.ksh.features.assignments.entity.AssignmentSubmission;
import com.ksh.features.assignments.repository.AssignmentFeedbackRepository;
import com.ksh.features.assignments.repository.AssignmentRepository;
import com.ksh.features.assignments.repository.AssignmentSubmissionRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.gradebook.dto.GradebookView;
import com.ksh.features.gradebook.dto.GradebookView.Cell;
import com.ksh.features.gradebook.dto.GradebookView.Column;
import com.ksh.features.gradebook.dto.GradebookView.StudentRow;
import com.ksh.features.tests.entity.Test;
import com.ksh.features.tests.entity.TestAttempt;
import com.ksh.features.tests.repository.TestAttemptRepository;
import com.ksh.features.tests.repository.TestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds a live grade matrix from enrollment, test and assignment records. */
@Service
public class ClassGradebookService {
    private static final BigDecimal TEN = BigDecimal.TEN;

    private final EnrollmentRepository enrollmentRepository;
    private final TestRepository testRepository;
    private final TestAttemptRepository attemptRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository submissionRepository;
    private final AssignmentFeedbackRepository feedbackRepository;

    public ClassGradebookService(EnrollmentRepository enrollmentRepository,
                                 TestRepository testRepository,
                                 TestAttemptRepository attemptRepository,
                                 AssignmentRepository assignmentRepository,
                                 AssignmentSubmissionRepository submissionRepository,
                                 AssignmentFeedbackRepository feedbackRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.testRepository = testRepository;
        this.attemptRepository = attemptRepository;
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional(readOnly = true)
    public GradebookView build(Long classId) {
        List<Enrollment> enrollments = enrollmentRepository
                .findAllByClassIdAndStatusOrderByJoinedAtDesc(classId, "ACTIVE");
        Map<Long, Map<String, Cell>> cellsByStudent = new LinkedHashMap<>();
        enrollments.forEach(e -> cellsByStudent.put(e.getUser().getId(), new LinkedHashMap<>()));

        List<Column> testColumns = loadTestGrades(classId, cellsByStudent);
        List<Column> assignmentColumns = loadAssignmentGrades(classId, cellsByStudent);
        List<Column> columns = new ArrayList<>(testColumns.size() + assignmentColumns.size());
        columns.addAll(testColumns);
        columns.addAll(assignmentColumns);

        List<StudentRow> students = enrollments.stream()
                .map(e -> new StudentRow(e.getUser().getId(), e.getUser().getFullName(),
                        e.getUser().getEmail(), cellsByStudent.get(e.getUser().getId())))
                .toList();
        return new GradebookView(List.copyOf(columns), students);
    }

    private List<Column> loadTestGrades(Long classId, Map<Long, Map<String, Cell>> cellsByStudent) {
        List<Test> tests = testRepository.findGradebookTestsByClassId(classId);
        if (tests.isEmpty()) return List.of();
        Map<String, TestAttempt> newest = new LinkedHashMap<>();
        attemptRepository.findCompletedByTestIds(tests.stream().map(Test::getId).toList()).forEach(attempt ->
                newest.putIfAbsent(attempt.getTestId() + ":" + attempt.getUserId(), attempt));

        List<Column> columns = new ArrayList<>();
        for (Test test : tests) {
            String key = "TEST:" + test.getId();
            BigDecimal max = newest.values().stream()
                    .filter(a -> a.getTestId().equals(test.getId()) && validTotal(a.getTotalPoints()))
                    .map(TestAttempt::getTotalPoints).findFirst().orElse(BigDecimal.valueOf(100));
            boolean hasGrade = false;
            for (TestAttempt attempt : newest.values()) {
                if (!attempt.getTestId().equals(test.getId()) || attempt.getScore() == null
                        || !cellsByStudent.containsKey(attempt.getUserId())) continue;
                BigDecimal total = validTotal(attempt.getTotalPoints()) ? attempt.getTotalPoints() : max;
                cellsByStudent.get(attempt.getUserId()).put(key, cell(attempt.getScore(), total));
                hasGrade = true;
            }
            if (hasGrade) columns.add(new Column(key, "TEST", test.getTitle(), max));
        }
        return columns;
    }

    private List<Column> loadAssignmentGrades(Long classId, Map<Long, Map<String, Cell>> cellsByStudent) {
        List<Assignment> assignments = assignmentRepository.findAllByClassIdNotDeleted(classId);
        if (assignments.isEmpty()) return List.of();
        List<Long> ids = assignments.stream().map(Assignment::getId).toList();
        List<AssignmentSubmission> submissions = submissionRepository.findAllByAssignmentIds(ids);
        if (submissions.isEmpty()) return List.of();
        Map<Long, AssignmentSubmission> submissionById = submissions.stream()
                .collect(Collectors.toMap(AssignmentSubmission::getId, Function.identity()));
        Map<Long, AssignmentFeedback> feedbackBySubmission = feedbackRepository
                .findAllBySubmissionIdIn(submissionById.keySet()).stream()
                .collect(Collectors.toMap(AssignmentFeedback::getSubmissionId, Function.identity()));

        List<Column> columns = new ArrayList<>();
        for (Assignment assignment : assignments) {
            String key = "ASSIGNMENT:" + assignment.getId();
            boolean hasGrade = false;
            for (AssignmentSubmission submission : submissions) {
                if (!submission.getAssignmentId().equals(assignment.getId())
                        || !cellsByStudent.containsKey(submission.getUserId())) continue;
                AssignmentFeedback feedback = feedbackBySubmission.get(submission.getId());
                if (feedback == null) continue;
                cellsByStudent.get(submission.getUserId()).put(key,
                        cell(feedback.getScore(), assignment.getMaxScore()));
                hasGrade = true;
            }
            if (hasGrade) columns.add(new Column(key, "ASSIGNMENT", assignment.getTitle(), assignment.getMaxScore()));
        }
        return columns;
    }

    private static boolean validTotal(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private static Cell cell(BigDecimal score, BigDecimal max) {
        BigDecimal safeMax = validTotal(max) ? max : BigDecimal.valueOf(100);
        BigDecimal normalized = score.multiply(TEN).divide(safeMax, 2, RoundingMode.HALF_UP);
        return new Cell(score.stripTrailingZeros(), safeMax.stripTrailingZeros(), normalized);
    }
}
