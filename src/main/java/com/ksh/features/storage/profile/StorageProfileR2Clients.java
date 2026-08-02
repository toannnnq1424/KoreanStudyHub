package com.ksh.features.storage.profile;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StorageProfileR2Clients {
    private final ConcurrentHashMap<ClientIdentity, S3Client> clients = new ConcurrentHashMap<>();

    public S3Client client(ResolvedStorageProfile profile) {
        if (profile == null || profile.backend() != StorageBackend.R2) {
            throw new StorageProfileException("STORAGE_PROFILE_UNAVAILABLE");
        }
        ClientIdentity identity = new ClientIdentity(
                profile.profileCode(), profile.revision(), profile.accessKeyId(),
                profile.secretAccessKey(), profile.endpoint(), profile.region());
        return clients.computeIfAbsent(identity, StorageProfileR2Clients::build);
    }

    public void invalidate(StorageProfileCode code) {
        clients.entrySet().removeIf(entry -> {
            if (entry.getKey().profileCode() != code) return false;
            try {
                entry.getValue().close();
            } catch (RuntimeException ignored) {
                // The saved revision is already authoritative; next use rebuilds.
            }
            return true;
        });
    }

    private static S3Client build(ClientIdentity identity) {
        return S3Client.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(identity.accessKeyId(), identity.secretAccessKey())))
                .endpointOverride(URI.create(identity.endpoint()))
                .region(Region.of(identity.region()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    private record ClientIdentity(
            StorageProfileCode profileCode,
            long revision,
            String accessKeyId,
            String secretAccessKey,
            String endpoint,
            String region) {
    }
}
