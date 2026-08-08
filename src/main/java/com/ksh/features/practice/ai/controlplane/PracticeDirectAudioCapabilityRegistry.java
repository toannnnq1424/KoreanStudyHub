package com.ksh.features.practice.ai.controlplane;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repository-controlled direct-audio capability verification registry.
 *
 * <p>The presets are convenience plus immutable technical evidence, not
 * provider-policy evidence. An unknown endpoint/model is a valid draft value,
 * but remains unverified and must never resolve to transport.</p>
 */
public final class PracticeDirectAudioCapabilityRegistry {

    public static final String REGISTRY_ARTIFACT_ID =
            "KSH_PRACTICE_DIRECT_AUDIO_CAPABILITY_VERIFICATION_V1";

    public static final String GEMINI_DEVELOPER_CODE =
            "GEMINI_DEVELOPER_DIRECT_AUDIO";
    public static final String GEMINI_DEVELOPER_MODEL = "gemini-3.6-flash";
    public static final String GEMINI_DEVELOPER_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/openai";

    public static final String GEMINI_ENTERPRISE_CODE =
            "GEMINI_ENTERPRISE_DIRECT_AUDIO";
    public static final String GEMINI_ENTERPRISE_MODEL = "gemini-3.5-flash";

    public static final String DEVELOPER_AUDIO_INPUT_EVIDENCE =
            "KSH-DA-AUDIO-GEMINI-DEVELOPER-2026-08-03";
    public static final String DEVELOPER_STRICT_OUTPUT_EVIDENCE =
            "KSH-DA-STRICT-OUTPUT-GEMINI-DEVELOPER-2026-08-03";
    public static final String ENTERPRISE_AUDIO_INPUT_EVIDENCE =
            "KSH-DA-AUDIO-GEMINI-ENTERPRISE-2026-08-03";
    public static final String ENTERPRISE_STRICT_OUTPUT_EVIDENCE =
            "KSH-DA-STRICT-OUTPUT-GEMINI-ENTERPRISE-2026-08-03";
    public static final String DEVELOPER_AUTH_ENDPOINT_EVIDENCE =
            "KSH-DA-AUTH-ENDPOINT-GEMINI-DEVELOPER-2026-08-03";
    public static final String ENTERPRISE_AUTH_ENDPOINT_EVIDENCE =
            "KSH-DA-AUTH-ENDPOINT-GEMINI-ENTERPRISE-2026-08-03";

    private static final Pattern VERTEX_PATH = Pattern.compile(
            "^/v1(?:beta1)?/projects/([^/]+)/locations/([^/]+)/endpoints/openapi$");

    private PracticeDirectAudioCapabilityRegistry() {
    }

    public static Verification assess(String rawBaseUrl, String rawModel) {
        URI uri;
        String model;
        try {
            uri = URI.create(required(rawBaseUrl));
            model = required(rawModel);
        } catch (RuntimeException exception) {
            return Verification.unverified();
        }
        if (developerEndpoint(uri) && GEMINI_DEVELOPER_MODEL.equals(model)) {
            return Verification.verified(
                    GEMINI_DEVELOPER_CODE,
                    "Gemini Developer API",
                    PracticeAiCredentialMode.STATIC_BEARER,
                    true,
                    DEVELOPER_AUDIO_INPUT_EVIDENCE,
                    DEVELOPER_STRICT_OUTPUT_EVIDENCE,
                    DEVELOPER_AUTH_ENDPOINT_EVIDENCE);
        }
        Matcher vertex = VERTEX_PATH.matcher(normalizedPath(uri));
        if (vertex.matches()
                && vertexHost(uri)
                && concrete(vertex.group(1))
                && concrete(vertex.group(2))
                && GEMINI_ENTERPRISE_MODEL.equals(model)) {
            return Verification.verified(
                    GEMINI_ENTERPRISE_CODE,
                    "Gemini Enterprise / Vertex AI",
                    PracticeAiCredentialMode.GOOGLE_CLOUD_ADC,
                    false,
                    ENTERPRISE_AUDIO_INPUT_EVIDENCE,
                    ENTERPRISE_STRICT_OUTPUT_EVIDENCE,
                    ENTERPRISE_AUTH_ENDPOINT_EVIDENCE);
        }
        return Verification.unverified();
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

    public enum State {
        VERIFIED_PRESET,
        UNVERIFIED
    }

    public record Verification(
            State state,
            String code,
            String displayName,
            PracticeAiCredentialMode credentialMode,
            boolean runtimeAuthReady,
            String audioInputEvidenceId,
            String strictStructuredOutputEvidenceId,
            String authEndpointEvidenceId) {

        static Verification verified(
                String code,
                String displayName,
                PracticeAiCredentialMode credentialMode,
                boolean runtimeAuthReady,
                String audioInputEvidenceId,
                String strictStructuredOutputEvidenceId,
                String authEndpointEvidenceId) {
            return new Verification(
                    State.VERIFIED_PRESET, code, displayName, credentialMode,
                    runtimeAuthReady, audioInputEvidenceId,
                    strictStructuredOutputEvidenceId, authEndpointEvidenceId);
        }

        static Verification unverified() {
            return new Verification(
                    State.UNVERIFIED, null, null, null, false,
                    null, null, null);
        }

        public boolean verified() {
            return state == State.VERIFIED_PRESET
                    && present(audioInputEvidenceId)
                    && present(strictStructuredOutputEvidenceId)
                    && present(authEndpointEvidenceId);
        }

        private static boolean present(String value) {
            return value != null && !value.isBlank();
        }
    }
}
