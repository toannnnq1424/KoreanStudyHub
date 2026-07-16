package com.ksh.features.practice.controller;

import com.ksh.features.practice.service.SpeakingAudioUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.practice.speaking-media.upload-api-enabled=true")
class PracticeSpeakingMediaGateTrueTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @MockBean
    private SpeakingAudioUploadService uploadService;

    @Test
    void propertyTrueRegistersControllerAndRoutes() {
        assertThat(applicationContext.getBeansOfType(PracticeSpeakingMediaController.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(PracticeSpeakingMediaPlaybackController.class)).isEmpty();
        assertThat(mappedSpeakingMediaRoutes()).isEqualTo(2);
        assertThat(mappedSpeakingMediaRoutes(RequestMethod.POST,
                "/practice/attempts/{attemptId}/questions/{questionId}/speaking-media")).isEqualTo(1);
        assertThat(mappedSpeakingMediaRoutes(RequestMethod.DELETE,
                "/practice/attempts/{attemptId}/questions/{questionId}/speaking-media/{mediaId}")).isEqualTo(1);
    }

    private long mappedSpeakingMediaRoutes() {
        return handlerMapping.getHandlerMethods().keySet().stream()
                .filter(info -> info.toString().contains("/speaking-media"))
                .count();
    }

    private long mappedSpeakingMediaRoutes(RequestMethod method, String path) {
        return handlerMapping.getHandlerMethods().keySet().stream()
                .filter(info -> info.getMethodsCondition().getMethods().contains(method))
                .filter(info -> info.toString().contains(path))
                .count();
    }
}
