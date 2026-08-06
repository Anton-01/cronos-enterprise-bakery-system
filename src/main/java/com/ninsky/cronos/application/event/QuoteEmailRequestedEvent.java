package com.ninsky.cronos.application.event;

import lombok.Builder;
import java.util.UUID;

/** Published after a user requests a quote be emailed to its client — see {@code EmailNotificationListener}. */
@Builder
public record QuoteEmailRequestedEvent(UUID quoteId) {}
