package com.ksh.features.practice.ai.speaking.enterprise;

import com.ksh.features.practice.ai.controlplane.PracticeAiControlPlaneException;
import org.springframework.stereotype.Component;

/** Production default until an approved workload-identity adapter exists. */
@Component
public final class DisabledGoogleCloudShortLivedAccessTokenSource
        implements GoogleCloudShortLivedAccessTokenSource {

    @Override
    public AccessToken issue(TokenRequest request) {
        throw new PracticeAiControlPlaneException(
                "GOOGLE_CLOUD_ADC_WORKLOAD_IDENTITY_UNAVAILABLE", false);
    }
}
