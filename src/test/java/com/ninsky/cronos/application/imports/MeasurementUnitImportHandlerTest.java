package com.ninsky.cronos.application.imports;

import com.ninsky.cronos.application.imports.spreadsheet.CellValue;
import com.ninsky.cronos.application.imports.spreadsheet.SheetRow;
import com.ninsky.cronos.application.service.audit.CatalogAuditTrail;
import com.ninsky.cronos.domain.entity.enums.UnitDimension;
import com.ninsky.cronos.domain.model.core.MeasurementUnit;
import com.ninsky.cronos.domain.model.core.UnitType;
import com.ninsky.cronos.domain.port.core.MeasurementUnitRepositoryPort;
import com.ninsky.cronos.domain.port.core.MeasurementUnitUsagePort;
import com.ninsky.cronos.domain.port.core.UnitTypeRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MeasurementUnitImportHandlerTest {

    private static final List<String> HEADERS = List.of("code", "name", "namePlural", "unitTypeCode", "factorToBase", "isBaseUnit");

    private final MeasurementUnitRepositoryPort units = mock(MeasurementUnitRepositoryPort.class);
    private final UnitTypeRepositoryPort types = mock(UnitTypeRepositoryPort.class);
    private final MeasurementUnitUsagePort usage = mock(MeasurementUnitUsagePort.class);
    private final MeasurementUnitImportHandler handler = new MeasurementUnitImportHandler(units, types, usage, mock(CatalogAuditTrail.class));
    private final IssueCollector issues = new IssueCollector(new StaticMessageSource(), Locale.ENGLISH);

    @BeforeEach
    void setUp() {
        when(types.findAll()).thenReturn(List.of(
                UnitType.builder().id(1L).codeIdentity("MASS").name("Masa").dimension(UnitDimension.MASS).build(),
                UnitType.builder().id(2L).codeIdentity("VOLUME").name("Volumen").dimension(UnitDimension.VOLUME).build()));
        when(units.findAll()).thenReturn(List.of(
                MeasurementUnit.builder().id(10L).codeIdentity("g").name("gramo").namePlural("gramos").unitTypeId(1L)
                        .multiplierToBase(BigDecimal.ONE).isBaseUnit(true).build()));
        when(usage.findReferencedUnitIds(any())).thenReturn(Set.of());
    }

    private List<PlannedRow<MeasurementUnit>> plan(String[]... rows) {
        SheetLayout layout = SheetLayout.resolve(HEADERS, handler.columns(), issues);
        List<RowReader> readers = Arrays.stream(rows)
                .map(cells -> new SheetRow(rowNumber(rows, cells), Arrays.stream(cells).map(v -> new CellValue(CellValue.Kind.TEXT, v)).toList()))
                .map(row -> new RowReader(row, layout, issues))
                .toList();
        return handler.plan(readers, issues);
    }

    private static int rowNumber(String[][] rows, String[] cells) {
        return Arrays.asList(rows).indexOf(cells) + 2;
    }

    private List<String> issueCodes() {
        return issues.issues().stream().map(ImportIssue::code).toList();
    }

    @Test
    void plansCreatesUpdatesAndUnchangedRowsByCode() {
        List<PlannedRow<MeasurementUnit>> plan = plan(
                new String[]{"g", "gramo", "gramos", "MASS", "1", "TRUE"},
                new String[]{"kg", "kilogramo", "kilogramos", "mass", "1000", "FALSE"},
                new String[]{"ml", "mililitro", "mililitros", "VOLUME", "1", "TRUE"});

        assertThat(issues.hasErrors()).isFalse();
        assertThat(plan).extracting(PlannedRow::action)
                .containsExactly(ImportAction.UNCHANGED, ImportAction.CREATE, ImportAction.CREATE);
        assertThat(plan.get(1).candidate().getUnitTypeId()).as("unit type resolved by code, case-insensitively").isEqualTo(1L);
    }

    @Test
    void reportsEveryProblemWithItsRowAndColumn() {
        plan(
                new String[]{"kg", "kilogramo", "kilogramos", "WEIGHT", "1000", "FALSE"},
                new String[]{"lb", "libra", "libras", "MASS", "453,59", "FALSE"},
                new String[]{"oz", "onza", "onzas", "MASS", "28.35", "maybe"});

        assertThat(issues.issues()).extracting(ImportIssue::row, ImportIssue::column, ImportIssue::code).containsExactly(
                tuple(2, "unitTypeCode", "import.unit.unitTypeUnknown"),
                tuple(3, "factorToBase", "import.cell.decimalComma"),
                tuple(4, "isBaseUnit", "import.cell.notABoolean"));
    }

    @Test
    void duplicateCodesInTheFileAreRejectedButCaseMatters() {
        plan(
                new String[]{"T", "cucharada", "cucharadas", "VOLUME", "15", "FALSE"},
                new String[]{"t", "cucharadita", "cucharaditas", "VOLUME", "5", "FALSE"},
                new String[]{"T", "otra", "otras", "VOLUME", "15", "FALSE"},
                new String[]{"ml", "mililitro", "mililitros", "VOLUME", "1", "TRUE"});

        assertThat(issueCodes()).containsExactly("import.row.duplicateKey");
        assertThat(issues.issues().getFirst().row()).isEqualTo(4);
    }

    @Test
    void aTypeLeftWithoutBaseUnitIsAFileLevelError() {
        plan(new String[]{"l", "litro", "litros", "VOLUME", "1000", "FALSE"});

        assertThat(issueCodes()).containsExactly("catalog.unit.baseMissing");
        assertThat(issues.issues().getFirst().row()).isNull();
    }
}
