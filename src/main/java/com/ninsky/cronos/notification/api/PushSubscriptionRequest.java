package com.ninsky.cronos.notification.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The browser's {@code PushSubscription.toJSON()} plus an optional label. */
public record PushSubscriptionRequest(@NotBlank @Size(max = 1000) String endpoint,
                                      @NotNull @Valid Keys keys,
                                      @Size(max = 80) String deviceLabel,
                                      @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String locale) {

    /** Subscription keys, base64url. */
    public record Keys(@NotBlank @Size(max = 255) String p256dh, @NotBlank @Size(max = 64) String auth) {
        @Override
        public String toString() {
            return "Keys[***]";
        }
    }

    @Override
    public String toString() {
        return "PushSubscriptionRequest[deviceLabel=" + deviceLabel + "]";
    }
}
