package com.ninsky.cronos.infrastructure.persistence.core.adapter;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.entity.enums.UnitDimension;
import com.ninsky.cronos.domain.model.core.MeasurementUnit;
import com.ninsky.cronos.domain.model.core.MeasurementUnitView;
import com.ninsky.cronos.domain.port.core.MeasurementUnitSearchCriteria;
import com.ninsky.cronos.infrastructure.persistence.jdbc.JdbcPages;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Read side of {@code measurement_units} in plain SQL: the unit and its unit type in one join (no entity graph, no
 * lazy proxy), page and total in one statement. Soft-deleted rows are excluded like the entities'
 * {@code @SQLRestriction}.
 */
@Repository
public class MeasurementUnitCustomRepository {

    private static final String COLUMNS = """
            m.id, m.code_identity, m.name, m.name_plural, m.unit_type_id, m.multiplier_to_base, m.is_base_unit,
            m.is_system_default, m.user_id, m.status, m.created_at, m.created_by, m.updated_at, m.updated_by,
            t.code_identity AS type_code, t.name AS type_name, t.dimension AS type_dimension""";
    private static final String FROM = " FROM measurement_units m JOIN unit_types t ON t.id = m.unit_type_id AND t.deleted_at IS NULL"
            + " WHERE m.deleted_at IS NULL";
    private static final Map<String, String> SORTABLE = Map.of(
            "id", "m.id", "codeIdentity", "m.code_identity", "name", "m.name", "namePlural", "m.name_plural",
            "multiplierToBase", "m.multiplier_to_base", "status", "m.status", "unitType", "t.name", "dimension", "t.dimension",
            "createdAt", "m.created_at", "updatedAt", "m.updated_at");

    private static final RowMapper<MeasurementUnitView> VIEW = (rs, row) -> new MeasurementUnitView(
            MeasurementUnit.builder()
                    .id(rs.getLong("id"))
                    .codeIdentity(rs.getString("code_identity"))
                    .name(rs.getString("name"))
                    .namePlural(rs.getString("name_plural"))
                    .unitTypeId(rs.getLong("unit_type_id"))
                    .multiplierToBase(rs.getBigDecimal("multiplier_to_base"))
                    .isBaseUnit(rs.getBoolean("is_base_unit"))
                    .isSystemDefault(rs.getBoolean("is_system_default"))
                    .userId(rs.getObject("user_id") == null ? null : rs.getLong("user_id"))
                    .status(RecordStatus.valueOf(rs.getString("status")))
                    .createdAt(local(rs.getTimestamp("created_at")))
                    .createdBy(rs.getString("created_by"))
                    .updatedAt(local(rs.getTimestamp("updated_at")))
                    .updatedBy(rs.getString("updated_by"))
                    .build(),
            rs.getString("type_code"),
            rs.getString("type_name"),
            UnitDimension.valueOf(rs.getString("type_dimension")));

    private final NamedParameterJdbcTemplate jdbc;

    public MeasurementUnitCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Filtered, sorted page of units with their unit type. */
    public Page<MeasurementUnitView> search(MeasurementUnitSearchCriteria criteria, Pageable pageable) {
        List<String> filters = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (criteria.search() != null) {
            filters.add("(lower(m.code_identity) LIKE :pattern ESCAPE '\\' OR lower(m.name) LIKE :pattern ESCAPE '\\'"
                    + " OR lower(m.name_plural) LIKE :pattern ESCAPE '\\')");
            params.addValue("pattern", "%" + UnitTypeRepositoryAdapter.escapeLike(criteria.search().toLowerCase(Locale.ROOT)) + "%");
        }
        if (criteria.unitTypeId() != null) {
            filters.add("m.unit_type_id = :unitTypeId");
            params.addValue("unitTypeId", criteria.unitTypeId());
        }
        if (criteria.dimension() != null) {
            filters.add("t.dimension = :dimension");
            params.addValue("dimension", criteria.dimension().name());
        }
        if (criteria.status() != null) {
            filters.add("m.status = :status");
            params.addValue("status", criteria.status().name());
        }
        String where = FROM + filters.stream().map(filter -> " AND " + filter).reduce("", String::concat);
        String sql = "SELECT " + COLUMNS + ", " + JdbcPages.TOTAL_COLUMN + where + JdbcPages.orderBy(pageable.getSort(), SORTABLE, "m.id");
        return JdbcPages.page(jdbc, sql, params, pageable, VIEW, "SELECT count(*)" + where);
    }

    /** One unit with its unit type. */
    public Optional<MeasurementUnitView> findViewById(Long id) {
        return jdbc.query("SELECT " + COLUMNS + FROM + " AND m.id = :id", new MapSqlParameterSource("id", id), VIEW)
                .stream().findFirst();
    }

    /** One unit. */
    public Optional<MeasurementUnit> findById(Long id) {
        return findViewById(id).map(MeasurementUnitView::unit);
    }

    /** Active units of active unit types, the order of the selectable catalog. */
    public List<MeasurementUnitView> findSelectableViews() {
        return jdbc.query("SELECT " + COLUMNS + FROM + " AND m.status = 'ACTIVE' AND t.status = 'ACTIVE'"
                + " ORDER BY t.name ASC, m.multiplier_to_base ASC, m.name ASC", new MapSqlParameterSource(), VIEW);
    }

    private static LocalDateTime local(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
