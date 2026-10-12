package com.ninsky.cronos.notification.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /notifications/push/subscriptions/unsubscribe}. */
public record EndpointRequest(@NotBlank @Size(max = 1000) String endpoint) {

    @Override
    public String toString() {
        return "EndpointRequest[***]";
    }
}
