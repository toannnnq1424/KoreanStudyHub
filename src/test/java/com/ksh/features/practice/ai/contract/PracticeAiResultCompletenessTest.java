package com.ksh.features.practice.ai.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeAiResultCompletenessTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exactVersionedStatesRoundTrip() {
        var complete = PracticeAiResultCompleteness.complete();
        var partial = PracticeAiResultCompleteness.partial(
                "DIAGNOSTIC_ITEMS_REJECTED", 2);
        var unavailable = PracticeAiResultCompleteness.unavailable(
                "PROVIDER_REFUSAL", 0);

        assertThat(PracticeAiResultCompleteness.require(
                mapper.valueToTree(java.util.Map.of(
                        PracticeAiResultCompleteness.FIELD,
                        complete.toMap())))).isEqualTo(complete);
        assertThat(partial.status()).isEqualTo(
                PracticeAiResultCompleteness.Status.PARTIAL_NON_SCORE);
        assertThat(partial.scoreBearingComplete()).isFalse();
        assertThat(unavailable.scoreBearingComplete()).isFalse();
    }

    @Test
    void unknownMissingAndInconsistentStatesFailClosed() throws Exception {
        assertThatThrownBy(() -> PracticeAiResultCompleteness.require(
                mapper.readTree("{}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PracticeAiResultCompleteness.require(
                mapper.readTree("""
                        {"result_completeness":{
                          "version":"practice-ai-result-completeness-v1",
                          "status":"COMPLETE","reason_code":"NONE",
                          "rejected_item_count":1}}
                        """)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PracticeAiResultCompleteness.require(
                mapper.readTree("""
                        {"result_completeness":{
                          "version":"future-v2","status":"UNAVAILABLE",
                          "reason_code":"PROVIDER_REFUSAL",
                          "rejected_item_count":0}}
                        """)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
