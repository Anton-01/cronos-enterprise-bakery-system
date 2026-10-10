package com.ninsky.cronos.eventing.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@link EventingProperties}. */
@Configuration
@EnableConfigurationProperties(EventingProperties.class)
public class EventingConfig {
}
