package com.ninsky.cronos.application.service.catalog;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.entity.enums.UnitDimension;
import com.ninsky.cronos.domain.model.core.MeasurementUnit;
import com.ninsky.cronos.domain.model.core.UnitType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UnitCatalogPolicyTest {

    private static final UnitType MASS = UnitType.builder().id(1L).codeIdentity("MASS").name("Masa").dimension(UnitDimension.MASS).build();
    private static final UnitType VOLUME = UnitType.builder().id(2L).codeIdentity("VOLUME").name("Volumen").dimension(UnitDimension.VOLUME).build();
    private static final MeasurementUnit GRAM = unit(10L, "g", "gramo", 1L, "1", true);
    private static final MeasurementUnit KILO = unit(11L, "kg", "kilogramo", 1L, "1000", false);
    private static final MeasurementUnit CUP = unit(20L, "cup", "taza", 2L, "240", false);
    private static final MeasurementUnit ML = unit(21L, "ml", "mililitro", 2L, "1", true);

    private static MeasurementUnit unit(Long id, String code, String name, Long typeId, String factor, boolean base) {
        return MeasurementUnit.builder().id(id).codeIdentity(code).name(name).namePlural(name + "s")
                .unitTypeId(typeId).multiplierToBase(new BigDecimal(factor)).isBaseUnit(base).build();
    }

    private static UnitCatalogPolicy policy(Set<Long> referenced) {
        return UnitCatalogPolicy.of(List.of(MASS, VOLUME), List.of(GRAM, KILO, CUP, ML), referenced);
    }

    private static List<String> keys(List<RuleViolation> violations) {
        return violations.stream().map(RuleViolation::messageKey).toList();
    }

    @Test
    void onlyOneUnitTypePerDimension() {
        UnitType secondMass = UnitType.builder().codeIdentity("MASS2").name("Masa imperial").dimension(UnitDimension.MASS).build();
        assertThat(keys(policy(Set.of()).admitUnitType(secondMass))).containsExactly("catalog.unitType.dimensionTaken");
    }

    @Test
    void unitTypeCodesAndNamesAreUniqueIgnoringCase() {
        UnitType clash = UnitType.builder().codeIdentity("mass").name("VOLUMEN").dimension(UnitDimension.LENGTH).build();
        assertThat(keys(policy(Set.of()).admitUnitType(clash)))
                .containsExactlyInAnyOrder("catalog.unitType.codeDuplicated", "catalog.unitType.nameDuplicated");
    }

    @Test
    void dimensionIsLockedOnceTheTypeHasUnits() {
        UnitType changed = MASS.toBuilder().dimension(UnitDimension.LENGTH).build();
        assertThat(keys(policy(Set.of()).admitUnitType(changed))).containsExactly("catalog.unitType.dimensionLocked");
    }

    @Test
    void unitTypeCannotBeDeactivatedWhileItHasActiveUnits() {
        UnitType inactive = MASS.toBuilder().status(RecordStatus.INACTIVE).build();
        assertThat(keys(policy(Set.of()).admitUnitType(inactive))).containsExactly("catalog.unitType.hasActiveUnits");
    }

    @Test
    void measurementUnitCodesAreCaseSensitive() {
        MeasurementUnit upper = unit(null, "G", "gramo grande", 1L, "5", false);
        assertThat(policy(Set.of()).admitMeasurementUnit(upper)).isEmpty();

        MeasurementUnit same = unit(null, "g", "otro gramo", 1L, "5", false);
        assertThat(keys(policy(Set.of()).admitMeasurementUnit(same))).containsExactly("catalog.unit.codeDuplicated");
    }

    @Test
    void secondBaseUnitIsCaughtByTheBaseVerification() {
        UnitCatalogPolicy policy = policy(Set.of());
        assertThat(policy.admitMeasurementUnit(unit(null, "gr", "gramo alterno", 1L, "1", true))).isEmpty();
        assertThat(keys(policy.verifyBaseUnits())).containsExactly("catalog.unit.baseMultiple");
    }

    @Test
    void firstUnitOfATypeMustBeItsBase() {
        UnitType length = UnitType.builder().id(3L).codeIdentity("LENGTH").name("Longitud").dimension(UnitDimension.LENGTH).build();
        UnitCatalogPolicy policy = UnitCatalogPolicy.of(List.of(MASS, length), List.of(GRAM), Set.of());
        assertThat(policy.admitMeasurementUnit(unit(null, "mm", "milímetro", 3L, "0.1", false))).isEmpty();
        assertThat(keys(policy.verifyBaseUnits())).containsExactly("catalog.unit.baseMissing");
    }

    @Test
    void movingTheBaseFlagWithinOneBatchIsValid() {
        UnitCatalogPolicy policy = policy(Set.of());
        assertThat(policy.admitMeasurementUnit(KILO.toBuilder().multiplierToBase(BigDecimal.ONE).isBaseUnit(true).build())).isEmpty();
        assertThat(policy.admitMeasurementUnit(GRAM.toBuilder().multiplierToBase(new BigDecimal("0.001")).isBaseUnit(false).build())).isEmpty();
        assertThat(policy.verifyBaseUnits()).isEmpty();
    }

    @Test
    void unitsInUseHaveFrozenConversionSemanticsButCanBeRenamed() {
        UnitCatalogPolicy policy = policy(Set.of(11L));
        assertThat(keys(policy.admitMeasurementUnit(KILO.toBuilder().multiplierToBase(new BigDecimal("999")).build())))
                .containsExactly("catalog.unit.inUseLocked");
        assertThat(policy.admitMeasurementUnit(KILO.toBuilder().name("kilo").build())).isEmpty();
    }

    @Test
    void reservedUnitsCannotBeRenamedDeactivatedOrDeleted() {
        UnitCatalogPolicy policy = policy(Set.of());
        assertThat(keys(policy.admitMeasurementUnit(CUP.toBuilder().codeIdentity("taza").build()))).containsExactly("catalog.unit.reserved");
        assertThat(keys(policy.admitMeasurementUnit(CUP.toBuilder().status(RecordStatus.INACTIVE).build()))).containsExactly("catalog.unit.reserved");
        assertThat(keys(policy.checkMeasurementUnitDeletion(CUP))).containsExactly("catalog.unit.reserved");
    }

    @Test
    void deletionIsBlockedForUnitsInUseAndForABaseOthersDependOn() {
        assertThat(keys(policy(Set.of(11L)).checkMeasurementUnitDeletion(KILO))).containsExactly("catalog.unit.inUseDelete");
        assertThat(keys(policy(Set.of()).checkMeasurementUnitDeletion(ML))).containsExactly("catalog.unit.baseDelete");
        assertThat(policy(Set.of()).checkMeasurementUnitDeletion(KILO)).isEmpty();
    }

    @Test
    void baseUnitFactorAndFormulaInjectionAreRejected() {
        MeasurementUnit bad = unit(null, "x", "=cmd", 2L, "2", true).toBuilder().namePlural("comandos").build();
        assertThat(keys(policy(Set.of()).admitMeasurementUnit(bad)))
                .containsExactlyInAnyOrder("catalog.text.formulaInjection", "catalog.unit.baseFactor");
    }

    @Test
    void newUnitsCannotJoinAnInactiveType() {
        UnitType inactiveLength = UnitType.builder().id(3L).codeIdentity("LENGTH").name("Longitud")
                .dimension(UnitDimension.LENGTH).status(RecordStatus.INACTIVE).build();
        UnitCatalogPolicy policy = UnitCatalogPolicy.of(List.of(inactiveLength), List.of(), Set.of());
        assertThat(keys(policy.admitMeasurementUnit(unit(null, "cm", "centímetro", 3L, "1", true))))
                .containsExactly("catalog.unitType.inactive");
    }
}
