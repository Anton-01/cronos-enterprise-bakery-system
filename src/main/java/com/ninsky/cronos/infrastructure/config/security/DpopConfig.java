package com.ninsky.cronos.infrastructure.config.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "dpop")
@Getter
@Setter
public class DpopConfig {

    /** Operational kill-switch — when false, DPoP-scheme requests are rejected outright (Bearer still works). */
    private boolean enabled = true;

    /** Max age of a proof's {@code iat} before it's considered stale, and the replay-guard TTL. */
    private int proofMaxAgeSeconds = 120;
}
