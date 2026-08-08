package com.ksh.features.practice.ai.speaking.enterprise;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Issues request-local Google Cloud access tokens without persistence. */
public interface GoogleCloudShortLivedAccessTokenSource {

    AccessToken issue(TokenRequest request);

    record TokenRequest(
            String audience,
            String scope,
            String project,
            String location,
            URI endpoint,
            long credentialModeRevision) {
        public TokenRequest {
            audience = required(audience, "audience");
            scope = required(scope, "scope");
            project = required(project, "project");
            location = required(location, "location");
            endpoint = Objects.requireNonNull(endpoint, "endpoint");
            if (credentialModeRevision < 0) {
                throw new IllegalArgumentException("credentialModeRevision");
            }
        }
    }

    record AccessToken(
            String value,
            Instant expiresAt,
            String audience,
            Set<String> scopes,
            String project,
            String location,
            URI endpoint,
            long credentialModeRevision) {
        public AccessToken {
            value = value == null ? "" : value.trim();
            expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
            audience = required(audience, "audience");
            scopes = Set.copyOf(Objects.requireNonNull(scopes, "scopes"));
            project = required(project, "project");
            location = required(location, "location");
            endpoint = Objects.requireNonNull(endpoint, "endpoint");
            if (credentialModeRevision < 0) {
                throw new IllegalArgumentException("credentialModeRevision");
            }
        }

        @Override
        public String toString() {
            return "AccessToken[value=<redacted>,expiresAt=" + expiresAt
                    + ",audience=" + audience + ",scopes=" + scopes
                    + ",project=" + project + ",location=" + location
                    + ",endpoint=" + endpoint
                    + ",credentialModeRevision=" + credentialModeRevision + "]";
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field);
        }
        return normalized;
    }
}
