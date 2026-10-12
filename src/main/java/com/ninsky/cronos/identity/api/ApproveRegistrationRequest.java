package com.ninsky.cronos.identity.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code POST /iam/registrations/{id}/approve}. */
public record ApproveRegistrationRequest(@NotEmpty @Size(max = 20) List<Long> roleIds, @Size(max = 500) String comment,
                                         @NotNull Long version) {
}
