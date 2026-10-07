package com.ninsky.cronos.kitchen.costing;

import com.ninsky.cronos.kitchen.shared.Dimension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.ninsky.cronos.kitchen.costing.CostFixtures.CUP;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.G;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.KG;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.L;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.PZ;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.d;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseCostTest {

    @ParameterizedTest(name = "{0} for {1} kg at {2}% yield → {3}/g")
    @CsvSource({
            "100, 1,   100, 0.10000000",
            "100, 1,   80,  0.12500000",
            "450, 2.5, 90,  0.20000000",
            "10,  3,   100, 0.00333333"
    })
    void dividesThePriceByUsableBaseQuantity(String price, String kilos, String yield, String expected) {
        assertThat(PurchaseCost.costPerBaseUnit(d(price), d(kilos), KG, Dimension.MASS, null, d(yield)))
                .isEqualByComparingTo(expected).hasScaleOf(PurchaseCost.SCALE);
    }

    @Test
    void bridgesVolumePurchaseToMassBase() {
        // 1 l of oil at 0.92 g/ml = 920 g for 46 MXN
        assertThat(PurchaseCost.costPerBaseUnit(d("46"), d("1"), L, Dimension.MASS, d("0.92"), d("100")))
                .isEqualByComparingTo("0.05");
    }

    @Test
    void rejectsIncompatiblePurchaseUnit() {
        assertThatThrownBy(() -> PurchaseCost.costPerBaseUnit(d("10"), d("12"), PZ, Dimension.MASS, null, d("100")))
                .isInstanceOf(UnitIncompatibleException.class);
    }

    @Test
    void baseQuantityKeepsSameDimensionWithoutDensity() {
        assertThat(BaseQuantity.of(d("2.5"), KG, Dimension.MASS, null)).isEqualByComparingTo("2500");
        assertThat(BaseQuantity.of(d("250"), G, Dimension.MASS, null)).isEqualByComparingTo("250");
        assertThat(BaseQuantity.compatible(CUP.dimension(), Dimension.MASS, null)).isFalse();
        assertThat(BaseQuantity.compatible(CUP.dimension(), Dimension.MASS, d("0.5"))).isTrue();
        assertThat(BaseQuantity.compatible(PZ.dimension(), Dimension.MASS, d("0.5"))).isFalse();
    }
}
