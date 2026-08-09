package com.ksh.features.classes.service;

import com.ksh.entities.Enrollment;
import com.ksh.entities.User;
import com.ksh.features.classes.repository.EnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Batch read model for approving join requests from the lecturer class list. */
@Service
public class ClassJoinRequestQuickViewService {

    private final EnrollmentRepository enrollmentRepository;

    public ClassJoinRequestQuickViewService(EnrollmentRepository enrollmentRepository) {
        this.enrollmentRepository = enrollmentRepository;
    }

    @Transactional(readOnly = true)
    public Map<Long, List<PendingJoinRow>> forOwnedClasses(Collection<Long> classIds,
                                                           Long ownerId) {
        if (classIds == null || classIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<PendingJoinRow>> grouped = new LinkedHashMap<>();
        for (Enrollment enrollment : enrollmentRepository.findPendingOwnedRequests(classIds, ownerId)) {
            User student = enrollment.getUser();
            PendingJoinRow row = new PendingJoinRow(student.getId(), student.getFullName(),
                    student.getEmail());
            grouped.computeIfAbsent(enrollment.getClassId(), ignored -> new java.util.ArrayList<>())
                    .add(row);
        }
        return grouped;
    }

    public record PendingJoinRow(Long userId, String fullName, String email) {}
}
