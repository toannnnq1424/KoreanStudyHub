package com.ksh.features.admin.settings.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ksh.features.storage.profile.StorageBackend;
import com.ksh.features.storage.profile.StorageProfileCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class StorageProfileDtos {
    public static final String MASKED = "********";

    private StorageProfileDtos() {
    }

    public record ProfileRow(
            StorageProfileCode profileCode,
            StorageBackend backend,
            String accountId,
            String accessKeyId,
            String bucket,
            String endpoint,
            String region,
            String keyPrefix,
            boolean enabled,
            long revision,
            LocalDateTime updatedAt) {
    }

    public record ProfileForm(
            @NotNull StorageProfileCode profileCode,
            @NotNull StorageBackend backend,
            @Size(max = 128) String accountId,
            @Size(max = 255) String accessKeyId,
            @Size(max = 4096) String secretAccessKey,
            @Size(max = 255) String bucket,
            @Size(max = 512) String endpoint,
            @Size(max = 64) String region,
            @Size(max = 255) String keyPrefix,
            boolean enabled,
            Long revision) {

        public static ProfileForm create(StorageProfileCode code) {
            return new ProfileForm(code, StorageBackend.LOCAL, "", "", "",
                    "", "", "auto", code.fixedKeyPrefix(), false, null);
        }
    }

    public enum ConnectionTestStatus {
        SUCCESS,
        NOT_APPLICABLE,
        FAILED
    }

    /** JSON result of testing one saved storage profile. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConnectionTestResult(
            ConnectionTestStatus status,
            String message) {
    }
}
