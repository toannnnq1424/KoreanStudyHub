package com.ksh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the ksh (University Learning Platform) Spring Boot application.
 *
 * <p>This class bootstraps the Spring application context, triggers Flyway schema
 * migrations, and starts the embedded web server. All auto-configuration is
 * enabled via {@link SpringBootApplication}.
 *
 * <p>{@link EnableScheduling} is enabled so that scheduled tasks (e.g. the
 * import-session cleanup job in {@code com.ksh.features.classes.imports.session.ImportSessionStore})
 * are picked up by Spring's task scheduler.
 */
@SpringBootApplication
@EnableScheduling
public class KshApplication {

	/**
	 * Launches the ksh application.
	 *
	 * @param args command-line arguments passed to {@link SpringApplication#run}
	 */
	public static void main(String[] args) {
		SpringApplication.run(KshApplication.class, args);
	}

}
