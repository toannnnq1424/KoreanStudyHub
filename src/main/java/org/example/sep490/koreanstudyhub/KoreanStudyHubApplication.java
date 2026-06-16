package org.example.sep490.koreanstudyhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
public class KoreanStudyHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(com.ksh.KoreanStudyHub.KoreanStudyHubApplication.class, args);
    }
}
