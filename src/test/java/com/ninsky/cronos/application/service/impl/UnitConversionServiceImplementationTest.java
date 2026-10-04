package com.ninsky.cronos.application.service.impl;

import com.ninsky.cronos.domain.entity.enums.UnitDimension;
import com.ninsky.cronos.domain.model.core.IngredientConversion;
import com.ninsky.cronos.domain.model.core.MeasurementUnit;
import com.ninsky.cronos.domain.model.core.UnitConversionResult;
import com.ninsky.cronos.domain.model.core.UnitType;
import com.ninsky.cronos.domain.port.core.IngredientConversionRepositoryPort;
import com.ninsky.cronos.domain.port.core.MeasurementUnitRepositoryPort;
import com.ninsky.cronos.domain.port.core.UnitTypeRepositoryPort;
import com.ninsky.cronos.infrastructure.exception.CatalogException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnitConversionServiceImplementationTest {

    private static final UUID FLOUR = UUID.randomUUID();

    private final IngredientConversionRepositoryPort conversions = mock(IngredientConversionRepositoryPort.class);
    private final MeasurementUnitRepositoryPort units = mock(MeasurementUnitRepositoryPort.class);
    private final UnitTypeRepositoryPort types = mock(UnitTypeRepositoryPort.class);
    private final UnitConversionServiceImplementation service = new UnitConversionServiceImplementation(conversions, units, types);

    private final MeasurementUnit gram = unit(1L, "g", 10L, "1");
    private final MeasurementUnit milligram = unit(2L, "mg", 10L, "0.001");
    private final MeasurementUnit kilogram = unit(3L, "kg", 10L, "1000");
    private final MeasurementUnit millilitre = unit(4L, "ml", 20L, "1");
    private final MeasurementUnit cup = unit(5L, "cup", 20L, "240");
    private final MeasurementUnit tablespoon = unit(6L, "tbsp", 20L, "15");
    private final MeasurementUnit piece = unit(7L, "pz", 30L, "1");

    private static MeasurementUnit unit(Long id, String code, Long typeId, String factor) {
        return MeasurementUnit.builder().id(id).codeIdentity(code).name(code).unitTypeId(typeId).multiplierToBase(new BigDecimal(factor)).build();
    }

    @BeforeEach
    void setUp() {
        when(types.findById(10L)).thenReturn(Optional.of(UnitType.builder().id(10L).dimension(UnitDimension.MASS).build()));
        when(types.findById(20L)).thenReturn(Optional.of(UnitType.builder().id(20L).dimension(UnitDimension.VOLUME).build()));
        when(types.findById(30L)).thenReturn(Optional.of(UnitType.builder().id(30L).dimension(UnitDimension.COUNT).build()));
        for (MeasurementUnit unit : List.of(gram, milligram, kilogram, millilitre, cup, tablespoon, piece)) {
            when(units.findById(unit.getId())).thenReturn(Optional.of(unit));
        }
    }

    @Test
    void sameUnitIsAnIdentity() {
        UnitConversionResult result = service.convertWithTrace(new BigDecimal("3"), cup, cup, null);
        assertThat(result.path()).isEqualTo(UnitConversionResult.Path.IDENTITY);
        assertThat(result.quantity()).isEqualByComparingTo("3");
    }

    @Test
    void sameDimensionIsLinear() {
        assertThat(service.convert(new BigDecimal("2.5"), kilogram, gram, null)).isEqualByComparingTo("2500");
        assertThat(service.convert(new BigDecimal("3"), cup, tablespoon, null)).isEqualByComparingTo("48");
    }

    @Test
    void keepsPrecisionForTinyAdditives() {
        // 0.5 mg in kg: lost entirely (rounded to 0.000001, +100%) at the former scale of 6.
        assertThat(service.convert(new BigDecimal("0.5"), milligram, kilogram, null)).isEqualByComparingTo("0.0000005");
    }

    @Test
    void volumeToMassPrefersTheRuleMeasuredInTheRequestedVolumeUnit() {
        IngredientConversion perCup = IngredientConversion.builder().id(100L).ingredientId(FLOUR).volumeUnitId(5L).massUnitId(1L).factor(new BigDecimal("125")).build();
        IngredientConversion perTablespoon = IngredientConversion.builder().id(101L).ingredientId(FLOUR).volumeUnitId(6L).massUnitId(1L).factor(new BigDecimal("8")).build();
        when(conversions.findAllByIngredientId(FLOUR)).thenReturn(List.of(perCup, perTablespoon));

        UnitConversionResult result = service.convertWithTrace(new BigDecimal("2"), tablespoon, gram, FLOUR);

        assertThat(result.path()).isEqualTo(UnitConversionResult.Path.DENSITY);
        assertThat(result.densityRuleId()).isEqualTo(101L);
        assertThat(result.quantity()).isEqualByComparingTo("16");
    }

    @Test
    void massToVolumeFallsBackToTheOldestRule() {
        IngredientConversion perCup = IngredientConversion.builder().id(100L).ingredientId(FLOUR).volumeUnitId(5L).massUnitId(1L).factor(new BigDecimal("125")).build();
        when(conversions.findAllByIngredientId(FLOUR)).thenReturn(List.of(perCup));

        // 250 g / 125 g per cup = 2 cups = 480 ml
        assertThat(service.convert(new BigDecimal("250"), gram, millilitre, FLOUR)).isEqualByComparingTo("480");
    }

    @Test
    void densityConversionWithoutARuleIsABusinessError() {
        when(conversions.findAllByIngredientId(FLOUR)).thenReturn(List.of());
        assertThatThrownBy(() -> service.convert(BigDecimal.ONE, cup, gram, FLOUR))
                .isInstanceOf(CatalogException.class)
                .hasMessage("catalog.conversion.densityMissing");
    }

    @Test
    void incompatibleDimensionsAreRejected() {
        assertThatThrownBy(() -> service.convert(BigDecimal.ONE, piece, gram, null))
                .isInstanceOf(CatalogException.class)
                .hasMessage("catalog.conversion.incompatible");
    }

    @Test
    void missingUnitTypeIsNotFound() {
        when(types.findById(anyLong())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.convert(BigDecimal.ONE, kilogram, gram, null))
                .isInstanceOf(CatalogException.class)
                .extracting(e -> ((CatalogException) e).reason())
                .isEqualTo(CatalogException.Reason.NOT_FOUND);
    }
}
