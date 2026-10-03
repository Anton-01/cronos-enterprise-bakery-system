package com.ninsky.cronos.application.imports;

import com.ninsky.cronos.application.service.UnitCatalogResponseMapper;
import com.ninsky.cronos.application.service.audit.CatalogAuditTrail;
import com.ninsky.cronos.application.service.catalog.RuleViolation;
import com.ninsky.cronos.application.service.catalog.UnitCatalogPolicy;
import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.entity.enums.UnitDimension;
import com.ninsky.cronos.domain.model.audit.Actor;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.core.UnitType;
import com.ninsky.cronos.domain.model.imports.ImportResource;
import com.ninsky.cronos.domain.port.core.MeasurementUnitRepositoryPort;
import com.ninsky.cronos.domain.port.core.UnitTypeRepositoryPort;
import com.ninsky.cronos.domain.service.core.MeasurementUnitRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Sheet {@code UnitTypes}: upsert by {@code code} (case-insensitive). A blank {@code status} keeps
 * the stored status (ACTIVE for new rows).
 */
@Component
@RequiredArgsConstructor
public class UnitTypeImportHandler implements CatalogImportHandler<UnitType> {

    static final String CODE = "code";
    static final String NAME = "name";
    static final String DIMENSION = "dimension";
    static final String STATUS = "status";

    private static final Map<String, String> COLUMN_OF_FIELD = Map.of(
            "codeIdentity", CODE, "name", NAME, "dimension", DIMENSION, "status", STATUS);

    private final UnitTypeRepositoryPort unitTypeRepository;
    private final MeasurementUnitRepositoryPort measurementUnitRepository;
    private final CatalogAuditTrail auditTrail;

    @Override
    public ImportResource resource() {
        return ImportResource.UNIT_TYPE;
    }

    @Override
    public List<ColumnSpec> columns() {
        return List.of(ColumnSpec.required(CODE), ColumnSpec.required(NAME), ColumnSpec.required(DIMENSION), ColumnSpec.optional(STATUS));
    }

    @Override
    public List<PlannedRow<UnitType>> plan(List<RowReader> rows, IssueCollector issues) {
        UnitCatalogPolicy policy = UnitCatalogPolicy.of(unitTypeRepository.findAll(), measurementUnitRepository.findAll(), Set.of());
        Map<String, Integer> firstRowOfCode = new HashMap<>();
        List<PlannedRow<UnitType>> plan = new ArrayList<>();

        for (RowReader row : rows) {
            Optional<String> code = row.requiredText(CODE, MeasurementUnitRules.CODE_MAX_LENGTH);
            Optional<String> name = row.requiredText(NAME, MeasurementUnitRules.NAME_MAX_LENGTH);
            Optional<UnitDimension> dimension = row.enumValue(DIMENSION, UnitDimension.class, true);
            Optional<RecordStatus> status = row.enumValue(STATUS, RecordStatus.class, false);
            if (row.hasErrors()) {
                continue;
            }
            Integer firstRow = firstRowOfCode.putIfAbsent(code.get().toLowerCase(Locale.ROOT), row.rowNumber());
            if (firstRow != null) {
                row.error(CODE, "import.row.duplicateKey", code.get(), firstRow);
                continue;
            }

            Optional<UnitType> current = policy.unitTypeByCode(code.get());
            UnitType candidate = current.map(UnitType::toBuilder).orElseGet(UnitType::builder)
                    .codeIdentity(current.map(UnitType::getCodeIdentity).orElse(code.get()))
                    .name(name.get())
                    .dimension(dimension.get())
                    .status(status.orElse(current.map(UnitType::getStatus).orElse(RecordStatus.ACTIVE)))
                    .build();

            List<RuleViolation> violations = policy.admitUnitType(candidate);
            violations.forEach(v -> row.error(columnOf(v.field()), v.messageKey(), v.args().toArray()));
            if (violations.isEmpty()) {
                plan.add(PlannedRow.of(row.rowNumber(), candidate.getCodeIdentity(), current.orElse(null), candidate, UnitCatalogResponseMapper::snapshot));
            }
        }
        return plan;
    }

    @Override
    public List<ImportRowResult> apply(List<PlannedRow<UnitType>> plan, Actor actor, UUID batchId) {
        List<ImportRowResult> results = new ArrayList<>(plan.size());
        for (PlannedRow<UnitType> row : plan) {
            if (row.action() == ImportAction.UNCHANGED) {
                results.add(row.toResult(row.current().getId()));
                continue;
            }
            UnitType saved = unitTypeRepository.save(row.candidate());
            AuditAction action = row.action() == ImportAction.CREATE ? AuditAction.UNIT_TYPE_CREATED : AuditAction.UNIT_TYPE_UPDATED;
            auditTrail.record(actor, action, CatalogAuditTrail.TARGET_UNIT_TYPE, saved.getId(), row.changes(), CatalogImportService.auditDetails(batchId, row.row()));
            results.add(row.toResult(saved.getId()));
        }
        return results;
    }

    /** Request-body field of a rule violation → worksheet column (rules without a field land on the key column). */
    private static String columnOf(String field) {
        return field == null ? CODE : COLUMN_OF_FIELD.getOrDefault(field, CODE);
    }
}
