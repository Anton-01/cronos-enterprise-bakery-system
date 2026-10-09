package com.ninsky.cronos.kitchen.recipe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Append-only recipe history (§5.8); summaries are stored as key + args and localised on read. */
@Component
@RequiredArgsConstructor
public class RecipeRevisions {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    /** Revisions of changes outside the editable aggregate (attachments and cover). */
    static final java.util.Set<String> ATTACHMENT_KEYS = java.util.Set.of("kitchen.revision.fileUploaded", "kitchen.revision.fileDeleted",
            "kitchen.revision.fileReplaced", "kitchen.revision.coverChanged", "kitchen.revision.coverCleared");

    private final RecipeRevisionCustomRepository repository;
    private final ObjectMapper objectMapper;
    private final KitchenMessages messages;

    /** Why a revision was written: an i18n key under {@code kitchen.revision.*} and its arguments. */
    public record Reason(String key, List<Object> args) {
        public Reason {
            args = List.copyOf(args);
        }

        public static Reason of(String key, Object... args) {
            return new Reason(key, List.of(args));
        }
    }

    public void write(UUID recipeId, long version, UUID actor, Instant at, Reason reason, Map<String, Object> changes, BigDecimal costPerUnit) {
        repository.insert(recipeId, version, actor, at, reason.key(), json(Map.of("args", reason.args())), json(changes), costPerUnit);
    }

    /**
     * Derived allergens of every live recipe using {@code ingredientId} changed: one set-based version bump
     * and revision per recipe. Returns the recipes touched.
     */
    public List<UUID> allergensChanged(UUID ingredientId, String ingredientName, UUID actor, Instant at) {
        return repository.bumpRecipesUsing(ingredientId, actor, at, "kitchen.revision.allergensChanged",
                json(Map.of("args", List.of(ingredientName))), json(Map.of("allergens", Map.of("ingredient", ingredientName))));
    }

    /**
     * True when the editor's {@code read} version is still a valid base for a save: nothing but attachment or cover
     * changes happened since (those bump the version for the history but do not touch what the editor holds).
     */
    public boolean onlyAttachmentChangesSince(UUID recipeId, long read, long current) {
        return read < current && repository.onlyKeysBetween(recipeId, read, current, ATTACHMENT_KEYS);
    }

    public CatalogPage<RecipeRevision> page(UUID recipeId, Integer page, Integer size) {
        PageQuery query = PageQuery.of(page, size, null, RecipeRevisionCustomRepository.SORTS, "version,desc");
        List<RecipeRevision> content = repository.page(recipeId, query.size(), query.offset()).stream()
                .map(this::revision).toList();
        return CatalogPage.of(content, query, repository.count(recipeId));
    }

    private RecipeRevision revision(RecipeRevisionCustomRepository.Row row) {
        Object args = read(row.summaryParams()).getOrDefault("args", List.of());
        Object[] values = args instanceof List<?> list ? list.toArray() : new Object[0];
        return new RecipeRevision(row.version(), row.changedAt(), row.changedBy(), messages.get(row.summaryKey(), values),
                read(row.changes()), row.costPerUnit());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Revision payload is not serialisable", e);
        }
    }

    private Map<String, Object> read(String value) {
        try {
            return value == null ? Map.of() : objectMapper.readValue(value, MAP);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
