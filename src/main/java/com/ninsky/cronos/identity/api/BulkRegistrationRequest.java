package com.ninsky.cronos.identity.api;

import com.ninsky.cronos.identity.domain.RejectReason;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** {@code POST /iam/registrations/bulk}: up to 100 decisions, each in its own transaction. */
public record BulkRegistrationRequest(@NotEmpty @Size(max = 100) List<UUID> ids, @NotNull Decision decision, List<Long> roleIds,
                                      RejectReason reason, @Size(max = 500) String comment, Boolean notifyApplicant) {

    /** What to do with every id. */
    public enum Decision { APPROVE, REJECT }
}
