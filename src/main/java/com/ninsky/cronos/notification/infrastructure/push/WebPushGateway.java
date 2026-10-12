package com.ninsky.cronos.notification.infrastructure.push;

import java.time.Duration;

/** Sends one encrypted message to one push service endpoint (§6.4.3). */
public interface WebPushGateway {

    /** Delivery hints of one message. */
    enum Urgency { VERY_LOW, LOW, NORMAL, HIGH }

    /** A subscription's address and keys. */
    record Target(String endpoint, String p256dh, String auth) {
    }

    /** What the push service answered; {@code status} is 0 when no answer arrived. */
    record Result(int status, String error) {

        public boolean delivered() {
            return status >= 200 && status < 300;
        }

        /** The subscription is gone for good (404/410). */
        public boolean gone() {
            return status == 404 || status == 410;
        }

        /** Retrying cannot help (payload too large or malformed). */
        public boolean permanent() {
            return status == 400 || status == 413;
        }
    }

    /** True when VAPID keys are configured. */
    boolean enabled();

    /** The VAPID public key (base64url) the browser subscribes with, or null when disabled. */
    String publicKey();

    Result send(Target target, byte[] payload, Duration ttl, Urgency urgency, String topic);
}
