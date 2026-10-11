package com.ninsky.cronos.quote.api;

import com.ninsky.cronos.quote.domain.QuoteRejectReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code POST /quotes/{id}/reject}; OTHER needs a comment. */
public record RejectQuoteRequest(@NotNull QuoteRejectReason reason, @Size(max = 500) String comment) {
}
