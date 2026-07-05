package com.ksh;

import com.ksh.features.practice.controller.PracticeSpeakingMediaController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class KshApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void contextLoads() {
	}

	@Test
	void speakingMediaUploadApiControllerIsAbsentByDefault() {
		assertThat(applicationContext.getBeansOfType(PracticeSpeakingMediaController.class)).isEmpty();
	}

}
