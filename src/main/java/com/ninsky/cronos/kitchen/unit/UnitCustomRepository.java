package com.ninsky.cronos.kitchen.unit;

import com.ninsky.cronos.domain.entity.enums.UnitDimension;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** Measurement-unit SQL for the kitchen's {@link UnitCatalog}. */
@Repository
@RequiredArgsConstructor
public class UnitCustomRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** A live unit and whether it is its dimension's base. */
    public record Row(UnitInfo unit, boolean base) {
    }

    public List<Row> live() {
        return jdbc.query("""
                SELECT mu.id, mu.code_identity, mu.name, ut.dimension, mu.multiplier_to_base, mu.status, mu.is_base_unit
                FROM measurement_units mu JOIN unit_types ut ON ut.id = mu.unit_type_id
                WHERE mu.deleted_at IS NULL AND ut.deleted_at IS NULL""", Map.of(),
                (rs, i) -> new Row(new UnitInfo(rs.getLong("id"), rs.getString("code_identity"), rs.getString("name"),
                        UnitDimension.valueOf(rs.getString("dimension")), rs.getBigDecimal("multiplier_to_base"),
                        "ACTIVE".equals(rs.getString("status"))), rs.getBoolean("is_base_unit")));
    }
}
