package com.ksh.features.practice.ai.readiness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderOperationalReadinessPolicyTest {

    private final ProviderOperationalReadinessPolicy policy = new ProviderOperationalReadinessPolicy();

    @Test
    void completeRunbookHasNoOperationalBlockers() {
        AiReadinessReport report = policy.assessRunbook(ProviderOperationalRunbook.complete());

        assertThat(report.blockers()).isEmpty();
        assertThat(report.rolloutAllowed()).isTrue();
        assertThat(report.issues())
                .extracting(AiReadinessIssue::code)
                .contains("BACKEND_OPERATOR_READINESS_ONLY");
    }

    @Test
    void missingRunbookItemsBlock8FC() {
        AiReadinessReport report = policy.assessRunbook(new ProviderOperationalRunbook(
                false, true, false, true, false, false));

        assertThat(report.blockers())
                .extracting(AiReadinessIssue::code)
                .contains("RUNBOOK_PROVIDER_OUTAGE_MISSING",
                        "RUNBOOK_COST_SPIKE_MISSING",
                        "RUNBOOK_MEDIA_STORAGE_MISSING",
                        "RUNBOOK_PRIVACY_INCIDENT_MISSING");
    }

    @Test
    void safeMetricDimensionsDoNotAllowHighCardinalitySecrets() {
        assertThat(policy.safeMetricDimensions()).containsExactly("feature", "outcome");
        assertThat(policy.forbiddenOperationalData())
                .contains("provider raw request body",
                        "provider raw response body",
                        "API key",
                        "learner transcript",
                        "storage key",
                        "user identity");
    }

    @Test
    void runbookToStringDoesNotExposeProviderOrLearnerPayloads() {
        String rendered = ProviderOperationalRunbook.complete().toString();

        assertThat(rendered)
                .doesNotContain("SECRET", "Bearer", "transcript text", "storage/path", "provider body");
    }
}
