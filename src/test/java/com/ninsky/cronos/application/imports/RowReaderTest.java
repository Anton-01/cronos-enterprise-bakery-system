package com.ninsky.cronos.application.imports;

import com.ninsky.cronos.application.imports.spreadsheet.CellValue;
import com.ninsky.cronos.application.imports.spreadsheet.SheetRow;
import com.ninsky.cronos.domain.entity.enums.UnitDimension;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class RowReaderTest {

    private final IssueCollector issues = new IssueCollector(new StaticMessageSource(), Locale.ENGLISH);

    private static CellValue text(String value) {
        return new CellValue(CellValue.Kind.TEXT, value);
    }

    private RowReader reader(List<String> headers, CellValue... cells) {
        SheetLayout layout = SheetLayout.resolve(headers,
                List.of(ColumnSpec.required("code"), ColumnSpec.required("factorToBase"), ColumnSpec.optional("status")), issues);
        return new RowReader(new SheetRow(7, List.of(cells)), layout, issues);
    }

    private List<String> codes() {
        return issues.issues().stream().map(ImportIssue::code).toList();
    }

    @Test
    void headersAreMatchedIgnoringCaseSpacesAndRequiredMarkers() {
        reader(List.of(" Code* ", "factor_to_base", "Notes"));
        assertThat(codes()).containsExactly("import.header.unknown");
        assertThat(issues.hasErrors()).isFalse();
    }

    @Test
    void missingAndDuplicatedHeadersAreErrors() {
        reader(List.of("code", "CODE"));
        assertThat(codes()).containsExactlyInAnyOrder("import.header.duplicated", "import.header.missing");
    }

    @Test
    void decimalsMustUseADotAndNoThousandsSeparator() {
        RowReader row = reader(List.of("code", "factorToBase"), text("oz"), text("28,35"));
        assertThat(row.requiredDecimal("factorToBase")).isEmpty();
        assertThat(codes()).containsExactly("import.cell.decimalComma");
        assertThat(issues.issues().getFirst().row()).isEqualTo(7);
        assertThat(issues.issues().getFirst().column()).isEqualTo("factorToBase");
    }

    @Test
    void numericCellsAndPlainTextDecimalsParse() {
        RowReader row = reader(List.of("code", "factorToBase"), text("oz"), new CellValue(CellValue.Kind.NUMBER, "28.349523125"));
        assertThat(row.requiredDecimal("factorToBase")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("28.349523125"));
        assertThat(issues.hasErrors()).isFalse();
    }

    @Test
    void textIsTrimmedNormalizedAndStrippedOfControlCharacters() {
        RowReader row = reader(List.of("code", "factorToBase"), text("  k\u0000g\t "), text("1"));
        assertThat(row.requiredText("code", 20)).contains("kg");
    }

    @Test
    void booleansAcceptEnglishSpanishAndNumericForms() {
        RowReader row = reader(List.of("code", "factorToBase"), text("Sí"), text("maybe"));
        assertThat(row.requiredBoolean("code")).contains(true);
        assertThat(row.requiredBoolean("factorToBase")).isEmpty();
        assertThat(codes()).containsExactly("import.cell.notABoolean");
    }

    @Test
    void enumsAreCaseInsensitiveAndListAllowedValuesOnFailure() {
        RowReader row = reader(List.of("code", "factorToBase", "status"), text("volume"), text("1"), text("MASA"));
        assertThat(row.enumValue("code", UnitDimension.class, true)).contains(UnitDimension.VOLUME);
        assertThat(row.enumValue("status", UnitDimension.class, true)).isEmpty();
        assertThat(issues.issues().getFirst().message()).isEqualTo("import.cell.notAllowed");
    }

    @Test
    void formulaErrorsAreReportedOnceNotAsMissingValues() {
        RowReader row = reader(List.of("code", "factorToBase"), text("x"), new CellValue(CellValue.Kind.ERROR, "#DIV/0!"));
        assertThat(row.requiredDecimal("factorToBase")).isEmpty();
        assertThat(codes()).containsExactly("import.cell.formulaError");
    }
}
