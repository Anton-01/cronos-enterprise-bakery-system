package com.ninsky.cronos.infrastructure.config;

import com.ninsky.cronos.application.imports.ImportProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(ImportProperties.class)
public class ImportConfig {

    /** UTC wall clock for import timestamps; replaceable in tests. */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
