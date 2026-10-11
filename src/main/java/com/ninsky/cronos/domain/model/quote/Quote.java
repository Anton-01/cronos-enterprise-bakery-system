package com.ninsky.cronos.domain.model.quote;

import com.ninsky.cronos.domain.entity.enums.QuoteStatus;
import com.ninsky.cronos.finance.pricing.FinanceRoundingMode;
import com.ninsky.cronos.finance.pricing.TaxFactorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import com.ninsky.cronos.quote.domain.QuoteRejectReason;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quote {
    private UUID id;
    private String quoteNumber;
    private UUID userId;
    private String clientName;
    private String clientEmail;
    private String clientPhone;
    private String clientAddress;
    private String notes;
    @Builder.Default
    private QuoteStatus status = QuoteStatus.DRAFT;
    private LocalDateTime validUntil;
    private BigDecimal subtotal;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal total;
    @Builder.Default
    private String currency = "MXN";
    // Pricing snapshot (spec §11.4): recalculations use these, never the current defaults
    private Integer currencyDecimalPlaces;
    private Long taxRateId;
    private TaxFactorType taxFactorType;
    private Boolean pricesIncludeTax;
    private FinanceRoundingMode roundingMode;
    private String publicToken;
    @Builder.Default
    private Integer viewsCount = 0;
    @Builder.Default
    private boolean isRevoked = false;
    private boolean priceReviewRequired;
    @Builder.Default
    private Long version = 0L;
    @Builder.Default
    private BigDecimal deliveryFee = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal extraFee = BigDecimal.ZERO;
    private String extraFeeDescription;
    /** Day of the event in the tenant calendar (optional). */
    private LocalDate eventDate;
    private Instant acceptedAt;
    private Instant rejectedAt;
    private QuoteRejectReason rejectReason;
    /** Order scheduled when the quote was accepted. */
    private UUID calendarEventId;
    private Instant firstViewedAt;
    /** Soft delete (DRAFT only). */
    private Instant deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<QuoteItem> items = new ArrayList<>();

    public void addItem(QuoteItem item) {
        items.add(item);
    }
}
