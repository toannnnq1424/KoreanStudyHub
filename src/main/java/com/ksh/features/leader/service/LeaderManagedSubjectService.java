package com.ksh.features.leader.service;

import com.ksh.entities.Subject;
import com.ksh.features.admin.subjects.repository.SubjectRepository;
import com.ksh.features.leader.dto.LeaderDtos.ManagedSubject;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read-only governance portfolio; an account's primary subject is not an assignment. */
@Service
public class LeaderManagedSubjectService {

    private final SubjectRepository subjects;

    public LeaderManagedSubjectService(SubjectRepository subjects) {
        this.subjects = subjects;
    }

    @Transactional(readOnly = true)
    public List<ManagedSubject> list(Long leaderId) {
        return subjects.findByLeaderUserIdOrderByCodeAsc(leaderId).stream()
                .filter(Subject::isActive)
                .map(LeaderManagedSubjectService::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public ManagedSubject require(Long leaderId, Long subjectId) {
        Subject subject = subjects.findById(subjectId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy môn học"));
        if (!subject.isActive() || !leaderId.equals(subject.getLeaderUserId())) {
            throw new AccessDeniedException("Môn học ngoài phạm vi quản lý");
        }
        return toView(subject);
    }

    private static ManagedSubject toView(Subject subject) {
        return new ManagedSubject(subject.getId(), subject.getCode(),
                subject.getName(), subject.getDescription());
    }
}
