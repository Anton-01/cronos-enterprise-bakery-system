package com.ninsky.cronos.application.imports;

import com.ninsky.cronos.application.service.UnitCatalogResponseMapper;
import com.ninsky.cronos.application.service.audit.CatalogAuditTrail;
import com.ninsky.cronos.application.service.catalog.RuleViolation;
import com.ninsky.cronos.application.service.catalog.UnitCatalogPolicy;
import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.model.audit.Actor;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.core.MeasurementUnit;
import com.ninsky.cronos.domain.model.core.UnitType;
import com.ninsky.cronos.domain.model.imports.ImportResource;
import com.ninsky.cronos.domain.port.core.MeasurementUnitRepositoryPort;
import com.ninsky.cronos.domain.port.core.MeasurementUnitUsagePort;
import com.ninsky.cronos.domain.port.core.UnitTypeRepositoryPort;
import com.ninsky.cronos.domain.service.core.MeasurementUnitRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sheet {@code MeasurementUnits}: upsert by {@code code} (case-sensitive: {@code T} ≠ {@code t}).
 * {@code unitTypeCode} must name an existing unit type — import unit types first. A blank
 * {@code status} keeps the stored status (ACTIVE for new rows). The one-base-unit-per-type rule is
 * judged on the catalog as it would be after the whole file, so row order never matters.
 */
@Component
@RequiredArgsConstructor
public class MeasurementUnitImportHandler implements CatalogImportHandler<MeasurementUnit> {

    static final String CODE = "code";
    static final String NAME = "name";
    static final String NAME_PLURAL = "namePlural";
    static final String UNIT_TYPE_CODE = "unitTypeCode";
    static final String FACTOR = "factorToBase";
    static final String IS_BASE = "isBaseUnit";
    static final String STATUS = "status";

    private static final Map<String, String> COLUMN_OF_FIELD = Map.of(
            "codeIdentity", CODE, "name", NAME, "namePlural", NAME_PLURAL, "unitTypeId", UNIT_TYPE_CODE,
            "multiplierToBase", FACTOR, "isBaseUnit", IS_BASE, "status", STATUS);

    private final MeasurementUnitRepositoryPort measurementUnitRepository;
    private final UnitTypeRepositoryPort unitTypeRepository;
    private final MeasurementUnitUsagePort usagePort;
    private final CatalogAuditTrail auditTrail;

    @Override
    public ImportResource resource() {
        return ImportResource.MEASUREMENT_UNIT;
    }

    @Override
    public List<ColumnSpec> columns() {
        return List.of(ColumnSpec.required(CODE), ColumnSpec.required(NAME), ColumnSpec.required(NAME_PLURAL),
                ColumnSpec.required(UNIT_TYPE_CODE), ColumnSpec.required(FACTOR), ColumnSpec.required(IS_BASE),
                ColumnSpec.optional(STATUS));
    }

    @Override
    public List<PlannedRow<MeasurementUnit>> plan(List<RowReader> rows, IssueCollector issues) {
        List<MeasurementUnit> storedUnits = measurementUnitRepository.findAll();
        UnitCatalogPolicy policy = UnitCatalogPolicy.of(unitTypeRepository.findAll(), storedUnits,
                usagePort.findReferencedUnitIds(storedUnits.stream().map(MeasurementUnit::getId).toList()));
        Map<String, Integer> firstRowOfCode = new HashMap<>();
        List<PlannedRow<MeasurementUnit>> plan = new ArrayList<>();

        for (RowReader row : rows) {
            Optional<String> code = row.requiredText(CODE, MeasurementUnitRules.CODE_MAX_LENGTH);
            Optional<String> name = row.requiredText(NAME, MeasurementUnitRules.NAME_MAX_LENGTH);
            Optional<String> namePlural = row.requiredText(NAME_PLURAL, MeasurementUnitRules.NAME_MAX_LENGTH);
            Optional<String> unitTypeCode = row.requiredText(UNIT_TYPE_CODE, MeasurementUnitRules.CODE_MAX_LENGTH);
            Optional<BigDecimal> factor = row.requiredDecimal(FACTOR);
            Optional<Boolean> isBase = row.requiredBoolean(IS_BASE);
            Optional<RecordStatus> status = row.enumValue(STATUS, RecordStatus.class, false);
            Optional<UnitType> unitType = unitTypeCode.flatMap(policy::unitTypeByCode);
            if (unitTypeCode.isPresent() && unitType.isEmpty()) {
                row.error(UNIT_TYPE_CODE, "import.unit.unitTypeUnknown", unitTypeCode.get());
            }
            if (row.hasErrors()) {
                continue;
            }
            Integer firstRow = firstRowOfCode.putIfAbsent(code.get(), row.rowNumber());
            if (firstRow != null) {
                row.error(CODE, "import.row.duplicateKey", code.get(), firstRow);
                continue;
            }

            Optional<MeasurementUnit> current = policy.measurementUnitByCode(code.get());
            MeasurementUnit candidate = current.map(MeasurementUnit::toBuilder)
                    .orElseGet(() -> MeasurementUnit.builder().isSystemDefault(true))
                    .codeIdentity(code.get())
                    .name(name.get())
                    .namePlural(namePlural.get())
                    .unitTypeId(unitType.get().getId())
                    .multiplierToBase(factor.get())
                    .isBaseUnit(isBase.get())
                    .status(status.orElse(current.map(MeasurementUnit::getStatus).orElse(RecordStatus.ACTIVE)))
                    .build();

            List<RuleViolation> violations = policy.admitMeasurementUnit(candidate);
            violations.forEach(v -> row.error(columnOf(v.field()), v.messageKey(), v.args().toArray()));
            if (violations.isEmpty()) {
                plan.add(PlannedRow.of(row.rowNumber(), candidate.getCodeIdentity(), current.orElse(null), candidate, UnitCatalogResponseMapper::snapshot));
            }
        }
        policy.verifyBaseUnits().forEach(v -> issues.error(null, columnOf(v.field()), v.messageKey(), v.args().toArray()));
        return plan;
    }

    @Override
    public List<ImportRowResult> apply(List<PlannedRow<MeasurementUnit>> plan, Actor actor, UUID batchId) {
        List<ImportRowResult> results = new ArrayList<>(plan.size());
        for (PlannedRow<MeasurementUnit> row : plan) {
            if (row.action() == ImportAction.UNCHANGED) {
                results.add(row.toResult(row.current().getId()));
                continue;
            }
            MeasurementUnit saved = measurementUnitRepository.save(row.candidate());
            AuditAction action = row.action() == ImportAction.CREATE ? AuditAction.MEASUREMENT_UNIT_CREATED : AuditAction.MEASUREMENT_UNIT_UPDATED;
            auditTrail.record(actor, action, CatalogAuditTrail.TARGET_MEASUREMENT_UNIT, saved.getId(), row.changes(), CatalogImportService.auditDetails(batchId, row.row()));
            results.add(row.toResult(saved.getId()));
        }
        return results;
    }

    /** Request-body field of a rule violation → worksheet column (rules without a field land on the key column). */
    private static String columnOf(String field) {
        return field == null ? CODE : COLUMN_OF_FIELD.getOrDefault(field, CODE);
    }
}
