package com.ksh.features.practice.manage.speaking;

import org.springframework.stereotype.Service;

/**
 * Keeps unbound private storage and ffprobe verification outside the short
 * draft/source binding transaction. The surrounding authoring calls check the
 * same source/draft expectation before staging and again under lock before the
 * first exact material-reference binding. This coordinator never invokes a
 * provider.
 */
@Service
public class SpeakingPromptOriginalAudioUploadCoordinator {

    private final SpeakingPromptAuthoringService authoringService;
    private final SpeakingPromptAssetService assetService;

    public SpeakingPromptOriginalAudioUploadCoordinator(
            SpeakingPromptAuthoringService authoringService,
            SpeakingPromptAssetService assetService) {
        this.authoringService = authoringService;
        this.assetService = assetService;
    }

    public SpeakingPromptAuthoringService.EnqueueResult uploadAndEnqueueStt(
            SpeakingPromptAuthoringService.UploadOriginalAudio command) {
        authoringService.requireUploadAllowed(command);
        SpeakingPromptAssetService.VerifiedOriginalUpload verified =
                assetService.uploadOriginal(
                        command.draftId(),
                        command.actorId(),
                        command.questionClientId(),
                        command.file());
        return authoringService.bindVerifiedOriginalUpload(command, verified);
    }

}
