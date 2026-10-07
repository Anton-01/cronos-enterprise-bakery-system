package com.ninsky.cronos.kitchen.unit;

import com.ninsky.cronos.application.event.UnitCatalogChangedEvent;
import com.ninsky.cronos.domain.entity.enums.UnitDimension;
import com.ninsky.cronos.kitchen.shared.Dimension;
import com.ninsky.cronos.kitchen.shared.KitchenCaches;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Live measurement units (§7), cached in {@code kitchenUnits}: every costing reads it, the catalog
 * changes rarely. Dropped after any committed unit-catalog change.
 */
@Component
@RequiredArgsConstructor
public class UnitCatalog {

    private static final String KEY = "all";

    private final NamedParameterJdbcTemplate jdbc;
    private final KitchenCaches caches;

    /** Units by id plus the base unit per dimension. */
    public record Snapshot(Map<Long, UnitInfo> byId, Map<Dimension, UnitInfo> baseUnits) {
    }

    public Snapshot snapshot() {
        return caches.get(KitchenCaches.UNITS, KEY, this::load);
    }

    public Optional<UnitInfo> find(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(snapshot().byId().get(id));
    }

    public Map<Long, UnitInfo> findAll(Collection<Long> ids) {
        Map<Long, UnitInfo> all = snapshot().byId();
        return ids.stream().distinct().filter(all::containsKey).collect(Collectors.toMap(Function.identity(), all::get));
    }

    public Optional<UnitInfo> baseUnit(Dimension dimension) {
        return Optional.ofNullable(snapshot().baseUnits().get(dimension));
    }

    public Optional<UnitInfo> byCode(String code) {
        return snapshot().byId().values().stream().filter(u -> u.code().equals(code)).findFirst();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(UnitCatalogChangedEvent event) {
        caches.clear(KitchenCaches.UNITS);
    }

    private Snapshot load() {
        record Row(UnitInfo unit, boolean base) {
        }
        var rows = jdbc.query("""
                SELECT mu.id, mu.code_identity, mu.name, ut.dimension, mu.multiplier_to_base, mu.status, mu.is_base_unit
                FROM measurement_units mu JOIN unit_types ut ON ut.id = mu.unit_type_id
                WHERE mu.deleted_at IS NULL AND ut.deleted_at IS NULL""", Map.of(),
                (rs, i) -> new Row(new UnitInfo(rs.getLong("id"), rs.getString("code_identity"), rs.getString("name"),
                        UnitDimension.valueOf(rs.getString("dimension")), rs.getBigDecimal("multiplier_to_base"),
                        "ACTIVE".equals(rs.getString("status"))), rs.getBoolean("is_base_unit")));
        Map<Long, UnitInfo> byId = rows.stream().map(Row::unit).collect(Collectors.toUnmodifiableMap(UnitInfo::id, Function.identity()));
        Map<Dimension, UnitInfo> bases = new EnumMap<>(Dimension.class);
        rows.stream().filter(Row::base)
                .forEach(r -> Dimension.of(r.unit().dimension()).ifPresent(d -> bases.put(d, r.unit())));
        return new Snapshot(byId, Map.copyOf(bases));
    }
}
