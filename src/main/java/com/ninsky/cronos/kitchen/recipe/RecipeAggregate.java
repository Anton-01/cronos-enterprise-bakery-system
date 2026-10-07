package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.kitchen.costing.CostStatus;
import com.ninsky.cronos.kitchen.costing.FixedCostMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** A stored recipe: head, lines and fixed costs, loaded together. */
public record RecipeAggregate(Head head, List<Line> lines, List<Fixed> fixed) {

    public RecipeAggregate {
        lines = List.copyOf(lines);
        fixed = List.copyOf(fixed);
    }

    public UUID id() {
        return head.id();
    }

    public Optional<Line> line(UUID lineId) {
        return lines.stream().filter(l -> l.id().equals(lineId)).findFirst();
    }

    /** Editable fields + cached cost of {@code recipes}. */
    public record Head(UUID id, String code, UUID ownerId, String name, Long categoryId, Difficulty difficulty, String description,
                       String processHtml, String storageInstructions, Integer shelfLifeDays, Integer prepMinutes, Integer bakeMinutes,
                       Integer coolMinutes, Integer ovenTemperatureC, BigDecimal yieldQuantity, String yieldUnit, RecipeStatus status,
                       BigDecimal targetMarginPercent, BigDecimal wastePercent, Cost cost, Instant createdAt, UUID createdBy,
                       Instant updatedAt, UUID updatedBy, long version) {

        public boolean system() {
            return ownerId == null;
        }

        public Integer totalMinutes() {
            Integer[] parts = {prepMinutes, bakeMinutes, coolMinutes};
            return java.util.Arrays.stream(parts).allMatch(java.util.Objects::isNull) ? null
                    : java.util.Arrays.stream(parts).filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
        }
    }

    /** Cost columns as last calculated. */
    public record Cost(BigDecimal ingredientsCost, BigDecimal wasteCost, BigDecimal fixedCosts, BigDecimal totalCost, BigDecimal costPerUnit,
                       BigDecimal suggestedUnitPrice, int unpricedLines, CostStatus status, Instant calculatedAt) {
    }

    public record Line(UUID id, UUID ingredientId, String section, BigDecimal quantity, long unitId, boolean optional,
                       boolean quoteSelectable, String notes, int displayOrder, BigDecimal lineCost, List<ExtraAllergen> extraAllergens) {
        public Line {
            extraAllergens = List.copyOf(extraAllergens);
        }
    }

    public record ExtraAllergen(long allergenId, AllergenSource source) {
    }

    /** A recipe fixed cost joined with its master {@code user_fixed_costs} row. */
    public record Fixed(UUID id, UUID userFixedCostId, String name, FixedCostMethod method, BigDecimal defaultAmount,
                        BigDecimal masterPercentage, Integer minutes, BigDecimal percentage, BigDecimal cost) {
    }
}
