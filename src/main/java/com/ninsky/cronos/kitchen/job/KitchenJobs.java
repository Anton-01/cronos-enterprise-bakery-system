package com.ninsky.cronos.kitchen.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbox of kitchen background work: JSON payloads over {@link KitchenJobCustomRepository}. */
@Component
@RequiredArgsConstructor
public class KitchenJobs {

    static final String RECALCULATE = "RECALCULATE_RECIPES";
    static final int MAX_ATTEMPTS = 5;

    private final KitchenJobCustomRepository repository;
    private final ObjectMapper objectMapper;

    public record Claimed(long id, RecalculationJob job, int attempts) {
    }

    public void enqueue(RecalculationJob job) {
        repository.insert(RECALCULATE, write(job));
    }

    /** Locks the oldest pending job for the current transaction. */
    Optional<Claimed> claim() {
        return repository.claim().map(row -> new Claimed(row.id(), read(row.payload()), row.attempts()));
    }

    void done(long id) {
        repository.done(id);
    }

    void failed(long id, int attempts, String error) {
        repository.failed(id, attempts, String.valueOf(error), MAX_ATTEMPTS);
    }

    List<UUID> recipesWithoutOwnPrice(UUID ingredientId) {
        return repository.recipesWithoutOwnPrice(ingredientId);
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
