package com.ninsky.cronos.account.fiscal.infrastructure;

import com.ninsky.cronos.account.fiscal.domain.FiscalData;
import com.ninsky.cronos.account.fiscal.domain.TaxpayerType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

/**
 * Read side of {@code user_fiscal_data} in one plain SQL statement. The row is mapped through the existing
 * {@link FiscalDataMapper} (value-object validation lives there) from a detached, never-persisted holder: no
 * persistence context, no dirty checking.
 */
@Repository
public class FiscalDataCustomRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final FiscalDataMapper mapper;

    public FiscalDataCustomRepository(NamedParameterJdbcTemplate jdbc, FiscalDataMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<FiscalData> findByUserId(UUID userId) {
        return jdbc.query("""
                        SELECT user_id, legal_name, tax_id, taxpayer_type, tax_regime, street, exterior_number, interior_number,
                               neighborhood, municipality, state, zip_code, country, version, updated_at
                        FROM user_fiscal_data WHERE user_id = :id""",
                new MapSqlParameterSource("id", userId), (rs, row) -> {
                    UserFiscalDataJpaEntity holder = new UserFiscalDataJpaEntity(rs.getObject("user_id", UUID.class));
                    holder.setLegalName(rs.getString("legal_name"));
                    holder.setTaxId(rs.getString("tax_id"));
                    holder.setTaxpayerType(TaxpayerType.valueOf(rs.getString("taxpayer_type")));
                    holder.setTaxRegime(rs.getString("tax_regime"));
                    holder.setAddress(new FiscalAddressEmbeddable(rs.getString("street"), rs.getString("exterior_number"),
                            rs.getString("interior_number"), rs.getString("neighborhood"), rs.getString("municipality"),
                            rs.getString("state"), rs.getString("zip_code"), rs.getString("country")));
                    holder.setVersion(rs.getLong("version"));
                    Timestamp updatedAt = rs.getTimestamp("updated_at");
                    holder.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
                    return mapper.toDomain(holder);
                }).stream().findFirst();
    }
}
