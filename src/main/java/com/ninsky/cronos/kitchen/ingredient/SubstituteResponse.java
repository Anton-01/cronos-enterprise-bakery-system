package com.ninsky.cronos.kitchen.ingredient;

import com.ninsky.cronos.kitchen.shared.AllergenRef;
import com.ninsky.cronos.kitchen.shared.Scope;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A declared substitute with what it removes ({@code freeOf}) and adds ({@code introduces}). */
public record SubstituteResponse(UUID ingredientId, String ingredientName, BigDecimal ratio, String notes, Scope scope,
                                 List<AllergenRef> freeOf, List<AllergenRef> introduces) {
}
