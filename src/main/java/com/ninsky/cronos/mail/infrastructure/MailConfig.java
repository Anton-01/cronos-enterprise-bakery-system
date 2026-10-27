package com.ninsky.cronos.mail.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.mail.application.MailProperties;
import com.ninsky.cronos.mail.domain.TemplateDefaults;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Mail module wiring: {@code app.mail.*} and the built-in template defaults. */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    /** The parsed {@code mail/catalog.json}, loaded once at startup. */
    @Bean
    public TemplateDefaults templateDefaults(ObjectMapper json) {
        return ClasspathTemplateDefaults.load(json);
    }
}
