package com.ksh.features.practice.manage.speaking;

/**
 * Lecturer prompt Text-to-Speech port. Calls are owned by the later explicit
 * Generate/Regenerate command path, never GET, autosave, preview or publish.
 */
@FunctionalInterface
interface SpeakingPromptTtsPort {

    SpeakingPromptAiContract.TtsResult synthesize(
            SpeakingPromptAiContract.TtsRequest request);
}
