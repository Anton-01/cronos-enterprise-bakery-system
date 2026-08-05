package com.ninsky.cronos.infrastructure.config.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "jwe")
@Getter
@Setter
public class JweConfig {

    /** KMS-wrapped EC private key, unwrapped once at startup by {@code JweKeyService}. */
    private String wrappedPrivateKey;

    private final Response response = new Response();

    @Getter
    @Setter
    public static class Response {
        /** Response buffering cap before JWE-encrypting — exceeding it fails loudly (5xx), never falls back to plaintext. */
        private long maxBufferedBytes = 4L * 1024 * 1024;

        /** Paths that opt out of response buffering/encryption entirely, even if the client asks for it. */
        private List<String> excludedPaths = new ArrayList<>();
    }
}
