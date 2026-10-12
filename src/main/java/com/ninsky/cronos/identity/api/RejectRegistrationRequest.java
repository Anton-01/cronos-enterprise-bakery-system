package com.ninsky.cronos.identity.api;

import com.ninsky.cronos.identity.domain.RejectReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code POST /iam/registrations/{id}/reject}; {@code comment} is required (10–500) when the reason is OTHER. */
public record RejectRegistrationRequest(@NotNull RejectReason reason, @Size(max = 500) String comment, Boolean notifyApplicant,
                                        @NotNull Long version) {
}
