package com.ksh.features.practice.ai.controlplane;

import com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.CapabilityTestResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.EnumMap;
import java.util.Map;

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
@EnabledIfEnvironmentVariable(named = "RUN_AIM5_DB_TESTS", matches = "true")
class PracticeAiControlPlanePersistenceIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PracticeAiProviderProfileRepository profileRepository;
    @Autowired
    private PracticeAiPurposeBindingRepository bindingRepository;
    @Autowired
    private PracticeAiCapabilityTestRunRepository capabilityTestRunRepository;
    @Autowired
    private PracticeAiExecutionAuditService auditService;
    @Autowired
    private PracticeAiBindingResolver resolver;
    @Autowired
    private PracticeAiControlPlaneCodec codec;

    @Test
    void freshV91SchemaPersistsPurposeBindingsSnapshotsAndRedactedAudits() {
        assertFreshMigrationAndSchema();

        Long actorId = jdbcTemplate.queryForObject(
                "SELECT id FROM users ORDER BY id LIMIT 1", Long.class);
        PracticeAiProviderProfile profile = profileRepository.saveAndFlush(
                new PracticeAiProviderProfile(
                        "AIM5_DISPOSABLE_PRIMARY",
                        "AIM-5 disposable profile",
                        PracticeAiBindingResolver.PROVIDER_FAMILY,
                        PracticeDirectAudioCapabilityRegistry.GEMINI_DEVELOPER_BASE_URL,
                        "AIM5_TEST_SECRET_NEVER_SENT",
                        true,
                        actorId));
        PracticeAiProviderProfile enterprise = profileRepository.saveAndFlush(
                new PracticeAiProviderProfile(
                        "AIM5_DISPOSABLE_ENTERPRISE",
                        "AIM-5 disposable Enterprise profile",
                        PracticeAiBindingResolver.PROVIDER_FAMILY,
                        "https://aiplatform.googleapis.com/v1/projects/ksh-test/"
                                + "locations/asia-southeast1/endpoints/openapi",
                        PracticeAiCredentialMode.GOOGLE_CLOUD_ADC.name(),
                        null,
                        false,
                        actorId));
        assertThat(enterprise.getCredentialSecret()).isNull();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT credential_secret IS NULL
                FROM practice_ai_provider_profiles
                WHERE profile_code = 'AIM5_DISPOSABLE_ENTERPRISE'
                """, Boolean.class)).isTrue();

        Map<PracticeAiPurpose, PracticeAiExecutionSnapshot> snapshots =
                new EnumMap<>(PracticeAiPurpose.class);
        for (PracticeAiPurpose purpose : PracticeAiPurpose.values()) {
            PracticeAiPurposeBinding binding = new PracticeAiPurposeBinding(
                    purpose,
                    profile,
                    purpose == PracticeAiPurpose.PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION
                            ? PracticeDirectAudioCapabilityRegistry.GEMINI_DEVELOPER_MODEL
                            : "aim5-" + purpose.name().toLowerCase(),
                    PracticeAiBindingResolver.TRANSPORT_DIALECT,
                    codec.capabilityJson(purpose, false,
                            purpose == PracticeAiPurpose
                                    .PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION),
                    codec.limitsJson(1_000, 5_000, 1, 1_048_576, 1_048_576),
                    retention(purpose),
                    true,
                    actorId);
            if (purpose == PracticeAiPurpose.PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION) {
                binding.updatePolicyEvidence("region/test", "non-training/test",
                        "retention/test", "deletion-sla/test");
            }
            bindingRepository.saveAndFlush(binding);
            snapshots.put(purpose, resolver.resolve(purpose).snapshot());
        }

        assertThat(bindingRepository.count()).isEqualTo(7);
        assertThat(snapshots).hasSize(7);
        assertThat(snapshots.values())
                .allSatisfy(snapshot -> {
                    assertThat(snapshot.bindingRevision()).isZero();
                    assertThat(snapshot.providerProfileRevision()).isZero();
                    assertThat(snapshot.providerProfileCode())
                            .isEqualTo("AIM5_DISPOSABLE_PRIMARY");
                });

        CapabilityTestResult fakeProbeResult = new PracticeAiCapabilityTestService(
                resolver,
                ignored -> { },
                capabilityTestRunRepository)
                .test(PracticeAiPurpose.PRACTICE_SPEAKING_TTS, actorId);
        assertThat(fakeProbeResult.ok()).isTrue();
        assertThat(capabilityTestRunRepository.count()).isEqualTo(1);

        PracticeAiExecutionSnapshot writing = snapshots.get(
                PracticeAiPurpose.PRACTICE_WRITING_EVALUATION);
        Long auditId = auditService.start(
                writing,
                "WRITING_EVALUATION",
                "schema-v1|prompt-v1|rubric-v1",
                writing.purpose().dataClass());
        auditService.success(auditId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM practice_ai_execution_audits WHERE id = ?",
                String.class,
                auditId)).isEqualTo("SUCCESS");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'practice_ai_execution_audits'
                  AND column_name LIKE '%secret%'
                """, Integer.class)).isZero();
        String persistedAudit = jdbcTemplate.queryForObject("""
                SELECT CONCAT_WS('|', purpose_code, provider_profile_code, model,
                        transport_dialect, operation_code, data_class,
                        COALESCE(bounded_error_code, ''))
                FROM practice_ai_execution_audits
                WHERE id = ?
                """, String.class, auditId);
        assertThat(persistedAudit).doesNotContain("AIM5_TEST_SECRET_NEVER_SENT");

        PracticeAiPurposeBinding pdf = bindingRepository.findDetailed(
                PracticeAiPurpose.PRACTICE_PDF_AUTHORING.name()).orElseThrow();
        PracticeAiExecutionSnapshot stalePdf = snapshots.get(
                PracticeAiPurpose.PRACTICE_PDF_AUTHORING);
        pdf.update(
                profile,
                "aim5-pdf-model-revision-2",
                PracticeAiBindingResolver.TRANSPORT_DIALECT,
                pdf.getCapabilityJson(),
                pdf.getLimitsJson(),
                pdf.getRetentionCode(),
                true,
                actorId);
        PracticeAiPurposeBinding revised = bindingRepository.saveAndFlush(pdf);
        assertThat(revised.getRevision()).isEqualTo(1L);
        assertThatThrownBy(() -> resolver.assertCurrent(stalePdf))
                .isInstanceOf(PracticeAiControlPlaneException.class)
                .extracting(error -> ((PracticeAiControlPlaneException) error).errorCode())
                .isEqualTo("PROVIDER_BINDING_CHANGED");

        PracticeAiPurposeBinding reading = bindingRepository.findDetailed(
                PracticeAiPurpose.PRACTICE_RL_EXPLANATION.name()).orElseThrow();
        reading.toggle(actorId);
        bindingRepository.saveAndFlush(reading);
        assertThatThrownBy(() -> resolver.resolve(
                PracticeAiPurpose.PRACTICE_RL_EXPLANATION))
                .isInstanceOf(PracticeAiControlPlaneException.class)
                .extracting(error -> ((PracticeAiControlPlaneException) error).errorCode())
                .isEqualTo("PROVIDER_PURPOSE_UNAVAILABLE");
    }

    private void assertFreshMigrationAndSchema() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                Integer.class)).isEqualTo(91);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history",
                Integer.class)).isEqualTo(91);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'practice_ai_provider_profiles'
                  AND column_name = 'credential_mode'
                  AND is_nullable = 'NO'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                    'practice_ai_provider_profiles',
                    'practice_ai_purpose_bindings',
                    'practice_ai_capability_test_runs',
                    'practice_ai_execution_audits')
                """, Integer.class)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.key_column_usage
                WHERE table_schema = DATABASE()
                  AND table_name = 'practice_ai_purpose_bindings'
                  AND constraint_name = 'PRIMARY'
                  AND column_name = 'purpose_code'
                """, Integer.class)).isEqualTo(1);
    }

    private static String retention(PracticeAiPurpose purpose) {
        return switch (purpose) {
            case PRACTICE_PDF_AUTHORING -> "PRACTICE_AUTHORING_V1";
            case PRACTICE_RL_EXPLANATION -> "PUBLISHED_EXPLANATION_V1";
            case PRACTICE_WRITING_EVALUATION -> "WRITING_EVALUATION_V1";
            case PRACTICE_SPEAKING_EVALUATION -> "SPEAKING_TRANSCRIPT_EVAL_V1";
            case PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION ->
                    "SPEAKING_DIRECT_AUDIO_EVAL_V1";
            case PRACTICE_SPEAKING_STT -> "SPEAKING_AUDIO_STT_V1";
            case PRACTICE_SPEAKING_TTS -> "LECTURER_PROMPT_TTS_V1";
        };
    }
}
