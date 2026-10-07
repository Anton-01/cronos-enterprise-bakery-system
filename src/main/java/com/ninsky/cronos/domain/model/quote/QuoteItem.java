package com.ninsky.cronos.domain.model.quote;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate-internal child of {@link Quote} — no independent repository/port exists for this today.
 * References Recipe (a peer aggregate) by {@code recipeId} only, nullable — a fully custom item
 * (not from the recipe catalog) is allowed. {@code recipeVersionId}/{@code profitMarginId} from the
 * old entity are dropped: confirmed dead (no reads/writes anywhere outside their own getter/setter).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteItem {
    private UUID id;
    private UUID recipeId;
    private String productName;
    private String productDescription;
    private String productSize;
    private String imageFilePath;
    private BigDecimal quantity;
    @Builder.Default
    private BigDecimal scaleFactor = BigDecimal.ONE;
    private BigDecimal unitCost;
    private BigDecimal profitPercentage;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private String notes;
    private Integer displayOrder;
    // Recipe snapshot (kitchen §6.2): later recipe changes never alter an issued quote
    private String recipeConfiguration;
    @Builder.Default
    private String allergens = "[]";
    private Long recipeVersion;
    private Instant costCalculatedAt;
    private boolean priceReviewRequired;
    @Builder.Default
    private Long version = 0L;
}
