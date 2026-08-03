package com.ksh.features.practice.ai.controlplane;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repository-controlled direct-audio provider candidates.
 *
 * <p>This catalog is a compatibility/readiness boundary, not legal evidence.
 * A matching endpoint and model never replace the binding's immutable region,
 * non-training, retention and deletion-SLA evidence.</p>
 */
public final class PracticeDirectAudioProviderCatalog {

    public static final String GEMINI_DEVELOPER_CODE =
            "GEMINI_DEVELOPER_DIRECT_AUDIO";
    public static final String GEMINI_DEVELOPER_MODEL = "gemini-3.6-flash";
    public static final String GEMINI_DEVELOPER_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/openai";

    public static final String GEMINI_ENTERPRISE_CODE =
            "GEMINI_ENTERPRISE_DIRECT_AUDIO";
    public static final String GEMINI_ENTERPRISE_MODEL = "gemini-3.5-flash";

    private static final Pattern VERTEX_PATH = Pattern.compile(
            "^/v1(?:beta1)?/projects/([^/]+)/locations/([^/]+)/endpoints/openapi$");

    private PracticeDirectAudioProviderCatalog() {
    }

    public static Optional<Candidate> match(String rawBaseUrl, String rawModel) {
        URI uri;
        try {
            uri = URI.create(required(rawBaseUrl));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        String model;
        try {
            model = required(rawModel);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        if (developerEndpoint(uri) && GEMINI_DEVELOPER_MODEL.equals(model)) {
            return Optional.of(new Candidate(
                    GEMINI_DEVELOPER_CODE,
                    "Gemini Developer API",
                    GEMINI_DEVELOPER_MODEL,
                    CredentialMode.STATIC_BEARER,
                    true));
        }
        Matcher vertex = VERTEX_PATH.matcher(normalizedPath(uri));
        if (vertex.matches()
                && vertexHost(uri)
                && concrete(vertex.group(1))
                && concrete(vertex.group(2))
                && GEMINI_ENTERPRISE_MODEL.equals(model)) {
            return Optional.of(new Candidate(
                    GEMINI_ENTERPRISE_CODE,
                    "Gemini Enterprise / Vertex AI",
                    GEMINI_ENTERPRISE_MODEL,
                    CredentialMode.GOOGLE_CLOUD_ADC,
                    false));
        }
        return Optional.empty();
    }

    private static boolean developerEndpoint(URI uri) {
        return secure(uri)
                && "generativelanguage.googleapis.com".equalsIgnoreCase(uri.getHost())
                && "/v1beta/openai".equals(normalizedPath(uri));
    }

    private static boolean vertexHost(URI uri) {
        if (!secure(uri)) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return "aiplatform.googleapis.com".equals(host)
                || host.matches("[a-z0-9-]+-aiplatform\\.googleapis\\.com");
    }

    private static boolean secure(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null
                && uri.getUserInfo() == null
                && uri.getQuery() == null
                && uri.getFragment() == null;
    }

    private static String normalizedPath(URI uri) {
        String path = uri.getPath();
        while (path != null && path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        return path == null ? "" : path;
    }

    private static boolean concrete(String value) {
        String normalized = value.toUpperCase(Locale.ROOT);
        return !value.isBlank()
                && !normalized.contains("PROJECT_ID")
                && !normalized.contains("LOCATION")
                && !normalized.contains("YOUR_");
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("blank");
        }
        return value.trim();
    }

    public enum CredentialMode {
        STATIC_BEARER,
        GOOGLE_CLOUD_ADC
    }

    public record Candidate(
            String code,
            String displayName,
            String model,
            CredentialMode credentialMode,
            boolean runtimeAuthReady) {
    }
}
