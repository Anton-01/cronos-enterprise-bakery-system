package com.ninsky.cronos.notification.infrastructure.push;

import com.ninsky.cronos.notification.infrastructure.NotificationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link WebPushGateway} over the JDK HTTP client: RFC 8291 payload encryption and a VAPID JWT per push-service origin,
 * cached for 12 hours (the maximum lifetime is 24 hours).
 */
@Slf4j
@Component
public class JdkWebPushGateway implements WebPushGateway {

    static final Duration JWT_LIFETIME = Duration.ofHours(12);

    private record CachedJwt(String header, Instant expiresAt) {
    }

    private final NotificationProperties properties;
    private final Clock clock;
    private final HttpClient http;
    private final Map<String, CachedJwt> jwtByOrigin = new ConcurrentHashMap<>();
    private final ECPrivateKey privateKey;
    private final byte[] publicKey;

    public JdkWebPushGateway(NotificationProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.http = HttpClient.newBuilder().connectTimeout(properties.pushTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
        ECPrivateKey key = null;
        byte[] pub = null;
        if (properties.pushActive()) {
            try {
                pub = WebPushCrypto.base64Url(properties.vapidPublicKey());
                WebPushCrypto.publicKey(pub);
                key = WebPushCrypto.privateKey(WebPushCrypto.base64Url(properties.vapidPrivateKey()));
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                log.error("Web Push disabled: the VAPID keys are not valid P-256 keys ({})", e.getClass().getSimpleName());
                key = null;
                pub = null;
            }
        } else if (properties.pushEnabled()) {
            log.warn("Web Push is enabled but the VAPID keys are not configured; pushes are skipped");
        }
        this.privateKey = key;
        this.publicKey = pub;
    }

    @Override
    public boolean enabled() {
        return privateKey != null;
    }

    @Override
    public String publicKey() {
        return publicKey == null ? null : WebPushCrypto.base64Url(publicKey);
    }

    @Override
    public Result send(Target target, byte[] payload, Duration ttl, Urgency urgency, String topic) {
        if (!enabled()) {
            return new Result(0, "Web Push is not configured");
        }
        try {
            URI endpoint = URI.create(target.endpoint());
            byte[] body = WebPushCrypto.encrypt(payload, WebPushCrypto.base64Url(target.p256dh()), WebPushCrypto.base64Url(target.auth()));
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                    .timeout(properties.pushTimeout())
                    .header("Content-Type", "application/octet-stream")
                    .header("Content-Encoding", "aes128gcm")
                    .header("TTL", Long.toString(Math.max(0, ttl.toSeconds())))
                    .header("Urgency", urgency.name().toLowerCase(Locale.ROOT).replace('_', '-'))
                    .header("Authorization", authorization(endpoint))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            if (topic != null && !topic.isBlank()) {
                request.header("Topic", topic);
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Result(response.statusCode(), response.statusCode() / 100 == 2 ? null : abbreviate(response.body()));
        } catch (IOException e) {
            return new Result(0, e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(0, "Interrupted");
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return new Result(400, "Invalid subscription keys: " + e.getMessage());
        }
    }

    private String authorization(URI endpoint) throws GeneralSecurityException {
        String origin = endpoint.getScheme() + "://" + endpoint.getHost() + (endpoint.getPort() < 0 ? "" : ":" + endpoint.getPort());
        Instant now = clock.instant();
        CachedJwt cached = jwtByOrigin.get(origin);
        if (cached == null || cached.expiresAt().minus(Duration.ofHours(1)).isBefore(now)) {
            Instant expiresAt = now.plus(JWT_LIFETIME);
            cached = new CachedJwt(WebPushCrypto.vapidAuthorization(origin, properties.pushSubject(), expiresAt, privateKey, publicKey), expiresAt);
            jwtByOrigin.put(origin, cached);
        }
        return cached.header();
    }

    private static String abbreviate(String body) {
        return body == null || body.length() <= 300 ? body : body.substring(0, 300);
    }
}
