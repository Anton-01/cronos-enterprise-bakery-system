package com.ninsky.cronos.application.response.quote;

import com.ninsky.cronos.kitchen.shared.AllergenRef;
import lombok.Builder;
import java.math.BigDecimal;
import java.util.List;

@Builder
public record PublicQuoteItemResponse(
        String productName,
        String productDescription,
        String productSize,
        String mainImageUrl,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal,
        // Allergen declaration ("Contiene: …", NOM-051)
        List<AllergenRef> allergens
) { }
