package com.ksh.features.practice.service;

import com.ksh.entities.Enrollment;
import com.ksh.entities.PracticeSet;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PracticeLearnerAccessService {

    private final PracticeSetRepository setRepository;
    private final EnrollmentRepository enrollmentRepository;

    public PracticeLearnerAccessService(PracticeSetRepository setRepository,
                                        EnrollmentRepository enrollmentRepository) {
        this.setRepository = setRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    public PracticeSet requireVisiblePublishedSet(Long setId, Long userId) {
        PracticeSet set = setRepository.findById(setId)
                .orElseThrow(() -> new EntityNotFoundException("Practice set not found"));
        if (!isVisiblePublishedSet(set, userId)) {
            throw new EntityNotFoundException("Practice set not found");
        }
        return set;
    }

    public boolean isVisiblePublishedSet(PracticeSet set, Long userId) {
        if (set == null || userId == null
                || !PracticeSet.STATUS_PUBLISHED.equals(set.getStatus())) {
            return false;
        }
        if (userId.equals(set.getCreatedBy())
                || PracticeSet.SCOPE_GLOBAL.equals(set.getScope())) {
            return true;
        }
        if (!PracticeSet.SCOPE_CLASS.equals(set.getScope()) || set.getClassId() == null) {
            return false;
        }
        return enrollmentRepository.findByUserIdAndClassId(userId, set.getClassId())
                .filter(enrollment -> Enrollment.STATUS_ACTIVE.equals(enrollment.getStatus()))
                .isPresent();
    }

    public List<Long> activeClassIds(Long userId) {
        if (userId == null) return List.of();
        return enrollmentRepository
                .findAllByUserIdAndStatusOrderByJoinedAtDesc(userId, Enrollment.STATUS_ACTIVE)
                .stream()
                .map(Enrollment::getClassId)
                .distinct()
                .toList();
    }
}
