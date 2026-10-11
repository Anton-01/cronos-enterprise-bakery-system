package com.ninsky.cronos.identity.infrastructure;

import com.ninsky.cronos.domain.model.auth.DeviceFingerprint;
import com.ninsky.cronos.domain.port.auth.DeviceFingerprintRepositoryPort;
import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.token.TokenCodec;
import com.ninsky.cronos.identity.domain.IdentityEvents;
import com.ninsky.cronos.infrastructure.util.auth.RequestContextUtil;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * New-device detection (§2.3.5): the fingerprint is the hash of browser family + operating system + the /24 network of
 * the IP. A fingerprint not seen for this user within {@code memory} publishes {@code identity.new-device-sign-in}; the
 * email and the security notification are produced by its handlers, after commit.
 */
@Component
public class DeviceRecognition {

    private final DeviceFingerprintRepositoryPort devices;
    private final RequestContextUtil request;
    private final DomainEventPublisher events;

    public DeviceRecognition(DeviceFingerprintRepositoryPort devices, RequestContextUtil request, DomainEventPublisher events) {
        this.devices = devices;
        this.request = request;
        this.events = events;
    }

    /** Records the sign-in device of {@code userId}; returns its fingerprint (the session's device id). */
    public String recognise(UUID userId, Instant now, Duration memory) {
        String browser = request.getBrowser();
        String os = request.getOperatingSystem();
        String ip = request.getClientIp();
        String fingerprint = TokenCodec.hash(browser + "|" + os + "|" + network(ip));
        LocalDateTime nowLocal = TenantTime.toLocal(now);
        Optional<DeviceFingerprint> known = devices.findByUserIdAndFingerprintHash(userId, fingerprint);
        boolean recent = known.map(device -> Optional.ofNullable(device.getLastSeenAt()).orElse(device.getFirstSeenAt()))
                .map(seen -> seen.isAfter(TenantTime.toLocal(now.minus(memory))))
                .orElse(false);
        DeviceFingerprint device = known.orElseGet(() -> DeviceFingerprint.builder().userId(userId).fingerprintHash(fingerprint)
                .deviceName(os + " " + browser).browser(browser).operatingSystem(os).deviceType(request.getDevice())
                .trusted(false).loginCount(0).firstSeenAt(nowLocal).build());
        device.setUserAgent(cut(request.getUserAgent(), 500));
        device.setIpAddress(cut(ip, 45));
        device.setLocation(cut(request.getLocation(), 100));
        device.setLoginCount(device.getLoginCount() + 1);
        device.setLastSeenAt(nowLocal);
        devices.save(device);
        if (!recent) {
            events.publish(new IdentityEvents.NewDeviceSignedIn(userId, browser, os, request.getDevice(), ip, request.getLocation(), now));
        }
        return fingerprint;
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
