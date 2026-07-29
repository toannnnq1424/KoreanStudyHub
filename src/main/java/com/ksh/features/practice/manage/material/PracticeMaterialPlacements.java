package com.ksh.features.practice.manage.material;

import java.util.Set;

/**
 * Persisted Practice material-placement vocabulary shared by authoring paths.
 *
 * <p>These values are storage/reference identities. Changing their bytes would
 * orphan existing material references.</p>
 */
public final class PracticeMaterialPlacements {

    public static final String SPEAKING_PROMPT_ORIGINAL =
            "SPEAKING_PROMPT_ORIGINAL";
    public static final String SPEAKING_PROMPT_TTS =
            "SPEAKING_PROMPT_TTS";
    public static final String SPEAKING_PROMPT_EXCEL_STAGING =
            "SPEAKING_PROMPT_EXCEL_STAGING";

    private static final Set<String> SPEAKING_PROMPT_PLACEMENTS = Set.of(
            SPEAKING_PROMPT_ORIGINAL,
            SPEAKING_PROMPT_TTS,
            SPEAKING_PROMPT_EXCEL_STAGING);

    private PracticeMaterialPlacements() {
    }

    public static boolean isSpeakingPrompt(String placement) {
        return placement != null && SPEAKING_PROMPT_PLACEMENTS.contains(placement);
    }
}
