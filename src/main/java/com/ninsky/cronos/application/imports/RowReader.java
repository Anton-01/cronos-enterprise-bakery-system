package com.ninsky.cronos.application.imports;

import com.ninsky.cronos.application.imports.spreadsheet.CellValue;
import com.ninsky.cronos.application.imports.spreadsheet.SheetRow;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Typed, validating access to one data row. Every failed read records an ERROR on the exact
 * row/column and returns empty, so a handler can read every field and report all problems of the
 * row at once.
 */
public final class RowReader {

    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("^[+-]?(\\d+(\\.\\d*)?|\\.\\d+)$");
    private static final Map<String, Boolean> BOOLEAN_WORDS = Map.ofEntries(
            Map.entry("TRUE", true), Map.entry("FALSE", false),
            Map.entry("VERDADERO", true), Map.entry("FALSO", false),
            Map.entry("YES", true), Map.entry("NO", false),
            Map.entry("SI", true), Map.entry("SÍ", true),
            Map.entry("1", true), Map.entry("0", false));

    private final SheetRow row;
    private final SheetLayout layout;
    private final IssueCollector issues;

    public RowReader(SheetRow row, SheetLayout layout, IssueCollector issues) {
        this.row = row;
        this.layout = layout;
        this.issues = issues;
    }

    public int rowNumber() {
        return row.rowNumber();
    }

    /** True once any error was recorded for this row (by this reader or by later rule checks). */
    public boolean hasErrors() {
        return issues.rowHasErrors(row.rowNumber());
    }

    public void error(String column, String messageKey, Object... args) {
        issues.error(row.rowNumber(), column, messageKey, args);
    }

    /** Trimmed, NFC-normalized text without control characters; empty for a blank or absent cell. */
    public Optional<String> text(String column) {
        CellValue cell = cell(column);
        if (cell.kind() == CellValue.Kind.ERROR) {
            error(column, "import.cell.formulaError", cell.text());
            return Optional.empty();
        }
        if (cell.isBlank()) {
            return Optional.empty();
        }
        String clean = CONTROL_CHARS.matcher(Normalizer.normalize(cell.text(), Normalizer.Form.NFC)).replaceAll("").trim();
        return clean.isEmpty() ? Optional.empty() : Optional.of(clean);
    }

    public Optional<String> requiredText(String column, int maxLength) {
        Optional<String> value = text(column);
        if (value.isEmpty()) {
            if (!hasCellError(column)) {
                error(column, "import.cell.required", column);
            }
            return Optional.empty();
        }
        if (value.get().length() > maxLength) {
            error(column, "import.cell.tooLong", column, maxLength);
            return Optional.empty();
        }
        return value;
    }

    /** Plain decimal with '.' as separator (or a numeric cell). Commas are refused: "1,000" is ambiguous. */
    public Optional<BigDecimal> requiredDecimal(String column) {
        Optional<String> value = text(column);
        if (value.isEmpty()) {
            if (!hasCellError(column)) {
                error(column, "import.cell.required", column);
            }
            return Optional.empty();
        }
        String raw = value.get();
        if (raw.indexOf(',') >= 0) {
            error(column, "import.cell.decimalComma", raw);
            return Optional.empty();
        }
        if (!PLAIN_DECIMAL.matcher(raw).matches()) {
            error(column, "import.cell.notANumber", raw);
            return Optional.empty();
        }
        return Optional.of(new BigDecimal(raw));
    }

    public Optional<Boolean> requiredBoolean(String column) {
        Optional<String> value = text(column);
        if (value.isEmpty()) {
            if (!hasCellError(column)) {
                error(column, "import.cell.required", column);
            }
            return Optional.empty();
        }
        Boolean parsed = BOOLEAN_WORDS.get(value.get().toUpperCase(Locale.ROOT));
        if (parsed == null) {
            error(column, "import.cell.notABoolean", value.get());
        }
        return Optional.ofNullable(parsed);
    }

    /** Case-insensitive enum name; on failure the message lists the allowed values. */
    public <E extends Enum<E>> Optional<E> enumValue(String column, Class<E> type, boolean required) {
        Optional<String> value = text(column);
        if (value.isEmpty()) {
            if (required && !hasCellError(column)) {
                error(column, "import.cell.required", column);
            }
            return Optional.empty();
        }
        String normalized = value.get().toUpperCase(Locale.ROOT);
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(normalized)) {
                return Optional.of(constant);
            }
        }
        error(column, "import.cell.notAllowed", value.get(), Arrays.toString(type.getEnumConstants()));
        return Optional.empty();
    }

    private boolean hasCellError(String column) {
        return cell(column).kind() == CellValue.Kind.ERROR;
    }

    private CellValue cell(String column) {
        return layout.indexOf(column).map(row::cell).orElse(CellValue.BLANK);
    }
}
