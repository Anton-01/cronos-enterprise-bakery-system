package com.ninsky.cronos.quote.api;

import com.ninsky.cronos.quote.domain.QuoteRejectReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The client rejects from the public link; {@code NO_RESPONSE} is not a client's reason. */
public record PublicRejectRequest(@NotNull QuoteRejectReason reason, @Size(max = 500) String comment) {
}
