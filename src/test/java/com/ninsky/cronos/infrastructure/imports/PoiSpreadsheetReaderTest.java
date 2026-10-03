package com.ninsky.cronos.infrastructure.imports;

import com.ninsky.cronos.application.imports.spreadsheet.CellValue;
import com.ninsky.cronos.application.imports.spreadsheet.RawSheet;
import com.ninsky.cronos.application.imports.spreadsheet.SpreadsheetRejectedException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoiSpreadsheetReaderTest {

    private final PoiSpreadsheetReader reader = new PoiSpreadsheetReader();

    private static byte[] workbook(String sheetName, Object[]... rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.createSheet("Instrucciones").createRow(0).createCell(0).setCellValue("ignored");
            Sheet sheet = wb.createSheet(sheetName);
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    Object value = rows[r][c];
                    if (value instanceof String s) {
                        row.createCell(c).setCellValue(s);
                    } else if (value instanceof Number n) {
                        row.createCell(c).setCellValue(n.doubleValue());
                    } else if (value instanceof Boolean b) {
                        row.createCell(c).setCellValue(b);
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void readsTheNamedSheetWithCanonicalCellValues() throws IOException {
        byte[] file = workbook("MeasurementUnits",
                new Object[]{"code", "factorToBase", "isBaseUnit"},
                new Object[]{"oz", 28.349523125, false},
                new Object[]{},
                new Object[]{"kg", 1000, true});

        RawSheet sheet = reader.read(file, "measurementunits", 10);

        assertThat(sheet.headers()).containsExactly("code", "factorToBase", "isBaseUnit");
        assertThat(sheet.rows()).hasSize(2);
        assertThat(sheet.rows().get(0).rowNumber()).isEqualTo(2);
        assertThat(sheet.rows().get(0).cell(1)).isEqualTo(new CellValue(CellValue.Kind.NUMBER, "28.349523125"));
        assertThat(sheet.rows().get(1).rowNumber()).as("blank row 3 skipped, numbering kept").isEqualTo(4);
        assertThat(sheet.rows().get(1).cell(1).text()).isEqualTo("1000");
        assertThat(sheet.rows().get(1).cell(2)).isEqualTo(new CellValue(CellValue.Kind.BOOLEAN, "TRUE"));
    }

    @Test
    void reportsAMissingSheetWithTheSheetsFound() throws IOException {
        byte[] file = workbook("Other", new Object[]{"code"});
        assertThatThrownBy(() -> reader.read(file, "UnitTypes", 10))
                .isInstanceOf(SpreadsheetRejectedException.class)
                .extracting(e -> ((SpreadsheetRejectedException) e).messageKey())
                .isEqualTo("import.file.sheetMissing");
    }

    @Test
    void enforcesTheRowLimitOnNonBlankRows() throws IOException {
        byte[] file = workbook("UnitTypes", new Object[]{"code"}, new Object[]{"A"}, new Object[]{"B"}, new Object[]{"C"});
        assertThatThrownBy(() -> reader.read(file, "UnitTypes", 2))
                .isInstanceOf(SpreadsheetRejectedException.class)
                .hasMessage("import.file.tooManyRows");
    }

    @Test
    void rejectsContentThatIsNotAnOoxmlPackage() {
        byte[] notAWorkbook = "PK\u0003\u0004 definitely not a zip".getBytes(StandardCharsets.ISO_8859_1);
        assertThatThrownBy(() -> reader.read(notAWorkbook, "UnitTypes", 10))
                .isInstanceOf(SpreadsheetRejectedException.class)
                .hasMessage("import.file.unreadable");
    }
}
