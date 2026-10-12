package com.ninsky.cronos.identity.infrastructure;

import com.ninsky.cronos.domain.model.auth.DeviceFingerprint;
import com.ninsky.cronos.domain.port.auth.DeviceFingerprintRepositoryPort;
import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.token.TokenCodec;
import com.ninsky.cronos.identity.domain.IdentityEvents;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * New-device detection (§2.3.5): the fingerprint is the hash of browser family + operating system + the /24 network of
 * the IP. A fingerprint not seen for this user within {@code memory} publishes {@code identity.new-device-sign-in}; the
 * email and the security notification are produced by its handlers. Runs as a consumer of
 * {@code identity.user-signed-in} (performance-latency P0-5): the sign-in request only computes the fingerprint.
 */
@Component
public class DeviceRecognition {

    private final DeviceFingerprintRepositoryPort devices;
    private final DomainEventPublisher events;

    public DeviceRecognition(DeviceFingerprintRepositoryPort devices, DomainEventPublisher events) {
        this.devices = devices;
        this.events = events;
    }

    /** The device fingerprint of a request (the session's device id); pure, no I/O. */
    public static String fingerprint(String browser, String operatingSystem, String ip) {
        return TokenCodec.hash(browser + "|" + operatingSystem + "|" + network(ip));
    }

    /**
     * Records the sign-in device of {@code userId} (upsert) and publishes the new-device event when it was not seen
     * within {@code memory}. Inside the consumer's transaction; the consumer runs once per event.
     */
    public void recognise(UUID userId, IdentityEvents.SignInContext context, Duration memory) {
        Instant now = context.at();
        String browser = context.browser();
        String os = context.operatingSystem();
        String fingerprint = fingerprint(browser, os, context.ipAddress());
        LocalDateTime nowLocal = TenantTime.toLocal(now);
        Optional<DeviceFingerprint> known = devices.findByUserIdAndFingerprintHash(userId, fingerprint);
        boolean recent = known.map(device -> Optional.ofNullable(device.getLastSeenAt()).orElse(device.getFirstSeenAt()))
                .map(seen -> seen.isAfter(TenantTime.toLocal(now.minus(memory))))
                .orElse(false);
        DeviceFingerprint device = known.orElseGet(() -> DeviceFingerprint.builder().userId(userId).fingerprintHash(fingerprint)
                .deviceName(os + " " + browser).browser(browser).operatingSystem(os).deviceType(context.device())
                .trusted(false).loginCount(0).firstSeenAt(nowLocal).build());
        device.setUserAgent(cut(context.userAgent(), 500));
        device.setIpAddress(cut(context.ipAddress(), 45));
        device.setLocation(cut(context.location(), 100));
        device.setLoginCount(device.getLoginCount() + 1);
        device.setLastSeenAt(nowLocal);
        devices.save(device);
        if (!recent) {
            events.publish(new IdentityEvents.NewDeviceSignedIn(userId, browser, os, context.device(), context.ipAddress(),
                    context.location(), now));
        }
    }

    /** {@code a.b.c.0/24} for IPv4, the first four groups for IPv6, the value itself otherwise. */
    static String network(String ip) {
        if (ip == null) {
            return "";
        }
        if (ip.indexOf('.') > 0 && ip.indexOf(':') < 0) {
            int last = ip.lastIndexOf('.');
            return ip.substring(0, last) + ".0/24";
        }
        String[] groups = ip.split(":");
        return groups.length > 4 ? String.join(":", groups[0], groups[1], groups[2], groups[3]) + "::/64" : ip;
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
