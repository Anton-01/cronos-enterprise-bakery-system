package com.ninsky.cronos.iam.audit.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Renders the summary sentence at read time from {@code audit.summary.<ACTION>}: {0} actor label
 * (or "system"), {1} target label, {2} {@code params.detail}. Stored params, never stored sentences.
 */
@Component
@RequiredArgsConstructor
public class AuditSummaryRenderer {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final MessageSource messages;
    private final ObjectMapper objectMapper;

    public String summary(AuditRow row, Locale locale) {
        String actor = Stream.of(row.actorLabel(), row.actorUsername()).filter(Objects::nonNull).findFirst()
                .orElseGet(() -> messages.getMessage(row.actorId() == null ? "audit.actor.system" : "audit.actor.unknown", null, locale));
        String target = targetLabel(row).orElseGet(() -> messages.getMessage("audit.actor.unknown", null, locale));
        String detail = Optional.ofNullable(json(row.paramsJson()).get("detail")).map(Object::toString).orElse("");
        return messages.getMessage("audit.summary." + row.action(), new Object[]{actor, target, detail}, row.action(), locale);
    }

    public Optional<String> targetLabel(AuditRow row) {
        return Stream.of(row.targetLabel(), row.targetId()).filter(Objects::nonNull).findFirst();
    }

    /** Stored JSON object as a map; empty for null or unreadable values. */
    public Map<String, Object> json(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return Optional.ofNullable(objectMapper.readValue(value, MAP)).orElse(Map.of());
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
