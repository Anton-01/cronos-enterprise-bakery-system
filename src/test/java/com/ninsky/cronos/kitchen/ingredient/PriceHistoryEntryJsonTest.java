package com.ninsky.cronos.kitchen.ingredient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PriceHistoryEntryJsonTest {

    /** Even a mapper that drops nulls must keep {@code changePercent} and {@code recordedBy}. */
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Test
    void nestsThePurchaseUnderPriceAndKeepsNullChangePercent() {
        PriceHistoryEntry entry = new PriceHistoryEntry(UUID.randomUUID(),
                new IngredientPrice(new BigDecimal("1.0000"), 3, "kg", new BigDecimal("224.24"), "MXN", null, LocalDate.of(2026, 10, 1)),
                new BigDecimal("0.22424000"), null, Instant.parse("2026-10-01T14:03:00Z"), null);

        JsonNode json = mapper.valueToTree(entry);

        assertThat(json.fieldNames()).toIterable()
                .containsExactly("id", "price", "costPerBaseUnit", "changePercent", "recordedAt", "recordedBy");
        assertThat(json.get("price").fieldNames()).toIterable()
                .containsExactly("purchaseQuantity", "purchaseUnitId", "purchaseUnitCode", "price", "currency", "supplier", "pricedAt");
        assertThat(json.get("price").get("pricedAt").asText()).isEqualTo("2026-10-01");
        assertThat(json.get("price").get("purchaseUnitCode").asText()).isEqualTo("kg");
        assertThat(json.get("changePercent").isNull()).isTrue();
        assertThat(json.get("recordedAt").asText()).isEqualTo("2026-10-01T14:03:00Z");
    }

    @Test
    void changePercentComparesWithThePreviousCostAtTwoDecimals() {
        assertThat(IngredientQueryCustomRepository.changePercent(new BigDecimal("0.19190000"), new BigDecimal("0.22424000")))
                .isEqualByComparingTo("16.85");
        assertThat(IngredientQueryCustomRepository.changePercent(new BigDecimal("0.20000000"), new BigDecimal("0.15000000")))
                .isEqualByComparingTo("-25.00");
        assertThat(IngredientQueryCustomRepository.changePercent(null, new BigDecimal("0.2"))).isNull();
    }
}
