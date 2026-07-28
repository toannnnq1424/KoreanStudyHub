package com.ksh.features.practice.manage.speaking;

/**
 * Lecturer prompt Speech-to-Text port. Implementations must not resolve or
 * transcribe learner {@code PracticeSpeakingMedia}.
 */
@FunctionalInterface
interface SpeakingPromptSttPort {

    SpeakingPromptAiContract.SttResult transcribe(
            SpeakingPromptAiContract.SttRequest request);
}
