package com.ksh.features.practice.manage.speaking;

interface SpeakingPromptAudioVerifier {

    SpeakingPromptAiContract.VerifiedAudio verifySttInput(
            byte[] bytes,
            String filename,
            String declaredMimeType,
            String expectedSha256);

    SpeakingPromptAiContract.VerifiedAudio verifyTtsOutput(
            byte[] bytes,
            String filename,
            String declaredMimeType);
}
