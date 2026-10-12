package com.ninsky.cronos.quote.api;

import com.ninsky.cronos.quote.domain.ShareChannel;
import jakarta.validation.constraints.NotNull;

/** {@code POST /quotes/{id}/shared}: the quote was shared out of band (WhatsApp, copied link, own email). */
public record ShareQuoteRequest(@NotNull ShareChannel channel) {
}
