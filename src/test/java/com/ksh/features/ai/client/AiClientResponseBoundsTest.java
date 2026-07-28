package com.ksh.features.ai.client;

import com.ksh.entities.AiProvider;
import com.ksh.features.admin.settings.repository.AiProviderRepository;
import com.ksh.features.ai.log.AiRequestLogger;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiClientResponseBoundsTest {

    @Test
    void oversized_success_body_is_rejected_and_the_next_provider_is_tried() {
        AiProviderRepository repository = mock(AiProviderRepository.class);
        AiRequestLogger requestLogger = mock(AiRequestLogger.class);
        AiProvider oversized = new AiProvider(
                "Oversized", "https://oversized.example.test/v1", "model-a", "key-a");
        AiProvider healthy = new AiProvider(
                "Healthy", "https://healthy.example.test/v1", "model-b", "key-b");
        when(repository.findEnabledOrdered()).thenReturn(List.of(oversized, healthy));

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiClient client =
                AiClient.withPreconfiguredTransport(repository, builder, requestLogger);

        String oversizedJson =
                "{\"choices\":[{\"message\":{\"content\":\""
                        + "x".repeat(1_100_000)
                        + "\"}}]}";
        server.expect(ExpectedCount.once(),
                        requestTo("https://oversized.example.test/v1/chat/completions"))
                .andRespond(withSuccess(oversizedJson, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(),
                        requestTo("https://healthy.example.test/v1/chat/completions"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"recovered\"}}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.chat("hello", 10)).isEqualTo("recovered");
        server.verify();
    }
}
