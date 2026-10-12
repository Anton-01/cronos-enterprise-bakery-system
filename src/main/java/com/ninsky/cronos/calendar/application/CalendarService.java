package com.ninsky.cronos.calendar.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.calendar.api.CalendarEventRecipeRequest;
import com.ninsky.cronos.calendar.api.CalendarEventRecipeResponse;
import com.ninsky.cronos.calendar.api.CalendarEventRequest;
import com.ninsky.cronos.calendar.api.CalendarEventResponse;
import com.ninsky.cronos.calendar.api.CalendarSummary;
import com.ninsky.cronos.calendar.api.RescheduleRequest;
import com.ninsky.cronos.calendar.api.StatusChangeRequest;
import com.ninsky.cronos.calendar.domain.CalendarEventStatus;
import com.ninsky.cronos.calendar.domain.CalendarEventType;
import com.ninsky.cronos.calendar.domain.CalendarEvents;
import com.ninsky.cronos.calendar.domain.ReminderPlan;
import com.ninsky.cronos.calendar.infrastructure.CalendarEventCustomRepository;
import com.ninsky.cronos.calendar.infrastructure.CalendarEventCustomRepository.EventRow;
import com.ninsky.cronos.calendar.infrastructure.CalendarEventCustomRepository.NewRecipe;
import com.ninsky.cronos.calendar.infrastructure.CalendarEventCustomRepository.RecipeRow;
import com.ninsky.cronos.domain.entity.enums.QuoteStatus;
import com.ninsky.cronos.domain.model.quote.Quote;
import com.ninsky.cronos.domain.model.quote.QuoteItem;
import com.ninsky.cronos.domain.port.quote.QuoteRepositoryPort;
import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.eventing.application.PublishOptions;
import com.ninsky.cronos.eventing.domain.TimeOrderedIds;
import com.ninsky.cronos.finance.settings.PricingSnapshotResolver;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.kitchen.recipe.RecipeCard;
import com.ninsky.cronos.kitchen.recipe.RecipeConfiguration;
import com.ninsky.cronos.kitchen.recipe.RecipeQueryCustomRepository;
import com.ninsky.cronos.kitchen.recipe.RecipeViews;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
import com.ninsky.cronos.quote.api.ScheduleOrderRequest;
import com.ninsky.cronos.quote.application.OrderScheduling;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Orders calendar (§5): owner-scoped entries with recipes, a status flow, reminders and the {@code .ics} export. Every
 * change publishes a {@code calendar.*} event in the same transaction; reminders, emails, quote links and live hints
 * are projections of those events, so a request only writes the entry.
 */
@Service
public class CalendarService implements OrderScheduling {

    static final Duration MAX_RANGE = Duration.ofDays(62);
    static final int MAX_ROWS = 500;
    static final Duration MAX_DURATION = Duration.ofDays(14);
    static final Duration SCHEDULE_PAST_TOLERANCE = Duration.ofDays(1);
    static final int UPCOMING_MAX = 20;
    static final int SEARCH_MIN = 2;
    static final BigDecimal QUANTITY_MAX = BigDecimal.valueOf(10_000);
    static final int TITLE_MAX = 150;
    static final int NOTES_MAX = 2000;
    static final int PHONE_MAX = 20;

    private final CalendarEventCustomRepository events;
    private final RecipeQueryCustomRepository recipeQueries;
    private final RecipeViews recipeViews;
    private final QuoteRepositoryPort quotes;
    private final ActorProvider actors;
    private final DomainEventPublisher publisher;
    private final PricingSnapshotResolver pricing;
    private final KitchenMessages messages;
    private final ObjectMapper json;
    private final Clock clock;

    public CalendarService(CalendarEventCustomRepository events, RecipeQueryCustomRepository recipeQueries, RecipeViews recipeViews,
                           QuoteRepositoryPort quotes, ActorProvider actors, DomainEventPublisher publisher, PricingSnapshotResolver pricing,
                           KitchenMessages messages, ObjectMapper json, Clock clock) {
        this.events = events;
        this.recipeQueries = recipeQueries;
        this.recipeViews = recipeViews;
        this.quotes = quotes;
        this.actors = actors;
        this.publisher = publisher;
        this.pricing = pricing;
        this.messages = messages;
        this.json = json;
        this.clock = clock;
    }

    // ─── queries ───────────────────────────────────────────────────────────────────────

    /**
     * Entries overlapping {@code [from, to)}; {@code search} matches title, client, location or quote number (accent-
     * and case-insensitive, after decryption).
     *
     * @throws ApiException VALIDATION_ERROR on {@code to} for a range over 62 days or over 500 entries
     */
    @Transactional(readOnly = true)
    public List<CalendarEventResponse> list(Instant from, Instant to, Set<CalendarEventType> types, Set<CalendarEventStatus> statuses,
                                            String search) {
        UUID owner = owner();
        requireRange(from, to);
        List<EventRow> rows = events.range(owner, from, to, orEmpty(types), orEmpty(statuses), MAX_ROWS + 1);
        if (rows.size() > MAX_ROWS) {
            throw ApiException.invalid("to", "calendar.range.tooMany", MAX_ROWS);
        }
        String term = fold(search);
        List<EventRow> matching = term == null || term.length() < SEARCH_MIN ? rows
                : rows.stream().filter(row -> Stream.of(row.title(), row.clientName(), row.location(), row.quoteNumber())
                        .map(CalendarService::fold).filter(Objects::nonNull).anyMatch(value -> value.contains(term))).toList();
        return views(owner, matching);
    }

    /**
     * The next active entries that have not ended.
     *
     * @throws ApiException VALIDATION_ERROR on {@code limit} outside 1–20
     */
    @Transactional(readOnly = true)
    public List<CalendarEventResponse> upcoming(int limit) {
        if (limit < 1 || limit > UPCOMING_MAX) {
            throw ApiException.invalid("limit", "api.validation.range", 1, UPCOMING_MAX);
        }
        UUID owner = owner();
        return views(owner, events.upcoming(owner, TenantTime.now(clock), limit));
    }

    /** @throws ApiException RESOURCE_NOT_FOUND */
    @Transactional(readOnly = true)
    public CalendarEventResponse get(UUID id) {
        UUID owner = owner();
        return view(owner, owned(owner, id, false));
    }

    /** Figures of {@code [from, to)} plus today's and the current production state (§5.5). */
    @Transactional(readOnly = true)
    public CalendarSummary summary(Instant from, Instant to) {
        UUID owner = owner();
        requireRange(from, to);
        String currency = defaultCurrency();
        LocalDate today = TenantTime.today(clock);
        CalendarEventCustomRepository.SummaryRow row = events.summary(owner, from, to,
                today.atStartOfDay(TenantTime.ZONE).toInstant(), today.plusDays(1).atStartOfDay(TenantTime.ZONE).toInstant(), currency);
        return new CalendarSummary(row.ordersInRange(), row.dueToday(), row.inProduction(), row.readyForDelivery(),
                row.revenueInRange().setScale(2, RoundingMode.HALF_UP), currency);
    }

    /** RFC 5545 document of the entries overlapping {@code [from, to)} (§5.7). */
    @Transactional(readOnly = true)
    public String export(Instant from, Instant to) {
        List<CalendarEventResponse> entries = list(from, to, Set.of(), Set.of(), null);
        return CalendarIcs.write(entries, TenantTime.now(clock), TenantTime.ZONE, new CalendarIcs.Labels(messages.get("calendar.ics.client"),
                messages.get("calendar.ics.phone"), messages.get("calendar.ics.recipes"), messages.get("calendar.ics.quote")));
    }

    // ─── commands ──────────────────────────────────────────────────────────────────────

    /**
     * Creates an entry (TENTATIVE unless CONFIRMED is asked for).
     *
     * @throws ApiException VALIDATION_ERROR; RESOURCE_NOT_FOUND for a recipe or quote the caller cannot see
     */
    @Transactional
    public CalendarEventResponse create(CalendarEventRequest request) {
        UUID owner = owner();
        CalendarEventStatus status = request.status() == null ? CalendarEventStatus.TENTATIVE : request.status();
        Violations violations = new Violations();
        validate(request, violations);
        violations.invalidIf(!status.initial(), "status", "calendar.status.initial");
        violations.throwIfAny();
        requireVisibleRecipes(owner, request.recipes());
        Quote quote = request.quoteId() == null ? null : acceptedQuote(owner, request.quoteId());

        Instant now = TenantTime.now(clock);
        EventRow row = new EventRow(TimeOrderedIds.next(clock), owner, request.title().strip(), request.type(), status, request.startAt(),
                request.endAt(), request.allDay(), upper(request.color()), blankToNull(request.location()), blankToNull(request.clientName()),
                blankToNull(request.clientPhone()), blankToNull(request.clientEmail()), blankToNull(request.notes()),
                quote == null ? null : quote.getId(), quote == null ? null : quote.getQuoteNumber(), request.totalAmount(),
                upper(request.currency()), List.copyOf(Set.copyOf(request.reminders())).stream().sorted().toList(), request.productionStartAt(),
                now, null, 0);
        events.insert(row);
        events.replaceRecipes(row.id(), newRecipes(request.recipes()), () -> TimeOrderedIds.next(clock));
        publishCreated(row);
        return view(owner, owned(owner, row.id(), false));
    }

    /**
     * Edits an entry; status and quote link are not changed here.
     *
     * @throws ApiException VALIDATION_ERROR ({@code version} required); CONCURRENT_MODIFICATION on a stale version
     */
    @Transactional
    public CalendarEventResponse update(UUID id, CalendarEventRequest request) {
        UUID owner = owner();
        Violations violations = new Violations();
        validate(request, violations);
        violations.invalidIf(request.version() == null, "version", "api.validation.required");
        violations.throwIfAny();
        EventRow current = current(owner, id, request.version());
        requireVisibleRecipes(owner, request.recipes());

        EventRow next = new EventRow(current.id(), owner, request.title().strip(), request.type(), current.status(), request.startAt(),
                request.endAt(), request.allDay(), upper(request.color()), blankToNull(request.location()), blankToNull(request.clientName()),
                blankToNull(request.clientPhone()), blankToNull(request.clientEmail()), blankToNull(request.notes()), current.quoteId(),
                current.quoteNumber(), request.totalAmount(), upper(request.currency()),
                List.copyOf(Set.copyOf(request.reminders())).stream().sorted().toList(), request.productionStartAt(), current.createdAt(),
                current.updatedAt(), current.version());
        List<NewRecipe> recipes = newRecipes(request.recipes());
        Map<String, CalendarEvents.Change> changes = changes(current, next);
        if (!sameRecipes(events.recipes(List.of(id)).getOrDefault(id, List.of()), recipes)) {
            changes.put("recipes", new CalendarEvents.Change(null, null));
        }
        events.update(next, TenantTime.now(clock));
        events.replaceRecipes(id, recipes, () -> TimeOrderedIds.next(clock));
        if (!changes.isEmpty()) {
            publisher.publish(new CalendarEvents.CalendarEventUpdated(id, owner, changes));
        }
        if (!current.startAt().equals(next.startAt()) || !current.endAt().equals(next.endAt())) {
            publisher.publish(new CalendarEvents.CalendarEventRescheduled(id, owner, current.startAt(), current.endAt(), next.startAt(),
                    next.endAt()));
        }
        return view(owner, owned(owner, id, false));
    }

    /**
     * Moves an entry in time (drag-to-reschedule); the production start moves by the same amount.
     *
     * @throws ApiException VALIDATION_ERROR; CONCURRENT_MODIFICATION
     */
    @Transactional
    public CalendarEventResponse reschedule(UUID id, RescheduleRequest request) {
        UUID owner = owner();
        EventRow current = current(owner, id, request.version());
        Violations violations = new Violations();
        validateTimes(request.startAt(), request.endAt(), current.allDay(), "", violations);
        violations.throwIfAny();
        Instant production = current.productionStartAt() == null ? null
                : current.productionStartAt().plus(Duration.between(current.startAt(), request.startAt()));
        EventRow next = withSchedule(current, request.startAt(), request.endAt(), production);
        events.update(next, TenantTime.now(clock));
        publisher.publish(new CalendarEvents.CalendarEventRescheduled(id, owner, current.startAt(), current.endAt(), next.startAt(),
                next.endAt()));
        return view(owner, owned(owner, id, false));
    }

    /**
     * Moves an entry through the status flow (§5.2).
     *
     * @throws ApiException INVALID_STATE_TRANSITION; CONCURRENT_MODIFICATION
     */
    @Transactional
    public CalendarEventResponse changeStatus(UUID id, StatusChangeRequest request) {
        UUID owner = owner();
        EventRow current = current(owner, id, request.version());
        if (!current.status().canMoveTo(request.status())) {
            throw ApiException.withDetails(ApiErrorCode.INVALID_STATE_TRANSITION, "status",
                    Map.of("from", current.status().name(), "to", request.status().name(), "allowed",
                            current.status().next().stream().map(Enum::name).sorted().toList()),
                    "calendar.invalidTransition", current.status().name(), request.status().name());
        }
        EventRow next = withStatus(current, request.status());
        events.update(next, TenantTime.now(clock));
        String reason = blankToNull(request.reason());
        publisher.publish(new CalendarEvents.CalendarEventStatusChanged(id, owner, current.status(), next.status(), reason));
        if (next.status() == CalendarEventStatus.CANCELLED) {
            publisher.publish(new CalendarEvents.CalendarEventCancelled(id, owner, current.quoteId(), reason));
        }
        if (next.status() == CalendarEventStatus.CONFIRMED) {
            announceOrder(next);
        }
        return view(owner, owned(owner, id, false));
    }

    /** Soft-deletes an entry; a linked quote is unlinked by the event's consumer. */
    @Transactional
    public void delete(UUID id) {
        UUID owner = owner();
        EventRow current = owned(owner, id, true);
        events.softDelete(id, TenantTime.now(clock));
        publisher.publish(new CalendarEvents.CalendarEventDeleted(id, owner, current.quoteId()));
    }

    /**
     * Creates the CONFIRMED order of an accepted quote (§4.4) inside the caller's transaction: one recipe line per quote
     * item with a visible recipe (quantity = configured yield, else the item quantity); other items go to the notes.
     */
    @Override
    public UUID scheduleFromQuote(UUID ownerId, Quote quote, ScheduleOrderRequest schedule) {
        Instant now = TenantTime.now(clock);
        Violations violations = new Violations();
        validateTimes(schedule.startAt(), schedule.endAt(), schedule.allDay(), "schedule.", violations);
        violations.invalidIf(schedule.startAt().isBefore(now.minus(SCHEDULE_PAST_TOLERANCE)), "schedule.startAt", "calendar.start.tooOld");
        violations.invalidIf(schedule.productionStartAt() != null && schedule.productionStartAt().isAfter(schedule.startAt()),
                "schedule.productionStartAt", "calendar.production.afterStart");
        violations.invalidIf(!ReminderPlan.valid(schedule.reminders()), "schedule.reminders", "calendar.reminders.invalid",
                ReminderPlan.OFFSETS.stream().sorted().map(String::valueOf).collect(Collectors.joining(", ")), ReminderPlan.MAX_REMINDERS);
        violations.throwIfAny();

        List<QuoteItem> items = quote.getItems() == null ? List.of() : quote.getItems();
        Set<UUID> visible = recipeQueries.visibleIds(ownerId, items.stream().map(QuoteItem::getRecipeId).filter(Objects::nonNull).toList());
        List<NewRecipe> recipes = new ArrayList<>();
        List<String> loose = new ArrayList<>();
        for (QuoteItem item : items) {
            BigDecimal quantity = quantityOf(item);
            if (item.getRecipeId() != null && visible.contains(item.getRecipeId()) && recipes.size() < 20 && quantity != null) {
                recipes.add(new NewRecipe(item.getRecipeId(), quantity, item.getRecipeConfiguration(), cut(item.getNotes(), 300)));
            } else {
                loose.add("• " + plain(item.getQuantity()) + " × " + item.getProductName());
            }
        }
        String notes = Stream.concat(Stream.ofNullable(blankToNull(schedule.notes())), loose.isEmpty() ? Stream.empty()
                : Stream.of(String.join("\n", loose))).collect(Collectors.joining("\n\n"));

        EventRow row = new EventRow(TimeOrderedIds.next(clock), ownerId,
                cut(quote.getQuoteNumber() + " · " + quote.getClientName(), TITLE_MAX), CalendarEventType.valueOf(schedule.type()),
                CalendarEventStatus.CONFIRMED, schedule.startAt(), schedule.endAt(), schedule.allDay(), null,
                blankToNull(schedule.location()), blankToNull(quote.getClientName()), cut(blankToNull(quote.getClientPhone()), PHONE_MAX),
                blankToNull(quote.getClientEmail()), cut(blankToNull(notes), NOTES_MAX), quote.getId(), quote.getQuoteNumber(), quote.getTotal(),
                upper(quote.getCurrency()), schedule.reminders().stream().distinct().sorted().toList(), schedule.productionStartAt(), now,
                null, 0);
        events.insert(row);
        events.replaceRecipes(row.id(), recipes, () -> TimeOrderedIds.next(clock));
        publishCreated(row);
        return row.id();
    }

    // ─── validation ────────────────────────────────────────────────────────────────────

    private void validate(CalendarEventRequest request, Violations violations) {
        validateTimes(request.startAt(), request.endAt(), request.allDay(), "", violations);
        violations.invalidIf(request.productionStartAt() != null && request.startAt() != null
                && request.productionStartAt().isAfter(request.startAt()), "productionStartAt", "calendar.production.afterStart");
        violations.invalidIf(!ReminderPlan.valid(request.reminders()), "reminders", "calendar.reminders.invalid",
                ReminderPlan.OFFSETS.stream().sorted().map(String::valueOf).collect(Collectors.joining(", ")), ReminderPlan.MAX_REMINDERS);
        violations.invalidIf(request.totalAmount() != null && request.currency() == null, "currency", "api.validation.required");
        for (int index = 0; index < request.recipes().size(); index++) {
            CalendarEventRecipeRequest recipe = request.recipes().get(index);
            violations.invalidIf(recipe.quantity() != null && recipe.quantity().compareTo(QUANTITY_MAX) > 0,
                    "recipes[" + index + "].quantity", "api.validation.range", "0.01", QUANTITY_MAX);
        }
    }

    /** {@code endAt > startAt}, at most 14 days; all-day entries start and end at midnight (tenant zone). */
    private static void validateTimes(Instant startAt, Instant endAt, boolean allDay, String prefix, Violations violations) {
        if (startAt == null || endAt == null) {
            return;
        }
        if (!endAt.isAfter(startAt)) {
            violations.invalid(prefix + "endAt", "calendar.endBeforeStart");
        } else if (Duration.between(startAt, endAt).compareTo(MAX_DURATION) > 0) {
            violations.invalid(prefix + "endAt", "calendar.duration.tooLong", MAX_DURATION.toDays());
        }
        if (allDay && (!midnight(startAt) || !midnight(endAt))) {
            violations.invalid(prefix + "startAt", "calendar.allDay.midnight");
        }
    }

    private static boolean midnight(Instant instant) {
        ZonedDateTime local = instant.atZone(TenantTime.ZONE);
        return local.toLocalTime().equals(LocalTime.MIDNIGHT);
    }

    private static void requireRange(Instant from, Instant to) {
        if (from == null || to == null) {
            throw ApiException.invalid(from == null ? "from" : "to", "api.validation.required");
        }
        if (!to.isAfter(from)) {
            throw ApiException.invalid("to", "calendar.endBeforeStart");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw ApiException.invalid("to", "calendar.range.tooLong", MAX_RANGE.toDays());
        }
    }

    private void requireVisibleRecipes(UUID owner, List<CalendarEventRecipeRequest> recipes) {
        Set<UUID> requested = recipes.stream().map(CalendarEventRecipeRequest::recipeId).collect(Collectors.toSet());
        if (!recipeQueries.visibleIds(owner, requested).containsAll(requested)) {
            throw ApiException.notFound("calendar.recipe.notFound");
        }
    }

    private Quote acceptedQuote(UUID owner, UUID quoteId) {
        Quote quote = quotes.findByIdAndUserId(quoteId, owner).filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("calendar.quote.notFound"));
        if (quote.getStatus() != QuoteStatus.ACCEPTED) {
            throw ApiException.invalid("quoteId", "calendar.quote.notAccepted", quote.getStatus().name());
        }
        return quote;
    }

    // ─── internals ─────────────────────────────────────────────────────────────────────

    private void publishCreated(EventRow row) {
        publisher.publish(new CalendarEvents.CalendarEventCreated(row.id(), row.ownerId(), row.type(), row.status(), row.startAt(),
                row.endAt(), row.quoteId()));
        if (row.status() == CalendarEventStatus.CONFIRMED) {
            announceOrder(row);
        }
    }

    /** Once per entry: a confirmed delivery or pickup tells the client (when there is an email). */
    private void announceOrder(EventRow row) {
        if (row.type().order()) {
            publisher.publish(new CalendarEvents.OrderScheduled(row.id(), row.ownerId(), row.quoteId(), row.quoteNumber(), row.type(),
                    row.startAt(), row.allDay()), PublishOptions.dedupe("order-scheduled:" + row.id()));
        }
    }

    private EventRow current(UUID owner, UUID id, Long version) {
        EventRow current = owned(owner, id, true);
        if (version == null || current.version() != version) {
            throw ApiException.concurrentModification();
        }
        return current;
    }

    private EventRow owned(UUID owner, UUID id, boolean lock) {
        return events.findOwned(owner, id, lock).orElseThrow(() -> ApiException.notFound("calendar.notFound"));
    }

    private UUID owner() {
        return actors.require().id();
    }

    private String defaultCurrency() {
        return pricing.forNewDocument(new PricingSnapshotResolver.PricingInput(null, null, null)).currencyCode().toUpperCase(Locale.ROOT);
    }

    private CalendarEventResponse view(UUID owner, EventRow row) {
        return views(owner, List.of(row)).getFirst();
    }

    /** Entries with their recipes, the recipe cards resolved in one batch. */
    private List<CalendarEventResponse> views(UUID owner, List<EventRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<RecipeRow>> recipes = events.recipes(rows.stream().map(EventRow::id).toList());
        Map<UUID, RecipeCard> cards = recipeViews.cards(recipes.values().stream().flatMap(List::stream).map(RecipeRow::recipeId)
                .collect(Collectors.toSet()), owner, KitchenMessages.language());
        return rows.stream().map(row -> new CalendarEventResponse(row.id(), row.title(), row.type(), row.status(), row.startAt(),
                row.endAt(), row.allDay(), row.color(), row.location(), row.clientName(), row.clientPhone(), row.clientEmail(), row.notes(),
                row.quoteId(), row.quoteNumber(), row.totalAmount(), row.currency(),
                recipes.getOrDefault(row.id(), List.of()).stream().map(recipe -> recipeView(recipe, cards.get(recipe.recipeId()))).toList(),
                row.reminders(), row.productionStartAt(), row.createdAt(), row.updatedAt(), row.version())).toList();
    }

    private CalendarEventRecipeResponse recipeView(RecipeRow recipe, RecipeCard card) {
        Optional<RecipeCard> found = Optional.ofNullable(card);
        return new CalendarEventRecipeResponse(recipe.id(), recipe.recipeId(), found.map(RecipeCard::name).orElse(null),
                found.map(RecipeCard::coverImageUrl).orElse(null), recipe.quantity(), found.map(RecipeCard::yieldUnit).orElse(null),
                configuration(recipe.configuration()), found.map(RecipeCard::allergens).orElse(List.of()), recipe.notes());
    }

    private List<NewRecipe> newRecipes(List<CalendarEventRecipeRequest> recipes) {
        return recipes.stream().map(recipe -> new NewRecipe(recipe.recipeId(), recipe.quantity().setScale(2, RoundingMode.HALF_UP),
                configurationJson(recipe.recipeConfiguration()), blankToNull(recipe.notes()))).toList();
    }

    private BigDecimal quantityOf(QuoteItem item) {
        BigDecimal configured = Optional.ofNullable(configuration(item.getRecipeConfiguration())).map(RecipeConfiguration::yieldQuantity)
                .orElse(null);
        BigDecimal quantity = configured != null ? configured : item.getQuantity();
        if (quantity == null || quantity.signum() <= 0 || quantity.compareTo(QUANTITY_MAX) > 0) {
            return null;
        }
        return quantity.setScale(2, RoundingMode.HALF_UP);
    }

    private RecipeConfiguration configuration(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return json.readValue(raw, RecipeConfiguration.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String configurationJson(RecipeConfiguration configuration) {
        if (configuration == null) {
            return null;
        }
        try {
            return json.writeValueAsString(configuration);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Recipe configuration cannot be serialised", e);
        }
    }

    /** Field → old/new value of what {@code PUT} changed (client values travel encrypted in the event). */
    static Map<String, CalendarEvents.Change> changes(EventRow before, EventRow after) {
        Map<String, Function<EventRow, Object>> fields = new LinkedHashMap<>();
        fields.put("title", EventRow::title);
        fields.put("type", EventRow::type);
        fields.put("startAt", EventRow::startAt);
        fields.put("endAt", EventRow::endAt);
        fields.put("allDay", EventRow::allDay);
        fields.put("color", EventRow::color);
        fields.put("location", EventRow::location);
        fields.put("clientName", EventRow::clientName);
        fields.put("clientPhone", EventRow::clientPhone);
        fields.put("clientEmail", EventRow::clientEmail);
        fields.put("notes", EventRow::notes);
        fields.put("totalAmount", row -> row.totalAmount() == null ? null : row.totalAmount().stripTrailingZeros());
        fields.put("currency", EventRow::currency);
        fields.put("reminders", EventRow::reminders);
        fields.put("productionStartAt", EventRow::productionStartAt);
        Map<String, CalendarEvents.Change> changes = new LinkedHashMap<>();
        fields.forEach((name, accessor) -> {
            Object from = accessor.apply(before);
            Object to = accessor.apply(after);
            if (!Objects.equals(from, to)) {
                changes.put(name, new CalendarEvents.Change(from == null ? null : from.toString(), to == null ? null : to.toString()));
            }
        });
        return changes;
    }

    private static boolean sameRecipes(List<RecipeRow> stored, List<NewRecipe> next) {
        if (stored.size() != next.size()) {
            return false;
        }
        for (int index = 0; index < stored.size(); index++) {
            RecipeRow a = stored.get(index);
            NewRecipe b = next.get(index);
            if (!a.recipeId().equals(b.recipeId()) || a.quantity().compareTo(b.quantity()) != 0 || !Objects.equals(a.notes(), b.notes())) {
                return false;
            }
        }
        return true;
    }

    private static EventRow withSchedule(EventRow row, Instant startAt, Instant endAt, Instant productionStartAt) {
        return new EventRow(row.id(), row.ownerId(), row.title(), row.type(), row.status(), startAt, endAt, row.allDay(), row.color(),
                row.location(), row.clientName(), row.clientPhone(), row.clientEmail(), row.notes(), row.quoteId(), row.quoteNumber(),
                row.totalAmount(), row.currency(), row.reminders(), productionStartAt, row.createdAt(), row.updatedAt(), row.version());
    }

    private static EventRow withStatus(EventRow row, CalendarEventStatus status) {
        return new EventRow(row.id(), row.ownerId(), row.title(), row.type(), status, row.startAt(), row.endAt(), row.allDay(), row.color(),
                row.location(), row.clientName(), row.clientPhone(), row.clientEmail(), row.notes(), row.quoteId(), row.quoteNumber(),
                row.totalAmount(), row.currency(), row.reminders(), row.productionStartAt(), row.createdAt(), row.updatedAt(), row.version());
    }

    private static <E extends Enum<E>> Set<E> orEmpty(Collection<E> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }

    /** Lower case without accents (ñ kept), for search. */
    static String fold(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String decomposed = Normalizer.normalize(value.strip().toLowerCase(Locale.ROOT).replace("ñ", ""), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").replace("", "ñ");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String upper(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "1" : value.stripTrailingZeros().toPlainString();
    }
}
