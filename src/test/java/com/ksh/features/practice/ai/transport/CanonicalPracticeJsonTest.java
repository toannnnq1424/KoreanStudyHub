package com.ksh.features.practice.ai.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalPracticeJsonTest {

    private final CanonicalPracticeJson canonical =
            new CanonicalPracticeJson(new ObjectMapper());

    @Test
    void fieldOrderAndKoreanNormalizationProduceOneDeterministicIdentity() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("z", "가");
        first.put("a", Map.of("beta", 2, "alpha", 1));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", Map.of("alpha", 1, "beta", 2));
        second.put("z", "가");

        CanonicalPracticeJson.CanonicalPayload firstPayload =
                canonical.serialize(first);
        CanonicalPracticeJson.CanonicalPayload secondPayload =
                canonical.serialize(second);

        assertThat(firstPayload.json())
                .isEqualTo("{\"a\":{\"alpha\":1,\"beta\":2},\"z\":\"가\"}");
        assertThat(firstPayload.json()).isEqualTo(secondPayload.json());
        assertThat(firstPayload.sha256()).isEqualTo(secondPayload.sha256());
        assertThat(firstPayload.utf8()).isEqualTo(secondPayload.utf8());
    }
}
