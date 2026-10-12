package com.ninsky.cronos.quote.domain;

import com.ninsky.cronos.eventing.domain.DomainEvent;
import com.ninsky.cronos.eventing.domain.EventType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Domain events of the quote lifecycle (§8.2). Aggregate: the quote; {@code ownerId} is the baker. */
public final class QuoteEvents {

    private static final String QUOTE = "QUOTE";

    private QuoteEvents() {
    }

    @EventType("quote.created")
    public record QuoteCreated(UUID quoteId, String quoteNumber, UUID ownerId) implements DomainEvent {
        @Override public String aggregateType() { return QUOTE; }
        @Override public UUID aggregateId() { return quoteId; }
    }

    @EventType("quote.updated")
    public record QuoteUpdated(UUID quoteId, String quoteNumber, UUID ownerId) implements DomainEvent {
        @Override public String aggregateType() { return QUOTE; }
        @Override public UUID aggregateId() { return quoteId; }
    }

    /** DRAFT/EXPIRED → SENT, or another out-of-band share of an open quote. */
    @EventType("quote.sent")
    public record QuoteSent(UUID quoteId, String quoteNumber, UUID ownerId, ShareChannel channel) implements DomainEvent {
        @Override public String aggregateType() { return QUOTE; }
        @Override public UUID aggregateId() { return quoteId; }
    }

    /** First open of the public link. */
    @EventType("quote.viewed")
    public record QuoteViewed(UUID quoteId, String quoteNumber, UUID ownerId, String clientName) implements DomainEvent {
        @Override public String aggregateType() { return QUOTE; }
        @Override public UUID aggregateId() { return quoteId; }
    }

    @EventType("quote.accepted")
    public record QuoteAccepted(UUID quoteId, String quoteNumber, UUID ownerId, QuoteActorType actor, String actorName,
                                BigDecimal total, String currency, LocalDate eventDate, UUID calendarEventId, String comment)
            implements DomainEvent {
        @Override public String aggregateType() { return QUOTE; }
        @Override public UUID aggregateId() { return quoteId; }
    }

    @EventType("quote.rejected")
    public record QuoteRejected(UUID quoteId, String quoteNumber, UUID ownerId, QuoteActorType actor, String actorName,
                                QuoteRejectReason reason, String comment) implements DomainEvent {
        @Override public String aggregateType() { return QUOTE; }
        @Override public UUID aggregateId() { return quoteId; }
    }

    /** An open quote expires within two days (once per quote). */
    @EventType("quote.expiring")
    public record QuoteExpiring(UUID quoteId, String quoteNumber, UUID ownerId, LocalDate validUntil) implements DomainEvent {
        @Override public String aggregateType() { return QUOTE; }
        @Override public UUID aggregateId() { return quoteId; }
    }

    @EventType("quote.expired")
    public record QuoteExpired(UUID quoteId, String quoteNumber, UUID ownerId) implements DomainEvent {
        @Override public String aggregateType() { return QUOTE; }
        @Override public UUID aggregateId() { return quoteId; }
    }

    @EventType("quote.revoked")
    public record QuoteRevoked(UUID quoteId, String quoteNumber, UUID ownerId) implements DomainEvent {
        @Override public String aggregateType() { return QUOTE; }
        @Override public UUID aggregateId() { return quoteId; }
    }

    @EventType("quote.duplicated")
    public record QuoteDuplicated(UUID quoteId, String quoteNumber, UUID ownerId, UUID sourceQuoteId) implements DomainEvent {
        @Override public String aggregateType() { return QUOTE; }
        @Override public UUID aggregateId() { return quoteId; }
    }

    @EventType("quote.deleted")
    public record QuoteDeleted(UUID quoteId, String quoteNumber, UUID ownerId) implements DomainEvent {
        @Override public String aggregateType() { return QUOTE; }
        @Override public UUID aggregateId() { return quoteId; }
    }
}
