package com.ksh.features.practice.manage.authoringcandidate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceKind;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ApplyCommand;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ApplyResultCode;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateState;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CreateCommand;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceSnapshot;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "app.practice.attempt-evaluation.worker-enabled=false",
        "app.practice.attempt-deadline.worker-enabled=false",
        "app.practice.speaking-media.cleanup-worker-enabled=false",
        "app.practice.speaking-prompt-authoring.worker-enabled=false",
        "app.practice.asset-lifecycle.worker-enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "RUN_AIM2_DB_TESTS", matches = "true")
class PracticeAuthoringCandidatePersistenceIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PracticeDraftRepository draftRepository;
    @Autowired
    private PracticeAuthoringCandidateRepository candidateRepository;
    @Autowired
    private PracticeAuthoringCandidateApplyEventRepository eventRepository;
    @Autowired
    private PracticeAuthoringCandidateApplyService applyService;
    @Autowired
    private PracticeAuthoringCandidateService candidateService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void freshSchemaHasExactlyOneSuccessfulMigrationForEveryVersionThrough83() {
        Integer successful = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                Integer.class);
        Integer failed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0",
                Integer.class);
        String latest = jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history",
                String.class);
        Integer candidateTables = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                    'practice_authoring_candidates',
                    'practice_authoring_candidate_apply_events')
                """, Integer.class);

        assertThat(successful).isEqualTo(83);
        assertThat(failed).isZero();
        assertThat(latest).isEqualTo("83");
        assertThat(candidateTables).isEqualTo(2);
    }

    @Test
    @Transactional
    void repositoryPersistsIdempotencyIdentityAndRejectsStaleOptimisticWriter() {
        Long ownerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users ORDER BY id LIMIT 1", Long.class);
        PracticeDraft draft = draftRepository.saveAndFlush(new PracticeDraft(
                "AIM-2 persistence", "", "GLOBAL", null, "DRAFT", ownerId,
                "{\"tests\":[],\"sections\":[]}"));
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 0, 0);
        String json = PracticeAuthoringCandidateTestFixtures
                .candidateEnvelope(objectMapper, true).toString();
        PracticeAuthoringCandidate candidate = new PracticeAuthoringCandidate(
                "33333333-3333-4333-8333-333333333333",
                ownerId, SourceKind.QUICK_EXCEL,
                "practice-quick-excel-v1",
                PracticeAuthoringCandidateTestFixtures.SOURCE_DIGEST,
                "db-proof-1", "reading.xlsx", SourceOperation.NONE,
                draft.getId(), 1, "READING", "R1", draft.getVersion(),
                json, PracticeAuthoringCandidateTestFixtures.CONTENT_DIGEST,
                now, now.plusDays(7));
        candidate.markNormalized(
                json, PracticeAuthoringCandidateTestFixtures.CONTENT_DIGEST, now);
        candidate.markValidated(
                json, PracticeAuthoringCandidateTestFixtures.CONTENT_DIGEST, now);
        candidate.beginReview(json, now);
        candidateRepository.saveAndFlush(candidate);

        PracticeAuthoringCandidate idempotent = candidateRepository.findIdempotent(
                ownerId, SourceKind.QUICK_EXCEL,
                "practice-quick-excel-v1",
                PracticeAuthoringCandidateTestFixtures.SOURCE_DIGEST,
                "db-proof-1", SourceOperation.NONE,
                draft.getId(), 1, "READING", "R1", draft.getVersion(),
                PracticeAuthoringCandidate.NORMALIZER_VERSION)
                .orElseThrow();
        assertThat(idempotent.getId()).isEqualTo(candidate.getId());
        assertThat(idempotent.getLockVersion()).isEqualTo(0L);

        jdbcTemplate.update("""
                UPDATE practice_authoring_candidates
                SET lock_version = lock_version + 1
                WHERE id = ?
                """, candidate.getId());
        candidate.replaceReview(
                json, "b".repeat(64), ownerId, false, now.plusMinutes(1));

        assertThatThrownBy(() -> candidateRepository.saveAndFlush(candidate))
                .isInstanceOf(OptimisticLockingFailureException.class);
        entityManager.clear();
    }

    @Test
    void realApplyTransactionMutatesOneDraftOnceAndReplaysLedgerResult()
            throws Exception {
        Long ownerId = authorizedLecturer();
        PracticeDraft draft = draftRepository.saveAndFlush(new PracticeDraft(
                "AIM-2 atomic apply", "", "GLOBAL", null, "DRAFT", ownerId,
                PracticeAuthoringCandidateTestFixtures.targetDraft(0)
                        .getDraftJson()));
        String candidateId = UUID.nameUUIDFromBytes(
                ("aim2-candidate-" + draft.getId())
                        .getBytes(StandardCharsets.UTF_8)).toString();
        UUID requestId = UUID.nameUUIDFromBytes(
                ("aim2-apply-" + draft.getId())
                        .getBytes(StandardCharsets.UTF_8));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        var envelope = PracticeAuthoringCandidateTestFixtures
                .candidateEnvelope(objectMapper, true);
        envelope.put("candidateId", candidateId);
        envelope.put("ownerId", ownerId);
        envelope.withObject("/target").put("draftId", draft.getId());
        String json = envelope.toString();
        PracticeAuthoringCandidate candidate = new PracticeAuthoringCandidate(
                candidateId, ownerId, SourceKind.QUICK_EXCEL,
                "practice-quick-excel-v1",
                PracticeAuthoringCandidateTestFixtures.SOURCE_DIGEST,
                "db-apply-" + draft.getId(), "reading.xlsx",
                SourceOperation.NONE, draft.getId(), 1, "READING", "R1",
                draft.getVersion(), json,
                PracticeAuthoringCandidateTestFixtures.CONTENT_DIGEST,
                now, now.plusDays(7));
        candidate.markNormalized(json,
                PracticeAuthoringCandidateTestFixtures.CONTENT_DIGEST, now);
        candidate.markValidated(json,
                PracticeAuthoringCandidateTestFixtures.CONTENT_DIGEST, now);
        candidate.beginReview(json, now);
        candidate.markReady(json, ownerId, false, now);
        candidate = candidateRepository.saveAndFlush(candidate);
        ApplyCommand command = new ApplyCommand(
                candidateId, requestId, ownerId, candidate.getLockVersion(),
                "sha256:" + candidate.getContentDigest());

        var applied = applyService.apply(command);
        var replayed = applyService.apply(command);

        assertThat(applied.result()).isEqualTo(ApplyResultCode.DRAFT_APPLIED);
        assertThat(replayed.result()).isEqualTo(ApplyResultCode.DRAFT_APPLIED);
        assertThat(replayed.replayed()).isTrue();
        assertThat(replayed.draftVersion()).isEqualTo(applied.draftVersion());
        PracticeDraft reloaded = draftRepository.findById(draft.getId())
                .orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo(1);
        assertThat(objectMapper.readTree(reloaded.getDraftJson())
                .path("sections").get(0).path("groups").size()).isEqualTo(1);
        assertThat(eventRepository.findByCandidateIdAndApplyRequestId(
                candidateId, requestId.toString())).isPresent();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM practice_authoring_candidate_apply_events
                WHERE candidate_id = ?
                """, Integer.class, candidateId)).isEqualTo(1);
        assertThat(candidateRepository.findById(candidateId).orElseThrow()
                .getState()).isEqualTo(
                PracticeAuthoringCandidateModels.CandidateState.APPLIED);
    }

    @Test
    void candidateServicePersistsOnceReusesIdentityAndMarksReady() {
        Long ownerId = authorizedLecturer();
        PracticeDraft draft = draftRepository.saveAndFlush(new PracticeDraft(
                "AIM-2 candidate service", "", "GLOBAL", null, "DRAFT",
                ownerId, PracticeAuthoringCandidateTestFixtures.targetDraft(0)
                .getDraftJson()));
        CreateCommand command = new CreateCommand(
                ownerId,
                new SourceSnapshot(
                        SourceKind.QUICK_EXCEL,
                        "practice-quick-excel-v1",
                        "sha256:" + PracticeAuthoringCandidateTestFixtures
                                .SOURCE_DIGEST,
                        "db-service-" + draft.getId(),
                        "reading.xlsx", SourceOperation.NONE, null),
                new TargetRoute(draft.getId(), 1, "READING", "R1"),
                PracticeAuthoringCandidateTestFixtures
                        .readingGroups(objectMapper, true));

        var created = candidateService.createOrReuse(command);
        var reused = candidateService.createOrReuse(command);
        var ready = candidateService.markReady(
                created.candidateId(), ownerId, created.version());

        assertThat(created.state()).isEqualTo(CandidateState.REVIEWING);
        assertThat(reused.candidateId()).isEqualTo(created.candidateId());
        assertThat(ready.state()).isEqualTo(CandidateState.READY_TO_APPLY);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM practice_authoring_candidates
                WHERE target_draft_id = ? AND source_revision = ?
                """, Integer.class, draft.getId(),
                "db-service-" + draft.getId())).isEqualTo(1);
    }

    private Long authorizedLecturer() {
        return jdbcTemplate.queryForObject("""
                SELECT DISTINCT u.id
                FROM users u
                JOIN v_user_effective_permissions permission
                  ON permission.user_id = u.id
                WHERE u.role = 'LECTURER'
                  AND u.is_active = 1
                  AND u.is_locked = 0
                  AND permission.feature_key = 'practice.edit'
                  AND permission.is_granted = 1
                ORDER BY u.id
                LIMIT 1
                """, Long.class);
    }
}
