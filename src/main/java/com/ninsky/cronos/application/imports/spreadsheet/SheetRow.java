package com.ninsky.cronos.application.imports.spreadsheet;

import java.util.List;

/** One data row: {@code rowNumber} is 1-based as displayed by Excel; cells are positional (header order). */
public record SheetRow(int rowNumber, List<CellValue> cells) {

    public SheetRow {
        cells = List.copyOf(cells);
    }

    public CellValue cell(int index) {
        return index < cells.size() ? cells.get(index) : CellValue.BLANK;
    }

    public boolean isBlank() {
        return cells.stream().allMatch(CellValue::isBlank);
    }
}
