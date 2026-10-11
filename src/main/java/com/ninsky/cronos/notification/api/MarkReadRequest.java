package com.ninsky.cronos.notification.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** {@code POST /notifications/read}. */
public record MarkReadRequest(@NotEmpty @Size(max = 100) List<@NotNull UUID> ids) {

    public MarkReadRequest {
        ids = ids == null ? List.of() : List.copyOf(ids);
    }
}
