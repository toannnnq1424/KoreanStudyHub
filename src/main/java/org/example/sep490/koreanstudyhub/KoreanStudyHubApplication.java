package org.example.sep490.koreanstudyhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class KoreanStudyHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(KoreanStudyHubApplication.class, args);
    }
//abc
}

