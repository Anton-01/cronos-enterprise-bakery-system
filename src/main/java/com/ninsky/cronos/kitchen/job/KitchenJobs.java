package com.ninsky.cronos.kitchen.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/** Outbox of kitchen background work ({@code kitchen_jobs}); claimed with SKIP LOCKED so instances never collide. */
@Component
@RequiredArgsConstructor
public class KitchenJobs {

    static final String RECALCULATE = "RECALCULATE_RECIPES";
    static final int MAX_ATTEMPTS = 5;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public record Claimed(long id, RecalculationJob job, int attempts) {
    }

    public void enqueue(RecalculationJob job) {
        jdbc.update("INSERT INTO kitchen_jobs (kind, payload) VALUES (:kind, CAST(:payload AS jsonb))",
                new MapSqlParameterSource().addValue("kind", RECALCULATE).addValue("payload", write(job)));
    }

    /** Locks the oldest pending job for the current transaction. */
    Optional<Claimed> claim() {
        return jdbc.query("""
                SELECT id, payload, attempts FROM kitchen_jobs WHERE status = 'PENDING' ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED""",
                Map.of(), (rs, i) -> new Claimed(rs.getLong("id"), read(rs.getString("payload")), rs.getInt("attempts"))).stream().findFirst();
    }

    void done(long id) {
        jdbc.update("UPDATE kitchen_jobs SET status = 'DONE', processed_at = now() WHERE id = :id", Map.of("id", id));
    }

    void failed(long id, int attempts, String error) {
        jdbc.update("""
                UPDATE kitchen_jobs SET attempts = :attempts, last_error = left(:error, 500),
                    status = CASE WHEN :attempts >= :max THEN 'FAILED' ELSE 'PENDING' END,
                    processed_at = CASE WHEN :attempts >= :max THEN now() END
                WHERE id = :id""",
                new MapSqlParameterSource().addValue("id", id).addValue("attempts", attempts).addValue("error", String.valueOf(error))
                        .addValue("max", MAX_ATTEMPTS));
    }

    private String write(RecalculationJob job) {
        try {
            return objectMapper.writeValueAsString(job);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Job payload is not serialisable", e);
        }
    }

    private RecalculationJob read(String payload) {
        try {
            return objectMapper.readValue(payload, RecalculationJob.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt kitchen job payload", e);
        }
    }
}
