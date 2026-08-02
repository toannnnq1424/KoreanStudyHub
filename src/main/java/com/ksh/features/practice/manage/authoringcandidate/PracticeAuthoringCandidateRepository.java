package com.ksh.features.practice.manage.authoringcandidate;

import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateState;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceKind;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PracticeAuthoringCandidateRepository
        extends JpaRepository<PracticeAuthoringCandidate, String> {

    Optional<PracticeAuthoringCandidate> findByIdAndOwnerId(
            String id, Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select c from PracticeAuthoringCandidate c
            where c.id = :id and c.ownerId = :ownerId
            """)
    Optional<PracticeAuthoringCandidate> findByIdAndOwnerIdForRead(
            @Param("id") String id,
            @Param("ownerId") Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PracticeAuthoringCandidate c where c.id = :id")
    Optional<PracticeAuthoringCandidate> findByIdForUpdate(
            @Param("id") String id);

    @Query("""
            select c from PracticeAuthoringCandidate c
            where c.ownerId = :ownerId
              and c.sourceKind = :sourceKind
              and c.sourceContractVersion = :contractVersion
              and c.sourceDigest = :sourceDigest
              and c.sourceRevision = :sourceRevision
              and c.sourceOperation = :sourceOperation
              and c.targetDraftId = :draftId
              and c.targetTestNo = :testNo
              and c.targetSkill = :skill
              and c.targetLessonCode = :lessonCode
              and c.baseDraftVersion = :baseDraftVersion
              and c.normalizerVersion = :normalizerVersion
            """)
    Optional<PracticeAuthoringCandidate> findIdempotent(
            @Param("ownerId") Long ownerId,
            @Param("sourceKind") SourceKind sourceKind,
            @Param("contractVersion") String contractVersion,
            @Param("sourceDigest") String sourceDigest,
            @Param("sourceRevision") String sourceRevision,
            @Param("sourceOperation") SourceOperation sourceOperation,
            @Param("draftId") Long draftId,
            @Param("testNo") int testNo,
            @Param("skill") String skill,
            @Param("lessonCode") String lessonCode,
            @Param("baseDraftVersion") int baseDraftVersion,
            @Param("normalizerVersion") String normalizerVersion);

    List<PracticeAuthoringCandidate> findByExpiresAtLessThanEqualAndStateIn(
            LocalDateTime now, Collection<CandidateState> states);
}
