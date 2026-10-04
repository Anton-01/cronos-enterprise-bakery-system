package com.ninsky.cronos.application.service.catalog;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.model.core.MeasurementUnit;
import com.ninsky.cronos.domain.model.core.ReservedUnitCodes;
import com.ninsky.cronos.domain.model.core.UnitType;
import com.ninsky.cronos.domain.service.core.MeasurementUnitRules;
import com.ninsky.cronos.infrastructure.exception.CatalogException.Reason;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Every cross-record rule of the unit catalog, evaluated against an in-memory snapshot of the whole
 * catalog (a handful of types, a few dozen units — loading it is cheaper than the round trips of
 * per-rule queries). The single-row REST services and the .xlsx import both go through here, so a
 * rule can never hold on one path and not on the other.
 * <p>
 * Usage: {@code admit…} a candidate (a new record has {@code id == null}; an update carries the id
 * of the record it replaces). With no violations the snapshot is updated in place, so later
 * candidates of the same batch see it; then {@link #verifyBaseUnits()} checks the types whose base
 * composition may have changed. Not thread-safe: one instance per write operation.
 */
public final class UnitCatalogPolicy {

    private final List<UnitType> types;
    private final List<MeasurementUnit> units;
    private final Set<Long> referencedUnitIds;
    private final Set<Long> baseSensitiveTypes = new HashSet<>();

    private UnitCatalogPolicy(Collection<UnitType> types, Collection<MeasurementUnit> units, Set<Long> referencedUnitIds) {
        this.types = new ArrayList<>(types);
        this.units = new ArrayList<>(units);
        this.referencedUnitIds = Set.copyOf(referencedUnitIds);
    }

    /** @param referencedUnitIds ids (among {@code units}) referenced by raw materials / recipes / density rules */
    public static UnitCatalogPolicy of(Collection<UnitType> types, Collection<MeasurementUnit> units, Set<Long> referencedUnitIds) {
        return new UnitCatalogPolicy(types, units, referencedUnitIds);
    }

    // ---------------------------------------------------------------- unit types

    public List<RuleViolation> admitUnitType(UnitType candidate) {
        Optional<UnitType> existing = typeById(candidate.getId());
        List<RuleViolation> violations = new ArrayList<>();

        MeasurementUnitRules.checkCode(candidate.getCodeIdentity())
                .ifPresent(v -> violations.add(RuleViolation.of(Reason.INVALID, "codeIdentity", "validation.catalog.code.pattern")));
        MeasurementUnitRules.checkDisplayText(candidate.getName())
                .ifPresent(v -> violations.add(RuleViolation.of(Reason.INVALID, "name", "catalog.text.formulaInjection", "name")));

        types.stream()
                .filter(other -> isOther(other.getId(), candidate.getId()))
                .forEach(other -> {
                    if (other.getCodeIdentity().equalsIgnoreCase(candidate.getCodeIdentity())) {
                        violations.add(RuleViolation.of(Reason.DUPLICATE, "codeIdentity", "catalog.unitType.codeDuplicated", candidate.getCodeIdentity()));
                    }
                    if (other.getName().equalsIgnoreCase(candidate.getName())) {
                        violations.add(RuleViolation.of(Reason.DUPLICATE, "name", "catalog.unitType.nameDuplicated", candidate.getName()));
                    }
                    if (other.getDimension() == candidate.getDimension()) {
                        violations.add(RuleViolation.of(Reason.DUPLICATE, "dimension", "catalog.unitType.dimensionTaken",
                                candidate.getDimension(), other.getName()));
                    }
                });

        existing.ifPresent(current -> {
            long unitCount = unitsOfType(current.getId()).size();
            if (current.getDimension() != candidate.getDimension() && unitCount > 0) {
                violations.add(RuleViolation.of(Reason.INTEGRITY, "dimension", "catalog.unitType.dimensionLocked", current.getName(), unitCount));
            }
        });
        if (candidate.getStatus() != RecordStatus.ACTIVE && candidate.getId() != null) {
            long activeUnits = unitsOfType(candidate.getId()).stream().filter(MeasurementUnit::isActive).count();
            if (activeUnits > 0) {
                violations.add(RuleViolation.of(Reason.INTEGRITY, "status", "catalog.unitType.hasActiveUnits",
                        candidate.getName(), candidate.getStatus(), activeUnits));
            }
        }

        if (violations.isEmpty()) {
            existing.ifPresent(types::remove);
            types.add(candidate);
        }
        return violations;
    }

    public List<RuleViolation> checkUnitTypeDeletion(UnitType unitType) {
        long unitCount = unitsOfType(unitType.getId()).size();
        return unitCount == 0
                ? List.of()
                : List.of(RuleViolation.of(Reason.INTEGRITY, null, "catalog.unitType.hasUnits", unitType.getName(), unitCount));
    }

    // ---------------------------------------------------------------- measurement units

    public List<RuleViolation> admitMeasurementUnit(MeasurementUnit candidate) {
        Optional<MeasurementUnit> existing = unitById(candidate.getId());
        List<RuleViolation> violations = new ArrayList<>();

        MeasurementUnitRules.checkCode(candidate.getCodeIdentity())
                .ifPresent(v -> violations.add(RuleViolation.of(Reason.INVALID, "codeIdentity", "validation.catalog.code.pattern")));
        MeasurementUnitRules.checkDisplayText(candidate.getName())
                .ifPresent(v -> violations.add(RuleViolation.of(Reason.INVALID, "name", "catalog.text.formulaInjection", "name")));
        MeasurementUnitRules.checkDisplayText(candidate.getNamePlural())
                .ifPresent(v -> violations.add(RuleViolation.of(Reason.INVALID, "namePlural", "catalog.text.formulaInjection", "namePlural")));
        MeasurementUnitRules.checkFactor(candidate.getMultiplierToBase(), candidate.isBaseUnit())
                .ifPresent(v -> violations.add(RuleViolation.of(Reason.INVALID, "multiplierToBase", factorMessageKey(v))));

        units.stream()
                .filter(other -> isOther(other.getId(), candidate.getId()))
                .forEach(other -> {
                    if (other.getCodeIdentity().equals(candidate.getCodeIdentity())) {
                        violations.add(RuleViolation.of(Reason.DUPLICATE, "codeIdentity", "catalog.unit.codeDuplicated", candidate.getCodeIdentity()));
                    }
                    if (other.getName().equalsIgnoreCase(candidate.getName())) {
                        violations.add(RuleViolation.of(Reason.DUPLICATE, "name", "catalog.unit.nameDuplicated", candidate.getName()));
                    }
                });

        Optional<UnitType> unitType = typeById(candidate.getUnitTypeId());
        if (unitType.isEmpty()) {
            violations.add(RuleViolation.of(Reason.NOT_FOUND, "unitTypeId", "catalog.unitType.notFound", candidate.getUnitTypeId()));
        } else if (candidate.isActive() && !unitType.get().isActive() && becomesActiveIn(candidate, existing)) {
            violations.add(RuleViolation.of(Reason.INTEGRITY, "unitTypeId", "catalog.unitType.inactive", unitType.get().getName()));
        }

        existing.ifPresent(current -> {
            if (referencedUnitIds.contains(current.getId())
                    && current.differsInConversionSemantics(candidate.getUnitTypeId(), candidate.getMultiplierToBase(), candidate.isBaseUnit())) {
                violations.add(RuleViolation.of(Reason.INTEGRITY, "multiplierToBase", "catalog.unit.inUseLocked", current.getName()));
            }
            if (ReservedUnitCodes.isReserved(current.getCodeIdentity())
                    && (!current.getCodeIdentity().equals(candidate.getCodeIdentity()) || !candidate.isActive())) {
                violations.add(RuleViolation.of(Reason.INTEGRITY, "codeIdentity", "catalog.unit.reserved", current.getCodeIdentity()));
            }
        });

        if (violations.isEmpty()) {
            markBaseSensitive(candidate, existing);
            existing.ifPresent(units::remove);
            units.add(candidate);
        }
        return violations;
    }

    public List<RuleViolation> checkMeasurementUnitDeletion(MeasurementUnit unit) {
        List<RuleViolation> violations = new ArrayList<>();
        if (ReservedUnitCodes.isReserved(unit.getCodeIdentity())) {
            violations.add(RuleViolation.of(Reason.INTEGRITY, null, "catalog.unit.reserved", unit.getCodeIdentity()));
        }
        if (referencedUnitIds.contains(unit.getId())) {
            violations.add(RuleViolation.of(Reason.INTEGRITY, null, "catalog.unit.inUseDelete", unit.getName()));
        }
        if (unit.isBaseUnit() && unitsOfType(unit.getUnitTypeId()).size() > 1) {
            String typeName = typeById(unit.getUnitTypeId()).map(UnitType::getName).orElse(String.valueOf(unit.getUnitTypeId()));
            violations.add(RuleViolation.of(Reason.INTEGRITY, null, "catalog.unit.baseDelete", unit.getName(), typeName));
        }
        return violations;
    }

    /**
     * Exactly one base unit per unit type whose base composition changed in this operation (a new
     * unit, a moved unit, a toggled base flag). Types left untouched are not judged, so legacy
     * inconsistencies elsewhere never block an unrelated edit.
     */
    public List<RuleViolation> verifyBaseUnits() {
        List<RuleViolation> violations = new ArrayList<>();
        for (Long typeId : baseSensitiveTypes) {
            List<MeasurementUnit> typeUnits = unitsOfType(typeId);
            if (typeUnits.isEmpty()) {
                continue;
            }
            String typeName = typeById(typeId).map(UnitType::getName).orElse(String.valueOf(typeId));
            List<String> bases = typeUnits.stream().filter(MeasurementUnit::isBaseUnit).map(MeasurementUnit::getName).sorted().toList();
            if (bases.isEmpty()) {
                violations.add(RuleViolation.of(Reason.INTEGRITY, "isBaseUnit", "catalog.unit.baseMissing", typeName));
            } else if (bases.size() > 1) {
                violations.add(RuleViolation.of(Reason.INTEGRITY, "isBaseUnit", "catalog.unit.baseMultiple", typeName, String.join(", ", bases)));
            }
        }
        return violations;
    }

    // ---------------------------------------------------------------- lookups

    public Optional<UnitType> unitTypeByCode(String code) {
        return code == null ? Optional.empty()
                : types.stream().filter(t -> t.getCodeIdentity().toLowerCase(Locale.ROOT).equals(code.toLowerCase(Locale.ROOT))).findFirst();
    }

    public Optional<MeasurementUnit> measurementUnitByCode(String code) {
        return units.stream().filter(u -> u.getCodeIdentity().equals(code)).findFirst();
    }

    public boolean isReferenced(Long unitId) {
        return unitId != null && referencedUnitIds.contains(unitId);
    }

    private Optional<UnitType> typeById(Long id) {
        return id == null ? Optional.empty() : types.stream().filter(t -> id.equals(t.getId())).findFirst();
    }

    private Optional<MeasurementUnit> unitById(Long id) {
        return id == null ? Optional.empty() : units.stream().filter(u -> id.equals(u.getId())).findFirst();
    }

    private List<MeasurementUnit> unitsOfType(Long unitTypeId) {
        return units.stream().filter(u -> Objects.equals(u.getUnitTypeId(), unitTypeId)).toList();
    }

    private void markBaseSensitive(MeasurementUnit candidate, Optional<MeasurementUnit> existing) {
        if (existing.isEmpty()) {
            baseSensitiveTypes.add(candidate.getUnitTypeId());
            return;
        }
        MeasurementUnit current = existing.get();
        if (!Objects.equals(current.getUnitTypeId(), candidate.getUnitTypeId()) || current.isBaseUnit() != candidate.isBaseUnit()) {
            baseSensitiveTypes.add(current.getUnitTypeId());
            baseSensitiveTypes.add(candidate.getUnitTypeId());
        }
    }

    /** A new unit, a unit moved to another type, or a re-activation. */
    private static boolean becomesActiveIn(MeasurementUnit candidate, Optional<MeasurementUnit> existing) {
        return existing.map(current -> !Objects.equals(current.getUnitTypeId(), candidate.getUnitTypeId()) || !current.isActive())
                .orElse(true);
    }

    /** {@code null} ids are never "the same record": two new rows are always distinct. */
    private static boolean isOther(Long otherId, Long candidateId) {
        return otherId == null || !otherId.equals(candidateId);
    }

    private static String factorMessageKey(MeasurementUnitRules.Violation violation) {
        return switch (violation) {
            case FACTOR_REQUIRED -> "validation.unit.factor.required";
            case FACTOR_NOT_POSITIVE -> "validation.unit.factor.positive";
            case FACTOR_PRECISION -> "validation.unit.factor.precision";
            case BASE_FACTOR_MUST_BE_ONE -> "catalog.unit.baseFactor";
            case CODE_FORMAT -> "validation.catalog.code.pattern";
            case TEXT_FORMULA_INJECTION -> "catalog.text.formulaInjection";
        };
    }
}
