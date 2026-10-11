package com.ninsky.cronos.quote.application;

import com.ninsky.cronos.domain.entity.enums.QuoteStatus;
import com.ninsky.cronos.domain.model.quote.Quote;
import com.ninsky.cronos.domain.model.quote.QuoteItem;
import com.ninsky.cronos.domain.port.quote.QuoteRepositoryPort;
import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.eventing.application.PublishOptions;
import com.ninsky.cronos.eventing.application.TraceContexts;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.shared.UserDirectory;
import com.ninsky.cronos.iam.shared.UserRef;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.quote.api.AcceptQuoteRequest;
import com.ninsky.cronos.quote.api.AcceptQuoteResponse;
import com.ninsky.cronos.quote.api.PublicAcceptRequest;
import com.ninsky.cronos.quote.api.PublicRejectRequest;
import com.ninsky.cronos.quote.api.QuoteStats;
import com.ninsky.cronos.quote.domain.QuoteActorType;
import com.ninsky.cronos.quote.domain.QuoteEvents;
import com.ninsky.cronos.quote.domain.QuoteNumbers;
import com.ninsky.cronos.quote.domain.QuoteRejectReason;
import com.ninsky.cronos.quote.domain.QuoteTransitions;
import com.ninsky.cronos.quote.domain.ShareChannel;
import com.ninsky.cronos.quote.infrastructure.QuoteHistoryCustomRepository;
import com.ninsky.cronos.quote.infrastructure.QuoteQueryCustomRepository;
import com.ninsky.cronos.finance.settings.PricingSnapshotResolver;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Quote lifecycle commands (§4): every transition goes through the state machine, appends to the status history in the
 * same transaction and publishes a {@code quote.*} event; nothing here sends an email or a notification. Accepting with
 * a schedule creates the order through the calendar module in the same transaction.
 */
@Slf4j
@Service
public class QuoteLifecycleService {

    static final Duration VIEW_NOTIFICATION_BUCKET = Duration.ofHours(6);
    static final Duration EXPIRING_WINDOW = Duration.ofDays(2);
    static final int COMMENT_MAX = 500;

    private final QuoteRepositoryPort quotes;
    private final QuoteHistoryCustomRepository history;
    private final QuoteQueryCustomRepository queries;
    private final OrderScheduling scheduling;
    private final ActorProvider actors;
    private final UserDirectory users;
    private final AuditRecorder audit;
    private final DomainEventPublisher events;
    private final PricingSnapshotResolver pricing;
    private final TransactionTemplate perQuote;
    private final Clock clock;

    public QuoteLifecycleService(QuoteRepositoryPort quotes, QuoteHistoryCustomRepository history, QuoteQueryCustomRepository queries,
                                 OrderScheduling scheduling, ActorProvider actors, UserDirectory users, AuditRecorder audit,
                                 DomainEventPublisher events, PricingSnapshotResolver pricing, PlatformTransactionManager transactions,
                                 Clock clock) {
        this.quotes = quotes;
        this.history = history;
        this.queries = queries;
        this.scheduling = scheduling;
        this.actors = actors;
        this.users = users;
        this.audit = audit;
        this.events = events;
        this.pricing = pricing;
        this.perQuote = new TransactionTemplate(transactions);
        this.perQuote.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    // ─── baker commands ────────────────────────────────────────────────────────────────

    /**
     * Records an out-of-band share: DRAFT/EXPIRED → SENT; for SENT/VIEWED only a history entry (once per channel and day).
     *
     * @throws ApiException RESOURCE_NOT_FOUND; INVALID_STATE_TRANSITION for a final quote
     */
    @Transactional
    public void share(UUID quoteId, ShareChannel channel) {
        Actor actor = actors.require();
        Quote quote = owned(actor.id(), quoteId);
        recordShare(quote, channel, actor);
    }

    /**
     * Emails the quote to its client ({@code quote.sent-to-client}, sent by the event handler) and records the share.
     *
     * @throws ApiException VALIDATION_ERROR on {@code clientEmail} when the quote has none
     */
    @Transactional
    public void sendByEmail(UUID quoteId) {
        Actor actor = actors.require();
        Quote quote = owned(actor.id(), quoteId);
        if (quote.getClientEmail() == null || quote.getClientEmail().isBlank()) {
            throw ApiException.invalid("clientEmail", "quote.clientEmail.required");
        }
        recordShare(quote, ShareChannel.EMAIL, actor);
    }

    /**
     * Accepts (baker) and optionally schedules the order in the same transaction.
     *
     * @throws ApiException INVALID_STATE_TRANSITION unless SENT/VIEWED; VALIDATION_ERROR on {@code schedule.*}
     */
    @Transactional
    public AcceptQuoteResponse accept(UUID quoteId, AcceptQuoteRequest request) {
        Actor actor = actors.require();
        Quote quote = owned(actor.id(), quoteId);
        require(quote, QuoteStatus.ACCEPTED);
        UUID eventId = request.schedule() == null ? null : scheduling.scheduleFromQuote(actor.id(), quote, request.schedule());
        quote.setCalendarEventId(eventId);
        String comment = clean(request.comment());
        Quote saved = move(quote, QuoteStatus.ACCEPTED, QuoteActorType.BAKER, actor.id(), name(actor.id()), null, comment);
        events.publish(new QuoteEvents.QuoteAccepted(saved.getId(), saved.getQuoteNumber(), saved.getUserId(), QuoteActorType.BAKER,
                name(actor.id()), saved.getTotal(), saved.getCurrency(), saved.getEventDate(), eventId, comment));
        return new AcceptQuoteResponse(saved.getId(), saved.getStatus().name(), eventId);
    }

    /**
     * Rejects (baker).
     *
     * @throws ApiException VALIDATION_ERROR on {@code comment} for OTHER without one; INVALID_STATE_TRANSITION
     */
    @Transactional
    public void reject(UUID quoteId, QuoteRejectReason reason, String comment) {
        Actor actor = actors.require();
        Quote quote = owned(actor.id(), quoteId);
        rejectAs(quote, reason, clean(comment), QuoteActorType.BAKER, actor.id(), name(actor.id()));
    }

    /**
     * Revokes the public link: SENT/VIEWED → REVOKED.
     *
     * @throws ApiException INVALID_STATE_TRANSITION
     */
    @Transactional
    public void revoke(UUID quoteId) {
        Actor actor = actors.require();
        Quote quote = owned(actor.id(), quoteId);
        require(quote, QuoteStatus.REVOKED);
        quote.setRevoked(true);
        Quote saved = move(quote, QuoteStatus.REVOKED, QuoteActorType.BAKER, actor.id(), name(actor.id()), null, null);
        events.publish(new QuoteEvents.QuoteRevoked(saved.getId(), saved.getQuoteNumber(), saved.getUserId()));
    }

    /**
     * Copies client, items (recipe configuration included) and fees into a new DRAFT with a new number; the event date
     * is kept only when it is still in the future.
     *
     * @return the new quote
     */
    @Transactional
    public Quote duplicate(UUID quoteId) {
        Actor actor = actors.require();
        Quote source = owned(actor.id(), quoteId);
        LocalDateTime now = TenantTime.nowLocal(clock);
        long validDays = Math.max(1, ChronoUnit.DAYS.between(source.getCreatedAt().toLocalDate(), source.getValidUntil().toLocalDate()));
        Quote copy = Quote.builder()
                .quoteNumber(QuoteNumbers.next(actor.id(), now))
                .userId(actor.id()).clientName(source.getClientName()).clientEmail(source.getClientEmail())
                .clientPhone(source.getClientPhone()).clientAddress(source.getClientAddress()).notes(source.getNotes())
                .status(QuoteStatus.DRAFT).validUntil(now.plusDays(validDays))
                .subtotal(source.getSubtotal()).taxRate(source.getTaxRate()).taxAmount(source.getTaxAmount()).total(source.getTotal())
                .currency(source.getCurrency()).currencyDecimalPlaces(source.getCurrencyDecimalPlaces()).taxRateId(source.getTaxRateId())
                .taxFactorType(source.getTaxFactorType()).pricesIncludeTax(source.getPricesIncludeTax()).roundingMode(source.getRoundingMode())
                .publicToken(QuoteNumbers.publicToken()).deliveryFee(source.getDeliveryFee()).extraFee(source.getExtraFee())
                .extraFeeDescription(source.getExtraFeeDescription()).priceReviewRequired(source.isPriceReviewRequired())
                .eventDate(source.getEventDate() != null && source.getEventDate().isAfter(TenantTime.today(clock)) ? source.getEventDate() : null)
                .items(new ArrayList<>())
                .build();
        source.getItems().stream().map(QuoteLifecycleService::copyItem).forEach(copy::addItem);
        Quote saved = quotes.save(copy);
        history.append(saved.getId(), QuoteStatus.DRAFT, QuoteActorType.BAKER, actor.id(), name(actor.id()),
                "DUPLICATED_FROM:" + source.getQuoteNumber(), null, TenantTime.now(clock), traceId());
        events.publish(new QuoteEvents.QuoteDuplicated(saved.getId(), saved.getQuoteNumber(), saved.getUserId(), source.getId()));
        return saved;
    }

    /**
     * Soft-deletes a DRAFT.
     *
     * @throws ApiException INVALID_STATE_TRANSITION for any other status
     */
    @Transactional
    public void delete(UUID quoteId) {
        Actor actor = actors.require();
        Quote quote = owned(actor.id(), quoteId);
        if (quote.getStatus() != QuoteStatus.DRAFT) {
            throw transitionError(quote.getStatus(), "DELETED");
        }
        quote.setDeletedAt(TenantTime.now(clock));
        Quote saved = quotes.save(quote);
        events.publish(new QuoteEvents.QuoteDeleted(saved.getId(), saved.getQuoteNumber(), saved.getUserId()));
    }

    /** Figures of the caller's quotes (§4.5). */
    @Transactional(readOnly = true)
    public QuoteStats stats() {
        UUID owner = actors.require().id();
        String currency = pricing.forNewDocument(new PricingSnapshotResolver.PricingInput(null, null, null)).currencyCode();
        Map<QuoteStatus, Long> byStatus = queries.statusCounts(owner).stream()
                .collect(Collectors.groupingBy(QuoteQueryCustomRepository.StatusCount::status, () -> new EnumMap<>(QuoteStatus.class),
                        Collectors.summingLong(QuoteQueryCustomRepository.StatusCount::count)));
        EnumSet.allOf(QuoteStatus.class).forEach(status -> byStatus.putIfAbsent(status, 0L));
        BigDecimal openValue = queries.statusCounts(owner).stream()
                .filter(count -> count.status().open() && currency.equalsIgnoreCase(count.currency()))
                .map(QuoteQueryCustomRepository.StatusCount::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Instant now = TenantTime.now(clock);
        BigDecimal accepted30 = queries.acceptedSince(owner, now.minus(Duration.ofDays(30))).entrySet().stream()
                .filter(entry -> currency.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        QuoteQueryCustomRepository.Decisions decisions = queries.decisionsSince(owner, now.minus(Duration.ofDays(90)),
                TenantTime.toLocal(now.minus(Duration.ofDays(90))));
        long decided = decisions.accepted() + decisions.rejected() + decisions.expired();
        Double conversion = decided == 0 ? null
                : BigDecimal.valueOf(decisions.accepted()).divide(BigDecimal.valueOf(decided), 4, RoundingMode.HALF_UP).doubleValue();
        LocalDateTime nowLocal = TenantTime.toLocal(now);
        long expiringSoon = queries.expiringBefore(owner, nowLocal, nowLocal.plusDays(3));
        return new QuoteStats(Map.copyOf(byStatus), openValue, accepted30, conversion, expiringSoon, currency);
    }

    // ─── public link ───────────────────────────────────────────────────────────────────

    /** First open of a SENT quote → VIEWED (notification coalesced per 6 h); later opens only count. */
    public void markViewed(Quote quote) {
        Instant now = TenantTime.now(clock);
        if (quote.getStatus() == QuoteStatus.SENT) {
            quote.setFirstViewedAt(now);
            move(quote, QuoteStatus.VIEWED, QuoteActorType.CLIENT, null, quote.getClientName(), null, null);
        }
        long bucket = now.getEpochSecond() / VIEW_NOTIFICATION_BUCKET.toSeconds();
        events.publish(new QuoteEvents.QuoteViewed(quote.getId(), quote.getQuoteNumber(), quote.getUserId(), quote.getClientName()),
                PublishOptions.dedupe("quote-viewed:" + quote.getId() + ":" + bucket));
    }

    /**
     * The client accepts from the public link; no order is scheduled (the baker does it).
     *
     * @throws ApiException INVALID_STATE_TRANSITION when the quote cannot be answered
     */
    @Transactional
    public Quote acceptAsClient(Quote quote, PublicAcceptRequest request) {
        requireRespondable(quote);
        require(quote, QuoteStatus.ACCEPTED);
        String clientName = request.clientName().trim();
        String comment = clean(request.comment());
        Quote saved = move(quote, QuoteStatus.ACCEPTED, QuoteActorType.CLIENT, null, clientName, null, comment);
        events.publish(new QuoteEvents.QuoteAccepted(saved.getId(), saved.getQuoteNumber(), saved.getUserId(), QuoteActorType.CLIENT,
                clientName, saved.getTotal(), saved.getCurrency(), saved.getEventDate(), null, comment));
        return saved;
    }

    /**
     * The client rejects from the public link.
     *
     * @throws ApiException VALIDATION_ERROR for NO_RESPONSE or OTHER without a comment; INVALID_STATE_TRANSITION
     */
    @Transactional
    public Quote rejectAsClient(Quote quote, PublicRejectRequest request) {
        requireRespondable(quote);
        if (request.reason() == QuoteRejectReason.NO_RESPONSE) {
            throw ApiException.invalid("reason", "quote.reject.clientReason");
        }
        return rejectAs(quote, request.reason(), clean(request.comment()), QuoteActorType.CLIENT, null, quote.getClientName());
    }

    /** True when the client may still answer: SENT/VIEWED, within validity, link not revoked. */
    public boolean canRespond(Quote quote) {
        return quote.getStatus().open() && !quote.isRevoked() && !expired(quote);
    }

    /** True once the validity ended. */
    public boolean expired(Quote quote) {
        return quote.getValidUntil() != null && quote.getValidUntil().isBefore(TenantTime.nowLocal(clock));
    }

    // ─── jobs ──────────────────────────────────────────────────────────────────────────

    /** Open quotes past {@code valid_until} → EXPIRED (actor SYSTEM), one transaction each. Returns how many. */
    public int expireOverdue(int limit) {
        int expired = 0;
        for (UUID id : queries.overdueOpen(TenantTime.nowLocal(clock), limit)) {
            try {
                Boolean done = perQuote.execute(status -> quotes.findById(id)
                        .filter(quote -> quote.getStatus().open() && quote.getDeletedAt() == null)
                        .map(quote -> {
                            Quote saved = move(quote, QuoteStatus.EXPIRED, QuoteActorType.SYSTEM, null, null, null, null);
                            events.publish(new QuoteEvents.QuoteExpired(saved.getId(), saved.getQuoteNumber(), saved.getUserId()));
                            return true;
                        }).orElse(false));
                expired += Boolean.TRUE.equals(done) ? 1 : 0;
            } catch (RuntimeException e) {
                log.warn("Could not expire quote {}: {}", id, e.getMessage());
            }
        }
        return expired;
    }

    /** Publishes {@code quote.expiring} once per open quote that ends within two days. Returns how many were new. */
    public int announceExpiring() {
        LocalDateTime now = TenantTime.nowLocal(clock);
        int published = 0;
        for (UUID id : queries.expiringOpen(now, now.plus(EXPIRING_WINDOW))) {
            Boolean fresh = perQuote.execute(status -> quotes.findById(id)
                    .map(quote -> events.publish(new QuoteEvents.QuoteExpiring(quote.getId(), quote.getQuoteNumber(), quote.getUserId(),
                            quote.getValidUntil().toLocalDate()), PublishOptions.dedupe("quote-expiring:" + quote.getId())))
                    .orElse(false));
            published += Boolean.TRUE.equals(fresh) ? 1 : 0;
        }
        return published;
    }

    // ─── internals ─────────────────────────────────────────────────────────────────────

    /** Moves {@code quote} to {@code to}, stamps the decision time, saves and appends the history entry. */
    Quote move(Quote quote, QuoteStatus to, QuoteActorType actorType, UUID actorId, String actorName, String detail, String comment) {
        QuoteStatus from = quote.getStatus();
        require(quote, to);
        Instant now = TenantTime.now(clock);
        quote.setStatus(to);
        if (to == QuoteStatus.ACCEPTED) {
            quote.setAcceptedAt(now);
        } else if (to == QuoteStatus.REJECTED) {
            quote.setRejectedAt(now);
        }
        Quote saved = quotes.save(quote);
        history.append(saved.getId(), to, actorType, actorId, actorName, detail, comment, now, traceId());
        audit.record(AuditEvent.of(AuditAction.QUOTE_STATUS_CHANGED, AuditTargets.QUOTE, saved.getId(), saved.getQuoteNumber())
                .params(Map.of("from", from.name(), "to", to.name(), "actor", actorType.name())).build());
        return saved;
    }

    private void recordShare(Quote quote, ShareChannel channel, Actor actor) {
        QuoteStatus status = quote.getStatus();
        if (status == QuoteStatus.DRAFT || status == QuoteStatus.EXPIRED) {
            if (status == QuoteStatus.EXPIRED && expired(quote)) {
                throw ApiException.of(ApiErrorCode.INVALID_STATE_TRANSITION, "validUntil", "quote.share.stillExpired");
            }
            quote.setRevoked(false);
            Quote saved = move(quote, QuoteStatus.SENT, QuoteActorType.BAKER, actor.id(), name(actor.id()), channel.name(), null);
            events.publish(new QuoteEvents.QuoteSent(saved.getId(), saved.getQuoteNumber(), saved.getUserId(), channel));
        } else if (status.open()) {
            if (channel == ShareChannel.EMAIL
                    || !history.sharedOn(quote.getId(), channel.name(), TenantTime.today(clock), TenantTime.ZONE)) {
                history.append(quote.getId(), status, QuoteActorType.BAKER, actor.id(), name(actor.id()), channel.name(), null,
                        TenantTime.now(clock), traceId());
            }
            events.publish(new QuoteEvents.QuoteSent(quote.getId(), quote.getQuoteNumber(), quote.getUserId(), channel));
        } else {
            throw transitionError(status, QuoteStatus.SENT.name());
        }
    }

    private Quote rejectAs(Quote quote, QuoteRejectReason reason, String comment, QuoteActorType actorType, UUID actorId, String actorName) {
        if (reason == null) {
            throw ApiException.invalid("reason", "api.validation.required");
        }
        if (reason == QuoteRejectReason.OTHER && comment == null) {
            throw ApiException.invalid("comment", "quote.reject.commentRequired");
        }
        require(quote, QuoteStatus.REJECTED);
        quote.setRejectReason(reason);
        Quote saved = move(quote, QuoteStatus.REJECTED, actorType, actorId, actorName, reason.name(), comment);
        events.publish(new QuoteEvents.QuoteRejected(saved.getId(), saved.getQuoteNumber(), saved.getUserId(), actorType, actorName,
                reason, comment));
        return saved;
    }

    private void requireRespondable(Quote quote) {
        if (!canRespond(quote)) {
            throw transitionError(quote.getStatus(), "RESPONDED");
        }
    }

    private static void require(Quote quote, QuoteStatus to) {
        try {
            QuoteTransitions.require(quote.getStatus(), to);
        } catch (QuoteTransitions.InvalidQuoteTransitionException e) {
            throw transitionError(quote.getStatus(), to.name());
        }
    }

    static ApiException transitionError(QuoteStatus from, String to) {
        return ApiException.withDetails(ApiErrorCode.INVALID_STATE_TRANSITION, "status", Map.of("from", from.name(), "to", to),
                "quote.invalidTransition", from.name(), to);
    }

    private Quote owned(UUID ownerId, UUID quoteId) {
        return quotes.findByIdAndUserId(quoteId, ownerId)
                .filter(quote -> quote.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("quote.notFound"));
    }

    private String name(UUID userId) {
        return users.ref(userId).map(UserRef::displayName).orElse(null);
    }

    private static String clean(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.length() > COMMENT_MAX ? trimmed.substring(0, COMMENT_MAX) : trimmed;
    }

    private static String traceId() {
        return MDC.get(TraceContexts.MDC_TRACE_ID);
    }

    private static QuoteItem copyItem(QuoteItem item) {
        return QuoteItem.builder().recipeId(item.getRecipeId()).productName(item.getProductName())
                .productDescription(item.getProductDescription()).productSize(item.getProductSize()).imageFilePath(item.getImageFilePath())
                .quantity(item.getQuantity()).scaleFactor(item.getScaleFactor()).unitCost(item.getUnitCost())
                .profitPercentage(item.getProfitPercentage()).unitPrice(item.getUnitPrice()).subtotal(item.getSubtotal())
                .notes(item.getNotes()).displayOrder(item.getDisplayOrder()).recipeConfiguration(item.getRecipeConfiguration())
                .allergens(item.getAllergens()).recipeVersion(item.getRecipeVersion()).costCalculatedAt(item.getCostCalculatedAt())
                .priceReviewRequired(item.isPriceReviewRequired()).build();
    }

    /** The requested statuses; empty means every status. */
    public static Set<QuoteStatus> statuses(List<QuoteStatus> raw) {
        return raw == null || raw.isEmpty() ? Set.of() : EnumSet.copyOf(raw);
    }
}
