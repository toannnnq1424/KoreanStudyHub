package com.ksh.features.practice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class PracticeSpeakingMediaGateAbsentTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void propertyAbsentDoesNotRegisterControllerOrRoutes() throws Exception {
        assertThat(applicationContext.getBeansOfType(PracticeSpeakingMediaController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(PracticeSpeakingMediaPlaybackController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(PracticeSpeakingMediaControllerAdvice.class)).hasSize(1);
        assertThat(mappedSpeakingMediaRoutes()).isZero();

        mockMvc.perform(delete("/practice/attempts/1/questions/2/speaking-media/3")
                        .with(user("student").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/practice/attempts/1/questions/2/speaking-media/3/content")
                        .with(user("student").roles("STUDENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void scopedAdviceDoesNotConvertUnrelatedGlobalErrorsToJsonWhenGateAbsent() throws Exception {
        mockMvc.perform(get("/definitely-not-speaking-media")
                        .with(user("student").roles("STUDENT")))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    private long mappedSpeakingMediaRoutes() {
        return handlerMapping.getHandlerMethods().keySet().stream()
                .filter(info -> info.toString().contains("/speaking-media"))
                .count();
    }
}
