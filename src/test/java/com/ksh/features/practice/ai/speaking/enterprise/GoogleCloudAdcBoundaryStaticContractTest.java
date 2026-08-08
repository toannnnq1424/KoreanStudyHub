package com.ksh.features.practice.ai.speaking.enterprise;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleCloudAdcBoundaryStaticContractTest {

    @Test
    void productionDefaultIsFailClosedAndNoCredentialOrNetworkAdapterIsPresent()
            throws Exception {
        String disabled = source("DisabledGoogleCloudShortLivedAccessTokenSource.java");
        String adapter = source("GeminiEnterpriseDirectAudioEvaluationAdapter.java");
        String tokenSource = source("GoogleCloudShortLivedAccessTokenSource.java");
        String pom = Files.readString(Path.of("pom.xml"));
        String properties = Files.readString(Path.of(
                "src/main/resources/application.properties"));

        assertThat(disabled)
                .contains("@Component", "GOOGLE_CLOUD_ADC_WORKLOAD_IDENTITY_UNAVAILABLE")
                .doesNotContain("@Value", "System.getenv", "GOOGLE_APPLICATION_CREDENTIALS",
                        "service_account", "private_key", "RestClient", "WebClient");
        assertThat(adapter)
                .contains("deliberately not a Spring bean")
                .contains("accessToken=<redacted>", "audioBytes=<redacted>")
                .doesNotContain("@Component", "@Service", "RestClient", "WebClient",
                        "HttpClient", "LoggerFactory", "Authorization");
        assertThat(tokenSource)
                .contains("value=<redacted>")
                .doesNotContain("@Value", "System.getenv", "private_key");
        assertThat(pom).doesNotContain("google-auth-library", "google-cloud-aiplatform");
        assertThat(properties).doesNotContain(
                "GOOGLE_APPLICATION_CREDENTIALS", "service-account-json",
                "practice.ai.google-cloud.access-token");
    }

    private static String source(String name) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/ai/speaking/enterprise/" + name));
    }
}
