package com.ksh.features.lecturer.service.support;

import com.ksh.entities.ClassEntity;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.classes.repository.EnrollmentRepository.ClassCount;
import com.ksh.features.classes.repository.EnrollmentRepository.ClassUserId;
import com.ksh.features.classes.service.support.ProgressMath;
import com.ksh.features.lecturer.dto.LecturerDashboardDtos.ClassDashboardRow;
import com.ksh.features.lessons.repository.LessonRepository;
import com.ksh.features.lessons.repository.LessonRepository.ClassLessonId;
import com.ksh.features.progress.repository.LearningProgressRepository;
import com.ksh.features.progress.repository.LearningProgressRepository.UserLessonId;
import com.ksh.features.tests.entity.Test;
import com.ksh.features.tests.entity.TestAttempt;
import com.ksh.features.tests.repository.TestRepository;
import com.ksh.features.tests.repository.TestAttemptRepository;
import com.ksh.features.assignments.entity.Assignment;
import com.ksh.features.assignments.entity.AssignmentSubmission;
import com.ksh.features.assignments.repository.AssignmentRepository;
import com.ksh.features.assignments.repository.AssignmentSubmissionRepository;
import com.ksh.security.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.ksh.common.IConstant.DEFAULT_TEACHING_PAGE_SIZE;
import static com.ksh.common.IConstant.MAX_TEACHING_PAGE_SIZE;

/**
 * Batch loaders + table window helpers for {@code LecturerDashboardService}.
 * Kept separate so the orchestration service stays under the ~200-line guideline.
 */
@Component
public class LecturerDashboardQuerySupport {

    private final ClassRepository classRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LessonRepository lessonRepository;
    private final LearningProgressRepository progressRepository;
    private final TestRepository testRepository;
    private final TestAttemptRepository testAttemptRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository submissionRepository;

    public LecturerDashboardQuerySupport(ClassRepository classRepository,
                                         EnrollmentRepository enrollmentRepository,
                                         LessonRepository lessonRepository,
                                         LearningProgressRepository progressRepository,
                                         TestRepository testRepository,
                                         TestAttemptRepository testAttemptRepository,
                                         AssignmentRepository assignmentRepository,
                                         AssignmentSubmissionRepository submissionRepository) {
        this.classRepository = classRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.lessonRepository = lessonRepository;
        this.progressRepository = progressRepository;
        this.testRepository = testRepository;
        this.testAttemptRepository = testAttemptRepository;
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
    }

    /** Scope mirrors {@code ClassesService#listForUser}. */
    public List<ClassEntity> loadScopedClasses(Long userId, Role role) {
        if (role == Role.LECTURER) {
            return classRepository.findAllByLecturerIdOrderByCreatedAtDesc(userId);
        }
        return classRepository.findAllByOrderByCreatedAtDesc();
    }

    public Map<Long, Long> loadActiveStudentCounts(List<Long> classIds) {
        Map<Long, Long> map = new HashMap<>();
        for (ClassCount row : enrollmentRepository.countActiveGroupedByClassIds(classIds)) {
            map.put(row.getClassId(), row.getCnt() == null ? 0L : row.getCnt());
        }
        return map;
    }

    public Map<Long, List<Long>> loadPublishedLessonIds(List<Long> classIds) {
        Map<Long, List<Long>> map = new HashMap<>();
        for (ClassLessonId row : lessonRepository.findPublishedLessonIdsByClassIds(classIds)) {
            map.computeIfAbsent(row.getClassId(), k -> new ArrayList<>()).add(row.getLessonId());
        }
        return map;
    }

    public Map<Long, List<Long>> loadActiveStudentIds(List<Long> classIds) {
        Map<Long, List<Long>> map = new HashMap<>();
        for (ClassUserId row : enrollmentRepository.findActiveUserIdsByClassIds(classIds)) {
            map.computeIfAbsent(row.getClassId(), k -> new ArrayList<>()).add(row.getUserId());
        }
        return map;
    }

    /**
     * Completed lesson sets keyed by user. Empty when no published lessons exist
     * (avoids invalid empty {@code IN ()} SQL).
     */
    public Map<Long, Set<Long>> loadCompletedLessonSets(Map<Long, List<Long>> lessonIdsByClass) {
        List<Long> allLessonIds = new ArrayList<>();
        for (List<Long> ids : lessonIdsByClass.values()) {
            allLessonIds.addAll(ids);
        }
        if (allLessonIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Set<Long>> completed = new HashMap<>();
        for (UserLessonId pair : progressRepository.findCompletedUserLessonPairs(allLessonIds)) {
            completed.computeIfAbsent(pair.getUserId(), k -> new HashSet<>())
                    .add(pair.getLessonId());
        }
        return completed;
    }

    /**
     * Mean of each ACTIVE student's completion % in the class.
     * Zero students or zero published lessons → 0.
     */
    public int classAveragePercent(List<Long> studentIds,
                                   List<Long> publishedLessonIds,
                                   Map<Long, Set<Long>> completedByUser) {
        if (studentIds.isEmpty() || publishedLessonIds.isEmpty()) {
            return 0;
        }
        Set<Long> publishedSet = new HashSet<>(publishedLessonIds);
        int total = publishedLessonIds.size();
        int sum = 0;
        for (Long studentId : studentIds) {
            Set<Long> completed = completedByUser.getOrDefault(studentId, Set.of());
            int completedCount = 0;
            for (Long lessonId : completed) {
                if (publishedSet.contains(lessonId)) {
                    completedCount++;
                }
            }
            sum += ProgressMath.percent(completedCount, total);
        }
        return (int) Math.round((double) sum / studentIds.size());
    }

    /**
     * Completion across every published class learning item: lessons, tests and
     * assignments. Each item has equal weight and each ACTIVE student contributes
     * one percentage to the class mean.
     */
    public Map<Long, Integer> loadAverageCompletionPercents(
            List<Long> classIds,
            Map<Long, List<Long>> studentIdsByClass,
            Map<Long, List<Long>> lessonIdsByClass,
            Map<Long, Set<Long>> completedLessonsByUser) {
        List<Test> tests = testRepository
                .findByClassIdInAndStatusOrderByUpdatedAtDesc(classIds, "PUBLISHED").stream()
                .filter(test -> !"PRACTICE".equals(test.getType()))
                .toList();
        List<Assignment> assignments = assignmentRepository.findVisibleByClassIds(classIds);

        Map<Long, List<Long>> testIdsByClass = new HashMap<>();
        for (Test test : tests) {
            testIdsByClass.computeIfAbsent(test.getClassId(), ignored -> new ArrayList<>())
                    .add(test.getId());
        }
        Map<Long, List<Long>> assignmentIdsByClass = new HashMap<>();
        for (Assignment assignment : assignments) {
            assignmentIdsByClass.computeIfAbsent(assignment.getClassId(), ignored -> new ArrayList<>())
                    .add(assignment.getId());
        }

        Map<Long, Set<Long>> completedTestsByUser = new HashMap<>();
        if (!tests.isEmpty()) {
            for (TestAttempt attempt : testAttemptRepository.findCompletedByTestIds(
                    tests.stream().map(Test::getId).toList())) {
                completedTestsByUser.computeIfAbsent(attempt.getUserId(), ignored -> new HashSet<>())
                        .add(attempt.getTestId());
            }
        }
        Map<Long, Set<Long>> completedAssignmentsByUser = new HashMap<>();
        if (!assignments.isEmpty()) {
            for (AssignmentSubmission submission : submissionRepository.findAllByAssignmentIds(
                    assignments.stream().map(Assignment::getId).toList())) {
                completedAssignmentsByUser
                        .computeIfAbsent(submission.getUserId(), ignored -> new HashSet<>())
                        .add(submission.getAssignmentId());
            }
        }

        Map<Long, Integer> result = new HashMap<>();
        for (Long classId : classIds) {
            List<Long> students = studentIdsByClass.getOrDefault(classId, List.of());
            Set<Long> lessonIds = new HashSet<>(lessonIdsByClass.getOrDefault(classId, List.of()));
            Set<Long> testIds = new HashSet<>(testIdsByClass.getOrDefault(classId, List.of()));
            Set<Long> assignmentIds = new HashSet<>(assignmentIdsByClass.getOrDefault(classId, List.of()));
            int itemCount = lessonIds.size() + testIds.size() + assignmentIds.size();
            if (students.isEmpty() || itemCount == 0) {
                result.put(classId, 0);
                continue;
            }
            int sum = 0;
            for (Long studentId : students) {
                int completed = intersectionSize(completedLessonsByUser.get(studentId), lessonIds)
                        + intersectionSize(completedTestsByUser.get(studentId), testIds)
                        + intersectionSize(completedAssignmentsByUser.get(studentId), assignmentIds);
                sum += ProgressMath.percent(completed, itemCount);
            }
            result.put(classId, (int) Math.round((double) sum / students.size()));
        }
        return result;
    }

    private static int intersectionSize(Set<Long> completed, Set<Long> scoped) {
        if (completed == null || completed.isEmpty() || scoped.isEmpty()) return 0;
        int count = 0;
        for (Long id : completed) if (scoped.contains(id)) count++;
        return count;
    }

    /** Case-insensitive substring match on class name or code. */
    public List<ClassDashboardRow> filterByQuery(List<ClassDashboardRow> rows, String q) {
        if (q == null || q.isBlank()) {
            return rows;
        }
        String needle = q.trim().toLowerCase(Locale.ROOT);
        List<ClassDashboardRow> out = new ArrayList<>();
        for (ClassDashboardRow row : rows) {
            String name = row.name() == null ? "" : row.name().toLowerCase(Locale.ROOT);
            String code = row.code() == null ? "" : row.code().toLowerCase(Locale.ROOT);
            if (name.contains(needle) || code.contains(needle)) {
                out.add(row);
            }
        }
        return out;
    }

    /** Clamps page/size and returns a Spring {@link Page} window. */
    public Page<ClassDashboardRow> paginate(List<ClassDashboardRow> rows, int page, int size) {
        int safeSize = size <= 0 ? DEFAULT_TEACHING_PAGE_SIZE
                : Math.min(size, MAX_TEACHING_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        int total = rows.size();
        // Clamp absurd offsets to an empty last window (no int overflow).
        long offset = (long) safePage * safeSize;
        int from = (int) Math.min(offset, total);
        int to = Math.min(from + safeSize, total);
        return new PageImpl<>(new ArrayList<>(rows.subList(from, to)),
                PageRequest.of(safePage, safeSize), total);
    }
}
