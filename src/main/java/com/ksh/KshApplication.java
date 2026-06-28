package com.ksh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KshApplication {

	public static void main(String[] args) {
		SpringApplication.run(KshApplication.class, args);
	}

}

