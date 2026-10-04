package com.ninsky.cronos.infrastructure.imports;

import com.ninsky.cronos.application.imports.spreadsheet.CellValue;
import com.ninsky.cronos.application.imports.spreadsheet.RawSheet;
import com.ninsky.cronos.application.imports.spreadsheet.SheetRow;
import com.ninsky.cronos.application.imports.spreadsheet.SpreadsheetReader;
import com.ninsky.cronos.application.imports.spreadsheet.SpreadsheetRejectedException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Apache POI implementation of {@link SpreadsheetReader}, read-only and defensive:
 * <ul>
 *   <li>zip-bomb limits: minimum inflate ratio and maximum uncompressed entry / text size
 *       ({@link ZipSecureFile} — JVM-wide POI settings, set once here);</li>
 *   <li>XXE: POI's OOXML parsers are hardened (external entities and DTDs disabled) by default;</li>
 *   <li>macro-enabled workbooks (.xlsm content) and OLE2 containers (legacy .xls, password-protected
 *       .xlsx) are refused;</li>
 *   <li>formulas are never evaluated: the result cached by Excel is read, an error result is reported.</li>
 * </ul>
 */
@Slf4j
@Component
public class PoiSpreadsheetReader implements SpreadsheetReader {

    private static final double MIN_INFLATE_RATIO = 0.01;
    private static final long MAX_ENTRY_SIZE_BYTES = 100L * 1024 * 1024;
    private static final long MAX_TEXT_SIZE_CHARS = 10L * 1024 * 1024;
    private static final int MAX_COLUMNS = 50;

    public PoiSpreadsheetReader() {
        ZipSecureFile.setMinInflateRatio(MIN_INFLATE_RATIO);
        ZipSecureFile.setMaxEntrySize(MAX_ENTRY_SIZE_BYTES);
        ZipSecureFile.setMaxTextSize(MAX_TEXT_SIZE_CHARS);
    }

    @Override
    public RawSheet read(byte[] content, String sheetName, int maxDataRows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            if (workbook.isMacroEnabled()) {
                throw new SpreadsheetRejectedException("import.file.macroEnabled");
            }
            XSSFSheet sheet = findSheet(workbook, sheetName);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null || headerRow.getRowNum() != 0) {
                throw new SpreadsheetRejectedException("import.file.headerMissing", sheetName);
            }
            int width = Math.min(Math.max(headerRow.getLastCellNum(), 0), MAX_COLUMNS);
            List<String> headers = IntStream.range(0, width).mapToObj(i -> valueOf(headerRow.getCell(i)).text()).toList();

            List<SheetRow> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                SheetRow sheetRow = new SheetRow(r + 1, IntStream.range(0, width).mapToObj(i -> valueOf(row.getCell(i))).toList());
                if (sheetRow.isBlank()) {
                    continue;
                }
                if (rows.size() == maxDataRows) {
                    throw new SpreadsheetRejectedException("import.file.tooManyRows", maxDataRows);
                }
                rows.add(sheetRow);
            }
            return new RawSheet(headers, rows);
        } catch (POIXMLException | IOException | IllegalArgumentException | IllegalStateException e) {
            // IllegalArgumentException also covers NotOfficeXmlFileException / EmptyFileException;
            // IOException covers the zip-bomb guard ("Zip bomb detected!").
            log.warn("Rejected unreadable .xlsx upload: {}", e.getMessage());
            throw new SpreadsheetRejectedException("import.file.unreadable", e);
        }
    }

    private static XSSFSheet findSheet(XSSFWorkbook workbook, String sheetName) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            if (workbook.getSheetName(i).trim().equalsIgnoreCase(sheetName)) {
                return workbook.getSheetAt(i);
            }
        }
        List<String> available = IntStream.range(0, workbook.getNumberOfSheets()).mapToObj(workbook::getSheetName).toList();
        throw new SpreadsheetRejectedException("import.file.sheetMissing", sheetName, String.join(", ", available));
    }

    private static CellValue valueOf(Cell cell) {
        if (cell == null) {
            return CellValue.BLANK;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> new CellValue(CellValue.Kind.TEXT, cell.getStringCellValue());
            case NUMERIC -> new CellValue(CellValue.Kind.NUMBER, plainNumber(cell.getNumericCellValue()));
            case BOOLEAN -> new CellValue(CellValue.Kind.BOOLEAN, cell.getBooleanCellValue() ? "TRUE" : "FALSE");
            case ERROR -> new CellValue(CellValue.Kind.ERROR, FormulaError.forInt(cell.getErrorCellValue()).getString());
            case BLANK, _NONE, FORMULA -> CellValue.BLANK;
        };
    }

    /**
     * {@code BigDecimal.valueOf(double)} goes through {@code Double.toString}, i.e. the shortest
     * decimal that round-trips: a factor typed as 0.0283495 stays 0.0283495, not
     * 0.028349499999999998… as {@code new BigDecimal(double)} would give.
     */
    private static String plainNumber(double value) {
        BigDecimal decimal = BigDecimal.valueOf(value).stripTrailingZeros();
        return (decimal.scale() < 0 ? decimal.setScale(0) : decimal).toPlainString();
    }
}
