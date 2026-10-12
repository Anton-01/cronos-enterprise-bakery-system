package com.ninsky.cronos.infrastructure.persistence.imports;

import com.ninsky.cronos.domain.model.imports.ImportBatch;
import com.ninsky.cronos.domain.model.imports.ImportResource;
import com.ninsky.cronos.domain.model.imports.ImportStatus;
import com.ninsky.cronos.infrastructure.persistence.jdbc.JdbcPages;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Read side of the import ledger in plain SQL. The history page never reads the {@code report} JSON (the heaviest
 * column); only the single-batch report does. Times are stored as UTC wall clock, as the JPA adapter wrote them.
 */
@Repository
public class DataImportBatchCustomRepository {

    private static final String COLUMNS = """
            b.id, b.resource, b.status, b.dry_run, b.file_name, b.file_size_bytes, b.file_sha256, b.total_rows, b.created_count,
            b.updated_count, b.unchanged_count, b.rejected_rows, b.error_count, b.warning_count, b.actor_user_id, b.actor_username,
            b.trace_id, b.started_at, b.finished_at""";

    private final NamedParameterJdbcTemplate jdbc;

    public DataImportBatchCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Newest first; optional filters; page and total in one statement. */
    public Page<ImportBatch> search(ImportResource resource, ImportStatus status, Pageable pageable) {
        String where = " FROM data_import_batches b WHERE (CAST(:resource AS text) IS NULL OR b.resource = :resource)"
                + " AND (CAST(:status AS text) IS NULL OR b.status = :status)";
        MapSqlParameterSource params = new MapSqlParameterSource("resource", resource == null ? null : resource.name())
                .addValue("status", status == null ? null : status.name());
        return JdbcPages.page(jdbc, "SELECT " + COLUMNS + ", " + JdbcPages.TOTAL_COLUMN + where + " ORDER BY b.started_at DESC, b.id",
                params, pageable, (rs, row) -> batch(rs, null), "SELECT count(*)" + where);
    }

    /** One batch with its full report. */
    public Optional<ImportBatch> findById(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + ", b.report::text AS report FROM data_import_batches b WHERE b.id = :id",
                new MapSqlParameterSource("id", id), (rs, row) -> batch(rs, rs.getString("report"))).stream().findFirst();
    }

    private static ImportBatch batch(ResultSet rs, String report) throws SQLException {
        return new ImportBatch(rs.getObject("id", UUID.class), ImportResource.valueOf(rs.getString("resource")),
                ImportStatus.valueOf(rs.getString("status")), rs.getBoolean("dry_run"), rs.getString("file_name"),
                rs.getLong("file_size_bytes"), rs.getString("file_sha256"), rs.getInt("total_rows"), rs.getInt("created_count"),
                rs.getInt("updated_count"), rs.getInt("unchanged_count"), rs.getInt("rejected_rows"), rs.getInt("error_count"),
                rs.getInt("warning_count"), rs.getObject("actor_user_id", UUID.class), rs.getString("actor_username"),
                rs.getString("trace_id"), utc(rs, "started_at"), utc(rs, "finished_at"), report);
    }

    private static Instant utc(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }
}
