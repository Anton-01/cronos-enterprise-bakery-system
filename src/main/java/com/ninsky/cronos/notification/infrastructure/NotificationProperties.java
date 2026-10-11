package com.ninsky.cronos.notification.infrastructure;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * {@code app.notifications.*}: the live stream and Web Push (§6.3, §6.4). Push is active only when enabled and both
 * VAPID keys are configured; the private key comes from the secret manager and is never logged.
 *
 * @param pushAllowedHosts host suffixes a subscription endpoint may point to (the push services of the browsers); any
 *                         other endpoint is refused, so the server never posts to an arbitrary URL
 */
@Validated
@ConfigurationProperties("app.notifications")
public record NotificationProperties(
        @DefaultValue("30m") @NotNull Duration streamTimeout,
        @DefaultValue("25s") @NotNull Duration heartbeat,
        @DefaultValue("60s") @NotNull Duration ticketTtl,
        @DefaultValue("100") @Min(1) @Max(500) int replayLimit,
        @DefaultValue("cronos:sse") @NotNull String streamChannel,
        @DefaultValue("false") boolean pushEnabled,
        @DefaultValue("mailto:soporte@cronos.mx") @NotNull String pushSubject,
        String vapidPublicKey,
        String vapidPrivateKey,
        @DefaultValue("24h") @NotNull Duration pushDefaultTtl,
        @DefaultValue("10") @Min(1) @Max(50) int maxDevicesPerUser,
        @DefaultValue("5") @Min(1) @Max(20) int maxConsecutiveFailures,
        @DefaultValue("5s") @NotNull Duration pushTimeout,
        @DefaultValue({"fcm.googleapis.com", "push.services.mozilla.com", "notify.windows.com", "push.apple.com"})
        List<String> pushAllowedHosts
) {

    public NotificationProperties {
        pushAllowedHosts = pushAllowedHosts == null ? List.of() : List.copyOf(pushAllowedHosts);
    }

    /** True when pushes can be signed. */
    public boolean pushActive() {
        return pushEnabled && vapidPublicKey != null && !vapidPublicKey.isBlank() && vapidPrivateKey != null && !vapidPrivateKey.isBlank();
    }

    @Override
    public String toString() {
        return "NotificationProperties[pushEnabled=" + pushEnabled + ", vapidPublicKey=" + vapidPublicKey + ", vapidPrivateKey=***]";
    }
}
