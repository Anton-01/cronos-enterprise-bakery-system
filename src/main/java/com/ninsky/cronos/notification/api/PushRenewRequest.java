package com.ninsky.cronos.notification.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code pushsubscriptionchange} from the service worker (no access token): the old endpoint proves ownership. */
public record PushRenewRequest(@NotBlank @Size(max = 1000) String oldEndpoint, @NotNull @Valid PushSubscriptionRequest subscription) {

    @Override
    public String toString() {
        return "PushRenewRequest[***]";
    }
}
