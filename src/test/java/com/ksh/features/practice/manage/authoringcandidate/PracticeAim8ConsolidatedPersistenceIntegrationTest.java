package com.ksh.features.practice.manage.authoringcandidate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.ai.controlplane.PracticeAiBindingResolver;
import com.ksh.features.practice.ai.controlplane.PracticeAiCapabilityTestRunRepository;
import com.ksh.features.practice.ai.controlplane.PracticeAiControlPlaneCodec;
import com.ksh.features.practice.ai.controlplane.PracticeAiControlPlaneException;
import com.ksh.features.practice.ai.controlplane.PracticeAiExecutionSnapshot;
import com.ksh.features.practice.ai.controlplane.PracticeAiProviderProfile;
import com.ksh.features.practice.ai.controlplane.PracticeAiProviderProfileRepository;
import com.ksh.features.practice.ai.controlplane.PracticeAiPurpose;
import com.ksh.features.practice.ai.controlplane.PracticeAiPurposeBinding;
import com.ksh.features.practice.ai.controlplane.PracticeAiPurposeBindingRepository;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ApplyCommand;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ApplyResultCode;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateView;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CreateCommand;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceKind;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceSnapshot;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.storage.profile.StorageBackend;
import com.ksh.features.storage.profile.StorageProfileCode;
import com.ksh.features.storage.profile.StorageProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.EnumMap;
import java.util.Map;
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
@EnabledIfEnvironmentVariable(named = "RUN_AIM8_DB_TESTS", matches = "true")
class PracticeAim8ConsolidatedPersistenceIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PracticeDraftRepository drafts;
    @Autowired PracticeAuthoringCandidateRepository candidates;
    @Autowired PracticeAuthoringCandidateApplyEventRepository applyEvents;
    @Autowired PracticeAuthoringCandidateService candidateService;
    @Autowired PracticeAuthoringCandidateApplyService applyService;
    @Autowired PracticeAuthoringCandidatePreviewService previewService;
    @Autowired PracticeAiProviderProfileRepository aiProfiles;
    @Autowired PracticeAiPurposeBindingRepository aiBindings;
    @Autowired PracticeAiCapabilityTestRunRepository capabilityRuns;
    @Autowired PracticeAiControlPlaneCodec aiCodec;
    @Autowired PracticeAiBindingResolver aiResolver;
    @Autowired StorageProfileRepository storageProfiles;

    @Test
    void currentSchemaConvergesExcelCandidateApplyAiAndStorageContracts()
            throws Exception {
        assertCurrentIntegratedSchema();
        Long ownerId = authorizedLecturer();
        Map<PracticeAiPurpose, PracticeAiExecutionSnapshot> snapshots =
                persistSixPurposeBindings(ownerId);

        PracticeDraft draft = drafts.saveAndFlush(new PracticeDraft(
                "AIM-8 cross-source candidate journey", "", "GLOBAL", null,
                "DRAFT", ownerId,
                PracticeAuthoringCandidateTestFixtures.targetDraft(0)
                        .getDraftJson()));
        String originalDraft = draft.getDraftJson();
        Map<SourceKind, CandidateView> created = new EnumMap<>(SourceKind.class);
        for (SourceKind kind : new SourceKind[] {
                SourceKind.QUICK_EXCEL,
                SourceKind.ADVANCED_EXCEL_V2,
                SourceKind.LEGACY_EXCEL_V1}) {
            CreateCommand command = excelCommand(
                    kind, ownerId, draft.getId(), approvedGroups(kind));
            CandidateView first = candidateService.createOrReuse(command);
            CandidateView replay = candidateService.createOrReuse(command);
            assertThat(replay.candidateId()).isEqualTo(first.candidateId());
            assertThat(first.state()).isEqualTo(
                    PracticeAuthoringCandidateModels.CandidateState.REVIEWING);
            created.put(kind, first);
        }
        CreateCommand pdfCreateCommand = pdfCommand(
                ownerId,
                draft.getId(),
                snapshots.get(PracticeAiPurpose.PRACTICE_PDF_AUTHORING));
        CandidateView pdfCandidate = candidateService.createOrReuse(pdfCreateCommand);
        CandidateView pdfReplay = candidateService.createOrReuse(pdfCreateCommand);
        assertThat(pdfReplay.candidateId()).isEqualTo(pdfCandidate.candidateId());
        assertThat(pdfCandidate.candidate().at(
                "/source/aiExecution/bindingRevision").asLong()).isZero();
        created.put(SourceKind.PDF_AI, pdfCandidate);

        PracticeDraft beforeApply = drafts.findById(draft.getId()).orElseThrow();
        assertThat(beforeApply.getVersion()).isZero();
        assertThat(beforeApply.getDraftJson()).isEqualTo(originalDraft);
        assertThat(applyEvents.count()).isZero();
        assertThat(candidates.count()).isEqualTo(4);

        CandidateView quickReady = ready(created.get(SourceKind.QUICK_EXCEL), ownerId);
        CandidateView advancedReady = ready(
                created.get(SourceKind.ADVANCED_EXCEL_V2), ownerId);
        UUID quickRequest = UUID.randomUUID();
        ApplyCommand quickApply = applyCommand(quickReady, quickRequest, ownerId);
        var applied = applyService.apply(quickApply);
        var replayed = applyService.apply(quickApply);
        var staleAdvanced = applyService.apply(applyCommand(
                advancedReady, UUID.randomUUID(), ownerId));

        assertThat(applied.result()).isEqualTo(ApplyResultCode.DRAFT_APPLIED);
        assertThat(replayed.result()).isEqualTo(ApplyResultCode.DRAFT_APPLIED);
        assertThat(replayed.replayed()).isTrue();
        assertThat(staleAdvanced.result()).isEqualTo(ApplyResultCode.CONFLICT);
        PracticeDraft afterApply = drafts.findById(draft.getId()).orElseThrow();
        assertThat(afterApply.getVersion()).isEqualTo(1);
        assertThat(objectMapper.readTree(afterApply.getDraftJson())
                .path("sections").get(0).path("groups")).hasSize(1);
        assertThat(applyEvents.count()).isEqualTo(2);

        CandidateView legacy = created.get(SourceKind.LEGACY_EXCEL_V1);
        String beforePreview = drafts.findById(draft.getId()).orElseThrow()
                .getDraftJson();
        assertThatThrownBy(() -> previewService.preview(
                legacy.candidateId(), ownerId,
                legacy.version(), legacy.contentDigest()))
                .isInstanceOf(PracticeAuthoringCandidateException.class)
                .extracting(error -> ((PracticeAuthoringCandidateException) error).code())
                .isEqualTo("TARGET_DRAFT_VERSION_CONFLICT");
        assertThat(drafts.findById(draft.getId()).orElseThrow().getDraftJson())
                .isEqualTo(beforePreview);

        assertThat(snapshots).hasSize(6);
        assertThat(snapshots.values()).allSatisfy(snapshot -> {
            assertThat(snapshot.providerProfileCode())
                    .startsWith("AIM8_DISPOSABLE_");
            assertThat(snapshot.model()).startsWith("aim8-");
            assertThat(snapshot.bindingRevision()).isZero();
            assertThat(snapshot.providerProfileRevision()).isZero();
        });
        assertThat(capabilityRuns.count()).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM practice_ai_execution_audits",
                Long.class)).isZero();

        PracticeAiPurposeBinding pdf = aiBindings.findDetailed(
                PracticeAiPurpose.PRACTICE_PDF_AUTHORING.name()).orElseThrow();
        PracticeAiExecutionSnapshot stalePdf = snapshots.get(
                PracticeAiPurpose.PRACTICE_PDF_AUTHORING);
        pdf.update(pdf.getProviderProfile(), "aim8-pdf-revision-2",
                PracticeAiBindingResolver.TRANSPORT_DIALECT,
                pdf.getCapabilityJson(), pdf.getLimitsJson(),
                pdf.getRetentionCode(), true, ownerId);
        aiBindings.saveAndFlush(pdf);
        assertThatThrownBy(() -> aiResolver.assertCurrent(stalePdf))
                .isInstanceOf(PracticeAiControlPlaneException.class)
                .extracting(error -> ((PracticeAiControlPlaneException) error)
                        .errorCode())
                .isEqualTo("PROVIDER_BINDING_CHANGED");
    }

    private void assertCurrentIntegratedSchema() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                Integer.class)).isEqualTo(86);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) "
                        + "FROM flyway_schema_history",
                Integer.class)).isEqualTo(86);
        assertThat(storageProfiles.findAll()).hasSize(3)
                .extracting(profile -> profile.getProfileCode())
                .containsExactlyInAnyOrder(StorageProfileCode.values());
        assertThat(storageProfiles.findAll()).allSatisfy(profile -> {
            assertThat(profile.getBackend()).isEqualTo(StorageBackend.LOCAL);
            assertThat(profile.getKeyPrefix())
                    .isEqualTo(profile.getProfileCode().fixedKeyPrefix());
        });
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM practice_storage_migration_jobs",
                Long.class)).isZero();
    }

    private Map<PracticeAiPurpose, PracticeAiExecutionSnapshot>
            persistSixPurposeBindings(Long actorId) {
        assertThat(aiBindings.count()).isZero();
        PracticeAiProviderProfile profile = aiProfiles.saveAndFlush(
                new PracticeAiProviderProfile(
                        "AIM8_DISPOSABLE_" + UUID.randomUUID()
                                .toString().replace("-", "").substring(0, 12),
                        "AIM-8 fake-only profile",
                        PracticeAiBindingResolver.PROVIDER_FAMILY,
                        "https://provider.invalid/v1",
                        "AIM8_FAKE_SECRET_NEVER_SENT",
                        true,
                        actorId));
        Map<PracticeAiPurpose, PracticeAiExecutionSnapshot> snapshots =
                new EnumMap<>(PracticeAiPurpose.class);
        for (PracticeAiPurpose purpose : PracticeAiPurpose.values()) {
            aiBindings.saveAndFlush(new PracticeAiPurposeBinding(
                    purpose,
                    profile,
                    "aim8-" + purpose.name().toLowerCase(),
                    PracticeAiBindingResolver.TRANSPORT_DIALECT,
                    aiCodec.capabilityJson(purpose, false),
                    aiCodec.limitsJson(
                            1_000, 5_000, 1, 1_048_576, 1_048_576),
                    retention(purpose),
                    true,
                    actorId));
            snapshots.put(purpose, aiResolver.resolve(purpose).snapshot());
        }
        assertThat(aiBindings.count()).isEqualTo(6);
        return snapshots;
    }

    private CreateCommand excelCommand(
            SourceKind kind,
            Long ownerId,
            Long draftId,
            ArrayNode groups) {
        String discriminator = Integer.toString(kind.ordinal() + 1);
        return new CreateCommand(
                ownerId,
                new SourceSnapshot(
                        kind,
                        kind.contractVersion(),
                        "sha256:" + discriminator.repeat(64),
                        "aim8-" + kind.name().toLowerCase(),
                        kind.name().toLowerCase() + ".xlsx",
                        SourceOperation.NONE,
                        null),
                new TargetRoute(draftId, 1, "READING", "R1"),
                groups);
    }

    private CreateCommand pdfCommand(
            Long ownerId,
            Long draftId,
            PracticeAiExecutionSnapshot snapshot) {
        ObjectNode execution = objectMapper.createObjectNode();
        execution.put("purpose", PracticeAiPurpose.PRACTICE_PDF_AUTHORING.name());
        execution.put("bindingRevision", snapshot.bindingRevision());
        execution.put("providerProfileCode", snapshot.providerProfileCode());
        execution.put("providerFamily", snapshot.providerFamily());
        execution.put("model", snapshot.model());
        execution.put("transportDialect", snapshot.transportDialect());
        execution.put("requestId", "88888888-8888-4888-8888-888888888888");
        return new CreateCommand(
                ownerId,
                new SourceSnapshot(
                        SourceKind.PDF_AI,
                        SourceKind.PDF_AI.contractVersion(),
                        "sha256:" + "8".repeat(64),
                        "authoring-v1-b0-f0-aim8",
                        "aim8-source.pdf",
                        SourceOperation.EXTRACT,
                        execution),
                new TargetRoute(draftId, 1, "READING", "R1"),
                pdfGroups());
    }

    private ArrayNode approvedGroups(SourceKind sourceKind) {
        ArrayNode groups = PracticeAuthoringCandidateTestFixtures
                .readingGroups(objectMapper, true);
        ObjectNode provenance = (ObjectNode) groups.get(0)
                .path("stimulus").path("provenance");
        provenance.put("source", sourceKind.name());
        return groups;
    }

    private ArrayNode pdfGroups() {
        ArrayNode groups = PracticeAuthoringCandidateTestFixtures
                .readingGroups(objectMapper, false);
        ObjectNode group = (ObjectNode) groups.get(0);
        ObjectNode sourceRef = objectMapper.createObjectNode();
        sourceRef.put("kind", "TEXT_SPAN");
        sourceRef.put("sourceId", "aim8-text-1");
        sourceRef.put("start", 0);
        sourceRef.put("end", 24);
        group.set("sourceRefs", objectMapper.createArrayNode()
                .add(sourceRef.deepCopy()));
        group.withObject("/stimulus/provenance")
                .put("source", "PDF_AI")
                .set("sourceRefs", objectMapper.createArrayNode()
                        .add(sourceRef.deepCopy()));
        group.withObject("/questions/0")
                .put("reviewState", "REVIEW_REQUIRED")
                .set("sourceRefs", objectMapper.createArrayNode()
                        .add(sourceRef.deepCopy()));
        return groups;
    }

    private CandidateView ready(CandidateView candidate, Long ownerId) {
        return candidateService.markReady(
                candidate.candidateId(), ownerId,
                candidate.version(), candidate.contentDigest());
    }

    private static ApplyCommand applyCommand(
            CandidateView candidate,
            UUID requestId,
            Long ownerId) {
        return new ApplyCommand(
                candidate.candidateId(), requestId, ownerId,
                candidate.version(), candidate.contentDigest());
    }

    private Long authorizedLecturer() {
        return jdbc.queryForObject("""
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

    private static String retention(PracticeAiPurpose purpose) {
        return switch (purpose) {
            case PRACTICE_PDF_AUTHORING -> "PRACTICE_AUTHORING_V1";
            case PRACTICE_RL_EXPLANATION -> "PUBLISHED_EXPLANATION_V1";
            case PRACTICE_WRITING_EVALUATION -> "WRITING_EVALUATION_V1";
            case PRACTICE_SPEAKING_EVALUATION -> "SPEAKING_TRANSCRIPT_EVAL_V1";
            case PRACTICE_SPEAKING_STT -> "SPEAKING_AUDIO_STT_V1";
            case PRACTICE_SPEAKING_TTS -> "LECTURER_PROMPT_TTS_V1";
        };
    }
}
