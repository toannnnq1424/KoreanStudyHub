package com.ksh.features.practice.controller;

import com.ksh.features.practice.service.PracticeSpeakingMediaPlaybackService;
import com.ksh.features.practice.service.SpeakingAudioUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.practice.speaking-media.upload-api-enabled=true",
        "app.practice.speaking-media.playback-api-enabled=true"
})
class PracticeSpeakingMediaUploadAndPlaybackGateIndependenceTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private SpeakingAudioUploadService uploadService;

    @MockBean
    private PracticeSpeakingMediaPlaybackService playbackService;

    @Test
    void bothGatesCanRegisterTheirOwnControllersWithoutSharingProperties() {
        assertThat(applicationContext.getBeansOfType(PracticeSpeakingMediaController.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(PracticeSpeakingMediaPlaybackController.class)).hasSize(1);
    }
}
