package com.ksh.features.practice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.practice.speaking-media.upload-api-enabled=false")
class PracticeSpeakingMediaGateFalseTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void propertyFalseDoesNotRegisterControllerOrRoutes() {
        assertThat(applicationContext.getBeansOfType(PracticeSpeakingMediaController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(PracticeSpeakingMediaPlaybackController.class)).isEmpty();
        assertThat(mappedSpeakingMediaRoutes()).isZero();
    }

    private long mappedSpeakingMediaRoutes() {
        return handlerMapping.getHandlerMethods().keySet().stream()
                .filter(info -> info.toString().contains("/speaking-media"))
                .count();
    }
}
