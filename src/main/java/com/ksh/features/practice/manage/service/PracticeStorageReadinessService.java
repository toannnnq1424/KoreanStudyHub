package com.ksh.features.practice.manage.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PracticeStorageReadinessService {

    private final AssetStorageService activeStorage;
    private final String plannedProvider;
    private final String r2AccountId;
    private final String r2Bucket;
    private final String r2Endpoint;
    private final String r2AccessKeyId;
    private final String r2SecretAccessKey;

    public PracticeStorageReadinessService(
            AssetStorageService activeStorage,
            @Value("${app.practice.storage.planned-provider:R2}") String plannedProvider,
            @Value("${app.practice.storage.r2.account-id:}") String r2AccountId,
            @Value("${app.practice.storage.r2.bucket:}") String r2Bucket,
            @Value("${app.practice.storage.r2.endpoint:}") String r2Endpoint,
            @Value("${app.practice.storage.r2.access-key-id:}") String r2AccessKeyId,
            @Value("${app.practice.storage.r2.secret-access-key:}") String r2SecretAccessKey) {
        this.activeStorage = activeStorage;
        this.plannedProvider = normalize(plannedProvider);
        this.r2AccountId = r2AccountId;
        this.r2Bucket = r2Bucket;
        this.r2Endpoint = r2Endpoint;
        this.r2AccessKeyId = r2AccessKeyId;
        this.r2SecretAccessKey = r2SecretAccessKey;
    }

    public Readiness readiness() {
        List<String> missing = new ArrayList<>();
        if (blank(r2AccountId)) missing.add("accountId");
        if (blank(r2Bucket)) missing.add("bucket");
        if (blank(r2Endpoint)) missing.add("endpoint");
        if (blank(r2AccessKeyId)) missing.add("accessKeyId");
        if (blank(r2SecretAccessKey)) missing.add("secretAccessKey");
        boolean r2ConfigPresent = missing.isEmpty();
        String state;
        if (!"R2".equals(plannedProvider)) {
            state = "LOCAL_ACTIVE";
        } else if (!r2ConfigPresent) {
            state = "LOCAL_ACTIVE_R2_NOT_CONFIGURED";
        } else {
            // Credentials are intentionally not exercised until the R2 adapter is installed.
            state = "LOCAL_ACTIVE_R2_ADAPTER_NOT_INSTALLED";
        }
        return new Readiness(
                activeStorage.providerCode(), plannedProvider, state,
                r2ConfigPresent, false, List.copyOf(missing),
                "Không thực hiện network probe hoặc provider call trong Phase 12.");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank()
                ? "R2" : value.trim().toUpperCase(Locale.ROOT);
    }

    public record Readiness(String activeProvider, String plannedProvider,
                            String state, boolean r2ConfigurationPresent,
                            boolean r2AdapterInstalled, List<String> missingConfiguration,
                            String note) {
        public boolean productionReady() {
            return !"R2".equals(plannedProvider)
                    || (r2ConfigurationPresent && r2AdapterInstalled);
        }
    }
}
