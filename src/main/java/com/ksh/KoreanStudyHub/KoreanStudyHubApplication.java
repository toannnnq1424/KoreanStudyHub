package com.ksh.KoreanStudyHub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
public class KoreanStudyHubApplication {

	public static void main(String[] args) {
		SpringApplication.run(KoreanStudyHubApplication.class, args);
	}
}
