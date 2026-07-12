package com.ksh.features.practice.manage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PracticeStorageReadinessServiceTest {

    private final AssetStorageService storage = mock(AssetStorageService.class);

    @BeforeEach
    void setUp() {
        when(storage.providerCode()).thenReturn("LOCAL");
    }

    @Test
    void reportsMissingR2ConfigurationWithoutProviderCall() {
        PracticeStorageReadinessService.Readiness result = service(
                "R2", "", "", "", "", "").readiness();

        assertEquals("LOCAL_ACTIVE_R2_NOT_CONFIGURED", result.state());
        assertFalse(result.r2ConfigurationPresent());
        assertFalse(result.r2AdapterInstalled());
        assertEquals(5, result.missingConfiguration().size());
    }

    @Test
    void reportsConfiguredButAdapterNotInstalledWithoutExposingSecrets() {
        PracticeStorageReadinessService.Readiness result = service(
                "R2", "account", "bucket", "endpoint", "access", "secret").readiness();

        assertEquals("LOCAL_ACTIVE_R2_ADAPTER_NOT_INSTALLED", result.state());
        assertTrue(result.r2ConfigurationPresent());
        assertTrue(result.missingConfiguration().isEmpty());
        assertFalse(result.toString().contains("secret"));
        assertFalse(result.toString().contains("access"));
    }

    @Test
    void nonR2PlanKeepsLocalProviderActive() {
        PracticeStorageReadinessService.Readiness result = service(
                "LOCAL", "", "", "", "", "").readiness();

        assertEquals("LOCAL_ACTIVE", result.state());
        assertEquals("LOCAL", result.activeProvider());
    }

    private PracticeStorageReadinessService service(
            String planned, String account, String bucket, String endpoint,
            String accessKey, String secretKey) {
        return new PracticeStorageReadinessService(
                storage, planned, account, bucket, endpoint, accessKey, secretKey);
    }
}
