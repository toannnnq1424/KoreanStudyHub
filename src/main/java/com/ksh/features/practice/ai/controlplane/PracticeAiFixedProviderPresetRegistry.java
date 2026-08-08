package com.ksh.features.practice.ai.controlplane;

import java.util.List;
import java.util.Optional;

/**
 * Fixed provider-profile metadata for Practice AI setup convenience.
 *
 * <p>A preset proves only the provider identity and OpenAI-compatible base URL.
 * It deliberately contains no model identifiers and grants no capability or
 * readiness authority.</p>
 */
public final class PracticeAiFixedProviderPresetRegistry {

    public static final String XAI_GROK_KEY = "XAI_GROK";
    public static final String XAI_GROK_PROFILE_CODE = "PRACTICE_XAI_GROK";
    public static final String XAI_GROK_BASE_URL = "https://api.x.ai/v1";
    public static final String XAI_GROK_KEY_CONSOLE_URL =
            "https://console.x.ai/team/default/api-keys";

    public static final String GROQ_KEY = "GROQ";
    public static final String GROQ_PROFILE_CODE = "PRACTICE_GROQ";
    public static final String GROQ_BASE_URL = "https://api.groq.com/openai/v1";
    public static final String GROQ_KEY_CONSOLE_URL =
            "https://console.groq.com/keys";

    private static final List<Preset> PRESETS = List.of(
            new Preset(
                    XAI_GROK_KEY,
                    XAI_GROK_PROFILE_CODE,
                    "xAI / Grok cho Practice",
                    XAI_GROK_BASE_URL,
                    XAI_GROK_KEY_CONSOLE_URL),
            new Preset(
                    GROQ_KEY,
                    GROQ_PROFILE_CODE,
                    "Groq cho Practice",
                    GROQ_BASE_URL,
                    GROQ_KEY_CONSOLE_URL));

    private PracticeAiFixedProviderPresetRegistry() {
    }

    public static List<Preset> all() {
        return PRESETS;
    }

    public static Optional<Preset> findByKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return PRESETS.stream()
                .filter(preset -> preset.key().equals(key))
                .findFirst();
    }

    public static Optional<Preset> findByProfileCode(String profileCode) {
        if (profileCode == null) {
            return Optional.empty();
        }
        return PRESETS.stream()
                .filter(preset -> preset.profileCode().equals(profileCode))
                .findFirst();
    }

    public record Preset(
            String key,
            String profileCode,
            String displayName,
            String baseUrl,
            String keyConsoleUrl) {
    }
}
