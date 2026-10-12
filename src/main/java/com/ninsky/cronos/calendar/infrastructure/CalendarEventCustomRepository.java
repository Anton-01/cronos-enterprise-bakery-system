package com.ninsky.cronos.calendar.infrastructure;

import com.ninsky.cronos.calendar.domain.CalendarEventStatus;
import com.ninsky.cronos.calendar.domain.CalendarEventType;
import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * SQL of {@code calendar_events} and {@code calendar_event_recipes} (§5). Client fields are encrypted here, on the way
 * in and out, with the column encryption key; every query is scoped to the owner except the consumers' {@link #find}.
 */
@Repository
public class CalendarEventCustomRepository {

    /** One entry, client fields in clear. {@code quoteNumber} is joined from the quote. */
    public record EventRow(UUID id, UUID ownerId, String title, CalendarEventType type, CalendarEventStatus status, Instant startAt,
                           Instant endAt, boolean allDay, String color, String location, String clientName, String clientPhone,
                           String clientEmail, String notes, UUID quoteId, String quoteNumber, BigDecimal totalAmount, String currency,
                           List<Integer> reminders, Instant productionStartAt, Instant createdAt, Instant updatedAt, long version) {
        public EventRow {
            reminders = reminders == null ? List.of() : List.copyOf(reminders);
        }
    }

    /** A recipe line of an entry. */
    public record RecipeRow(UUID id, UUID eventId, UUID recipeId, BigDecimal quantity, String configuration, String notes, int displayOrder) {
    }

    /** A recipe line to store. */
    public record NewRecipe(UUID recipeId, BigDecimal quantity, String configuration, String notes) {
    }

    /** Figures of {@code GET /calendar/summary}. */
    public record SummaryRow(long ordersInRange, long dueToday, long inProduction, long readyForDelivery, BigDecimal revenueInRange) {
    }

    private static final String SELECT = """
            SELECT e.*, q.quote_number FROM calendar_events e LEFT JOIN quotes q ON q.id = e.quote_id
            """;
    private static final String ORDER_TYPES = "('ORDER_DELIVERY', 'ORDER_PICKUP')";

    private final NamedParameterJdbcTemplate jdbc;
    private final FieldEncryptionService encryption;
    private final RowMapper<EventRow> rowMapper;

    public CalendarEventCustomRepository(NamedParameterJdbcTemplate jdbc, FieldEncryptionService encryption) {
        this.jdbc = jdbc;
        this.encryption = encryption;
        this.rowMapper = (rs, n) -> new EventRow(
                rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class), rs.getString("title"),
                CalendarEventType.valueOf(rs.getString("type")), CalendarEventStatus.valueOf(rs.getString("status")),
                instant(rs.getTimestamp("start_at")), instant(rs.getTimestamp("end_at")), rs.getBoolean("all_day"),
                rs.getString("color"), rs.getString("location"), encryption.decrypt(rs.getString("client_name")),
                encryption.decrypt(rs.getString("client_phone")), encryption.decrypt(rs.getString("client_email")),
                rs.getString("notes"), rs.getObject("quote_id", UUID.class), rs.getString("quote_number"),
                rs.getBigDecimal("total_amount"), rs.getString("currency"), ints(rs.getArray("reminders")),
                instant(rs.getTimestamp("production_start_at")), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")), rs.getLong("version"));
    }

    public void insert(EventRow row) {
        jdbc.update("""
                        INSERT INTO calendar_events (id, owner_id, title, type, status, start_at, end_at, all_day, color, location,
                                                     client_name, client_phone, client_email, notes, quote_id, total_amount, currency,
                                                     reminders, production_start_at, created_at, version)
                        VALUES (:id, :owner, :title, :type, :status, :startAt, :endAt, :allDay, :color, :location,
                                :clientName, :clientPhone, :clientEmail, :notes, :quoteId, :totalAmount, :currency,
                                CAST(:reminders AS SMALLINT[]), :productionStartAt, :createdAt, 0)""",
                params(row).addValue("owner", row.ownerId()).addValue("quoteId", row.quoteId())
                        .addValue("createdAt", Timestamp.from(row.createdAt())));
    }

    /** Writes the editable fields of {@code row} and bumps the version. */
    public void update(EventRow row, Instant now) {
        jdbc.update("""
                        UPDATE calendar_events SET title = :title, type = :type, status = :status, start_at = :startAt, end_at = :endAt,
                               all_day = :allDay, color = :color, location = :location, client_name = :clientName,
                               client_phone = :clientPhone, client_email = :clientEmail, notes = :notes, total_amount = :totalAmount,
                               currency = :currency, reminders = CAST(:reminders AS SMALLINT[]), production_start_at = :productionStartAt,
                               updated_at = :now, version = version + 1
                        WHERE id = :id AND deleted_at IS NULL""",
                params(row).addValue("now", Timestamp.from(now)));
    }

    public void softDelete(UUID id, Instant now) {
        jdbc.update("UPDATE calendar_events SET deleted_at = :now, updated_at = :now, version = version + 1 WHERE id = :id AND deleted_at IS NULL",
                new MapSqlParameterSource("id", id).addValue("now", Timestamp.from(now)));
    }

    /** The owner's live entry; {@code lock} takes the row lock for a write. */
    public Optional<EventRow> findOwned(UUID ownerId, UUID id, boolean lock) {
        return jdbc.query(SELECT + " WHERE e.id = :id AND e.owner_id = :owner AND e.deleted_at IS NULL" + (lock ? " FOR UPDATE OF e" : ""),
                new MapSqlParameterSource("id", id).addValue("owner", ownerId), rowMapper).stream().findFirst();
    }

    /** Any live entry (outbox consumers). */
    public Optional<EventRow> find(UUID id) {
        return jdbc.query(SELECT + " WHERE e.id = :id AND e.deleted_at IS NULL", Map.of("id", id), rowMapper).stream().findFirst();
    }

    /**
     * Entries overlapping {@code [from, to)} ordered all-day first, then by start and title; at most {@code limit} rows.
     *
     * @param types    empty for every type
     * @param statuses empty for every status
     */
    public List<EventRow> range(UUID ownerId, Instant from, Instant to, Set<CalendarEventType> types, Set<CalendarEventStatus> statuses,
                                int limit) {
        StringBuilder sql = new StringBuilder(SELECT)
                .append(" WHERE e.owner_id = :owner AND e.deleted_at IS NULL AND e.start_at < :to AND e.end_at > :from");
        MapSqlParameterSource params = new MapSqlParameterSource("owner", ownerId)
                .addValue("from", Timestamp.from(from)).addValue("to", Timestamp.from(to)).addValue("limit", limit);
        if (!types.isEmpty()) {
            sql.append(" AND e.type IN (:types)");
            params.addValue("types", names(types));
        }
        if (!statuses.isEmpty()) {
            sql.append(" AND e.status IN (:statuses)");
            params.addValue("statuses", names(statuses));
        }
        sql.append(" ORDER BY e.all_day DESC, e.start_at, e.title LIMIT :limit");
        return jdbc.query(sql.toString(), params, rowMapper);
    }

    /** The next active entries that have not ended yet. */
    public List<EventRow> upcoming(UUID ownerId, Instant now, int limit) {
        return jdbc.query(SELECT + """
                         WHERE e.owner_id = :owner AND e.deleted_at IS NULL AND e.end_at > :now
                           AND e.status NOT IN ('CANCELLED', 'DELIVERED')
                        ORDER BY e.start_at, e.title LIMIT :limit""",
                new MapSqlParameterSource("owner", ownerId).addValue("now", Timestamp.from(now)).addValue("limit", limit), rowMapper);
    }

    /** One aggregate query (§5.5); revenue only counts {@code currency}. */
    public SummaryRow summary(UUID ownerId, Instant from, Instant to, Instant todayStart, Instant todayEnd, String currency) {
        return jdbc.queryForObject("""
                        SELECT count(*) FILTER (WHERE in_range AND is_order AND status <> 'CANCELLED') AS orders_in_range,
                               count(*) FILTER (WHERE is_order AND status NOT IN ('CANCELLED', 'DELIVERED')
                                                AND start_at < :todayEnd AND end_at > :todayStart) AS due_today,
                               count(*) FILTER (WHERE status = 'IN_PRODUCTION') AS in_production,
                               count(*) FILTER (WHERE status = 'READY') AS ready,
                               coalesce(sum(total_amount) FILTER (WHERE in_range AND is_order AND status <> 'CANCELLED'
                                                                  AND upper(currency) = :currency), 0) AS revenue
                        FROM (SELECT status, start_at, end_at, total_amount, currency, type IN %s AS is_order,
                                     (start_at < :to AND end_at > :from) AS in_range
                              FROM calendar_events WHERE owner_id = :owner AND deleted_at IS NULL) e""".formatted(ORDER_TYPES),
                new MapSqlParameterSource("owner", ownerId).addValue("from", Timestamp.from(from)).addValue("to", Timestamp.from(to))
                        .addValue("todayStart", Timestamp.from(todayStart)).addValue("todayEnd", Timestamp.from(todayEnd))
                        .addValue("currency", currency),
                (rs, n) -> new SummaryRow(rs.getLong("orders_in_range"), rs.getLong("due_today"), rs.getLong("in_production"),
                        rs.getLong("ready"), rs.getBigDecimal("revenue")));
    }

    // ─── recipes ───────────────────────────────────────────────────────────────────────

    /** Replaces the recipe lines of {@code eventId}, keeping the given order. */
    public void replaceRecipes(UUID eventId, List<NewRecipe> recipes, Supplier<UUID> ids) {
        jdbc.update("DELETE FROM calendar_event_recipes WHERE event_id = :event", Map.of("event", eventId));
        if (recipes.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = IntStream.range(0, recipes.size()).mapToObj(index -> {
            NewRecipe recipe = recipes.get(index);
            return new MapSqlParameterSource("id", ids.get()).addValue("event", eventId).addValue("recipe", recipe.recipeId())
                    .addValue("quantity", recipe.quantity()).addValue("configuration", recipe.configuration())
                    .addValue("notes", recipe.notes()).addValue("order", index);
        }).toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO calendar_event_recipes (id, event_id, recipe_id, quantity, recipe_configuration, notes, display_order)
                VALUES (:id, :event, :recipe, :quantity, CAST(:configuration AS JSONB), :notes, :order)""", batch);
    }

    /** Recipe lines of {@code eventIds}, grouped by entry in display order. */
    public Map<UUID, List<RecipeRow>> recipes(Collection<UUID> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.query("""
                                SELECT id, event_id, recipe_id, quantity, recipe_configuration::text AS configuration, notes, display_order
                                FROM calendar_event_recipes WHERE event_id IN (:events) ORDER BY event_id, display_order""",
                        Map.of("events", Set.copyOf(eventIds)),
                        (rs, n) -> new RecipeRow(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class),
                                rs.getObject("recipe_id", UUID.class), rs.getBigDecimal("quantity"), rs.getString("configuration"),
                                rs.getString("notes"), rs.getInt("display_order")))
                .stream().collect(Collectors.groupingBy(RecipeRow::eventId, LinkedHashMap::new, Collectors.toList()));
    }

    // ─── helpers ───────────────────────────────────────────────────────────────────────

    private MapSqlParameterSource params(EventRow row) {
        return new MapSqlParameterSource("id", row.id())
                .addValue("title", row.title())
                .addValue("type", row.type().name())
                .addValue("status", row.status().name())
                .addValue("startAt", Timestamp.from(row.startAt()))
                .addValue("endAt", Timestamp.from(row.endAt()))
                .addValue("allDay", row.allDay())
                .addValue("color", row.color())
                .addValue("location", row.location())
                .addValue("clientName", encryption.encrypt(row.clientName()))
                .addValue("clientPhone", encryption.encrypt(row.clientPhone()))
                .addValue("clientEmail", encryption.encrypt(row.clientEmail()))
                .addValue("notes", row.notes())
                .addValue("totalAmount", row.totalAmount())
                .addValue("currency", row.currency())
                .addValue("reminders", "{" + row.reminders().stream().map(String::valueOf).collect(Collectors.joining(",")) + "}")
                .addValue("productionStartAt", row.productionStartAt() == null ? null : Timestamp.from(row.productionStartAt()));
    }

    private static List<String> names(Set<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).toList();
    }

    static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static List<Integer> ints(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return Arrays.stream((Object[]) array.getArray()).map(value -> ((Number) value).intValue()).toList();
    }
}
