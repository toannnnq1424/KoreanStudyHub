package com.ksh.features.practice.ai.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PracticeAiMetricsConfiguration {

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    MeterRegistry practiceAiMeterRegistry() {
        return new SimpleMeterRegistry();
    }
}
