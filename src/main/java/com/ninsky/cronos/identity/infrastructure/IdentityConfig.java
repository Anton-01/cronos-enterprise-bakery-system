package com.ninsky.cronos.identity.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@link AuthProperties}. */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class IdentityConfig {
}
